package com.bravo68web.mclivetracker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class McLiveTrackerMod implements ModInitializer {
    public static final String MODID = "mclivetracker";

    private final AtomicReference<MinecraftServer> serverRef = new AtomicReference<>();
    private final WebServerManager webServer = new WebServerManager(resolvePort());

    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverRef.set(server);
            webServer.setSeed(server.getOverworld().getSeed());
            webServer.start();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            webServer.stop();
            serverRef.set(null);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Throttle to ~5 updates per second
            if (++tickCounter % 4 == 0) {
                List<PlayerPosition> positions = collectPositions(server);
                webServer.updatePlayerPositions(positions);
            }
        });
    }

    private List<PlayerPosition> collectPositions(MinecraftServer server) {
        List<PlayerPosition> list = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            list.add(PlayerPosition.from(p));
        }
        return list;
    }

    private static int resolvePort() {
        // System property has priority, then env var, otherwise default
        int def = 27134;
        try {
            String prop = System.getProperty("mclivetracker.port");
            if (prop != null && !prop.isEmpty())
                return Integer.parseInt(prop);
        } catch (NumberFormatException ignored) {
        }
        try {
            String env = System.getenv("MCLIVETRACKER_PORT");
            if (env != null && !env.isEmpty())
                return Integer.parseInt(env);
        } catch (NumberFormatException ignored) {
        }
        return def;
    }
}
