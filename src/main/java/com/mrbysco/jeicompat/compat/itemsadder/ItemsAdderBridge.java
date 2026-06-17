package com.mrbysco.jeicompat.compat.itemsadder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Optional ItemsAdder hook via reflection. No ItemsAdder = plugin still works.
 */
public final class ItemsAdderBridge {
	private final Plugin plugin;
	private boolean available;
	private Method applyResourcepack;

	public ItemsAdderBridge(Plugin plugin) {
		this.plugin = plugin;
		Plugin itemsAdder = Bukkit.getPluginManager().getPlugin("ItemsAdder");
		if (itemsAdder == null) {
			return;
		}

		try {
			ClassLoader classLoader = itemsAdder.getClass().getClassLoader();
			Class<?> itemsAdderClass = Class.forName("dev.lone.itemsadder.api.ItemsAdder", true, classLoader);
			applyResourcepack = itemsAdderClass.getMethod("applyResourcepack", Player.class);
			available = true;
		} catch (ReflectiveOperationException exception) {
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
		if (!available) {
			return;
		}

		try {
			applyResourcepack.invoke(null, player);
		} catch (ReflectiveOperationException ignored) {
		}
	}

	public void registerLoadListener(Runnable onReload) {
		registerEvent("dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent", onReload);
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
						} catch (ReflectiveOperationException ignored) {
						}
					},
					plugin,
					true
			);
		} catch (ReflectiveOperationException exception) {
			plugin.getLogger().log(
					Level.WARNING,
					"Could not register ItemsAdder resource pack listener: " + exception.getMessage()
			);
		}
	}

	private void registerEvent(String className, Runnable callback) {
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
			Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(className, true, classLoader);
			Listener listener = new Listener() {
			};
			Bukkit.getPluginManager().registerEvent(
					eventClass,
					listener,
					EventPriority.MONITOR,
					(l, event) -> callback.run(),
					plugin,
					true
			);
		} catch (ReflectiveOperationException exception) {
			plugin.getLogger().log(
					Level.WARNING,
					"Could not register ItemsAdder event listener: " + exception.getMessage()
			);
		}
	}
}
