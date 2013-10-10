package com.playgrid.bukkit.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.playgrid.api.client.RestAPI;
import com.playgrid.api.client.manager.GameManager;
import com.playgrid.api.client.manager.PlayerManager;
import com.playgrid.api.entity.CommandScript;
import com.playgrid.api.entity.Game;
import com.playgrid.api.entity.GameResponse;
import com.playgrid.api.entity.PendingOrderLine;
import com.playgrid.api.entity.Player;
import com.playgrid.api.entity.PlayerResponse;
import com.playgrid.bukkit.plugin.command.RegisterCommandExecutor;
import com.playgrid.bukkit.plugin.handler.LogHandler;
import com.playgrid.bukkit.plugin.listener.PlayerConnectionListener;
import com.playgrid.bukkit.plugin.permission.Permissions;
import com.playgrid.bukkit.plugin.stats.Stats;
import com.playgrid.bukkit.plugin.task.HeartbeatTask;


public class PlayGridMC extends JavaPlugin {

	public Game game;
	public Permissions permissions;
	public Stats stats;

	private final Map<String, Player> activePlayers = new HashMap<String, Player>();
	
	
	/**
	 * Load PlayGridMC Plugin
	 */
	@Override
    public void onLoad() {
		
		getConfig().options().copyDefaults(true);                               // Get configuration
		saveDefaultConfig();

		boolean debug   = getConfig().getBoolean("debug");
		RestAPI.getConfig().setDebug(debug);
		
		@SuppressWarnings("unchecked")
		Map<String, String> pgp = (Map<String, String>) (Map<?, ?>)getConfig().getConfigurationSection("pgp").getValues(true);
		
		RestAPI.getConfig().setAccessToken(pgp.get("secret_key"));              // Setup API
		RestAPI.getConfig().setURL(pgp.get("url"));
		RestAPI.getConfig().setVersion(pgp.get("version"));
		
		StringBuilder uaBuilder = new StringBuilder(getDescription().getName());
		uaBuilder.append("/" + getDescription().getVersion());
		RestAPI.getConfig().appendUserAgent(uaBuilder.toString());
		
    }

	
	
	/**
	 * Enable PlayGridMC Plugin
	 */
	@Override
	public void onEnable() {
		
		try {
			
			permissions = new Permissions(this);                                // Initialize features
			stats = new Stats(this); 
			
			if (getConfig().getString("pgp.secret_key") == null) {              // Confirm secret_key
				StringBuilder builder = new StringBuilder();
				builder.append(ChatColor.RED);
				builder.append("[PlayGridMC] Config.yml property 'secret_key' is missing. "); 
				builder.append("Set your 'secret_key' to your key on playgrid.com -> Admin -> Plugin.");
				
				getServer().getConsoleSender().sendMessage(builder.toString());
				
				getServer().getPluginManager().disablePlugin(this);
				return;
			
			}
			
			try {
				CommandExecutor cmd = new RegisterCommandExecutor(this);        // Initialize commands
				getCommand("register").setExecutor(cmd);
				
			} catch (NullPointerException e) {
				StringBuilder builder = new StringBuilder();
				builder.append(ChatColor.RED);
				builder.append("[PlayGridMC] There was a problem enabling commands."); 
				getServer().getConsoleSender().sendMessage(builder.toString());
				
				throw e;
			
			}
				
			GameManager gameManager = RestAPI.getInstance().getGamesManager();

			GameResponse gameResponse = gameManager.connect();                  // Connect game
			game = gameResponse.resources;
			getLogger().info(String.format("Connected as %s", game.name));


			new PlayerConnectionListener(this);                                 // Initialize listeners
			new HeartbeatTask(this);                                            // Initialize heartbeat
						
		} catch (Exception e) {
			getLogger().severe(e.getMessage());
			getServer().getPluginManager().disablePlugin(this);
		
		}
    	
	}

	

	/**
	 * Disable PlayGridMC Plugin
	 */
	@Override
	public void onDisable() {
		
		try {
			
			// TODO: (JP) Disable Listeners & Tasks
	
			if (game == null) {                                                 // Exit if never connected game
				return;
			}
	
			GameManager gameManager = RestAPI.getInstance().getGamesManager();
	
			GameResponse gameResponse = gameManager.disconnect();               // Disconnect game
			game = gameResponse.resources;
			getLogger().info(String.format("Disconnected game: %s", game.name));

		} catch (Exception e) {
			getLogger().severe(e.getMessage());
		
		}

	}
	
	
	/**
	 * Get Config
	 * 
	 * Adds support to migrate config.yml files
	 */
	@Override
	public FileConfiguration getConfig() {
		FileConfiguration config = super.getConfig();
		
		if (!(config.contains("secret_key") || config.contains("player_status"))) {
			return config;
		}
		
		getLogger().info("Migrating config.yml to new schema");

		
		// Transpose
		config.set("pgp.secret_key", config.getString("secret_key"));
		config.set("secret_key", null);
		
		String url = config.getString("api_base");                              // FIXME (JP): Process URL scheme
		config.set("pgp.url", url);
		config.set("api_base", null);
		
		config.set("player.disable_stats", !config.getBoolean("track_stats"));
		config.set("track_stats", null);
		
		String action = config.getString("player_status.none.action");
		boolean authorization_required = ("kick".equals(action)) ? true : false;
		config.set("player.authorization_required", authorization_required);
		
		config.set("player.status", config.getConfigurationSection("player_status"));
		config.set("player_status", null);
		
		// TODO (JP): Backup config.yml file
		// TODO (JP): Save
		
		return config;
    }
	

	/**
	 * Store Player in the activePlayers cache
	 * @param player
	 */
	public void setPlayer(Player player) {
		
		activePlayers.put(player.name, player);

	}
	
	
	
	/**
	 * Reload Player and recache the PlayGrid Player
	 * @param player_token
	 * @return Player
	 */
	public Player reloadPlayer(String player_token) {
		
		PlayerManager playerManager = RestAPI.getInstance().getPlayerManager();

		Player player = getPlayer(player_token);
		if (player == null ) {
			return null;
		}
		PlayerResponse response = playerManager.reload(player);
		player = response.resources;
		setPlayer(player);
		
		return player;
	
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
	
	/**
	 * Execute a list of commands
	 * @param commands
	 * @return console log as string
	 */
	public String executeCommands(ArrayList<String> commands) throws CommandException {
		// add a log handler to capture log output from running commands
		LogHandler handler = new LogHandler();
		getServer().getLogger().addHandler(handler);
		for(String command : commands) {
			try {
				getLogger().info(command);	// send command to log so we have it in the log
				getServer().dispatchCommand(getServer().getConsoleSender(), command);	
			} catch (CommandException e) {
				getLogger().warning(e.toString());
				throw e;
			}
		}
		getServer().getLogger().removeHandler(handler);
		return handler.toString();
	}
	
	/**
	 * Execute a CommandScript
	 * @param script
	 */
	public void executeCommandScript(CommandScript script) {
		try {
			String log = executeCommands(script.commands);
			script.complete(log, true);
		} catch (CommandException e) {
			script.complete(e.getMessage(), false);
		}
	}
	
	
	/**
	 * Execute a PendingOrderLine
	 * @param bPlayer: bukkit player associated with order line
	 * @param line: PendingOrderLine to execute
	 */
	public void executePendingOrderLine(org.bukkit.entity.Player bPlayer, PendingOrderLine line) {
		// first run any commands
		if(line.script != null)
			executeCommandScript(line.script);
	}
}
