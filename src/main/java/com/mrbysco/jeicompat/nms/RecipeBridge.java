package com.mrbysco.jeicompat.nms;

import com.mrbysco.jeicompat.config.PluginConfig;
import org.bukkit.entity.Player;

public interface RecipeBridge {
	record NeoForgePayload(byte[] recipeBytes, Object tagPacket) {
	}

	boolean isAvailable();

	int recipeCount();

	byte[] buildFabricPayload(PluginConfig config, RecipeFilterListener listener);

	NeoForgePayload buildNeoForgePayload(PluginConfig config, RecipeFilterListener listener);

	void sendFabric(Player player, byte[] payload);

	void sendFabricSyncFinished(Player player);

	void sendNeoForge(Player player, NeoForgePayload payload);
}
