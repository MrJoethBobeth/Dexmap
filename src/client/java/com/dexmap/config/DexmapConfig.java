package com.dexmap.config;

public class DexmapConfig {
    // Minimap
    public boolean minimapEnabled = true;
    public int minimapSize = 128;
    public boolean minimapVisible = true; // keep for compatibility
    public MinimapPosition minimapPosition = MinimapPosition.TOP_RIGHT;
    public int minimapMargin = 10;

    // World map UI
    public boolean worldMapGrid = false;
    public boolean worldMapCoordinates = true;

    // Rendering (new)
    // Size of a chunk texture in pixels; must be a multiple of 16 (16, 32, 48, 64, 80, 96, 112, 128)
    public int textureResolution = 64;

    // Terrain shading
    public float heightExaggeration = 1.25f; // 0.6–2.0 typical
    public boolean microStepShading = true;  // per-block highlight/shadow
    public boolean contoursEnabled = true;
    public int contourStep = 8;              // blocks between contour lines

    // Water rendering
    public float waterDepthStrength = 1.0f;  // 0–2, higher = darker with depth

    // Canopy rendering
    public float canopyPatternStrength = 1.0f; // 0–2, 0 disables

    // Biome colors
    // -1 = use Minecraft’s own biome blend setting; 0..7 = override radius
    public int biomeBlendOverride = -1;

    // Filtering preference (optional; currently forcing nearest in screen)
    public boolean sharpZoomedIn = true;

    // Legacy / compatibility
    public float worldMapScale = 1.0f; // unused by new screen but kept to avoid breaking code
    public int chunkScanRadius = 8;    // unused since we don’t scan per-block in background
    public boolean asyncScanning = true;
    public int maxCachedChunks = 1000;

    public DexmapConfig() {}

    public enum MinimapPosition { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public boolean isMinimapEnabled() {
        // keep legacy behavior: enabled + visible
        return minimapEnabled && minimapVisible;
    }

    public void toggleMinimap() {
        minimapEnabled = !minimapEnabled;
    }
}