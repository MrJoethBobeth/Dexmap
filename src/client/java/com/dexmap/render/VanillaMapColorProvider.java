package com.dexmap.render;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

public class VanillaMapColorProvider {
    private static final MinecraftClient client = MinecraftClient.getInstance();

    /**
     * Get the vanilla map color (with vanilla-style height shading) for the
     * surface block at X,Z.
     */
    public static int getMapColor(ClientWorld world, BlockPos posXZ) {
        // Find surface Y with WORLD_SURFACE (vanilla map uses this kind of logic)
        int x = posXZ.getX();
        int z = posXZ.getZ();

        int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        if (topY <= world.getBottomY()) {
            return 0;
        }
        BlockPos surface = new BlockPos(x, topY - 1, z);
        BlockState state = world.getBlockState(surface);
        if (state.isAir()) return 0;

        MapColor mapColor = state.getMapColor(world, surface);
        if (mapColor == MapColor.CLEAR) return 0;

        int brightness = calculateVanillaBrightness(world, x, z);
        return mapColor.getRenderColor(brightness);
    }

    /**
     * More accurate color: first try block color providers (biome-tinted grass,
     * leaves, water), then apply vanilla-style shading. If none, fall back to
     * vanilla MapColor.
     */
    public static int getAccurateBlockColor(ClientWorld world, BlockPos posXZ) {
        int x = posXZ.getX();
        int z = posXZ.getZ();
        int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        if (topY <= world.getBottomY()) return 0;

        BlockPos surface = new BlockPos(x, topY - 1, z);
        BlockState state = world.getBlockState(surface);

        // Try client BlockColors first (handles biome-tinted blocks)
        int providerColor = -1;
        try {
            if (client != null && client.getBlockColors() != null) {
                providerColor = client.getBlockColors().getColor(state, world, surface, 0);
            }
        } catch (Exception ignored) {}

        if (providerColor != -1 && providerColor != 0xFFFFFF) {
            return applyVanillaShading(providerColor, world, x, z);
        }

        // Fallback to vanilla map color
        return getMapColor(world, posXZ);
    }

    private static int calculateVanillaBrightness(ClientWorld world, int x, int z) {
        int here = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        int north = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z - 1);
        int south = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z + 1);
        int west = world.getTopY(Heightmap.Type.WORLD_SURFACE, x - 1, z);
        int east = world.getTopY(Heightmap.Type.WORLD_SURFACE, x + 1, z);

        int slope =
                (north - here) + (south - here) + (west - here) + (east - here);

        // Vanilla-like brightness bucket (0..3)
        if (slope > 1) return 1;     // darker
        if (slope < -1) return 3;    // brighter
        return 2;                    // normal
    }

    private static int applyVanillaShading(
            int color, ClientWorld world, int x, int z
    ) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int brightness = calculateVanillaBrightness(world, x, z);
        float mod =
                switch (brightness) {
                    case 0 -> 0.35f;
                    case 1 -> 0.70f;
                    case 2 -> 1.00f;
                    case 3 -> 1.40f;
                    default -> 1.00f;
                };

        r = MathHelper.clamp((int) (r * mod), 0, 255);
        g = MathHelper.clamp((int) (g * mod), 0, 255);
        b = MathHelper.clamp((int) (b * mod), 0, 255);
        return (r << 16) | (g << 8) | b;
    }
}