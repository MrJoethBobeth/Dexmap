package com.dexmap.data;

import com.dexmap.render.MinecraftStyleRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

public class ChunkData {
    private final ChunkPos position;
    private NativeImageBackedTexture cachedTexture;
    private final Identifier textureId;
    private boolean textureReady = false;

    public ChunkData(ChunkPos position) {
        this.position = position;
        this.textureId =
                Identifier.of("dexmap", "chunk_" + position.x + "_" + position.z);
    }

    public Identifier getTextureId(ClientWorld world) {
        if (!textureReady && world != null) {
            buildTexture(world);
        }
        return textureId;
    }

    // Convenience no-arg overload to fix HudRenderer call sites
    public Identifier getTextureId() {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (!textureReady && world != null) {
            buildTexture(world);
        }
        return textureId;
    }

    private void buildTexture(ClientWorld world) {
        if (cachedTexture != null) {
            cachedTexture.close();
            cachedTexture = null;
        }
        cachedTexture =
                MinecraftStyleRenderer.createMinecraftStyleTexture(position, world);
        MinecraftClient.getInstance()
                .getTextureManager()
                .registerTexture(textureId, cachedTexture);
        textureReady = true;
    }

    public void invalidateTexture() {
        textureReady = false;
    }

    public void dispose() {
        if (cachedTexture != null) {
            cachedTexture.close();
            cachedTexture = null;
        }
        textureReady = false;
    }

    public ChunkPos getPosition() {
        return position;
    }
}