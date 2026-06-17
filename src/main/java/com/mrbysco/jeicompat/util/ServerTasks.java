package com.mrbysco.jeicompat.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class ServerTasks {
	private ServerTasks() {
	}

	public static void runGlobal(Plugin plugin, Runnable task) {
		Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
	}
}
