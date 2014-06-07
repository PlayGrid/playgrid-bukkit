package com.playgrid.bukkit.plugin.permission;

import java.util.Arrays;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.playgrid.api.entity.Player;
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

		boolean enable_groups = plugin.getConfig().getBoolean("player.enable_groups", false);

		if (plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
			RegisteredServiceProvider<Permission> rsp;
			rsp = plugin.getServer().getServicesManager().getRegistration(Permission.class);

			if (rsp != null) {
				provider = rsp.getProvider();

				String msg = String.format("Detected Vault permissions provider %s", provider.getName());
				plugin.getLogger().info(msg);

				if (!provider.hasGroupSupport()) {
					msg = "%s does not provide group support";
					disable(String.format(msg, provider.getName()));
				
				} else if (!enable_groups) {
					if (this.groups.length > 0) {
						msg = "Set 'player.enable_groups: true' in config.yml ";
						msg += "to enable PlayGrid group management for %s.";
						msg = String.format(msg, Arrays.toString(this.groups));
						disable(msg);
					}

				} else {
					plugin.getLogger().info(String.format("Using PlayGrid groups %s", Arrays.toString(this.groups)));
				}

			} else {
				disable("Vault permissions provider not found");
			}

		} else {
			disable("Vault not found");
		}
	}

	/**
	 * Is enabled
	 * 
	 * @return enabled status boolean
	 */
	private boolean isEnabled() {
		if (provider != null && enabled) {
			return true;
		}

		return false;
	}

	/**
	 * Disable permissions
	 * 
	 * @param reason
	 *            reason for disabling permissions
	 */
	private void disable(String reason) {
		plugin.getLogger().warning("Disabling group support: " + reason);
		enabled = false;
	}

	/**
	 * Set player membership group
	 * 
	 * @param player
	 * 			  player to place in an appropriate membership group 
	 * @return
	 * 	          group the player was placed in 
	 */
	public String setGroup(Player player) {
		if (!isEnabled()) {
			return null;
		}

		removeGroups(player);
		
		String group;
		if (player.entitlements.length != 0) {
			group = "playgrid.entitlement." + player.entitlements[player.entitlements.length - 1];
		
		} else if (player.membership != null) {
			group = "playgrid.membership." + player.membership;
		
		} else {
			group = "playgrid." + player.registration;
		}

		group = group.toLowerCase();
		if (!addGroup(player, group)) {
			plugin.getLogger().warning(String.format("Unable to add %s to the '%s' group", player.name, group));
			return null;
		}

		return group;
	}

	/**
	 * Remove groups from player
	 * 
	 * @param player
	 *            player to remove all PlayGrid groups from
	 */
	public void removeGroups(Player player) {
		if (!isEnabled()) {
			return;
		}

		String[] groups = provider.getPlayerGroups(Bukkit.getPlayer(player.name));
		for (String group : groups) {
			if (group.startsWith("playgrid.")) {
				if (!removeGroup(player, group)) {
					plugin.getLogger().warning(String.format("Unable to remove %s from the '%s' group", player.name, group));
				}
			}
		}
	}

	/**
	 * Add group to player
	 * 
	 * @param player
	 *            player to add group to
	 * @param group
	 *            group to add to player
	 * @return success or failure
	 */
	public boolean addGroup(Player player, String group) {
		if (!isEnabled()) {
			return false;
		}

		try {
			World world = null;  // null world for global groups
			return provider.playerAddGroup(world, player.name, group);

		} catch (UnsupportedOperationException e) {
			disable(e.getMessage());
		}

		return false;
	}

	/**
	 * Remove group from player
	 * 
	 * @param player
	 *            player to remove group from
	 * @param group
	 *            group to remove from player
	 * @return success or failure
	 */
	public boolean removeGroup(Player player, String group) {
		if (!isEnabled()) {
			return false;
		}

		try {
			World world = null;  // null world for global groups
			return provider.playerRemoveGroup(world, player.name, group);

		} catch (UnsupportedOperationException e) {
			disable(e.getMessage());
		}

		return false;
	}
}
