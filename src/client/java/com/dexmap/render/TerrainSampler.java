package com.dexmap.render;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

public final class TerrainSampler {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    public static final class SurfaceInfo {
        public final int x;
        public final int z;

        public final int surfaceY; // WORLD_SURFACE - 1
        public final int terrainY; // MOTION_BLOCKING_NO_LEAVES - 1
        public final int oceanFloorY; // OCEAN_FLOOR - 1

        public final boolean isWater;
        public final boolean isLeaves;
        public final boolean isTreeCanopy;
        public final boolean isGrassBlock;
        public final boolean isSnow;

        // biome-blended, RGB (no alpha)
        public final int baseColor;

        private SurfaceInfo(
                int x,
                int z,
                int surfaceY,
                int terrainY,
                int oceanFloorY,
                boolean isWater,
                boolean isLeaves,
                boolean isTreeCanopy,
                boolean isGrassBlock,
                boolean isSnow,
                int baseColor
        ) {
            this.x = x;
            this.z = z;
            this.surfaceY = surfaceY;
            this.terrainY = terrainY;
            this.oceanFloorY = oceanFloorY;
            this.isWater = isWater;
            this.isLeaves = isLeaves;
            this.isTreeCanopy = isTreeCanopy;
            this.isGrassBlock = isGrassBlock;
            this.isSnow = isSnow;
            this.baseColor = baseColor;
        }
    }

    public static SurfaceInfo sample(ClientWorld world, int x, int z) {
        // Heights
        int worldSurfaceTop = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        int surfaceY = Math.max(world.getBottomY(), worldSurfaceTop - 1);

        int motionTop =
                world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        int terrainY = Math.max(world.getBottomY(), motionTop - 1);

        int oceanTop = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);
        int oceanFloorY = Math.max(world.getBottomY(), oceanTop - 1);

        BlockPos surfacePos = new BlockPos(x, surfaceY, z);
        BlockState surface = world.getBlockState(surfacePos);

        boolean surfaceIsAir = surface.isAir();
        boolean isWater =
                (!surfaceIsAir && surface.getFluidState().isIn(FluidTags.WATER))
                        || surface.isOf(Blocks.WATER);

        boolean isLeaves = surface.isIn(BlockTags.LEAVES);
        boolean isTreeCanopy = isLeaves && (surfaceY - terrainY) >= 2;

        boolean isGrassBlock = surface.isOf(Blocks.GRASS_BLOCK);
        boolean isSnow =
                surface.isOf(Blocks.SNOW) || surface.isOf(Blocks.SNOW_BLOCK);

        // Robust visible block fallback (handles overhangs/odd columns)
        BlockState visibleState = surface;
        BlockPos visiblePos = surfacePos;
        if (surfaceIsAir || visibleIsUnrenderable(visibleState)) {
            // Walk down until we find something renderable, but not too deep
            int minY = Math.max(world.getBottomY(), surfaceY - 16);
            for (int y = surfaceY - 1; y >= minY; y--) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = world.getBlockState(p);
                if (!s.isAir() && !visibleIsUnrenderable(s)) {
                    visibleState = s;
                    visiblePos = p;
                    break;
                }
            }
        }

        // Biome-blended color choice
        int radius = getBiomeBlendRadiusSafe();
        int color;

        if (isWater) {
            color = blendedWaterColor(world, x, z, radius);
        } else if (isLeaves) {
            color = blendedFoliageColor(world, x, z, radius);
        } else if (isGrassBlock) {
            color = blendedGrassColor(world, x, z, radius);
        } else {
            // Try block color provider first
            color = -1;
            try {
                if (MC != null && MC.getBlockColors() != null) {
                    color = MC.getBlockColors().getColor(visibleState, world, visiblePos, 0);
                }
            } catch (Exception ignored) {}
            if (color == -1 || color == 0xFFFFFF) {
                MapColor mc = visibleState.getMapColor(world, visiblePos);
                color = (mc != MapColor.CLEAR) ? mc.getRenderColor(2) : 0x888888;
            }
        }

        // Slight separation tweaks (conservative; won’t cause lime)
        if (isLeaves) {
            color = adjustSV(color, 1.10f, 0.97f);
        } else if (isGrassBlock) {
            color = adjustSV(color, 1.03f, 1.02f);
        }

        return new SurfaceInfo(
                x,
                z,
                surfaceY,
                terrainY,
                oceanFloorY,
                isWater,
                isLeaves,
                isTreeCanopy,
                isGrassBlock,
                isSnow,
                color
        );
    }

    public static int waterDepth(ClientWorld world, SurfaceInfo s) {
        return Math.max(0, s.surfaceY - s.oceanFloorY);
    }

    public static int reliefHeightForShading(SurfaceInfo s) {
        return s.isWater ? s.oceanFloorY : s.terrainY;
    }

    // ---------- Biome blending ----------

    private static int blendedGrassColor(ClientWorld w, int x, int z, int r) {
        return averageColor(x, z, r, (xi, zi) -> BiomeColors.getGrassColor(w, new BlockPos(xi, 0, zi)));
    }

    private static int blendedFoliageColor(ClientWorld w, int x, int z, int r) {
        return averageColor(x, z, r, (xi, zi) -> BiomeColors.getFoliageColor(w, new BlockPos(xi, 0, zi)));
    }

    private static int blendedWaterColor(ClientWorld w, int x, int z, int r) {
        return averageColor(x, z, r, (xi, zi) -> BiomeColors.getWaterColor(w, new BlockPos(xi, 0, zi)));
    }

    @FunctionalInterface
    private interface ColorAt {
        int get(int x, int z);
    }

    private static int averageColor(int x, int z, int radius, ColorAt fn) {
        if (radius <= 0) return fn.get(x, z);

        long rs = 0, gs = 0, bs = 0, n = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int c = fn.get(x + dx, z + dz);
                rs += (c >> 16) & 0xFF;
                gs += (c >> 8) & 0xFF;
                bs += c & 0xFF;
                n++;
            }
        }
        int r = (int) (rs / n);
        int g = (int) (gs / n);
        int b = (int) (bs / n);
        return (r << 16) | (g << 8) | b;
    }

    private static int getBiomeBlendRadiusSafe() {
        try {
            // Works on recent Fabric mappings; fall back if signature differs
            return MC.options.getBiomeBlendRadius().getValue();
        } catch (Throwable t) {
            return 2; // sensible default
        }
    }

    // ---------- Shading helpers used by renderer ----------

    public static int applyDepthDarkening(int rgb, int depth) {
        if (depth <= 0) return rgb;
        float f = MathHelper.clamp(1.0f - (depth * 0.022f), 0.55f, 0.96f);
        return mul(rgb, f);
    }

    public static int applyHillshade(int rgb, float nx, float ny, float nz, float exaggeration) {
        float lx = -0.6f, ly = 0.8f, lz = -0.6f;
        float inv = 1.0f / (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx *= inv;
        ly *= inv;
        lz *= inv;

        float dot = nx * lx + ny * ly + nz * lz;
        float f = 1.0f + dot * 0.6f * exaggeration;
        f = MathHelper.clamp(f, 0.55f, 1.45f);
        return mul(rgb, f);
    }

    // Consistent one-block step cues (tiny, directional)
    public static int applyMicroStepShading(int rgb, int dyNorth, int dyWest) {
        float f = 1.0f;
        // Light from NW -> highlight if current is higher than north/west neighbor
        if (dyNorth > 0) f += 0.06f;
        if (dyWest > 0) f += 0.06f;
        if (dyNorth < 0) f -= 0.06f;
        if (dyWest < 0) f -= 0.06f;
        f = MathHelper.clamp(f, 0.8f, 1.2f);
        return mul(rgb, f);
    }

    public static int applyContour(int rgb, int height, int sea, int step) {
        int rel = height - sea;
        int mod = Math.floorMod(rel, step);
        if (mod == 0) return mul(rgb, 0.86f);
        if (mod == 1) return mul(rgb, 0.93f);
        return rgb;
    }

    public static int applyCanopyPattern(int rgb, int worldX, int worldZ, boolean canopy) {
        if (!canopy) return rgb;
        int h = hash(worldX * 734287 + worldZ * 912931);
        float v = 1.0f + (((h & 0x7) - 3) * 0.015f); // very subtle +/- ~4.5%
        return mul(rgb, v);
    }

    // ---------- internals ----------

    private static boolean visibleIsUnrenderable(BlockState s) {
        // Fluids, barrier-like, and certain non-opaque states can make map “holes”
        return s.getFluidState().isIn(FluidTags.WATER) || s.isAir();
    }

    private static int mul(int rgb, float f) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = MathHelper.clamp((int) (r * f), 0, 255);
        g = MathHelper.clamp((int) (g * f), 0, 255);
        b = MathHelper.clamp((int) (b * f), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private static int adjustSV(int rgb, float sat, float val) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float gray = (r + g + b) / 3f;

        // “saturation” by pushing away from gray
        r = gray + (r - gray) * sat;
        g = gray + (g - gray) * sat;
        b = gray + (b - gray) * sat;

        r = MathHelper.clamp(r * val, 0f, 1f);
        g = MathHelper.clamp(g * val, 0f, 1f);
        b = MathHelper.clamp(b * val, 0f, 1f);

        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static int hash(int x) {
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        return x;
    }

    private TerrainSampler() {}
}