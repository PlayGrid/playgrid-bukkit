package com.playgrid.bukkit.plugin.permissions;

import java.util.ArrayList;
import java.util.List;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.playgrid.bukkit.plugin.PlayGridMC;



public class Permissions {
	
	private final PlayGridMC plugin;
	private Permission provider;
	private boolean enabled = true;

	
	/**
	 * Constructor
	 */
	public Permissions(PlayGridMC plugin) {

		this.plugin = plugin;

        if(plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {

			RegisteredServiceProvider<Permission> rsp;
		    rsp = plugin.getServer().getServicesManager().getRegistration(Permission.class);
		    
		    if (rsp != null) {
		    	provider = rsp.getProvider();
		    	
		    	String msg = String.format("Detected Vault permissions provider %s", provider.getName());
		    	plugin.getLogger().info(msg);
		    	return;

		    } else {
		    	disable("Vault permissions provider not found");

		    }
		    
	    } else {
	    	disable("Vault not found");
	    
	    }

	}
	
	
	/**
	 * Is Enabled
	 * @return boolean
	 */
	private boolean isEnabled() {
		if (provider != null && enabled) {
			return true;
		}
		
    	return false;
	}

	
	
	/**
	 * Disable Permissions
	 * @param reason
	 */
	private void disable(String reason) {
		plugin.getLogger().warning("Disabling permissions support: " + reason);
		enabled = false;

	}
	
	
	/**
	 * Add Groups
	 * @param player
	 * @param groups
	 * @return boolean
	 */
	public boolean addGroups(Player player, String[] groups) {
		if (!isEnabled()) { return false; }
		
		List<String> successList = new ArrayList<String>();
		List<String> failList = new ArrayList<String>();
		
		for (String group : groups) {
			if (addGroup(player, group)) {
				successList.add(group);
				continue;
			}
			failList.add(group);
		}
		
		String successMsg = String.format("[PlayGrid] You have been added to the %s groups", successList);
		String failMsg = String.format("[PlayGrid] Failed to add you to the %s groups", failList);

		player.sendMessage(ChatColor.GREEN + successMsg);
		if (failList.size() > 0) {
			player.sendMessage(ChatColor.RED + failMsg);
			return false;
			
		}
		
		return true;
		
	}

	
	
	/**
	 * Set Groups - removes all groups before adding provided groups
	 * @param player
	 * @param groups
	 * @return boolean
	 */
	public boolean setGroups(Player player, String[] groups) {
		if (!isEnabled()) { return false; }
		
		removeGroups(player);
		return addGroups(player, groups);
	}
	
	
	
	/**
	 * Remove Groups
	 * @param player
	 * @return boolean
	 */
	public boolean removeGroups(Player player) {
		if (!isEnabled()) { return false; }
		
		String[] groups = new String[] {};
		
		try {
			groups = provider.getPlayerGroups(player);
			
		} catch (UnsupportedOperationException e) {
			disable(e.getMessage());
		
		}

		for (String group : groups) {
			removeGroup(player, group);
		}
		return true;                                                            // FIXME (JP): Test for truth?
	}
	
	
	
	/**
	 * Add Group
	 * @param player
	 * @param group
	 * @return boolean
	 */
	public boolean addGroup(Player player, String group) {
		if (!isEnabled()) { return false; }
		
		try {
			return provider.playerAddGroup(player, group);
		
		} catch (UnsupportedOperationException e) {
			disable(e.getMessage());
		
		}
		
		return false;

	}
	
	
	
	/**
	 * Remove Group
	 * @param player
	 * @param group
	 * @return boolean
	 */
	public boolean removeGroup(Player player, String group) {
		if (!isEnabled()) { return false; }

		try {
			return provider.playerRemoveGroup(player, group);
			
		} catch (UnsupportedOperationException e) {
			disable(e.getMessage());
		
		}
		
		return false;
		
	}
	
}
