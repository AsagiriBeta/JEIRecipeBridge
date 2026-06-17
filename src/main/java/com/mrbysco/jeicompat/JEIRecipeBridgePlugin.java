package com.mrbysco.jeicompat;

import com.mrbysco.jeicompat.command.JEIRecipeBridgeCommand;
import com.mrbysco.jeicompat.compat.itemsadder.ItemsAdderBridge;
import com.mrbysco.jeicompat.compat.itemsadder.ItemsAdderShowcaseService;
import com.mrbysco.jeicompat.config.PluginConfig;
import com.mrbysco.jeicompat.listener.ItemsAdderResourcePackListener;
import com.mrbysco.jeicompat.listener.ResourceReloadListener;
import com.mrbysco.jeicompat.nms.NmsRecipeBridge;
import com.mrbysco.jeicompat.nms.RecipeBridge;
import com.mrbysco.jeicompat.sync.RecipeDiscoveryService;
import com.mrbysco.jeicompat.sync.RecipePayloadCache;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class JEIRecipeBridgePlugin extends JavaPlugin {
	public static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeBridge");
	private final AtomicReference<PluginConfig> pluginConfig = new AtomicReference<>();
	private RecipeBridge recipeBridge;
	private ItemsAdderBridge itemsAdderBridge;
	private ItemsAdderShowcaseService itemsAdderShowcaseService;
	private RecipePayloadCache payloadCache;
	private RecipeDiscoveryService recipeDiscoveryService;
	private RecipeSyncService syncService;
	private ItemsAdderResourcePackListener resourcePackListener;

	@Override
	public void onEnable() {
		recipeBridge = new NmsRecipeBridge(this);
		itemsAdderBridge = new ItemsAdderBridge(this);
		reloadPluginConfig();

		final Server server = getServer();
		final Messenger messenger = server.getMessenger();
		messenger.registerOutgoingPluginChannel(this, "neoforge:recipe_content");
		messenger.registerOutgoingPluginChannel(this, "fabric:recipe_sync");
		messenger.registerOutgoingPluginChannel(this, "fabric:recipe_sync_finished");

		itemsAdderBridge.registerLoadListener(() -> com.mrbysco.jeicompat.util.ServerTasks.runGlobal(this, () -> {
			refreshRecipeContent();
			if (syncService != null) {
				syncService.resyncAll();
			}
		}));
		itemsAdderBridge.registerResourcePackSendListener(player -> {
			if (resourcePackListener != null) {
				resourcePackListener.onItemsAdderPackSent(player);
			}
		});

		server.getPluginManager().registerEvents(new RecipeHandler(syncService), this);
		server.getPluginManager().registerEvents(new ResourceReloadListener(this, syncService, this::getPluginConfig), this);

		JEIRecipeBridgeCommand bridgeCommand = new JEIRecipeBridgeCommand(this, syncService, itemsAdderBridge, recipeBridge);
		registerCommand(
				"jeibridge",
				"JEI Recipe Bridge commands",
				List.of("jrb"),
				bridgeCommand
		);

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
		Messenger messenger = getServer().getMessenger();
		messenger.unregisterOutgoingPluginChannel(this, "neoforge:recipe_content");
		messenger.unregisterOutgoingPluginChannel(this, "fabric:recipe_sync");
		messenger.unregisterOutgoingPluginChannel(this, "fabric:recipe_sync_finished");
	}

	public void reloadPluginConfig() {
		reloadConfig();
		PluginConfig config = PluginConfig.load(this);
		pluginConfig.set(config);

		if (payloadCache == null) {
			payloadCache = new RecipePayloadCache(this::getPluginConfig, recipeBridge);
			recipeDiscoveryService = new RecipeDiscoveryService(this::getPluginConfig);
			itemsAdderShowcaseService = new ItemsAdderShowcaseService(this, this::getPluginConfig, itemsAdderBridge);
			syncService = new RecipeSyncService(
					this,
					this::getPluginConfig,
					recipeBridge,
					payloadCache,
					recipeDiscoveryService,
					itemsAdderBridge
			);
			resourcePackListener = new ItemsAdderResourcePackListener(syncService, this::getPluginConfig);
			syncService.setResourcePackListener(resourcePackListener);
			getServer().getPluginManager().registerEvents(resourcePackListener, this);
		}

		refreshRecipeContent();
	}

	public void refreshRecipeContent() {
		if (itemsAdderShowcaseService != null) {
			itemsAdderShowcaseService.scheduleRefreshShowcaseRecipes();
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

	public ItemsAdderShowcaseService getItemsAdderShowcaseService() {
		return itemsAdderShowcaseService;
	}
}
