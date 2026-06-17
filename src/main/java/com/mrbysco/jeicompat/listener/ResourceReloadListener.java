package com.mrbysco.jeicompat.listener;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import com.mrbysco.jeicompat.RecipeSyncService;
import com.mrbysco.jeicompat.config.PluginConfig;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.function.Supplier;

public final class ResourceReloadListener implements Listener {
	private final JEIRecipeBridgePlugin plugin;
	private final RecipeSyncService syncService;
	private final Supplier<PluginConfig> config;

	public ResourceReloadListener(
			JEIRecipeBridgePlugin plugin,
			RecipeSyncService syncService,
			Supplier<PluginConfig> config) {
		this.plugin = plugin;
		this.syncService = syncService;
		this.config = config;
	}

	@EventHandler
	public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
		plugin.refreshRecipeContent();
		if (config.get().syncOnDatapackReload()) {
			syncService.resyncAll();
		}
	}
}
