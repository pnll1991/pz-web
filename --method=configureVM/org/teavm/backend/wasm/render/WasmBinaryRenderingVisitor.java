package org.teavm.backend.wasm.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.teavm.backend.wasm.debug.DebugLines;
import org.teavm.backend.wasm.generate.DwarfGenerator;
import org.teavm.backend.wasm.model.WasmBlockType;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmArrayCopy;
import org.teavm.backend.wasm.model.instruction.WasmArrayGet;
import org.teavm.backend.wasm.model.instruction.WasmArrayLength;
import org.teavm.backend.wasm.model.instruction.WasmArrayNewDefault;
import org.teavm.backend.wasm.model.instruction.WasmArrayNewFixed;
import org.teavm.backend.wasm.model.instruction.WasmArraySet;
import org.teavm.backend.wasm.model.instruction.WasmBlock;
import org.teavm.backend.wasm.model.instruction.WasmBranch;
import org.teavm.backend.wasm.model.instruction.WasmBreak;
import org.teavm.backend.wasm.model.instruction.WasmCall;
import org.teavm.backend.wasm.model.instruction.WasmCallReference;
import org.teavm.backend.wasm.model.instruction.WasmCast;
import org.teavm.backend.wasm.model.instruction.WasmCastBranch;
import org.teavm.backend.wasm.model.instruction.WasmCatchClause;
import org.teavm.backend.wasm.model.instruction.WasmConditional;
import org.teavm.backend.wasm.model.instruction.WasmConversion;
import org.teavm.backend.wasm.model.instruction.WasmCopy;
import org.teavm.backend.wasm.model.instruction.WasmDrop;
import org.teavm.backend.wasm.model.instruction.WasmExternConversion;
import org.teavm.backend.wasm.model.instruction.WasmFill;
import org.teavm.backend.wasm.model.instruction.WasmFloat32Constant;
import org.teavm.backend.wasm.model.instruction.WasmFloat64Constant;
import org.teavm.backend.wasm.model.instruction.WasmFloatBinary;
import org.teavm.backend.wasm.model.instruction.WasmFloatUnary;
import org.teavm.backend.wasm.model.instruction.WasmFunctionReference;
import org.teavm.backend.wasm.model.instruction.WasmGetGlobal;
import org.teavm.backend.wasm.model.instruction.WasmGetLocal;
import org.teavm.backend.wasm.model.instruction.WasmIndirectCall;
import org.teavm.backend.wasm.model.instruction.WasmInstruction;
import org.teavm.backend.wasm.model.instruction.WasmInstructionList;
import org.teavm.backend.wasm.model.instruction.WasmInstructionVisitor;
import org.teavm.backend.wasm.model.instruction.WasmInt31Get;
import org.teavm.backend.wasm.model.instruction.WasmInt31Reference;
import org.teavm.backend.wasm.model.instruction.WasmInt32Constant;
import org.teavm.backend.wasm.model.instruction.WasmInt64Constant;
import org.teavm.backend.wasm.model.instruction.WasmIntBinary;
import org.teavm.backend.wasm.model.instruction.WasmIntUnary;
import org.teavm.backend.wasm.model.instruction.WasmIsNull;
import org.teavm.backend.wasm.model.instruction.WasmLoadFloat32;
import org.teavm.backend.wasm.model.instruction.WasmLoadFloat64;
import org.teavm.backend.wasm.model.instruction.WasmLoadInt32;
import org.teavm.backend.wasm.model.instruction.WasmLoadInt64;
import org.teavm.backend.wasm.model.instruction.WasmMemoryGrow;
import org.teavm.backend.wasm.model.instruction.WasmNullBranch;
import org.teavm.backend.wasm.model.instruction.WasmNullConstant;
import org.teavm.backend.wasm.model.instruction.WasmReferencesEqual;
import org.teavm.backend.wasm.model.instruction.WasmReturn;
import org.teavm.backend.wasm.model.instruction.WasmSetGlobal;
import org.teavm.backend.wasm.model.instruction.WasmSetLocal;
import org.teavm.backend.wasm.model.instruction.WasmSignedType;
import org.teavm.backend.wasm.model.instruction.WasmStoreFloat32;
import org.teavm.backend.wasm.model.instruction.WasmStoreFloat64;
import org.teavm.backend.wasm.model.instruction.WasmStoreInt32;
import org.teavm.backend.wasm.model.instruction.WasmStoreInt64;
import org.teavm.backend.wasm.model.instruction.WasmStructGet;
import org.teavm.backend.wasm.model.instruction.WasmStructNew;
import org.teavm.backend.wasm.model.instruction.WasmStructNewDefault;
import org.teavm.backend.wasm.model.instruction.WasmStructSet;
import org.teavm.backend.wasm.model.instruction.WasmSwitch;
import org.teavm.backend.wasm.model.instruction.WasmTeeLocal;
import org.teavm.backend.wasm.model.instruction.WasmTest;
import org.teavm.backend.wasm.model.instruction.WasmThrow;
import org.teavm.backend.wasm.model.instruction.WasmTry;
import org.teavm.backend.wasm.model.instruction.WasmUnreachable;
import org.teavm.model.InliningInfo;
import org.teavm.model.TextLocation;

class WasmBinaryRenderingVisitor implements WasmInstructionVisitor {
   private final WasmBinaryWriter writer;
   private final WasmModule module;
   private final DebugLines debugLines;
   private final DwarfGenerator dwarfGenerator;
   private int depth;
   private final Map<WasmInstruction, Integer> blockDepths = new HashMap<>();
   private TextLocation lastEmittedLocation;
   private int addressOffset;
   private List<InliningInfo> methodStack = new ArrayList<>();
   private List<InliningInfo> currentMethodStack = new ArrayList<>();

   WasmBinaryRenderingVisitor(WasmBinaryWriter writer, WasmModule module, DebugLines debugLines, DwarfGenerator dwarfGenerator, int addressOffset) {
      this.writer = writer;
      this.module = module;
      this.debugLines = debugLines;
      this.dwarfGenerator = dwarfGenerator;
      this.addressOffset = addressOffset;
   }

   void render(WasmInstructionList list) {
      for (WasmInstruction instruction : list) {
         instruction.acceptVisitor(this);
      }
   }

   @Override
   public void visit(WasmUnreachable instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(0);
   }

   @Override
   public void visit(WasmBlock instruction) {
      this.depth++;
      this.blockDepths.put(instruction, this.depth);
      this.writer.writeByte(instruction.isLoop() ? 3 : 2);
      this.writeBlockType(instruction.getType());
      this.render(instruction.getBody());
      this.writer.writeByte(11);
      this.blockDepths.remove(instruction);
      this.depth--;
   }

   @Override
   public void visit(WasmConditional instruction) {
      this.writer.writeByte(4);
      this.writeBlockType(instruction.getType());
      this.depth++;
      this.blockDepths.put(instruction, this.depth);
      this.render(instruction.getThenBlock());
      if (!instruction.getElseBlock().isEmpty()) {
         this.writer.writeByte(5);
         this.render(instruction.getElseBlock());
      }

      this.blockDepths.remove(instruction);
      this.depth--;
      this.writer.writeByte(11);
   }

   @Override
   public void visit(WasmBranch instruction) {
      this.writer.writeByte(13);
      this.writeLabel(instruction, instruction.getTarget());
   }

   @Override
   public void visit(WasmNullBranch instruction) {
      this.emitLocation(instruction);
      switch (instruction.getCondition()) {
         case NULL:
            this.writer.writeByte(213);
            break;
         case NOT_NULL:
            this.writer.writeByte(214);
      }

      this.writeLabel(instruction, instruction.getTarget());
   }

   @Override
   public void visit(WasmCastBranch instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      switch (instruction.getCondition()) {
         case SUCCESS:
            this.writer.writeByte(24);
            break;
         case FAILURE:
            this.writer.writeByte(25);
      }

      int flags = 0;
      if (instruction.getSourceType().isNullable()) {
         flags |= 1;
      }

      if (instruction.getTargetType().isNullable()) {
         flags |= 2;
      }

      this.writer.writeByte(flags);
      this.writeLabel(instruction, instruction.getTarget());
      this.writer.writeHeapType(instruction.getSourceType(), this.module);
      this.writer.writeHeapType(instruction.getTargetType(), this.module);
   }

   @Override
   public void visit(WasmBreak instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(12);
      this.writeLabel(instruction, instruction.getTarget());
   }

   @Override
   public void visit(WasmSwitch instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(14);
      this.writer.writeLEB(instruction.getTargets().size());

      for (WasmInstructionList target : instruction.getTargets()) {
         this.writeLabel(instruction, target);
      }

      this.writer.writeLEB(this.depth - this.blockDepths.get(instruction.getDefaultTarget().getBreakTarget()));
   }

   @Override
   public void visit(WasmReturn instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(15);
   }

   @Override
   public void visit(WasmInt32Constant instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(65);
      this.writer.writeSignedLEB(instruction.getValue());
   }

   @Override
   public void visit(WasmInt64Constant instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(66);
      this.writer.writeSignedLEB(instruction.getValue());
   }

   @Override
   public void visit(WasmFloat32Constant instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(67);
      this.writer.writeFixed(Float.floatToRawIntBits(instruction.getValue()));
   }

   @Override
   public void visit(WasmFloat64Constant instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(68);
      this.writer.writeFixed(Double.doubleToRawLongBits(instruction.getValue()));
   }

   @Override
   public void visit(WasmNullConstant instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(208);
      this.writer.writeHeapType(instruction.getType(), this.module);
   }

   @Override
   public void visit(WasmIsNull instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(209);
   }

   @Override
   public void visit(WasmGetLocal instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(32);
      this.writer.writeLEB(instruction.getLocal().getIndex());
   }

   @Override
   public void visit(WasmSetLocal instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(33);
      this.writer.writeLEB(instruction.getLocal().getIndex());
   }

   @Override
   public void visit(WasmTeeLocal instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(34);
      this.writer.writeLEB(instruction.getLocal().getIndex());
   }

   @Override
   public void visit(WasmGetGlobal instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(35);
      this.writer.writeLEB(this.module.globals.indexOf(instruction.getGlobal()));
   }

   @Override
   public void visit(WasmSetGlobal instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(36);
      this.writer.writeLEB(this.module.globals.indexOf(instruction.getGlobal()));
   }

   @Override
   public void visit(WasmIntBinary instruction) {
      this.emitLocation(instruction);
      switch (instruction.getType()) {
         case INT32:
            switch (instruction.getOperation()) {
               case ADD:
                  this.writer.writeByte(106);
                  return;
               case SUB:
                  this.writer.writeByte(107);
                  return;
               case MUL:
                  this.writer.writeByte(108);
                  return;
               case DIV_SIGNED:
                  this.writer.writeByte(109);
                  return;
               case DIV_UNSIGNED:
                  this.writer.writeByte(110);
                  return;
               case REM_SIGNED:
                  this.writer.writeByte(111);
                  return;
               case REM_UNSIGNED:
                  this.writer.writeByte(112);
                  return;
               case AND:
                  this.writer.writeByte(113);
                  return;
               case OR:
                  this.writer.writeByte(114);
                  return;
               case XOR:
                  this.writer.writeByte(115);
                  return;
               case SHL:
                  this.writer.writeByte(116);
                  return;
               case SHR_SIGNED:
                  this.writer.writeByte(117);
                  return;
               case SHR_UNSIGNED:
                  this.writer.writeByte(118);
                  return;
               case ROTL:
                  this.writer.writeByte(119);
                  return;
               case ROTR:
                  this.writer.writeByte(120);
                  return;
               case EQ:
                  this.writer.writeByte(70);
                  return;
               case NE:
                  this.writer.writeByte(71);
                  return;
               case LT_SIGNED:
                  this.writer.writeByte(72);
                  return;
               case LT_UNSIGNED:
                  this.writer.writeByte(73);
                  return;
               case GT_SIGNED:
                  this.writer.writeByte(74);
                  return;
               case GT_UNSIGNED:
                  this.writer.writeByte(75);
                  return;
               case LE_SIGNED:
                  this.writer.writeByte(76);
                  return;
               case LE_UNSIGNED:
                  this.writer.writeByte(77);
                  return;
               case GE_SIGNED:
                  this.writer.writeByte(78);
                  return;
               case GE_UNSIGNED:
                  this.writer.writeByte(79);
                  return;
               default:
                  return;
            }
         case INT64:
            switch (instruction.getOperation()) {
               case ADD:
                  this.writer.writeByte(124);
                  break;
               case SUB:
                  this.writer.writeByte(125);
                  break;
               case MUL:
                  this.writer.writeByte(126);
                  break;
               case DIV_SIGNED:
                  this.writer.writeByte(127);
                  break;
               case DIV_UNSIGNED:
                  this.writer.writeByte(128);
                  break;
               case REM_SIGNED:
                  this.writer.writeByte(129);
                  break;
               case REM_UNSIGNED:
                  this.writer.writeByte(130);
                  break;
               case AND:
                  this.writer.writeByte(131);
                  break;
               case OR:
                  this.writer.writeByte(132);
                  break;
               case XOR:
                  this.writer.writeByte(133);
                  break;
               case SHL:
                  this.writer.writeByte(134);
                  break;
               case SHR_SIGNED:
                  this.writer.writeByte(135);
                  break;
               case SHR_UNSIGNED:
                  this.writer.writeByte(136);
                  break;
               case ROTL:
                  this.writer.writeByte(137);
                  break;
               case ROTR:
                  this.writer.writeByte(138);
                  break;
               case EQ:
                  this.writer.writeByte(81);
                  break;
               case NE:
                  this.writer.writeByte(82);
                  break;
               case LT_SIGNED:
                  this.writer.writeByte(83);
                  break;
               case LT_UNSIGNED:
                  this.writer.writeByte(84);
                  break;
               case GT_SIGNED:
                  this.writer.writeByte(85);
                  break;
               case GT_UNSIGNED:
                  this.writer.writeByte(86);
                  break;
               case LE_SIGNED:
                  this.writer.writeByte(87);
                  break;
               case LE_UNSIGNED:
                  this.writer.writeByte(88);
                  break;
               case GE_SIGNED:
                  this.writer.writeByte(89);
                  break;
               case GE_UNSIGNED:
                  this.writer.writeByte(90);
            }
      }
   }

   @Override
   public void visit(WasmFloatBinary instruction) {
      this.emitLocation(instruction);
      switch (instruction.getType()) {
         case FLOAT32:
            switch (instruction.getOperation()) {
               case ADD:
                  this.writer.writeByte(146);
                  return;
               case SUB:
                  this.writer.writeByte(147);
                  return;
               case MUL:
                  this.writer.writeByte(148);
                  return;
               case DIV:
                  this.writer.writeByte(149);
                  return;
               case MIN:
                  this.writer.writeByte(150);
                  return;
               case MAX:
                  this.writer.writeByte(151);
                  return;
               case EQ:
                  this.writer.writeByte(91);
                  return;
               case NE:
                  this.writer.writeByte(92);
                  return;
               case LT:
                  this.writer.writeByte(93);
                  return;
               case GT:
                  this.writer.writeByte(94);
                  return;
               case LE:
                  this.writer.writeByte(95);
                  return;
               case GE:
                  this.writer.writeByte(96);
                  return;
               default:
                  return;
            }
         case FLOAT64:
            switch (instruction.getOperation()) {
               case ADD:
                  this.writer.writeByte(160);
                  break;
               case SUB:
                  this.writer.writeByte(161);
                  break;
               case MUL:
                  this.writer.writeByte(162);
                  break;
               case DIV:
                  this.writer.writeByte(163);
                  break;
               case MIN:
                  this.writer.writeByte(164);
                  break;
               case MAX:
                  this.writer.writeByte(165);
                  break;
               case EQ:
                  this.writer.writeByte(97);
                  break;
               case NE:
                  this.writer.writeByte(98);
                  break;
               case LT:
                  this.writer.writeByte(99);
                  break;
               case GT:
                  this.writer.writeByte(100);
                  break;
               case LE:
                  this.writer.writeByte(101);
                  break;
               case GE:
                  this.writer.writeByte(102);
            }
      }
   }

   @Override
   public void visit(WasmIntUnary instruction) {
      this.emitLocation(instruction);
      switch (instruction.getType()) {
         case INT32:
            switch (instruction.getOperation()) {
               case EQZ:
                  this.writer.writeByte(69);
                  return;
               case CLZ:
                  this.writer.writeByte(103);
                  return;
               case CTZ:
                  this.writer.writeByte(104);
                  return;
               case POPCNT:
                  this.writer.writeByte(105);
                  return;
               default:
                  return;
            }
         case INT64:
            switch (instruction.getOperation()) {
               case EQZ:
                  this.writer.writeByte(80);
                  break;
               case CLZ:
                  this.writer.writeByte(121);
                  break;
               case CTZ:
                  this.writer.writeByte(122);
                  break;
               case POPCNT:
                  this.writer.writeByte(123);
            }
      }
   }

   @Override
   public void visit(WasmFloatUnary instruction) {
      this.emitLocation(instruction);
      switch (instruction.getType()) {
         case FLOAT32:
            switch (instruction.getOperation()) {
               case ABS:
                  this.writer.writeByte(139);
                  return;
               case NEG:
                  this.writer.writeByte(140);
                  return;
               case CEIL:
                  this.writer.writeByte(141);
                  return;
               case FLOOR:
                  this.writer.writeByte(142);
                  return;
               case TRUNC:
                  this.writer.writeByte(143);
                  return;
               case NEAREST:
                  this.writer.writeByte(144);
                  return;
               case SQRT:
                  this.writer.writeByte(145);
                  return;
               case COPYSIGN:
                  this.writer.writeByte(152);
                  return;
               default:
                  return;
            }
         case FLOAT64:
            switch (instruction.getOperation()) {
               case ABS:
                  this.writer.writeByte(153);
                  break;
               case NEG:
                  this.writer.writeByte(154);
                  break;
               case CEIL:
                  this.writer.writeByte(155);
                  break;
               case FLOOR:
                  this.writer.writeByte(156);
                  break;
               case TRUNC:
                  this.writer.writeByte(157);
                  break;
               case NEAREST:
                  this.writer.writeByte(158);
                  break;
               case SQRT:
                  this.writer.writeByte(159);
                  break;
               case COPYSIGN:
                  this.writer.writeByte(166);
            }
      }
   }

   @Override
   public void visit(WasmConversion instruction) {
      this.emitLocation(instruction);
      switch (instruction.getSourceType()) {
         case INT32:
            switch (instruction.getTargetType()) {
               case INT32:
               default:
                  return;
               case INT64:
                  this.writer.writeByte(instruction.isSigned() ? 172 : 173);
                  return;
               case FLOAT32:
                  if (instruction.isReinterpret()) {
                     this.writer.writeByte(190);
                  } else {
                     this.writer.writeByte(instruction.isSigned() ? 178 : 179);
                  }

                  return;
               case FLOAT64:
                  this.writer.writeByte(instruction.isSigned() ? 183 : 184);
                  return;
            }
         case INT64:
            switch (instruction.getTargetType()) {
               case INT32:
                  this.writer.writeByte(167);
                  return;
               case INT64:
               default:
                  return;
               case FLOAT32:
                  this.writer.writeByte(instruction.isSigned() ? 180 : 181);
                  return;
               case FLOAT64:
                  if (instruction.isReinterpret()) {
                     this.writer.writeByte(191);
                  } else {
                     this.writer.writeByte(instruction.isSigned() ? 185 : 186);
                  }

                  return;
            }
         case FLOAT32:
            switch (instruction.getTargetType()) {
               case INT32:
                  if (instruction.isReinterpret()) {
                     this.writer.writeByte(188);
                     return;
                  } else {
                     if (instruction.isNonTrapping()) {
                        this.writer.writeByte(252);
                        this.writer.writeByte(instruction.isSigned() ? 0 : 1);
                     } else {
                        this.writer.writeByte(instruction.isSigned() ? 168 : 169);
                     }

                     return;
                  }
               case INT64:
                  if (instruction.isNonTrapping()) {
                     this.writer.writeByte(252);
                     this.writer.writeByte(instruction.isSigned() ? 4 : 5);
                  } else {
                     this.writer.writeByte(instruction.isSigned() ? 174 : 175);
                  }

                  return;
               case FLOAT32:
               default:
                  return;
               case FLOAT64:
                  this.writer.writeByte(187);
                  return;
            }
         case FLOAT64:
            switch (instruction.getTargetType()) {
               case INT32:
                  if (instruction.isNonTrapping()) {
                     this.writer.writeByte(252);
                     this.writer.writeByte(instruction.isSigned() ? 2 : 3);
                  } else {
                     this.writer.writeByte(instruction.isSigned() ? 170 : 171);
                  }
                  break;
               case INT64:
                  if (instruction.isReinterpret()) {
                     this.writer.writeByte(189);
                  } else if (instruction.isNonTrapping()) {
                     this.writer.writeByte(252);
                     this.writer.writeByte(instruction.isSigned() ? 6 : 7);
                  } else {
                     this.writer.writeByte(instruction.isSigned() ? 176 : 177);
                  }
                  break;
               case FLOAT32:
                  this.writer.writeByte(182);
               case FLOAT64:
            }
      }
   }

   @Override
   public void visit(WasmCall instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(16);
      this.writer.writeLEB(this.module.functions.indexOf(instruction.getFunction()));
   }

   @Override
   public void visit(WasmIndirectCall instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(17);
      this.writer.writeLEB(this.module.types.indexOf(instruction.getType()));
      this.writer.writeByte(0);
   }

   @Override
   public void visit(WasmCallReference instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(20);
      this.writer.writeLEB(this.module.types.indexOf(instruction.getType()));
   }

   @Override
   public void visit(WasmDrop instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(26);
   }

   @Override
   public void visit(WasmLoadInt32 instruction) {
      this.emitLocation(instruction);
      switch (instruction.getConvertFrom()) {
         case INT8:
            this.writer.writeByte(44);
            break;
         case UINT8:
            this.writer.writeByte(45);
            break;
         case INT16:
            this.writer.writeByte(46);
            break;
         case UINT16:
            this.writer.writeByte(47);
            break;
         case INT32:
            this.writer.writeByte(40);
      }

      this.writer.writeByte(this.alignment(instruction.getAlignment()));
      this.writer.writeLEB(instruction.getOffset());
   }

   @Override
   public void visit(WasmLoadInt64 instruction) {
      this.emitLocation(instruction);
      switch (instruction.getConvertFrom()) {
         case INT8:
            this.writer.writeByte(48);
            break;
         case UINT8:
            this.writer.writeByte(49);
            break;
         case INT16:
            this.writer.writeByte(50);
            break;
         case UINT16:
            this.writer.writeByte(51);
            break;
         case INT32:
            this.writer.writeByte(52);
            break;
         case UINT32:
            this.writer.writeByte(53);
            break;
         case INT64:
            this.writer.writeByte(41);
      }

      this.writer.writeByte(this.alignment(instruction.getAlignment()));
      this.writer.writeLEB(instruction.getOffset());
   }

   @Override
   public void visit(WasmLoadFloat32 instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(42);
      this.writer.writeByte(this.alignment(instruction.getAlignment()));
      this.writer.writeLEB(instruction.getOffset());
   }

   @Override
   public void visit(WasmLoadFloat64 instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(43);
      this.writer.writeByte(this.alignment(instruction.getAlignment()));
      this.writer.writeLEB(instruction.getOffset());
   }

   @Override
   public void visit(WasmStoreInt32 instruction) {
      this.emitLocation(instruction);
      switch (instruction.getConvertTo()) {
         case INT8:
         case UINT8:
            this.writer.writeByte(58);
            break;
         case INT16:
         case UINT16:
            this.writer.writeByte(59);
            break;
         case INT32:
            this.writer.writeByte(54);
      }

      this.writer.writeByte(this.alignment(instruction.getAlignment()));
      this.writer.writeLEB(instruction.getOffset());
   }

   @Override
   public void visit(WasmStoreInt64 instruction) {
      this.emitLocation(instruction);
      switch (instruction.getConvertTo()) {
         case INT8:
         case UINT8:
            this.writer.writeByte(60);
            break;
         case INT16:
         case UINT16:
            this.writer.writeByte(61);
            break;
         case INT32:
         case UINT32:
            this.writer.writeByte(62);
            break;
         case INT64:
            this.writer.writeByte(55);
      }

      this.writer.writeByte(this.alignment(instruction.getAlignment()));
      this.writer.writeLEB(instruction.getOffset());
   }

   @Override
   public void visit(WasmStoreFloat32 instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(56);
      this.writer.writeByte(this.alignment(instruction.getAlignment()));
      this.writer.writeLEB(instruction.getOffset());
   }

   @Override
   public void visit(WasmStoreFloat64 instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(57);
      this.writer.writeByte(this.alignment(instruction.getAlignment()));
      this.writer.writeLEB(instruction.getOffset());
   }

   @Override
   public void visit(WasmMemoryGrow instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(64);
      this.writer.writeByte(0);
   }

   @Override
   public void visit(WasmFill instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(252);
      this.writer.writeLEB(11);
      this.writer.writeByte(0);
   }

   @Override
   public void visit(WasmCopy instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(252);
      this.writer.writeLEB(10);
      this.writer.writeByte(0);
      this.writer.writeByte(0);
   }

   @Override
   public void visit(WasmTry instruction) {
      this.writer.writeByte(31);
      this.writer.writeType(instruction.getType(), this.module);
      this.writer.writeLEB(instruction.getCatches().size());

      for (WasmCatchClause catchClause : instruction.getCatches()) {
         if (catchClause.getTag() != null) {
            if (!catchClause.isRef()) {
               this.writer.writeByte(0);
            } else {
               this.writer.writeByte(1);
            }

            this.writer.writeLEB(catchClause.getTag().getIndex());
         } else if (!catchClause.isRef()) {
            this.writer.writeByte(2);
         } else {
            this.writer.writeByte(3);
         }

         this.writeLabel(instruction, catchClause.getTarget());
      }

      this.depth++;
      this.blockDepths.put(instruction, this.depth);
      this.render(instruction.getBody());
      this.blockDepths.remove(instruction);
      this.depth--;
      this.writer.writeByte(11);
   }

   @Override
   public void visit(WasmThrow instruction) {
      this.emitLocation(instruction);
      if (instruction.getTag() != null) {
         this.writer.writeByte(8);
         this.writer.writeLEB(instruction.getTag().getIndex());
      } else {
         this.writer.writeByte(10);
      }
   }

   @Override
   public void visit(WasmReferencesEqual instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(211);
   }

   @Override
   public void visit(WasmCast instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      this.writer.writeByte(instruction.getTargetType().isNullable() ? 23 : 22);
      this.writer.writeHeapType(instruction.getTargetType(), this.module);
   }

   @Override
   public void visit(WasmTest instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      this.writer.writeByte(instruction.getTestType().isNullable() ? 21 : 20);
      this.writer.writeHeapType(instruction.getTestType(), this.module);
   }

   @Override
   public void visit(WasmExternConversion instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      switch (instruction.getType()) {
         case EXTERN_TO_ANY:
            this.writer.writeByte(26);
            break;
         case ANY_TO_EXTERN:
            this.writer.writeByte(27);
      }
   }

   @Override
   public void visit(WasmStructNew instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      this.writer.writeByte(0);
      this.writer.writeLEB(this.module.types.indexOf(instruction.getType()));
   }

   @Override
   public void visit(WasmStructNewDefault instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      this.writer.writeByte(1);
      this.writer.writeLEB(this.module.types.indexOf(instruction.getType()));
   }

   @Override
   public void visit(WasmStructGet instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      if (instruction.getSignedType() == null) {
         this.writer.writeByte(2);
      } else {
         switch (instruction.getSignedType()) {
            case SIGNED:
               this.writer.writeByte(3);
               break;
            case UNSIGNED:
               this.writer.writeByte(4);
         }
      }

      this.writer.writeLEB(this.module.types.indexOf(instruction.getType()));
      this.writer.writeLEB(instruction.getFieldIndex());
   }

   @Override
   public void visit(WasmStructSet instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      this.writer.writeByte(5);
      this.writer.writeLEB(this.module.types.indexOf(instruction.getType()));
      this.writer.writeLEB(instruction.getFieldIndex());
   }

   @Override
   public void visit(WasmArrayNewDefault instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      this.writer.writeByte(7);
      this.writer.writeLEB(this.module.types.indexOf(instruction.getType()));
   }

   @Override
   public void visit(WasmArrayNewFixed instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      this.writer.writeByte(8);
      this.writer.writeLEB(this.module.types.indexOf(instruction.getType()));
      this.writer.writeLEB(instruction.getSize());
   }

   @Override
   public void visit(WasmArrayGet instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      if (instruction.getSignedType() == null) {
         this.writer.writeByte(11);
      } else {
         switch (instruction.getSignedType()) {
            case SIGNED:
               this.writer.writeByte(12);
               break;
            case UNSIGNED:
               this.writer.writeByte(13);
         }
      }

      this.writer.writeLEB(this.module.types.indexOf(instruction.getType()));
   }

   @Override
   public void visit(WasmArraySet instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      this.writer.writeByte(14);
      this.writer.writeLEB(this.module.types.indexOf(instruction.getType()));
   }

   @Override
   public void visit(WasmArrayLength instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      this.writer.writeByte(15);
   }

   @Override
   public void visit(WasmArrayCopy instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      this.writer.writeByte(17);
      this.writer.writeLEB(this.module.types.indexOf(instruction.getTargetArrayType()));
      this.writer.writeLEB(this.module.types.indexOf(instruction.getSourceArrayType()));
   }

   @Override
   public void visit(WasmFunctionReference instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(210);
      this.writer.writeLEB(this.module.functions.indexOf(instruction.getFunction()));
   }

   @Override
   public void visit(WasmInt31Reference instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      this.writer.writeByte(28);
   }

   @Override
   public void visit(WasmInt31Get instruction) {
      this.emitLocation(instruction);
      this.writer.writeByte(251);
      this.writer.writeByte(instruction.getSignedType() == WasmSignedType.SIGNED ? 29 : 30);
   }

   private void writeBlockType(WasmBlockType type) {
      if (type == null) {
         this.writer.writeType(null, this.module);
      } else if (type instanceof WasmBlockType.Function) {
         WasmFunctionType functionType = ((WasmBlockType.Function)type).ref;
         this.writer.writeSignedLEB(this.module.types.indexOf(functionType));
      } else {
         WasmType valueType = ((WasmBlockType.Value)type).type;
         this.writer.writeType(valueType, this.module);
      }
   }

   private void writeLabel(WasmInstruction source, WasmInstructionList target) {
      Integer targetDepth = this.blockDepths.get(target.getBreakTarget());
      if (targetDepth != null) {
         this.writer.writeLEB(this.depth - targetDepth);
      } else {
         StringBuilder sb = new StringBuilder("Instruction (path to root):\n");

         for (WasmInstruction insn = source; insn != null; insn = insn.getOwner().getBreakTarget()) {
            sb.append("- ").append(insn).append("\n");
         }

         sb.append("Target (path to root):\n");

         for (WasmInstruction var6 = target.getBreakTarget(); var6 != null; var6 = var6.getOwner().getBreakTarget()) {
            sb.append("- ").append(var6).append("\n");
         }

         throw new IllegalStateException("Invalid break target for instruction\n" + sb);
      }
   }

   private int alignment(int value) {
      return 31 - Integer.numberOfLeadingZeros(Math.max(1, value));
   }

   private void emitLocation(WasmInstruction instruction) {
      if (!Objects.equals(instruction.getLocation(), this.lastEmittedLocation)) {
         this.lastEmittedLocation = instruction.getLocation();
         this.doEmitLocation();
      }
   }

   private void doEmitLocation() {
      int address = this.writer.getPosition() + this.addressOffset;
      if (this.dwarfGenerator != null) {
         if (this.lastEmittedLocation != null && this.lastEmittedLocation.getFileName() != null) {
            this.dwarfGenerator.lineNumber(address, this.lastEmittedLocation.getFileName(), this.lastEmittedLocation.getLine());
         } else {
            this.dwarfGenerator.endLineNumberSequence(address);
         }
      }

      if (this.debugLines != null) {
         this.debugLines.advance(address);
         TextLocation loc = this.lastEmittedLocation;

         for (InliningInfo inlining = loc != null ? loc.getInlining() : null; inlining != null; inlining = inlining.getParent()) {
            this.currentMethodStack.add(inlining);
         }

         Collections.reverse(this.currentMethodStack);
         int commonPart = 0;

         while (
            commonPart < this.currentMethodStack.size()
               && commonPart < this.methodStack.size()
               && this.currentMethodStack.get(commonPart) == this.methodStack.get(commonPart)
         ) {
            commonPart++;
         }

         while (this.methodStack.size() > commonPart) {
            this.debugLines.end();
            this.methodStack.remove(this.methodStack.size() - 1);
         }

         while (commonPart < this.currentMethodStack.size()) {
            InliningInfo method = this.currentMethodStack.get(commonPart++);
            this.methodStack.add(method);
            this.debugLines.location(method.getFileName(), method.getLine());
            this.debugLines.start(method.getMethod());
         }

         this.currentMethodStack.clear();
         if (loc != null) {
            this.debugLines.location(loc.getFileName(), loc.getLine());
         } else {
            this.debugLines.emptyLocation();
         }
      }
   }
}
