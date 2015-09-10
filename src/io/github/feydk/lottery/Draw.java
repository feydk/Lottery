package io.github.feydk.lottery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class Draw
{
	private int id;
	private double pot;
	private double transfer;
	private Date drawDate;
	private int winnerId;
	private int current;
	private String winnerName;
	private int foundRows;
	
	public int getId()
	{
		return id;
	}
	
	public void setId(int id)
	{
		this.id = id;
	}
	
	public double getPot()
	{
		return pot;
	}
	
	public void setPot(double pot)
	{
		this.pot = pot;
	}
	
	public double getTransfer()
	{
		return transfer;
	}

	public void setTransfer(double transfer)
	{
		this.transfer = transfer;
	}
	
	public Date getDrawDate()
	{
		return drawDate;
	}
	
	public void setDrawDate(Date drawDate)
	{
		this.drawDate = drawDate;
	}
	
	public int getWinnerId()
	{
		return winnerId;
	}
	
	public void setWinnerId(int winnerId)
	{
		this.winnerId = winnerId;
	}
	
	public int isCurrent()
	{
		return current;
	}

	public void setCurrent(int current)
	{
		this.current = current;
	}
	
	public String getWinnerName()
	{
		return winnerName;
	}

	public void setWinnerName(String winnerName)
	{
		this.winnerName = winnerName;
	}

	public int getFoundRows()
	{
		return foundRows;
	}

	public void setFoundRows(int foundRows)
	{
		this.foundRows = foundRows;
	}
	
	public Draw()
	{}
	
	public static Draw populate(ResultSet rs)
	{
		try
		{
			if(rs != null && rs.next())
			{
				Draw obj = new Draw();
				obj.setId(rs.getInt("id"));
				obj.setPot(rs.getDouble("pot"));
				obj.setTransfer(rs.getDouble("transfer"));
				obj.setDrawDate(rs.getTimestamp("draw_date"));
				obj.setWinnerId(rs.getInt("winner_id"));
				obj.setCurrent(rs.getInt("current"));
				
				return obj;
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		
		return null;
	}
	
	public void update()
	{
		String sql = "update draws set pot = ?, transfer = ?, winner_id = ?, current = ? where id = ?";
		
		HashMap<Integer, Object> params = new HashMap<Integer, Object>();
		params.put(1, pot);
		params.put(2, transfer);
		params.put(3, winnerId);
		params.put(4, current);
		params.put(5, id);
		
		LotteryPlugin.db.update(sql, params);
	}
	
	public static Draw create()
	{
		String sql = 
			"insert into draws (pot, transfer, draw_date, winner_id, current) " +
			"values (?, ?, ?, 0, 1)";
		
		// Determine next draw date.
		Calendar today = Calendar.getInstance();
		int dayOfWeek = today.get(Calendar.DAY_OF_WEEK);
		int daysUntilNextDraw = LotteryPlugin.config.DRAW_WEEKDAY - dayOfWeek;
		
		if(daysUntilNextDraw < 0)
		{
			daysUntilNextDraw += 7;
		}
		else if(daysUntilNextDraw == 0)
		{
			daysUntilNextDraw = 7 - daysUntilNextDraw;
		}
		
		Calendar nextDraw = (Calendar)today.clone();
		nextDraw.add(Calendar.DAY_OF_WEEK, daysUntilNextDraw);
		nextDraw.set(Calendar.HOUR_OF_DAY, LotteryPlugin.config.DRAW_HOURS);
		nextDraw.set(Calendar.MINUTE, LotteryPlugin.config.DRAW_MINUTES);
		nextDraw.set(Calendar.SECOND, 0);
						
		String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(nextDraw.getTime());
		
		HashMap<Integer, Object> params = new HashMap<Integer, Object>();
		params.put(1, 0);
		params.put(2, 0);
		params.put(3, date);
		
		int id = LotteryPlugin.db.insert(sql, params);
		
		sql = "select * from draws where id = " + id;
		
		ResultSet rs = LotteryPlugin.db.select(sql, null);
		
		return Draw.populate(rs);
	}
	
	public static Draw getCurrent()
	{
		String sql = "select * from draws where current = 1 order by draw_date desc limit 0, 1";
		
		ResultSet rs = LotteryPlugin.db.select(sql, null);
		
		return Draw.populate(rs);
	}
	
	public static List<Draw> getHistory(int page)
	{
		int pagesize = 5;
		int start = (page - 1) * pagesize;
		
		String sql = "select SQL_CALC_FOUND_ROWS draws.*, players.name from draws join players on players.id = draws.winner_id where winner_id > 0 order by draw_date desc limit " + start + ", " + pagesize;
		
		ResultSet rs = LotteryPlugin.db.select(sql, null);
		
		List<Draw> list = new ArrayList<Draw>();
		
		try
		{
			if(rs != null && rs.isBeforeFirst())
			{
				while(!rs.isLast())
				{
					Draw obj = Draw.populate(rs);
					
					if(obj != null)
					{
						obj.setWinnerName(rs.getString("name"));
					
						list.add(obj);
					}
				}
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		
		if(list.size() > 0)
		{
			sql = "select FOUND_ROWS() as found";
			
			list.get(0).setFoundRows(LotteryPlugin.db.getInt(sql, null));
		}
		
		return list;
	}
	
	public void addToPot(double amount)
	{
		pot += amount;
	}

	public void addToTransfer(double amount)
	{
		transfer += amount;
	}
}