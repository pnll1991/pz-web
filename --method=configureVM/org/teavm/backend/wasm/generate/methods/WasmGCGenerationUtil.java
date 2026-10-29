package org.teavm.backend.wasm.generate.methods;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfo;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.reflection.ClassInfoStruct;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmNullCondition;
import org.teavm.model.ValueType;

public final class WasmGCGenerationUtil {
   private WasmGCGenerationUtil() {
   }

   public static void allocateArray(
      WasmGCClassInfoProvider classInfoProvider, ValueType itemType, WasmInstructionBuilder builder, BiConsumer<WasmArray, WasmInstructionBuilder> data
   ) {
      ClassInfoStruct classInfoType = classInfoProvider.reflectionTypes().classInfo();
      WasmGCClassInfo classInfo = classInfoProvider.getClassInfo(ValueType.arrayOf(itemType));
      WasmType.CompositeReference wasmArrayType = (WasmType.CompositeReference)classInfo.getStructure().getFields().get(2).getUnpackedType();
      WasmArray wasmArray = (WasmArray)wasmArrayType.composite;
      int depth = 1;

      while (itemType instanceof ValueType.Array) {
         itemType = ((ValueType.Array)itemType).getItemType();
      }

      builder.getGlobal(classInfoProvider.getClassInfo(itemType).getPointer());

      for (int i = 0; i < depth; i++) {
         builder.call(classInfoProvider.getGetArrayClassFunction());
      }

      builder.structGet(classInfoType.structure(), classInfoType.vtableIndex());
      builder.nullConst(WasmType.EQ);
      data.accept(wasmArray, builder);
      builder.structNew(classInfo.getStructure());
   }

   public static void getArrayLengthOfNullable(WasmInstructionBuilder builder, Consumer<WasmInstructionBuilder> value) {
      WasmInstructionBuilder outerBlock = builder.block(WasmType.INT32);
      WasmInstructionBuilder innerBlock = outerBlock.block();
      value.accept(innerBlock);
      innerBlock.nullBranch(WasmNullCondition.NULL, innerBlock).arrayLength().breakTo(outerBlock);
      outerBlock.i32Const(0);
   }

   public static void emitClassInfoLiteral(WasmGCClassInfoProvider classInfoProvider, WasmInstructionBuilder builder, ValueType type) {
      int degree = 0;
      if (type instanceof ValueType.Array) {
         ValueType itemType = ((ValueType.Array)type).getItemType();
         if (!(itemType instanceof ValueType.Primitive)) {
            while (type instanceof ValueType.Array) {
               type = ((ValueType.Array)type).getItemType();
               degree++;
            }
         }
      }

      builder.getGlobal(classInfoProvider.getClassInfo(type).getPointer());

      while (degree-- > 0) {
         builder.call(classInfoProvider.getGetArrayClassFunction());
      }
   }

   public static void emitClassLiteral(WasmGCClassInfoProvider classInfoProvider, WasmInstructionBuilder builder, ValueType type) {
      emitClassInfoLiteral(classInfoProvider, builder, type);
      builder.call(classInfoProvider.reflectionTypes().classInfo().classObjectFunction());
   }
}
