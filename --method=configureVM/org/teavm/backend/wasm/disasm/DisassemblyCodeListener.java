package org.teavm.backend.wasm.disasm;

import org.teavm.backend.wasm.model.WasmNumType;
import org.teavm.backend.wasm.model.instruction.WasmFloatBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmFloatType;
import org.teavm.backend.wasm.model.instruction.WasmFloatUnaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmInt32Subtype;
import org.teavm.backend.wasm.model.instruction.WasmInt64Subtype;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.backend.wasm.model.instruction.WasmIntUnaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmSignedType;
import org.teavm.backend.wasm.parser.BranchOpcode;
import org.teavm.backend.wasm.parser.CodeListener;
import org.teavm.backend.wasm.parser.LocalOpcode;
import org.teavm.backend.wasm.parser.Opcode;
import org.teavm.backend.wasm.parser.WasmHollowBlockType;
import org.teavm.backend.wasm.parser.WasmHollowType;

public class DisassemblyCodeListener extends BaseDisassemblyListener implements CodeListener {
   private int blockIdGen;
   private int currentFunctionId;

   public DisassemblyCodeListener(DisassemblyWriter writer, NameProvider nameProvider) {
      super(writer, nameProvider);
   }

   public void setCurrentFunctionId(int currentFunctionId) {
      this.currentFunctionId = currentFunctionId;
   }

   public void reset() {
      this.blockIdGen = 0;
   }

   @Override
   public void error(int depth) {
      this.writer.address();
      this.writer.write("error").eol();

      for (int i = 0; i < depth; i++) {
         this.writer.outdent();
      }
   }

   @Override
   public int startBlock(boolean loop, WasmHollowBlockType type) {
      this.writer.address();
      int label = this.blockIdGen++;
      this.writer
         .startLinkTarget("start" + this.currentFunctionId + "-" + label)
         .startLink("end" + this.currentFunctionId + "-" + label)
         .write(loop ? "loop" : "block")
         .endLink()
         .endLinkTarget();
      this.writer.write(" $label_" + label);
      this.writeBlockType(type);
      this.writer.indent().eol();
      return label;
   }

   @Override
   public int startConditionalBlock(WasmHollowBlockType type) {
      this.writer.address();
      int label = this.blockIdGen++;
      this.writer
         .startLinkTarget("start" + this.currentFunctionId + "-" + label)
         .startLink("end" + this.currentFunctionId + "-" + label)
         .write("if")
         .endLink()
         .endLinkTarget();
      this.writer.write(" $label_" + label);
      this.writeBlockType(type);
      this.writer.indent().eol();
      return label;
   }

   @Override
   public void startElseSection(int token) {
      this.writer.address();
      this.writer.outdent().startLink("start" + this.currentFunctionId + "-" + token).write("else").endLink();
      this.writer.write(" (; $label_" + token + " ;)").indent().eol();
   }

   @Override
   public int startTry(WasmHollowType type) {
      this.writer.address();
      int label = this.blockIdGen++;
      this.writer
         .startLinkTarget("start" + this.currentFunctionId + "-" + label)
         .startLink("end" + this.currentFunctionId + "-" + label)
         .write("try")
         .endLink()
         .endLinkTarget();
      this.writer.write(" $label_" + label);
      if (type != null) {
         this.writer.write(" ");
         this.writeType(type);
      }

      this.writer.indent().eol();
      return label;
   }

   @Override
   public void startCatch(int tagIndex) {
      this.writer.outdent().address();
      this.writer.write("catch ").write(String.valueOf(tagIndex)).indent().eol();
   }

   @Override
   public int tryTable(WasmHollowBlockType type) {
      this.writer.address();
      int label = this.blockIdGen++;
      this.writer
         .startLinkTarget("start" + this.currentFunctionId + "-" + label)
         .startLink("end" + this.currentFunctionId + "-" + label)
         .write("try_table")
         .endLink()
         .endLinkTarget();
      this.writer.write(" $label_" + label);
      if (type != null) {
         this.writer.write(" ");
         this.writeBlockType(type);
      }

      this.writer.indent().eol();
      return label;
   }

   @Override
   public void catchTag(int tagIndex, boolean withRef, int depth, int target) {
      this.writer.address();
      this.writer.write("(").write(withRef ? "catch_ref" : "catch");
      this.writer.write(" ").write(String.valueOf(tagIndex));
      this.writer.write(" ").startLink("start" + this.currentFunctionId + "-" + target).write("$label_" + target).endLink();
      this.writer.write(")").eol();
   }

   @Override
   public void catchAll(boolean withRef, int depth, int target) {
      this.writer.address();
      this.writer.write("(").write(withRef ? "catch_all_ref" : "catch_all");
      this.writer.write(" ").startLink("start" + this.currentFunctionId + "-" + target).write("$label_" + target).endLink();
      this.writer.write(")").eol();
   }

   @Override
   public void endBlock(int token, boolean loop) {
      this.writer.address().outdent();
      this.writer
         .startLinkTarget("end" + this.currentFunctionId + "-" + token)
         .startLink("start" + this.currentFunctionId + "-" + token)
         .write("end")
         .endLink()
         .endLinkTarget();
      this.writer.write(" (; $label_" + token + " ;)").eol();
   }

   @Override
   public void branch(BranchOpcode opcode, int depth, int target) {
      this.writer.address().startLink("start" + this.currentFunctionId + "-" + target);
      switch (opcode) {
         case BR:
            this.writer.write("br");
            break;
         case BR_IF:
            this.writer.write("br_if");
            break;
         case BR_ON_NULL:
            this.writer.write("br_on_null");
            break;
         case BR_ON_NON_NULL:
            this.writer.write("br_on_non_null");
      }

      this.writer.endLink().write(" $label_" + target).eol();
   }

   @Override
   public void castBranch(boolean success, int depth, int target, WasmHollowType.Reference sourceType, WasmHollowType.Reference targetType) {
      this.writer.address().startLink("start" + this.currentFunctionId + "-" + target);
      if (success) {
         this.writer.write("br_if_cast");
      } else {
         this.writer.write("br_if_cast_fail");
      }

      this.writer.endLink().write(" $label_" + target);
      this.writer.write(" ");
      this.writeType(sourceType);
      this.writer.write(" ");
      this.writeType(targetType);
      this.writer.eol();
   }

   @Override
   public void tableBranch(int[] depths, int[] targets, int defaultDepth, int defaultTarget) {
      this.writer.address();
      this.writer.write("br_table");

      for (int target : targets) {
         this.writer.write(" $label_" + target);
      }

      this.writer.write(" $label_" + defaultTarget).eol();
   }

   @Override
   public void throwInstruction(int tagIndex) {
      this.writer.address();
      this.writer.write("throw ").write(String.valueOf(tagIndex)).eol();
   }

   @Override
   public void opcode(Opcode opcode) {
      this.writer.address();
      switch (opcode) {
         case UNREACHABLE:
            this.writer.write("unreachable");
            break;
         case NOP:
            this.writer.write("nop");
            break;
         case RETURN:
            this.writer.write("return");
            break;
         case DROP:
            this.writer.write("drop");
            break;
         case REF_EQ:
            this.writer.write("ref.eq");
            break;
         case ARRAY_LENGTH:
            this.writer.write("array.length");
            break;
         case IS_NULL:
            this.writer.write("ref.is_null");
            break;
         case EXTERN_TO_ANY:
            this.writer.write("any.convert_extern");
            break;
         case ANY_TO_EXTERN:
            this.writer.write("extern.convert_any");
            break;
         case THROW_REF:
            this.writer.write("throw_ref");
      }

      this.writer.eol();
   }

   @Override
   public void local(LocalOpcode opcode, int index) {
      this.writer.address();
      switch (opcode) {
         case GET:
            this.writer.write("local.get");
            break;
         case SET:
            this.writer.write("local.set");
            break;
         case TEE:
            this.writer.write("local.tee");
      }

      this.writer.write(" ");
      this.writeLocalRef(this.currentFunctionId, index);
      this.writer.eol();
   }

   @Override
   public void getGlobal(int globalIndex) {
      this.writer.address().write("global.get ");
      this.writeGlobalRef(globalIndex);
      this.writer.eol();
   }

   @Override
   public void setGlobal(int globalIndex) {
      this.writer.address().write("global.set ");
      this.writeGlobalRef(globalIndex);
      this.writer.eol();
   }

   @Override
   public void call(int functionIndex) {
      this.writer.address();
      this.writer.write("call ");
      this.writeFunctionRef(functionIndex);
      this.writer.eol();
   }

   @Override
   public void indirectCall(int typeIndex, int tableIndex) {
      this.writer.address();
      this.writer.write("call_indirect " + tableIndex + " " + typeIndex).eol();
   }

   @Override
   public void callReference(int typeIndex) {
      this.writer.address();
      this.writer.write("call_ref ");
      this.writeTypeRef(typeIndex);
      this.writer.eol();
   }

   @Override
   public void loadInt32(WasmInt32Subtype convertFrom, int align, int offset) {
      this.writer.address();
      int defaultAlign = 0;
      switch (convertFrom) {
         case INT8:
            this.writer.write("i32.load8_s");
            defaultAlign = 1;
            break;
         case UINT8:
            this.writer.write("i32.load8_u");
            defaultAlign = 1;
            break;
         case INT16:
            this.writer.write("i32.load16_s");
            defaultAlign = 2;
            break;
         case UINT16:
            this.writer.write("i32.load16_u");
            defaultAlign = 2;
            break;
         case INT32:
            this.writer.write("i32.load");
            defaultAlign = 4;
      }

      this.writeMemArg(align, defaultAlign, offset);
      this.writer.eol();
   }

   @Override
   public void storeInt32(WasmInt32Subtype convertTo, int align, int offset) {
      this.writer.address();
      int defaultAlign = 0;
      switch (convertTo) {
         case INT8:
         case UINT8:
            this.writer.write("i32.store8");
            defaultAlign = 1;
            break;
         case INT16:
         case UINT16:
            this.writer.write("i32.store16");
            defaultAlign = 2;
            break;
         case INT32:
            this.writer.write("i32.store");
            defaultAlign = 4;
      }

      this.writeMemArg(align, defaultAlign, offset);
      this.writer.eol();
   }

   @Override
   public void loadInt64(WasmInt64Subtype convertFrom, int align, int offset) {
      this.writer.address();
      int defaultAlign = 0;
      switch (convertFrom) {
         case INT8:
            this.writer.write("i64.load8_s");
            defaultAlign = 1;
            break;
         case UINT8:
            this.writer.write("i64.load8_u");
            defaultAlign = 1;
            break;
         case INT16:
            this.writer.write("i64.load16_s");
            defaultAlign = 2;
            break;
         case UINT16:
            this.writer.write("i64.load16_u");
            defaultAlign = 2;
            break;
         case INT32:
            this.writer.write("i64.load32_s");
            defaultAlign = 4;
            break;
         case UINT32:
            this.writer.write("i64.load32_u");
            defaultAlign = 4;
            break;
         case INT64:
            this.writer.write("i64.load");
            defaultAlign = 8;
      }

      this.writeMemArg(align, defaultAlign, offset);
      this.writer.eol();
   }

   @Override
   public void storeInt64(WasmInt64Subtype convertTo, int align, int offset) {
      this.writer.address();
      int defaultAlign = 0;
      switch (convertTo) {
         case INT8:
         case UINT8:
            this.writer.write("i64.store8");
            defaultAlign = 1;
            break;
         case INT16:
         case UINT16:
            this.writer.write("i64.store16");
            defaultAlign = 2;
            break;
         case INT32:
         case UINT32:
            this.writer.write("i64.store32");
            defaultAlign = 4;
            break;
         case INT64:
            this.writer.write("i64.store");
            defaultAlign = 8;
      }

      this.writeMemArg(align, defaultAlign, offset);
      this.writer.eol();
   }

   @Override
   public void loadFloat32(int align, int offset) {
      this.writer.address().write("f32.load");
      this.writeMemArg(align, 4, offset);
      this.writer.eol();
   }

   @Override
   public void storeFloat32(int align, int offset) {
      this.writer.address().write("f32.store");
      this.writeMemArg(align, 4, offset);
      this.writer.eol();
   }

   @Override
   public void loadFloat64(int align, int offset) {
      this.writer.address().write("f64.load");
      this.writeMemArg(align, 8, offset);
      this.writer.eol();
   }

   @Override
   public void storeFloat64(int align, int offset) {
      this.writer.address().write("f64.store");
      this.writeMemArg(align, 8, offset);
      this.writer.eol();
   }

   @Override
   public void memoryGrow() {
      this.writer.address().write("memory.grow").eol();
   }

   @Override
   public void memoryFill() {
      this.writer.address().write("memory.fill").eol();
   }

   @Override
   public void memoryCopy() {
      this.writer.address().write("memory.copy").eol();
   }

   private void writeMemArg(int align, int defaultAlign, int offset) {
      boolean needsComma = false;
      if (align != defaultAlign) {
         this.writer.write(" align=" + align);
         needsComma = true;
      }

      if (offset != 0) {
         if (needsComma) {
            this.writer.write(",");
         }

         this.writer.write(" offset=" + offset);
      }
   }

   @Override
   public void unary(WasmIntUnaryOperation opcode, WasmIntType type) {
      this.writer.address();
      switch (type) {
         case INT32:
            this.writer.write("i32.");
            break;
         case INT64:
            this.writer.write("i64.");
      }

      switch (opcode) {
         case CLZ:
            this.writer.write("clz");
            break;
         case CTZ:
            this.writer.write("ctz");
            break;
         case EQZ:
            this.writer.write("eqz");
            break;
         case POPCNT:
            this.writer.write("popcnt");
      }

      this.writer.eol();
   }

   @Override
   public void unary(WasmFloatUnaryOperation opcode, WasmFloatType type) {
      this.writer.address();
      switch (type) {
         case FLOAT32:
            this.writer.write("f32.");
            break;
         case FLOAT64:
            this.writer.write("f64.");
      }

      switch (opcode) {
         case ABS:
            this.writer.write("abs");
            break;
         case NEG:
            this.writer.write("neg");
            break;
         case FLOOR:
            this.writer.write("floor");
            break;
         case CEIL:
            this.writer.write("ceil");
            break;
         case TRUNC:
            this.writer.write("trunc");
            break;
         case NEAREST:
            this.writer.write("nearest");
            break;
         case SQRT:
            this.writer.write("sqrt");
            break;
         case COPYSIGN:
            this.writer.write("copysign");
      }

      this.writer.eol();
   }

   @Override
   public void binary(WasmIntBinaryOperation opcode, WasmIntType type) {
      this.writer.address();
      switch (type) {
         case INT32:
            this.writer.write("i32.");
            break;
         case INT64:
            this.writer.write("i64.");
      }

      switch (opcode) {
         case ADD:
            this.writer.write("add");
            break;
         case SUB:
            this.writer.write("sub");
            break;
         case MUL:
            this.writer.write("mul");
            break;
         case DIV_SIGNED:
            this.writer.write("div_s");
            break;
         case DIV_UNSIGNED:
            this.writer.write("div_u");
            break;
         case REM_SIGNED:
            this.writer.write("rem_s");
            break;
         case REM_UNSIGNED:
            this.writer.write("rem_u");
            break;
         case AND:
            this.writer.write("and");
            break;
         case OR:
            this.writer.write("or");
            break;
         case XOR:
            this.writer.write("xor");
            break;
         case SHL:
            this.writer.write("shl");
            break;
         case SHR_SIGNED:
            this.writer.write("shr_s");
            break;
         case SHR_UNSIGNED:
            this.writer.write("shr_u");
            break;
         case ROTL:
            this.writer.write("rotl");
            break;
         case ROTR:
            this.writer.write("rotr");
            break;
         case EQ:
            this.writer.write("eq");
            break;
         case NE:
            this.writer.write("ne");
            break;
         case LT_SIGNED:
            this.writer.write("lt_s");
            break;
         case LT_UNSIGNED:
            this.writer.write("lt_u");
            break;
         case GT_SIGNED:
            this.writer.write("gt_s");
            break;
         case GT_UNSIGNED:
            this.writer.write("gt_u");
            break;
         case LE_SIGNED:
            this.writer.write("le_s");
            break;
         case LE_UNSIGNED:
            this.writer.write("le_u");
            break;
         case GE_SIGNED:
            this.writer.write("ge_s");
            break;
         case GE_UNSIGNED:
            this.writer.write("ge_u");
      }

      this.writer.eol();
   }

   @Override
   public void binary(WasmFloatBinaryOperation opcode, WasmFloatType type) {
      this.writer.address();
      switch (type) {
         case FLOAT32:
            this.writer.write("f32.");
            break;
         case FLOAT64:
            this.writer.write("f64.");
      }

      switch (opcode) {
         case ADD:
            this.writer.write("add");
            break;
         case SUB:
            this.writer.write("sub");
            break;
         case MUL:
            this.writer.write("mul");
            break;
         case DIV:
            this.writer.write("div");
            break;
         case MIN:
            this.writer.write("min");
            break;
         case MAX:
            this.writer.write("max");
            break;
         case EQ:
            this.writer.write("eq");
            break;
         case NE:
            this.writer.write("ne");
            break;
         case LT:
            this.writer.write("lt");
            break;
         case GT:
            this.writer.write("gt");
            break;
         case LE:
            this.writer.write("le");
            break;
         case GE:
            this.writer.write("ge");
      }

      this.writer.eol();
   }

   @Override
   public void convert(WasmNumType sourceType, WasmNumType targetType, boolean signed, boolean reinterpret, boolean nonTrapping) {
      label93:
      switch (targetType) {
         case FLOAT32:
            this.writer.address().write("f32.");
            switch (sourceType) {
               case FLOAT64:
                  this.writer.write("demote_f64");
                  break label93;
               case INT64:
                  if (signed) {
                     this.writer.write("convert_i64_s");
                  } else {
                     this.writer.write("convert_i64_u");
                  }
                  break label93;
               case INT32:
                  if (reinterpret) {
                     this.writer.write("reinterpret_i32");
                  } else if (signed) {
                     this.writer.write("convert_i32_s");
                  } else {
                     this.writer.write("convert_i32_u");
                  }
                  break label93;
               default:
                  this.writer.write("error");
                  break label93;
            }
         case FLOAT64:
            this.writer.address().write("f64.");
            switch (sourceType) {
               case FLOAT32:
                  this.writer.write("promote_f32");
                  break label93;
               case FLOAT64:
               default:
                  this.writer.write("error");
                  break label93;
               case INT64:
                  if (reinterpret) {
                     this.writer.write("reinterpret_i64");
                  } else if (signed) {
                     this.writer.write("convert_i64_s");
                  } else {
                     this.writer.write("convert_i64_u");
                  }
                  break label93;
               case INT32:
                  if (signed) {
                     this.writer.write("convert_i32_s");
                  } else {
                     this.writer.write("convert_i32_u");
                  }
                  break label93;
            }
         case INT64:
            this.writer.address().write("i64.");
            switch (sourceType) {
               case FLOAT32:
                  this.writer.write("trunc_");
                  if (nonTrapping) {
                     this.writer.write("sat_");
                  }

                  this.writer.write("f32_").write(signed ? "s" : "u");
                  break label93;
               case FLOAT64:
                  if (reinterpret) {
                     this.writer.write("reinterpret_f64");
                  } else {
                     this.writer.write("trunc_");
                     if (nonTrapping) {
                        this.writer.write("sat_");
                     }

                     this.writer.write("f64_").write(signed ? "s" : "u");
                  }
                  break label93;
               case INT64:
               default:
                  this.writer.write("error");
                  break label93;
               case INT32:
                  if (signed) {
                     this.writer.write("extend_i32_s");
                  } else {
                     this.writer.write("extend_i32_u");
                  }
                  break label93;
            }
         case INT32:
            this.writer.address().write("i32.");
            switch (sourceType) {
               case FLOAT32:
                  if (reinterpret) {
                     this.writer.write("reinterpret_f32");
                  } else {
                     this.writer.write("trunc_");
                     if (nonTrapping) {
                        this.writer.write("sat_");
                     }

                     this.writer.write("f32_").write(signed ? "s" : "u");
                  }
                  break;
               case FLOAT64:
                  this.writer.write("trunc_");
                  if (nonTrapping) {
                     this.writer.write("sat_");
                  }

                  this.writer.write("f64_").write(signed ? "s" : "u");
                  break;
               case INT64:
                  this.writer.write("wrap_i64");
                  break;
               default:
                  this.writer.write("error");
            }
      }

      this.writer.eol();
   }

   @Override
   public void int32Constant(int value) {
      this.writer.address().write("i32.const " + value).eol();
   }

   @Override
   public void int64Constant(long value) {
      this.writer.address().write("i64.const " + value).eol();
   }

   @Override
   public void float32Constant(float value) {
      this.writer.address().write("f32.const " + Float.toHexString(value)).eol();
   }

   @Override
   public void float64Constant(double value) {
      this.writer.address().write("f64.const " + Double.toHexString(value)).eol();
   }

   @Override
   public void nullConstant(WasmHollowType.Reference type) {
      this.writer.address().write("ref.null ");
      this.writeType(type);
      this.writer.eol();
   }

   @Override
   public void cast(WasmHollowType.Reference type) {
      this.writer.address().write("ref.cast ");
      this.writeType(type);
      this.writer.eol();
   }

   @Override
   public void test(WasmHollowType.Reference type) {
      this.writer.address().write("ref.test ");
      this.writeType(type);
      this.writer.eol();
   }

   @Override
   public void structNew(int typeIndex) {
      this.writer.address().write("struct.new ");
      this.writeTypeRef(typeIndex);
      this.writer.eol();
   }

   @Override
   public void structNewDefault(int typeIndex) {
      this.writer.address().write("struct.new_default ");
      this.writeTypeRef(typeIndex);
      this.writer.eol();
   }

   @Override
   public void structGet(WasmSignedType signedType, int typeIndex, int fieldIndex) {
      this.writer.address();
      if (signedType == null) {
         this.writer.write("struct.get");
      } else if (signedType == WasmSignedType.SIGNED) {
         this.writer.write("struct.get_s");
      } else {
         this.writer.write("struct.get_u");
      }

      this.writer.write(" ");
      this.writeTypeRef(typeIndex);
      this.writer.write(" ");
      this.writeFieldRef(typeIndex, fieldIndex);
      this.writer.eol();
   }

   @Override
   public void structSet(int typeIndex, int fieldIndex) {
      this.writer.address().write("struct.set ");
      this.writeTypeRef(typeIndex);
      this.writer.write(" ");
      this.writeFieldRef(typeIndex, fieldIndex);
      this.writer.eol();
   }

   @Override
   public void arrayNewDefault(int typeIndex) {
      this.writer.address().write("array.new_default ");
      this.writeTypeRef(typeIndex);
      this.writer.eol();
   }

   @Override
   public void arrayNewFixed(int typeIndex, int size) {
      this.writer.address().write("array.new_fixed ");
      this.writeTypeRef(typeIndex);
      this.writer.write(" ").write(Integer.toString(size)).eol();
   }

   @Override
   public void arrayGet(WasmSignedType signedType, int typeIndex) {
      this.writer.address();
      if (signedType == null) {
         this.writer.write("array.get");
      } else if (signedType == WasmSignedType.SIGNED) {
         this.writer.write("array.get_s");
      } else {
         this.writer.write("array.get_u");
      }

      this.writer.write(" ");
      this.writeTypeRef(typeIndex);
      this.writer.eol();
   }

   @Override
   public void arraySet(int typeIndex) {
      this.writer.address().write("array.set ");
      this.writeTypeRef(typeIndex);
      this.writer.eol();
   }

   @Override
   public void arrayCopy(int targetTypeIndex, int sourceTypeIndex) {
      this.writer.address().write("array.copy ");
      this.writeTypeRef(targetTypeIndex);
      this.writer.write(" ");
      this.writeTypeRef(sourceTypeIndex);
      this.writer.eol();
   }

   @Override
   public void functionReference(int functionIndex) {
      this.writer.address().write("ref.func ");
      this.writeFunctionRef(functionIndex);
      this.writer.eol();
   }

   @Override
   public void int31Reference() {
      this.writer.address().write("ref.i31").eol();
   }

   @Override
   public void int31Get(WasmSignedType signedType) {
      this.writer.address().write("ref.i31_").write(signedType == WasmSignedType.SIGNED ? "s" : "u").eol();
   }
}
