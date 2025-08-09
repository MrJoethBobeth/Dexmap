package com.dexmap.world;

import com.dexmap.Dexmap;
import com.dexmap.data.ChunkData;
import com.dexmap.data.MapData;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

public class ChunkScanner {
    private final Map<ChunkPos, ChunkData> scannedChunks = new ConcurrentHashMap<>();
    private final MapData mapData = new MapData();
    private final Set<ChunkPos> loading = ConcurrentHashMap.newKeySet();

    private PlayerEntity lastPlayer;

    public void onChunkLoad(ClientWorld world, WorldChunk chunk) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || world != client.world) return;

        ChunkPos pos = chunk.getPos();
        if (scannedChunks.containsKey(pos) || !loading.add(pos)) return;

        try {
            ChunkData data = new ChunkData(pos);
            // optionally warm texture so first draw is instant:
            // data.getTextureId(world);

            scannedChunks.put(pos, data);
            mapData.addChunk(data);
        } catch (Exception e) {
            Dexmap.LOGGER.error("Failed to add chunk {}: {}", pos, e.getMessage());
        } finally {
            loading.remove(pos);
        }
    }

    public void onChunkUnload(ClientWorld world, WorldChunk chunk) {
        ChunkPos pos = chunk.getPos();
        ChunkData data = scannedChunks.get(pos);
        if (data == null) return;

        if (lastPlayer != null) {
            ChunkPos pc = new ChunkPos(lastPlayer.getBlockPos());
            int dist = Math.abs(pos.x - pc.x) + Math.abs(pos.z - pc.z);
            if (dist > 10) data.dispose();
        }
        // keep entry for map history; remove if you want:
        // scannedChunks.remove(pos);
    }

    public void updatePlayerPosition(PlayerEntity player) {
        this.lastPlayer = player;
    }

    public ChunkData getChunkData(ChunkPos pos) {
        return scannedChunks.get(pos);
    }

    public MapData getMapData() {
        return mapData;
    }

    public void cleanup() {
        scannedChunks.values().forEach(ChunkData::dispose);
        scannedChunks.clear();
        mapData.clear();
        loading.clear();
    }
}