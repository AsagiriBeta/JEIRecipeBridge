package com.mrbysco.jeicompat;

import com.mrbysco.jeicompat.compat.itemsadder.ItemsAdderBridge;
import com.mrbysco.jeicompat.listener.ResourceReloadListener;
import com.mrbysco.jeicompat.nms.NmsRecipeBridge;
import com.mrbysco.jeicompat.sync.RecipePayloadCache;
import com.mrbysco.jeicompat.util.ServerTasks;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JEIRecipeBridgePlugin extends JavaPlugin {
	public static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeBridge");

	private RecipeSyncService syncService;

	@Override
	public void onEnable() {
		NmsRecipeBridge recipeBridge = new NmsRecipeBridge(this);
		ItemsAdderBridge itemsAdderBridge = new ItemsAdderBridge(this);
		RecipePayloadCache payloadCache = new RecipePayloadCache(recipeBridge);
		syncService = new RecipeSyncService(this, recipeBridge, payloadCache);

		Messenger messenger = getServer().getMessenger();
		messenger.registerOutgoingPluginChannel(this, "neoforge:recipe_content");
		messenger.registerOutgoingPluginChannel(this, "fabric:recipe_sync");
		messenger.registerOutgoingPluginChannel(this, "fabric:recipe_sync_finished");

		getServer().getPluginManager().registerEvents(new RecipeHandler(syncService), this);
		getServer().getPluginManager().registerEvents(new ResourceReloadListener(this, syncService), this);

		itemsAdderBridge.registerLoadListener(() -> ServerTasks.runGlobal(this, () -> {
			refreshRecipeCache();
			syncService.resyncAll();
		}));

		refreshRecipeCache();

		LOGGER.info(
				"JEI Recipe Bridge enabled (sync: {}, ItemsAdder: {})",
				recipeBridge.isAvailable() ? "ready" : "dormant",
				itemsAdderBridge.isAvailable() ? "yes" : "no"
		);
	}

	@Override
	public void onDisable() {
		Messenger messenger = getServer().getMessenger();
		messenger.unregisterOutgoingPluginChannel(this, "neoforge:recipe_content");
		messenger.unregisterOutgoingPluginChannel(this, "fabric:recipe_sync");
		messenger.unregisterOutgoingPluginChannel(this, "fabric:recipe_sync_finished");
	}

	public void refreshRecipeCache() {
		syncService.refreshRecipeKeys();
		syncService.invalidateCache();
	}
}
