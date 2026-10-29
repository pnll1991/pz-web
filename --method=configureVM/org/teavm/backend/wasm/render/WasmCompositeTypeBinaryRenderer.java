package org.teavm.backend.wasm.render;

import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmCompositeTypeVisitor;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStorageType;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;

public class WasmCompositeTypeBinaryRenderer implements WasmCompositeTypeVisitor {
   private WasmModule module;
   private WasmBinaryWriter section;

   public WasmCompositeTypeBinaryRenderer(WasmModule module, WasmBinaryWriter section) {
      this.module = module;
      this.section = section;
   }

   @Override
   public void visit(WasmStructure type) {
      this.section.writeByte(80);
      if (type.getSupertype() != null) {
         this.section.writeLEB(1);
         this.section.writeLEB(this.module.types.indexOf(type.getSupertype()));
      } else {
         this.section.writeLEB(0);
      }

      this.section.writeByte(95);
      this.section.writeLEB(type.getFields().size());

      for (WasmField fieldType : type.getFields()) {
         this.writeStorageType(fieldType.getType());
         this.section.writeLEB(fieldType.isImmutable() ? 0 : 1);
      }
   }

   @Override
   public void visit(WasmArray type) {
      this.section.writeByte(94);
      this.writeStorageType(type.getElementType());
      this.section.writeLEB(type.isImmutable() ? 0 : 1);
   }

   @Override
   public void visit(WasmFunctionType type) {
      if (!type.isFinal() || !type.getSupertypes().isEmpty()) {
         this.section.writeByte(80);
         this.section.writeLEB(type.getSupertypes().size());

         for (WasmFunctionType supertype : type.getSupertypes()) {
            this.section.writeLEB(this.module.types.indexOf(supertype));
         }
      }

      this.section.writeByte(96);
      this.section.writeLEB(type.getParameterTypes().size());

      for (WasmType inputType : type.getParameterTypes()) {
         this.section.writeType(inputType, this.module);
      }

      this.section.writeLEB(type.getReturnTypes().size());

      for (WasmType outputType : type.getReturnTypes()) {
         this.section.writeType(outputType, this.module);
      }
   }

   private void writeStorageType(WasmStorageType storageType) {
      if (storageType instanceof WasmStorageType.Packed) {
         switch (((WasmStorageType.Packed)storageType).type) {
            case INT8:
               this.section.writeByte(120);
               break;
            case INT16:
               this.section.writeByte(119);
         }
      } else {
         this.section.writeType(storageType.asUnpackedType(), this.module);
      }
   }
}
