package com.mrbysco.jeicompat.sync;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import com.mrbysco.jeicompat.config.PluginConfig;
import com.mrbysco.jeicompat.nms.RecipeBridge;
import com.mrbysco.jeicompat.nms.RecipeFilterListener;
import com.mrbysco.jeicompat.util.Lazy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class RecipePayloadCache {
	private final Supplier<PluginConfig> config;
	private final RecipeBridge bridge;
	private final Lazy<byte[]> fabricPayload = new Lazy<>(this::buildFabricPayload);
	private final Lazy<RecipeBridge.NeoForgePayload> neoForgePayload = new Lazy<>(this::buildNeoForgePayload);

	public RecipePayloadCache(Supplier<PluginConfig> config, RecipeBridge bridge) {
		this.config = config;
		this.bridge = bridge;
	}

	public byte[] fabricPayload() {
		return fabricPayload.get();
	}

	public RecipeBridge.NeoForgePayload neoForgePayload() {
		return neoForgePayload.get();
	}

	public void invalidate() {
		fabricPayload.invalidate();
		neoForgePayload.invalidate();
	}

	private byte[] buildFabricPayload() {
		AtomicInteger filteredCount = new AtomicInteger();
		RecipeFilterListener listener = buildListener(filteredCount);
		byte[] payload = bridge.buildFabricPayload(config.get(), listener);
		logFiltered(filteredCount.get(), "Fabric");
		return payload;
	}

	private RecipeBridge.NeoForgePayload buildNeoForgePayload() {
		AtomicInteger filteredCount = new AtomicInteger();
		RecipeFilterListener listener = buildListener(filteredCount);
		RecipeBridge.NeoForgePayload payload = bridge.buildNeoForgePayload(config.get(), listener);
		logFiltered(filteredCount.get(), "NeoForge");
		return payload;
	}

	private RecipeFilterListener buildListener(AtomicInteger filteredCount) {
		PluginConfig currentConfig = config.get();
		return (recipeId, reason) -> {
			filteredCount.incrementAndGet();
			if (currentConfig.logFilteredRecipes() || currentConfig.debug()) {
				JEIRecipeBridgePlugin.LOGGER.info("Skipping recipe {} while building payload: {}", recipeId, reason);
			}
		};
	}

	private void logFiltered(int filteredCount, String loader) {
		PluginConfig currentConfig = config.get();
		if (filteredCount > 0 && currentConfig.logFilteredRecipes()) {
			JEIRecipeBridgePlugin.LOGGER.info("Built {} payload; filtered {} recipe(s)", loader, filteredCount);
		} else if (currentConfig.debug()) {
			JEIRecipeBridgePlugin.LOGGER.debug("Built {} payload", loader);
		}
	}
}
