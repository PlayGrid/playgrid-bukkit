package com.playgrid.bukkit.plugin;

import java.util.HashMap;
import java.util.Map;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.playgrid.api.client.RestAPI;
import com.playgrid.api.client.manager.GameManager;
import com.playgrid.api.entity.Game;
import com.playgrid.api.entity.GameResponse;
import com.playgrid.api.entity.Player;
import com.playgrid.bukkit.plugin.listener.PlayerConnectionListener;
import com.playgrid.bukkit.plugin.task.HeartbeatTask;


public class PlayGridMC extends JavaPlugin {

	public Game game;
	public Permission permissionProvider;

	private final Map<String, Player> activePlayers = new HashMap<String, Player>();
	
	
	/**
	 * Enable PlayGridMC Plugin
	 */
	@Override
	public void onEnable() {

		initializePermissions();                                                // Initialize Features
		
		
		this.getConfig().options().copyDefaults(true);                          // Get configuration
		this.saveDefaultConfig();

		
		String token    = this.getConfig().getString("api.secret_key");         // Setup API
		String url      = this.getConfig().getString("api.url");
		String version  = this.getConfig().getString("api.version");
		
		RestAPI.getConfig().setAccessToken(token);
		RestAPI.getConfig().setURL(url);
		RestAPI.getConfig().setVersion(version);

		GameManager gameManager = RestAPI.getInstance().getGamesManager();

		GameResponse gameResponse = gameManager.connect();                      // Connect Game // TODO (JP): What happens with bad token?
		this.game = gameResponse.resources;
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
		Game game = gameResponse.resources;
		getLogger().info(String.format("Disconnected game: %s", game.name));

	}

	

	/**
	 * Initialize Permissions Feature
	 * @return boolean
	 */
	private boolean initializePermissions() {
	    RegisteredServiceProvider<Permission> rsp;
	    rsp = getServer().getServicesManager().getRegistration(Permission.class);
	    
	    if (rsp != null) {
	    	permissionProvider = rsp.getProvider();
	    	
	    	String msg = String.format("Detected permissions provider - %s", permissionProvider.getName());
	    	getLogger().info(msg);
	    
	    } else {
	    	getLogger().warning("Permissions disabled.");
	    
	    }
	    
	    return permissionProvider != null;
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
