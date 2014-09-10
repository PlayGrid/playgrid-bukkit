package com.playgrid.bukkit.plugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.WebApplicationException;

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
import com.playgrid.bukkit.plugin.command.PGCommandExecutorManager;
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
	public Boolean debug = false;
	public Boolean onlineMode = true; // false if server is running in offline (unathenticated) mode
	public Boolean ignorePlayerUUID = false; // true if server version is pre 1.7.2 where UUIDs are inconsistent

	private final Map<String, Player> activePlayers = new HashMap<String, Player>();

	/**
	 * Load PlayGridMC Plugin
	 */
	@Override
	public void onLoad() {
		// Get configuration
		getConfig().options().copyDefaults(true);
		saveDefaultConfig();
		
		this.debug = getConfig().getBoolean("debug");
		RestAPI.getConfig().setDebug(this.debug);
		
		this.onlineMode = this.getServer().getOnlineMode();
		if(this.debug && !onlineMode) 
			getLogger().info("Server is running in offline mode");
		// check to see if serer version is pre-1.7.2, if so ignore player UUIDs
		String version = this.getServer().getBukkitVersion().split("-")[0];
		if(debug)
			getLogger().info("Server version: "+version);
		int version_a = Integer.parseInt(version.split("\\.")[0]);
		int version_b = Integer.parseInt(version.split("\\.")[1]);
		int version_c = Integer.parseInt(version.split("\\.")[2]);
		if(version_a < 1)
			this.ignorePlayerUUID = true;
		else if (version_a == 1 && version_b < 7)
			this.ignorePlayerUUID = true;
		else if (version_a == 1 && version_b == 7 && version_c < 2 )
			this.ignorePlayerUUID = true;
		if(this.debug && ignorePlayerUUID) 
			getLogger().info("Server is pre-1.7.2: ignoring player UUIDs");

		@SuppressWarnings("unchecked")
		Map<String, String> pgp = (Map<String, String>) (Map<?, ?>) getConfig().getConfigurationSection("pgp").getValues(true);

		// Setup API
		RestAPI.getConfig().setAccessToken(pgp.get("secret_key"));
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
			// Confirm secret_key
			if (getConfig().getString("pgp.secret_key") == null) {
				StringBuilder builder = new StringBuilder();
				builder.append(ChatColor.RED);
				builder.append("[PlayGridMC] Config.yml property 'secret_key' is missing. ");
				builder.append("Set your 'secret_key' to your key on playgrid.com -> Admin -> Plugin.");

				getServer().getConsoleSender().sendMessage(builder.toString());

				getServer().getPluginManager().disablePlugin(this);
				return;
			}

			try {
				// Initialize commands
				CommandExecutor cmd = new RegisterCommandExecutor(this);
				getCommand("register").setExecutor(cmd);
				
				new PGCommandExecutorManager(this);

			} catch (NullPointerException e) {
				StringBuilder builder = new StringBuilder();
				builder.append(ChatColor.RED);
				builder.append("[PlayGridMC] Could not enable commands");
				getServer().getConsoleSender().sendMessage(builder.toString());

				throw e;
			}

			// Connect
			GameManager gameManager = RestAPI.getInstance().getGameManager();
			game = gameManager.self();
			GameConnect connect = gameManager.connect(game);                  
			getLogger().info(connect.message);
			if(connect.warning_message != null && !connect.warning_message.isEmpty()) {
				StringBuilder builder = new StringBuilder();
				builder.append("[PlayGridMC] ");
				builder.append(ChatColor.YELLOW);
				builder.append(connect.warning_message); 
				getServer().getConsoleSender().sendMessage(builder.toString());				
			}

			// Initialize features
			permissions = new Permissions(this);
			stats = new Stats(this);

			// Initialize listeners & tasks
			new PlayerConnectionListener(this);
			new HeartbeatTask(this, game.heartbeat_interval);

			String name = getDescription().getName();
			String version = getDescription().getVersion();
			getLogger().info(String.format("%s %s successfully enabled", name, version));

		} catch (WebApplicationException e) {
			getLogger().severe(e.getMessage());
			getServer().getPluginManager().disablePlugin(this);

		} catch (Exception e) {
			getLogger().log(Level.SEVERE, e.getMessage(), e);
			getServer().getPluginManager().disablePlugin(this);
		}
	}

	/**
	 * Disable PlayGridMC Plugin
	 */
	@Override
	public void onDisable() {
		// TODO: (JP) Disable Listeners & Tasks
		if (game == null) {
			// Exit if game never connected
			return;
		}

		try {
			// Disconnect
			GameManager gameManager = RestAPI.getInstance().getGameManager();
			gameManager.disconnect(game);
			getLogger().info(String.format("Disconnected game: %s", game.name));

		} catch (WebApplicationException e) {
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

		// legacy 1.x
		if (config.contains("secret_key")) {
			config.set("pgp.secret_key", config.getString("secret_key"));
			config.set("secret_key", null);
		}

		// legacy 1.x
		if (config.contains("api_base")) {
			String url = config.getString("api_base");
			config.set("pgp.url", url);
			config.set("api_base", null);
		}

		// legacy 1.x
		if (config.contains("track_stats")) {
			config.set("player.disable_stats", !config.getBoolean("track_stats"));
			config.set("track_stats", null);
		}

		// changed in 2.16
		if (config.contains("player.disable_stats")) {
			config.set("player.enable_stats", !config.getBoolean("player.disable_stats"));
			config.set("player.disable_stats", null);
		}

		// legacy 1.x
		if (config.contains("player_status")) {
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
	 *  
	 * @param player
	 */
	public void setPlayer(Player player) {
		activePlayers.put(player.name, player);
	}

	/**
	 * Reload Player and recache the PlayGrid Player
	 * 
	 * @param name
	 * @return Player
	 */
	public Player reloadPlayer(String name) {
		PlayerManager playerManager = RestAPI.getInstance().getPlayerManager();

		Player player = getPlayer(name);
		if (player == null) {
			return null;
		}

		player = playerManager.reload(player);
		setPlayer(player);

		return getPlayer(player.name);
	}

	/**
	 * Retrieve Player from the activePlayers cache
	 * 
	 * @param name
	 * @return Player
	 */
	public Player getPlayer(String name) {
		return activePlayers.get(name);
	}
	

	/**
	 * Retrieve the UID for a bPlayer
	 * - considers whether to prepend 'offline!' if in offline mode or to return no uid if pre-1.7.2
	 * 
	 * @param bPlayer
	 */
	
	public String getUIDforPlayer(org.bukkit.entity.Player bPlayer) {
		if(this.ignorePlayerUUID) {
			return "";
		} else {
			String player_uid = bPlayer.getUniqueId().toString().replaceAll("-", "");
			if(!this.onlineMode) {
				return "offline!"+player_uid;
			} else {
				return player_uid;
			}
		}
	}
	

	/**
	 * Remove Player from activePlayers cache
	 * 
	 * @param name
	 * @return the removed Player
	 */
	public Player removePlayer(String name) {
		return activePlayers.remove(name);
	}

	/**
	 * Execute a list of commands
	 * 
	 * @param commands
	 * @return console log as string
	 */
	public String executeCommands(ArrayList<String> commands) throws Exception {
		// add a log handler to capture log output from running commands
		LogHandler handler = new LogHandler();
		getServer().getLogger().addHandler(handler);
		for (String command : commands) {
			try {
				// send command to log so we have it in the log
				getLogger().info(command); 
											
				getServer().dispatchCommand(getServer().getConsoleSender(), command);

			} catch (Exception e) {
				getLogger().warning(e.toString());
				throw e;
			}
		}
		getServer().getLogger().removeHandler(handler);
		return handler.toString();
	}

	/**
	 * Execute a CommandScript
	 * 
	 * @param script
	 */
	public void executeCommandScript(CommandScript script) throws Exception {
		try {
			String log = executeCommands(script.commands);
			script.complete(log, true);
		
		} catch (Exception e) {
			script.complete(e.getMessage(), false);
			throw e;
		}
	}

	/**
	 * Execute a OrderLine
	 * 
	 * @param bPlayer
	 *            : bukkit player associated with order line
	 * @param line
	 *            : PendingOrderLine to execute
	 */
	public void executeOrderLine(org.bukkit.entity.Player bPlayer, OrderLine line) {
		// first run any commands
		if (line.script != null)
			try {
				executeCommandScript(line.script);
				if (line.message.length() > 0)
					bPlayer.sendMessage(line.message);
			
			} catch (Exception e) {
				String message = "There was a problem completing your %s order.  Please contact %s support.";
				message = String.format(message, line.product.title, game.name);
				bPlayer.sendMessage(message);
			}
	}
}
