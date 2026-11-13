package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

/**
 * Stage 1 (toggle key, default G).
 * ON + a sword in your selected slot + a hit: the hit is made with the sword's damage and cooldown AND the
 * Knockback enchantment of another item/block from your inventory. The selected slot never changes: the
 * knockback item is swapped into the sword's slot with inventory packets and back, all inside the same tick.
 * The hit always waits for the sword's full cooldown (a click before that is queued for a few ticks).
 */
public final class Stage1 {
    private static final float READY = 1.0f;  // sword cooldown that must be reached
    private static final int WAIT_TICKS = 12; // how long an early click stays queued

    private static KeyBinding toggleKey;
    private static boolean busy;
    private static int pending;

    private Stage1() {}

    public static void init() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.elytrapro.stage1", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.elytrapro"));
    }

    public static boolean isEnabled() {
        return ElytraProConfig.get().stage1Enabled;
    }

    /** Client tick: toggle key + fires a queued hit as soon as the sword is ready. */
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
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null
                || !isEnabled() || !isPlainSword(p.getMainHandStack())) {
            pending = 0;
            return;
        }
        pending--;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity target) || !target.isAlive()) {
            return; // keep waiting until the crosshair is on a target again
        }
        if (p.getAttackCooldownProgress(0.5f) < READY) return;
        int slotId = findKnockbackSlotId(p.getInventory());
        if (slotId < 0) { pending = 0; return; }
        pending = 0;
        combinedHit(mc, p, target, slotId);
        p.swingHand(Hand.MAIN_HAND);
    }

    /**
     * Called by the attack hook (every client attack on an entity).
     * @return true when the attack was handled here and the vanilla one must be skipped.
     */
    public static boolean onAttack(MinecraftClient mc, Entity target) {
        ClientPlayerEntity p = mc.player;
        if (busy || p == null || mc.interactionManager == null || !isEnabled()) return false;
        if (!(target instanceof LivingEntity)) return false;
        if (!isPlainSword(p.getMainHandStack())) return false;
        int slotId = findKnockbackSlotId(p.getInventory());
        if (slotId < 0) return false;

        if (p.getAttackCooldownProgress(0.5f) < READY) {
            pending = WAIT_TICKS; // early click: wait for the sword's cooldown, then hit
            return true;
        }
        pending = 0;
        combinedHit(mc, p, target, slotId);
        return true; // vanilla still plays the swing animation after this
    }

    /** swap in, hit, swap back: same tick, same selected slot. */
    private static void combinedHit(MinecraftClient mc, ClientPlayerEntity p, Entity target, int slotId) {
        int selected = p.getInventory().selectedSlot;
        busy = true;
        try {
            mc.interactionManager.clickSlot(0, slotId, selected, SlotActionType.SWAP, p);
            mc.interactionManager.attackEntity(p, target);
            mc.interactionManager.clickSlot(0, slotId, selected, SlotActionType.SWAP, p);
        } finally {
            busy = false;
        }
    }

    /** A sword that has no Knockback itself (if it has, the vanilla hit already does everything). */
    private static boolean isPlainSword(ItemStack stack) {
        return stack.isIn(ItemTags.SWORDS) && !hasKnockback(stack);
    }

    /** Screen-handler slot id of the first item with Knockback (hotbar first, then the bag), skipping the held slot. */
    private static int findKnockbackSlotId(PlayerInventory inv) {
        int selected = inv.selectedSlot;
        for (int i = 0; i < 36; i++) {
            if (i == selected) continue;
            if (hasKnockback(inv.getStack(i))) return i < 9 ? 36 + i : i;
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
}
