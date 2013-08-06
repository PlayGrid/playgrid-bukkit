package com.playgrid.bukkit.plugin;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import com.playgrid.api.client.RestAPI;
import com.playgrid.api.client.manager.GameManager;
import com.playgrid.api.entity.Game;
import com.playgrid.api.entity.GameResponse;
import com.playgrid.api.entity.Player;
import com.playgrid.bukkit.plugin.listener.PlayerConnectionListener;
import com.playgrid.bukkit.plugin.permissions.Permissions;
import com.playgrid.bukkit.plugin.task.HeartbeatTask;


public class PlayGridMC extends JavaPlugin {

	public Game game;
	public Permissions permissions;

	private final Map<String, Player> activePlayers = new HashMap<String, Player>();
	
	
	/**
	 * Enable PlayGridMC Plugin
	 */
	@Override
	public void onEnable() {
		
		permissions = new Permissions(this);                                    // Initialize Features
		
		
		getConfig().options().copyDefaults(true);                               // Get configuration
		saveDefaultConfig();

		
		String token    = getConfig().getString("api.secret_key");              // Setup API
		String url      = getConfig().getString("api.url");
		String version  = getConfig().getString("api.version");
		
		RestAPI.getConfig().setAccessToken(token);
		RestAPI.getConfig().setURL(url);
		RestAPI.getConfig().setVersion(version);

		GameManager gameManager = RestAPI.getInstance().getGamesManager();

		GameResponse gameResponse = gameManager.connect();                      // Connect Game // TODO (JP): What happens with bad token?
		game = gameResponse.resources;
		getLogger().info(String.format("Connected game: %s", game.name));


		new PlayerConnectionListener(this);                                     // Initialize Listeners
		new HeartbeatTask(this);                                                // Initialize Heartbeat
		
	}

	

	/**
	 * Disable PlayGridMC Plugin
	 */
	@Override
	public void onDisable() {
		
		// TODO: (JP) Disable Listeners & Tasks

		GameManager gameManager = RestAPI.getInstance().getGamesManager();

		GameResponse gameResponse = gameManager.disconnect();                   // Disconnect Game
		game = gameResponse.resources;
		getLogger().info(String.format("Disconnected game: %s", game.name));

	}

	

	/**
	 * Store Player in the activePlayers cache
	 * @param player
	 */
	public void addPlayer(Player player) {
		
		activePlayers.put(player.name, player);

	}

	
	
	/**
	 * Retrieve Player from the activePlayers cache
	 * @param player_token
	 * @return Player
	 */
	public Player getPlayer(String player_token) {

		return activePlayers.get(player_token);

	}

	
	
	/**
	 * Remove Player from activePlayers cache
	 * @param player_token
	 * @return the removed Player
	 */
	public Player removePlayer(String player_token) {
		
		return activePlayers.remove(player_token);
		
	}
	
}
