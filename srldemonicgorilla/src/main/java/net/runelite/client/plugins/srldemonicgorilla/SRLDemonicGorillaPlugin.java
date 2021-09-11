/*
 * Copyright (c) 2018, Woox <https://github.com/wooxsolo>
 * Copyright (c) 2021, SRLJustin <https://github.com/SRLJustin>
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
package net.runelite.client.plugins.srldemonicgorilla;

import com.google.common.collect.ImmutableSet;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.api.MenuAction;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.pf4j.Extension;

@Extension
@PluginDescriptor(
	name = "SRL Demonic Gorillas",
	enabledByDefault = false,
	description = "A plugin for Demonic Gorillas",
	tags = {"combat", "overlay", "pve", "pvm"}
)
public class SRLDemonicGorillaPlugin extends Plugin
{
	private static final Set<Integer> DEMONIC_PROJECTILES = ImmutableSet.of(ProjectileID.DEMONIC_GORILLA_RANGED, ProjectileID.DEMONIC_GORILLA_MAGIC, ProjectileID.DEMONIC_GORILLA_BOULDER);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Getter(AccessLevel.PACKAGE)
	private Map<NPC, SRLDemonicGorilla> gorillas;

	private List<WorldPoint> recentBoulders;

	private List<SRLPendingGorillaAttack> pendingAttacks;

	private Map<Player, SRLMemorizedPlayer> memorizedPlayers;

	@Inject
	private SRLDemonicGorillaConfig config;

	@Provides
	SRLDemonicGorillaConfig provideConfig(final ConfigManager configManager)
	{
		return configManager.getConfig(SRLDemonicGorillaConfig.class);
	}

	@Override
	protected void startUp()
	{
		gorillas = new HashMap<>();
		recentBoulders = new ArrayList<>();
		pendingAttacks = new ArrayList<>();
		memorizedPlayers = new HashMap<>();
		clientThread.invoke(this::reset); // Updates the list of gorillas and players
	}

	@Override
	protected void shutDown()
	{
		gorillas = null;
		recentBoulders = null;
		pendingAttacks = null;
		memorizedPlayers = null;
	}

	private void clear()
	{
		recentBoulders.clear();
		pendingAttacks.clear();
		memorizedPlayers.clear();
		gorillas.clear();
	}

	private void reset()
	{
		recentBoulders.clear();
		pendingAttacks.clear();
		resetGorillas();
		resetPlayers();
	}

	private void resetGorillas()
	{
		gorillas.clear();
		for (NPC npc : client.getNpcs())
		{
			if (isNpcGorilla(npc.getId()))
			{
				gorillas.put(npc, new SRLDemonicGorilla(npc));
			}
		}
	}

	private void resetPlayers()
	{
		memorizedPlayers.clear();
		for (Player player : client.getPlayers())
		{
			memorizedPlayers.put(player, new SRLMemorizedPlayer(player));
		}
	}

	private static boolean isNpcGorilla(int npcId)
	{
		return npcId == NpcID.DEMONIC_GORILLA ||
			npcId == NpcID.DEMONIC_GORILLA_7145 ||
			npcId == NpcID.DEMONIC_GORILLA_7146 ||
			npcId == NpcID.DEMONIC_GORILLA_7147 ||
			npcId == NpcID.DEMONIC_GORILLA_7148 ||
			npcId == NpcID.DEMONIC_GORILLA_7149;
	}

	private void checkGorillaAttackStyleSwitch(SRLDemonicGorilla gorilla, final SRLDemonicGorilla.AttackStyle... protectedStyles)
	{
		if (gorilla.getAttacksUntilSwitch() <= 0 ||
			gorilla.getNextPosibleAttackStyles().isEmpty())
		{
			gorilla.setNextPosibleAttackStyles(Arrays
				.stream(SRLDemonicGorilla.ALL_REGULAR_ATTACK_STYLES)
				.filter(x -> Arrays.stream(protectedStyles).noneMatch(y -> x == y))
				.collect(Collectors.toList()));
			gorilla.setAttacksUntilSwitch(SRLDemonicGorilla.ATTACKS_PER_SWITCH);
			gorilla.setChangedAttackStyleThisTick(true);
		}
	}

	private SRLDemonicGorilla.AttackStyle getProtectedStyle(Player player)
	{
		HeadIcon headIcon = player.getOverheadIcon();
		if (headIcon == null)
		{
			return null;
		}
		switch (headIcon)
		{
			case MELEE:
				return SRLDemonicGorilla.AttackStyle.MELEE;
			case RANGED:
				return SRLDemonicGorilla.AttackStyle.RANGED;
			case MAGIC:
				return SRLDemonicGorilla.AttackStyle.MAGIC;
			default:
				return null;
		}
	}

	@Inject
	private ChatMessageManager chatMessageManager;

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

	public void sendMsg (String message) {
		if (config.showDebugMsg()) {
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
	public void activatePrayer(Prayer prayer, boolean force)
	{
		if (!config.useAssist())
			return;
		if (prayer == null)
		{
			return;
		}
		if (tickDelay > 0 && !force)
		{
			sendMsg("Wanted to swap prayer but we're in tick delay: " + tickDelay);
			return;
		}
		//check if prayer is already active this tick
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

		if (config.useTickDelay()) {
			tickDelay = getRandomTickDelay(config.clickDelayMin());
			sendMsg("Tick delay set: " + tickDelay);
		} else {
			tickDelay = 0;
		}
	}
	Prayer BOOST = null;

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

	private int tickDelay = 0;
	Random r = new Random();
	private int getRandomTickDelay(int min)
	{
		int next = r.nextInt(config.clickDelayMax() - config.clickDelayMin() + 1) + config.clickDelayMin();

		//clamp to min
		if (next < min)
			next = min;

		return next;
	}
	private void onGorillaAttack(SRLDemonicGorilla gorilla, final SRLDemonicGorilla.AttackStyle attackStyle)
	{
		gorilla.setInitiatedCombat(true);

		Player target = (Player) gorilla.getNpc().getInteracting();

		SRLDemonicGorilla.AttackStyle protectedStyle = null;
		if (target != null)
		{
			protectedStyle = getProtectedStyle(target);
		}
		boolean correctPrayer =
			target == null || // If player is out of memory, assume prayer was correct
				(attackStyle != null &&
					attackStyle.equals(protectedStyle));

		//gorilla.getOverheadIcon().toString() = what the gorilla is praying (used for auto swapper)


		if (target == client.getLocalPlayer() && !correctPrayer) {
			if (attackStyle == SRLDemonicGorilla.AttackStyle.MELEE){
				sendMsg("Swapping to PROTECT_FROM_MELEE");
				activatePrayer(Prayer.PROTECT_FROM_MELEE, true);
			}
			if (attackStyle == SRLDemonicGorilla.AttackStyle.MAGIC){
				sendMsg("Swapping to PROTECT_FROM_MAGIC");
				activatePrayer(Prayer.PROTECT_FROM_MAGIC, true);
			}
			if (attackStyle == SRLDemonicGorilla.AttackStyle.RANGED){
				sendMsg("Swapping to PROTECT_FROM_MISSILES");
				activatePrayer(Prayer.PROTECT_FROM_MISSILES, true);
			}
		}
		if (attackStyle == SRLDemonicGorilla.AttackStyle.BOULDER)
		{
			// The gorilla can't throw boulders when it's meleeing
			gorilla.setNextPosibleAttackStyles(gorilla
				.getNextPosibleAttackStyles()
				.stream()
				.filter(x -> x != SRLDemonicGorilla.AttackStyle.MELEE)
				.collect(Collectors.toList()));
		}
		else
		{
			if (correctPrayer)
			{
				gorilla.setAttacksUntilSwitch(gorilla.getAttacksUntilSwitch() - 1);
			}
			else
			{
				// We're not sure if the attack will hit a 0 or not,
				// so we don't know if we should decrease the counter or not,
				// so we keep track of the attack here until the damage splat
				// has appeared on the player.

				int damagesOnTick = client.getTickCount();
				if (attackStyle == SRLDemonicGorilla.AttackStyle.MAGIC)
				{
					SRLMemorizedPlayer mp = memorizedPlayers.get(target);
					WorldArea lastPlayerArea = mp.getLastWorldArea();
					if (lastPlayerArea != null)
					{
						int dist = gorilla.getNpc().getWorldArea().distanceTo(lastPlayerArea);
						damagesOnTick += (dist + SRLDemonicGorilla.PROJECTILE_MAGIC_DELAY) /
								SRLDemonicGorilla.PROJECTILE_MAGIC_SPEED;
					}
				}
				else if (attackStyle == SRLDemonicGorilla.AttackStyle.RANGED)
				{
					SRLMemorizedPlayer mp = memorizedPlayers.get(target);
					WorldArea lastPlayerArea = mp.getLastWorldArea();
					if (lastPlayerArea != null)
					{
						int dist = gorilla.getNpc().getWorldArea().distanceTo(lastPlayerArea);
						damagesOnTick += (dist + SRLDemonicGorilla.PROJECTILE_RANGED_DELAY) /
								SRLDemonicGorilla.PROJECTILE_RANGED_SPEED;
					}
				}
				pendingAttacks.add(new SRLPendingGorillaAttack(gorilla, attackStyle, target, damagesOnTick));
			}

			gorilla.setNextPosibleAttackStyles(gorilla
				.getNextPosibleAttackStyles()
				.stream()
				.filter(x -> x == attackStyle)
				.collect(Collectors.toList()));


			if (gorilla.getNextPosibleAttackStyles().isEmpty())
			{
				// Sometimes the gorilla can switch attack style before it's supposed to
				// if someone was fighting it earlier and then left, so we just
				// reset the counter in that case.

				gorilla.setNextPosibleAttackStyles(Arrays
					.stream(SRLDemonicGorilla.ALL_REGULAR_ATTACK_STYLES)
					.filter(x -> x == attackStyle)
					.collect(Collectors.toList()));
				gorilla.setAttacksUntilSwitch(SRLDemonicGorilla.ATTACKS_PER_SWITCH -
					(correctPrayer ? 1 : 0));
			}
		}

		checkGorillaAttackStyleSwitch(gorilla, protectedStyle);

		int tickCounter = client.getTickCount();
		gorilla.setNextAttackTick(tickCounter + SRLDemonicGorilla.ATTACK_RATE);
	}
	private void checkGorillaAttacks()
	{
		int tickCounter = client.getTickCount();
		for (SRLDemonicGorilla gorilla : gorillas.values())
		{
			Player interacting = (Player) gorilla.getNpc().getInteracting();
			SRLMemorizedPlayer mp = memorizedPlayers.get(interacting);

			if (gorilla.getLastTickInteracting() != null && interacting == null)
			{
				gorilla.setInitiatedCombat(false);
			}
			else if (mp != null && mp.getLastWorldArea() != null &&
				!gorilla.isInitiatedCombat() &&
				tickCounter < gorilla.getNextAttackTick() &&
				gorilla.getNpc().getWorldArea().isInMeleeDistance(mp.getLastWorldArea()))
			{
				gorilla.setInitiatedCombat(true);
				gorilla.setNextAttackTick(tickCounter + 1);
			}

			int animationId = gorilla.getNpc().getAnimation();

			if (gorilla.isTakenDamageRecently() &&
				tickCounter >= gorilla.getNextAttackTick() + 4)
			{
				// The gorilla was flinched, so its next attack gets delayed
				gorilla.setNextAttackTick(tickCounter + SRLDemonicGorilla.ATTACK_RATE / 2);
				gorilla.setInitiatedCombat(true);

				if (mp != null && mp.getLastWorldArea() != null &&
					!gorilla.getNpc().getWorldArea().isInMeleeDistance(mp.getLastWorldArea()) &&
					!gorilla.getNpc().getWorldArea().intersectsWith(mp.getLastWorldArea()))
				{
					// Gorillas stop meleeing when they get flinched
					// and the target isn't in melee distance
					gorilla.setNextPosibleAttackStyles(gorilla
						.getNextPosibleAttackStyles()
						.stream()
						.filter(x -> x != SRLDemonicGorilla.AttackStyle.MELEE)
						.collect(Collectors.toList()));
					if (interacting != null)
					{
						checkGorillaAttackStyleSwitch(gorilla, SRLDemonicGorilla.AttackStyle.MELEE,
							getProtectedStyle(interacting));
					}
				}
			}
			else if (animationId != gorilla.getLastTickAnimation())
			{
				if (animationId == AnimationID.DEMONIC_GORILLA_MELEE_ATTACK)
				{
					onGorillaAttack(gorilla, SRLDemonicGorilla.AttackStyle.MELEE);
				}
				else if (animationId == AnimationID.DEMONIC_GORILLA_MAGIC_ATTACK)
				{
					onGorillaAttack(gorilla, SRLDemonicGorilla.AttackStyle.MAGIC);
				}
				else if (animationId == AnimationID.DEMONIC_GORILLA_RANGED_ATTACK)
				{
					onGorillaAttack(gorilla, SRLDemonicGorilla.AttackStyle.RANGED);
				}
				else if (animationId == AnimationID.DEMONIC_GORILLA_AOE_ATTACK && interacting != null)
				{
					// Note that AoE animation is the same as prayer switch animation
					// so we need to check if the prayer was switched or not.
					// It also does this animation when it spawns, so
					// we need the interacting != null check.

					if (gorilla.getOverheadIcon() == gorilla.getLastTickOverheadIcon())
					{
						// Confirmed, the gorilla used the AoE attack
						onGorillaAttack(gorilla, SRLDemonicGorilla.AttackStyle.BOULDER);
					}
					else
					{
						if (tickCounter >= gorilla.getNextAttackTick())
						{
							gorilla.setChangedPrayerThisTick(true);

							// This part is more complicated because the gorilla may have
							// used an attack, but the prayer switch animation takes
							// priority over normal attack animations.

							int projectileId = gorilla.getRecentProjectileId();
							if (projectileId == ProjectileID.DEMONIC_GORILLA_MAGIC)
							{
								onGorillaAttack(gorilla, SRLDemonicGorilla.AttackStyle.MAGIC);
							}
							else if (projectileId == ProjectileID.DEMONIC_GORILLA_RANGED)
							{
								onGorillaAttack(gorilla, SRLDemonicGorilla.AttackStyle.RANGED);
							}
							else if (mp != null)
							{
								WorldArea lastPlayerArea = mp.getLastWorldArea();
								if (lastPlayerArea != null && recentBoulders.stream()
									.anyMatch(x -> x.distanceTo(lastPlayerArea) == 0))
								{
									// A boulder started falling on the gorillas target,
									// so we assume it was the gorilla who shot it
									onGorillaAttack(gorilla, SRLDemonicGorilla.AttackStyle.BOULDER);
								}
								else if (!mp.getRecentHitsplats().isEmpty())
								{
									// It wasn't any of the three other attacks,
									// but the player took damage, so we assume
									// it's a melee attack
									onGorillaAttack(gorilla, SRLDemonicGorilla.AttackStyle.MELEE);
								}
							}
						}

						// The next attack tick is always delayed if the
						// gorilla switched prayer
						gorilla.setNextAttackTick(tickCounter + SRLDemonicGorilla.ATTACK_RATE);
						gorilla.setChangedPrayerThisTick(true);
					}
				}
			}

			if (gorilla.getDisabledMeleeMovementForTicks() > 0)
			{
				gorilla.setDisabledMeleeMovementForTicks(gorilla.getDisabledMeleeMovementForTicks() - 1);
			}
			else if (gorilla.isInitiatedCombat() &&
				gorilla.getNpc().getInteracting() != null &&
				!gorilla.isChangedAttackStyleThisTick() &&
				gorilla.getNextPosibleAttackStyles().size() >= 2 &&
				gorilla.getNextPosibleAttackStyles().stream()
					.anyMatch(x -> x == SRLDemonicGorilla.AttackStyle.MELEE))
			{
				// If melee is a possibility, we can check if the gorilla
				// is or isn't moving toward the player to determine if
				// it is actually attempting to melee or not.
				// We only run this check if the gorilla is in combat
				// because otherwise it attempts to travel to melee
				// distance before attacking its target.

				if (mp != null && mp.getLastWorldArea() != null && gorilla.getLastWorldArea() != null)
				{
					WorldArea predictedNewArea = gorilla.getLastWorldArea().calculateNextTravellingPoint(
						client, mp.getLastWorldArea(), true, x ->
						{
							// Gorillas can't normally walk through other gorillas
							// or other players
							final WorldArea area1 = new WorldArea(x, 1, 1);
							return gorillas.values().stream().noneMatch(y ->
							{
								if (y == gorilla)
								{
									return false;
								}
								final WorldArea area2 =
									y.getNpc().getIndex() < gorilla.getNpc().getIndex() ?
										y.getNpc().getWorldArea() : y.getLastWorldArea();
								return area2 != null && area1.intersectsWith(area2);
							}) && memorizedPlayers.values().stream().noneMatch(y ->
							{
								final WorldArea area2 = y.getLastWorldArea();
								return area2 != null && area1.intersectsWith(area2);
							});

							// There is a special case where if a player walked through
							// a gorilla, or a player walked through another player,
							// the tiles that were walked through becomes
							// walkable, but I didn't feel like it's necessary to handle
							// that special case as it should rarely happen.
						});
					if (predictedNewArea != null)
					{
						int distance = gorilla.getNpc().getWorldArea().distanceTo(mp.getLastWorldArea());
						WorldPoint predictedMovement = predictedNewArea.toWorldPoint();
						if (distance <= SRLDemonicGorilla.MAX_ATTACK_RANGE && mp.getLastWorldArea().hasLineOfSightTo(client, gorilla.getLastWorldArea()))
						{
							if (predictedMovement.distanceTo(gorilla.getLastWorldArea().toWorldPoint()) != 0)
							{
								if (predictedMovement.distanceTo(gorilla.getNpc().getWorldLocation()) == 0)
								{
									gorilla.setNextPosibleAttackStyles(gorilla
										.getNextPosibleAttackStyles()
										.stream()
										.filter(x -> x == SRLDemonicGorilla.AttackStyle.MELEE)
										.collect(Collectors.toList()));
										activatePrayer(Prayer.PROTECT_FROM_MELEE, true);
								}
								else
								{
									gorilla.setNextPosibleAttackStyles(gorilla
										.getNextPosibleAttackStyles()
										.stream()
										.filter(x -> x != SRLDemonicGorilla.AttackStyle.MELEE)
										.collect(Collectors.toList()));

									//activatePrayer(x);

								}
							}
							else if (tickCounter >= gorilla.getNextAttackTick() &&
								gorilla.getRecentProjectileId() == -1 &&
								recentBoulders.stream().noneMatch(x -> x.distanceTo(mp.getLastWorldArea()) == 0)) {
								gorilla.setNextPosibleAttackStyles(gorilla
										.getNextPosibleAttackStyles()
										.stream()
										.filter(x -> x == SRLDemonicGorilla.AttackStyle.MELEE)
										.collect(Collectors.toList()));
								activatePrayer(Prayer.PROTECT_FROM_MELEE, true);

							}
						}
					}
				}
			}

			if (gorilla.isTakenDamageRecently())
			{
				gorilla.setInitiatedCombat(true);
			}

			if (gorilla.getOverheadIcon() != gorilla.getLastTickOverheadIcon())
			{
				if (gorilla.isChangedAttackStyleLastTick() ||
					gorilla.isChangedAttackStyleThisTick())
				{
					// Apparently if it changes attack style and changes
					// prayer on the same tick or 1 tick apart, it won't
					// be able to move for the next 2 ticks if it attempts
					// to melee
					gorilla.setDisabledMeleeMovementForTicks(2);
				}
				else
				{
					// If it didn't change attack style lately,
					// it's only for the next 1 tick
					gorilla.setDisabledMeleeMovementForTicks(1);
				}
			}
			gorilla.setLastTickAnimation(gorilla.getNpc().getAnimation());
			gorilla.setLastWorldArea(gorilla.getNpc().getWorldArea());
			gorilla.setLastTickInteracting(gorilla.getNpc().getInteracting());
			gorilla.setTakenDamageRecently(false);
			gorilla.setChangedPrayerThisTick(false);
			gorilla.setChangedAttackStyleLastTick(gorilla.isChangedAttackStyleThisTick());
			gorilla.setChangedAttackStyleThisTick(false);
			gorilla.setLastTickOverheadIcon(gorilla.getOverheadIcon());
			gorilla.setRecentProjectileId(-1);
		}
	}

	@Subscribe
	private void onProjectileSpawned(ProjectileSpawned event)
	{
		final Projectile projectile = event.getProjectile();
		final int projectileId = projectile.getId();

		if (!DEMONIC_PROJECTILES.contains(projectileId))
		{
			return;
		}

		final WorldPoint loc = WorldPoint.fromLocal(client, projectile.getX1(), projectile.getY1(), client.getPlane());

		if (projectileId == ProjectileID.DEMONIC_GORILLA_BOULDER)
		{
			recentBoulders.add(loc);
		}
		else
		{
			for (SRLDemonicGorilla gorilla : gorillas.values())
			{
				if (gorilla.getNpc().getWorldLocation().distanceTo(loc) == 0)
				{
					gorilla.setRecentProjectileId(projectile.getId());
				}
			}
		}
	}

	private void checkPendingAttacks()
	{
		Iterator<SRLPendingGorillaAttack> it = pendingAttacks.iterator();
		int tickCounter = client.getTickCount();
		while (it.hasNext())
		{
			SRLPendingGorillaAttack attack = it.next();
			if (tickCounter >= attack.getFinishesOnTick())
			{
				boolean shouldDecreaseCounter = false;
				SRLDemonicGorilla gorilla = attack.getAttacker();
				SRLMemorizedPlayer target = memorizedPlayers.get(attack.getTarget());
				if (target == null)
				{
					// Player went out of memory, so assume the hit was a 0
					shouldDecreaseCounter = true;
				}
				else if (target.getRecentHitsplats().isEmpty())
				{
					// No hitsplats was applied. This may happen in some cases
					// where the player was out of memory while the
					// projectile was travelling. So we assume the hit was a 0.
					shouldDecreaseCounter = true;
				}
				else if (target.getRecentHitsplats().stream()
					.anyMatch(x -> x.getHitsplatType() == Hitsplat.HitsplatType.BLOCK_ME))
				{
					// A blue hitsplat appeared, so we assume the gorilla hit a 0
					shouldDecreaseCounter = true;
				}

				if (shouldDecreaseCounter)
				{
					gorilla.setAttacksUntilSwitch(gorilla.getAttacksUntilSwitch() - 1);
					checkGorillaAttackStyleSwitch(gorilla);
				}

				it.remove();
			}
		}
	}

	private void updatePlayers()
	{
		for (SRLMemorizedPlayer mp : memorizedPlayers.values())
		{
			mp.setLastWorldArea(mp.getPlayer().getWorldArea());
			mp.getRecentHitsplats().clear();
		}
	}

	@Subscribe
	private void onHitsplatApplied(HitsplatApplied event)
	{
		if (gorillas.isEmpty())
		{
			return;
		}

		if (event.getActor() instanceof Player)
		{
			Player player = (Player) event.getActor();
			SRLMemorizedPlayer mp = memorizedPlayers.get(player);
			if (mp != null)
			{
				mp.getRecentHitsplats().add(event.getHitsplat());
			}
		}
		else if (event.getActor() instanceof NPC)
		{
			SRLDemonicGorilla gorilla = gorillas.get(event.getActor());
			Hitsplat.HitsplatType hitsplatType = event.getHitsplat().getHitsplatType();
			if (gorilla != null && (hitsplatType == Hitsplat.HitsplatType.BLOCK_ME ||
				hitsplatType == Hitsplat.HitsplatType.DAMAGE_ME))
			{
				gorilla.setTakenDamageRecently(true);
			}
		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event)
	{
		GameState gs = event.getGameState();
		if (gs == GameState.LOGGING_IN ||
			gs == GameState.CONNECTION_LOST ||
			gs == GameState.HOPPING)
		{
			reset();
		}
	}

	@Subscribe
	private void onPlayerSpawned(PlayerSpawned event)
	{
		if (gorillas.isEmpty())
		{
			return;
		}

		Player player = event.getPlayer();
		memorizedPlayers.put(player, new SRLMemorizedPlayer(player));
	}

	@Subscribe
	private void onPlayerDespawned(PlayerDespawned event)
	{
		if (gorillas.isEmpty())
		{
			return;
		}

		memorizedPlayers.remove(event.getPlayer());
	}

	@Subscribe
	private void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		if (isNpcGorilla(npc.getId()))
		{
			if (gorillas.isEmpty())
			{
				// Players are not kept track of when there are no gorillas in
				// memory, so we need to add the players that were already in memory.
				resetPlayers();
			}

			gorillas.put(npc, new SRLDemonicGorilla(npc));
		}
	}

	@Subscribe
	private void onNpcDespawned(NpcDespawned event)
	{
		if (gorillas.remove(event.getNpc()) != null && gorillas.isEmpty())
		{
			clear();
		}
	}

	@Subscribe
	private void onGameTick(GameTick event) {
		if (gorillas.isEmpty())
			return;
		if (config.useAssist()) {
			if (config.usePrayerBoost()) {
				int itemId = client.getLocalPlayer().getPlayerComposition().getEquipmentId(KitType.WEAPON);
				if (itemId == config.meleeWeapon() && !client.isPrayerActive(Prayer.PIETY) && BOOST == null && client.getLocalPlayer().getInteracting() != null) {
					if (client.getLocalPlayer().getInteracting().getName().contains("Demonic")) {
						tickDelay = r.nextInt(3 - 1 + 1) + 2;
						BOOST = Prayer.PIETY;
					}
				}
				if (itemId == config.rangeWeapon() && !client.isPrayerActive(Prayer.EAGLE_EYE) && BOOST == null && client.getLocalPlayer().getInteracting() != null) {
					if (client.getLocalPlayer().getInteracting().getName().contains("Demonic")) {
						tickDelay = r.nextInt(3 - 1 + 1) + 2;
						BOOST = Prayer.EAGLE_EYE;
					}
				}
			}
		}
		if (tickDelay > 0) {
			sendMsg("Tick delay: " + tickDelay);
			tickDelay--;
		}
		checkGorillaAttacks();
		checkPendingAttacks();
		updatePlayers();
		recentBoulders.clear();
		if (config.useAssist()) {
			if (config.usePrayerBoost() && BOOST != null && tickDelay == 0) {
				activatePrayer(BOOST, false);
				BOOST = null;
			}
		}
	}
}
