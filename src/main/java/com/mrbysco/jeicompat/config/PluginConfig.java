package com.mrbysco.jeicompat.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PluginConfig {
	private final boolean enabled;
	private final boolean syncOnJoin;
	private final int syncDelayTicks;
	private final boolean retryOnFailedSync;
	private final int syncRetryDelayTicks;
	private final boolean syncOnDatapackReload;
	private final boolean discoverRecipesOnJoin;
	private final boolean notifyPlayer;
	private final boolean filterInvalidRecipes;
	private final boolean logFilteredRecipes;
	private final Set<String> recipeBlacklist;
	private final boolean itemsAdderEnabled;
	private final boolean itemsAdderRegisterShowcaseRecipes;
	private final boolean itemsAdderApplyResourcePack;
	private final boolean itemsAdderWaitForResourcePack;
	private final int itemsAdderResourcePackWaitTicks;
	private final boolean itemsAdderSkipHiddenItems;
	private final boolean fabricSendSyncFinished;
	private final boolean debug;

	public PluginConfig(
			boolean enabled,
			boolean syncOnJoin,
			int syncDelayTicks,
			boolean retryOnFailedSync,
			int syncRetryDelayTicks,
			boolean syncOnDatapackReload,
			boolean discoverRecipesOnJoin,
			boolean notifyPlayer,
			boolean filterInvalidRecipes,
			boolean logFilteredRecipes,
			Set<String> recipeBlacklist,
			boolean itemsAdderEnabled,
			boolean itemsAdderRegisterShowcaseRecipes,
			boolean itemsAdderApplyResourcePack,
			boolean itemsAdderWaitForResourcePack,
			int itemsAdderResourcePackWaitTicks,
			boolean itemsAdderSkipHiddenItems,
			boolean fabricSendSyncFinished,
			boolean debug) {
		this.enabled = enabled;
		this.syncOnJoin = syncOnJoin;
		this.syncDelayTicks = syncDelayTicks;
		this.retryOnFailedSync = retryOnFailedSync;
		this.syncRetryDelayTicks = syncRetryDelayTicks;
		this.syncOnDatapackReload = syncOnDatapackReload;
		this.discoverRecipesOnJoin = discoverRecipesOnJoin;
		this.notifyPlayer = notifyPlayer;
		this.filterInvalidRecipes = filterInvalidRecipes;
		this.logFilteredRecipes = logFilteredRecipes;
		this.recipeBlacklist = recipeBlacklist;
		this.itemsAdderEnabled = itemsAdderEnabled;
		this.itemsAdderRegisterShowcaseRecipes = itemsAdderRegisterShowcaseRecipes;
		this.itemsAdderApplyResourcePack = itemsAdderApplyResourcePack;
		this.itemsAdderWaitForResourcePack = itemsAdderWaitForResourcePack;
		this.itemsAdderResourcePackWaitTicks = itemsAdderResourcePackWaitTicks;
		this.itemsAdderSkipHiddenItems = itemsAdderSkipHiddenItems;
		this.fabricSendSyncFinished = fabricSendSyncFinished;
		this.debug = debug;
	}

	public static PluginConfig load(Plugin plugin) {
		plugin.saveDefaultConfig();
		FileConfiguration config = plugin.getConfig();

		Set<String> blacklist = new HashSet<>(config.getStringList("recipe-blacklist"));
		boolean itemsAdderDetected = plugin.getServer().getPluginManager().getPlugin("ItemsAdder") != null;
		boolean itemsAdderEnabled = config.getBoolean("itemsadder.enabled", true) && itemsAdderDetected;

		return new PluginConfig(
				config.getBoolean("enabled", true),
				config.getBoolean("sync-on-join", true),
				Math.max(0, config.getInt("sync-delay-ticks", 1)),
				config.getBoolean("retry-on-failed-sync", true),
				Math.max(1, config.getInt("sync-retry-delay-ticks", 20)),
				config.getBoolean("sync-on-datapack-reload", true),
				config.getBoolean("discover-recipes-on-join", true),
				config.getBoolean("notify-player", false),
				config.getBoolean("filter-invalid-recipes", true),
				config.getBoolean("log-filtered-recipes", true),
				Collections.unmodifiableSet(blacklist),
				itemsAdderEnabled,
				config.getBoolean("itemsadder.register-showcase-recipes", false),
				config.getBoolean("itemsadder.apply-resource-pack", true),
				config.getBoolean("itemsadder.wait-for-resource-pack", true),
				Math.max(20, config.getInt("itemsadder.resource-pack-wait-timeout-ticks", 200)),
				config.getBoolean("itemsadder.skip-hidden-items", true),
				config.getBoolean("fabric-send-sync-finished", true),
				config.getBoolean("debug", false)
		);
	}

	public boolean enabled() {
		return enabled;
	}

	public boolean syncOnJoin() {
		return syncOnJoin;
	}

	public int syncDelayTicks() {
		return syncDelayTicks;
	}

	public boolean retryOnFailedSync() {
		return retryOnFailedSync;
	}

	public int syncRetryDelayTicks() {
		return syncRetryDelayTicks;
	}

	public boolean syncOnDatapackReload() {
		return syncOnDatapackReload;
	}

	public boolean discoverRecipesOnJoin() {
		return discoverRecipesOnJoin;
	}

	public boolean notifyPlayer() {
		return notifyPlayer;
	}

	public boolean filterInvalidRecipes() {
		return filterInvalidRecipes;
	}

	public boolean logFilteredRecipes() {
		return logFilteredRecipes;
	}

	public Set<String> recipeBlacklist() {
		return recipeBlacklist;
	}

	public boolean itemsAdderEnabled() {
		return itemsAdderEnabled;
	}

	public boolean itemsAdderRegisterShowcaseRecipes() {
		return itemsAdderRegisterShowcaseRecipes;
	}

	public boolean itemsAdderApplyResourcePack() {
		return itemsAdderApplyResourcePack;
	}

	public boolean itemsAdderWaitForResourcePack() {
		return itemsAdderWaitForResourcePack;
	}

	public int itemsAdderResourcePackWaitTicks() {
		return itemsAdderResourcePackWaitTicks;
	}

	public boolean itemsAdderSkipHiddenItems() {
		return itemsAdderSkipHiddenItems;
	}

	public boolean fabricSendSyncFinished() {
		return fabricSendSyncFinished;
	}

	public boolean debug() {
		return debug;
	}
}
