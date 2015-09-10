package io.github.feydk.lottery;

import org.bukkit.configuration.file.FileConfiguration;

public class PluginConfig
{
	// Basic db stuff.
	public String DB_HOST;
	public String DB_PORT;
	public String DB_USER;
	public String DB_PASS;
	public String DB_NAME;
	
	// Draw stuff.
	public int DRAW_WEEKDAY;
	public int DRAW_HOURS;
	public int DRAW_MINUTES;
	
	// General lottery stuff.
	public double TICKET_PRICE;
	public double TRANSFER_PERCENTAGE;
	
	public PluginConfig(FileConfiguration config)
	{
		DB_HOST = config.getString("mysql.host", "localhost");
		DB_PORT = config.getString("mysql.port", "3306");
		DB_USER = config.getString("mysql.user", "root");
		DB_PASS = config.getString("mysql.password", "password");
		DB_NAME = config.getString("mysql.database", "lottery");
		
		DRAW_WEEKDAY = config.getInt("draw.weekday", 6);
		DRAW_HOURS = config.getInt("draw.hours", 22);
		DRAW_MINUTES = config.getInt("draw.minutes", 0);
		
		TICKET_PRICE = config.getDouble("lottery.price", 10);
		TRANSFER_PERCENTAGE = config.getDouble("lottery.transfer", 20);
	}
}