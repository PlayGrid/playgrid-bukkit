package com.playgrid.bukkit.plugin.listener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import javax.ws.rs.NotFoundException;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.joda.time.DateTime;
import org.joda.time.Instant;
import org.joda.time.Period;

import com.playgrid.api.client.RestAPI;
import com.playgrid.api.client.manager.PlayerManager;
import com.playgrid.api.entity.CommandScript;
import com.playgrid.api.entity.OrderLine;
import com.playgrid.api.entity.Player;
import com.playgrid.bukkit.plugin.PlayGridMC;


public class PlayerConnectionListener implements Listener {
	
	private final PlayGridMC plugin;

	public PlayerConnectionListener(PlayGridMC plugin) {
		this.plugin = plugin;
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	@EventHandler
	public void onPlayerLogin(PlayerLoginEvent event) {
		
		try {
			Player pPlayer = null;
			Map<String, Object> statusConfig = null;
			
			String name = event.getPlayer().getName();
			String player_uid = event.getPlayer().getUniqueId().toString().replaceAll("-", "");

			try {
				PlayerManager playerManager = RestAPI.getInstance().getPlayerManager();
				pPlayer = playerManager.authorize(name, player_uid);
				
			} catch (NotFoundException e) {
				plugin.getLogger().severe(e.getMessage());
	
				pPlayer = new Player();
				pPlayer.name = name;
				pPlayer.uid = player_uid;
				pPlayer.status = Player.Status.ERROR;
			} 
			
			fixupUnverifiedStatus(pPlayer);
			
			statusConfig = plugin.getPlayerStatusConfig(pPlayer);
			
			StringBuilder messageBuilder = new StringBuilder((String) statusConfig.get("message"));
			PlayerLoginEvent.Result result = PlayerLoginEvent.Result.KICK_OTHER;
	
			switch(pPlayer.status) {
				
				case AUTHORIZED:
				case ERROR:
				case NONE:
					break;
				
				case BANNED:
					messageBuilder.append("\n\n");
					messageBuilder.append(String.format("Reason: %s", pPlayer.reason));
					
					result = PlayerLoginEvent.Result.KICK_BANNED;
					break;
	
				case SUSPENDED:
					messageBuilder.append("\n\n");
					messageBuilder.append(String.format("Reason: %s", pPlayer.reason));
					
					String duration = getSuspensionDuration(pPlayer);
					
					messageBuilder.append("\n");
					messageBuilder.append(String.format("Suspension ends in %s", duration));
					break;
				
				case UNVERIFIED:
					Integer max_unverified_days = (Integer)statusConfig.get("max_unverified_days");
					if (max_unverified_days != -1) {
						if (pPlayer.unverified_days > max_unverified_days) {
							
							messageBuilder.append("\n\n");
							messageBuilder.append("You must verify your email address to continue playing.");

							statusConfig.put("action", "kick");
						}
					}
					break;
				
				default:
					plugin.getLogger().info("Unhandled player status: " + pPlayer.status.toString());
					break;
			}
			
			if (statusConfig.get("action").toString().equalsIgnoreCase("allow")) {
				plugin.setPlayer(pPlayer);
				
			} else {
				event.disallow(result, messageBuilder.toString());
				plugin.removePlayer(name);
	
				plugin.permissions.removeGroups(event.getPlayer());
			}

		} catch (Exception e) {
			plugin.getLogger().severe(e.getMessage());
		}
	}



	private void fixupUnverifiedStatus(Player pPlayer) {
		if (pPlayer.unverified_days != 0) {
			pPlayer.status = Player.Status.UNVERIFIED;						    // FIXME: (JP) The API does not return unverified
		}
	}



	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerJoin(PlayerJoinEvent event) {

		try {
			
			String name = event.getPlayer().getName();
			Player pPlayer = plugin.getPlayer(name);
			
			if (pPlayer == null) {
				return;

			}
			
			pPlayer = join(pPlayer);
			fixupUnverifiedStatus(pPlayer);
			plugin.setPlayer(pPlayer);
			
			Map<String, Object> statusConfig = plugin.getPlayerStatusConfig(pPlayer);
			String[] config_group = new String[] {};

			String group = (String)statusConfig.get("group");
			if (group != null) {
				config_group = new String[] {group};
			}
			
			StringBuilder messageBuilder = new StringBuilder((String)statusConfig.get("message"));
			
	
			switch(pPlayer.status) {
				case BANNED:
					messageBuilder.append("\n\n");
					messageBuilder.append(String.format("Banned for: %s", pPlayer.reason));
					
					pPlayer.permission_groups = config_group;					// Force config_group only
					break;
				
				case SUSPENDED:
					String duration = getSuspensionDuration(pPlayer);

					messageBuilder.append("\n\n");
					messageBuilder.append(String.format("Suspension ends in %s", duration));

					pPlayer.permission_groups = config_group;					// Force config_group only
					break;
					
				case UNVERIFIED:
					int max_unverified_days = (Integer) statusConfig.get("max_unverified_days");
					if (max_unverified_days != -1) {
						
						messageBuilder.append("\n\n");
						messageBuilder.append("Warning! - you have ");
						messageBuilder.append(max_unverified_days - pPlayer.unverified_days);
						messageBuilder.append(" days left to verify your account.");
						
					}
					break;
					
				default:
					break;
			}
			
			event.getPlayer().sendMessage(messageBuilder.toString());
	
			plugin.permissions.setGroups(event.getPlayer(), pPlayer.permission_groups);
			
			String logMsg = String.format(
					"%s joined and was added to the %s groups.", 
					pPlayer.name, 
					Arrays.toString(pPlayer.permission_groups)
					);
			plugin.getLogger().info(logMsg);

			// retrieve and execute any scripts
			ArrayList<CommandScript> scripts = pPlayer.getScripts();
			if (scripts != null) {
				for(CommandScript script : scripts) {
					plugin.executeCommandScript(script);
				}
			}
			
			// retrieve and execute any order lines
			ArrayList<OrderLine> lines = pPlayer.getLines();
			if (lines != null) {
				for(OrderLine line : lines) {
					org.bukkit.entity.Player bPlayer = event.getPlayer();
					plugin.executeOrderLine(bPlayer, line);
				}
			}
		
		} catch (Exception e) {
			plugin.getLogger().severe(e.getMessage());
		}

	}
	
	
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		
		try {
			String name = event.getPlayer().getName();
			plugin.permissions.removeGroups(event.getPlayer());
			
			Player pPlayer = plugin.removePlayer(name);
			
			pPlayer = quit(pPlayer);
	
			plugin.getLogger().info(pPlayer.name + " has left.");

		} catch (Exception e) {
			plugin.getLogger().severe(e.getMessage());
		}
	}

	private Player join(Player player) {
		if (player.url == null) {                                               // Players with ERROR status are not real, return
			return player;
		}
		
		PlayerManager playerManager = RestAPI.getInstance().getPlayerManager();

		String json_stats_payload = plugin.stats.getPlayerStats(player.name);	// Get player stats
		
		return playerManager.join(player, json_stats_payload);

	}

	private Player quit(Player player) {
		if (player.url == null) {                                               // Players with ERROR status are not real, return
			return player;
		}
		PlayerManager playerManager = RestAPI.getInstance().getPlayerManager();
		
		String json_stats_payload = plugin.stats.getPlayerStats(player.name);	// Get player stats
		
		return playerManager.quit(player, json_stats_payload);
	}
	
	private String getSuspensionDuration(Player pPlayer) {
		DateTime suspended_until = new DateTime(pPlayer.suspended_until);
		Period period = new Period(new Instant(), suspended_until);
		String duration = "unknown"; 
		
		if (period.getDays() != 0) {
			duration = String.format("%s days", period.getDays());
		
		} else if (period.getHours() != 0) {
			duration = String.format("%s hours", period.getHours());
		
		} else if (period.getMinutes() != 0) {
			duration = String.format("%s minutes", period.getMinutes());
		
		} else if (period.getSeconds() != 0) {
			duration = String.format("%s seconds", period.getSeconds());
		}
		return duration;
	}
}
