package com.playgrid.bukkit.plugin.permission;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.playgrid.api.entity.Player;
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

		boolean enable_groups = plugin.getConfig().getBoolean("player.enable_groups", true);

		if (plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
			RegisteredServiceProvider<Permission> rsp;
			rsp = plugin.getServer().getServicesManager().getRegistration(Permission.class);

			if (rsp != null) {
				provider = rsp.getProvider();

				String msg = String.format("Detected Vault permissions provider %s", provider.getName());
				plugin.getLogger().info(msg);

				try {
					if (!provider.hasGroupSupport()) {
						msg = "%s does not provide group support";
						disable(String.format(msg, provider.getName()));
						return;
					} 

				} catch(NoSuchMethodError e) {
					// unable to determine group support at this time, carry on
				}

				if (!enable_groups) {
					disable("Set 'player.enable_groups: true' in config.yml to enable PlayGrid permission group support");
					return;
				}
				
				try {
					// Setup Registration and Membership groups
					World world = null;  // null world for global groups
					for (Group group : Group.getGroups()) {
						String path = group.getGroupPath();
						String[] permissions = group.getGroupPermissions();
						for (String permission : permissions) {
							provider.groupAdd(world, path, permission);
						}
					}
					
				} catch (Exception e) {
					disable("Unable to initialize base playgrid.* groups");
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
		
		String group_path;
		if (player.entitlements.length != 0) {
			String entitlement = player.entitlements[player.entitlements.length - 1];
			group_path = Group.getGroupPath(entitlement);
		
		} else if (player.membership != null) {
			group_path = Group.getGroupPath(player.membership);
		
		} else {
			group_path = Group.getGroupPath(player.registration);
		}

		if (!addGroup(player, group_path)) {
			plugin.getLogger().warning(String.format("Unable to add %s to the '%s' group", player.name, group_path));
			return null;
		}

		return group_path;
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

		World world = null;  // null world for global groups
		String[] groups = provider.getPlayerGroups(world, player.name);
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

		group = group.toLowerCase();
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

		group = group.toLowerCase();
		try {
			World world = null;  // null world for global groups
			return provider.playerRemoveGroup(world, player.name, group);

		} catch (UnsupportedOperationException e) {
			disable(e.getMessage());
		}

		return false;
	}

	/**
	 * Group Enum
	 */
	public enum Group {
		// Registration Groups
		UNREGISTERED ("playgrid.unregistered", new String[0]),
		REGISTERED ("playgrid.registered", new String[0]),
		VERIFIED ("playgrid.verified", new String[0]),
		
		// Member Groups
		MEMBER ("playgrid.membership.member", new String[0]),
		STAFF ("playgrid.membership.staff", new String[] {"playgrid.command.ban"}),
		ADMIN ("playgrid.membership.admin", new String[] {"playgrid.command.ban"});
		
		private String path;
		private String[] permissions;
		
		Group(String path, String[] permissions) {
			this.path = path;
			this.permissions = permissions;
		}

		/**
		 * Get Registration Groups
		 * @return all Registration Groups
		 */
		static Group[] getRegistrationGroups() {
			return new Group[]{UNREGISTERED, REGISTERED, VERIFIED};
		}

		/**
		 * Get Membership Groups
		 * @return all Membership Groups
		 */
		static Group[] getMembershipGroups() {
			return new Group[]{MEMBER, STAFF, ADMIN};
		}

		/**
		 * Get all Registration and Membership Groups
		 * @return all pre-defined Groups
		 */
		static Group[] getGroups() {
			return Group.values();
		}

		/**
		 * Get Group by Registration or Membership name
		 * @param name 
		 * @return Group
		 */
		static Group getGroup(String name) {
			try {
				return valueOf(name.toUpperCase());
				
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
		
		/**
		 * Get Fully Qualified Group Path
		 * 
		 * 	If name is does not match pre-defined Registration or Membership group
		 * 	then assume that it is the name of an Entitlement 
		 * 
		 * @param name 
		 * @return fully qualified Group path
		 */
		static String getGroupPath(String name) {
			Group group = getGroup(name);
			if (group != null) {
				return group.path;
			
			} else {
				return "playgrid.entitlement." + name.toLowerCase();
			}
		}
		
		/**
		 * Get Group Path
		 * @return fully qualified Group path
		 */
		public String getGroupPath() {
			return this.path;
		}

		/**
		 * Get Group Permissions
		 * @return permissions
		 */
		public String[] getGroupPermissions() {
			return this.permissions;
		}
	}
}
