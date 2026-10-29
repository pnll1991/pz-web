package org.teavm.debugging.information;

import org.teavm.common.RecordArray;

class LayerSourceLocationIterator {
   private DebugInformation debugInformation;
   private int lineIndex;
   private int fileIndex;
   private GeneratedLocation location;
   private int fileId = -1;
   private int line = -1;
   private boolean endReached;
   private DebugInformation.Layer layer;

   LayerSourceLocationIterator(DebugInformation debugInformation, DebugInformation.Layer layer) {
      this.debugInformation = debugInformation;
      this.layer = layer;
      this.read();
   }

   public boolean isEndReached() {
      return this.endReached;
   }

   private void read() {
      if (this.fileIndex < this.layer.fileMapping.size() && this.lineIndex < this.layer.lineMapping.size()) {
         RecordArray.Record fileRecord = this.layer.fileMapping.get(this.fileIndex);
         RecordArray.Record lineRecord = this.layer.lineMapping.get(this.lineIndex);
         GeneratedLocation fileLoc = DebugInformation.key(fileRecord);
         GeneratedLocation lineLoc = DebugInformation.key(lineRecord);
         int cmp = fileLoc.compareTo(lineLoc);
         if (cmp < 0) {
            this.nextFileRecord();
         } else if (cmp > 0) {
            this.nextLineRecord();
         } else {
            this.nextFileRecord();
            this.nextLineRecord();
         }
      } else if (this.fileIndex < this.layer.fileMapping.size()) {
         this.nextFileRecord();
      } else if (this.lineIndex < this.layer.lineMapping.size()) {
         this.nextLineRecord();
      } else {
         if (this.endReached) {
            throw new IllegalStateException("End already reached");
         }

         this.endReached = true;
      }
   }

   private void nextFileRecord() {
      RecordArray.Record record = this.layer.fileMapping.get(this.fileIndex++);
      this.location = DebugInformation.key(record);
      this.fileId = record.get(2);
   }

   private void nextLineRecord() {
      RecordArray.Record record = this.layer.lineMapping.get(this.lineIndex++);
      this.location = DebugInformation.key(record);
      this.line = record.get(2);
   }

   public void next() {
      if (this.isEndReached()) {
         throw new IllegalStateException("End already reached");
      }

      this.read();
   }

   public GeneratedLocation getLocation() {
      if (this.isEndReached()) {
         throw new IllegalStateException("End already reached");
      } else {
         return this.location;
      }
   }

   public int getFileNameId() {
      if (this.isEndReached()) {
         throw new IllegalStateException("End already reached");
      } else {
         return this.fileId;
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
         return this.line;
      }
   }
}
