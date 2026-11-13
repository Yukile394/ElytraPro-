package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Fullbright: a client-side night vision effect (nothing is sent to the server). */
public final class Fullbright {
    private static final int LONG = 1_000_000; // long enough that the "fading" flicker never starts
    private static KeyBinding toggleKey;
    private static boolean applied;

    private Fullbright() {}

    public static void init() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.elytrapro.fullbright", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.elytrapro"));
    }

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ElytraProConfig cfg = ElytraProConfig.get();
        if (toggleKey != null) {
            while (toggleKey.wasPressed()) {
                cfg.fullbright = !cfg.fullbright;
                cfg.save();
                if (p != null) {
                    p.sendMessage(Text.literal("ElytraPro Fullbright: " + (cfg.fullbright ? "AÇIK" : "KAPALI")), true);
                }
            }
        }
        if (p == null) { applied = false; return; }

        if (cfg.fullbright) {
            StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < LONG / 2) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, LONG, 0, false, false, false));
                applied = true;
            }
        } else if (applied) {
            p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            applied = false;
        }
    }
}
