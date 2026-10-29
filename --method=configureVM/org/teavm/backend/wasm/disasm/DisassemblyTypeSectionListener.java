package org.teavm.backend.wasm.disasm;

import java.util.ArrayList;
import java.util.List;
import org.teavm.backend.wasm.parser.TypeSectionListener;
import org.teavm.backend.wasm.parser.WasmHollowFunctionType;
import org.teavm.backend.wasm.parser.WasmHollowStorageType;
import org.teavm.backend.wasm.parser.WasmHollowType;

public class DisassemblyTypeSectionListener extends BaseDisassemblyListener implements TypeSectionListener {
   private boolean currentTypeNeedsClosing;
   private boolean emittingReturn;
   private int currentTypeIndex;
   private int fieldIndex;
   private boolean needsFieldIndex;
   private List<WasmHollowFunctionType> functionTypes = new ArrayList<>();
   private List<WasmHollowType> parameterTypes = new ArrayList<>();
   private List<WasmHollowType> resultTypes = new ArrayList<>();

   public DisassemblyTypeSectionListener(DisassemblyWriter writer, NameProvider nameProvider) {
      super(writer, nameProvider);
   }

   @Override
   public void startRecType(int count) {
      this.writer.address().write("(rec ").indent().eol();
   }

   @Override
   public void endRecType() {
      this.writer.outdent().write(")").eol();
   }

   @Override
   public void startType(int index, boolean open, int[] supertypes) {
      this.functionTypes.add(null);
      this.currentTypeIndex = index;
      this.writer.address().write("(type ");
      this.writer.startLinkTarget("t" + index).write("(; ").write(String.valueOf(index)).write(" ;) ");
      String name = this.nameProvider.type(index);
      if (name != null) {
         this.writer.write("$").write(name);
      }

      this.writer.endLinkTarget().write(" ");
      if (!open || supertypes.length > 0) {
         this.currentTypeNeedsClosing = true;
         this.writer.write("(sub ");
         if (!open) {
            this.writer.write("final");
         }

         for (int supertype : supertypes) {
            this.writeTypeRef(supertype);
            this.writer.write(" ");
         }
      }

      this.writer.indent().eol();
   }

   @Override
   public void startArrayType() {
      this.writer.address().write("(array ").indent().eol();
   }

   @Override
   public void endArrayType() {
      this.writer.outdent().write(")").eol();
   }

   @Override
   public void startStructType(int fieldCount) {
      this.needsFieldIndex = true;
      this.writer.address().write("(struct ").indent().eol();
   }

   @Override
   public void field(WasmHollowStorageType hollowType, boolean mutable) {
      this.writer.address().write("(field ");
      if (this.needsFieldIndex) {
         int index = this.fieldIndex++;
         this.writer.startLinkTarget("f" + this.currentTypeIndex + "." + index).write("(; " + index + " ;)");
         String name = this.nameProvider.field(this.currentTypeIndex, index);
         if (name != null) {
            this.writer.write(" ").write("$").write(name);
         }

         this.writer.endLinkTarget().write(" ");
      }

      if (mutable) {
         this.writer.write("(mut ");
      }

      this.writeType(hollowType);
      if (mutable) {
         this.writer.write(")");
      }

      this.writer.write(")").eol();
   }

   @Override
   public void endStructType() {
      this.writer.outdent().write(")").eol();
      this.fieldIndex = 0;
      this.needsFieldIndex = false;
   }

   @Override
   public void funcType(int paramCount) {
      this.writer.address().write("(func ").indent().eol();
   }

   @Override
   public void funcTypeResults(int returnCount) {
      this.emittingReturn = true;
   }

   @Override
   public void resultType(WasmHollowType type) {
      if (this.emittingReturn) {
         this.resultTypes.add(type);
      } else {
         this.parameterTypes.add(type);
      }

      this.writer.address().write("(").write(this.emittingReturn ? "result" : "param").write(" ");
      this.writeType(type);
      this.writer.write(")").eol();
   }

   @Override
   public void endFuncType() {
      WasmHollowFunctionType type = new WasmHollowFunctionType(
         this.parameterTypes.toArray(new WasmHollowType[0]), this.resultTypes.toArray(new WasmHollowType[0])
      );
      this.functionTypes.set(this.currentTypeIndex, type);
      this.parameterTypes.clear();
      this.resultTypes.clear();
      this.emittingReturn = false;
      this.writer.outdent().write(")").eol();
   }

   @Override
   public void endType() {
      this.writer.outdent();
      if (this.currentTypeNeedsClosing) {
         this.writer.write(")");
         this.currentTypeNeedsClosing = false;
      }

      this.writer.write(")").eol();
   }

   public WasmHollowFunctionType[] getFunctionTypes() {
      return this.functionTypes.toArray(new WasmHollowFunctionType[0]);
   }
}
