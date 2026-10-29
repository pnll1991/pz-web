package org.teavm.backend.wasm.parser;

import java.util.ArrayList;
import java.util.List;
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

public class CodeParser extends BaseSectionParser {
   private CodeListener codeListener;
   private final List<CodeParser.Block> blockStack = new ArrayList<>();

   public void setCodeListener(CodeListener codeListener) {
      this.codeListener = codeListener;
   }

   @Override
   protected void parseContent() {
      this.parseCode();
   }

   public boolean parseSingleExpression(WasmBinaryReader reader) {
      this.reader = reader;

      try {
         return this.parseExpressions();
      } finally {
         this.reader = reader;
      }
   }

   private void parseCode() {
      if (!this.parseExpressions()) {
         this.codeListener.error(this.blockStack.size());
         this.blockStack.clear();
      }
   }

   private boolean parseExpressions() {
      while (this.reader.data[this.reader.ptr] != 11) {
         if (!this.parseExpr()) {
            return false;
         }
      }

      return true;
   }

   private boolean parseExpr() {
      this.reportAddress();
      switch (this.reader.data[this.reader.ptr++] & 0xFF) {
         case 0:
            this.codeListener.opcode(Opcode.UNREACHABLE);
            break;
         case 1:
            this.codeListener.opcode(Opcode.NOP);
            break;
         case 2:
            return this.parseBlock(false);
         case 3:
            return this.parseBlock(true);
         case 4:
            return this.parseConditional();
         case 5:
         case 7:
         case 9:
         case 11:
         case 18:
         case 19:
         case 21:
         case 22:
         case 23:
         case 24:
         case 25:
         case 27:
         case 28:
         case 29:
         case 30:
         case 37:
         case 38:
         case 39:
         case 63:
         case 192:
         case 193:
         case 194:
         case 195:
         case 196:
         case 197:
         case 198:
         case 199:
         case 200:
         case 201:
         case 202:
         case 203:
         case 204:
         case 205:
         case 206:
         case 207:
         case 212:
         case 215:
         case 216:
         case 217:
         case 218:
         case 219:
         case 220:
         case 221:
         case 222:
         case 223:
         case 224:
         case 225:
         case 226:
         case 227:
         case 228:
         case 229:
         case 230:
         case 231:
         case 232:
         case 233:
         case 234:
         case 235:
         case 236:
         case 237:
         case 238:
         case 239:
         case 240:
         case 241:
         case 242:
         case 243:
         case 244:
         case 245:
         case 246:
         case 247:
         case 248:
         case 249:
         case 250:
         default:
            return false;
         case 6:
            return this.parseTryCatch();
         case 8:
            this.codeListener.throwInstruction(this.readLEB());
            break;
         case 10:
            this.codeListener.opcode(Opcode.THROW_REF);
            break;
         case 12:
            this.parseBranch(BranchOpcode.BR);
            break;
         case 13:
            this.parseBranch(BranchOpcode.BR_IF);
            break;
         case 14:
            this.parseTableBranch();
            break;
         case 15:
            this.codeListener.opcode(Opcode.RETURN);
            break;
         case 16:
            this.codeListener.call(this.readLEB());
            break;
         case 17:
            this.codeListener.indirectCall(this.readLEB(), this.readLEB());
            break;
         case 20:
            this.codeListener.callReference(this.readLEB());
            break;
         case 26:
            this.codeListener.opcode(Opcode.DROP);
            break;
         case 31:
            return this.parseTryTable();
         case 32:
            this.codeListener.local(LocalOpcode.GET, this.readLEB());
            break;
         case 33:
            this.codeListener.local(LocalOpcode.SET, this.readLEB());
            break;
         case 34:
            this.codeListener.local(LocalOpcode.TEE, this.readLEB());
            break;
         case 35:
            this.codeListener.getGlobal(this.readLEB());
            break;
         case 36:
            this.codeListener.setGlobal(this.readLEB());
            break;
         case 40:
            this.codeListener.loadInt32(WasmInt32Subtype.INT32, 1 << this.readLEB(), this.readLEB());
            break;
         case 41:
            this.codeListener.loadInt64(WasmInt64Subtype.INT64, 1 << this.readLEB(), this.readLEB());
            break;
         case 42:
            this.codeListener.loadFloat32(1 << this.readLEB(), this.readLEB());
            break;
         case 43:
            this.codeListener.loadFloat64(1 << this.readLEB(), this.readLEB());
            break;
         case 44:
            this.codeListener.loadInt32(WasmInt32Subtype.INT8, 1 << this.readLEB(), this.readLEB());
            break;
         case 45:
            this.codeListener.loadInt32(WasmInt32Subtype.UINT8, 1 << this.readLEB(), this.readLEB());
            break;
         case 46:
            this.codeListener.loadInt32(WasmInt32Subtype.INT16, 1 << this.readLEB(), this.readLEB());
            break;
         case 47:
            this.codeListener.loadInt32(WasmInt32Subtype.UINT16, 1 << this.readLEB(), this.readLEB());
            break;
         case 48:
            this.codeListener.loadInt64(WasmInt64Subtype.INT8, 1 << this.readLEB(), this.readLEB());
            break;
         case 49:
            this.codeListener.loadInt64(WasmInt64Subtype.UINT8, 1 << this.readLEB(), this.readLEB());
            break;
         case 50:
            this.codeListener.loadInt64(WasmInt64Subtype.INT16, 1 << this.readLEB(), this.readLEB());
            break;
         case 51:
            this.codeListener.loadInt64(WasmInt64Subtype.UINT16, 1 << this.readLEB(), this.readLEB());
            break;
         case 52:
            this.codeListener.loadInt64(WasmInt64Subtype.INT32, 1 << this.readLEB(), this.readLEB());
            break;
         case 53:
            this.codeListener.loadInt64(WasmInt64Subtype.UINT32, 1 << this.readLEB(), this.readLEB());
            break;
         case 54:
            this.codeListener.storeInt32(WasmInt32Subtype.INT32, 1 << this.readLEB(), this.readLEB());
            break;
         case 55:
            this.codeListener.storeInt64(WasmInt64Subtype.INT64, 1 << this.readLEB(), this.readLEB());
            break;
         case 56:
            this.codeListener.storeFloat32(1 << this.readLEB(), this.readLEB());
            break;
         case 57:
            this.codeListener.storeFloat64(1 << this.readLEB(), this.readLEB());
            break;
         case 58:
            this.codeListener.storeInt32(WasmInt32Subtype.INT8, 1 << this.readLEB(), this.readLEB());
            break;
         case 59:
            this.codeListener.storeInt32(WasmInt32Subtype.INT16, 1 << this.readLEB(), this.readLEB());
            break;
         case 60:
            this.codeListener.storeInt64(WasmInt64Subtype.INT8, 1 << this.readLEB(), this.readLEB());
            break;
         case 61:
            this.codeListener.storeInt64(WasmInt64Subtype.INT16, 1 << this.readLEB(), this.readLEB());
            break;
         case 62:
            this.codeListener.storeInt64(WasmInt64Subtype.INT32, 1 << this.readLEB(), this.readLEB());
            break;
         case 64:
            this.readLEB();
            this.codeListener.memoryGrow();
            break;
         case 65:
            this.codeListener.int32Constant(this.readSignedLEB());
            break;
         case 66:
            this.codeListener.int64Constant(this.readSignedLongLEB());
            break;
         case 67:
            this.codeListener.float32Constant(Float.intBitsToFloat(this.readFixedInt()));
            break;
         case 68:
            this.codeListener.float64Constant(Double.longBitsToDouble(this.readFixedLong()));
            break;
         case 69:
            this.codeListener.unary(WasmIntUnaryOperation.EQZ, WasmIntType.INT32);
            break;
         case 70:
            this.codeListener.binary(WasmIntBinaryOperation.EQ, WasmIntType.INT32);
            break;
         case 71:
            this.codeListener.binary(WasmIntBinaryOperation.NE, WasmIntType.INT32);
            break;
         case 72:
            this.codeListener.binary(WasmIntBinaryOperation.LT_SIGNED, WasmIntType.INT32);
            break;
         case 73:
            this.codeListener.binary(WasmIntBinaryOperation.LT_UNSIGNED, WasmIntType.INT32);
            break;
         case 74:
            this.codeListener.binary(WasmIntBinaryOperation.GT_SIGNED, WasmIntType.INT32);
            break;
         case 75:
            this.codeListener.binary(WasmIntBinaryOperation.GT_UNSIGNED, WasmIntType.INT32);
            break;
         case 76:
            this.codeListener.binary(WasmIntBinaryOperation.LE_SIGNED, WasmIntType.INT32);
            break;
         case 77:
            this.codeListener.binary(WasmIntBinaryOperation.LE_UNSIGNED, WasmIntType.INT32);
            break;
         case 78:
            this.codeListener.binary(WasmIntBinaryOperation.GE_SIGNED, WasmIntType.INT32);
            break;
         case 79:
            this.codeListener.binary(WasmIntBinaryOperation.GE_UNSIGNED, WasmIntType.INT32);
            break;
         case 80:
            this.codeListener.unary(WasmIntUnaryOperation.EQZ, WasmIntType.INT64);
            break;
         case 81:
            this.codeListener.binary(WasmIntBinaryOperation.EQ, WasmIntType.INT64);
            break;
         case 82:
            this.codeListener.binary(WasmIntBinaryOperation.NE, WasmIntType.INT64);
            break;
         case 83:
            this.codeListener.binary(WasmIntBinaryOperation.LT_SIGNED, WasmIntType.INT64);
            break;
         case 84:
            this.codeListener.binary(WasmIntBinaryOperation.LT_UNSIGNED, WasmIntType.INT64);
            break;
         case 85:
            this.codeListener.binary(WasmIntBinaryOperation.GT_SIGNED, WasmIntType.INT64);
            break;
         case 86:
            this.codeListener.binary(WasmIntBinaryOperation.GT_UNSIGNED, WasmIntType.INT64);
            break;
         case 87:
            this.codeListener.binary(WasmIntBinaryOperation.LE_SIGNED, WasmIntType.INT64);
            break;
         case 88:
            this.codeListener.binary(WasmIntBinaryOperation.LE_UNSIGNED, WasmIntType.INT64);
            break;
         case 89:
            this.codeListener.binary(WasmIntBinaryOperation.GE_SIGNED, WasmIntType.INT64);
            break;
         case 90:
            this.codeListener.binary(WasmIntBinaryOperation.GE_UNSIGNED, WasmIntType.INT64);
            break;
         case 91:
            this.codeListener.binary(WasmFloatBinaryOperation.EQ, WasmFloatType.FLOAT32);
            break;
         case 92:
            this.codeListener.binary(WasmFloatBinaryOperation.NE, WasmFloatType.FLOAT32);
            break;
         case 93:
            this.codeListener.binary(WasmFloatBinaryOperation.LT, WasmFloatType.FLOAT32);
            break;
         case 94:
            this.codeListener.binary(WasmFloatBinaryOperation.GT, WasmFloatType.FLOAT32);
            break;
         case 95:
            this.codeListener.binary(WasmFloatBinaryOperation.LE, WasmFloatType.FLOAT32);
            break;
         case 96:
            this.codeListener.binary(WasmFloatBinaryOperation.GE, WasmFloatType.FLOAT32);
            break;
         case 97:
            this.codeListener.binary(WasmFloatBinaryOperation.EQ, WasmFloatType.FLOAT64);
            break;
         case 98:
            this.codeListener.binary(WasmFloatBinaryOperation.NE, WasmFloatType.FLOAT64);
            break;
         case 99:
            this.codeListener.binary(WasmFloatBinaryOperation.LT, WasmFloatType.FLOAT64);
            break;
         case 100:
            this.codeListener.binary(WasmFloatBinaryOperation.GT, WasmFloatType.FLOAT64);
            break;
         case 101:
            this.codeListener.binary(WasmFloatBinaryOperation.LE, WasmFloatType.FLOAT64);
            break;
         case 102:
            this.codeListener.binary(WasmFloatBinaryOperation.GE, WasmFloatType.FLOAT64);
            break;
         case 103:
            this.codeListener.unary(WasmIntUnaryOperation.CLZ, WasmIntType.INT32);
            break;
         case 104:
            this.codeListener.unary(WasmIntUnaryOperation.CTZ, WasmIntType.INT32);
            break;
         case 105:
            this.codeListener.unary(WasmIntUnaryOperation.POPCNT, WasmIntType.INT32);
            break;
         case 106:
            this.codeListener.binary(WasmIntBinaryOperation.ADD, WasmIntType.INT32);
            break;
         case 107:
            this.codeListener.binary(WasmIntBinaryOperation.SUB, WasmIntType.INT32);
            break;
         case 108:
            this.codeListener.binary(WasmIntBinaryOperation.MUL, WasmIntType.INT32);
            break;
         case 109:
            this.codeListener.binary(WasmIntBinaryOperation.DIV_SIGNED, WasmIntType.INT32);
            break;
         case 110:
            this.codeListener.binary(WasmIntBinaryOperation.DIV_UNSIGNED, WasmIntType.INT32);
            break;
         case 111:
            this.codeListener.binary(WasmIntBinaryOperation.REM_SIGNED, WasmIntType.INT32);
            break;
         case 112:
            this.codeListener.binary(WasmIntBinaryOperation.REM_UNSIGNED, WasmIntType.INT32);
            break;
         case 113:
            this.codeListener.binary(WasmIntBinaryOperation.AND, WasmIntType.INT32);
            break;
         case 114:
            this.codeListener.binary(WasmIntBinaryOperation.OR, WasmIntType.INT32);
            break;
         case 115:
            this.codeListener.binary(WasmIntBinaryOperation.XOR, WasmIntType.INT32);
            break;
         case 116:
            this.codeListener.binary(WasmIntBinaryOperation.SHL, WasmIntType.INT32);
            break;
         case 117:
            this.codeListener.binary(WasmIntBinaryOperation.SHR_SIGNED, WasmIntType.INT32);
            break;
         case 118:
            this.codeListener.binary(WasmIntBinaryOperation.SHR_UNSIGNED, WasmIntType.INT32);
            break;
         case 119:
            this.codeListener.binary(WasmIntBinaryOperation.ROTL, WasmIntType.INT32);
            break;
         case 120:
            this.codeListener.binary(WasmIntBinaryOperation.ROTR, WasmIntType.INT32);
            break;
         case 121:
            this.codeListener.unary(WasmIntUnaryOperation.CLZ, WasmIntType.INT64);
            break;
         case 122:
            this.codeListener.unary(WasmIntUnaryOperation.CTZ, WasmIntType.INT64);
            break;
         case 123:
            this.codeListener.unary(WasmIntUnaryOperation.POPCNT, WasmIntType.INT64);
            break;
         case 124:
            this.codeListener.binary(WasmIntBinaryOperation.ADD, WasmIntType.INT64);
            break;
         case 125:
            this.codeListener.binary(WasmIntBinaryOperation.SUB, WasmIntType.INT64);
            break;
         case 126:
            this.codeListener.binary(WasmIntBinaryOperation.MUL, WasmIntType.INT64);
            break;
         case 127:
            this.codeListener.binary(WasmIntBinaryOperation.DIV_SIGNED, WasmIntType.INT64);
            break;
         case 128:
            this.codeListener.binary(WasmIntBinaryOperation.DIV_UNSIGNED, WasmIntType.INT64);
            break;
         case 129:
            this.codeListener.binary(WasmIntBinaryOperation.REM_SIGNED, WasmIntType.INT64);
            break;
         case 130:
            this.codeListener.binary(WasmIntBinaryOperation.REM_UNSIGNED, WasmIntType.INT64);
            break;
         case 131:
            this.codeListener.binary(WasmIntBinaryOperation.AND, WasmIntType.INT64);
            break;
         case 132:
            this.codeListener.binary(WasmIntBinaryOperation.OR, WasmIntType.INT64);
            break;
         case 133:
            this.codeListener.binary(WasmIntBinaryOperation.XOR, WasmIntType.INT64);
            break;
         case 134:
            this.codeListener.binary(WasmIntBinaryOperation.SHL, WasmIntType.INT64);
            break;
         case 135:
            this.codeListener.binary(WasmIntBinaryOperation.SHR_UNSIGNED, WasmIntType.INT64);
            break;
         case 136:
            this.codeListener.binary(WasmIntBinaryOperation.SHR_UNSIGNED, WasmIntType.INT64);
            break;
         case 137:
            this.codeListener.binary(WasmIntBinaryOperation.ROTL, WasmIntType.INT64);
            break;
         case 138:
            this.codeListener.binary(WasmIntBinaryOperation.ROTR, WasmIntType.INT64);
            break;
         case 139:
            this.codeListener.unary(WasmFloatUnaryOperation.ABS, WasmFloatType.FLOAT32);
            break;
         case 140:
            this.codeListener.unary(WasmFloatUnaryOperation.NEG, WasmFloatType.FLOAT32);
            break;
         case 141:
            this.codeListener.unary(WasmFloatUnaryOperation.CEIL, WasmFloatType.FLOAT32);
            break;
         case 142:
            this.codeListener.unary(WasmFloatUnaryOperation.FLOOR, WasmFloatType.FLOAT32);
            break;
         case 143:
            this.codeListener.unary(WasmFloatUnaryOperation.TRUNC, WasmFloatType.FLOAT32);
            break;
         case 144:
            this.codeListener.unary(WasmFloatUnaryOperation.NEAREST, WasmFloatType.FLOAT32);
            break;
         case 145:
            this.codeListener.unary(WasmFloatUnaryOperation.SQRT, WasmFloatType.FLOAT32);
            break;
         case 146:
            this.codeListener.binary(WasmFloatBinaryOperation.ADD, WasmFloatType.FLOAT32);
            break;
         case 147:
            this.codeListener.binary(WasmFloatBinaryOperation.SUB, WasmFloatType.FLOAT32);
            break;
         case 148:
            this.codeListener.binary(WasmFloatBinaryOperation.MUL, WasmFloatType.FLOAT32);
            break;
         case 149:
            this.codeListener.binary(WasmFloatBinaryOperation.DIV, WasmFloatType.FLOAT32);
            break;
         case 150:
            this.codeListener.binary(WasmFloatBinaryOperation.MIN, WasmFloatType.FLOAT32);
            break;
         case 151:
            this.codeListener.binary(WasmFloatBinaryOperation.MAX, WasmFloatType.FLOAT32);
            break;
         case 152:
            this.codeListener.unary(WasmFloatUnaryOperation.COPYSIGN, WasmFloatType.FLOAT32);
            break;
         case 153:
            this.codeListener.unary(WasmFloatUnaryOperation.ABS, WasmFloatType.FLOAT64);
            break;
         case 154:
            this.codeListener.unary(WasmFloatUnaryOperation.NEG, WasmFloatType.FLOAT64);
            break;
         case 155:
            this.codeListener.unary(WasmFloatUnaryOperation.CEIL, WasmFloatType.FLOAT64);
            break;
         case 156:
            this.codeListener.unary(WasmFloatUnaryOperation.FLOOR, WasmFloatType.FLOAT64);
            break;
         case 157:
            this.codeListener.unary(WasmFloatUnaryOperation.TRUNC, WasmFloatType.FLOAT64);
            break;
         case 158:
            this.codeListener.unary(WasmFloatUnaryOperation.NEAREST, WasmFloatType.FLOAT64);
            break;
         case 159:
            this.codeListener.unary(WasmFloatUnaryOperation.SQRT, WasmFloatType.FLOAT64);
            break;
         case 160:
            this.codeListener.binary(WasmFloatBinaryOperation.ADD, WasmFloatType.FLOAT64);
            break;
         case 161:
            this.codeListener.binary(WasmFloatBinaryOperation.SUB, WasmFloatType.FLOAT64);
            break;
         case 162:
            this.codeListener.binary(WasmFloatBinaryOperation.MUL, WasmFloatType.FLOAT64);
            break;
         case 163:
            this.codeListener.binary(WasmFloatBinaryOperation.DIV, WasmFloatType.FLOAT64);
            break;
         case 164:
            this.codeListener.binary(WasmFloatBinaryOperation.MIN, WasmFloatType.FLOAT64);
            break;
         case 165:
            this.codeListener.binary(WasmFloatBinaryOperation.MAX, WasmFloatType.FLOAT64);
            break;
         case 166:
            this.codeListener.unary(WasmFloatUnaryOperation.COPYSIGN, WasmFloatType.FLOAT64);
            break;
         case 167:
            this.codeListener.convert(WasmNumType.INT64, WasmNumType.INT32, false, false, false);
            break;
         case 168:
            this.codeListener.convert(WasmNumType.FLOAT32, WasmNumType.INT32, false, false, false);
            break;
         case 169:
            this.codeListener.convert(WasmNumType.FLOAT32, WasmNumType.INT32, true, false, false);
            break;
         case 170:
            this.codeListener.convert(WasmNumType.FLOAT64, WasmNumType.INT32, false, false, false);
            break;
         case 171:
            this.codeListener.convert(WasmNumType.FLOAT64, WasmNumType.INT32, true, false, false);
            break;
         case 172:
            this.codeListener.convert(WasmNumType.INT32, WasmNumType.INT64, false, false, false);
            break;
         case 173:
            this.codeListener.convert(WasmNumType.INT32, WasmNumType.INT64, true, false, false);
            break;
         case 174:
            this.codeListener.convert(WasmNumType.FLOAT32, WasmNumType.INT64, false, false, false);
            break;
         case 175:
            this.codeListener.convert(WasmNumType.FLOAT32, WasmNumType.INT64, true, false, false);
            break;
         case 176:
            this.codeListener.convert(WasmNumType.FLOAT64, WasmNumType.INT64, false, false, false);
            break;
         case 177:
            this.codeListener.convert(WasmNumType.FLOAT64, WasmNumType.INT64, true, false, false);
            break;
         case 178:
            this.codeListener.convert(WasmNumType.INT32, WasmNumType.FLOAT32, false, false, false);
            break;
         case 179:
            this.codeListener.convert(WasmNumType.INT32, WasmNumType.FLOAT32, true, false, false);
            break;
         case 180:
            this.codeListener.convert(WasmNumType.INT64, WasmNumType.FLOAT32, false, false, false);
            break;
         case 181:
            this.codeListener.convert(WasmNumType.INT64, WasmNumType.FLOAT32, true, false, false);
            break;
         case 182:
            this.codeListener.convert(WasmNumType.FLOAT64, WasmNumType.FLOAT32, true, false, false);
            break;
         case 183:
            this.codeListener.convert(WasmNumType.INT32, WasmNumType.FLOAT64, false, false, false);
            break;
         case 184:
            this.codeListener.convert(WasmNumType.INT32, WasmNumType.FLOAT64, true, false, false);
            break;
         case 185:
            this.codeListener.convert(WasmNumType.INT64, WasmNumType.FLOAT64, false, false, false);
            break;
         case 186:
            this.codeListener.convert(WasmNumType.INT64, WasmNumType.FLOAT64, true, false, false);
            break;
         case 187:
            this.codeListener.convert(WasmNumType.FLOAT32, WasmNumType.FLOAT64, true, false, false);
            break;
         case 188:
            this.codeListener.convert(WasmNumType.FLOAT32, WasmNumType.INT32, false, true, false);
            break;
         case 189:
            this.codeListener.convert(WasmNumType.FLOAT64, WasmNumType.INT64, false, true, false);
            break;
         case 190:
            this.codeListener.convert(WasmNumType.INT32, WasmNumType.FLOAT32, false, true, false);
            break;
         case 191:
            this.codeListener.convert(WasmNumType.INT64, WasmNumType.FLOAT64, false, true, false);
            break;
         case 208:
            this.codeListener.nullConstant(this.reader.readHeapType(true));
            break;
         case 209:
            this.codeListener.opcode(Opcode.IS_NULL);
            break;
         case 210:
            this.codeListener.functionReference(this.readLEB());
            break;
         case 211:
            this.codeListener.opcode(Opcode.REF_EQ);
            break;
         case 213:
            this.parseBranch(BranchOpcode.BR_ON_NULL);
            break;
         case 214:
            this.parseBranch(BranchOpcode.BR_ON_NON_NULL);
            break;
         case 251:
            return this.parseExtExpr2();
         case 252:
            return this.parseExtExpr();
      }

      return true;
   }

   private boolean parseExtExpr() {
      switch (this.readLEB()) {
         case 0:
            this.codeListener.convert(WasmNumType.INT32, WasmNumType.FLOAT32, true, false, true);
            return true;
         case 1:
            this.codeListener.convert(WasmNumType.INT32, WasmNumType.FLOAT32, false, false, true);
            return true;
         case 2:
            this.codeListener.convert(WasmNumType.INT32, WasmNumType.FLOAT64, true, false, true);
            return true;
         case 3:
            this.codeListener.convert(WasmNumType.INT32, WasmNumType.FLOAT64, false, false, true);
            return true;
         case 4:
            this.codeListener.convert(WasmNumType.INT64, WasmNumType.FLOAT32, true, false, true);
            return true;
         case 5:
            this.codeListener.convert(WasmNumType.INT64, WasmNumType.FLOAT32, false, false, true);
            return true;
         case 6:
            this.codeListener.convert(WasmNumType.INT64, WasmNumType.FLOAT64, true, false, true);
            return true;
         case 7:
            this.codeListener.convert(WasmNumType.INT64, WasmNumType.FLOAT64, false, false, true);
            return true;
         case 8:
         case 9:
         default:
            return false;
         case 10:
            if (this.reader.data[this.reader.ptr++] == 0 && this.reader.data[this.reader.ptr++] == 0) {
               this.codeListener.memoryCopy();
               return true;
            }

            return false;
         case 11:
            if (this.reader.data[this.reader.ptr++] != 0) {
               return false;
            } else {
               this.codeListener.memoryFill();
               return true;
            }
      }
   }

   private boolean parseExtExpr2() {
      switch (this.readLEB()) {
         case 0:
            this.codeListener.structNew(this.readLEB());
            return true;
         case 1:
            this.codeListener.structNewDefault(this.readLEB());
            return true;
         case 2:
            this.codeListener.structGet(null, this.readLEB(), this.readLEB());
            return true;
         case 3:
            this.codeListener.structGet(WasmSignedType.SIGNED, this.readLEB(), this.readLEB());
            return true;
         case 4:
            this.codeListener.structGet(WasmSignedType.UNSIGNED, this.readLEB(), this.readLEB());
            return true;
         case 5:
            this.codeListener.structSet(this.readLEB(), this.readLEB());
            return true;
         case 6:
         case 9:
         case 10:
         case 16:
         case 18:
         case 19:
         default:
            return false;
         case 7:
            this.codeListener.arrayNewDefault(this.readLEB());
            return true;
         case 8:
            this.codeListener.arrayNewFixed(this.readLEB(), this.readLEB());
            return true;
         case 11:
            this.codeListener.arrayGet(null, this.readLEB());
            return true;
         case 12:
            this.codeListener.arrayGet(WasmSignedType.SIGNED, this.readLEB());
            return true;
         case 13:
            this.codeListener.arrayGet(WasmSignedType.UNSIGNED, this.readLEB());
            return true;
         case 14:
            this.codeListener.arraySet(this.readLEB());
            return true;
         case 15:
            this.codeListener.opcode(Opcode.ARRAY_LENGTH);
            return true;
         case 17:
            this.codeListener.arrayCopy(this.readLEB(), this.readLEB());
            return true;
         case 20:
            this.codeListener.test(this.reader.readHeapType(false));
            return true;
         case 21:
            this.codeListener.test(this.reader.readHeapType(true));
            return true;
         case 22:
            this.codeListener.cast(this.reader.readHeapType(false));
            return true;
         case 23:
            this.codeListener.cast(this.reader.readHeapType(true));
            return true;
         case 24:
            this.parseCastBranch(true);
            return true;
         case 25:
            this.parseCastBranch(false);
            return true;
         case 26:
            this.codeListener.opcode(Opcode.EXTERN_TO_ANY);
            return true;
         case 27:
            this.codeListener.opcode(Opcode.ANY_TO_EXTERN);
            return true;
         case 28:
            this.codeListener.int31Reference();
            return true;
         case 29:
            this.codeListener.int31Get(WasmSignedType.SIGNED);
            return true;
         case 30:
            this.codeListener.int31Get(WasmSignedType.UNSIGNED);
            return true;
      }
   }

   private boolean parseBlock(boolean isLoop) {
      WasmHollowBlockType type = this.reader.readBlockType();
      int token = this.codeListener.startBlock(isLoop, type);
      this.blockStack.add(new CodeParser.Block(token));
      if (!this.parseExpressions()) {
         return false;
      }

      this.blockStack.remove(this.blockStack.size() - 1);
      this.reportAddress();
      this.codeListener.endBlock(token, isLoop);
      this.reader.ptr++;
      return true;
   }

   private boolean parseTryTable() {
      WasmHollowBlockType type = this.reader.readBlockType();
      int token = this.codeListener.tryTable(type);
      int catchCount = this.reader.readLEB();

      for (int i = 0; i < catchCount; i++) {
         this.reportAddress();
         switch (this.reader.data[this.reader.ptr++]) {
            case 0: {
               int tag = this.readLEB();
               int depth = this.readLEB();
               int target = this.blockStack.get(this.blockStack.size() - depth - 1).token;
               this.codeListener.catchTag(tag, false, depth, target);
               break;
            }
            case 1: {
               int tag = this.readLEB();
               int depth = this.readLEB();
               int target = this.blockStack.get(this.blockStack.size() - depth - 1).token;
               this.codeListener.catchTag(tag, true, depth, target);
               break;
            }
            case 2: {
               int depth = this.readLEB();
               int target = this.blockStack.get(this.blockStack.size() - depth - 1).token;
               this.codeListener.catchAll(false, depth, target);
               break;
            }
            case 3: {
               int depth = this.readLEB();
               int target = this.blockStack.get(this.blockStack.size() - depth - 1).token;
               this.codeListener.catchAll(true, depth, target);
               break;
            }
            default:
               return false;
         }
      }

      this.blockStack.add(new CodeParser.Block(token));
      if (!this.parseExpressions()) {
         return false;
      }

      this.blockStack.remove(this.blockStack.size() - 1);
      this.reportAddress();
      this.codeListener.endBlock(token, false);
      this.reader.ptr++;
      return true;
   }

   private boolean parseConditional() {
      WasmHollowBlockType type = this.reader.readBlockType();
      int token = this.codeListener.startConditionalBlock(type);
      this.blockStack.add(new CodeParser.Block(token));
      boolean hasElse = false;

      while (true) {
         switch (this.reader.data[this.reader.ptr]) {
            case 5:
               if (hasElse) {
                  return false;
               }

               this.reportAddress();
               this.codeListener.startElseSection(this.blockStack.get(this.blockStack.size() - 1).token);
               this.reader.ptr++;
               break;
            case 11:
               this.blockStack.remove(this.blockStack.size() - 1);
               this.reportAddress();
               this.codeListener.endBlock(token, false);
               this.reader.ptr++;
               return true;
            default:
               if (!this.parseExpr()) {
                  return false;
               }
         }
      }
   }

   private boolean parseTryCatch() {
      WasmHollowType type = this.reader.readType();
      int token = this.codeListener.startTry(type);
      this.blockStack.add(new CodeParser.Block(token));

      while (true) {
         switch (this.reader.data[this.reader.ptr]) {
            case 7:
               this.reportAddress();
               int tagIndex = this.readLEB();
               this.reader.ptr++;
               this.codeListener.startCatch(tagIndex);
               break;
            case 11:
               this.blockStack.remove(this.blockStack.size() - 1);
               this.reportAddress();
               this.codeListener.endBlock(token, false);
               this.reader.ptr++;
               return true;
            default:
               if (!this.parseExpr()) {
                  return false;
               }
         }
      }
   }

   private void parseBranch(BranchOpcode opcode) {
      int depth = this.readLEB();
      CodeParser.Block target = this.blockStack.get(this.blockStack.size() - depth - 1);
      this.codeListener.branch(opcode, depth, target.token);
   }

   private void parseCastBranch(boolean success) {
      byte flags = this.reader.data[this.reader.ptr++];
      int depth = this.readLEB();
      CodeParser.Block target = this.blockStack.get(this.blockStack.size() - depth - 1);
      WasmHollowType.Reference sourceType = this.reader.readHeapType((flags & 1) != 0);
      WasmHollowType.Reference targetType = this.reader.readHeapType((flags & 2) != 0);
      this.codeListener.castBranch(success, depth, target.token, sourceType, targetType);
   }

   private void parseTableBranch() {
      int count = this.readLEB();
      int[] depths = new int[count];
      int[] targets = new int[count];

      for (int i = 0; i < count; i++) {
         int depth = this.readLEB();
         depths[i] = depth;
         targets[i] = this.blockStack.get(this.blockStack.size() - depth - 1).token;
      }

      int defaultDepth = this.readLEB();
      int defaultTarget = this.blockStack.get(this.blockStack.size() - defaultDepth - 1).token;
      this.codeListener.tableBranch(depths, targets, defaultDepth, defaultTarget);
   }

   private static class Block {
      int token;

      Block(int token) {
         this.token = token;
      }
   }
}
