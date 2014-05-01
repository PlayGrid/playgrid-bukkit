package com.playgrid.bukkit.plugin.task;

import org.bukkit.scheduler.BukkitRunnable;

import com.playgrid.api.client.RestAPI;
import com.playgrid.api.client.manager.GameManager;
import com.playgrid.api.entity.Game;
import com.playgrid.bukkit.plugin.PlayGridMC;

public class HeartbeatTask extends BukkitRunnable {

	private final PlayGridMC plugin;
	private int interval;
	
	
	public HeartbeatTask(PlayGridMC plugin, int interval) {
		
		this.plugin = plugin;
		this.interval = interval;
		
		int ticks = 20 * interval;                                              // 20 ticks/second
		runTaskTimerAsynchronously(plugin, ticks, ticks);
		
	}


	
	@Override
	public void run() {
		
		try {
		
			if (plugin.getConfig().getBoolean("debug")) {
				StringBuilder builder = new StringBuilder("Heartbeat - Online Players: ");
				builder.append(plugin.getServer().getOnlinePlayers().length);
	
				plugin.getLogger().info(builder.toString());
			
			}
	
			GameManager gameManager = RestAPI.getInstance().getGameManager();
			Game game = gameManager.heartbeat(plugin.game);
			
			if (this.interval != game.heartbeat_interval) {
				new HeartbeatTask(this.plugin, game.heartbeat_interval);        // schedule a task with new interval
				cancel();                                                       // cancel current task's recurrence
			
			}
			
		} catch (RuntimeException e) {
			plugin.getLogger().severe(e.getMessage());
		
		}
		
	}

}
