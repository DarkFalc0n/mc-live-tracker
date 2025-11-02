package com.bravo68web.mclivetracker;

import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerPosition {
    public final String name;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;
    public final String dimension; // e.g., minecraft:overworld

    public PlayerPosition(String name, double x, double y, double z, float yaw, float pitch, String dimension) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.dimension = dimension;
    }

    public static PlayerPosition from(ServerPlayerEntity p) {
    return new PlayerPosition(
        p.getEntityWorld().getPlayerByUuid(p.getUuid()).getName().getString(),
                p.getX(),
                p.getY(),
                p.getZ(),
                p.getYaw(),
                p.getPitch(),
                p.getEntityWorld().getRegistryKey().getValue().toString()
        );
    }
}
