package com.mrbysco.jeicompat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class RecipeHandler implements Listener {
	private final RecipeSyncService syncService;

	public RecipeHandler(RecipeSyncService syncService) {
		this.syncService = syncService;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onJoin(PlayerJoinEvent event) {
		syncService.scheduleSync(event.getPlayer());
	}
}
