package com.bravo68web.mclivetracker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            webServer.setMetadata(buildMetadata(server));
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

    private Map<String, Object> buildMetadata(MinecraftServer server) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("minecraftVersion", server.getVersion());
        meta.put("modVersion", FabricLoader.getInstance()
                .getModContainer(MODID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown"));
        meta.put("modId", MODID);
        meta.put("levelName", server.getSaveProperties().getLevelName());
        // String to avoid precision loss in JS (values above 2^53)
        meta.put("seed", Long.toString(server.getOverworld().getSeed()));
        meta.put("difficulty", server.getSaveProperties().getDifficulty().getName());
        meta.put("gameMode", server.getDefaultGameMode().getId());
        meta.put("hardcore", server.isHardcore());
        meta.put("dataVersion", server.getSaveProperties().getVersion());
        meta.put("dimensions", server.getWorldRegistryKeys().stream()
                .map(key -> key.getValue().toString())
                .sorted()
                .toList());
        WorldProperties.SpawnPoint spawn = server.getOverworld().getSpawnPoint();
        BlockPos spawnPos = spawn.getPos();
        meta.put("spawn", Map.of(
                "x", spawnPos.getX(),
                "y", spawnPos.getY(),
                "z", spawnPos.getZ(),
                "dimension", spawn.getDimension().getValue().toString()));
        return meta;
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
