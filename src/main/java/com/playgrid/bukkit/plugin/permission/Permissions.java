package com.playgrid.bukkit.plugin.permission;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.playgrid.bukkit.plugin.PlayGridMC;



public class Permissions {
	
	private final PlayGridMC plugin;
	private final String[] groups;
	private Permission provider;
	private boolean enabled = true;


	/**
	 * Constructor
	 */
	public Permissions(PlayGridMC plugin) {

		this(plugin, new String[0]);
	
	}
	
	
	
	/**
	 * Constructor
	 */
	public Permissions(PlayGridMC plugin, String[] groups) {

		this.plugin = plugin;
		this.groups = groups;

        if(plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {

			RegisteredServiceProvider<Permission> rsp;
		    rsp = plugin.getServer().getServicesManager().getRegistration(Permission.class);
		    
		    if (rsp != null) {
		    	provider = rsp.getProvider();
		    	
		    	String msg = String.format("Detected Vault permissions provider %s", provider.getName());
		    	plugin.getLogger().info(msg);
		    	plugin.getLogger().info(String.format("Using PlayGrid groups %s", Arrays.toString(this.groups)));
		    	return;

		    } else {
		    	disable("Vault permissions provider not found");

		    }
		    
	    } else {
	    	disable("Vault not found");
	    
	    }

	}
	
	
	
	/**
	 * Is enabled
	 * @return	enabled status boolean
	 */
	private boolean isEnabled() {
		if (provider != null && enabled) {
			return true;
		}
		
    	return false;
	}

	
	
	/**
	 * Disable permissions
	 * @param	reason	reason for disabling permissions
	 */
	private void disable(String reason) {
		plugin.getLogger().warning("Disabling permissions support: " + reason);
		enabled = false;

	}
	
	
	
	/**
	 * Add groups to player
	 * @param 	player	player to add groups too
	 * @param 	groups	groups to add to player
	 */
	public void addGroups(Player player, String[] groups) {
		if (!isEnabled()) { return; }
		
		List<String> successList = new ArrayList<String>();
		List<String> failList = new ArrayList<String>();
		
		for (String group : groups) {
			if (!addGroup(player, group)) {
				failList.add(group);
			}
			successList.add(group);
		}
		
		String successMsg = String.format("[PlayGrid] You have been added to the %s groups", successList);
		String failMsg = String.format("[PlayGrid] Failed to add you to the %s groups", failList);

		if (successList.size() > 0) {
			player.sendMessage(ChatColor.GREEN + successMsg);
			
		}

		if (failList.size() > 0) {
			player.sendMessage(ChatColor.RED + failMsg);

			plugin.getLogger().warning(
					String.format(
							"Unable to add %s to the %s groups", 
							player.getName(), 
							failList
							)
					);
			
		}

	}

	
	
	/**
	 * Set groups removes all groups before adding provided groups
	 * @param 	player	player to add groups too
	 * @param 	groups	groups to add to player
	 */
	public void setGroups(Player player, String[] groups) {
		if (!isEnabled()) { return; }
		
		removeGroups(player);
		addGroups(player, groups);
	}
	
	
	
	/**
	 * Remove groups from player
	 * @param 	player	player to remove all PlayGrid groups from	
	 */
	public void removeGroups(Player player) {
		if (!isEnabled()) { return; }
		
		List<String> failList = new ArrayList<String>();
		
		for (String group : groups) {
			if (!removeGroup(player, group)) {
				failList.add(group);
			}

		}
		
		if (!failList.isEmpty()) {
			String msg = String.format(
					"Unable to remove %s from the %s groups", 
					player.getName(), 
					failList
					);
			plugin.getLogger().warning(msg);
		
		}

	}
	
	
	
	/**
	 * Add group to player
	 * @param 	player 	player to add group to
	 * @param 	group 	group to add to player
	 * @return 			success boolean
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
	 * Remove group from player
	 * @param	player	player to remove group from
	 * @param 	group	group to remove from player
	 * @return 			success boolean
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
