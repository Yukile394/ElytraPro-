package dev.elytrapro.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElytraProClient implements ClientModInitializer {
    public static final String MOD_ID = "elytrapro";
    /** Used for real errors only. */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        Stage1.init();
        ClientTickEvents.END_CLIENT_TICK.register(AutoElytra::tick);
        ClientTickEvents.END_CLIENT_TICK.register(Stage1::tick);
    }
}
