package net.runelite.client.plugins.srlzulrah;

import com.google.common.base.Preconditions;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.srlzulrah.rotationutils.RotationType;
import net.runelite.client.plugins.srlzulrah.rotationutils.ZulrahData;
import net.runelite.client.plugins.srlzulrah.rotationutils.ZulrahPhase;
import org.pf4j.Extension;

@Extension
@PluginDescriptor(
   name = "SRL Zulrah",
   description = "A plugin for Zulrah",
   tags = {"zulrah", "zul", "andra", "snakeling"},
   enabledByDefault = false
)

@Slf4j
public class SRLZulrahPlugin extends Plugin  {

   @Inject
   private Client client;

   private NPC zulrahNpc = null;
   private int stage = 0;
   private int phaseTicks = -1;
   private int attackTicks = -1;
   private RotationType currentRotation = null;
   private List<RotationType> potentialRotations = new ArrayList();
   private final Map<LocalPoint, Integer> projectilesMap = new HashMap();
   private static boolean flipStandLocation = false;
   private static boolean flipPhasePrayer = false;
   private static boolean zulrahReset = false;
   private final BiConsumer<RotationType, RotationType> phaseTicksHandler = (current, potential) -> {
      if (zulrahReset) {
         this.phaseTicks = 38;
      } else {
         ZulrahPhase p = current != null ? this.getCurrentPhase(current) : this.getCurrentPhase(potential);
         Preconditions.checkNotNull(p, "Attempted to set phase ticks but current Zulrah phase was somehow null. Stage: " + this.stage);
         this.phaseTicks = p.getAttributes().getPhaseTicks();
      }

   };

   protected void startUp() {
   }

   protected void shutDown() {
      this.reset();
   }

   private void reset() {
      this.zulrahNpc = null;
      this.stage = 0;
      this.phaseTicks = -1;
      this.attackTicks = -1;
      this.currentRotation = null;
      this.potentialRotations.clear();
      this.projectilesMap.clear();
      flipStandLocation = false;
      flipPhasePrayer = false;
      zulrahReset = false;
      log.debug("Zulrah Reset!");
   }

   @Subscribe
   private void onGameTick(GameTick event) {
      if (this.client.getGameState() == GameState.LOGGED_IN && this.zulrahNpc != null) {
         if (this.attackTicks >= 0) {
            --this.attackTicks;
         }

         if (this.phaseTicks >= 0) {
            --this.phaseTicks;
         }

         if (this.projectilesMap.size() > 0) {
            this.projectilesMap.values().removeIf((v) -> v <= 0);
            this.projectilesMap.replaceAll((k, v) -> v - 1);
         }


         Prayer prayer = null;
         Iterator var3 = getZulrahData().iterator();

         while(var3.hasNext()) {
            ZulrahData data = (ZulrahData)var3.next();
            if (data.getCurrentPhasePrayer().isPresent()) {
               prayer = (Prayer)data.getCurrentPhasePrayer().get();
            }
         }
         if (prayer != null && !((ZulrahData) var3.next()).isJad()) {
            activatePrayer(prayer);
         }
      }
   }

   @Subscribe
   private void onAnimationChanged(AnimationChanged event) {
      if (event.getActor() instanceof NPC) {
         NPC npc = (NPC)event.getActor();
         if (npc.getName() == null || npc.getName().equalsIgnoreCase("zulrah")) {
            switch(npc.getAnimation()) {
            case 5069:
               this.attackTicks = 4;
               if (this.getCurrentPhase(this.currentRotation).getZulrahNpc().isJad()) {
                  flipPhasePrayer = !flipPhasePrayer;
               }
               break;
            case 5071:
               this.zulrahNpc = npc;
               this.potentialRotations = RotationType.findPotentialRotations(npc, this.stage);
               this.phaseTicksHandler.accept(this.currentRotation, (RotationType)this.potentialRotations.get(0));
               log.debug("New Zulrah Encounter Started");
               break;
            case 5072:
               if (zulrahReset) {
                  zulrahReset = false;
               }

               if (this.currentRotation != null && this.isLastPhase(this.currentRotation)) {
                  this.stage = -1;
                  this.currentRotation = null;
                  this.potentialRotations.clear();
                  flipStandLocation = false;
                  flipPhasePrayer = false;
                  zulrahReset = true;
                  log.debug("Resetting Zulrah");
               }
               break;
            case 5073:
               ++this.stage;
               if (this.currentRotation == null) {
                  this.potentialRotations = RotationType.findPotentialRotations(npc, this.stage);
                  this.currentRotation = this.potentialRotations.size() == 1 ? (RotationType)this.potentialRotations.get(0) : null;
               }

               this.phaseTicksHandler.accept(this.currentRotation, (RotationType)this.potentialRotations.get(0));
               break;
            case 5804:
               this.reset();
               break;
            case 5806:
            case 5807:
               this.attackTicks = 8;
               flipStandLocation = !flipStandLocation;
            }

         }
      }
   }

   @Subscribe
   private void onProjectileSpawned(ProjectileSpawned event) {
      if (event.getProjectile().getId() == 1046 && this.getCurrentPhase(this.currentRotation).getZulrahNpc().isJad()) { //Mage attack and Jad
         activatePrayer(Prayer.PROTECT_FROM_MISSILES);
      }

      if (event.getProjectile().getId() == 1044 && this.getCurrentPhase(this.currentRotation).getZulrahNpc().isJad()) { //Range attack and Jad
         activatePrayer(Prayer.PROTECT_FROM_MAGIC);
      }
   }

   @Subscribe
   private void onProjectileMoved(ProjectileMoved event) {
      if (this.zulrahNpc != null) {
         Projectile p = event.getProjectile();
         switch(p.getId()) {
         case 1045:
         case 1047:
            this.projectilesMap.put(event.getPosition(), p.getRemainingCycles() / 30);
         default:
         }
      }
   }

   @Subscribe
   private void onGameStateChanged(GameStateChanged event) {
      if (this.zulrahNpc != null) {
         switch(event.getGameState()) {
         case LOADING:
         case CONNECTION_LOST:
         case HOPPING:
            this.reset();
         default:
         }
      }
   }

   @Nullable
   private ZulrahPhase getCurrentPhase(RotationType type) {
      return this.stage >= type.getZulrahPhases().size() ? null : (ZulrahPhase)type.getZulrahPhases().get(this.stage);
   }

   @Nullable
   private ZulrahPhase getNextPhase(RotationType type) {
      return this.isLastPhase(type) ? null : (ZulrahPhase)type.getZulrahPhases().get(this.stage + 1);
   }

   private boolean isLastPhase(RotationType type) {
      return this.stage == type.getZulrahPhases().size() - 1;
   }

   public Set<ZulrahData> getZulrahData() {
      Set<ZulrahData> zulrahDataSet = new LinkedHashSet();
      if (this.currentRotation == null) {
         this.potentialRotations.forEach((type) -> {
            zulrahDataSet.add(new ZulrahData(this.getCurrentPhase(type), this.getNextPhase(type)));
         });
      } else {
         zulrahDataSet.add(new ZulrahData(this.getCurrentPhase(this.currentRotation), this.getNextPhase(this.currentRotation)));
      }

      return (Set)(zulrahDataSet.size() > 0 ? zulrahDataSet : Collections.emptySet());
   }

   public NPC getZulrahNpc() {
      return this.zulrahNpc;
   }

   public int getPhaseTicks() {
      return this.phaseTicks;
   }

   public int getAttackTicks() {
      return this.attackTicks;
   }

   public RotationType getCurrentRotation() {
      return this.currentRotation;
   }

   public Map<LocalPoint, Integer> getProjectilesMap() {
      return this.projectilesMap;
   }

   public static boolean isFlipStandLocation() {
      return flipStandLocation;
   }

   public static boolean isFlipPhasePrayer() {
      return flipPhasePrayer;
   }

   public static boolean isZulrahReset() {
      return zulrahReset;
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
      sendMsg("Activating prayer " + prayer.toString());
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
   private static ChatMessageManager chatMessageManager;

   public static void sendMsg(String message) {
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
