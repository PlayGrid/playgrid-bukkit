package com.playgrid.bukkit.plugin.listener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import javax.ws.rs.NotFoundException;

import org.bukkit.event.EventHandler;
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
import com.playgrid.api.entity.Game;
import com.playgrid.api.entity.OrderLine;
import com.playgrid.api.entity.Player;
import com.playgrid.api.entity.PlayerResponse;
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
			
			String player_token = event.getPlayer().getName();
			try {
				pPlayer = authorize(player_token);
				
			} catch (NotFoundException e) {
				plugin.getLogger().severe(e.getMessage());
	
				pPlayer = new Player();
				pPlayer.name = player_token;
				pPlayer.status = Player.Status.ERROR;
	
			} 
			
			if (pPlayer.unverified_days != 0) {
				pPlayer.status = Player.Status.UNVERIFIED;						// FIXME: (JP) The API does not return unverified
			}
			
			
			statusConfig = getPlayerStatusConfig(pPlayer);
			
			String message = (String) statusConfig.get("message");
			PlayerLoginEvent.Result result = PlayerLoginEvent.Result.KICK_OTHER;
	
			switch(pPlayer.status) {
				
				case AUTHORIZED:
				case ERROR:
				case NONE:
					break;
				
				case BANNED:
					message += "\n\nReason: " + pPlayer.reason;
					result = PlayerLoginEvent.Result.KICK_BANNED;
					break;
	
				case SUSPENDED:
					message += "\n\nReason: " + pPlayer.reason;
					
					String duration = getSuspensionDuration(pPlayer);
					message += String.format("\nSuspension ends in %s", duration);
					break;
				
				case UNVERIFIED:
					Integer max_unverified_days = (Integer)statusConfig.get("max_unverified_days");
					if (max_unverified_days != -1) {
						if (pPlayer.unverified_days > max_unverified_days) {
							message += "\n\nYou must verify your email address to continue playing.";
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
				event.disallow(result, message);
				plugin.removePlayer(player_token);
	
				plugin.permissions.removeGroups(event.getPlayer());
			
			}

		} catch (Exception e) {
			plugin.getLogger().severe(e.getMessage());
		
		}
		
	}



	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {

		try {
			
			String player_token = event.getPlayer().getName();
			Player pPlayer = plugin.getPlayer(player_token);
			
			if (pPlayer == null) {
				return;

			}
			
			pPlayer = join(pPlayer);
			plugin.setPlayer(pPlayer);
				
			Map<String, Object> statusConfig = getPlayerStatusConfig(pPlayer);
	
			String[] permission_groups = new String[] {};
			String group = (String) statusConfig.get("group");
			if (group != null) {
				permission_groups = new String[] {group};
			}
			String message = (String) statusConfig.get("message");
			
	
			switch(pPlayer.status) {
				case AUTHORIZED:
					if(pPlayer.permission_groups.length > 0) {
						permission_groups = pPlayer.permission_groups; 
						break;
					}

				case UNVERIFIED:
					int max_unverified_days = (Integer) statusConfig.get("max_unverified_days");
					if (max_unverified_days != -1) {
						message += "\n\nWarning! - you have " + Integer.toString(max_unverified_days - pPlayer.unverified_days) + " days left to verify your account.";
					}
					break;
	
				case SUSPENDED:
					String duration = getSuspensionDuration(pPlayer);
					message += String.format("\n\nSuspension ends in %s", duration);
					break;
					
				default:
					break;
			}
			
			event.getPlayer().sendMessage(message);
	
			plugin.permissions.setGroups(event.getPlayer(), permission_groups);
			
			plugin.getLogger().info(pPlayer.name + " joined and was added to the " + Arrays.toString(permission_groups) + " groups.");

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
			
			String player_token = event.getPlayer().getName();
			Player pPlayer = plugin.removePlayer(player_token);
			
			pPlayer = quit(pPlayer);
	
			plugin.getLogger().info(pPlayer.name + " has left.");

		} catch (Exception e) {
			plugin.getLogger().severe(e.getMessage());
		
		}

	}

	

	private Player authorize(String player_token) {

		PlayerManager playerManager = RestAPI.getInstance().getPlayerManager();

		Boolean authorization_required = plugin.getConfig().getBoolean("player.authorization_required");

		PlayerResponse response = playerManager.authorize(player_token, authorization_required);
		return response.resources;
		
	}
	
	
	
	private Player join(Player player) {
		if (player.url == null) {                                               // Players with ERROR status are not real, return
			return player;
		}
		
		PlayerManager playerManager = RestAPI.getInstance().getPlayerManager();

		String json_stats_payload = plugin.stats.getPlayerStats(player.name);	// Get player stats
		
		PlayerResponse response = playerManager.join(player, json_stats_payload);
		
		return response.resources;
		
	}



	private Player quit(Player player) {
		if (player.url == null) {                                               // Players with ERROR status are not real, return
			return player;
		}
		PlayerManager playerManager = RestAPI.getInstance().getPlayerManager();
		
		String json_stats_payload = plugin.stats.getPlayerStats(player.name);	// Get player stats
		
		PlayerResponse response = playerManager.quit(player, json_stats_payload);
		return response.resources;
		
	}
	
	
	
	private Map<String, Object> getPlayerStatusConfig(Player pPlayer) {
		
		String configPath = String.format("player.status.%s", pPlayer.status.toString().toLowerCase());
		Map<String, Object> statusConfig = plugin.getConfig().getConfigurationSection(configPath).getValues(true);
		
		String message = (String) statusConfig.get("message");
		if (message != null) {
			Game game = plugin.game;
			message = message.replace("$game_site$", game.website.toString());
			message = message.replace("$playername$", pPlayer.name);
		} else {
			message = "";
		}
		
		statusConfig.put("message", message);
		
		return statusConfig;
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
