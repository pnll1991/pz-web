package org.teavm.debugging.information;

class MethodIterator {
   private DebugInformation.Layer layer;
   private int index;

   MethodIterator(DebugInformation.Layer layer) {
      this.layer = layer;
   }

   public boolean isEndReached() {
      return this.index >= this.layer.methodMapping.size();
   }

   public GeneratedLocation getLocation() {
      if (this.isEndReached()) {
         throw new IllegalStateException("End already reached");
      } else {
         return DebugInformation.key(this.layer.methodMapping.get(this.index));
      }
   }

   public int getMethodId() {
      if (this.isEndReached()) {
         throw new IllegalStateException("End already reached");
      } else {
         return this.layer.methodMapping.get(this.index).get(2);
      }
   }

   public void next() {
      if (this.isEndReached()) {
         throw new IllegalStateException("End already reached");
      }

      this.index++;
   }
}
