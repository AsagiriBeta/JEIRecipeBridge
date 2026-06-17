package com.mrbysco.jeicompat.listener;

import com.mrbysco.jeicompat.RecipeSyncService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemsAdderResourcePackListener implements Listener {
	private final RecipeSyncService syncService;
	private final Set<UUID> awaitingPack = ConcurrentHashMap.newKeySet();

	public ItemsAdderResourcePackListener(RecipeSyncService syncService) {
		this.syncService = syncService;
	}

	public void markAwaiting(Player player) {
		awaitingPack.add(player.getUniqueId());
	}

	public void cancelAwaiting(Player player) {
		awaitingPack.remove(player.getUniqueId());
	}

	public boolean isAwaiting(Player player) {
		return awaitingPack.contains(player.getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
		Player player = event.getPlayer();
		UUID playerId = player.getUniqueId();

		switch (event.getStatus()) {
			case SUCCESSFULLY_LOADED, DECLINED, FAILED_DOWNLOAD -> {
				awaitingPack.remove(playerId);
				syncService.syncTo(player);
			}
			default -> {
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		awaitingPack.remove(event.getPlayer().getUniqueId());
	}
}
