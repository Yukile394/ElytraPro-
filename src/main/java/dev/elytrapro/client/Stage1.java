package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Stage 1: while gliding, every rocket click looks for a player right in front of you
 * (within stage1Range, inside a narrow cone). If one is there and a hotbar item has Knockback,
 * it swaps to it, hits (as a critical: sprint is cancelled for the hit) and swaps back,
 * all inside the same tick. Not gliding = nothing happens.
 */
public final class Stage1 {
    private static KeyBinding toggleKey;
    private static int pending;
    private static boolean pendingKeepSlot;

    private Stage1() {}

    public static void init() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.elytrapro.stage1", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.elytrapro"));
    }

    public static boolean isEnabled() {
        ElytraProConfig c = ElytraProConfig.get();
        return c.enabled && c.stage1Enabled;
    }

    /** Client tick: handles the toggle key and the short retry window after a rocket click. */
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (toggleKey != null) {
            while (toggleKey.wasPressed()) {
                ElytraProConfig cfg = ElytraProConfig.get();
                cfg.stage1Enabled = !cfg.stage1Enabled;
                cfg.save();
                pending = 0;
                if (p != null) {
                    p.sendMessage(Text.literal("ElytraPro Aşama 1: " + (cfg.stage1Enabled ? "AÇIK" : "KAPALI")), true);
                }
            }
        }
        if (pending <= 0) return;
        if (p == null || mc.world == null || mc.interactionManager == null
                || !isEnabled() || !p.isFallFlying()) {
            pending = 0;
            return;
        }
        pending--;
        if (attempt(mc, p, pendingKeepSlot)) pending = 0;
    }

    /** Called right after a rocket click while the wings are open. */
    public static void trigger(MinecraftClient mc, ClientPlayerEntity p, Hand rocketHand) {
        if (!isEnabled() || !p.isFallFlying() || mc.world == null || mc.interactionManager == null) return;
        boolean keepSlot = rocketHand == Hand.MAIN_HAND; // the rocket is in the selected slot: do not move away from it
        if (!attempt(mc, p, keepSlot)) {
            pending = ElytraProConfig.get().stage1WaitTicks;
            pendingKeepSlot = keepSlot;
        } else {
            pending = 0;
        }
    }

    /** @return true when finished (hit done, or nothing to do); false to keep waiting. */
    private static boolean attempt(MinecraftClient mc, ClientPlayerEntity p, boolean keepSlot) {
        if (mc.currentScreen != null) return true;
        PlayerInventory inv = p.getInventory();
        int kb = findKnockbackSlot(inv);
        if (kb < 0) return true; // no knockback item in the hotbar

        PlayerEntity target = findTarget(mc, p);
        if (target == null) return false;
        if (p.getAttackCooldownProgress(0.5f) < 0.9f) return false; // wait for a full (critical) swing

        ElytraProConfig cfg = ElytraProConfig.get();
        int orig = inv.selectedSlot;
        int back = (keepSlot || cfg.stage1ReturnSlot < 1 || cfg.stage1ReturnSlot > 9) ? orig : cfg.stage1ReturnSlot - 1;

        // critical hits need: not sprinting, in the air (gliding counts), full cooldown
        boolean sprint = p.isSprinting();
        if (sprint) {
            p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            p.setSprinting(false);
        }

        inv.selectedSlot = kb;
        mc.interactionManager.attackEntity(p, target); // syncs the new slot, then sends the attack
        p.swingHand(Hand.MAIN_HAND);
        inv.selectedSlot = back;
        if (back != kb) {
            p.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(back));
        }

        if (sprint) {
            p.setSprinting(true);
            p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_SPRINTING));
        }
        return true;
    }

    private static int findKnockbackSlot(PlayerInventory inv) {
        int cur = inv.selectedSlot;
        if (hasKnockback(inv.getStack(cur))) return cur;
        for (int i = 0; i < 9; i++) {
            if (hasKnockback(inv.getStack(i))) return i;
        }
        return -1;
    }

    private static boolean hasKnockback(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (RegistryEntry<Enchantment> e : stack.getEnchantments().getEnchantments()) {
            if (e.getKey().map(k -> k.getValue().getPath().equals("knockback")).orElse(false)) return true;
        }
        return false;
    }

    /** The player closest to your crosshair that is within range AND in front of you (never behind or beside). */
    private static PlayerEntity findTarget(MinecraftClient mc, ClientPlayerEntity p) {
        ElytraProConfig cfg = ElytraProConfig.get();
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f).normalize();
        double range2 = cfg.stage1Range * cfg.stage1Range;
        double minDot = Math.cos(Math.toRadians(cfg.stage1Angle));

        PlayerEntity best = null;
        double bestDot = -2.0;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator()) continue;
            Box b = o.getBoundingBox();

            // distance from the eyes to the hitbox
            double dx = Math.max(Math.max(b.minX - eye.x, 0.0), eye.x - b.maxX);
            double dy = Math.max(Math.max(b.minY - eye.y, 0.0), eye.y - b.maxY);
            double dz = Math.max(Math.max(b.minZ - eye.z, 0.0), eye.z - b.maxZ);
            if (dx * dx + dy * dy + dz * dz > range2) continue;

            // point of the hitbox nearest to where your view line passes it
            Vec3d to = b.getCenter().subtract(eye);
            double proj = Math.max(0.0, to.dotProduct(look));
            Vec3d q = eye.add(look.multiply(proj));
            Vec3d cp = new Vec3d(
                    MathHelper.clamp(q.x, b.minX, b.maxX),
                    MathHelper.clamp(q.y, b.minY, b.maxY),
                    MathHelper.clamp(q.z, b.minZ, b.maxZ));
            Vec3d v = cp.subtract(eye);
            double len = v.length();
            double dot = len < 1.0E-6 ? 1.0 : v.dotProduct(look) / len;
            if (dot < minDot) continue;

            if (dot > bestDot) {
                bestDot = dot;
                best = o;
            }
        }
        return best;
    }
}
