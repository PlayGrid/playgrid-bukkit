package com.playgrid.bukkit.plugin.stats;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.lolmewn.stats.api.Stat;
import nl.lolmewn.stats.api.StatsAPI;
import nl.lolmewn.stats.player.StatData;
import nl.lolmewn.stats.player.StatsPlayer;

import com.google.gson.Gson;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.playgrid.bukkit.plugin.PlayGridMC;

public class Stats {

	private final PlayGridMC plugin;
	private StatsAPI provider;
	private boolean enabled = true;
	private float version = 0; 

	/**
	 * Constructor
	 */
	public Stats(PlayGridMC plugin) {

		this.plugin = plugin;
		boolean enable_stats = plugin.getConfig().getBoolean("player.enable_stats", true);

		if (plugin.getServer().getPluginManager().isPluginEnabled("Stats")) {
			
			Pattern pattern = Pattern.compile("(^\\d+\\.\\d+).*");
			String version_string = plugin.getServer().getPluginManager().getPlugin("Stats").getDescription().getVersion();
			Matcher matcher = pattern.matcher(version_string);
			if (matcher.matches()) {
				version = Float.parseFloat(matcher.group(1));
			}

			RegisteredServiceProvider<StatsAPI> rsp;
			rsp = plugin.getServer().getServicesManager().getRegistration(StatsAPI.class);

			if (rsp != null) {
				provider = rsp.getProvider();
				plugin.getLogger().info("Detected Stats provider");

				if (!enable_stats) {
					disable("Set 'player.enable_stats: true' in config.yml to enable PlayGrid stats support.");
				}

				return;

			} else {
				disable("Stats provider not found");
			}

		} else {
			disable("Stats not found");
		}

		String msg = "Enable Stats by installing the Stats plugin [http://dev.bukkit.org/bukkit-mods/lolmewnstats/]";
		plugin.getLogger().warning(msg);
	}

	/**
	 * IsEnabled
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
	 * Disable Stats
	 * 
	 * @param reason
	 *            a reason for disabling
	 */
	private void disable(String reason) {
		plugin.getLogger().warning("Disabling stats support: " + reason);
		enabled = false;
	}

	/**
	 * GetPlayerStats
	 * 
	 * @param player
	 *            player name to extract stats for
	 * @return a collection of player stats and values
	 */
	public String getPlayerStats(String player) {
		if (!this.isEnabled()) {
			return null;
		}
		
		Map<String, Object> stats_map;
		
		if (version >= 2.0) {
			StatsPlayer statsPlayer = provider.getPlayer(player);

			if (statsPlayer.isTemp()) {
				/** 
				 * If 'temp' then statsPlayer is still loading.
				 * Harvesting now will zero existing stat data.
				 */
				return null;
			}
			
			stats_map = new HashMap<String, Object>();

			for (Stat stat : provider.getAllStats()) {
				StatData statData = statsPlayer.getGlobalStatData(stat);
				Collection<Object[]> variables = statData.getAllVariables();
				
		        double value = 0;
				if (variables.isEmpty()) {
					value = statData.getValue(new Object[]{});
				
				} else {
			        for (Object[] var : variables) {
			            value += statData.getValue(var);
			        }
				}
				
				// convert 
				Object converted_value = null; 
				switch(stat.getMySQLType()) {
					case INTEGER:
						converted_value = (int) value;
						break;

					case TIMESTAMP:
						converted_value = new SimpleDateFormat("MMM d, y h:mm:ss a").format(value);
						break;
					
					default:
						converted_value = value;
						break;
				}
		
				stats_map.put(stat.getName(), converted_value);
			}
		
		} else {
			// Pre 2.0 stats harvesting - for backwards compatibility
			// This will be deprecated
			try {
				Connection conn = provider.getConnection();
				Statement statement = conn.createStatement();
	
				String sql = String.format("SELECT * FROM Stats_player WHERE player='%s'", player);
	
				ResultSet resultSet = statement.executeQuery(sql);
				ResultSetMetaData metaData = resultSet.getMetaData();
	
				int columns = metaData.getColumnCount();
				stats_map = new HashMap<String, Object>(columns);
	
				while (resultSet.next()) {
					for (int i = 1; i <= columns; ++i) {
						String name = metaData.getColumnName(i);
						if (!name.equals("player")) {
							stats_map.put(metaData.getColumnName(i), resultSet.getObject(i));
						}
					}
				}

			} catch (SQLException e) {
				this.plugin.getLogger().log(Level.SEVERE, "[Stats] " + e.getMessage(), e);
				return null;
			}
		}
		
		Gson gson = new Gson();

		Map<String, Object> stats_payload = new HashMap<String, Object>(1);
		stats_payload.put("stats", stats_map);

		return gson.toJson(stats_payload);
	}
}
