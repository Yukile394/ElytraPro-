package dev.elytrapro.client;

import dev.elytrapro.config.ElytraProConfig;
import net.minecraft.client.network.ClientPlayerEntity;

/** While {@link #active} is true, planks/logs have no collision (set only during the local player's tick). */
public final class Phase {
    public static boolean active;

    private Phase() {}

    public static boolean wants(ClientPlayerEntity p) {
        return ElytraProConfig.get().phaseThroughBlocks && p.isFallFlying();
    }
}
