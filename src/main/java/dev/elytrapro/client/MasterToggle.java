package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Key (default H) that switches the whole mod on/off. OFF = everything stays vanilla. */
public final class MasterToggle {
    private static KeyBinding key;

    private MasterToggle() {}

    public static void init() {
        key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.elytrapro.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.elytrapro"));
    }

    public static void tick(MinecraftClient mc) {
        if (key == null) return;
        while (key.wasPressed()) {
            ElytraProConfig cfg = ElytraProConfig.get();
            cfg.enabled = !cfg.enabled;
            cfg.save();
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("ElytraPro: " + (cfg.enabled ? "AÇIK" : "KAPALI")), true);
            }
        }
    }
}
