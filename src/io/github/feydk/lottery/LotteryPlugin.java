package io.github.feydk.lottery;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.github.feydk.lottery.MySQLDatabase;
import io.github.feydk.lottery.PluginConfig;
import net.milkbowl.vault.economy.Economy;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class LotteryPlugin extends JavaPlugin implements Listener
{		
	private Economy economy;
	Draw currentDraw;
	private List<LotteryPlayer> playersToBeNotified;
	private boolean makingDraw;
	static PluginConfig config;	
	static MySQLDatabase db;
	private LotteryScheduler scheduler;
	
	public LotteryPlugin()
	{}
	
	private void loadConfig()
	{
		reloadConfig();
		config = new PluginConfig(getConfig());
	}
	
	@Override
	public void onEnable()
	{
		saveDefaultConfig();
		reloadConfig();
		
		RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
		
		if(economyProvider != null)
			economy = economyProvider.getProvider();
		else
			throw new RuntimeException("Failed to setup economy.");
		
		loadConfig();
		
		getServer().getPluginManager().registerEvents(this, this);
		
		initDb();
		
		scheduler = new LotteryScheduler(this);
		scheduler.start();
	}
	
	@Override
	public void onDisable()
	{}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[])
	{
		Player player = null;

		if(sender instanceof Player)
			player = (Player)sender;

		if(player == null)
		{
			sender.sendMessage("Player expected");
			return true;
		}
		
		if(command.getName().equals("lottery") && player.hasPermission("lottery.lottery"))
		{
			if(args.length == 0)
			{
				displayMain(player);
				return true;
			}
			
			if(args[0].equals("buy"))
			{
				int amount = 1;
				
				if(args.length > 1)
				{
					try
					{
						amount = Integer.parseInt(args[1]);
					}
					catch(NumberFormatException ex)
					{
						amount = 1;
					}
				}
				
				buyTickets(player, amount);
				
				return true;
			}
			
			if(args[0].equals("confirm"))
			{
				int amount = 1;
				
				if(args.length > 1)
				{
					try
					{
						amount = Integer.parseInt(args[1]);
					}
					catch(NumberFormatException ex)
					{
						amount = 1;
					}
				}
				
				if(args.length < 3)
				{
					player.sendMessage(" " + ChatColor.RED + "Transaction Id expected.");
					return true;
				}
				
				confirmPurchase(player, amount, args[2]);
			}
			
			if(args[0].equals("winners"))
			{
				int page = 1;
				
				if(args.length > 1)
				{
					try
					{
						page = Integer.parseInt(args[1]);
					}
					catch(NumberFormatException ex)
					{
						page = 1;
					}
				}
				
				displayWinners(player, page);
				
				return true;
			}
			
			if(args[0].equals("info"))
			{
				displayHelp(player);
				
				return true;
			}
		}
		
		if(command.getName().equals("lotteryadmin") && player.hasPermission("lottery.admin"))
		{
			if(args.length == 0)
			{
				displayAdmin(player);
				return true;
			}
			
			if(args[0].equals("reload"))
			{
				loadConfig();
				player.sendMessage(" Lottery config reloaded");
				return true;
			}
			
			if(args[0].equals("redeem"))
			{
				if(args.length < 3)
				{
					player.sendMessage(ChatColor.RED + "Syntax: /lotteryadmin redeem <player> <amount>");
					return true;
				}
				
				String name = args[1];
				int amount = 1;
				
				try
				{
					amount = Integer.parseInt(args[2]);
				}
				catch(NumberFormatException ex)
				{
					amount = 1;
				}
				
				redeemTickets(player, name, amount);
				
				return true;
			}
			
			if(args[0].equals("draw"))
			{
				forceDraw(player);
				
				return true;
			}
			
			if(args[0].equals("confirmdraw"))
			{
				confirmForcedDraw(player);
				
				return true;
			}
			
			if(args[0].equals("announce"))
			{
				announce();
				return true;
			}
		}
		
		return true;
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event)
	{
		final Player player = event.getPlayer();
		
		List<LotteryPlayer> list = new ArrayList<LotteryPlayer>();
		
		// Check if player won while he was offline.
		if(playersToBeNotified.size() > 0)
		{
			for(LotteryPlayer p : playersToBeNotified)
			{
				if(p.getUuid().equals(player.getUniqueId()))
					list.add(p);
			}
		}
		
		if(list.size() > 0)
		{
			SimpleDateFormat sdf = new SimpleDateFormat("MMM d");
			
			String json = "[";
			
			for(LotteryPlayer p : list)
			{
				json += "{\"text\": \"§8[§6Lottery§8] §fYou won §b" + economy.format(p.getWonDrawPot()) + " §fin the draw of " + sdf.format(p.getWonDrawDate()) + "\n\"}, ";
			}
			
			json += "{\"text\": \"\"} ";
			json += "] ";
			
			final String msg = json;
			
			new BukkitRunnable()
	    	{
	    		@Override public void run()
	    		{
	    			sendJsonMessage(player, msg);
	    		}
	    	}.runTaskLater(this, 20 * 5);
	    	
	    	for(LotteryPlayer p : list)
			{
	    		p.setNotified(1);
	    		p.update();
			}
	    	
	    	playersToBeNotified = LotteryPlayer.getForNotification();
		}
	}
	
	@SuppressWarnings("unused")
	private void debug(Object o)
	{
		System.out.println(o);
	}
	
	/// Command methods ///
	private void displayMain(Player player)
	{
		LotteryPlayer p = LotteryPlayer.get(player, currentDraw.getId());

		long drawTime = currentDraw.getDrawDate().getTime();
		long now = new Date().getTime();
		long diff = drawTime - now;
		
		int secs = (int)(diff / 1000);
		int days = secs / 86400;
		secs -= days * 86400;
		int hours = secs / 3600;
		secs -= hours * 3600;
		int mins = secs / 60;
		
		String next = "";
		
		if(days > 0)
			next += days + " " + (days > 1 ? "days" : "day") + ", ";
		
		if(hours > 0)
			next += hours + " " + (hours > 1 ? "hours" : "hour") + " and ";
		
		next += mins + " " + (mins > 1 || mins == 0 ? "minutes" : "minute");
		
		String json = "[";		
		json += "{\"text\": \"§6§l§m   \"}, {\"text\": \"§6 ✦ \"}, {\"text\": \"§fWinthier Lottery\"}, {\"text\": \"§6 ✦ §l§m   \n\"}, ";
		json += "{\"text\": \" §fThis is your chance to earn some extra dough. Buy tickets and you might win!\n \n\"}, ";
		json += "{\"text\": \" §3Time until next draw: §b" + next + "\n\"}, ";
		json += "{\"text\": \" §3Current pot size: §b" + economy.format(currentDraw.getPot()) + "\n \n\"}, ";
		
		if(p == null || p.getTickets() == 0)
		{
			json += "{\"text\": \" §fYou haven't bought any tickets for this draw yet.\n\"}, ";
			json += "{\"text\": \" §7[§aBuy tickets§7]\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/lottery buy \" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§3/lottery buy\n§7Click, then enter an amount\n§7of tickets to buy.\"}}, ";
		}
		else if(p != null && p.getTickets() > 0)
		{
			json += "{\"text\": \" §fYou have §6" + p.getTickets() + " §f" + (p.getTickets() > 1 ? "tickets" : "ticket") + " for this draw. Good luck!\n\"}, ";
			json += "{\"text\": \" §7[§aBuy more tickets§7]\", \"clickEvent\": {\"action\": \"suggest_command\", \"value\": \"/lottery buy \" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§3/lottery buy\n§7Click, then enter an amount\n§7of tickets to buy.\"}}, ";
		}
		
		json += "{\"text\": \"\n \n\"}, ";
		json += "{\"text\": \" §7[§3More info§7]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/lottery info\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§3/lottery info\n§7Read how the lottery works.\"}}, ";
		json += "{\"text\": \" \"}, ";
		json += "{\"text\": \" §7[§3Past winners§7]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/lottery winners\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§3/lottery winners\n§7See past lottery winners.\"}} ";
		json += "] ";
		
		sendJsonMessage(player, json);
	}
	
	private void displayHelp(Player player)
	{
		String[] days = { "", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };
		
		String json = "[";
		json += "{\"text\": \"§6§l§m   \"}, {\"text\": \"§6 ✦ \"}, {\"text\": \"§fWinthier Lottery Info\"}, {\"text\": \"§6 ✦ §l§m   \n\"}, ";
		json += "{\"color\": \"gray\", \"text\": \" The lottery is a game of luck. You buy tickets at " + economy.format(config.TICKET_PRICE) + " per ticket and then hope one of your tickets will be drawn.\n If it does, you win the pot!\n \n\"}, ";
		json += "{\"color\": \"gray\", \"text\": \" The pot size is determined by the amount of tickets sold. Every time a player buys a ticket, the pot grows.\n \n\"}, ";
		json += "{\"color\": \"gray\", \"text\": \" The draw is made every " + days[config.DRAW_WEEKDAY] + " at " + String.format("%02d", config.DRAW_HOURS) + ":" + String.format("%02d", config.DRAW_MINUTES) + " server time.\"} ";
		json += "] ";
		
		sendJsonMessage(player, json);
	}
	
	private void displayAdmin(Player player)
	{
		String msg = ChatColor.AQUA + "Lottery admin interface usage" + "\n";
		msg += " §b/la" + " §3Show this menu" + "\n";
		msg += " §b/la draw" + " §3Force a draw" + "\n";
		msg += " §b/la redeem <name> <amount>" + " §3Buy back tickets" + "\n";
		msg += " §b/la announce" + " §3Trigger the lottery status announcement" + "\n";
		msg += " §b/la reload" + " §3Reload the config" + "\n";
		
		player.sendMessage(msg);
	}
	
	private void displayWinners(Player player, int page)
	{
		String json = "[";
		json += "{\"text\": \"§6§l§m   \"}, {\"text\": \"§6 ✦ \"}, {\"text\": \"§fWinthier Lottery Winners\"}, {\"text\": \"§6 ✦ §l§m   \n\"}, ";
	
		List<Draw> list = Draw.getHistory(page);
		
		if(list.size() > 0)
		{
			SimpleDateFormat sdf = new SimpleDateFormat("MMM dd");
			int total = list.get(0).getFoundRows();
			int pages = (int)Math.ceil((double)total / 5.0);
			
			for(Draw draw : list)
			{
				json += "{\"text\": \"§7[§b" + sdf.format(draw.getDrawDate()) + "§7] §f" + draw.getWinnerName() + " §3won §b" + economy.format(draw.getPot()) + "\n\"}, ";
			}
			
			if(pages > 1)
			{
				json += "{\"text\": \"§3§l§m  §3 Page §b" + page + "§3/§b" + pages + " \"}, ";
				
				if(page + 1 <= pages)
					json += "{\"text\": \"§3[§bMore§3]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/lottery winners " + (page + 1) + "\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§7View more winners.\"}}, ";
			}
			
			json += "{\"text\": \"\"}";
		}
		else
		{
			json += "{\"text\": \" §3There are no winners yet.\"}";
		}
				
		json += "] ";
		
		sendJsonMessage(player, json);
	}
	
	private boolean validateCanBuy(Player player, int amount)
	{
		if(makingDraw)
		{
			player.sendMessage(ChatColor.RED + " We're about to make the draw, so you can't buy tickets right now.");
			return false;
		}
		
		if(amount < 0)
		{
			player.sendMessage(ChatColor.RED + " You can't buy a negative amount of tickets, silly!");
			return false;
		}
		
		double pay = amount * config.TICKET_PRICE;
		double balance = economy.getBalance(player);
		
		if(balance < pay)
		{
			player.sendMessage(ChatColor.RED + " You can't afford that. You only have " + economy.format(balance) + ", but " + amount + " tickets will cost you " + economy.format(pay) + ".");
			return false;
		}
		
		return true;
	}
	
	private void buyTickets(Player player, int amount)
	{
		if(!validateCanBuy(player, amount))
			return;
		
		LotteryPlayer p = LotteryPlayer.get(player, currentDraw.getId());
		
		if(p == null)
		{
			p = LotteryPlayer.create(player.getUniqueId(), player.getName(), currentDraw.getId());
		}
		
		double pay = amount * config.TICKET_PRICE;
		
		// Everything's good so far. Create a new transaction id and have the player confirm it.
		p.setTransactionId(UUID.randomUUID());
		p.update();
		
		String json = "[";
		json += "{ \"text\": \" §3Buy §b" + amount + "§3 " + (amount > 1 ? "tickets" : "ticket") + " for §b" +  economy.format(pay) + "§3?\"}, ";
		json += "{ \"text\": \" §3[§fConfirm§3]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/lottery confirm " + amount + " " + p.getTransactionId() + "\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"" + ChatColor.DARK_AQUA + "Click to confirm.\"}} ";
		json += "] ";
		
		sendJsonMessage(player, json);
	}
	
	private void confirmPurchase(Player player, int amount, String transactionId)
	{
		if(!validateCanBuy(player, amount))
			return;
		
		LotteryPlayer p = LotteryPlayer.get(player, currentDraw.getId());
		
		if(p == null)
			return;
		
		double pay = amount * config.TICKET_PRICE;
		
		// If provided transaction id matches the transaction id in the database, complete the purchase.
		if(p.getTransactionId().toString().equals(transactionId))
		{
			if(economy.withdrawPlayer(player, pay).transactionSuccess())
			{
				p.addTickets(amount);
				p.setTransactionId(UUID.randomUUID());	// Set to new random UUID so players don't confirm the same thing twice.
				p.update();
				
				double addPot = (pay / 100) * (100 - config.TRANSFER_PERCENTAGE);
				double transferPot = pay - addPot;
				
				currentDraw.addToPot(addPot);
				currentDraw.addToTransfer(transferPot);
				currentDraw.update();
				
				player.sendMessage(" " + ChatColor.GREEN + "You bought " + amount + " " + (amount > 1 ? "tickets" : "ticket") + ". Good luck!");
			}
		}
	}
	
	private void redeemTickets(Player player, String playername, int amount)
	{
		double pay = amount * config.TICKET_PRICE;
		double addPot = (pay / 100) * (100 - config.TRANSFER_PERCENTAGE);
		double transferPot = pay - addPot;
		
		Player entity = getServer().getPlayer(playername);
		
		if(entity != null)
		{
			currentDraw.addToPot(addPot * -1);
			currentDraw.addToTransfer(transferPot * -1);
			currentDraw.update();
			
			LotteryPlayer p = LotteryPlayer.get(entity, currentDraw.getId());
			p.addTickets(amount * -1);
			p.update();
		
			economy.depositPlayer(entity, pay);
			
			player.sendMessage(" Bought back " + amount + " tickets from " + playername);
		}
	}
	
	private void forceDraw(Player player)
	{
		String json = "[";
		json += "{\"color\": \"green\", \"text\": \" You're about to force a draw. Please confirm: \"}, ";
		json += "{\"color\": \"dark_green\", \"text\": \"[Confirm draw]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/lotteryadmin confirmdraw\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"" + ChatColor.GREEN + "Confirm this draw.\"}} ";
		json += "] ";
		
		sendJsonMessage(player, json);
	}
	
	private void confirmForcedDraw(Player player)
	{
		makeDraw();
	}
	
	@SuppressWarnings("deprecation")
	private void announce()
	{
		String json = "[";
		json += "{\"text\": \"§8[§6Lottery§8]\", \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"" + ChatColor.DARK_AQUA + "You can click the\n" + ChatColor.DARK_AQUA + "highlighted word\"}}, ";
		json += "{\"color\": \"white\", \"text\": \" Current pot size is \"}, ";
		json += "{\"color\": \"gold\", \"text\": \"" + economy.format(currentDraw.getPot()) + "\"}, ";
		json += "{\"color\": \"white\", \"text\": \". \"}, ";
		json += "{\"color\": \"dark_aqua\", \"text\": \"View details\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/lottery\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"" + ChatColor.DARK_AQUA + "/lottery\n" + ChatColor.GRAY + "Click to view\"}}, ";
		json += "{\"color\": \"white\", \"text\": \".\"} ";
		json += "] ";
		
		for(Player player : getServer().getOnlinePlayers())
			sendJsonMessage(player, json);
	}
	
	/// Helper methods ///	
	private void initDb()
	{
		db = new MySQLDatabase(config.DB_HOST, config.DB_PORT, config.DB_USER, config.DB_PASS, config.DB_NAME);
		
		String sql = 
			"CREATE TABLE IF NOT EXISTS `players` ( " +
			"`id` int(11) NOT NULL AUTO_INCREMENT, " +
			"`draw_id` int(11) DEFAULT NULL, " +
			"`uuid` varchar(50) DEFAULT NULL, " +
			"`name` varchar(50) DEFAULT NULL, " +
			"`tickets` int(11) DEFAULT NULL, " +
			"`transaction_id` varchar(50) DEFAULT NULL, " +
			"`notified` TINYINT DEFAULT 0, " +
			"PRIMARY KEY (`id`) " +
			");";
		
		db.execute(sql, null);
		
		sql = 
			"CREATE TABLE IF NOT EXISTS `draws` ( " +
			"`id` INT NOT NULL AUTO_INCREMENT, " +
			"`pot` DECIMAL DEFAULT 0, " +
			"`transfer` DECIMAL DEFAULT 0, " +
			"`draw_date` DATETIME, " +
			"`winner_id` int(11) DEFAULT NULL, " +
			"`current` TINYINT NULL, " +
			"PRIMARY KEY (`id`) " +
			");";
		
		db.execute(sql, null);
		
		// Get the current draw.
		currentDraw = Draw.getCurrent();
		
		// Create a draw if there's no current draw.
		if(currentDraw == null)
		{
			currentDraw = Draw.create();
		}
		
		playersToBeNotified = LotteryPlayer.getForNotification();
	}
	
	@SuppressWarnings("deprecation")
	void makeDraw()
	{
		if(makingDraw)
			return;
		
		makingDraw = true;
		
		SimpleDateFormat sdf = new SimpleDateFormat("MMM d");
		
		LotteryPlayer winner = LotteryPlayer.pickWinner(currentDraw.getId());
		
		// No tickets were bought for this draw, so transfer to next draw.
		if(winner == null)
		{
			Draw newDraw = Draw.create();
			newDraw.setPot(currentDraw.getPot());
			newDraw.update();
			
			currentDraw.setCurrent(0);
			currentDraw.update();
			
			String json = "[";
			json += "{\"text\": \"§8[§6Lottery§8]\", \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"" + ChatColor.DARK_AQUA + "You can click the\n" + ChatColor.DARK_AQUA + "highlighted word\"}}, ";
			json += "{\"text\": \" §fNo winner for this draw, since no tickets were bought!\n\"}, ";
			json += "{\"text\": \" §fThe next draw will be " + sdf.format(newDraw.getDrawDate()) + ". You can\"}, ";
			json += "{\"text\": \" §3buy tickets now\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/lottery\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"" + ChatColor.DARK_AQUA + "/lottery\n" + ChatColor.GRAY + "Click to view\"}}, ";
			json += "{\"text\": \"§f.\"} ";
			json += "] ";
			
			// Notify everyone.
			for(Player p : getServer().getOnlinePlayers())
			{
				sendJsonMessage(p, json);
			}
			
			currentDraw = newDraw;
		}
		else
		{
			OfflinePlayer player = getServer().getOfflinePlayer(winner.getUuid());
			
			currentDraw.setWinnerId(winner.getId());
			currentDraw.setCurrent(0);
	
			economy.depositPlayer(player, currentDraw.getPot());
			
			// Make a new draw.
			Draw newDraw = Draw.create();
			newDraw.setPot(currentDraw.getTransfer());
			newDraw.update();
			
			currentDraw.update();		
			
			// Notify player if he's online.
			if(player.isOnline())
			{
				((CommandSender)player).sendMessage("§8[§6Lottery§8] §fYou won! §b" + economy.format(currentDraw.getPot()) + " §fhas been deposited in your account.");
				winner.setNotified(1);
				winner.update();
			}
			else
			{
				playersToBeNotified = LotteryPlayer.getForNotification();
			}
			
			String json = "[";
			json += "{\"text\": \"§8[§6Lottery§8]\"}, ";
			json += "{\"text\": \" §b" + winner.getName() + " §fwon the draw and the pot of §b" + economy.format(currentDraw.getPot()) + "§f.\n\"}, ";
			json += "{\"text\": \" §fThe next draw will be " + sdf.format(newDraw.getDrawDate()) + ". You can\"}, ";
			json += "{\"text\": \" §3buy tickets now\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/lottery\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"" + ChatColor.DARK_AQUA + "/lottery\n" + ChatColor.GRAY + "Click to view\"}}, ";
			json += "{\"text\": \"§f.\"} ";
			json += "] ";
			
			// Notify everyone.
			for(Player p : getServer().getOnlinePlayers())
			{
				sendJsonMessage(p, json);
			}
			
			currentDraw = newDraw;
		}
    	
    	makingDraw = false;
	}
	
	private boolean sendJsonMessage(Player player, String json)
	{
		if(player == null)
	    	return false;
	    
	    final CommandSender console = getServer().getConsoleSender();
	    final String command = "minecraft:tellraw " + player.getName() + " " + json;
	
	    getServer().dispatchCommand(console, command);
	    
	    return true;
	}
}