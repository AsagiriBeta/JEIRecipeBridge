package com.mrbysco.jeicompat.listener;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import com.mrbysco.jeicompat.RecipeSyncService;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class ResourceReloadListener implements Listener {
	private final JEIRecipeBridgePlugin plugin;
	private final RecipeSyncService syncService;

	public ResourceReloadListener(JEIRecipeBridgePlugin plugin, RecipeSyncService syncService) {
		this.plugin = plugin;
		this.syncService = syncService;
	}

	@EventHandler
	public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
		plugin.refreshRecipeCache();
		syncService.resyncAll();
	}
}
