package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import dev.elytrapro.util.ElytraUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class AutoElytra {
    private static int airTicks;
    private static boolean attemptedThisPress;

    private AutoElytra() {}

    /**
     * Runs every client tick. Inside a cobweb only: opens the wings ONE time per space press (no retries,
     * no packet spam) and never sends a "stop gliding" packet.
     */
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ElytraProConfig cfg = ElytraProConfig.get();
        if (p == null || mc.world == null) { airTicks = 0; attemptedThisPress = false; return; }

        // queued "take off + rocket" from a right click
        AutoFirework.tick(mc);

        if (!cfg.enabled || !cfg.autoElytra) return;

        boolean jump = mc.options.jumpKey.isPressed() && mc.currentScreen == null;
        if (!jump) attemptedThisPress = false;

        airTicks = (p.isOnGround() || p.isTouchingWater()) ? 0 : airTicks + 1;

        if (p.isFallFlying()) {
            // wings are open (by us or by vanilla): this press is done
            if (jump) attemptedThisPress = true;
            return;
        }

        // Only inside a cobweb. Everywhere else space stays vanilla.
        if (!ElytraUtil.inCobweb(p)) return;

        if (!jump || attemptedThisPress) return;
        if (airTicks < cfg.minAirTicks) return;
        if (!ElytraUtil.canStartNow(p)) return; // still on the ground: wait inside the same press

        attemptedThisPress = true; // exactly one attempt per press
        ElytraUtil.tryStartGliding(p);
    }
}
