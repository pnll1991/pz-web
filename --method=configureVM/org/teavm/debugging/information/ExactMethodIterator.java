package org.teavm.debugging.information;

import org.teavm.common.RecordArray;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public class ExactMethodIterator {
   private DebugInformation debugInformation;
   private DebugInformation.Layer layer;
   private GeneratedLocation location;
   private int classIndex;
   private int methodIndex;
   private int classId = -1;
   private int methodId = -1;

   ExactMethodIterator(DebugInformation debugInformation, DebugInformation.Layer layer) {
      this.debugInformation = debugInformation;
      this.layer = layer;
      if (!this.isEndReached()) {
         this.read();
      }
   }

   public boolean isEndReached() {
      return this.methodIndex >= this.layer.methodMapping.size() && this.classIndex >= this.layer.classMapping.size();
   }

   private void read() {
      if (this.classIndex < this.layer.classMapping.size() && this.methodIndex < this.layer.methodMapping.size()) {
         RecordArray.Record classRecord = this.layer.classMapping.get(this.classIndex);
         RecordArray.Record methodRecord = this.layer.methodMapping.get(this.methodIndex);
         GeneratedLocation classLoc = DebugInformation.key(classRecord);
         GeneratedLocation methodLoc = DebugInformation.key(methodRecord);
         int cmp = classLoc.compareTo(methodLoc);
         if (cmp < 0) {
            this.nextClassRecord();
         } else if (cmp > 0) {
            this.nextMethodRecord();
         } else {
            this.nextClassRecord();
            this.nextMethodRecord();
         }
      } else if (this.classIndex < this.layer.classMapping.size()) {
         this.nextClassRecord();
      } else {
         if (this.methodIndex >= this.layer.methodMapping.size()) {
            throw new IllegalStateException("End already reached");
         }

         this.nextMethodRecord();
      }
   }

   private void nextClassRecord() {
      RecordArray.Record record = this.layer.classMapping.get(this.classIndex++);
      this.classId = record.get(2);
      this.location = DebugInformation.key(record);
   }

   private void nextMethodRecord() {
      RecordArray.Record record = this.layer.methodMapping.get(this.methodIndex++);
      this.methodId = record.get(2);
      this.location = DebugInformation.key(record);
   }

   public void next() {
      if (this.isEndReached()) {
         throw new IllegalStateException("End already reached");
      }

      this.read();
   }

   public int getClassNameId() {
      if (this.isEndReached()) {
         throw new IllegalStateException("End already reached");
      } else {
         return this.classId;
      }
   }

   public String getClassName() {
      int classId = this.getClassNameId();
      return classId >= 0 ? this.debugInformation.getClassName(classId) : null;
   }

   public int getMethodId() {
      if (this.isEndReached()) {
         throw new IllegalStateException("End already reached");
      } else {
         return this.methodId;
      }
   }

   public MethodDescriptor getMethod() {
      int methodId = this.getMethodId();
      return methodId >= 0 ? this.debugInformation.getMethod(methodId) : null;
   }

   public int getExactMethodId() {
      return this.classId >= 0 && this.methodId >= 0 ? this.debugInformation.getExactMethodId(this.classId, this.methodId) : -1;
   }

   public MethodReference getExactMethod() {
      int methodId = this.getExactMethodId();
      return methodId >= 0 ? this.debugInformation.getExactMethod(this.getExactMethodId()) : null;
   }

   public GeneratedLocation getLocation() {
      return this.location;
   }
}
