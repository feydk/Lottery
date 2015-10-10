package io.github.feydk.lottery;

import java.util.Date;

import org.bukkit.scheduler.BukkitRunnable;

public class LotteryScheduler extends BukkitRunnable
{
	private LotteryPlugin plugin;

	public LotteryScheduler(LotteryPlugin plugin)
	{
		this.plugin = plugin;
	}
	
	public void start()
	{
		// Every ~ 1 minute should be fine.
		runTaskTimer(plugin, 1, 1200);
	}
	
	public void stop()
	{
	}
	
	@Override
	public void run()
	{
		if(plugin.currentDraw.getDrawDate().getTime() < new Date().getTime())
		{
			plugin.makeDraw();
		}
	}
}