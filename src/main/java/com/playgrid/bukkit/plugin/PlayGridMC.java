package com.playgrid.bukkit.plugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.playgrid.api.entity.GameConnect;
import com.playgrid.api.entity.OrderLine;
import com.playgrid.api.entity.Player;
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
		// add the underlying server user agent string
		RestAPI.getConfig().appendUserAgent(getServerUserAgent());
		
    }

	
	
	/**
	 * Enable PlayGridMC Plugin
	 */
	@Override
	public void onEnable() {
		
		try {
			
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
				
			GameManager gameManager = RestAPI.getInstance().getGameManager();

			game = gameManager.self();
			GameConnect connect = gameManager.connect(game);                  // Connect game
			getLogger().info(String.format(connect.message));
			if(connect.warning_message != null && !connect.warning_message.isEmpty()) {
				StringBuilder builder = new StringBuilder();
				builder.append("[PlayGridMC] ");
				builder.append(ChatColor.YELLOW);
				builder.append(connect.warning_message); 
				getServer().getConsoleSender().sendMessage(builder.toString());				
			}

			
			// Update game.permission_groups with config.yml groups
			List<String> permission_groups = new ArrayList<String>(Arrays.asList(game.permission_groups));

			String configPath = "player.status";
			Map<String, Object> statusConfig = getConfig().getConfigurationSection(configPath).getValues(false);

			for (String key : statusConfig.keySet()) {
				String group = getConfig().getString(configPath + "." + key + ".group");

				if (group != null && !permission_groups.contains(group)) {
					permission_groups.add(group);
			
				}
			
			}
			game.permission_groups = permission_groups.toArray(new String[permission_groups.size()]); 

			
			permissions = new Permissions(this, game.permission_groups);        // Initialize features
			stats = new Stats(this); 

			new PlayerConnectionListener(this);                                 // Initialize listeners
			new HeartbeatTask(this, game.heartbeat_interval);                   // Initialize heartbeat
			
			
			getLogger().info(String.format("%s %s successfully enabled", getDescription().getName(), getDescription().getVersion()));
			
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
	
			GameManager gameManager = RestAPI.getInstance().getGameManager();
	
			gameManager.disconnect(game);               // Disconnect game
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
		
		// Transpose older configuration options to new schema
		if (config.contains("secret_key")) {                                    // legacy 1.x
			config.set("pgp.secret_key", config.getString("secret_key"));
			config.set("secret_key", null);
		}
		
		if (config.contains("api_base")) {                                      // legacy 1.x
			String url = config.getString("api_base");
			config.set("pgp.url", url);
			config.set("api_base", null);
		}
		
		if (config.contains("track_stats")) {                                   // legacy 1.x
			config.set("player.disable_stats", !config.getBoolean("track_stats"));
			config.set("track_stats", null);
		}

		if (config.contains("player.disable_stats")) {                          // changed in 2.16
			config.set("player.enable_stats", !config.getBoolean("player.disable_stats"));
			config.set("player.disable_stats", null);
		}
		
		if (config.contains("player_status")) {                                 // legacy 1.x
			String action = config.getString("player_status.none.action");
			boolean authorization_required = ("kick".equals(action)) ? true : false;
			config.set("player.authorization_required", authorization_required);
			
			config.set("player.status", config.getConfigurationSection("player_status"));
			config.set("player_status", null);
		}
		
		// TODO (JP): Backup current config.yml
		// TODO (JP): Save migrated config.yml
		
		return config;
    }
	
	public String getPlayerLocale(org.bukkit.entity.Player player) {
        String locale;

        try {
            /*
                It seems an API call has not yet been included, therefore
                we have to use reflection to obtain the locale.
            */
            Object getHandle = player.getClass().getMethod("getHandle", (Class<?>[]) null).invoke(player, (Object[]) null);
            Field language = getHandle.getClass().getDeclaredField("locale");
            language.setAccessible(true);
            locale = (String)language.get(getHandle);

        } catch (Throwable e){
            return("en-US");
        }
        return locale;
	}
	
	public void activatePlayerLocale(org.bukkit.entity.Player player) {
		String locale = this.getPlayerLocale(player);
		RestAPI.getConfig().setLocale(locale);
	}

	

	/**
	 * Get Player Status Config
	 * 
	 * @param player
	 * @return player status map
	 */
	public Map<String, Object> getPlayerStatusConfig(Player player) {
		
		String configPath = String.format("player.status.%s", player.status.toString().toLowerCase());
		Map<String, Object> statusConfig = getConfig().getConfigurationSection(configPath).getValues(true);
		
		// Process message
		String message = (String) statusConfig.get("message");
		if (message != null) {
			message = message.replace("$game_site$", game.website.toString());
			message = message.replace("$playername$", player.name);

			message = message.replace("$username$", player.name);				// support legacy configs
		
		} else {
			message = "";
		
		}
		
		statusConfig.put("message", message);
		
		try {
			// Process max_unverified_days
			if (statusConfig.containsKey("max_unverified_days")) {
				if (((String) statusConfig.get("max_unverified_days")).toLowerCase().equals("any")) {
					statusConfig.put("max_unverified_days", -1);
	
				}
			}
		} catch (ClassCastException e) {
		}
		
		return statusConfig;
		
	}
	
	
	/**
	 * Get the type and version of underlying server
	 */
	public String getServerUserAgent() {
		String cb_version;
		String cb_name;
		String userAgent;
		
		cb_name = getServer().getName();
		cb_version = getServer().getBukkitVersion();
		userAgent = cb_name+"/"+cb_version;
		
		// have to parse in order to get MC version
		String long_version = getServer().getVersion();
	    Pattern pattern = Pattern.compile("\\(MC: (.*?)\\)");
	    Matcher matcher = pattern.matcher(long_version);
	    if(matcher.find()) {
	    	String mc_version;
	    	
	    	mc_version = matcher.group(1);		
	    	userAgent += " "+"Minecraft/"+mc_version;
	    }
	    return(userAgent);
	}


	
	/**
	 * Store Player in the activePlayers cache
	 *  - updates permission_groups with player.status group
	 * @param player
	 */
	public void setPlayer(Player player) {

		List<String> permission_groups = new ArrayList<String>(Arrays.asList(player.permission_groups));

		Map<String, Object> statusConfig = getPlayerStatusConfig(player);
		
		String group = (String) statusConfig.get("group");
		if (group != null && !permission_groups.contains(group)) {
			permission_groups.add(group);
		}
		player.permission_groups = permission_groups.toArray(new String[permission_groups.size()]);

		activePlayers.put(player.name, player);

	}
	
	
	
	/**
	 * Reload Player and recache the PlayGrid Player
	 * @param name
	 * @return Player
	 */
	public Player reloadPlayer(String name) {
		
		PlayerManager playerManager = RestAPI.getInstance().getPlayerManager();

		Player player = getPlayer(name);
		if (player == null ) {
			return null;
		}
		player = playerManager.reload(player);
		setPlayer(player);
		
		return getPlayer(player.name);
	
	}

	
	
	/**
	 * Retrieve Player from the activePlayers cache
	 * @param name
	 * @return Player
	 */
	public Player getPlayer(String name) {

		return activePlayers.get(name);

	}

	
	
	/**
	 * Remove Player from activePlayers cache
	 * @param name
	 * @return the removed Player
	 */
	public Player removePlayer(String name) {
		
		return activePlayers.remove(name);
		
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
	public void executeCommandScript(CommandScript script) throws CommandException {
		try {
			String log = executeCommands(script.commands);
			script.complete(log, true);
		} catch (CommandException e) {
			script.complete(e.getMessage(), false);
			throw e;
		}
	}
	
	
	
	/**
	 * Execute a OrderLine
	 * @param bPlayer: bukkit player associated with order line
	 * @param line: PendingOrderLine to execute
	 */
	public void executeOrderLine(org.bukkit.entity.Player bPlayer, OrderLine line) {
		// first run any commands
		if(line.script != null)
			try {
				executeCommandScript(line.script);
				if(line.message.length() > 0) 
					bPlayer.sendMessage(line.message);
			} catch (CommandException e) {
				String message = "There was a problem completing your %s order.  Please contact %s support.";
				message = String.format(message, line.product.title, game.name);
				bPlayer.sendMessage(message);
			}
	}
}
