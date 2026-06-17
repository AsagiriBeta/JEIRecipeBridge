package com.mrbysco.jeicompat.nms;

import org.bukkit.entity.Player;

public interface RecipeBridge {
	record NeoForgePayload(byte[] recipeBytes, Object tagPacket) {
	}

	boolean isAvailable();

	int recipeCount();

	byte[] buildFabricPayload();

	NeoForgePayload buildNeoForgePayload();

	void sendFabric(Player player, byte[] payload);

	void sendFabricSyncFinished(Player player);

	void sendNeoForge(Player player, NeoForgePayload payload);
}
