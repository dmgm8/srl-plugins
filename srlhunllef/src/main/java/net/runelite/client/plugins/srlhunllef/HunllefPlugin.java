/*
 * Copyright (c) 2020, dutta64 <https://github.com/dutta64>
 * Copyright (c) 2019, kThisIsCvpv <https://github.com/kThisIsCvpv>
 * Copyright (c) 2019, ganom <https://github.com/Ganom>
 * Copyright (c) 2019, kyle <https://github.com/Kyleeld>
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

package net.runelite.client.plugins.srlhunllef;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.events.*;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.srlhunllef.entity.Hunllef;
import org.pf4j.Extension;

@Extension
@PluginDescriptor(
	name = "SRL Hunllef",
	enabledByDefault = false,
	description = "A plugin for Hunllef",
	tags = {"gauntlet"}
)
@Singleton
public class HunllefPlugin extends Plugin
{
	public static final int ONEHAND_SLASH_AXE_ANIMATION = 395;
	public static final int ONEHAND_CRUSH_PICKAXE_ANIMATION = 400;
	public static final int ONEHAND_CRUSH_AXE_ANIMATION = 401;
	public static final int UNARMED_PUNCH_ANIMATION = 422;
	public static final int UNARMED_KICK_ANIMATION = 423;
	public static final int BOW_ATTACK_ANIMATION = 426;
	public static final int ONEHAND_STAB_HALBERD_ANIMATION = 428;
	public static final int ONEHAND_SLASH_HALBERD_ANIMATION = 440;
	public static final int ONEHAND_SLASH_SWORD_ANIMATION = 390;
	public static final int ONEHAND_STAB_SWORD_ANIMATION = 386;
	public static final int HIGH_LEVEL_MAGIC_ATTACK = 1167;
	public static final int HUNLEFF_TORNADO = 8418;

	private static final Set<Integer> MELEE_ANIM_IDS = Set.of(
		ONEHAND_STAB_SWORD_ANIMATION, ONEHAND_SLASH_SWORD_ANIMATION,
		ONEHAND_SLASH_AXE_ANIMATION, ONEHAND_CRUSH_PICKAXE_ANIMATION,
		ONEHAND_CRUSH_AXE_ANIMATION, UNARMED_PUNCH_ANIMATION,
		UNARMED_KICK_ANIMATION, ONEHAND_STAB_HALBERD_ANIMATION,
		ONEHAND_SLASH_HALBERD_ANIMATION
	);

	private static final Set<Integer> ATTACK_ANIM_IDS = new HashSet<>();

	static
	{
		ATTACK_ANIM_IDS.addAll(MELEE_ANIM_IDS);
		ATTACK_ANIM_IDS.add(BOW_ATTACK_ANIMATION);
		ATTACK_ANIM_IDS.add(HIGH_LEVEL_MAGIC_ATTACK);
	}

	private static final Set<Integer> PROJECTILE_MAGIC_IDS = Set.of(
		ProjectileID.HUNLLEF_MAGE_ATTACK, ProjectileID.HUNLLEF_CORRUPTED_MAGE_ATTACK
	);

	private static final Set<Integer> PROJECTILE_RANGE_IDS = Set.of(
		ProjectileID.HUNLLEF_RANGE_ATTACK, ProjectileID.HUNLLEF_CORRUPTED_RANGE_ATTACK
	);

	private static final Set<Integer> PROJECTILE_PRAYER_IDS = Set.of(
		ProjectileID.HUNLLEF_PRAYER_ATTACK, ProjectileID.HUNLLEF_CORRUPTED_PRAYER_ATTACK
	);

	final List<Integer> EGNIOL_POTION = List.of(ItemID.EGNIOL_POTION_1, ItemID.EGNIOL_POTION_2, ItemID.EGNIOL_POTION_3, ItemID.EGNIOL_POTION_4);

	private static final Set<Integer> PROJECTILE_IDS = new HashSet<>();

	static
	{
		PROJECTILE_IDS.addAll(PROJECTILE_MAGIC_IDS);
		PROJECTILE_IDS.addAll(PROJECTILE_RANGE_IDS);
		PROJECTILE_IDS.addAll(PROJECTILE_PRAYER_IDS);
	}

	private static final Set<Integer> HUNLLEF_IDS = Set.of(
		NpcID.CRYSTALLINE_HUNLLEF, NpcID.CRYSTALLINE_HUNLLEF_9022,
		NpcID.CRYSTALLINE_HUNLLEF_9023, NpcID.CRYSTALLINE_HUNLLEF_9024,
		NpcID.CORRUPTED_HUNLLEF, NpcID.CORRUPTED_HUNLLEF_9036,
		NpcID.CORRUPTED_HUNLLEF_9037, NpcID.CORRUPTED_HUNLLEF_9038
	);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Getter
	private Hunllef hunllef;

	@Inject
	private HunllefConfig config;

	private boolean inGauntlet;
	private boolean inHunllef;

	@Provides
	HunllefConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HunllefConfig.class);
	}


	@Override
	protected void startUp()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invoke(this::pluginEnabled);
		}
	}

	@Override
	protected void shutDown()
	{
		inGauntlet = false;
		inHunllef = false;
		hunllef = null;
	}

	@Subscribe
	private void onVarbitChanged(final VarbitChanged event)
	{
		if (isHunllefVarbitSet())
		{
			if (!inHunllef)
			{
				initHunllef();
			}
		}
		else if (isGauntletVarbitSet())
		{
			if (!inGauntlet)
			{
				initGauntlet();
			}
		}
		else
		{
			if (inGauntlet || inHunllef)
			{
				shutDown();
			}
		}
	}

	@Getter
	private Item[] equippedItems;

	@Subscribe
	public void onItemContainerChanged(final ItemContainerChanged event) {
		if (event.getItemContainer() == client.getItemContainer(InventoryID.EQUIPMENT)) {
			equippedItems = event.getItemContainer().getItems();
			return;
		}
	}

	public java.util.List<WidgetItem> getItems(Collection<Integer> ids) {
		Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
		List<WidgetItem> matchedItems = new ArrayList<>();

		if (inventoryWidget != null) {
			Collection<WidgetItem> items = inventoryWidget.getWidgetItems();
			for (WidgetItem item : items) {
				if (ids.contains(item.getId())) {
					matchedItems.add(item);
				}
			}
			return matchedItems;
		}
		return null;
	}

	@Subscribe
	private void onGameTick(final GameTick event)
	{
		if (isHunllefVarbitSet()) {
			final Hunllef hunllef = getHunllef();

			if (hunllef == null) {
				return;
			}

			final NPC npc = hunllef.getNpc();

			if (npc == null || npc.isDead()) {
				return;
			}

			hunllef.decrementTicksUntilNextAttack();
			final Hunllef.AttackPhase phase = hunllef.getAttackPhase();

			if (!client.isPrayerActive(phase.getPrayer())) {
				activatePrayer(phase.getPrayer());
				return;
			}

			if (config.usePrayerTick()) {
				if (hunllef.getTicksUntilNextAttack() > 1) {
					if (client.isPrayerActive(phase.getPrayer())) {
						deactivatePrayer(phase.getPrayer());
						return;
					}
				}
			}

			int itemId = client.getLocalPlayer().getPlayerComposition().getEquipmentId(KitType.WEAPON);

			if (!getItems(EGNIOL_POTION).isEmpty()) {
				if (config.useOffensive()) {
					if (itemId == ItemID.CORRUPTED_BOW_PERFECTED) {
						activatePrayer(config.rangeOffensive().getPrayer());
					}
					if (itemId == ItemID.CORRUPTED_BOW_ATTUNED) {
						activatePrayer(config.rangeOffensive().getPrayer());
					}
					if (itemId == ItemID.CORRUPTED_BOW_BASIC) {
						activatePrayer(config.rangeOffensive().getPrayer());
					}

					if (itemId == ItemID.CORRUPTED_STAFF_PERFECTED) {
						activatePrayer(config.mageOffensive().getPrayer());
					}
					if (itemId == ItemID.CORRUPTED_STAFF_ATTUNED) {
						activatePrayer(config.mageOffensive().getPrayer());
					}
					if (itemId == ItemID.CORRUPTED_STAFF_BASIC) {
						activatePrayer(config.mageOffensive().getPrayer());
					}

					if (itemId == ItemID.CORRUPTED_HALBERD_PERFECTED) {
						activatePrayer(config.meleeOffensive().getPrayer());
					}
					if (itemId == ItemID.CORRUPTED_HALBERD_ATTUNED) {
						activatePrayer(config.meleeOffensive().getPrayer());
					}
					if (itemId == ItemID.CORRUPTED_HALBERD_BASIC) {
						activatePrayer(config.meleeOffensive().getPrayer());
					}

					if (itemId == ItemID.CRYSTAL_BOW_PERFECTED) {
						activatePrayer(config.rangeOffensive().getPrayer());
					}
					if (itemId == ItemID.CRYSTAL_BOW_ATTUNED) {
						activatePrayer(config.rangeOffensive().getPrayer());
					}
					if (itemId == ItemID.CRYSTAL_BOW_BASIC) {
						activatePrayer(config.rangeOffensive().getPrayer());
					}

					if (itemId == ItemID.CRYSTAL_STAFF_PERFECTED) {
						activatePrayer(config.mageOffensive().getPrayer());
					}
					if (itemId == ItemID.CRYSTAL_STAFF_ATTUNED) {
						activatePrayer(config.mageOffensive().getPrayer());
					}
					if (itemId == ItemID.CRYSTAL_STAFF_BASIC) {
						activatePrayer(config.mageOffensive().getPrayer());
					}

					if (itemId == ItemID.CRYSTAL_HALBERD_PERFECTED) {
						activatePrayer(config.meleeOffensive().getPrayer());
					}
					if (itemId == ItemID.CRYSTAL_HALBERD_ATTUNED) {
						activatePrayer(config.meleeOffensive().getPrayer());
					}
					if (itemId == ItemID.CRYSTAL_HALBERD_BASIC) {
						activatePrayer(config.meleeOffensive().getPrayer());
					}
				}
			}
		}
	}

	@Subscribe
	private void onGameStateChanged(final GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOADING:
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				shutDown();
				break;
		}
	}

	@Subscribe
	private void onNpcSpawned(final NpcSpawned event)
	{
		final NPC npc = event.getNpc();

		final int id = npc.getId();

		if (HUNLLEF_IDS.contains(id))
		{
			hunllef = new Hunllef(npc);
		}

	}

	@Subscribe
	private void onNpcDespawned(final NpcDespawned event)
	{
		final NPC npc = event.getNpc();

		final int id = npc.getId();

		if (HUNLLEF_IDS.contains(id))
		{
			hunllef = null;
		}
	}

	@Subscribe
	private void onProjectileSpawned(final ProjectileSpawned event)
	{
		if (hunllef == null)
		{
			return;
		}

		final Projectile projectile = event.getProjectile();

		final int id = projectile.getId();

		if (!PROJECTILE_IDS.contains(id))
		{
			return;
		}

		hunllef.updateAttackCount();

	}

	@Subscribe
	private void onActorDeath(final ActorDeath event)
	{
		if (event.getActor() != client.getLocalPlayer())
		{
			return;
		}
	}

	@Subscribe
	private void onAnimationChanged(final AnimationChanged event)
	{
		if (!isHunllefVarbitSet() || hunllef == null)
		{
			return;
		}

		final Actor actor = event.getActor();

		final int animationId = actor.getAnimation();

		if (actor instanceof Player)
		{
			if (!ATTACK_ANIM_IDS.contains(animationId))
			{
				return;
			}
		}
		else if (actor instanceof NPC)
		{
			if (animationId == HUNLEFF_TORNADO)
			{
				hunllef.updateAttackCount();
			}
		}
	}

	private boolean isAttackAnimationValid(final int animationId)
	{
		final HeadIcon headIcon = hunllef.getNpc().getComposition().getOverheadIcon();

		if (headIcon == null)
		{
			return true;
		}

		switch (headIcon)
		{
			case MELEE:
				if (MELEE_ANIM_IDS.contains(animationId))
				{
					return false;
				}
				break;
			case RANGED:
				if (animationId == BOW_ATTACK_ANIMATION)
				{
					return false;
				}
				break;
			case MAGIC:
				if (animationId == HIGH_LEVEL_MAGIC_ATTACK)
				{
					return false;
				}
				break;
		}

		return true;
	}

	private void pluginEnabled()
	{
		if (isGauntletVarbitSet())
		{
			initGauntlet();
		}

		if (isHunllefVarbitSet())
		{
			initHunllef();
		}
	}

	private void initGauntlet()
	{
		inGauntlet = true;
	}

	private void initHunllef()
	{
		inHunllef = true;
	}

	private boolean isGauntletVarbitSet()
	{
		return client.getVarbitValue(9178) == 1;
	}

	private boolean isHunllefVarbitSet()
	{
		return client.getVarbitValue(9177) == 1;
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

	public void deactivatePrayer(Prayer prayer)
	{
		if (prayer == null)
		{
			return;
		}

		if (!client.isPrayerActive(prayer))
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

		entry = new MenuEntry("Deactivate", prayer_widget.getName(), 1, MenuAction.CC_OP.getId(), prayer_widget.getItemId(), prayer_widget.getId(), false);
		click();
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
