package com.playgrid.bukkit.plugin.command;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.WebApplicationException;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.playgrid.api.client.RestAPI;
import com.playgrid.api.client.manager.PlayerManager;
import com.playgrid.api.entity.PlayerRegistration;
import com.playgrid.bukkit.plugin.PlayGridMC;


public class RegisterCommandExecutor implements CommandExecutor {

	private PlayGridMC plugin;
	
	public RegisterCommandExecutor(PlayGridMC plugin) {
		this.plugin = plugin;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("You must be a player!");
			return false;
        } 
		
		
		if (args.length == 0) { return false;}                                  // No args, show usage
		
        Player bPlayer = (Player) sender;
        plugin.activatePlayerLocale(bPlayer); // set language
		String email = args[0];
	        
		try {
			boolean success = register(bPlayer, email);
			return success;

		} catch (WebApplicationException e) {
			plugin.getLogger().severe(e.getMessage());
			bPlayer.sendMessage("An error occurred and your request could not be completed at this time.");
			return false;
		}
	}
	
	private void reportClientExecption(Player bPlayer, WebApplicationException ex) {
		// reports content (error message) to player when receiving a client exception
		String message = ex.getMessage();
		if(message != "") {
			bPlayer.sendMessage(message);
		}
	}
	
	private boolean register(Player bPlayer, String email) {
		PlayerManager playerManager = RestAPI.getInstance().getPlayerManager();
		String player_uid = bPlayer.getUniqueId().toString().replaceAll("-", "");
		
		PlayerRegistration playerRegistration;
		
		// possible results:
		//  201: player+user binding success
		//  200: player already registered to this account
		//  400: bad request - incorrect email format
		//  403: multiple accounts for email address
		//  403: player+user binding failed
		
		try {
			playerRegistration = playerManager.register(bPlayer.getName(), player_uid, email);
			
		} catch (BadRequestException ex) {
			this.reportClientExecption(bPlayer, ex);
			return false;
		} catch (ForbiddenException ex) {
			this.reportClientExecption(bPlayer, ex);
			return false;
		} catch (WebApplicationException ex) {
			this.reportClientExecption(bPlayer, ex);
			return false;
		}
		
		
		if (playerRegistration.message.equals("SUCCESS")) {
			String[] messages = new String[] {
					"You have successfully registered.", 
					String.format("Check your %s account for instructions to finalize your registsration.", playerRegistration.email),
			};
			bPlayer.sendMessage(messages);
			
			String name = bPlayer.getName();
			com.playgrid.api.entity.Player pPlayer = plugin.getPlayer(name);
			plugin.permissions.removeGroups(bPlayer);
			
			pPlayer = plugin.reloadPlayer(name);

//			plugin.permissions.setGroups(bPlayer, pPlayer.permission_groups);
//			plugin.getLogger().info(pPlayer.name + " registered and was added to the " + Arrays.toString(pPlayer.permission_groups) + " groups.");
			
		} else if (playerRegistration.message.equals("REJECTED")) {
			String[] messages = new String[] {
					String.format("%s is already in use.", playerRegistration.email), 
					String.format("Visit %s to manage your players.", plugin.game.website),
			};
			bPlayer.sendMessage(messages);

		} else if (playerRegistration.message.equals("ALREADY REGISTERED")) {
			bPlayer.sendMessage(String.format("You have already registered using %s.", playerRegistration.email));
		
		} else {
			plugin.getLogger().info("Unrecognized registration message: " + playerRegistration.message);
			return false;
		}

		return true;
	}
}
