package org.teavm.backend.wasm.disasm;

import org.teavm.backend.wasm.parser.WasmHollowBlockType;
import org.teavm.backend.wasm.parser.WasmHollowStorageType;
import org.teavm.backend.wasm.parser.WasmHollowType;

public abstract class BaseDisassemblyListener {
   protected final DisassemblyWriter writer;
   protected final NameProvider nameProvider;

   public BaseDisassemblyListener(DisassemblyWriter writer, NameProvider nameProvider) {
      this.writer = writer;
      this.nameProvider = nameProvider;
   }

   protected void writeBlockType(WasmHollowBlockType type) {
      if (type != null) {
         this.writer.write(" ");
         if (type instanceof WasmHollowBlockType.Value) {
            this.writeType(((WasmHollowBlockType.Value)type).type);
         } else {
            this.writer.write("(type ");
            this.writeTypeRef(((WasmHollowBlockType.Function)type).ref);
            this.writer.write(")");
         }
      }
   }

   protected void writeType(WasmHollowStorageType type) {
      if (type instanceof WasmHollowStorageType.Packed) {
         switch (((WasmHollowStorageType.Packed)type).type) {
            case INT8:
               this.writer.write("i8");
               return;
            case INT16:
               this.writer.write("i16");
               return;
         }
      } else {
         this.writeType(type.asUnpackedType());
      }
   }

   protected void writeType(WasmHollowType type) {
      label53:
      if (type == null) {
         this.writer.write("unknown");
      } else if (type instanceof WasmHollowType.Number) {
         switch (((WasmHollowType.Number)type).number) {
            case INT32:
               this.writer.write("i32");
               return;
            case INT64:
               this.writer.write("i64");
               return;
            case FLOAT32:
               this.writer.write("f32");
               return;
            case FLOAT64:
               this.writer.write("f64");
               return;
            default:
               break label53;
         }
      } else if (type instanceof WasmHollowType.SpecialReference refType) {
         if (refType.isNullable()) {
            switch (refType.kind) {
               case ANY:
                  this.writer.write("anyref");
                  return;
               case EQ:
                  this.writer.write("eqref");
                  return;
               case FUNC:
                  this.writer.write("funcref");
                  return;
               case ARRAY:
                  this.writer.write("arrayref");
                  return;
               case EXTERN:
                  this.writer.write("externref");
                  return;
               case STRUCT:
                  this.writer.write("structref");
                  return;
               case EXN:
                  this.writer.write("exnref");
                  return;
               case I31:
                  this.writer.write("i31ref");
                  return;
               default:
                  throw new IllegalArgumentException();
            }
         } else {
            this.writer.write("(ref ");
            switch (refType.kind) {
               case ANY:
                  this.writer.write("any");
                  break;
               case EQ:
                  this.writer.write("eq");
                  break;
               case FUNC:
                  this.writer.write("func");
                  break;
               case ARRAY:
                  this.writer.write("array");
                  break;
               case EXTERN:
                  this.writer.write("extern");
                  break;
               case STRUCT:
                  this.writer.write("struct");
                  break;
               case EXN:
                  this.writer.write("exn");
                  break;
               case I31:
                  this.writer.write("i31");
                  break;
               default:
                  throw new IllegalArgumentException();
            }

            this.writer.write(")");
         }
      } else {
         if (type instanceof WasmHollowType.CompositeReference refType) {
            this.writer.write("(ref ");
            if (refType.isNullable()) {
               this.writer.write("null ");
            }

            this.writeTypeRef(refType.index);
            this.writer.write(")");
            return;
         }
         break label53;
      }
   }

   protected void writeGlobalRef(int index) {
      this.writer.startLink("g" + index);
      this.writeRef(this.nameProvider.global(index), index);
      this.writer.endLink();
   }

   protected void writeFunctionRef(int index) {
      this.writer.startLink("f" + index);
      this.writeRef(this.nameProvider.function(index), index);
      this.writer.endLink();
   }

   protected void writeTypeRef(int index) {
      this.writer.startLink("t" + index);
      this.writeRef(this.nameProvider.type(index), index);
      this.writer.endLink();
   }

   protected void writeFieldRef(int typeIndex, int index) {
      this.writer.startLink("f" + typeIndex + "." + index);
      this.writeRef(this.nameProvider.field(typeIndex, index), index);
      this.writer.endLink();
   }

   protected void writeLocalRef(int functionIndex, int index) {
      this.writer.startLink("l" + functionIndex + "." + index);
      this.writeRef(this.nameProvider.local(functionIndex, index), index);
      this.writer.endLink();
   }

   private void writeRef(String name, int index) {
      if (name == null) {
         this.writer.write(Integer.toString(index));
      } else {
         this.writer.write("(; ").write(Integer.toString(index)).write(" ;) $").write(name);
      }
   }
}
