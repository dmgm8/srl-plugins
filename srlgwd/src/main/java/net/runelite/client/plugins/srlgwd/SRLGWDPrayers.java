/*
 * Copyright (c) 2019, Ganom <https://github.com/Ganom>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.srlgwd;

import com.openosrs.client.game.NPCManager;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.pf4j.Extension;

@Extension
@PluginDescriptor(
	name = "SRL GWD Prayers",
	enabledByDefault = false,
	description = "SRL GWD Prayers",
	tags = {"srl", "prayers"}
)
public class SRLGWDPrayers extends Plugin
{
	private static final int GENERAL_REGION = 11347;
	private static final int ARMA_REGION = 11346;
	private static final int SARA_REGION = 11602;
	private static final int ZAMMY_REGION = 11603;
	public static final int MINION_AUTO1 = 6154;
	public static final int MINION_AUTO2 = 6156;
	public static final int MINION_AUTO3 = 7071;
	public static final int MINION_AUTO4 = 7073;
	public static final int GENERAL_AUTO1 = 7018;
	public static final int GENERAL_AUTO2 = 7020;
	public static final int GENERAL_AUTO3 = 7021;
	public static final int ZAMMY_GENERIC_AUTO = 64;
	public static final int KRIL_AUTO = 6948;
	public static final int KRIL_SPEC = 6950;
	public static final int ZAKL_AUTO = 7077;
	public static final int BALFRUG_AUTO = 4630;
	public static final int ZILYANA_MELEE_AUTO = 6964;
	public static final int ZILYANA_AUTO = 6967;
	public static final int ZILYANA_SPEC = 6970;
	public static final int STARLIGHT_AUTO = 6376;
	public static final int BREE_AUTO = 7026;
	public static final int GROWLER_AUTO = 7037;
	public static final int KREE_RANGED = 6978;
	public static final int SKREE_AUTO = 6955;
	public static final int GEERIN_AUTO = 6956;
	public static final int GEERIN_FLINCH = 6958;
	public static final int KILISA_AUTO = 6957;

	@Inject
	private Client client;

	@Inject
	private NPCManager npcManager;

	@Getter(AccessLevel.PACKAGE)
	private Set<SRLGWDPrayersNPCContainer> SRLGWDNpcContainers = new HashSet<>();
	private boolean validRegion;

	@Getter(AccessLevel.PACKAGE)
	private long lastTickTime;

	@Override
	public void startUp()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (regionCheck())
		{
			SRLGWDNpcContainers.clear();
			for (NPC npc : client.getNpcs())
			{
				addNpc(npc);
			}
			validRegion = true;
		}
		else if (!regionCheck())
		{
			validRegion = false;
			SRLGWDNpcContainers.clear();
		}
	}

	@Override
	public void shutDown()
	{
		SRLGWDNpcContainers.clear();
		validRegion = false;
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (regionCheck())
		{
			SRLGWDNpcContainers.clear();
			for (NPC npc : client.getNpcs())
			{
				addNpc(npc);
			}
			validRegion = true;
		}
		else if (!regionCheck())
		{
			validRegion = false;
			SRLGWDNpcContainers.clear();
		}
	}

	@Subscribe
	private void onNpcSpawned(NpcSpawned event)
	{
		if (!validRegion)
		{
			return;
		}

		addNpc(event.getNpc());
	}

	@Subscribe
	private void onNpcDespawned(NpcDespawned event)
	{
		if (!validRegion)
		{
			return;
		}

		removeNpc(event.getNpc());
	}

	@Subscribe
	public void onGameTick(GameTick Event)
	{
		lastTickTime = System.currentTimeMillis();

		if (!validRegion)
		{
			return;
		}
		handleBosses();
	}

	private int gameTik = 0;

	private void handleBosses()
	{
		gameTik = client.getGameCycle();
		TreeMap<Integer, TreeMap<Integer, Prayer>> tickAttackMap = new TreeMap<>();
		int priority = 999;

		for (SRLGWDPrayersNPCContainer npc : getSRLGWDNpcContainers())
		{
			npc.setNpcInteracting(npc.getNpc().getInteracting());

			if (npc.getTicksUntilAttack() >= 0)
			{
				npc.setTicksUntilAttack(npc.getTicksUntilAttack() - 1);
			}
			/*if (npc.getTicksUntilAttack() == 1)
			{
				activatePrayer(npc.getAttackStyle().getPrayer());
			}*/
			for (int animation : npc.getAnimations())
			{
				if (animation == npc.getNpc().getAnimation() && npc.getTicksUntilAttack() < 1)
				{
					npc.setTicksUntilAttack(npc.getAttackSpeed());
				}
			}
			int ticksLeft = npc.getTicksUntilAttack();
			var attacks = tickAttackMap.computeIfAbsent(ticksLeft, (k) -> new TreeMap<>());
			switch (npc.getMonsterType())
			{
				case SERGEANT_STRONGSTACK:
					priority = 3;
					break;
				case SERGEANT_STEELWILL:
					priority = 1;
					break;
				case SERGEANT_GRIMSPIKE:
					priority = 2;
					break;
				case GENERAL_GRAARDOR:
					priority = 0;
					break;
				default:
					break;
			}

			attacks.putIfAbsent(priority, npc.getAttackStyle().getPrayer());
		}
		if (!tickAttackMap.isEmpty())
		{
			for (var tickEntry : tickAttackMap.entrySet())
			{
				var attackEntry = tickEntry.getValue().firstEntry();
				Prayer prayer = attackEntry.getValue();
				if (prayer != null)
				{
					activatePrayer(prayer);
				}
			}
		}
	}

	private boolean regionCheck()
	{
		return Arrays.stream(client.getMapRegions()).anyMatch(
			x -> x == ARMA_REGION || x == GENERAL_REGION || x == ZAMMY_REGION || x == SARA_REGION
		);
	}

	private void addNpc(NPC npc)
	{
		if (npc == null)
		{
			return;
		}

		switch (npc.getId())
		{
			case NpcID.SERGEANT_STRONGSTACK:
			case NpcID.SERGEANT_STEELWILL:
			case NpcID.SERGEANT_GRIMSPIKE:
			case NpcID.GENERAL_GRAARDOR:
			case NpcID.TSTANON_KARLAK:
			case NpcID.BALFRUG_KREEYATH:
			case NpcID.ZAKLN_GRITCH:
			case NpcID.KRIL_TSUTSAROTH:
			case NpcID.STARLIGHT:
			case NpcID.BREE:
			case NpcID.GROWLER:
			case NpcID.COMMANDER_ZILYANA:
			case NpcID.FLIGHT_KILISA:
			case NpcID.FLOCKLEADER_GEERIN:
			case NpcID.WINGMAN_SKREE:
			case NpcID.KREEARRA:
					SRLGWDNpcContainers.add(new SRLGWDPrayersNPCContainer(npc, npcManager.getAttackSpeed(npc.getId())));
				break;
		}
	}

	private void removeNpc(NPC npc)
	{
		if (npc == null)
		{
			return;
		}

		switch (npc.getId())
		{
			case NpcID.SERGEANT_STRONGSTACK:
			case NpcID.SERGEANT_STEELWILL:
			case NpcID.SERGEANT_GRIMSPIKE:
			case NpcID.GENERAL_GRAARDOR:
			case NpcID.TSTANON_KARLAK:
			case NpcID.BALFRUG_KREEYATH:
			case NpcID.ZAKLN_GRITCH:
			case NpcID.KRIL_TSUTSAROTH:
			case NpcID.STARLIGHT:
			case NpcID.BREE:
			case NpcID.GROWLER:
			case NpcID.COMMANDER_ZILYANA:
			case NpcID.FLIGHT_KILISA:
			case NpcID.FLOCKLEADER_GEERIN:
			case NpcID.WINGMAN_SKREE:
			case NpcID.KREEARRA:
				SRLGWDNpcContainers.removeIf(c -> c.getNpc() == npc);
				break;
		}
	}

	private MenuEntry entry = null;

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (entry != null)
		{
			event.setMenuEntry(entry);
		}
		entry = null;
	}

	public void activatePrayer(Prayer prayer)
	{
		if (prayer == null)
		{
			return;
		}

		if (client.isPrayerActive(prayer))
		{
			return;
		}

		WidgetInfo widgetInfo = prayer.getWidgetInfo();

		if (widgetInfo == null)
		{
			return;
		}

		Widget prayer_widget = client.getWidget(widgetInfo);

		if (prayer_widget == null)
		{
			return;
		}

		if (client.getBoostedSkillLevel(Skill.PRAYER) <= 0)
		{
			return;
		}

		entry = new MenuEntry("Activate", prayer_widget.getName(), 1, MenuAction.CC_OP.getId(), prayer_widget.getItemId(), prayer_widget.getId(), false);
		click();
	}

	public void click()
	{
		Point pos = client.getMouseCanvasPosition();
		if (client.isStretchedEnabled())
		{
			final Dimension stretched = client.getStretchedDimensions();
			final Dimension real = client.getRealDimensions();
			final double width = (stretched.width / real.getWidth());
			final double height = (stretched.height / real.getHeight());
			final Point point = new Point((int) (pos.getX() * width), (int) (pos.getY() * height));
			client.getCanvas().dispatchEvent(new MouseEvent(client.getCanvas(), 501, System.currentTimeMillis(), 0, point.getX(), point.getY(), 1, false, 1));
			client.getCanvas().dispatchEvent(new MouseEvent(client.getCanvas(), 502, System.currentTimeMillis(), 0, point.getX(), point.getY(), 1, false, 1));
			client.getCanvas().dispatchEvent(new MouseEvent(client.getCanvas(), 500, System.currentTimeMillis(), 0, point.getX(), point.getY(), 1, false, 1));
			return;
		}

		client.getCanvas().dispatchEvent(new MouseEvent(client.getCanvas(), 501, System.currentTimeMillis(), 0, pos.getX(), pos.getY(), 1, false, 1));
		client.getCanvas().dispatchEvent(new MouseEvent(client.getCanvas(), 502, System.currentTimeMillis(), 0, pos.getX(), pos.getY(), 1, false, 1));
		client.getCanvas().dispatchEvent(new MouseEvent(client.getCanvas(), 500, System.currentTimeMillis(), 0, pos.getX(), pos.getY(), 1, false, 1));
	}

}
