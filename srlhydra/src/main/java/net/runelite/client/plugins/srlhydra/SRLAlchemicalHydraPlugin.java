/*
 * Copyright (c) 2019, Lucas <https://github.com/lucwousin>
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
package net.runelite.client.plugins.srlhydra;

import com.google.inject.Provides;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.srlhydra.entity.SRLHydra;
import net.runelite.client.plugins.srlhydra.entity.SRLHydra.AttackStyle;
import net.runelite.client.plugins.srlhydra.entity.SRLHydraPhase;
import org.pf4j.Extension;

@Singleton
@Extension
@PluginDescriptor(
	name = "SRL Alchemical Hydra",
	enabledByDefault = false,
	description = "A plugin for the Alchemical Hydra boss.",
	tags = {"alchemical", "hydra"}
)
public class SRLAlchemicalHydraPlugin extends Plugin
{
	private static final String MESSAGE_NEUTRALIZE = "The chemicals neutralise the Alchemical Hydra's defences!";
	private static final String MESSAGE_STUN = "The Alchemical Hydra temporarily stuns you.";

	private static final int[] HYDRA_REGIONS = {5279, 5280, 5535, 5536};

	@Inject
	private Client client;

	private boolean atHydra;

	@Getter
	private SRLHydra SRLHydra;
	public static final int HYDRA_1_1 = 8237;
	public static final int HYDRA_1_2 = 8238;
	public static final int HYDRA_LIGHTNING = 8241;
	public static final int HYDRA_2_1 = 8244;
	public static final int HYDRA_2_2 = 8245;
	public static final int HYDRA_FIRE = 8248;
	public static final int HYDRA_3_1 = 8251;
	public static final int HYDRA_3_2 = 8252;
	public static final int HYDRA_4_1 = 8257;
	public static final int HYDRA_4_2 = 8258;

	@Getter
	private final Map<LocalPoint, Projectile> poisonProjectiles = new HashMap<>();


	@Provides
	SRLHydraConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SRLHydraConfig.class);
	}

	private int lastAttackTick = -1;

	@Inject
	private SRLHydraConfig config;

	@Override
	protected void startUp()
	{
		if (client.getGameState() == GameState.LOGGED_IN && isInHydraRegion())
		{
			init();
		}
	}

	private void init()
	{
		atHydra = true;

		for (final NPC npc : client.getNpcs())
		{
			onNpcSpawned(new NpcSpawned(npc));
		}
	}

	@Override
	protected void shutDown()
	{
		atHydra = false;

		SRLHydra = null;
		poisonProjectiles.clear();
		lastAttackTick = -1;
	}

	@Subscribe
	private void onGameStateChanged(final GameStateChanged event)
	{
		final GameState gameState = event.getGameState();

		switch (gameState)
		{
			case LOGGED_IN:
				if (isInHydraRegion())
				{
					if (!atHydra)
					{
						init();
					}
				}
				else
				{
					if (atHydra)
					{
						shutDown();
					}
				}
				break;
			case HOPPING:
			case LOGIN_SCREEN:
				if (atHydra)
				{
					shutDown();
				}
			default:
				break;
		}
	}

	@Subscribe
	private void onGameTick(final GameTick event)
	{
		if (atHydra) {
			final Prayer prayer = SRLHydra.getNextAttack().getPrayer();
			if (!client.isPrayerActive(prayer)) {
				activatePrayer(prayer);
			}

		}
	}

	@Subscribe
	private void onNpcSpawned(final NpcSpawned event)
	{
		final NPC npc = event.getNpc();

		if (npc.getId() == NpcID.ALCHEMICAL_HYDRA)
		{
			SRLHydra = new SRLHydra(npc);
		}
	}

	@Subscribe
	private void onAnimationChanged(final AnimationChanged event)
	{
		final Actor actor = event.getActor();

		if (SRLHydra == null || actor != SRLHydra.getNpc())
		{
			return;
		}

		final SRLHydraPhase phase = SRLHydra.getPhase();

		final int animationId = actor.getAnimation();

		if ((animationId == phase.getDeathAnimation2() && phase != SRLHydraPhase.FLAME)
			|| (animationId == phase.getDeathAnimation1() && phase == SRLHydraPhase.FLAME))
		{
			switch (phase)
			{
				case POISON:
					SRLHydra.changePhase(SRLHydraPhase.LIGHTNING);
					break;
				case LIGHTNING:
					SRLHydra.changePhase(SRLHydraPhase.FLAME);
					break;
				case FLAME:
					SRLHydra.changePhase(SRLHydraPhase.ENRAGED);
					break;
				case ENRAGED:
					// NpcDespawned event does not fire for Hydra inbetween kills; must use death animation.
					SRLHydra = null;

					if (!poisonProjectiles.isEmpty())
					{
						poisonProjectiles.clear();
					}
					break;
			}

			return;
		}
		else if (animationId == phase.getSpecialAnimationId() && phase.getSpecialAnimationId() != 0)
		{
			SRLHydra.setNextSpecial();
		}

		if (!poisonProjectiles.isEmpty())
		{
			poisonProjectiles.values().removeIf(p -> p.getEndCycle() < client.getGameCycle());
		}
	}

	@Subscribe
	private void onProjectileMoved(final ProjectileMoved event)
	{
		final Projectile projectile = event.getProjectile();

		if (SRLHydra == null || client.getGameCycle() >= projectile.getStartMovementCycle())
		{
			return;
		}

		final int projectileId = projectile.getId();

		if (SRLHydra.getPhase().getSpecialProjectileId() == projectileId)
		{
			if (SRLHydra.getAttackCount() >= SRLHydra.getNextSpecial())
			{
				SRLHydra.setNextSpecial();
			}

			poisonProjectiles.put(event.getPosition(), projectile);
		}
		else if (client.getTickCount() != lastAttackTick
			&& (projectileId == AttackStyle.MAGIC.getProjectileID() || projectileId == AttackStyle.RANGED.getProjectileID()))
		{
			SRLHydra.handleProjectile(projectileId);

			lastAttackTick = client.getTickCount();
		}
	}

	@Subscribe
	private void onChatMessage(final ChatMessage event)
	{
		final ChatMessageType chatMessageType = event.getType();

		if (chatMessageType != ChatMessageType.SPAM && chatMessageType != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		final String message = event.getMessage();

		if (message.equals(MESSAGE_NEUTRALIZE))
		{
			SRLHydra.setImmunity(false);
		}
	}

	private boolean isInHydraRegion()
	{
		return client.isInInstancedRegion() && Arrays.equals(client.getMapRegions(), HYDRA_REGIONS);
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
