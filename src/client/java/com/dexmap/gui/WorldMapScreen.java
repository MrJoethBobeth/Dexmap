package com.dexmap.gui;

import com.dexmap.DexmapClient;
import com.dexmap.data.ChunkData;
import com.dexmap.world.ChunkScanner;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Objects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

public class WorldMapScreen extends Screen {
    // Visual
    private float mapScale = 4.0f; // multiplier over 1 block = 1 px (16 px per chunk)
    private float mapOffsetX = 0f;
    private float mapOffsetZ = 0f;
    private boolean showCoordinates = true;
    private boolean showGrid = false;

    // Cache to avoid redundant filter calls
    private Identifier lastBoundForFilter = null;

    public WorldMapScreen() {
        super(Text.literal("World Map"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        if (client == null || client.player == null || client.world == null) {
            super.render(ctx, mouseX, mouseY, delta);
            return;
        }

        final var cfg = DexmapClient.getInstance().getConfig();
        final ChunkScanner scanner = DexmapClient.getInstance().getChunkScanner();
        final ClientWorld world = client.world;
        final ChunkPos playerChunk = new ChunkPos(client.player.getBlockPos());

        // Center of the screen in GUI coords
        final int centerX = width / 2;
        final int centerY = height / 2;

        // Background panel
        ctx.fill(0, 0, width, height, 0xFF121212);

        // Chunk size in screen pixels at current zoom
        final float chunkPx = 16f * mapScale;

        // Compute how many chunks we need around the center
        final int viewRadius = (int) Math.ceil(
                Math.max(width, height) / chunkPx
        ) + 2;

        // Draw visible chunks
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Loop only the chunks that can appear on screen
        for (int dx = -viewRadius; dx <= viewRadius; dx++) {
            for (int dz = -viewRadius; dz <= viewRadius; dz++) {
                final int drawX = (int) (centerX + (dx * chunkPx) + mapOffsetX);
                final int drawY = (int) (centerY + (dz * chunkPx) + mapOffsetZ);

                // Cull offscreen chunks early
                if (
                        drawX > width || drawY > height || drawX + chunkPx < 0 || drawY + chunkPx < 0
                ) {
                    continue;
                }

                final ChunkPos cpos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                final ChunkData cdata = scanner.getChunkData(cpos);
                if (cdata == null) continue;

                drawChunkTexture(ctx, world, cdata, drawX, drawY, (int) chunkPx);
            }
        }

        // Grid
        if (showGrid && mapScale >= 1.0f) {
            drawGrid(ctx, centerX, centerY, chunkPx, viewRadius);
        }

        // Player indicator with corrected direction
        drawPlayerIndicator(ctx, centerX, centerY);

        // HUD text
        drawUI(ctx);

        RenderSystem.disableBlend();
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawChunkTexture(
            DrawContext ctx,
            ClientWorld world,
            ChunkData chunk,
            int x,
            int y,
            int sizeOnScreen
    ) {
        final var cfg = DexmapClient.getInstance().getConfig();

        // Get or build the texture
        Identifier tex = chunk.getTextureId(world);

        // Bind and force nearest-neighbor filtering to avoid blur
        // Also pass the true texture size so UVs are exact (prevents sampling blur)
        bindTextureWithNearest(tex);

        final int texSize = Math.max(16, cfg.textureResolution);
        ctx.drawTexture(tex, x, y, 0, 0, sizeOnScreen, sizeOnScreen, texSize, texSize);
    }

    private void bindTextureWithNearest(Identifier id) {
        // Avoid redundant texParameter calls if same texture bound consecutively
        if (!Objects.equals(lastBoundForFilter, id)) {
            RenderSystem.setShaderTexture(0, id);
            GL11.glTexParameteri(
                    GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER,
                    GL11.GL_NEAREST
            );
            GL11.glTexParameteri(
                    GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER,
                    GL11.GL_NEAREST
            );
            lastBoundForFilter = id;
        }
    }

    private void drawGrid(
            DrawContext ctx,
            int centerX,
            int centerY,
            float chunkPx,
            int viewRadius
    ) {
        final int color = 0x30FFFFFF;

        for (int dx = -viewRadius; dx <= viewRadius; dx++) {
            int x = (int) (centerX + (dx * chunkPx) + mapOffsetX);
            if (x >= 0 && x <= width) {
                ctx.drawVerticalLine(x, 0, height, color);
            }
        }

        for (int dz = -viewRadius; dz <= viewRadius; dz++) {
            int y = (int) (centerY + (dz * chunkPx) + mapOffsetZ);
            if (y >= 0 && y <= height) {
                ctx.drawHorizontalLine(0, width, y, color);
            }
        }
    }

    private void drawPlayerIndicator(DrawContext ctx, int centerX, int centerY) {
        if (client == null || client.player == null) return;

        final int px = (int) (centerX + mapOffsetX);
        final int py = (int) (centerY + mapOffsetZ);

        final int size = Math.max(2, (int) (3 * Math.min(mapScale, 2.0f)));

        // outline
        ctx.fill(px - size - 1, py - size - 1, px + size + 1, py + size + 1, 0xFF000000);
        // dot
        ctx.fill(px - size, py - size, px + size, py + size, 0xFFFFFFFF);

        // Correct direction (north-up). MC yaw: 0=S, 90=W, 180=N, 270=E.
        // Convert to map: 0=N, 90=E, 180=S, 270=W.
        float correctedYaw = client.player.getYaw() + 180.0f;
        float rad = (float) Math.toRadians(correctedYaw);
        int dx = (int) (Math.sin(rad) * (size + 5));
        int dy = (int) (-Math.cos(rad) * (size + 5));

        ctx.fill(px + dx - 1, py + dy - 1, px + dx + 1, py + dy + 1, 0xFFFFFF00);
    }

    private void drawUI(DrawContext ctx) {
        final var tr = this.textRenderer;
        final var cfg = DexmapClient.getInstance().getConfig();

        ctx.drawTextWithShadow(tr, String.format("Scale: %.2fx", mapScale), 10, 10, 0xFFFFFFFF);

        if (showCoordinates && client != null && client.player != null) {
            int x = client.player.getBlockPos().getX();
            int z = client.player.getBlockPos().getZ();
            ctx.drawTextWithShadow(tr, "Position: " + x + ", " + z, 10, 25, 0xFFFFFFFF);
        }

        int chunks = DexmapClient.getInstance().getChunkScanner().getMapData().getChunkCount();
        ctx.drawTextWithShadow(tr, "Chunks: " + chunks, 10, 40, 0xFFFFFFFF);

        int ly = height - 48;
        ctx.drawTextWithShadow(tr, "Mouse wheel: Zoom", 10, ly, 0xFFCCCCCC);
        ctx.drawTextWithShadow(tr, "Drag: Pan", 10, ly + 12, 0xFFCCCCCC);
        ctx.drawTextWithShadow(tr, "G: Grid | C: Coords | R: Reset", 10, ly + 24, 0xFFCCCCCC);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        float old = mapScale;
        mapScale = MathHelper.clamp(mapScale + (float) verticalAmount * 0.5f, 0.25f, 16.0f);

        // Zoom towards cursor
        if (mapScale != old) {
            float s = mapScale / old;
            float mx = (float) mouseX - width / 2f;
            float my = (float) mouseY - height / 2f;
            mapOffsetX = (mapOffsetX - mx) * s + mx;
            mapOffsetZ = (mapOffsetZ - my) * s + my;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY
    ) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            mapOffsetX += (float) deltaX;
            mapOffsetZ += (float) deltaY;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_G -> {
                showGrid = !showGrid;
                return true;
            }
            case GLFW.GLFW_KEY_C -> {
                showCoordinates = !showCoordinates;
                return true;
            }
            case GLFW.GLFW_KEY_R -> {
                resetView();
                return true;
            }
            default -> {}
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void resetView() {
        mapScale = 4.0f;
        mapOffsetX = 0f;
        mapOffsetZ = 0f;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}