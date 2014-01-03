package com.playgrid.bukkit.plugin.stats;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import nl.lolmewn.stats.api.StatsAPI;

import org.bukkit.craftbukkit.libs.com.google.gson.Gson;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.playgrid.bukkit.plugin.PlayGridMC;

public class Stats {

	private final PlayGridMC plugin;
	private StatsAPI provider;
	private boolean enabled = true;

	
	/**
	 * Constructor
	 */
	public Stats(PlayGridMC plugin) {

		this.plugin = plugin;
		boolean enable_stats = plugin.getConfig().getBoolean("player.enable_stats", true);

        if(plugin.getServer().getPluginManager().isPluginEnabled("Stats")) {

			RegisteredServiceProvider<StatsAPI> rsp;
		    rsp = plugin.getServer().getServicesManager().getRegistration(StatsAPI.class);
		    
		    if (rsp != null) {
		    	provider = rsp.getProvider();
		    	
		    	plugin.getLogger().info("Detected Stats provider");
		    	
		    	if (enable_stats == false) {
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
	 * @return	enabled status boolean
	 */
	private boolean isEnabled() {
		if (provider != null && enabled) {
			return true;
		
		}
		
    	return false;
	}


	
	/**
	 * Disable Stats
	 * @param 	reason	a reason for disabling  
	 */
	private void disable(String reason) {
		plugin.getLogger().warning("Disabling stats support: " + reason);
		enabled = false;
		
	}

	
	
	/**
	 * GetPlayerStats
	 * @param	player	player name to extract stats for
	 * @return			a collection of player stats and values
	 */
	public String getPlayerStats(String player) {

		if (!this.isEnabled()) {
			return null;
		}
		
		try {
			Connection conn = provider.getConnection();
			Statement statement = conn.createStatement();
			
			String sql = String.format("SELECT * FROM Stats_player WHERE player='%s'", player);

			ResultSet resultSet = statement.executeQuery(sql);
			ResultSetMetaData metaData = resultSet.getMetaData();

			int columns = metaData.getColumnCount();
			Map<String, Object> stats = new HashMap<String, Object>(columns);

			while (resultSet.next()){
				
				for(int i=1; i<=columns; ++i){      
				
					String name = metaData.getColumnName(i);
					if(!name.equals("player"))
						stats.put(metaData.getColumnName(i), resultSet.getObject(i));
				
				}
		
			}					

			Gson gson = new Gson();

			Map<String, Object> stats_payload = new HashMap<String, Object>(1);
			stats_payload.put("stats", stats);
			
			return gson.toJson(stats_payload);
			
		} catch (SQLException e) {
			plugin.getLogger().severe("Unable to connect to the stats database");

		}
		
		return null;
	}
	
}
