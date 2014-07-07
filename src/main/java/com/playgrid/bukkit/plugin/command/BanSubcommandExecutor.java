package com.playgrid.bukkit.plugin.command;

import java.util.Arrays;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.WebApplicationException;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.playgrid.api.client.RestAPI;
import com.playgrid.api.client.manager.PlayerManager;
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
		
		String[] durations = new String[]{"24h","48h","72h", "1w", "2w", "3w", "4w" };
		
		// validate command
		if (args.length == 0) {
			// no args, show usage
			sender.sendMessage(ChatColor.GRAY + getUsage());
			return true;
		
		} else if (args.length > 2) {
			sender.sendMessage(ChatColor.GRAY + "Too many arguements");
			sender.sendMessage(ChatColor.GRAY + getUsage());
			return true;

		} else if (Bukkit.getServer().getPlayer(args[0]) == null) {
 			sender.sendMessage(ChatColor.GRAY + args[0] + " is not online");
 			return true;
 		
		} else if (this.plugin.getPlayer(args[0]) == null) {
 			sender.sendMessage(ChatColor.GRAY + "Unable to identify PlayGrid player: " + args[0]);
 			return true;
 			
 		} else if (args.length == 2 && !Arrays.asList(durations).contains(args[1])) {
			sender.sendMessage(ChatColor.GRAY + "Invalid ban duration");
			sender.sendMessage(ChatColor.GRAY + getUsage());
			return true;
 		}

		String player_name = args[0];
		String duration = null;
		String action = "banned";
		if (args.length == 2) {
			duration = args[1];
			action = "suspended";
		}

		try {
			PlayerManager playerManager = RestAPI.getInstance().getPlayerManager();

			if (args.length == 2) {
				playerManager.suspend(this.plugin.getPlayer(player_name), duration);

			} else {
				playerManager.ban(this.plugin.getPlayer(player_name));
			}

		} catch (BadRequestException e) {
			
			sendWebApplicationExceptionMessage(sender, e, "Invalid player name or ban duration");
			sender.sendMessage(ChatColor.GRAY + getUsage());
			return true;
			
		} catch (WebApplicationException e) {
			sendWebApplicationExceptionMessage(sender, e);
			return true;
		}

		// get kick message
		String kick_message = "You have been banned by the administrator";
		if (duration != null) {
			StringBuilder sb = new StringBuilder("You have been suspended for ");
			sb.append(duration.substring(0, duration.length() - 1));
			if ("h".equals(duration.substring(duration.length() - 1))) {
				sb.append(" hours");
			} else {
				sb.append(" weeks");
			}
			sb.append(" by the administrator");
			kick_message = sb.toString();
		}
		
		Player player = Bukkit.getServer().getPlayer(player_name);
		player.kickPlayer(kick_message);

		sender.sendMessage("[PlayGridMC] " + ChatColor.GRAY + "Successfully " + action + " " + player_name);
		return true;
	}
}
