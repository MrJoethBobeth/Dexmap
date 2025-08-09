package com.dexmap.render;

import com.dexmap.DexmapClient;
import com.dexmap.data.ChunkData;
import com.dexmap.world.ChunkScanner;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class HudRenderer implements HudRenderCallback {
    private static final int MINIMAP_SIZE = 128;
    private static final int MINIMAP_MARGIN = 10;
    private static final int RENDER_RADIUS = 4;

    // Cache for rendered minimap sections
    private final Map<String, CachedMinimapSection> minimapCache = new ConcurrentHashMap<>();
    private ChunkPos lastPlayerChunk = null;
    private int framesSinceUpdate = 0;

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || client.world == null) return;
        if (client.options.hudHidden) return;

        DexmapClient dexmapClient = DexmapClient.getInstance();
        if (dexmapClient == null || !dexmapClient.getConfig().isMinimapEnabled()) return;

        // Only update every few frames to improve performance
        framesSinceUpdate++;
        if (framesSinceUpdate >= 5) { // Update every 5 frames
            framesSinceUpdate = 0;
            renderMinimap(drawContext, client.player, tickCounter);
        } else {
            // Just draw the cached version
            renderCachedMinimap(drawContext, client.player);
        }
    }

    private void renderMinimap(DrawContext context, PlayerEntity player, RenderTickCounter tickCounter) {
        ChunkPos playerChunk = new ChunkPos(player.getBlockPos());

        // Check if we need to update the cache
        if (!playerChunk.equals(lastPlayerChunk)) {
            updateMinimapCache(playerChunk);
            lastPlayerChunk = playerChunk;
        }

        renderCachedMinimap(context, player);
    }

    private void renderCachedMinimap(DrawContext context, PlayerEntity player) {
        int screenWidth = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int mapX = screenWidth - MINIMAP_SIZE - MINIMAP_MARGIN;
        int mapY = MINIMAP_MARGIN;

        // Draw background with border
        context.fill(mapX - 2, mapY - 2, mapX + MINIMAP_SIZE + 2, mapY + MINIMAP_SIZE + 2, 0xFF2C2C2C);
        context.fill(mapX - 1, mapY - 1, mapX + MINIMAP_SIZE + 1, mapY + MINIMAP_SIZE + 1, 0xFF000000);

        ChunkPos playerChunk = new ChunkPos(player.getBlockPos());
        ChunkScanner scanner = DexmapClient.getInstance().getChunkScanner();

        // Render chunks with high-resolution textures
        int chunkPixelSize = MINIMAP_SIZE / (RENDER_RADIUS * 2 + 1);

        // Enable texture filtering for smooth minimap
        RenderSystem.enableBlend();
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        RenderSystem.defaultBlendFunc();

        for (int chunkX = -RENDER_RADIUS; chunkX <= RENDER_RADIUS; chunkX++) {
            for (int chunkZ = -RENDER_RADIUS; chunkZ <= RENDER_RADIUS; chunkZ++) {
                ChunkPos chunkPos = new ChunkPos(playerChunk.x + chunkX, playerChunk.z + chunkZ);
                ChunkData chunkData = scanner.getChunkData(chunkPos);

                if (chunkData != null) {
                    int startX = mapX + (chunkX + RENDER_RADIUS) * chunkPixelSize;
                    int startY = mapY + (chunkZ + RENDER_RADIUS) * chunkPixelSize;

                    // Use high-resolution texture
                    Identifier textureId = chunkData.getTextureId();
                    context.drawTexture(textureId, startX, startY, 0, 0,
                            chunkPixelSize, chunkPixelSize, chunkPixelSize, chunkPixelSize);
                }
            }
        }

        RenderSystem.disableBlend();

        // Draw player dot with corrected direction
        drawPlayerIndicator(context, mapX, mapY, player);
    }

    private void drawPlayerIndicator(DrawContext context, int mapX, int mapY, PlayerEntity player) {
        int playerDotX = mapX + MINIMAP_SIZE / 2;
        int playerDotY = mapY + MINIMAP_SIZE / 2;

        // Player dot
        context.fill(playerDotX - 2, playerDotY - 2, playerDotX + 2, playerDotY + 2, 0xFF000000);
        context.fill(playerDotX - 1, playerDotY - 1, playerDotX + 1, playerDotY + 1, 0xFFFFFFFF);

        // Fixed direction arrow
        float yaw = player.getYaw();
        float correctedYaw = yaw + 180; // Fix coordinate system
        float radians = (float)Math.toRadians(correctedYaw);
        int dirX = (int)(Math.sin(radians) * 6);
        int dirY = (int)(-Math.cos(radians) * 6); // Negative cos for north-up

        // Draw arrow
        context.fill(playerDotX + dirX - 1, playerDotY + dirY - 1,
                playerDotX + dirX + 1, playerDotY + dirY + 1, 0xFFFFFF00);
    }

    private void updateMinimapCache(ChunkPos centerChunk) {
        // This method can be expanded to pre-cache surrounding chunks
        // For now, we rely on the individual chunk texture caching
    }

    private static class CachedMinimapSection {
        final NativeImageBackedTexture texture;
        final long lastUpdate;

        CachedMinimapSection(NativeImageBackedTexture texture) {
            this.texture = texture;
            this.lastUpdate = System.currentTimeMillis();
        }
    }
}