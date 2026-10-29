package org.teavm.backend.wasm.disasm;

import org.teavm.backend.wasm.parser.CodeListener;
import org.teavm.backend.wasm.parser.CodeSectionListener;
import org.teavm.backend.wasm.parser.WasmHollowFunctionType;
import org.teavm.backend.wasm.parser.WasmHollowType;

public class DisassemblyCodeSectionListener extends BaseDisassemblyListener implements CodeSectionListener {
   private int currentFunctionId;
   private int currentFunctionParameterCount;
   private DisassemblyCodeListener codeListener;
   private WasmHollowFunctionType[] functionTypes;
   private int[] functionTypeRefs;

   public DisassemblyCodeSectionListener(DisassemblyWriter writer, NameProvider nameProvider) {
      super(writer, nameProvider);
      this.codeListener = new DisassemblyCodeListener(writer, nameProvider);
   }

   public void setFunctionTypes(WasmHollowFunctionType[] functionTypes) {
      this.functionTypes = functionTypes;
   }

   public void setFunctionTypeRefs(int[] functionTypeRefs) {
      this.functionTypeRefs = functionTypeRefs;
   }

   @Override
   public boolean functionStart(int index, int size) {
      this.currentFunctionId = index;
      this.writer.address().write("(func ");
      this.writer.startLinkTarget("f" + index).write("(; " + index + " ;)");
      String name = this.nameProvider.function(index);
      if (name != null) {
         this.writer.write(" $").write(name);
      }

      this.writer.endLinkTarget();
      int typeRef = this.functionTypeRefs[index];
      this.writer.write(" (type ");
      this.writeTypeRef(typeRef);
      this.writer.write(")");
      this.writer.indent().eol();
      WasmHollowFunctionType type = typeRef < this.functionTypes.length ? this.functionTypes[typeRef] : null;
      if (type != null) {
         this.currentFunctionParameterCount = type.parameterTypes.length;

         for (int i = 0; i < type.parameterTypes.length; i++) {
            this.writer.write("(param ");
            this.writer.startLinkTarget("l" + index + "." + i).write(" (; " + i + " ;)");
            String paramName = this.nameProvider.local(index, i);
            if (paramName != null) {
               this.writer.write(" $").write(paramName);
            }

            this.writer.endLinkTarget();
            this.writer.write(" ");
            this.writeType(type.parameterTypes[i]);
            this.writer.write(")").eol();
         }

         for (int i = 0; i < type.returnTypes.length; i++) {
            this.writer.write("(result ");
            this.writeType(type.returnTypes[i]);
            this.writer.write(")").eol();
         }
      } else {
         this.currentFunctionParameterCount = 0;
      }

      return true;
   }

   @Override
   public void localsStart(int count) {
      this.writer.address().write("(; locals " + count + " ;)").eol();
   }

   @Override
   public void local(int start, int count, WasmHollowType type) {
      this.writer.address();

      for (int i = 0; i < count; i++) {
         this.writer.write("(local ");
         int id = i + start + this.currentFunctionParameterCount;
         this.writer.startLinkTarget("l" + this.currentFunctionId + "." + id).write("(; " + id + " ;)");
         String name = this.nameProvider.local(this.currentFunctionId, id);
         if (name != null) {
            this.writer.write(" ").write("$").write(name);
         }

         this.writer.endLinkTarget().write(" ");
         this.writeType(type);
         this.writer.write(")").eol();
      }
   }

   @Override
   public CodeListener code() {
      this.codeListener.setCurrentFunctionId(this.currentFunctionId);
      this.codeListener.reset();
      return this.codeListener;
   }

   @Override
   public void functionEnd() {
      this.writer.outdent().write(")").eol();
   }

   @Override
   public void sectionEnd() {
      this.writer.outdent().write(")").eol();
   }
}
