package com.kit531.bloodfx;

import com.kit531.bloodfx.client.HurtTracker;
import com.kit531.bloodfx.config.BloodFxConfig;
import com.kit531.bloodfx.update.Updater;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class BloodFxClient implements ClientModInitializer {
    public static final String MOD_ID = "bloodfx";

    @Override
    public void onInitializeClient() {
        BloodFxConfig.load();
        ClientTickEvents.END_CLIENT_TICK.register(HurtTracker::tick);
        ClientTickEvents.END_CLIENT_TICK.register(Updater::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HurtTracker.reset());
        Updater.init();
    }
}
