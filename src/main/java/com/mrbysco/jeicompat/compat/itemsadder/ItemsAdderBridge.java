package com.mrbysco.jeicompat.compat.itemsadder;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
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
import java.util.function.Consumer;
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
	private Method getAllItems;
	private Method checkIsCustomRecipe;

	public ItemsAdderBridge(Plugin plugin) {
		this.plugin = plugin;
		initialize();
	}

	private void initialize() {
		Plugin itemsAdder = Bukkit.getPluginManager().getPlugin("ItemsAdder");
		if (itemsAdder == null) {
			available = false;
			return;
		}

		try {
			ClassLoader classLoader = itemsAdder.getClass().getClassLoader();
			customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack", true, classLoader);
			getNamespacedIdsInRegistry = customStackClass.getMethod("getNamespacedIdsInRegistry");
			getInstance = customStackClass.getMethod("getInstance", String.class);
			getItemStack = customStackClass.getMethod("getItemStack");
			getConfigSectionCopy = customStackClass.getMethod("getConfigSectionCopy");
			byItemStack = customStackClass.getMethod("byItemStack", ItemStack.class);
			getNamespacedId = customStackClass.getMethod("getNamespacedID");

			Class<?> itemsAdderClass = Class.forName("dev.lone.itemsadder.api.ItemsAdder", true, classLoader);
			applyResourcepack = itemsAdderClass.getMethod("applyResourcepack", Player.class);
			getAllItems = itemsAdderClass.getMethod("getAllItems");
			checkIsCustomRecipe = itemsAdderClass.getMethod("isCustomRecipe", NamespacedKey.class);

			available = true;
			JEIRecipeBridgePlugin.LOGGER.info("ItemsAdder detected; server-side JEI compatibility is enabled.");
		} catch (ReflectiveOperationException exception) {
			available = false;
			applyResourcepack = null;
			getAllItems = null;
			checkIsCustomRecipe = null;
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
			List<CatalogItem> fromAllItems = collectCatalogItemsFromGetAllItems(skipHiddenItems);
			if (!fromAllItems.isEmpty()) {
				return fromAllItems;
			}

			@SuppressWarnings("unchecked")
			Collection<String> ids = (Collection<String>) getNamespacedIdsInRegistry.invoke(null);
			List<CatalogItem> items = new ArrayList<>(ids.size());

			for (String namespacedId : ids) {
				Object customStack = getInstance.invoke(null, namespacedId);
				if (customStack == null) {
					continue;
				}

				items.add(toCatalogItem(customStack, namespacedId, skipHiddenItems));
			}

			return items.stream().filter(item -> item != null).toList();
		} catch (ReflectiveOperationException exception) {
			JEIRecipeBridgePlugin.LOGGER.error("Failed to collect ItemsAdder catalog items", exception);
			return List.of();
		}
	}

	public int countCustomRecipes() {
		if (!available || checkIsCustomRecipe == null) {
			return 0;
		}

		int count = 0;
		for (Recipe recipe : iteratorToIterable(Bukkit.recipeIterator())) {
			if (!(recipe instanceof Keyed keyed)) {
				continue;
			}

			if (isCustomRecipe(keyed.getKey())) {
				count++;
			}
		}
		return count;
	}

	public boolean isCustomRecipe(NamespacedKey recipeKey) {
		if (!available || checkIsCustomRecipe == null || recipeKey == null) {
			return false;
		}

		try {
			return (boolean) checkIsCustomRecipe.invoke(null, recipeKey);
		} catch (ReflectiveOperationException exception) {
			return false;
		}
	}

	public boolean isCustomRecipe(String namespacedId) {
		if (!available || namespacedId == null || namespacedId.isBlank()) {
			return false;
		}

		String[] parts = namespacedId.split(":", 2);
		if (parts.length != 2) {
			return false;
		}

		return isCustomRecipe(new NamespacedKey(parts[0], parts[1]));
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

	public void registerResourcePackSendListener(Consumer<Player> onItemsAdderPackSent) {
		if (!available) {
			return;
		}

		try {
			Plugin itemsAdder = Bukkit.getPluginManager().getPlugin("ItemsAdder");
			if (itemsAdder == null) {
				return;
			}

			ClassLoader classLoader = itemsAdder.getClass().getClassLoader();
			@SuppressWarnings("unchecked")
			Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(
					"dev.lone.itemsadder.api.Events.ResourcePackSendEvent",
					true,
					classLoader
			);
			Method isItemsAdderPack = eventClass.getMethod("isItemsAdderPack");
			Method getPlayer = eventClass.getMethod("getPlayer");
			Listener listener = new Listener() {
			};
			Bukkit.getPluginManager().registerEvent(
					eventClass,
					listener,
					EventPriority.MONITOR,
					(l, event) -> {
						try {
							if (!(boolean) isItemsAdderPack.invoke(event)) {
								return;
							}
							Player player = (Player) getPlayer.invoke(event);
							if (player != null) {
								onItemsAdderPackSent.accept(player);
							}
						} catch (ReflectiveOperationException exception) {
							if (JEIRecipeBridgePlugin.LOGGER.isDebugEnabled()) {
								JEIRecipeBridgePlugin.LOGGER.debug(
										"Could not handle ItemsAdder ResourcePackSendEvent",
										exception
								);
							}
						}
					},
					plugin,
					true
			);
		} catch (ReflectiveOperationException exception) {
			plugin.getLogger().log(
					Level.WARNING,
					"Could not register ItemsAdder resource pack send listener: " + exception.getMessage()
			);
		}
	}

	public void registerLoadListener(Runnable onReload) {
		if (!available) {
			return;
		}

		try {
			Plugin itemsAdder = Bukkit.getPluginManager().getPlugin("ItemsAdder");
			if (itemsAdder == null) {
				return;
			}

			ClassLoader classLoader = itemsAdder.getClass().getClassLoader();
			@SuppressWarnings("unchecked")
			Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(
					"dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent",
					true,
					classLoader
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

	private List<CatalogItem> collectCatalogItemsFromGetAllItems(boolean skipHiddenItems)
			throws ReflectiveOperationException {
		if (getAllItems == null) {
			return List.of();
		}

		@SuppressWarnings("unchecked")
		List<Object> allItems = (List<Object>) getAllItems.invoke(null);
		if (allItems == null || allItems.isEmpty()) {
			return List.of();
		}

		List<CatalogItem> items = new ArrayList<>(allItems.size());
		for (Object customStack : allItems) {
			String namespacedId = (String) getNamespacedId.invoke(customStack);
			CatalogItem catalogItem = toCatalogItem(customStack, namespacedId, skipHiddenItems);
			if (catalogItem != null) {
				items.add(catalogItem);
			}
		}
		return items;
	}

	private CatalogItem toCatalogItem(Object customStack, String namespacedId, boolean skipHiddenItems)
			throws ReflectiveOperationException {
		if (customStack == null || namespacedId == null || namespacedId.isBlank()) {
			return null;
		}

		if (skipHiddenItems && isHiddenFromInventory(customStack)) {
			return null;
		}

		ItemStack itemStack = (ItemStack) getItemStack.invoke(customStack);
		if (itemStack == null || itemStack.getType().isAir()) {
			return null;
		}

		return new CatalogItem(namespacedId, itemStack.clone());
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
