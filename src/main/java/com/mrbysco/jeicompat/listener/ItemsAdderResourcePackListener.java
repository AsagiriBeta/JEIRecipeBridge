package com.mrbysco.jeicompat.listener;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import com.mrbysco.jeicompat.RecipeSyncService;
import com.mrbysco.jeicompat.config.PluginConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Re-syncs recipes after the ItemsAdder resource pack is accepted so JEI can render IA models.
 */
public final class ItemsAdderResourcePackListener implements Listener {
	private final RecipeSyncService syncService;
	private final Supplier<PluginConfig> config;
	private final Set<UUID> awaitingPack = ConcurrentHashMap.newKeySet();
	private final Set<UUID> packReady = ConcurrentHashMap.newKeySet();

	public ItemsAdderResourcePackListener(RecipeSyncService syncService, Supplier<PluginConfig> config) {
		this.syncService = syncService;
		this.config = config;
	}

	public void markAwaiting(Player player) {
		awaitingPack.add(player.getUniqueId());
	}

	public void onItemsAdderPackSent(Player player) {
		if (!config.get().itemsAdderEnabled() || !config.get().itemsAdderWaitForResourcePack()) {
			return;
		}

		markAwaiting(player);
		if (config.get().debug()) {
			JEIRecipeBridgePlugin.LOGGER.debug(
					"ItemsAdder resource pack sent to {}; waiting for client load",
					player.getName()
			);
		}
	}

	public void cancelAwaiting(Player player) {
		awaitingPack.remove(player.getUniqueId());
	}

	public boolean isAwaiting(Player player) {
		return awaitingPack.contains(player.getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
		PluginConfig currentConfig = config.get();
		if (!currentConfig.itemsAdderEnabled() || !currentConfig.itemsAdderWaitForResourcePack()) {
			return;
		}

		Player player = event.getPlayer();
		UUID playerId = player.getUniqueId();

		switch (event.getStatus()) {
			case SUCCESSFULLY_LOADED -> {
				awaitingPack.remove(playerId);
				packReady.add(playerId);
				if (currentConfig.debug()) {
					JEIRecipeBridgePlugin.LOGGER.debug(
							"ItemsAdder resource pack loaded for {}; re-syncing recipes",
							player.getName()
					);
				}
				syncService.syncTo(player);
			}
			case DECLINED, FAILED_DOWNLOAD -> {
				awaitingPack.remove(playerId);
				JEIRecipeBridgePlugin.LOGGER.info(
						"{} did not load the ItemsAdder resource pack ({}). IA items may look like vanilla paper in JEI.",
						player.getName(),
						event.getStatus()
				);
				syncService.syncTo(player);
			}
			default -> {
				// DOWNLOADED and other intermediate states are ignored.
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		UUID playerId = event.getPlayer().getUniqueId();
		awaitingPack.remove(playerId);
		packReady.remove(playerId);
	}
}
