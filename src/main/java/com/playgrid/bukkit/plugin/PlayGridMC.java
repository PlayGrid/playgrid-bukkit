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
import com.playgrid.bukkit.plugin.task.HeartbeatTask;

public class PlayGridMC extends JavaPlugin {

	public Game game = null;
	private final Map<String, Player> activePlayers = new HashMap<String, Player>();
	
	
	@Override
	public void onEnable() {
		getLogger().info("onEnable has been invoked!");

		// Get config
		this.getConfig().options().copyDefaults(true);
		this.saveDefaultConfig();
		
		// Setup API
		String token    = this.getConfig().getString("api.secret_key");
		String url      = this.getConfig().getString("api.url");
		String version  = this.getConfig().getString("api.version");
		
		RestAPI.getConfig().setAccessToken(token);
		RestAPI.getConfig().setURL(url);
		RestAPI.getConfig().setVersion(version);

		GameManager gameManager = RestAPI.getInstance().getGamesManager();

		// Connect Game
		GameResponse gameResponse = gameManager.connect();
		this.game = gameResponse.resources;
		getLogger().info(String.format("Connected as game %s.", game.toString()));


		// Initialize Listeners
		new PlayerConnectionListener(this);
		
		// Initialize Heartbeat
		new HeartbeatTask(this);
		
	}

	
	
	@Override
	public void onDisable() {
		
		getLogger().info("onDisable has been invoked!");
		
		// TODO: (JP) Disable Listeners & Tasks

		GameManager gameManager = RestAPI.getInstance().getGamesManager();

		// Disconnect Game
		GameResponse gameResponse = gameManager.disconnect();
		Game game = gameResponse.resources;
		getLogger().info(game.toString());

	}


	/**
	 * Store an active Player by player_token
	 */
	public void addPlayer(Player player) {
		
		this.activePlayers.put(player.name, player);

	}

	
	
	/**
	 * Retrieve an active Player by player_token
	 */
	public Player getPlayer(String player_token) {

		return this.activePlayers.get(player_token);

	}

	
	
	/**
	 * Remove an active Player by player_token
	 */
	public Player removePlayer(String player_token) {
		
		return this.activePlayers.remove(player_token);
		
	}
	
}
