package com.mrbysco.jeicompat.sync;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import com.mrbysco.jeicompat.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class RecipeDiscoveryService {
	private final Supplier<PluginConfig> config;
	private List<NamespacedKey> cachedRecipeKeys = List.of();

	public RecipeDiscoveryService(Supplier<PluginConfig> config) {
		this.config = config;
	}

	public void refreshRecipeKeys() {
		Set<String> blacklist = config.get().recipeBlacklist();
		List<NamespacedKey> keys = new ArrayList<>();

		for (Recipe recipe : iteratorToIterable(Bukkit.recipeIterator())) {
			if (!(recipe instanceof Keyed keyed)) {
				continue;
			}

			NamespacedKey key = keyed.getKey();
			if (blacklist.contains(key.toString())) {
				continue;
			}

			keys.add(key);
		}

		cachedRecipeKeys = List.copyOf(keys);
		if (config.get().debug()) {
			JEIRecipeBridgePlugin.LOGGER.debug("Cached {} recipe key(s) for discoverRecipes()", cachedRecipeKeys.size());
		}
	}

	public void discoverRecipes(Player player) {
		if (!config.get().discoverRecipesOnJoin() || cachedRecipeKeys.isEmpty()) {
			return;
		}

		player.discoverRecipes(cachedRecipeKeys);
		if (config.get().debug()) {
			JEIRecipeBridgePlugin.LOGGER.debug(
					"Discovered {} recipe(s) for {}",
					cachedRecipeKeys.size(),
					player.getName()
			);
		}
	}

	private static Iterable<Recipe> iteratorToIterable(Iterator<Recipe> iterator) {
		return () -> iterator;
	}
}
