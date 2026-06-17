package com.mrbysco.jeicompat;

import com.mrbysco.jeicompat.compat.itemsadder.ItemsAdderBridge;
import com.mrbysco.jeicompat.config.PluginConfig;
import com.mrbysco.jeicompat.listener.ItemsAdderResourcePackListener;
import com.mrbysco.jeicompat.nms.RecipeBridge;
import com.mrbysco.jeicompat.sync.ClientBrand;
import com.mrbysco.jeicompat.sync.RecipeDiscoveryService;
import com.mrbysco.jeicompat.sync.RecipePayloadCache;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

public final class RecipeSyncService {
	private final Plugin plugin;
	private final Supplier<PluginConfig> config;
	private final RecipeBridge bridge;
	private final RecipePayloadCache payloadCache;
	private final RecipeDiscoveryService recipeDiscoveryService;
	private final ItemsAdderBridge itemsAdderBridge;
	private ItemsAdderResourcePackListener resourcePackListener;

	public RecipeSyncService(
			Plugin plugin,
			Supplier<PluginConfig> config,
			RecipeBridge bridge,
			RecipePayloadCache payloadCache,
			RecipeDiscoveryService recipeDiscoveryService,
			ItemsAdderBridge itemsAdderBridge) {
		this.plugin = plugin;
		this.config = config;
		this.bridge = bridge;
		this.payloadCache = payloadCache;
		this.recipeDiscoveryService = recipeDiscoveryService;
		this.itemsAdderBridge = itemsAdderBridge;
	}

	public void setResourcePackListener(ItemsAdderResourcePackListener resourcePackListener) {
		this.resourcePackListener = resourcePackListener;
	}

	public void scheduleSync(Player player) {
		PluginConfig currentConfig = config.get();
		if (!currentConfig.enabled() || !currentConfig.syncOnJoin() || !plugin.isEnabled()) {
			return;
		}

		if (currentConfig.itemsAdderEnabled() && currentConfig.itemsAdderApplyResourcePack()) {
			itemsAdderBridge.applyResourcePack(player);
		}

		if (shouldWaitForResourcePack(currentConfig)) {
			resourcePackListener.markAwaiting(player);
			player.getScheduler().runDelayed(
					plugin,
					task -> {
						if (resourcePackListener.isAwaiting(player)) {
							resourcePackListener.cancelAwaiting(player);
							attemptSync(player);
						}
					},
					null,
					currentConfig.itemsAdderResourcePackWaitTicks()
			);
			return;
		}

		int delay = currentConfig.syncDelayTicks();
		if (delay <= 0) {
			attemptSync(player);
			return;
		}

		player.getScheduler().runDelayed(plugin, task -> attemptSync(player), null, delay);
	}

	public void attemptSync(Player player) {
		if (!plugin.isEnabled()) {
			return;
		}

		if (!syncTo(player) && config.get().retryOnFailedSync()) {
			player.getScheduler().runDelayed(
					plugin,
					task -> syncTo(player),
					null,
					config.get().syncRetryDelayTicks()
			);
		}
	}

	public boolean syncTo(Player player) {
		if (!player.isOnline() || !plugin.isEnabled()) {
			return false;
		}

		PluginConfig currentConfig = config.get();
		if (!currentConfig.enabled() || !bridge.isAvailable()) {
			return false;
		}

		ClientBrand brand = ClientBrand.fromBrand(player.getClientBrandName());
		if (!brand.isSupported()) {
			if (currentConfig.debug()) {
				JEIRecipeBridgePlugin.LOGGER.debug(
						"Skipping recipe sync for {}: unsupported client brand",
						player.getName()
				);
			}
			return false;
		}

		try {
			recipeDiscoveryService.discoverRecipes(player);

			if (currentConfig.notifyPlayer()) {
				player.sendMessage("§6JEI Recipe Bridge: Syncing recipes...§r");
			}

			switch (brand) {
				case FABRIC -> {
					byte[] payload = payloadCache.fabricPayload();
					if (payload.length == 0) {
						return false;
					}
					bridge.sendFabric(player, payload);
					if (currentConfig.fabricSendSyncFinished()) {
						bridge.sendFabricSyncFinished(player);
					}
				}
				case NEOFORGE -> {
					RecipeBridge.NeoForgePayload payload = payloadCache.neoForgePayload();
					if (payload.recipeBytes().length == 0) {
						return false;
					}
					bridge.sendNeoForge(player, payload);
				}
				default -> {
					return false;
				}
			}

			if (currentConfig.debug()) {
				JEIRecipeBridgePlugin.LOGGER.debug("Synced recipes to {} ({})", player.getName(), brand);
			}
			return true;
		} catch (Exception exception) {
			JEIRecipeBridgePlugin.LOGGER.error("Failed to sync recipes for {}", player.getName(), exception);
			return false;
		}
	}

	public void resyncAll() {
		if (!plugin.isEnabled()) {
			return;
		}

		for (Player player : Bukkit.getOnlinePlayers()) {
			player.getScheduler().run(plugin, task -> syncTo(player), null);
		}
	}

	public void invalidateCache() {
		payloadCache.invalidate();
	}

	private boolean shouldWaitForResourcePack(PluginConfig currentConfig) {
		return currentConfig.itemsAdderEnabled()
				&& currentConfig.itemsAdderApplyResourcePack()
				&& currentConfig.itemsAdderWaitForResourcePack()
				&& resourcePackListener != null;
	}
}
