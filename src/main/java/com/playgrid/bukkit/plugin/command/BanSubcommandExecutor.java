package com.playgrid.bukkit.plugin.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.playgrid.bukkit.plugin.PlayGridMC;

public class BanSubcommandExecutor extends SubcommandExecutor {

	private PlayGridMC plugin;

	public BanSubcommandExecutor(PlayGridMC plugin) {
		if (plugin == null) {
			throw new IllegalArgumentException("plugin cannot be null");
		}
		this.plugin = plugin;
		setDescription("Manage player ban status");
		setUsage("/pg ban <player> [24h|48h|72h|1w|2w|3w|4w]");
		setAliases(new String[]{"b"});

		setPermission(new String[]{"playgrid.membership.staff", "playgrid.membership.admin"});
		setPermission_message("You don't have Staff or Admin permission");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!hasPermission(sender)) {
			sender.sendMessage(ChatColor.YELLOW + getPermission_message());
			return true;
		}

		if (args.length >= 1 && args.length <= 2) {
			sender.sendMessage(ChatColor.GREEN + "Success");
//			Lookup player
//			Execute api call
//			kick with message
//			send success message
			return true;
		
		} else {
			sender.sendMessage(ChatColor.GRAY + getUsage());
			return true;
		}
	}
}
