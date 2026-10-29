package org.teavm.debugging.information;

class LayerIterator {
   private int currentLayer;
   private MethodIterator[] methodIterators;
   private GeneratedLocation currentLocation;

   LayerIterator(DebugInformation debugInformation) {
      this.methodIterators = new MethodIterator[debugInformation.layerCount()];

      for (int i = 0; i < this.methodIterators.length; i++) {
         this.methodIterators[i] = new MethodIterator(debugInformation.layers[i]);
      }

      if (!this.isEndReached()) {
         this.nextImpl();
      }
   }

   public boolean isEndReached() {
      return this.currentLayer == 0 && this.methodIterators[this.currentLayer].isEndReached();
   }

   public int getLayer() {
      if (this.isEndReached()) {
         throw new IllegalStateException();
      } else {
         return this.currentLayer;
      }
   }

   public GeneratedLocation getLocation() {
      if (this.isEndReached()) {
         throw new IllegalStateException();
      } else {
         return this.currentLocation;
      }
   }

   public void next() {
      if (this.isEndReached()) {
         throw new IllegalStateException();
      }

      int previous = this.currentLayer;

      do {
         this.nextImpl();
      } while (!this.isEndReached() && previous == this.currentLayer);
   }

   private void nextImpl() {
      if (this.isEndReached()) {
         throw new IllegalStateException("End already reached");
      }

      if (this.canEnterLayer()) {
         this.currentLayer++;

         while (this.canEnterLayer()) {
            this.currentLayer++;
         }

         this.currentLocation = this.methodIterators[this.currentLayer].getLocation();
         this.methodIterators[this.currentLayer].next();
      } else if (this.canExitLayer()) {
         GeneratedLocation limitLocation = this.methodIterators[this.currentLayer].getLocation();
         this.moveToNextNotEmpty();

         while (true) {
            this.currentLayer--;
            this.moveToLocation(limitLocation);
            if (this.currentLayer <= 0
               || this.methodIterators[this.currentLayer].getMethodId() >= 0
               || !this.methodIterators[this.currentLayer].getLocation().equals(limitLocation)) {
               this.currentLocation = limitLocation;
               break;
            }

            this.moveToNextNotEmpty();
         }
      } else {
         this.currentLocation = this.methodIterators[this.currentLayer].getLocation();
         this.methodIterators[this.currentLayer].next();
      }
   }

   private boolean canEnterLayer() {
      return this.currentLayer < this.methodIterators.length - 1
         && !this.methodIterators[this.currentLayer + 1].isEndReached()
         && !this.methodIterators[this.currentLayer].isEndReached()
         && this.isLocationLessOrEqual(this.currentLayer + 1, this.currentLayer);
   }

   private boolean canExitLayer() {
      return this.currentLayer == 0 ? false : this.methodIterators[this.currentLayer].getMethodId() < 0;
   }

   private void moveToNextNotEmpty() {
      while (!this.methodIterators[this.currentLayer].isEndReached() && this.methodIterators[this.currentLayer].getMethodId() < 0) {
         this.methodIterators[this.currentLayer].next();
      }
   }

   private void moveToLocation(GeneratedLocation location) {
      while (true) {
         GeneratedLocation nextLocation = this.methodIterators[this.currentLayer].getLocation();
         if (nextLocation.compareTo(location) >= 0) {
            return;
         }

         this.currentLocation = location;
         this.methodIterators[this.currentLayer].next();
      }
   }

   private boolean isLocationLessOrEqual(int firstLayer, int secondLayer) {
      return this.methodIterators[firstLayer].getLocation().compareTo(this.methodIterators[secondLayer].getLocation()) <= 0;
   }
}
