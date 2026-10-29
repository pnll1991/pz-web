package org.teavm.debugging.information;

public class SourceLocationIterator {
   private DebugInformation debugInformation;
   private LayerIterator layerIterator;
   private SourceLocationIterator.LayerInfo[] layerSourceIterators;
   private boolean endReached;
   private int currentLayer;
   private GeneratedLocation lastLocation;

   public SourceLocationIterator(DebugInformation debugInformation) {
      this.debugInformation = debugInformation;
      this.layerIterator = new LayerIterator(debugInformation);
      this.layerSourceIterators = new SourceLocationIterator.LayerInfo[debugInformation.layerCount()];

      for (int i = 0; i < this.layerSourceIterators.length; i++) {
         this.layerSourceIterators[i] = new SourceLocationIterator.LayerInfo(new LayerSourceLocationIterator(debugInformation, debugInformation.layers[i]));
      }

      if (!this.layerIterator.isEndReached()) {
         this.currentLayer = this.layerIterator.getLayer();
         this.layerIterator.next();
      } else {
         this.currentLayer = 0;
      }

      this.lastLocation = this.layerSourceIterators[this.currentLayer].lastLocation;
      if (this.lastLocation == null) {
         this.endReached = true;
      }
   }

   public GeneratedLocation getLocation() {
      return this.lastLocation;
   }

   public boolean isEndReached() {
      return this.endReached;
   }

   public void next() {
      if (this.endReached) {
         throw new IllegalStateException();
      }

      SourceLocationIterator.LayerInfo currentIterator = this.layerSourceIterators[this.currentLayer];
      if (this.currentLayer == 0 && currentIterator.iterator.isEndReached()) {
         this.endReached = true;
      } else {
         if (currentIterator.iterator.isEndReached()
            || !this.layerIterator.isEndReached() && currentIterator.iterator.getLocation().compareTo(this.layerIterator.getLocation()) >= 0) {
            this.currentLayer = this.layerIterator.getLayer();
            this.lastLocation = this.layerIterator.getLocation();
            this.layerIterator.next();
            currentIterator = this.layerSourceIterators[this.currentLayer];

            while (!currentIterator.iterator.isEndReached() && currentIterator.iterator.getLocation().compareTo(this.lastLocation) <= 0) {
               currentIterator.next();
            }
         } else {
            currentIterator.next();
            this.lastLocation = currentIterator.lastLocation;
         }
      }
   }

   public int getFileNameId() {
      if (this.isEndReached()) {
         throw new IllegalStateException("End already reached");
      } else {
         return this.layerSourceIterators[this.currentLayer].lastFileId;
      }
   }

   public String getFileName() {
      int fileId = this.getFileNameId();
      return fileId >= 0 ? this.debugInformation.getFileName(fileId) : null;
   }

   public int getLine() {
      if (this.isEndReached()) {
         throw new IllegalStateException("End already reached");
      } else {
         return this.layerSourceIterators[this.currentLayer].lastLine;
      }
   }

   static class LayerInfo {
      LayerSourceLocationIterator iterator;
      int lastFileId;
      int lastLine;
      GeneratedLocation lastLocation;

      LayerInfo(LayerSourceLocationIterator iterator) {
         this.iterator = iterator;
         if (!iterator.isEndReached()) {
            this.next();
         }
      }

      void next() {
         this.lastFileId = this.iterator.getFileNameId();
         this.lastLine = this.iterator.getLine();
         this.lastLocation = this.iterator.getLocation();
         this.iterator.next();
      }
   }
}
