package com.dexmap.data;

import net.minecraft.util.math.ChunkPos;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;

public class MapData {
    private final Map<ChunkPos, ChunkData> chunks = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ChunkPos> accessOrder = new ConcurrentLinkedQueue<>();
    private final int maxCachedChunks;

    public MapData() {
        this(1000); // Default cache size
    }

    public MapData(int maxCachedChunks) {
        this.maxCachedChunks = maxCachedChunks;
    }

    public void addChunk(ChunkData chunk) {
        ChunkPos pos = chunk.getPosition();

        // Add to cache
        chunks.put(pos, chunk);
        accessOrder.offer(pos);

        // Remove oldest entries if cache is full
        while (chunks.size() > maxCachedChunks) {
            ChunkPos oldest = accessOrder.poll();
            if (oldest != null) {
                chunks.remove(oldest);
            }
        }
    }

    public ChunkData getChunk(ChunkPos pos) {
        ChunkData data = chunks.get(pos);
        if (data != null) {
            // Update access order
            accessOrder.remove(pos);
            accessOrder.offer(pos);
        }
        return data;
    }

    public Map<ChunkPos, ChunkData> getAllChunks() {
        return chunks;
    }

    public boolean hasChunk(ChunkPos pos) {
        return chunks.containsKey(pos);
    }

    public void clear() {
        chunks.clear();
        accessOrder.clear();
    }

    public int getChunkCount() {
        return chunks.size();
    }
}