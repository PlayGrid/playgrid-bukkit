package com.playgrid.bukkit.plugin.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import com.playgrid.bukkit.plugin.PlayGridMC;

public class PGCommandExecutorManager implements CommandExecutor {

	private PlayGridMC plugin;
	private ArrayList<String> subcommand_names = new ArrayList<String>();
	private HashMap<String, SubcommandExecutor> subcommands = new HashMap<String, SubcommandExecutor>();

	public PGCommandExecutorManager(PlayGridMC plugin) {
		if (plugin == null) {
			throw new IllegalArgumentException("plugin cannot be null");
		}
		this.plugin = plugin;

		BanSubcommandExecutor banSubcommandExecutor = new BanSubcommandExecutor(this.plugin);
		registerSubcommand("ban", banSubcommandExecutor);

		plugin.getCommand("pg").setExecutor(this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length >= 1) {
			String subcommand = args[0];

			if (hasSubcommand(subcommand)) {
				args = Arrays.copyOfRange(args, 1, args.length);
				label = label + " " + subcommand;
				SubcommandExecutor subcommandExecutor = getSubcommandExecutor(subcommand);
				return subcommandExecutor.onCommand(sender, command, label, args);

			} else if (subcommand.equals("help") && args.length >= 2) {
				SubcommandExecutor subcommandExecutor = getSubcommandExecutor(args[1]);
				sender.sendMessage(subcommandExecutor.getUsage());
				return true;
			}
		}

		List<String> usage = new ArrayList<String>();
		for (String subcommand_name : this.subcommand_names) {
			SubcommandExecutor subcommand = getSubcommandExecutor(subcommand_name);
			usage.add(subcommand.getUsage());
		}
		
		sender.sendMessage(usage.toArray(new String[usage.size()]));
		return false; // force default usage
	}

	public void registerSubcommand(String subcommand, SubcommandExecutor subcommandExecutor) {
		if (subcommand == null || subcommandExecutor == null || subcommand.isEmpty()) {
			throw new IllegalArgumentException("invalid subcommand parameters specified");
		}
		subcommand = subcommand.toLowerCase();
		this.subcommand_names.add(subcommand);
		this.subcommands.put(subcommand, subcommandExecutor);
		for (String alias : subcommandExecutor.getAliases()) {
			this.subcommands.put(alias.toLowerCase(), subcommandExecutor);
		}
	}

	public void unregisterSubcommand(String subcommand) {
		subcommand = subcommand.toLowerCase();
		this.subcommand_names.remove(subcommand);
		SubcommandExecutor subcommandExecutor = this.subcommands.remove(subcommand);
		if (subcommandExecutor != null) {
			for (String alias : subcommandExecutor.getAliases()) {
				this.subcommands.remove(alias.toLowerCase());
			}
		}
	}

	public boolean hasSubcommand(String subcommand) {
		return this.subcommands.containsKey(subcommand.toLowerCase());
	}

	public SubcommandExecutor getSubcommandExecutor(String subcommand) {
		return this.subcommands.get(subcommand.toLowerCase());
	}
}
