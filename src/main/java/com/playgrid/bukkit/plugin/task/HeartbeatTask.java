package com.playgrid.bukkit.plugin.task;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.playgrid.api.client.RestAPI;
import com.playgrid.api.client.manager.GameManager;

public class HeartbeatTask extends BukkitRunnable {

	private final JavaPlugin plugin;
	
	
	public HeartbeatTask(JavaPlugin plugin) {
		
		this.plugin = plugin;
		this.runTaskTimerAsynchronously(plugin, 20*60, 20*60);                  // Testing: 20*5, 20*10 	

	}


	
	@Override
	public void run() {
		
		try {
		
			if (plugin.getConfig().getBoolean("debug")) {
				StringBuilder builder = new StringBuilder("Heartbeat - Online Players: ");
				builder.append(plugin.getServer().getOnlinePlayers().length);
	
				plugin.getLogger().info(builder.toString());
			
			}
	
			GameManager gameManager = RestAPI.getInstance().getGamesManager();
			gameManager.heartbeat();
			
		} catch (RuntimeException e) {
			plugin.getLogger().severe(e.getMessage());
		}
		
	}

}
