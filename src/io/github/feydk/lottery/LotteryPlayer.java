package io.github.feydk.lottery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

public class LotteryPlayer
{
	private int id;
	private int drawId;
	private UUID uuid;
	private String name;
	private int tickets;
	private UUID transactionId;
	private int notified;
	private Date wonDrawDate;
	private double wonDrawPot;
	
	public int getId()
	{
		return id;
	}
	
	public void setId(int id)
	{
		this.id = id;
	}
	
	public int getDrawId()
	{
		return drawId;
	}
	
	public void setDrawId(int drawId)
	{
		this.drawId = drawId;
	}
	
	public UUID getUuid()
	{
		return uuid;
	}
	
	public void setUuid(UUID uuid)
	{
		this.uuid = uuid;
	}
	
	public String getName()
	{
		return name;
	}
	
	public void setName(String name)
	{
		this.name = name;
	}
	
	public int getTickets()
	{
		return tickets;
	}
	
	public void setTickets(int tickets)
	{
		this.tickets = tickets;
	}
	
	public UUID getTransactionId()
	{
		return transactionId;
	}

	public void setTransactionId(UUID transactionId)
	{
		this.transactionId = transactionId;
	}
	
	public int getNotified()
	{
		return notified;
	}

	public void setNotified(int notified)
	{
		this.notified = notified;
	}
	
	public Date getWonDrawDate()
	{
		return wonDrawDate;
	}

	public void setWonDrawDate(Date wonDrawDate)
	{
		this.wonDrawDate = wonDrawDate;
	}

	public double getWonDrawPot()
	{
		return wonDrawPot;
	}

	public void setWonDrawPot(double wonDrawPot)
	{
		this.wonDrawPot = wonDrawPot;
	}
	
	public LotteryPlayer()
	{}
	
	public static LotteryPlayer populate(ResultSet rs)
	{
		try
		{
			if(rs != null && rs.next())
			{
				LotteryPlayer obj = new LotteryPlayer();
				obj.setId(rs.getInt("id"));
				obj.setDrawId(rs.getInt("draw_id"));
				obj.setUuid(UUID.fromString(rs.getString("uuid")));
				obj.setName(rs.getString("name"));
				obj.setTickets(rs.getInt("tickets"));
				
				if(rs.getString("transaction_id") != null)
					obj.setTransactionId(UUID.fromString(rs.getString("transaction_id")));
				
				obj.setNotified(rs.getInt("notified"));
				
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
		String sql = "update players set name = ?, tickets = ?, transaction_id = ?, notified = ? where uuid = ? and draw_id = ?";
		
		HashMap<Integer, Object> params = new HashMap<Integer, Object>();
		params.put(1, name);
		params.put(2, tickets);
		params.put(3, transactionId.toString());
		params.put(4, notified);
		params.put(5, uuid.toString());
		params.put(6, drawId);
		
		LotteryPlugin.db.update(sql, params);
	}
	
	public static LotteryPlayer create(UUID uuid, String name, int drawId)
	{
		String sql = "insert into players (uuid, name, draw_id) values (?, ?, ?)";
		
		HashMap<Integer, Object> params = new HashMap<Integer, Object>();
		params.put(1, uuid.toString());
		params.put(2, name);
		params.put(3, drawId);
		
		int newId = LotteryPlugin.db.insert(sql, params);
		
		if(newId > 0)
			return LotteryPlayer.getById(newId);
		
		return null;
	}
	
	public static LotteryPlayer pickWinnerOld(int drawId)
	{
		// Do a little clean up first.
		String sql = "delete from players where tickets = 0";
		LotteryPlugin.db.execute(sql, null);
		
		sql = "select t.* from players t ";
		sql += "inner join (select t.id, sum(tt.tickets) as cummulative_weight ";
		sql += "from players t ";
		sql += "inner join players tt on tt.id <= t.id ";
		sql += "where t.draw_id = ? ";
		sql += "group by t.id) tc ";
		sql += "on tc.id = t.id, ";
		sql += "(select sum(tickets) as total_weight from players where draw_id = ?) tt, ";
		sql += "(select rand() as rnd) r ";
		sql += "where r.rnd * tt.total_weight <= tc.cummulative_weight ";
		sql += "and draw_id = ? ";
		sql += "order by t.id asc limit 0, 1";
		
		HashMap<Integer, Object> params = new HashMap<Integer, Object>();
		params.put(1, drawId);
		params.put(2, drawId);
		params.put(3, drawId);
		
		ResultSet rs = LotteryPlugin.db.select(sql, params);
		
		return LotteryPlayer.populate(rs);
	}
	
	public static LotteryPlayer pickWinnerNew(int drawId)
	{
		// Do a little clean up first.
		String sql = "delete from players where tickets = 0";
		LotteryPlugin.db.execute(sql, null);
		
		sql = "select * from players where draw_id = ?";
		
		HashMap<Integer, Object> params = new HashMap<Integer, Object>();
		params.put(1, drawId);
		
		ResultSet rs = LotteryPlugin.db.select(sql, params);
		
		List<Integer> tickets = new ArrayList<Integer>();
		
		try
		{
			if(rs != null && rs.isBeforeFirst())
			{
				while(!rs.isLast())
				{
					rs.next();
					
					int count = rs.getInt("tickets");
					int player_id = rs.getInt("id");
					
					for(int i = 1; i <= count; i++)
						tickets.add(player_id);
				}
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		
		if(tickets.size() > 0)
		{
			// Shuffle the array. Now we can just pick the first entry, as that will be "random enough".
			Collections.shuffle(tickets);
			
			return LotteryPlayer.getById(tickets.get(0));
		}
		
		return null;
	}
	
	public static LotteryPlayer get(Player player, int drawId)
	{
		String sql = "select * from players where uuid = ? and draw_id = ?";
		
		HashMap<Integer, Object> params = new HashMap<Integer, Object>();
		params.put(1, player.getUniqueId().toString());
		params.put(2, drawId);
		
		ResultSet rs = LotteryPlugin.db.select(sql, params);
		
		LotteryPlayer p = LotteryPlayer.populate(rs);
		
		if(p != null)
		{		
			if(!p.getName().equals(player.getName()))
			{
				p.setName(player.getName());
				p.update();
			}
		}
		
		return p;
	}
	
	public static List<LotteryPlayer> getForNotification()
	{
		String sql = "select players.*, draws.draw_date, draws.pot from players join draws on draws.id = draw_id and draws.winner_id = players.id where players.notified = 0 order by draws.draw_date desc";
		
		ResultSet rs = LotteryPlugin.db.select(sql, null);
		
		List<LotteryPlayer> list = new ArrayList<LotteryPlayer>();
		
		try
		{
			if(rs != null && rs.isBeforeFirst())
			{
				while(!rs.isLast())
				{
					LotteryPlayer obj = LotteryPlayer.populate(rs);
					
					if(obj != null)
					{
						obj.setWonDrawDate(rs.getTimestamp("draw_date"));
						obj.setWonDrawPot(rs.getDouble("pot"));
					
						list.add(obj);
					}
				}
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		
		return list;
	}
	
	private static LotteryPlayer getById(int id)
	{
		String sql = "select * from players where id = ?";
		
		HashMap<Integer, Object> params = new HashMap<Integer, Object>();
		params.put(1, id);
		
		ResultSet rs = LotteryPlugin.db.select(sql, params);
		
		return LotteryPlayer.populate(rs);
	}
	
	public void addTickets(int amount)
	{
		tickets += amount;
	}
}