package com.playgrid.bukkit.plugin.listener;

import java.util.ArrayList;
import java.util.logging.Level;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.event.player.PlayerQuitEvent;

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
		Player pPlayer = new Player(); // create provisional Player
		
		String name = event.getPlayer().getName();
		String player_uid = event.getPlayer().getUniqueId().toString().replaceAll("-", "");
		
		try {

			try {
				PlayerManager playerManager = RestAPI.getInstance().getPlayerManager();
				pPlayer = playerManager.authorize(name, player_uid);

			} catch (NotFoundException e) {
				plugin.getLogger().severe(e.getMessage());
				
				// TODO: (JP) Does creating a provisional Player make sense any more?
				pPlayer.name = name;
				pPlayer.uid = player_uid;

			} catch (ServerErrorException e) {
				plugin.getLogger().severe(e.getMessage());
				
				String msg = "Unable to process your request at this time, please try again.";
				event.disallow(Result.KICK_OTHER, msg);
				
				plugin.removePlayer(name);
				plugin.permissions.removeGroups(pPlayer);

				return;
			}
			
			if (pPlayer.authorized) {
				plugin.setPlayer(pPlayer);

			} else {
				event.disallow(Result.KICK_BANNED, pPlayer.message);
				
				plugin.removePlayer(name);
				plugin.permissions.removeGroups(pPlayer);
			}

		} catch (Exception e) {
			plugin.getLogger().log(Level.SEVERE, "", e);
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
			plugin.setPlayer(pPlayer);

			event.getPlayer().sendMessage(pPlayer.message);

			String group = plugin.permissions.setGroup(pPlayer);

			String msg = String.format("%s joined", pPlayer.name); 
			if (group != null) {
				msg += String.format(" and was added to the '%s' group", group); 
			}
			plugin.getLogger().info(msg);

			// retrieve and execute any scripts
			ArrayList<CommandScript> scripts = pPlayer.getScripts();
			if (scripts != null) {
				for (CommandScript script : scripts) {
					plugin.executeCommandScript(script);
				}
			}

			// retrieve and execute any order lines
			ArrayList<OrderLine> lines = pPlayer.getLines();
			if (lines != null) {
				for (OrderLine line : lines) {
					org.bukkit.entity.Player bPlayer = event.getPlayer();
					plugin.executeOrderLine(bPlayer, line);
				}
			}

		} catch (WebApplicationException e) {
			plugin.getLogger().severe(e.getMessage());

		} catch (Exception e) {
			plugin.getLogger().log(Level.SEVERE, "", e);
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		try {
			String name = event.getPlayer().getName();

			Player pPlayer = plugin.removePlayer(name);
			plugin.permissions.removeGroups(pPlayer);
			pPlayer = quit(pPlayer);

			plugin.getLogger().info(pPlayer.name + " has left.");

		} catch (WebApplicationException e) {
			plugin.getLogger().severe(e.getMessage());

		} catch (Exception e) {
			plugin.getLogger().log(Level.SEVERE, "", e);
		}
	}

	private Player join(Player player) {
		// TODO: (JP) Revisit, if player.url is null then should we even have a player?
		if (player.url == null) {
			// Players with ERROR status are not real, return
			return player;
		}

		String json_stats_payload = plugin.stats.getPlayerStats(player.name);

		PlayerManager playerManager = RestAPI.getInstance().getPlayerManager();
		return playerManager.join(player, json_stats_payload);
	}

	private Player quit(Player player) {
		// TODO: (JP) Revisit, if player.url is null then should we even have a player?
		if (player.url == null) {
			// Players with ERROR status are not real, return
			return player;
		}

		String json_stats_payload = plugin.stats.getPlayerStats(player.name);

		PlayerManager playerManager = RestAPI.getInstance().getPlayerManager();
		return playerManager.quit(player, json_stats_payload);
	}
}
