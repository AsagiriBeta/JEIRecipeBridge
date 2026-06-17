package com.mrbysco.jeicompat;

import com.mrbysco.jeicompat.nms.RecipeBridge;
import com.mrbysco.jeicompat.sync.ClientBrand;
import com.mrbysco.jeicompat.sync.RecipePayloadCache;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class RecipeSyncService {
	private static final int SYNC_DELAY_TICKS = 1;
	private static final int RETRY_DELAY_TICKS = 20;

	private final Plugin plugin;
	private final RecipeBridge bridge;
	private final RecipePayloadCache payloadCache;
	private List<NamespacedKey> recipeKeys = List.of();

	public RecipeSyncService(Plugin plugin, RecipeBridge bridge, RecipePayloadCache payloadCache) {
		this.plugin = plugin;
		this.bridge = bridge;
		this.payloadCache = payloadCache;
	}

	public void refreshRecipeKeys() {
		List<NamespacedKey> keys = new ArrayList<>();
		for (Recipe recipe : iteratorToIterable(Bukkit.recipeIterator())) {
			if (recipe instanceof Keyed keyed) {
				keys.add(keyed.getKey());
			}
		}
		recipeKeys = List.copyOf(keys);
	}

	public void invalidateCache() {
		payloadCache.invalidate();
	}

	public void scheduleSync(Player player) {
		if (!plugin.isEnabled()) {
			return;
		}

		ClientBrand brand = ClientBrand.fromBrand(player.getClientBrandName());
		if (!brand.isSupported()) {
			return;
		}

		player.getScheduler().runDelayed(plugin, task -> attemptSync(player), null, SYNC_DELAY_TICKS);
	}

	public void attemptSync(Player player) {
		if (!plugin.isEnabled()) {
			return;
		}

		if (!syncTo(player)) {
			player.getScheduler().runDelayed(plugin, task -> syncTo(player), null, RETRY_DELAY_TICKS);
		}
	}

	public boolean syncTo(Player player) {
		if (!player.isOnline() || !plugin.isEnabled() || !bridge.isAvailable()) {
			return false;
		}

		ClientBrand brand = ClientBrand.fromBrand(player.getClientBrandName());
		if (!brand.isSupported()) {
			return false;
		}

		try {
			if (!recipeKeys.isEmpty()) {
				player.discoverRecipes(recipeKeys);
			}

			switch (brand) {
				case FABRIC -> {
					byte[] payload = payloadCache.fabricPayload();
					if (payload.length == 0) {
						return false;
					}
					bridge.sendFabric(player, payload);
					bridge.sendFabricSyncFinished(player);
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

	private static Iterable<Recipe> iteratorToIterable(Iterator<Recipe> iterator) {
		return () -> iterator;
	}
}
