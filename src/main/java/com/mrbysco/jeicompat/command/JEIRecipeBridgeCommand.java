package com.mrbysco.jeicompat.command;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import com.mrbysco.jeicompat.RecipeSyncService;
import com.mrbysco.jeicompat.compat.itemsadder.ItemsAdderBridge;
import com.mrbysco.jeicompat.nms.RecipeBridge;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@NullMarked
public final class JEIRecipeBridgeCommand implements BasicCommand {
	private static final String PERM_RESYNC = "jeirecipebridge.command.resync";
	private static final String PERM_RESYNC_OTHERS = "jeirecipebridge.command.resync.others";
	private static final String PERM_RELOAD = "jeirecipebridge.command.reload";
	private static final String PERM_INFO = "jeirecipebridge.command.info";

	private final JEIRecipeBridgePlugin plugin;
	private final RecipeSyncService syncService;
	private final ItemsAdderBridge itemsAdderBridge;
	private final RecipeBridge recipeBridge;

	public JEIRecipeBridgeCommand(
			JEIRecipeBridgePlugin plugin,
			RecipeSyncService syncService,
			ItemsAdderBridge itemsAdderBridge,
			RecipeBridge recipeBridge) {
		this.plugin = plugin;
		this.syncService = syncService;
		this.itemsAdderBridge = itemsAdderBridge;
		this.recipeBridge = recipeBridge;
	}

	@Override
	public void execute(CommandSourceStack source, String[] args) {
		CommandSender sender = source.getSender();
		if (args.length == 0) {
			sendUsage(sender);
			return;
		}

		switch (args[0].toLowerCase(Locale.ROOT)) {
			case "reload" -> handleReload(sender);
			case "resync" -> handleResync(sender, args);
			case "info" -> handleInfo(sender);
			default -> sendUsage(sender);
		}
	}

	@Override
	public boolean canUse(CommandSender sender) {
		return canReload(sender) || canResyncSelf(sender) || canResyncOthers(sender) || canInfo(sender);
	}

	@Override
	public @Nullable String permission() {
		return null;
	}

	@Override
	public Collection<String> suggest(CommandSourceStack source, String[] args) {
		CommandSender sender = source.getSender();
		if (args.length == 1) {
			List<String> options = new ArrayList<>();
			if (canReload(sender)) {
				options.add("reload");
			}
			if (canResyncSelf(sender) || canResyncOthers(sender)) {
				options.add("resync");
			}
			if (canInfo(sender)) {
				options.add("info");
			}
			return filter(options, args[0]);
		}

		if (args.length == 2 && args[0].equalsIgnoreCase("resync") && canResyncOthers(sender)) {
			List<String> suggestions = new ArrayList<>();
			suggestions.add("all");
			for (Player player : Bukkit.getOnlinePlayers()) {
				suggestions.add(player.getName());
			}
			return filter(suggestions, args[1]);
		}

		return List.of();
	}

	private void handleReload(CommandSender sender) {
		if (!canReload(sender)) {
			sendNoPermission(sender);
			return;
		}

		plugin.reloadPluginConfig();
		sender.sendMessage("§aJEI Recipe Bridge configuration reloaded.");
	}

	private void handleResync(CommandSender sender, String[] args) {
		if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
			if (!canResyncOthers(sender)) {
				sendNoPermission(sender);
				return;
			}

			plugin.refreshRecipeContent();
			syncService.resyncAll();
			sender.sendMessage("§aRe-synced recipes for all online players.");
			return;
		}

		Player target;
		if (args.length >= 2) {
			if (!canResyncOthers(sender)) {
				sendNoPermission(sender);
				return;
			}

			target = Bukkit.getPlayerExact(args[1]);
			if (target == null) {
				sender.sendMessage("§cPlayer not found: " + args[1]);
				return;
			}
		} else if (sender instanceof Player player) {
			if (!canResyncSelf(sender)) {
				sendNoPermission(sender);
				return;
			}
			target = player;
		} else {
			sender.sendMessage("§cUsage: /jeibridge resync <player|all>");
			return;
		}

		if (args.length >= 2 || !(sender instanceof Player self) || self.getUniqueId().equals(target.getUniqueId())) {
			plugin.refreshRecipeContent();
		}

		boolean synced = syncService.syncTo(target);
		if (synced) {
			if (sender instanceof Player self && self.getUniqueId().equals(target.getUniqueId())) {
				sender.sendMessage("§aYour recipes have been re-synced.");
			} else {
				sender.sendMessage("§aRe-synced recipes for " + target.getName() + ".");
			}
		} else {
			sender.sendMessage("§eCould not sync recipes for " + target.getName()
					+ " (unsupported client brand or empty payload).");
		}
	}

	private void handleInfo(CommandSender sender) {
		if (!canInfo(sender)) {
			sendNoPermission(sender);
			return;
		}

		var config = plugin.getPluginConfig();
		sender.sendMessage("§6JEI Recipe Bridge");
		sender.sendMessage("§7Enabled: §f" + config.enabled());
		sender.sendMessage("§7Sync on join: §f" + config.syncOnJoin());
		sender.sendMessage("§7Discover recipes on join: §f" + config.discoverRecipesOnJoin());
		sender.sendMessage("§7Filter invalid recipes: §f" + config.filterInvalidRecipes());
		sender.sendMessage("§7Recipe blacklist entries: §f" + config.recipeBlacklist().size());
		sender.sendMessage("§7ItemsAdder integration: §f" + (itemsAdderBridge.isAvailable() ? "detected" : "not found"));
		sender.sendMessage("§7ItemsAdder showcase recipes: §f" + config.itemsAdderRegisterShowcaseRecipes()
				+ (plugin.getItemsAdderShowcaseService() != null
				? " (" + plugin.getItemsAdderShowcaseService().registeredShowcaseCount() + " active)"
				: ""));
		sender.sendMessage("§7ItemsAdder wait for resource pack: §f" + config.itemsAdderWaitForResourcePack());
		if (itemsAdderBridge.isAvailable()) {
			sender.sendMessage("§7ItemsAdder catalog items: §f" + itemsAdderBridge.collectCatalogItems(
					config.itemsAdderSkipHiddenItems()
			).size());
			sender.sendMessage("§7ItemsAdder registered recipes: §f" + itemsAdderBridge.countCustomRecipes());
		}
		sender.sendMessage("§7Recipe sync bridge: §f" + (recipeBridge.isAvailable() ? "ready" : "dormant"));
		sender.sendMessage("§7Cached server recipes: §f" + recipeBridge.recipeCount());
		sender.sendMessage("§7Server version: §f" + Bukkit.getVersion());
		sender.sendMessage("§7Online players: §f" + Bukkit.getOnlinePlayers().size());
		sender.sendMessage("§7Compatibility: §fPaper/Purpur/Folia 1.21.2-26.1.x (single jar, reflection)");
		if (itemsAdderBridge.isAvailable()) {
			sender.sendMessage("§eNote: §7IA custom items cannot appear in JEI's item list without client IA support.");
			sender.sendMessage("§7Define real ItemsAdder recipes in IA YAML and accept the IA resource pack.");
		}
	}

	private void sendUsage(CommandSender sender) {
		sender.sendMessage("§eUsage: /jeibridge <reload|resync|info> [player|all]");
		if (canResyncSelf(sender)) {
			sender.sendMessage("§7Players can use §f/jeibridge resync §7to refresh their own JEI data.");
		}
		if (isBackendConsole(sender)) {
			sender.sendMessage("§7This is a Paper backend plugin. Run commands on the Paper console or in-game.");
		}
	}

	private static void sendNoPermission(CommandSender sender) {
		sender.sendMessage("§cYou do not have permission to use this command.");
	}

	private static boolean isBackendConsole(CommandSender sender) {
		return sender instanceof ConsoleCommandSender;
	}

	private static boolean canReload(CommandSender sender) {
		return isBackendConsole(sender) || sender.hasPermission(PERM_RELOAD);
	}

	private static boolean canResyncSelf(CommandSender sender) {
		return isBackendConsole(sender) || sender.hasPermission(PERM_RESYNC);
	}

	private static boolean canResyncOthers(CommandSender sender) {
		return isBackendConsole(sender) || sender.hasPermission(PERM_RESYNC_OTHERS);
	}

	private static boolean canInfo(CommandSender sender) {
		return isBackendConsole(sender) || sender.hasPermission(PERM_INFO);
	}

	private static List<String> filter(List<String> options, String input) {
		String lower = input.toLowerCase(Locale.ROOT);
		return options.stream()
				.filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
				.toList();
	}
}
