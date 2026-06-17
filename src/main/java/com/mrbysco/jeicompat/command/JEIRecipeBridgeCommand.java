package com.mrbysco.jeicompat.command;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import com.mrbysco.jeicompat.RecipeSyncService;
import com.mrbysco.jeicompat.compat.itemsadder.ItemsAdderBridge;
import com.mrbysco.jeicompat.nms.RecipeBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JEIRecipeBridgeCommand implements CommandExecutor, TabCompleter {
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
	public boolean onCommand(
			@NotNull CommandSender sender,
			@NotNull Command command,
			@NotNull String label,
			@NotNull String[] args) {
		if (args.length == 0) {
			sendUsage(sender);
			return true;
		}

		return switch (args[0].toLowerCase(Locale.ROOT)) {
			case "reload" -> handleReload(sender);
			case "resync" -> handleResync(sender, args, label);
			case "info" -> handleInfo(sender);
			default -> {
				sendUsage(sender);
				yield true;
			}
		};
	}

	private boolean handleReload(CommandSender sender) {
		if (!canReload(sender)) {
			sendNoPermission(sender);
			return true;
		}

		plugin.reloadPluginConfig();
		sender.sendMessage("§aJEI Recipe Bridge configuration reloaded.");
		return true;
	}

	private boolean handleResync(CommandSender sender, String[] args, String label) {
		if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
			if (!canResyncOthers(sender)) {
				sendNoPermission(sender);
				return true;
			}

			plugin.refreshRecipeContent();
			syncService.resyncAll();
			sender.sendMessage("§aRe-synced recipes for all online players.");
			return true;
		}

		Player target;
		if (args.length >= 2) {
			if (!canResyncOthers(sender)) {
				sendNoPermission(sender);
				return true;
			}

			target = Bukkit.getPlayerExact(args[1]);
			if (target == null) {
				sender.sendMessage("§cPlayer not found: " + args[1]);
				return true;
			}
		} else if (sender instanceof Player player) {
			if (!canResyncSelf(sender)) {
				sendNoPermission(sender);
				return true;
			}
			target = player;
		} else {
			sender.sendMessage("§cUsage: /" + label + " resync <player|all>");
			return true;
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
		return true;
	}

	private boolean handleInfo(CommandSender sender) {
		if (!canInfo(sender)) {
			sendNoPermission(sender);
			return true;
		}

		var config = plugin.getPluginConfig();
		sender.sendMessage("§6JEI Recipe Bridge");
		sender.sendMessage("§7Enabled: §f" + config.enabled());
		sender.sendMessage("§7Sync on join: §f" + config.syncOnJoin());
		sender.sendMessage("§7Discover recipes on join: §f" + config.discoverRecipesOnJoin());
		sender.sendMessage("§7Filter invalid recipes: §f" + config.filterInvalidRecipes());
		sender.sendMessage("§7Recipe blacklist entries: §f" + config.recipeBlacklist().size());
		sender.sendMessage("§7ItemsAdder integration: §f" + (itemsAdderBridge.isAvailable() ? "detected" : "not found"));
		sender.sendMessage("§7ItemsAdder showcase recipes: §f" + config.itemsAdderRegisterShowcaseRecipes());
		sender.sendMessage("§7Recipe sync bridge: §f" + (recipeBridge.isAvailable() ? "ready" : "dormant"));
		sender.sendMessage("§7Cached server recipes: §f" + recipeBridge.recipeCount());
		sender.sendMessage("§7Server version: §f" + Bukkit.getVersion());
		sender.sendMessage("§7Online players: §f" + Bukkit.getOnlinePlayers().size());
		sender.sendMessage("§7Compatibility: §fPaper/Purpur/Folia 1.21.2-26.1.x (single jar, reflection)");
		return true;
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

	@Override
	public @Nullable List<String> onTabComplete(
			@NotNull CommandSender sender,
			@NotNull Command command,
			@NotNull String alias,
			@NotNull String[] args) {
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

	private static List<String> filter(List<String> options, String input) {
		String lower = input.toLowerCase(Locale.ROOT);
		return options.stream()
				.filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
				.toList();
	}
}
