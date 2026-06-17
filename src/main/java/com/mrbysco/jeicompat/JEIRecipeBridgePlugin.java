package com.mrbysco.jeicompat;

import com.mrbysco.jeicompat.command.JEIRecipeBridgeCommand;
import com.mrbysco.jeicompat.compat.itemsadder.ItemsAdderBridge;
import com.mrbysco.jeicompat.compat.itemsadder.ItemsAdderShowcaseService;
import com.mrbysco.jeicompat.config.PluginConfig;
import com.mrbysco.jeicompat.listener.ResourceReloadListener;
import com.mrbysco.jeicompat.nms.NmsRecipeBridge;
import com.mrbysco.jeicompat.nms.RecipeBridge;
import com.mrbysco.jeicompat.sync.RecipeDiscoveryService;
import com.mrbysco.jeicompat.sync.RecipePayloadCache;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class JEIRecipeBridgePlugin extends JavaPlugin {
	public static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeBridge");
	public static Plugin Plugin;
	private final AtomicReference<PluginConfig> pluginConfig = new AtomicReference<>();
	private RecipeBridge recipeBridge;
	private ItemsAdderBridge itemsAdderBridge;
	private ItemsAdderShowcaseService itemsAdderShowcaseService;
	private RecipePayloadCache payloadCache;
	private RecipeDiscoveryService recipeDiscoveryService;
	private RecipeSyncService syncService;

	@Override
	public void onEnable() {
		Plugin = this;
		recipeBridge = new NmsRecipeBridge(this);
		itemsAdderBridge = new ItemsAdderBridge(this);
		reloadPluginConfig();

		final Server server = getServer();
		final Messenger messenger = server.getMessenger();
		messenger.registerOutgoingPluginChannel(this, "neoforge:recipe_content");
		messenger.registerOutgoingPluginChannel(this, "fabric:recipe_sync");
		messenger.registerOutgoingPluginChannel(this, "fabric:recipe_sync_finished");

		itemsAdderBridge.registerLoadListener(() -> {
			refreshRecipeContent();
			if (syncService != null) {
				syncService.resyncAll();
			}
		});

		server.getPluginManager().registerEvents(new RecipeHandler(syncService), this);
		server.getPluginManager().registerEvents(new ResourceReloadListener(this, syncService, this::getPluginConfig), this);

		var command = Objects.requireNonNull(getCommand("jeibridge"), "jeibridge command missing from plugin.yml");
		JEIRecipeBridgeCommand executor = new JEIRecipeBridgeCommand(this, syncService, itemsAdderBridge, recipeBridge);
		command.setExecutor(executor);
		command.setTabCompleter(executor);

		PluginConfig config = getPluginConfig();
		if (!recipeBridge.isAvailable()) {
			LOGGER.warn("Recipe sync is dormant on this server version; check debug logs for details.");
		}
		LOGGER.info(
				"JEI Recipe Bridge enabled (mode: reflection, sync: {}, filtering: {}, ItemsAdder: {}, showcase: {})",
				recipeBridge.isAvailable() ? "ready" : "dormant",
				config.filterInvalidRecipes() ? "on" : "off",
				config.itemsAdderEnabled() ? "on" : "off",
				config.itemsAdderRegisterShowcaseRecipes() ? "on" : "off"
		);
	}

	@Override
	public void onDisable() {
		if (itemsAdderShowcaseService != null) {
			itemsAdderShowcaseService.clearShowcaseRecipes();
		}
		Plugin = null;
	}

	public void reloadPluginConfig() {
		PluginConfig config = PluginConfig.load(this);
		pluginConfig.set(config);

		if (payloadCache == null) {
			payloadCache = new RecipePayloadCache(this::getPluginConfig, recipeBridge);
			recipeDiscoveryService = new RecipeDiscoveryService(this::getPluginConfig);
			itemsAdderShowcaseService = new ItemsAdderShowcaseService(this::getPluginConfig, itemsAdderBridge);
			syncService = new RecipeSyncService(
					this::getPluginConfig,
					recipeBridge,
					payloadCache,
					recipeDiscoveryService,
					itemsAdderBridge
			);
		}

		refreshRecipeContent();
	}

	public void refreshRecipeContent() {
		if (itemsAdderShowcaseService != null) {
			itemsAdderShowcaseService.refreshShowcaseRecipes();
		}
		if (recipeDiscoveryService != null) {
			recipeDiscoveryService.refreshRecipeKeys();
		}
		if (payloadCache != null) {
			payloadCache.invalidate();
		}
	}

	public PluginConfig getPluginConfig() {
		return pluginConfig.get();
	}
}
