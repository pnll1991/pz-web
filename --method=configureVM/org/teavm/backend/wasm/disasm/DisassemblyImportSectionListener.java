package org.teavm.backend.wasm.disasm;

import org.teavm.backend.wasm.parser.ImportSectionListener;
import org.teavm.backend.wasm.parser.WasmHollowFunctionType;
import org.teavm.backend.wasm.parser.WasmHollowType;

public class DisassemblyImportSectionListener extends BaseDisassemblyListener implements ImportSectionListener {
   private WasmHollowFunctionType[] functionTypes;
   private String currentModule;
   private String currentName;
   private int functionIndex;
   private int globalIndex;
   private int memoryIndex;

   public DisassemblyImportSectionListener(DisassemblyWriter writer, NameProvider nameProvider, WasmHollowFunctionType[] functionTypes) {
      super(writer, nameProvider);
      this.functionTypes = functionTypes;
   }

   public int functionCount() {
      return this.functionIndex;
   }

   public int globalCount() {
      return this.globalIndex;
   }

   @Override
   public void startEntry(String module, String name) {
      this.currentModule = module;
      this.currentName = name;
   }

   @Override
   public void function(int typeIndex) {
      this.writer.address().write("(import \"").write(this.currentModule).write("\" \"").write(this.currentName).write("\" ");
      this.writer.write("(func ");
      this.writer.startLinkTarget("f" + this.functionIndex).write("(; " + this.functionIndex + " ;)");
      String name = this.nameProvider.function(this.functionIndex);
      if (name != null) {
         this.writer.write(" $").write(name);
      }

      this.writer.endLinkTarget();
      this.writer.write(" (type ");
      this.writeTypeRef(typeIndex);
      this.writer.write(")");
      this.writer.indent().eol();
      WasmHollowFunctionType type = typeIndex < this.functionTypes.length ? this.functionTypes[typeIndex] : null;
      if (type != null) {
         for (int i = 0; i < type.parameterTypes.length; i++) {
            this.writer.write("(param ");
            this.writer.startLinkTarget("l" + this.functionIndex + "." + i).write(" (; " + i + " ;)");
            String paramName = this.nameProvider.local(this.functionIndex, i);
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
      }

      this.writer.outdent().write("))").eol();
      this.functionIndex++;
   }

   @Override
   public void global(WasmHollowType type, boolean mutable) {
      this.writer.address().write("(import \"").write(this.currentModule).write("\" \"").write(this.currentName).write("\" ");
      this.writer.write("(global ");
      this.writer.startLinkTarget("g" + this.globalIndex).write("(; " + this.globalIndex + " ;)");
      String name = this.nameProvider.global(this.globalIndex);
      if (name != null) {
         this.writer.write(" $").write(name);
      }

      this.writer.endLinkTarget();
      this.writer.write(" ");
      if (mutable) {
         this.writer.write("(mut ");
      }

      this.writeType(type);
      if (mutable) {
         this.writer.write(")");
      }

      this.writer.write("))").eol();
      this.globalIndex++;
   }

   @Override
   public void memory(int minSize, int maxSize) {
      this.writer.address().write("(import \"").write(this.currentModule).write("\" \"").write(this.currentName).write("\" ");
      this.writer.write("(memory ");
      this.writer.startLinkTarget("m" + this.memoryIndex).write("(; " + this.memoryIndex + " ;) ");
      this.writer.write(Integer.toString(minSize));
      if (maxSize >= 0) {
         this.writer.write(" ").write(Integer.toString(maxSize));
      }

      this.writer.write("))").eol();
      this.memoryIndex++;
   }
}
