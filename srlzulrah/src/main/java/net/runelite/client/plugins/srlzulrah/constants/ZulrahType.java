package net.runelite.client.plugins.srlzulrah.constants;

import java.awt.Color;

import net.runelite.api.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum ZulrahType {
   RANGE("Range", 2042, Skill.RANGED, Color.YELLOW),
   MELEE("Melee", 2043, Skill.ATTACK, Color.RED),
   MAGIC("Magic", 2044, Skill.MAGIC, Color.CYAN);

   private static final Logger log = LoggerFactory.getLogger(ZulrahType.class);
   private final String name;
   private final int npcId;
   private final Skill skill;
   private final Color color;

   public static ZulrahType valueOf(int npcId) {
      switch(npcId) {
      case 2042:
         return RANGE;
      case 2043:
         return MELEE;
      case 2044:
         return MAGIC;
      default:
         return null;
      }
   }

   public Color getColorWithAlpha(int alpha) {
      return new Color(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), alpha);
   }

   public String toString() {
      return this.name;
   }

   private ZulrahType(String name, int npcId, Skill skill, Color color) {
      this.name = name;
      this.npcId = npcId;
      this.skill = skill;
      this.color = color;
   }

   public String getName() {
      return this.name;
   }

   public int getNpcId() {
      return this.npcId;
   }

   public Skill getSkill() {
      return this.skill;
   }

   public Color getColor() {
      return this.color;
   }
}
