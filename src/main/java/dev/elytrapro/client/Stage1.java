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
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Stage 1: while gliding, every rocket click looks for a player right in front of you
 * (within stage1Range, inside a cone). If one is there and ANY item in your inventory (hotbar or bag)
 * has Knockback, it turns toward that player, brings the item into your hand, hits (as a critical:
 * sprint is cancelled for the hit) and puts everything back, all inside the same tick.
 * Not gliding = nothing happens. If the swing is not ready yet (or nobody is in front yet) it keeps
 * trying for stage1WaitTicks.
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
        return ElytraProConfig.get().stage1Enabled;
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

    /**
     * Called BEFORE the rocket is used: turns your view to the player in front so the rocket flies toward him
     * (only with stage1TurnView). Does nothing when Stage 1 is off, the wings are closed or nobody is in front.
     */
    public static void preAim(MinecraftClient mc, ClientPlayerEntity p) {
        ElytraProConfig cfg = ElytraProConfig.get();
        if (!cfg.stage1Enabled || !cfg.stage1TurnView || !p.isFallFlying() || mc.world == null) return;
        if (findKnockbackSlot(p.getInventory()) < 0) return;
        PlayerEntity target = findTarget(mc, p);
        if (target == null) return;
        float[] a = anglesTo(p, target);
        if (a != null) {
            p.setYaw(a[0]);
            p.setPitch(a[1]);
        }
    }

    /** Called right after a rocket click (also a swallowed one) while the wings are open. */
    public static void trigger(MinecraftClient mc, ClientPlayerEntity p, Hand rocketHand) {
        if (!isEnabled() || !p.isFallFlying() || mc.world == null || mc.interactionManager == null) return;
        boolean keepSlot = rocketHand == Hand.MAIN_HAND; // the rocket is in the selected slot: do not move away from it
        if (attempt(mc, p, keepSlot)) {
            pending = 0;
        } else {
            pending = ElytraProConfig.get().stage1WaitTicks;
            pendingKeepSlot = keepSlot;
        }
    }

    /** @return true when finished (hit done, or nothing to do); false to keep waiting. */
    private static boolean attempt(MinecraftClient mc, ClientPlayerEntity p, boolean keepSlot) {
        if (mc.currentScreen != null) return true;
        PlayerInventory inv = p.getInventory();
        int kb = findKnockbackSlot(inv); // 0-8 hotbar, 9-35 bag
        if (kb < 0) return true;         // no knockback item anywhere

        PlayerEntity target = findTarget(mc, p);
        if (target == null) return false;
        if (p.getAttackCooldownProgress(0.5f) < 0.9f) return false; // wait for a full (critical) swing

        ElytraProConfig cfg = ElytraProConfig.get();
        int orig = inv.selectedSlot;
        int back = (keepSlot || cfg.stage1ReturnSlot < 1 || cfg.stage1ReturnSlot > 9) ? orig : cfg.stage1ReturnSlot - 1;

        // item in the bag: swap it into a hotbar slot for the hit (never the slot that holds the rocket)
        boolean fromBag = kb >= 9;
        int use = fromBag ? (orig == 8 ? 7 : 8) : kb;

        // critical hits need: not sprinting, in the air (gliding counts), full cooldown
        boolean sprint = p.isSprinting();
        if (sprint) {
            p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            p.setSprinting(false);
        }

        // face the target for the hit (server side too)
        boolean onGround = p.isOnGround();
        float[] a = anglesTo(p, target);
        float realYaw = p.getYaw(), realPitch = p.getPitch();
        if (a != null) {
            p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(a[0], a[1], onGround));
            if (cfg.stage1TurnView) {
                p.setYaw(a[0]);
                p.setPitch(a[1]);
            }
        }

        if (fromBag) mc.interactionManager.clickSlot(0, kb, use, SlotActionType.SWAP, p);
        inv.selectedSlot = use;
        mc.interactionManager.attackEntity(p, target); // syncs the new slot, then sends the attack
        p.swingHand(Hand.MAIN_HAND);
        if (fromBag) mc.interactionManager.clickSlot(0, kb, use, SlotActionType.SWAP, p);
        inv.selectedSlot = back;
        if (back != use) {
            p.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(back));
        }

        if (a != null && !cfg.stage1TurnView) {
            // silent aim: give the server your real view back in the same tick
            p.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(realYaw, realPitch, onGround));
        }

        if (sprint) {
            p.setSprinting(true);
            p.networkHandler.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_SPRINTING));
        }
        return true;
    }

    /** Yaw/pitch from your eyes to a point inside the target's hitbox (between its nearest point and its center). */
    private static float[] anglesTo(ClientPlayerEntity p, PlayerEntity t) {
        Vec3d eye = p.getEyePos();
        Box b = t.getBoundingBox();
        Vec3d near = new Vec3d(
                MathHelper.clamp(eye.x, b.minX, b.maxX),
                MathHelper.clamp(eye.y, b.minY, b.maxY),
                MathHelper.clamp(eye.z, b.minZ, b.maxZ));
        Vec3d aim = near.add(b.getCenter().subtract(near).multiply(0.5));
        double dx = aim.x - eye.x, dy = aim.y - eye.y, dz = aim.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        if (dx * dx + dy * dy + dz * dz < 1.0E-6) return null;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return new float[] {MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0f, 90.0f)};
    }

    /** Selected slot first, then the hotbar, then the bag. -1 = none. */
    private static int findKnockbackSlot(PlayerInventory inv) {
        int cur = inv.selectedSlot;
        if (hasKnockback(inv.getStack(cur))) return cur;
        for (int i = 0; i < 36; i++) {
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
