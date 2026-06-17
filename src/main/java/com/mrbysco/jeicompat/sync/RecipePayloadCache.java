package com.mrbysco.jeicompat.sync;

import com.mrbysco.jeicompat.nms.RecipeBridge;
import com.mrbysco.jeicompat.util.Lazy;

public final class RecipePayloadCache {
	private final RecipeBridge bridge;
	private final Lazy<byte[]> fabricPayload;
	private final Lazy<RecipeBridge.NeoForgePayload> neoForgePayload;

	public RecipePayloadCache(RecipeBridge bridge) {
		this.bridge = bridge;
		this.fabricPayload = new Lazy<>(bridge::buildFabricPayload);
		this.neoForgePayload = new Lazy<>(bridge::buildNeoForgePayload);
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
}
