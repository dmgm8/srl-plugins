package net.runelite.client.plugins.srlnightmare;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.ui.overlay.infobox.Timer;
import net.runelite.client.util.Text;
import org.pf4j.Extension;

@Extension
@PluginDescriptor(
	name = "SRL Nightmare of Ashihama",
	enabledByDefault = false,
	description = "Auto prayer for Nightmare of Ashihama",
	tags = {"bosses", "combat", "nm", "overlay", "nightmare", "pve", "pvm", "ashihama"}
)

@Slf4j
@Singleton
public class NightmarePlugin extends Plugin
{
	@Inject
	private Client client;
	@Inject
	private ItemManager itemManager;

	// Nightmare's attack animations
	private static final int NIGHTMARE_HUSK_SPAWN = 8565;
	private static final int NIGHTMARE_PARASITE_TOSS = 8606;
	private static final int NIGHTMARE_CHARGE = 8609;
	private static final int NIGHTMARE_MELEE_ATTACK = 8594;
	private static final int NIGHTMARE_RANGE_ATTACK = 8596;
	private static final int NIGHTMARE_MAGIC_ATTACK = 8595;
	private static final int NIGHTMARE_PRE_MUSHROOM = 37738;
	private static final int NIGHTMARE_MUSHROOM = 37739;
	private static final int NIGHTMARE_SHADOW = 1767;   // graphics object

	private static final LocalPoint MIDDLE_LOCATION = new LocalPoint(6208, 8128);
	private static final Set<LocalPoint> PHOSANIS_MIDDLE_LOCATIONS = ImmutableSet.of(new LocalPoint(6208, 7104), new LocalPoint(7232, 7104));
	private static final List<Integer> INACTIVE_TOTEMS = Arrays.asList(9435, 9438, 9441, 9444);
	private static final List<Integer> ACTIVE_TOTEMS = Arrays.asList(9436, 9439, 9442, 9445);
	@Getter(AccessLevel.PACKAGE)
	private final Map<Integer, MemorizedTotem> totems = new HashMap<>();
	@Getter(AccessLevel.PACKAGE)
	private final Map<LocalPoint, GameObject> spores = new HashMap<>();
	@Getter(AccessLevel.PACKAGE)
	private final Map<Polygon, Player> huskTarget = new HashMap<>();
	@Getter(AccessLevel.PACKAGE)
	private final Map<Integer, Player> parasiteTargets = new HashMap<>();
	@Getter(AccessLevel.PACKAGE)
	private final Set<GraphicsObject> shadows = new HashSet<>();
	private final Set<NPC> husks = new HashSet<>();
	private final Set<NPC> parasites = new HashSet<>();
	private final Set<NPC> sleepwalkers = new HashSet<>();

	@Nullable
	@Getter(AccessLevel.PACKAGE)
	private NightmareAttack pendingNightmareAttack;
	@Nullable
	@Getter(AccessLevel.PACKAGE)
	private NPC nm;
	@Getter(AccessLevel.PACKAGE)
	private boolean inFight;

	private boolean cursed;

	@Getter(AccessLevel.PACKAGE)
	private int ticksUntilNextAttack = 0;

	@Getter(AccessLevel.PACKAGE)
	private boolean parasite;
	@Getter(AccessLevel.PACKAGE)
	private int ticksUntilParasite = 0;

	@Getter(AccessLevel.PACKAGE)
	private boolean nightmareCharging = false;

	@Getter(AccessLevel.PACKAGE)
	private boolean shadowsSpawning = false;
	@Getter(AccessLevel.PACKAGE)
	private int shadowsTicks;

	private int totemsAlive = 0;

	@Getter(AccessLevel.PACKAGE)
	@Setter
	private boolean flash = false;

	public NightmarePlugin()
	{
		inFight = false;
	}

	@Override
	protected void startUp()
	{
		reset();
	}

	@Override
	protected void shutDown()
	{
		reset();
	}

	private void reset()
	{
		inFight = false;
		nm = null;
		pendingNightmareAttack = null;
		nightmareCharging = false;
		shadowsSpawning = false;
		cursed = false;
		flash = false;
		parasite = false;
		ticksUntilNextAttack = 0;
		ticksUntilParasite = 0;
		shadowsTicks = 0;
		totemsAlive = 0;
		totems.clear();
		spores.clear();
		shadows.clear();
		husks.clear();
		huskTarget.clear();
		parasites.clear();
		parasiteTargets.clear();
		sleepwalkers.clear();
	}

	@Subscribe
	private void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (!inFight)
		{
			return;
		}

		GameObject gameObj = event.getGameObject();
		int id = gameObj.getId();
		if (id == NIGHTMARE_MUSHROOM || id == NIGHTMARE_PRE_MUSHROOM)
		{
			spores.put(gameObj.getLocalLocation(), gameObj);
		}
	}

	@Subscribe
	private void onGameObjectDespawned(GameObjectDespawned event)
	{
		if (!inFight)
		{
			return;
		}

		GameObject gameObj = event.getGameObject();
		int id = gameObj.getId();
		if (id == NIGHTMARE_MUSHROOM || id == NIGHTMARE_PRE_MUSHROOM)
		{
			spores.remove(gameObj.getLocalLocation());
		}
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		if (!inFight)
		{
			return;
		}

		if (event.getGraphicsObject().getId() == NIGHTMARE_SHADOW)
		{
			shadows.add(event.getGraphicsObject());
			shadowsSpawning = true;
			shadowsTicks = 5;
			ticksUntilNextAttack = 5;
		}
	}

	@Subscribe
	private void onProjectileSpawned(ProjectileSpawned event)
	{
		if (!inFight)
		{
			return;
		}

		var projectile = event.getProjectile();
		Player targetPlayer;
		switch (projectile.getId())
		{
			case 1770:
				targetPlayer = (Player) projectile.getInteracting();
				parasiteTargets.putIfAbsent(targetPlayer.getPlayerId(), targetPlayer);
				break;
			case 1781:
				targetPlayer = (Player) projectile.getInteracting();
				huskTarget.putIfAbsent(targetPlayer.getCanvasTilePoly(), targetPlayer);
				break;
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (!(actor instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) actor;

		// this will trigger once when the fight begins
		if (nm == null && npc.getName() != null && (npc.getName().equalsIgnoreCase("The Nightmare") || npc.getName().equalsIgnoreCase("Phosani's Nightmare")))
		{
			//reset everything
			reset();
			nm = npc;
			inFight = true;
		}
		
		if (!inFight || !npc.equals(nm))
		{
			return;
		}
		
		int animationId = npc.getAnimation();
		
		if (animationId == NIGHTMARE_MAGIC_ATTACK)
		{
			ticksUntilNextAttack = 7;
			pendingNightmareAttack = cursed ? NightmareAttack.CURSE_MAGIC : NightmareAttack.MAGIC;
		}
		else if (animationId == NIGHTMARE_MELEE_ATTACK)
		{
			ticksUntilNextAttack = 7;
			pendingNightmareAttack = cursed ? NightmareAttack.CURSE_MELEE : NightmareAttack.MELEE;
		}
		else if (animationId == NIGHTMARE_RANGE_ATTACK)
		{
			ticksUntilNextAttack = 7;
			pendingNightmareAttack = cursed ? NightmareAttack.CURSE_RANGE : NightmareAttack.RANGE;
		}
		// check if phosanis because the middle locations may be used in the others charge locations
		else if (animationId == NIGHTMARE_CHARGE && ((!isPhosanis(npc.getId()) && !MIDDLE_LOCATION.equals(npc.getLocalLocation())) || (isPhosanis(npc.getId()) && !PHOSANIS_MIDDLE_LOCATIONS.contains(npc.getLocalLocation()))))
		{
			nightmareCharging = true;
			ticksUntilNextAttack = 5;
		}

		if (nightmareCharging && animationId != -1 && animationId != NIGHTMARE_CHARGE)
		{
			nightmareCharging = false;
		}

		if (animationId != NIGHTMARE_HUSK_SPAWN && !huskTarget.isEmpty())
		{
			huskTarget.clear();
		}

		if (animationId == NIGHTMARE_PARASITE_TOSS)
		{
			ticksUntilParasite = 27;

		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		final NPC npc = event.getNpc();

		//if npc is in the totems map, update its phase
		if (totems.containsKey(npc.getIndex()))
		{
			totems.get(npc.getIndex()).updateCurrentPhase(npc.getId());
		}
		if (INACTIVE_TOTEMS.contains(npc.getId()))
		{
			//else if the totem is not in the totem array and it is an inactive totem, add it to the totem map.
			totems.putIfAbsent(npc.getIndex(), new MemorizedTotem(npc));
			totemsAlive++;
		}
		if (ACTIVE_TOTEMS.contains(npc.getId()))
		{
			totemsAlive--;
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		final NPC npc = event.getNpc();

		if (npc.getName() != null && npc.getName().equalsIgnoreCase("parasite"))
		{
			parasites.add(npc);
		}

		if (npc.getName() != null && npc.getName().equalsIgnoreCase("husk"))
		{
			husks.add(npc);
		}

		if (npc.getName() != null && npc.getName().equalsIgnoreCase("sleepwalker"))
		{
			sleepwalkers.add(npc);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		final NPC npc = event.getNpc();

		if (npc.getName() != null && npc.getName().equalsIgnoreCase("sleepwalker"))
		{
			sleepwalkers.remove(npc);
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() instanceof NPC && event.getActor().getName() != null)
		{
			final NPC npc = (NPC)event.getActor();

			if (npc.getName() != null && npc.getName().equalsIgnoreCase("parasite"))
			{
				parasites.remove(npc);
			}

			if (npc.getName() != null && npc.getName().equalsIgnoreCase("husk"))
			{
				husks.remove(npc);
			}
		}
	}

	@Subscribe
	private void onChatMessage(ChatMessage event)
	{
		if (!inFight || nm == null || event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		if (event.getMessage().contains("The Nightmare has impregnated you with a deadly parasite!"))
		{
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer != null)
			{
				parasiteTargets.putIfAbsent(localPlayer.getPlayerId(), localPlayer);
			}

			flash = true;
			parasite = true;
			ticksUntilParasite = 22;
		}

		if (event.getMessage().toLowerCase().contains("the parasite within you has been weakened") || event.getMessage().toLowerCase().contains("the parasite bursts out of you, fully grown"))
		{
			parasite = false;
		}

		if (event.getMessage().toLowerCase().contains("the nightmare has cursed you, shuffling your prayers!"))
		{
			cursed = true;
		}

		if (event.getMessage().toLowerCase().contains("you feel the effects of the nightmare's curse wear off."))
		{
			cursed = false;
		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event)
	{
		GameState gamestate = event.getGameState();

		//if loading happens while inFight, the user has left the area (either via death or teleporting).
		if (gamestate == GameState.LOADING && inFight)
		{
			reset();
		}
	}

	@Subscribe
	private void onGameTick(final GameTick event)
	{
		if (!inFight || nm == null)
		{
			return;
		}

		//the fight has ended and everything should be reset
		if (nm.getId() == 378 || nm.getId() == 377)
		{
			reset();
		}

		ticksUntilNextAttack--;

		if (ticksUntilParasite > 0)
		{
			ticksUntilParasite--;
			if (ticksUntilParasite == 0)
			{
				parasiteTargets.clear();
			}
		}

		if (pendingNightmareAttack != null && ticksUntilNextAttack <= 3)
		{
			pendingNightmareAttack = null;
		}

		if (shadowsTicks > 0)
		{
			shadowsTicks--;
			if (shadowsTicks == 0)
			{
				shadowsSpawning = false;
				shadows.clear();
			}
		}
		Prayer prayer = getPendingNightmareAttack().getPrayer();
		if (ticksUntilNextAttack == 1) {
			activatePrayer(prayer);
		}
	}

	private boolean isPhosanis(int id)
	{
		return (id >= 9416 && id <= 9424) || (id >= 11153 && id <= 11155);
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
