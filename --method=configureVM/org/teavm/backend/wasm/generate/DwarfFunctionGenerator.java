package org.teavm.backend.wasm.generate;

import org.teavm.backend.wasm.debug.info.VariableType;
import org.teavm.backend.wasm.dwarf.DwarfAbbreviation;
import org.teavm.backend.wasm.dwarf.DwarfInfoWriter;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.common.binary.Blob;
import org.teavm.model.MethodDescriptor;

public class DwarfFunctionGenerator {
   private DwarfClassGenerator classGen;
   private DwarfInfoWriter writer;
   private DwarfStrings strings;
   private DwarfAbbreviation methodAbbrev;
   private DwarfAbbreviation parameterAbbrev;
   private DwarfAbbreviation variableAbbrev;

   public DwarfFunctionGenerator(DwarfClassGenerator classGen, DwarfInfoWriter writer, DwarfStrings strings) {
      this.classGen = classGen;
      this.writer = writer;
      this.strings = strings;
   }

   public void prepareContent(DwarfClassGenerator.Subprogram subprogram) {
      if (subprogram.function != null && subprogram.function.getName() != null) {
         MethodDescriptor descriptor = subprogram.descriptor;
         if (descriptor != null) {
            WasmFunction function = subprogram.function;
            int offset = subprogram.isStatic ? 0 : 1;
            int count = Math.min(function.getLocalVariables().size() - offset, descriptor.parameterCount());

            for (int i = 0; i < count; i++) {
               WasmLocal local = function.getLocalVariables().get(i + offset);
               if (local.getName() != null) {
                  this.classGen.getTypePtr(descriptor.parameterType(i));
               }
            }

            for (int i = count + offset; i < function.getLocalVariables().size(); i++) {
               WasmLocal local = function.getLocalVariables().get(i);
               if (local.getName() != null && local.getJavaType() != null) {
                  this.classGen.getTypePtr(local.getJavaType());
               }
            }
         }
      }
   }

   public void writeContent(DwarfClassGenerator.Subprogram subprogram) {
      if (subprogram.function.getName() != null) {
         this.writer.tag(this.getMethodAbbrev());
         this.writer.writeInt(this.strings.stringRef(subprogram.name));
         this.writer.writeInt(subprogram.startOffset);
         this.writer.writeInt(subprogram.endOffset);
         this.writeLocals(subprogram);
         this.writer.emptyTag();
      }
   }

   private void writeLocals(DwarfClassGenerator.Subprogram subprogram) {
      MethodDescriptor descriptor = subprogram.descriptor;
      if (descriptor != null) {
         WasmFunction function = subprogram.function;
         int offset = subprogram.isStatic ? 0 : 1;
         int count = Math.min(function.getLocalVariables().size() - offset, descriptor.parameterCount());

         for (int i = 0; i < count; i++) {
            WasmLocal local = function.getLocalVariables().get(i + offset);
            if (local.getName() != null) {
               this.writer.tag(this.getParameterAbbrev());
               this.writer.writeInt(this.strings.stringRef(local.getName()));
               this.writer.ref(this.classGen.getTypePtr(descriptor.parameterType(i)), Blob::writeInt);
               Blob operations = new Blob();
               operations.writeByte(237).writeByte(0).writeLEB(i + 1);
               if (local.getJavaType() == VariableType.OBJECT) {
                  operations.writeByte(159);
               }

               this.writer.writeLEB(operations.size());
               operations.newReader(this.writer::write).readRemaining();
            }
         }

         for (int i = count + offset; i < function.getLocalVariables().size(); i++) {
            WasmLocal local = function.getLocalVariables().get(i);
            if (local.getName() != null && local.getJavaType() != null) {
               this.writer.tag(this.getVariableAbbrev());
               this.writer.writeInt(this.strings.stringRef(local.getName()));
               this.writer.ref(this.classGen.getTypePtr(local.getJavaType()), Blob::writeInt);
               Blob operations = new Blob();
               operations.writeByte(237).writeByte(0).writeLEB(i + 1);
               if (local.getJavaType() == VariableType.OBJECT) {
                  operations.writeByte(159);
               }

               this.writer.writeLEB(operations.size());
               operations.newReader(this.writer::write).readRemaining();
            }
         }
      }
   }

   private DwarfAbbreviation getMethodAbbrev() {
      if (this.methodAbbrev == null) {
         this.methodAbbrev = this.writer.abbreviation(46, true, data -> {
            data.writeLEB(3).writeLEB(14);
            data.writeLEB(17).writeLEB(1);
            data.writeLEB(18).writeLEB(1);
         });
      }

      return this.methodAbbrev;
   }

   private DwarfAbbreviation getParameterAbbrev() {
      if (this.parameterAbbrev == null) {
         this.parameterAbbrev = this.writer.abbreviation(5, false, data -> {
            data.writeLEB(3).writeLEB(14);
            data.writeLEB(73).writeLEB(19);
            data.writeLEB(2).writeLEB(24);
         });
      }

      return this.parameterAbbrev;
   }

   private DwarfAbbreviation getVariableAbbrev() {
      if (this.variableAbbrev == null) {
         this.variableAbbrev = this.writer.abbreviation(52, false, data -> {
            data.writeLEB(3).writeLEB(14);
            data.writeLEB(73).writeLEB(19);
            data.writeLEB(2).writeLEB(24);
         });
      }

      return this.variableAbbrev;
   }
}
