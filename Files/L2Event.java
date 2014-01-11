/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jserver.gameserver.model.entity;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javolution.util.FastList;
import javolution.util.FastMap;

import com.l2jserver.gameserver.cache.HtmCache;
import com.l2jserver.gameserver.datatables.NpcTable;
import com.l2jserver.gameserver.datatables.SpawnTable;
import com.l2jserver.gameserver.model.L2Spawn;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.network.serverpackets.CharInfo;
import com.l2jserver.gameserver.network.serverpackets.ExBrExtraUserInfo;
import com.l2jserver.gameserver.network.serverpackets.MagicSkillUse;
import com.l2jserver.gameserver.network.serverpackets.NpcHtmlMessage;
import com.l2jserver.gameserver.network.serverpackets.UserInfo;
import com.l2jserver.gameserver.templates.chars.L2NpcTemplate;
import com.l2jserver.util.PlayerEventStatus;
import com.l2jserver.util.ValueSortMap;

/**
 * @since $Revision: 1.3.4.1 $ $Date: 2005/03/27 15:29:32 $
 * This ancient thingie got reworked by Nik at $Date: 2011/05/17 21:51:39 $
 * Yeah, for 6 years no one bothered reworking this buggy event engine.
 */

public class L2Event
{
	protected static final Logger _log = Logger.getLogger(L2Event.class.getName());
	public static EventState eventState = EventState.OFF;
	public static String _eventName = "";
	public static String _eventCreator = "";
	public static String _eventInfo = "";
	public static int _teamsNumber = 0;
	public static final Map<Integer, String> _teamNames = new FastMap<Integer, String>();
	public static final List<L2PcInstance> _registeredPlayers = new FastList<L2PcInstance>();
	public static final Map<Integer, FastList<L2PcInstance>> _teams = new FastMap<Integer, FastList<L2PcInstance>>();
	public static int _npcId = 0;
	//public static final List<L2Npc> _npcs = new FastList<L2Npc>();
	private static final Map<L2PcInstance, PlayerEventStatus> _connectionLossData = new FastMap<L2PcInstance, PlayerEventStatus>();
	
	public enum EventState
	{
		OFF, // Not running
		STANDBY, // Waiting for participants to register
		ON // Registration is over and the event has started.
	}
	
	/**
	 * 
	 * @param player
	 * @return The team ID where the player is in, or -1 if player is null or team not found.
	 */
	public static int getPlayerTeamId(L2PcInstance player)
	{
		if (player == null)
			return -1;
		
		for (Entry<Integer, FastList<L2PcInstance>> team : _teams.entrySet())
		{
			if (team.getValue().contains(player))
				return team.getKey();
		}
		
		return -1;
	}
	
	public static List<L2PcInstance> getTopNKillers(int n)
	{
		Map<L2PcInstance, Integer> tmp = new FastMap<L2PcInstance, Integer>();
		
		for (FastList<L2PcInstance> teamList : _teams.values())
		{
			for (L2PcInstance player : teamList)
			{
				if (player.getEventStatus() == null)
					continue;
				
				tmp.put(player, player.getEventStatus().kills.size());
			}
		}
		
		ValueSortMap.sortMapByValue(tmp, false);
		
		// If the map size is less than "n", n will be as much as the map size
		if (tmp.size() <= n)
		{
			List<L2PcInstance> toReturn = new FastList<L2PcInstance>();
			toReturn.addAll(tmp.keySet());
			return toReturn;
		}
		else
		{
			List<L2PcInstance> toReturn = new FastList<L2PcInstance>();
			toReturn.addAll(tmp.keySet());
			return toReturn.subList(1, n);
		}
	}

	public static void showEventHtml(L2PcInstance player, String objectid)
	{//TODO: work on this
		
		if (eventState == EventState.STANDBY)
		{
			try
			{
				final String htmContent;
				NpcHtmlMessage html = new NpcHtmlMessage(5);
				
				if (_registeredPlayers.contains(player))
					htmContent = HtmCache.getInstance().getHtm(player.getHtmlPrefix(), "data/html/mods/EventEngine/Participating.htm");
				else
					htmContent = HtmCache.getInstance().getHtm(player.getHtmlPrefix(), "data/html/mods/EventEngine/Participation.htm");
				
				if (htmContent != null)
					html.setHtml(htmContent);
				
				html.replace("%objectId%", objectid); // Yeah, we need this.
				html.replace("%eventName%", _eventName);
				html.replace("%eventCreator%", _eventCreator);
				html.replace("%eventInfo%", _eventInfo);
				player.sendPacket(html);
			}
			catch (Exception e)
			{
				_log.log(Level.WARNING, "Exception on showEventHtml(): " + e.getMessage(), e);
			}
		}
	}
	
	/**
	 * Spawns an event participation NPC near the player.
	 * The npc id used to spawning is L2Event._npcId
	 */
	public static void spawnEventNpc(L2PcInstance target)
	{
		
		L2NpcTemplate template = NpcTable.getInstance().getTemplate(_npcId);
		
		try
		{
			L2Spawn spawn = new L2Spawn(template);
			
			spawn.setLocx(target.getX() + 50);
			spawn.setLocy(target.getY() + 50);
			spawn.setLocz(target.getZ());
			spawn.setAmount(1);
			spawn.setHeading(target.getHeading());
			
			SpawnTable.getInstance().addNewSpawn(spawn, false);
			
			spawn.init();
			spawn.getLastSpawn().setCurrentHp(999999999);
			spawn.getLastSpawn().setTitle(_eventName);
			spawn.getLastSpawn().isEventMob = true;
			//spawn.getLastSpawn().decayMe();
			//spawn.getLastSpawn().spawnMe(spawn.getLastSpawn().getX(), spawn.getLastSpawn().getY(), spawn.getLastSpawn().getZ());
			
			spawn.getLastSpawn().broadcastPacket(new MagicSkillUse(spawn.getLastSpawn(), spawn.getLastSpawn(), 1034, 1, 1, 1));
			
			//_npcs.add(spawn.getLastSpawn());
			
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Exception on spawn(): " + e.getMessage(), e);
		}
		
	}
	
	public static void unspawnEventNpcs()
	{
		//Its a little rough, but for sure it will remove every damn event NPC.
		for (L2Spawn spawn : SpawnTable.getInstance().getSpawnTable())
		{
			if (spawn.getLastSpawn() != null && spawn.getLastSpawn().isEventMob)
			{
				spawn.getLastSpawn().deleteMe();
				spawn.stopRespawn();
				SpawnTable.getInstance().deleteSpawn(spawn, false);
			}
		}
		//for (L2Npc npc : _npcs)
		//	npc.deleteMe();
	}
	
	/**
	 * 
	 * @return False: If player is null, his event status is null or the event state is off.
	 * True: if the player is inside the _registeredPlayers list while the event state is STANDBY.
	 * If the event state is ON, it will check if the player is inside in one of the teams.
	 */
	public static boolean isParticipant(L2PcInstance player)
	{
		if (player == null || player.getEventStatus() == null)
			return false;
		
		switch (eventState)
		{
			case OFF:
				return false;
			case STANDBY:
				return _registeredPlayers.contains(player);
			case ON:
				for (FastList<L2PcInstance> teamList : _teams.values())
				{
					if (teamList.contains(player))
						return true;
				}
		}
		return false;
		
	}
	
	/**
	 * 
	 * Adds the player to the list of participants.
	 * If the event state is NOT STANDBY, the player wont be registered.
	 */
	public static void registerPlayer(L2PcInstance player)
	{
		if (eventState != EventState.STANDBY)
		{
			player.sendMessage("The registration period for this event is over.");
			return;
		}
		
		_registeredPlayers.add(player);
	}
	
	/**
	 * 
	 * Removes the player from the participating players and the teams and restores
	 * his init stats before he registered at the event (loc, pvp, pk, title etc)
	 */
	public static void removeAndResetPlayer(L2PcInstance player)
	{
		
		try
		{			
			if (isParticipant(player))
			{
				if (player.isDead())
				{
					player.restoreExp(100.0);
					player.doRevive();
					player.setCurrentHpMp(player.getMaxHp(), player.getMaxMp());
					player.setCurrentCp(player.getMaxCp());
				}
				
				player.getPoly().setPolyInfo(null, "1");
				player.decayMe();
				player.spawnMe(player.getX(), player.getY(), player.getZ());
				CharInfo info1 = new CharInfo(player);
				player.broadcastPacket(info1);
				UserInfo info2 = new UserInfo(player);
				player.sendPacket(info2);
				player.broadcastPacket(new ExBrExtraUserInfo(player));
				
				player.stopTransformation(true);
			}
			
			if (player.getEventStatus() != null)
				player.getEventStatus().restoreInits();
			
			player.setEventStatus(null);
			
			_registeredPlayers.remove(player);
			int teamId = getPlayerTeamId(player);
			if (_teams.containsKey(teamId))
				_teams.get(teamId).remove(player);
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Error at unregisterAndResetPlayer in the event:" + e.getMessage(), e);
		}
	}
	
	/**
	 * 
	 * The player's event status will be saved at _connectionLossData
	 */
	public static void savePlayerEventStatus(L2PcInstance player)
	{
			_connectionLossData.put(player, player.getEventStatus());
	}
	
	/**
	 * 
	 * If _connectionLossData contains the player, it will restore the player's event status.
	 * Also it will remove the player from the _connectionLossData.
	 */
	public static void restorePlayerEventStatus(L2PcInstance player)
	{
		if (_connectionLossData.containsKey(player))
		{
			player.setEventStatus(_connectionLossData.get(player));
			_connectionLossData.remove(player);
		}
	}
	
	/**
	 * If the event is ON or STANDBY, it will not start.
	 * Sets the event state to STANDBY and spawns registration NPCs
	 * @return a string with information if the event participation has been successfully started or not.
	 */
	public static String startEventParticipation()
	{
		try
		{
			switch (eventState)
			{
				case ON:
					return "Cannot start event, it is already on.";
				case STANDBY:
					return "Cannot start event, it is on standby mode.";
				case OFF: // Event is off, so no problem turning it on.
					eventState = EventState.STANDBY;
					break;
			}
			
			// Just in case
			unspawnEventNpcs();
			_registeredPlayers.clear();
			//_npcs.clear();
			
			if (NpcTable.getInstance().getTemplate(_npcId) == null)
				return "Cannot start event, invalid npc id.";
			
			DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream("data/events/" + _eventName)));
			BufferedReader inbr = new BufferedReader(new InputStreamReader(in));
			_eventCreator = inbr.readLine();
			_eventInfo = inbr.readLine();
			
			List<L2PcInstance> temp = new FastList<L2PcInstance>();
			for (L2PcInstance player : L2World.getInstance().getAllPlayers().values())
			{
				if (!player.isOnline()) // Offline shops? 
					continue;
				
				if (!temp.contains(player))
				{
					spawnEventNpc(player);
					temp.add(player);
				}
				for (L2PcInstance playertemp : player.getKnownList().getKnownPlayers().values())
				{
					if ((Math.abs(playertemp.getX() - player.getX()) < 1000) && (Math.abs(playertemp.getY() - player.getY()) < 1000) && (Math.abs(playertemp.getZ() - player.getZ()) < 1000))
						temp.add(playertemp);
				}
			}	
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return "Cannot start event participation, an error has occured.";
		}
		
		return "The event participation has been successfully started.";
	}
	
	/**
	 * If the event is ON or OFF, it will not start.
	 * Sets the event state to ON, creates the teams, 
	 * adds the registered players ordered by level at the teams
	 * and adds a new event status to the players.
	 * @return a string with information if the event has been successfully started or not.
	 */
	public static String startEvent()
	{
		try
		{
			switch (eventState)
			{
				case ON:
					return "Cannot start event, it is already on.";
				case STANDBY:
					eventState = EventState.ON;
					break;
				case OFF: // Event is off, so no problem turning it on.
					return "Cannot start event, it is off. Participation start is required.";
			}
			
			// Clean the things we will use, just in case.
			unspawnEventNpcs();
			_teams.clear();
			_connectionLossData.clear();
			
			// Insert empty lists at _teams.
			for (int i = 0; i < _teamsNumber; i++)
				_teams.put(i + 1, new FastList<L2PcInstance>());
			
			int i = 0;
			while (!_registeredPlayers.isEmpty())
			{
				//Get the player with the biggest level
				int max = 0;
				L2PcInstance biggestLvlPlayer = null;
				for (L2PcInstance player : _registeredPlayers)
				{
						if (player == null)
							continue;
						
						if (max < player.getLevel())
						{
							max = player.getLevel();
							biggestLvlPlayer = player;
						}
				}
				
				if (biggestLvlPlayer == null)
					continue;
				
				_registeredPlayers.remove(biggestLvlPlayer);
				_teams.get(i + 1).add(biggestLvlPlayer);
				biggestLvlPlayer.setEventStatus();
				i = (i + 1) % _teamsNumber;
			}
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return "Cannot start event, an error has occured.";
		}
		
		return "The event has been successfully started.";
	}
	
	/**
	 * If the event state is OFF, it will not finish.
	 * Sets the event state to OFF, unregisters and resets the players,
	 * unspawns and clers the event NPCs, clears the teams, registered players,
	 * connection loss data, sets the teams number to 0, sets the event name to empty.
	 * @return a string with information if the event has been successfully stopped or not.
	 */
	public static String finishEvent()
	{
			switch (eventState)
			{
				case OFF:
					return "Cannot finish event, it is already off.";
				case STANDBY:
					for (L2PcInstance player : _registeredPlayers)
						removeAndResetPlayer(player);
					
					unspawnEventNpcs();
					//_npcs.clear();
					_registeredPlayers.clear();
					_teams.clear();
					_connectionLossData.clear();
					_teamsNumber = 0;
					_eventName = "";
					eventState = EventState.OFF;
					return "The event has been stopped at STANDBY mode, all players unregistered and all event npcs unspawned.";
				case ON:
					for (FastList<L2PcInstance> teamList : _teams.values())
					{
						for (L2PcInstance player : teamList)
							removeAndResetPlayer(player);
					}
					
					eventState = EventState.OFF;
					unspawnEventNpcs(); // Just in case
					//_npcs.clear();
					_registeredPlayers.clear();
					_teams.clear();
					_connectionLossData.clear();
					_teamsNumber = 0;
					_eventName = "";
					_npcId = 0;
					_eventCreator = "";
					_eventInfo = "";
					return "The event has been stopped, all players unregistered and all event npcs unspawned.";
			}
		
		return "The event has been successfully finished.";
	}
}
