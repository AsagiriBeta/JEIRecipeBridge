package com.mrbysco.jeicompat.compat.itemsadder;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Re-sync recipes when ItemsAdder reloads its data (/iareload).
 */
public final class ItemsAdderBridge {
	private final Plugin plugin;
	private boolean available;

	public ItemsAdderBridge(Plugin plugin) {
		this.plugin = plugin;
		available = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
	}

	public boolean isAvailable() {
		return available;
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
			available = false;
			plugin.getLogger().log(
					Level.WARNING,
					"ItemsAdder is installed but its reload listener could not be registered: "
							+ exception.getMessage()
			);
		}
	}
}
