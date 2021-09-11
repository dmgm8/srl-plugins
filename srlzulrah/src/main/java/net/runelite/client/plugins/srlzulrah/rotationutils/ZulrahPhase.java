package net.runelite.client.plugins.srlzulrah.rotationutils;

public final class ZulrahPhase {
   private final ZulrahNpc zulrahNpc;
   private final ZulrahAttributes attributes;

   public ZulrahPhase(ZulrahNpc zulrahNpc, ZulrahAttributes attributes) {
      this.zulrahNpc = zulrahNpc;
      this.attributes = attributes;
   }

   public ZulrahNpc getZulrahNpc() {
      return this.zulrahNpc;
   }

   public ZulrahAttributes getAttributes() {
      return this.attributes;
   }

   public boolean equals(Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof ZulrahPhase)) {
         return false;
      } else {
         ZulrahPhase other = (ZulrahPhase)o;
         Object this$zulrahNpc = this.getZulrahNpc();
         Object other$zulrahNpc = other.getZulrahNpc();
         if (this$zulrahNpc == null) {
            if (other$zulrahNpc != null) {
               return false;
            }
         } else if (!this$zulrahNpc.equals(other$zulrahNpc)) {
            return false;
         }

         Object this$attributes = this.getAttributes();
         Object other$attributes = other.getAttributes();
         if (this$attributes == null) {
            if (other$attributes != null) {
               return false;
            }
         } else if (!this$attributes.equals(other$attributes)) {
            return false;
         }

         return true;
      }
   }
}
