package org.teavm.backend.wasm.debug;

import org.teavm.backend.wasm.debug.info.FieldType;
import org.teavm.model.PrimitiveType;

public class DebugClassLayoutBuilder extends DebugSectionBuilder implements DebugClassLayout {
   private DebugClasses classes;
   private DebugStrings strings;
   private int currentIndex;
   private int currentAddress;
   private DebugClassLayoutBuilder.ClassPhase phase = DebugClassLayoutBuilder.ClassPhase.NO_CLASS;
   private int lastFieldOffset;

   public DebugClassLayoutBuilder(DebugClasses classes, DebugStrings strings) {
      super("teavm_cll");
      this.classes = classes;
      this.strings = strings;
   }

   @Override
   public void startClass(String name, int parent, int address, int size) {
      this.blob.writeByte(parent >= 0 ? 1 : 0);
      this.blob.writeLEB(this.classes.classPtr(name));
      if (parent >= 0) {
         this.blob.writeSLEB(this.currentIndex - parent);
      }

      this.writeAddress(address);
      this.blob.writeLEB(size);
      this.lastFieldOffset = 0;
      this.phase = DebugClassLayoutBuilder.ClassPhase.STATIC_FIELDS;
   }

   @Override
   public void instanceField(String name, int offset, FieldType type) {
      if (this.phase == DebugClassLayoutBuilder.ClassPhase.STATIC_FIELDS) {
         this.blob.writeByte(1);
         this.lastFieldOffset = 0;
         this.phase = DebugClassLayoutBuilder.ClassPhase.INSTANCE_FIELDS;
      }

      this.writeFieldType(type);
      this.blob.writeLEB(this.strings.stringPtr(name));
      this.blob.writeSLEB(offset - this.lastFieldOffset);
      this.lastFieldOffset = offset;
   }

   @Override
   public void staticField(String name, int offset, FieldType type) {
      this.writeFieldType(type);
      this.blob.writeLEB(this.strings.stringPtr(name));
      this.blob.writeSLEB(offset - this.lastFieldOffset);
      this.lastFieldOffset = offset;
   }

   private void writeFieldType(FieldType type) {
      switch (type) {
         case BOOLEAN:
            this.blob.writeByte(2);
            break;
         case BYTE:
            this.blob.writeByte(3);
            break;
         case SHORT:
            this.blob.writeByte(4);
            break;
         case CHAR:
            this.blob.writeByte(5);
            break;
         case INT:
            this.blob.writeByte(6);
            break;
         case LONG:
            this.blob.writeByte(7);
            break;
         case FLOAT:
            this.blob.writeByte(8);
            break;
         case DOUBLE:
            this.blob.writeByte(9);
            break;
         case OBJECT:
            this.blob.writeByte(10);
            break;
         case ADDRESS:
            this.blob.writeByte(11);
            break;
         case UNDEFINED:
            this.blob.writeByte(12);
      }
   }

   @Override
   public void endClass() {
      if (this.phase == DebugClassLayoutBuilder.ClassPhase.STATIC_FIELDS) {
         this.blob.writeByte(0);
      }

      if (this.phase == DebugClassLayoutBuilder.ClassPhase.INSTANCE_FIELDS) {
         this.blob.writeByte(1);
      }

      this.currentIndex++;
   }

   @Override
   public void writeInterface(String name, int address) {
      this.blob.writeByte(2);
      this.blob.writeLEB(this.classes.classPtr(name));
      this.writeAddress(address);
      this.currentIndex++;
   }

   @Override
   public void writePrimitive(PrimitiveType type, int address) {
      switch (type) {
         case BOOLEAN:
            this.blob.writeLEB(4);
            break;
         case BYTE:
            this.blob.writeLEB(5);
            break;
         case SHORT:
            this.blob.writeLEB(6);
            break;
         case CHARACTER:
            this.blob.writeLEB(7);
            break;
         case INTEGER:
            this.blob.writeLEB(8);
            break;
         case LONG:
            this.blob.writeLEB(9);
            break;
         case FLOAT:
            this.blob.writeLEB(10);
            break;
         case DOUBLE:
            this.blob.writeLEB(11);
      }

      this.writeAddress(address);
      this.currentIndex++;
   }

   @Override
   public void writeArray(int itemType, int address) {
      this.blob.writeByte(3);
      this.blob.writeSLEB(this.currentIndex - itemType);
      this.writeAddress(address);
      this.currentIndex++;
   }

   @Override
   public void writeUnknown(int address) {
      this.blob.writeByte(12);
      this.writeAddress(address);
      this.currentIndex++;
   }

   private void writeAddress(int address) {
      this.blob.writeSLEB(address - this.currentAddress);
      this.currentAddress = address;
   }

   private enum ClassPhase {
      NO_CLASS,
      STATIC_FIELDS,
      INSTANCE_FIELDS;
   }
}
