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
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Stage 1 (toggle key, default G). Items are NEVER moved: only the selected hotbar slot changes.
 *  - hit with the sword            -> the next slot is the first hotbar slot that holds a Knockback item
 *  - hit with the Knockback item   -> back to slot 1
 * So the hits alternate sword / knockback / sword / ... The switch is sent in the same tick as the hit.
 */
public final class Stage1 {
    private static final int HOME_SLOT = 0; // slot 1

    private static KeyBinding toggleKey;

    private Stage1() {}

    public static void init() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.elytrapro.stage1", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.elytrapro"));
    }

    public static boolean isEnabled() {
        return ElytraProConfig.get().stage1Enabled;
    }

    /** Client tick: only the toggle key. */
    public static void tick(MinecraftClient mc) {
        if (toggleKey == null) return;
        ClientPlayerEntity p = mc.player;
        while (toggleKey.wasPressed()) {
            ElytraProConfig cfg = ElytraProConfig.get();
            cfg.stage1Enabled = !cfg.stage1Enabled;
            cfg.save();
            if (p != null) {
                p.sendMessage(Text.literal("ElytraPro Aşama 1: " + (cfg.stage1Enabled ? "AÇIK" : "KAPALI")), true);
            }
        }
    }

    /** Called right after the client attacked an entity (the attack itself is untouched). */
    public static void afterAttack(MinecraftClient mc, Entity target) {
        ClientPlayerEntity p = mc.player;
        if (p == null || !isEnabled() || !(target instanceof LivingEntity)) return;

        PlayerInventory inv = p.getInventory();
        int cur = inv.selectedSlot;
        ItemStack held = inv.getStack(cur);

        int next;
        if (hasKnockback(held)) {
            next = HOME_SLOT;                       // knockback hit done -> slot 1
        } else if (held.isIn(ItemTags.SWORDS)) {
            next = findKnockbackHotbarSlot(inv, cur); // sword hit done -> knockback slot
        } else {
            return;
        }
        if (next < 0 || next == cur) return;

        inv.selectedSlot = next;
        p.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(next));
    }

    private static int findKnockbackHotbarSlot(PlayerInventory inv, int skip) {
        for (int i = 0; i < 9; i++) {
            if (i != skip && hasKnockback(inv.getStack(i))) return i;
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
