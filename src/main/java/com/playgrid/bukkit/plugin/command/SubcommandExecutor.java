package com.playgrid.bukkit.plugin.command;

import javax.ws.rs.WebApplicationException;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public abstract class SubcommandExecutor implements CommandExecutor {

	private String description = "";
	private String usage = "/pg <command>";
	private String[] permission = new String[0];
	private String permission_message = "You don't have <permission>";
	private String[] aliases = new String[0];
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public String getUsage() {
		return usage;
	}
	
	public void setUsage(String usage) {
		this.usage = usage;
	}
	
	public String[] getPermission() {
		return permission;
	}
	
	public void setPermission(String[] permission) {
		this.permission = permission;
	}
	
	public String getPermission_message() {
		return permission_message;
	}
	
	public void setPermission_message(String permission_message) {
		this.permission_message = permission_message;
	}
	
	public boolean hasPermission(CommandSender sender) {
		for (String permission : this.permission) {
			if (sender.hasPermission(permission)) {
				return true;
			}
		}
		return false;
	}
	
	public String[] getAliases() {
		return aliases;
	}
	
	public void setAliases(String[] aliases) {
		this.aliases = aliases;
	}
	
	public void sendWebApplicationExceptionMessage(CommandSender sender, WebApplicationException ex) {
		sendWebApplicationExceptionMessage(sender, ex, null);
	}
	
	public void sendWebApplicationExceptionMessage(CommandSender sender, WebApplicationException ex, String default_message) {
		StringBuilder sb = new StringBuilder("[PlayGridMC] " + ChatColor.GRAY);
		
		String message = ex.getMessage();
		if (message != null && !message.isEmpty()) {
			sb.append(message);
			
		} else if (default_message != null && !default_message.isEmpty()) {
			sb.append(default_message);
			
		} else {
			sb.append("Unable to process your request at this time");
		}
		sender.sendMessage(sb.toString());
	}
}