/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2020, dutta64 <https://github.com/dutta64>
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
 *
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
package net.runelite.client.plugins.srldagannoths;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.srldagannoths.entity.SRLDagannothKing;
import org.pf4j.Extension;

@Singleton
@Extension
@PluginDescriptor(
	name = "SRL Dagannoth Kings",
	enabledByDefault = false,
	description = "A plugin for the Dagannoth Kings.",
	tags = {"dagannoth", "kings", "dks", "daggs"}
)
public class SRLDagannothsPlugin extends Plugin
{
	public static final int DAG_REX = 2853;
	public static final int DAG_PRIME = 2854;
	public static final int DAG_SUPREME = 2855;

	private static final int WATERBITH_REGION = 11589;

	@Inject
	private Client client;

	@Getter
	private final Set<SRLDagannothKing> SRLDagannothKings_Prime = new HashSet<>();

	@Getter
	private final Set<SRLDagannothKing> SRLDagannothKings_Rex = new HashSet<>();

	@Getter
	private final Set<SRLDagannothKing> SRLDagannothKings_Supreme = new HashSet<>();


	private boolean atDks;

	@Inject
	private ConfigManager configManager;


	@Provides
	SRLDagannothsConfig getConfig(ConfigManager configManager) {
		return configManager.getConfig(SRLDagannothsConfig.class);
	}

	@Override
	public void startUp()
	{
		if (client.getGameState() != GameState.LOGGED_IN || !atDks())
		{
			return;
		}

		init();
	}

	private void init()
	{
		atDks = true;

		for (final NPC npc : client.getNpcs())
		{
			addNpc(npc);
		}
	}

	@Override
	public void shutDown()
	{
		atDks = false;

		SRLDagannothKings_Prime.clear();
		SRLDagannothKings_Rex.clear();
		SRLDagannothKings_Supreme.clear();
	}

	@Subscribe
	private void onGameStateChanged(final GameStateChanged event) {
		final GameState gameState = event.getGameState();

		switch (gameState) {
			case LOGGED_IN:
				if (atDks()) {
					if (!atDks) {
						init();
					}
				} else {
					if (atDks) {
						shutDown();
					}
				}
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				shutDown();
				break;
		}
	}

	@Subscribe
	private void onGameTick(final GameTick Event)
	{

		boolean halt = false;

		if (!SRLDagannothKings_Prime.isEmpty()) {
			for (final SRLDagannothKing SRLDagannothKing : SRLDagannothKings_Prime) {
				SRLDagannothKing.updateTicksUntilNextAnimation();
				final int ticksUntilNextAnimation = SRLDagannothKing.getTicksUntilNextAnimation();
				if (ticksUntilNextAnimation == 1) {
					activatePrayer(SRLDagannothKing.getAttackStyle().getPrayer());
					halt = true;
				}
			}
		}

		if (!SRLDagannothKings_Supreme.isEmpty()) {
			for (final SRLDagannothKing SRLDagannothKing : SRLDagannothKings_Supreme) {
				SRLDagannothKing.updateTicksUntilNextAnimation();
				final int ticksUntilNextAnimation = SRLDagannothKing.getTicksUntilNextAnimation();
				if (halt == false) {
					if (ticksUntilNextAnimation == 1) {
						activatePrayer(SRLDagannothKing.getAttackStyle().getPrayer());
						halt = true;
					}
				}
			}
		}

		if (!SRLDagannothKings_Rex.isEmpty()) {
			for (final SRLDagannothKing SRLDagannothKing : SRLDagannothKings_Rex) {
				SRLDagannothKing.updateTicksUntilNextAnimation();
				final int ticksUntilNextAnimation = SRLDagannothKing.getTicksUntilNextAnimation();
				if (halt == false) {
					if (ticksUntilNextAnimation == 1) {
						activatePrayer(SRLDagannothKing.getAttackStyle().getPrayer());
						halt = true;
					}
				}
			}
		}
	}

	@Subscribe
	private void onNpcSpawned(final NpcSpawned event)
	{
		addNpc(event.getNpc());
	}

	@Subscribe
	private void onNpcDespawned(final NpcDespawned event)
	{
		removeNpc(event.getNpc());
	}

	private void addNpc(final NPC npc)
	{
		switch (npc.getId())
		{
			case NpcID.DAGANNOTH_REX:
				SRLDagannothKings_Rex.add(new SRLDagannothKing(npc));
				break;
			case NpcID.DAGANNOTH_PRIME:
				SRLDagannothKings_Prime.add(new SRLDagannothKing(npc));
				break;
			case NpcID.DAGANNOTH_SUPREME:
				SRLDagannothKings_Supreme.add(new SRLDagannothKing(npc));
				break;
			default:
				break;
		}
	}

	private void removeNpc(final NPC npc)
	{
		switch (npc.getId())
		{
			case NpcID.DAGANNOTH_REX:
				SRLDagannothKings_Rex.remove(npc);
				break;
			case NpcID.DAGANNOTH_PRIME:
				SRLDagannothKings_Prime.remove(npc);
				break;
			case NpcID.DAGANNOTH_SUPREME:
				SRLDagannothKings_Supreme.remove(npc);
				break;
			default:
				break;
		}
	}

	private boolean atDks()
	{
		return Arrays.stream(client.getMapRegions()).anyMatch(x -> x == WATERBITH_REGION);
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

	@Inject
	private ChatMessageManager chatMessageManager;

	public void sendMsg (String message) {
		if (getConfig(configManager).useTestStuff()) {
			String msg = new ChatMessageBuilder()
					.append(ChatColorType.NORMAL)
					.append(message)
					.build();

			chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage(msg)
					.build());
		}
	}

}
