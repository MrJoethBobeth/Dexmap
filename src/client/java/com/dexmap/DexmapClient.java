package com.dexmap;

import com.dexmap.config.DexmapConfig;
import com.dexmap.input.KeybindingManager;
import com.dexmap.render.HudRenderer;
import com.dexmap.world.ChunkScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class DexmapClient implements ClientModInitializer {
	private static DexmapClient instance;
	private ChunkScanner chunkScanner;
	private HudRenderer hudRenderer;
	private DexmapConfig config;

	@Override
	public void onInitializeClient() {
		instance = this;

		// Initialize components
		config = new DexmapConfig();
		chunkScanner = new ChunkScanner();
		hudRenderer = new HudRenderer();

		// Register keybindings
		KeybindingManager.register();

		// Register event callbacks
		registerEvents();

		Dexmap.LOGGER.info("Dexmap client initialized!");
	}

	private void registerEvents() {
		// Chunk loading events for scanning terrain
		ClientChunkEvents.CHUNK_LOAD.register(chunkScanner::onChunkLoad);
		ClientChunkEvents.CHUNK_UNLOAD.register(chunkScanner::onChunkUnload);

		// Tick events for updates
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null) {
				chunkScanner.updatePlayerPosition(client.player);
			}
		});

		// HUD rendering using the standard HudRenderCallback
		HudRenderCallback.EVENT.register(hudRenderer::onHudRender);
	}

	public static DexmapClient getInstance() {
		return instance;
	}

	public ChunkScanner getChunkScanner() {
		return chunkScanner;
	}

	public DexmapConfig getConfig() {
		return config;
	}
}