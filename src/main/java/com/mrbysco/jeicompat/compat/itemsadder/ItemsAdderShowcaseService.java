package com.mrbysco.jeicompat.compat.itemsadder;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import com.mrbysco.jeicompat.config.PluginConfig;
import com.mrbysco.jeicompat.util.ServerTasks;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Registers lightweight showcase recipes so ItemsAdder items appear in synced recipe data.
 * This stays entirely on the Paper server and does not require any client mod.
 */
public final class ItemsAdderShowcaseService {
	private final Plugin plugin;
	private final Supplier<PluginConfig> config;
	private final ItemsAdderBridge itemsAdderBridge;
	private final Set<NamespacedKey> registeredKeys = new HashSet<>();

	public ItemsAdderShowcaseService(Plugin plugin, Supplier<PluginConfig> config, ItemsAdderBridge itemsAdderBridge) {
		this.plugin = plugin;
		this.config = config;
		this.itemsAdderBridge = itemsAdderBridge;
	}

	public void scheduleRefreshShowcaseRecipes() {
		ServerTasks.runGlobal(plugin, this::refreshShowcaseRecipes);
	}

	public void refreshShowcaseRecipes() {
		clearShowcaseRecipes();
		if (!shouldRegisterShowcaseRecipes()) {
			return;
		}

		Set<String> recipeBackedIds = itemsAdderBridge.collectRecipeBackedItemIds();
		List<ItemsAdderBridge.CatalogItem> catalogItems = itemsAdderBridge.collectCatalogItems(
				config.get().itemsAdderSkipHiddenItems()
		);

		int registered = 0;
		for (ItemsAdderBridge.CatalogItem catalogItem : catalogItems) {
			if (recipeBackedIds.contains(catalogItem.namespacedId())) {
				continue;
			}

			if (registerShowcaseRecipe(catalogItem)) {
				registered++;
			}
		}

		JEIRecipeBridgePlugin.LOGGER.info(
				"Registered {} ItemsAdder showcase recipe(s) for JEI recipe indexing",
				registered
		);
	}

	public void clearShowcaseRecipes() {
		for (NamespacedKey key : Set.copyOf(registeredKeys)) {
			Bukkit.removeRecipe(key);
		}
		registeredKeys.clear();
	}

	private boolean shouldRegisterShowcaseRecipes() {
		PluginConfig currentConfig = config.get();
		return currentConfig.itemsAdderEnabled() && currentConfig.itemsAdderRegisterShowcaseRecipes();
	}

	private boolean registerShowcaseRecipe(ItemsAdderBridge.CatalogItem catalogItem) {
		NamespacedKey key = new NamespacedKey(
				plugin,
				ItemsAdderBridge.showcaseKeySuffix(catalogItem.namespacedId())
		);

		ItemStack result = catalogItem.itemStack().clone();
		result.setAmount(1);

		ShapelessRecipe recipe = new ShapelessRecipe(key, result);
		recipe.addIngredient(new RecipeChoice.ExactChoice(result));

		if (Bukkit.addRecipe(recipe)) {
			registeredKeys.add(key);
			return true;
		}

		return false;
	}
}
