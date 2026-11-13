package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import dev.elytrapro.util.ElytraUtil;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Collision switches. Both flags are true ONLY while the local player's own tick runs
 * (set/cleared by ClientPlayerEntityMixin), so nothing else in the world is affected.
 */
public final class Phase {
    /** Planks / logs have no collision. */
    public static boolean active;
    /** Cobweb does not slow the player. */
    public static boolean noWeb;

    private static int hold;
    private static boolean lastActive;

    private Phase() {}

    /** Keeps the switches on for a few ticks (take-off before the wings are open). */
    public static void hold(int ticks) {
        if (ticks > hold) hold = ticks;
    }

    public static void begin(ClientPlayerEntity p) {
        ElytraProConfig cfg = ElytraProConfig.get();
        boolean launching = p.isFallFlying() || hold > 0;
        if (hold > 0) hold--;
        noWeb = cfg.ignoreCobweb && launching;
        // also stay on while the hitbox is still inside such a block, so you are never pushed out / stuck
        boolean want = cfg.phaseThroughBlocks && (launching || (lastActive && ElytraUtil.insidePhaseBlock(p)));
        active = want;
        lastActive = want;
    }

    public static void end() {
        active = false;
        noWeb = false;
    }
}
