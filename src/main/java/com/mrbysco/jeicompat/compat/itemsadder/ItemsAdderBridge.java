package com.mrbysco.jeicompat.compat.itemsadder;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Optional ItemsAdder integration via reflection so the plugin still runs without ItemsAdder installed.
 */
public final class ItemsAdderBridge {
	private static final String SHOWCASE_PREFIX = "ia_showcase/";

	private final Plugin plugin;
	private boolean available;
	private Class<?> customStackClass;
	private Method getNamespacedIdsInRegistry;
	private Method getInstance;
	private Method getItemStack;
	private Method getConfigSectionCopy;
	private Method byItemStack;
	private Method getNamespacedId;
	private Method applyResourcepack;

	public ItemsAdderBridge(Plugin plugin) {
		this.plugin = plugin;
		initialize();
	}

	private void initialize() {
		if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
			available = false;
			return;
		}

		try {
			customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
			getNamespacedIdsInRegistry = customStackClass.getMethod("getNamespacedIdsInRegistry");
			getInstance = customStackClass.getMethod("getInstance", String.class);
			getItemStack = customStackClass.getMethod("getItemStack");
			getConfigSectionCopy = customStackClass.getMethod("getConfigSectionCopy");
			byItemStack = customStackClass.getMethod("byItemStack", ItemStack.class);
			getNamespacedId = customStackClass.getMethod("getNamespacedID");

			Class<?> itemsAdderClass = Class.forName("dev.lone.itemsadder.api.ItemsAdder");
			applyResourcepack = itemsAdderClass.getMethod("applyResourcepack", Player.class);

			available = true;
			JEIRecipeBridgePlugin.LOGGER.info("ItemsAdder detected; server-side JEI compatibility is enabled.");
		} catch (ReflectiveOperationException exception) {
			available = false;
			applyResourcepack = null;
			plugin.getLogger().log(
					Level.WARNING,
					"ItemsAdder is installed but its API could not be resolved: " + exception.getMessage()
			);
		}
	}

	public boolean isAvailable() {
		return available;
	}

	public void applyResourcePack(Player player) {
		if (!available || applyResourcepack == null) {
			return;
		}

		try {
			applyResourcepack.invoke(null, player);
		} catch (ReflectiveOperationException exception) {
			if (JEIRecipeBridgePlugin.LOGGER.isDebugEnabled()) {
				JEIRecipeBridgePlugin.LOGGER.debug(
						"Could not apply ItemsAdder resource pack to {}",
						player.getName(),
						exception
				);
			}
		}
	}

	public List<CatalogItem> collectCatalogItems(boolean skipHiddenItems) {
		if (!available) {
			return List.of();
		}

		try {
			@SuppressWarnings("unchecked")
			Collection<String> ids = (Collection<String>) getNamespacedIdsInRegistry.invoke(null);
			List<CatalogItem> items = new ArrayList<>(ids.size());

			for (String namespacedId : ids) {
				Object customStack = getInstance.invoke(null, namespacedId);
				if (customStack == null) {
					continue;
				}

				if (skipHiddenItems && isHiddenFromInventory(customStack)) {
					continue;
				}

				ItemStack itemStack = (ItemStack) getItemStack.invoke(customStack);
				if (itemStack == null || itemStack.getType().isAir()) {
					continue;
				}

				items.add(new CatalogItem(namespacedId, itemStack.clone()));
			}

			return items;
		} catch (ReflectiveOperationException exception) {
			JEIRecipeBridgePlugin.LOGGER.error("Failed to collect ItemsAdder catalog items", exception);
			return List.of();
		}
	}

	public Set<String> collectRecipeBackedItemIds() {
		Set<String> ids = new HashSet<>();
		if (!available) {
			return ids;
		}

		for (Recipe recipe : iteratorToIterable(Bukkit.recipeIterator())) {
			ItemStack result = recipe.getResult();
			if (result == null || result.getType().isAir()) {
				continue;
			}

			String namespacedId = resolveNamespacedId(result);
			if (namespacedId != null) {
				ids.add(namespacedId);
			}
		}

		return ids;
	}

	public String resolveNamespacedId(ItemStack itemStack) {
		if (!available || itemStack == null || itemStack.getType().isAir()) {
			return null;
		}

		try {
			Object customStack = byItemStack.invoke(null, itemStack);
			if (customStack == null) {
				return null;
			}
			return (String) getNamespacedId.invoke(customStack);
		} catch (ReflectiveOperationException exception) {
			return null;
		}
	}

	public void registerLoadListener(Runnable onReload) {
		if (!available) {
			return;
		}

		try {
			@SuppressWarnings("unchecked")
			Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(
					"dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent"
			);
			Listener listener = new Listener() {
			};
			Bukkit.getPluginManager().registerEvent(
					eventClass,
					listener,
					EventPriority.MONITOR,
					(l, event) -> onReload.run(),
					plugin,
					true
			);
		} catch (ReflectiveOperationException exception) {
			plugin.getLogger().log(
					Level.WARNING,
					"Could not register ItemsAdder reload listener: " + exception.getMessage()
			);
		}
	}

	public static String showcaseKeySuffix(String namespacedId) {
		return SHOWCASE_PREFIX + namespacedId.toLowerCase(Locale.ROOT).replace(':', '/');
	}

	private boolean isHiddenFromInventory(Object customStack) {
		try {
			ConfigurationSection section = (ConfigurationSection) getConfigSectionCopy.invoke(customStack);
			if (section == null) {
				return false;
			}

			if (!section.getBoolean("enabled", true)) {
				return true;
			}

			ConfigurationSection behaviours = section.getConfigurationSection("behaviours");
			if (behaviours != null) {
				ConfigurationSection creativeInventory = behaviours.getConfigurationSection("creative_inventory");
				if (creativeInventory != null && creativeInventory.getBoolean("hide", false)) {
					return true;
				}
			}

			return section.getBoolean("creative_inventory.hide", false)
					|| section.getBoolean("hide_from_inventory", false);
		} catch (ReflectiveOperationException exception) {
			return false;
		}
	}

	public record CatalogItem(String namespacedId, ItemStack itemStack) {
	}

	private static Iterable<Recipe> iteratorToIterable(Iterator<Recipe> iterator) {
		return () -> iterator;
	}
}
