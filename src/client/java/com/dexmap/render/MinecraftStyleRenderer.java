package com.dexmap.render;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;

public class MinecraftStyleRenderer {
    // 4 px per block; raise to 8 for ultra detail (costs VRAM/CPU)
    private static final int CHUNK_TEXTURE_SIZE = 64;
    private static final int BLOCK_PX = 4;

    // Shading tuning
    private static final float HEIGHT_EXAGGERATION = 1.25f;
    private static final int CONTOUR_STEP = 8;

    public static NativeImageBackedTexture createMinecraftStyleTexture(
            ChunkPos chunkPos, ClientWorld world
    ) {
        NativeImage img = new NativeImage(CHUNK_TEXTURE_SIZE, CHUNK_TEXTURE_SIZE, false);

        int[][] shadeH = new int[16][16];
        TerrainSampler.SurfaceInfo[][] info = new TerrainSampler.SurfaceInfo[16][16];

        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int wx = chunkPos.getStartX() + bx;
                int wz = chunkPos.getStartZ() + bz;
                TerrainSampler.SurfaceInfo s = TerrainSampler.sample(world, wx, wz);
                info[bx][bz] = s;
                shadeH[bx][bz] = TerrainSampler.reliefHeightForShading(s);
            }
        }

        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int wx = chunkPos.getStartX() + bx;
                int wz = chunkPos.getStartZ() + bz;
                TerrainSampler.SurfaceInfo s = info[bx][bz];

                // Neighbor heights for hillshade normal
                int hL = getH(shadeH, bx - 1, bz);
                int hR = getH(shadeH, bx + 1, bz);
                int hN = getH(shadeH, bx, bz - 1);
                int hS = getH(shadeH, bx, bz + 1);

                float dx = (hR - hL) * 0.5f;
                float dz = (hS - hN) * 0.5f;
                float nx = -dx, ny = 2.0f, nz = -dz;
                float inv =
                        1.0f / (float) Math.max(Math.sqrt(nx * nx + ny * ny + nz * nz), 1e-5);
                nx *= inv;
                ny *= inv;
                nz *= inv;

                int rgb = s.baseColor;

                // Water depth darkening
                if (s.isWater) {
                    int depth = TerrainSampler.waterDepth(world, s);
                    rgb = TerrainSampler.applyDepthDarkening(rgb, depth);
                }

                // Hillshade
                rgb = TerrainSampler.applyHillshade(rgb, nx, ny, nz, HEIGHT_EXAGGERATION);

                // Micro step shading (consistent per-block cue)
                int dyNorth = shadeH[bx][bz] - hN;
                int dyWest = shadeH[bx][bz] - hL;
                rgb = TerrainSampler.applyMicroStepShading(rgb, dyNorth, dyWest);

                // Contours
                rgb =
                        TerrainSampler.applyContour(
                                rgb, shadeH[bx][bz], world.getSeaLevel(), CONTOUR_STEP
                        );

                // Tree canopy subtle pattern; no pattern on grass/ground (removes dotted look)
                rgb = TerrainSampler.applyCanopyPattern(rgb, wx, wz, s.isTreeCanopy);

                drawBlock(img, bx, bz, rgb, s);
            }
        }

        return new NativeImageBackedTexture(img);
    }

    private static int getH(int[][] h, int x, int z) {
        x = Math.max(0, Math.min(15, x));
        z = Math.max(0, Math.min(15, z));
        return h[x][z];
    }

    private static void drawBlock(
            NativeImage img, int bx, int bz, int rgb, TerrainSampler.SurfaceInfo s
    ) {
        // Convert to ABGR once
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        int sx = bx * BLOCK_PX;
        int sz = bz * BLOCK_PX;

        // No per-pixel noise on grass/ground to avoid dotted “lime”; keep very
        // slight variation for canopy/water to avoid banding.
        boolean subtle = s.isTreeCanopy || s.isWater;

        for (int px = 0; px < BLOCK_PX; px++) {
            for (int pz = 0; pz < BLOCK_PX; pz++) {
                float v = subtle ? 1.0f + (((px + pz) % 3) - 1) * 0.012f : 1.0f;
                int rr = Math.max(0, Math.min(255, (int) (r * v)));
                int gg = Math.max(0, Math.min(255, (int) (g * v)));
                int bb = Math.max(0, Math.min(255, (int) (b * v)));
                int abgr = 0xFF000000 | (bb << 16) | (gg << 8) | rr;
                img.setColor(sx + px, sz + pz, abgr);
            }
        }
    }
}