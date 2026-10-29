package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.CachedValue;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.reflection.FieldInfoStruct;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.model.ValueType;

public class FieldInfoIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public FieldInfoIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      FieldInfoStruct infoStruct = this.classInfoProvider.reflectionTypes().fieldInfo();
      switch (invocation.getMethod().getName()) {
         case "name":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.nameIndex());
            break;
         case "modifiers":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.modifiersIndex());
            break;
         case "type":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.typeIndex());
            break;
         case "read":
            context.generate(builder, invocation.getArguments().get(0));
            CachedValue cachedFieldInfo = context.valueCache().create(infoStruct.structure().getReference(), builder);
            builder.structGet(infoStruct.structure(), infoStruct.readerIndex());
            context.generate(builder, invocation.getArguments().get(1));
            cachedFieldInfo.emit(builder);
            builder.structGet(infoStruct.structure(), infoStruct.readerConverterIndex()).callReference(infoStruct.readerConverterType());
            cachedFieldInfo.release();
            break;
         case "readAsBoolean":
         case "readAsByte":
         case "readAsShort":
         case "readAsChar":
         case "readAsInt":
            this.generatePrimitiveRead(invocation, context, builder, WasmType.INT32);
            break;
         case "readAsLong":
            this.generatePrimitiveRead(invocation, context, builder, WasmType.INT64);
            break;
         case "readAsFloat":
            this.generatePrimitiveRead(invocation, context, builder, WasmType.FLOAT32);
            break;
         case "readAsDouble":
            this.generatePrimitiveRead(invocation, context, builder, WasmType.FLOAT64);
            break;
         case "write":
            this.generateWrite(invocation, context, builder);
            break;
         case "reflection":
            context.generate(builder, invocation.getArguments().get(0));
            builder.structGet(infoStruct.structure(), infoStruct.reflectionIndex());
            break;
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }

   private void generatePrimitiveRead(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder, WasmType rawWasmType) {
      FieldInfoStruct infoStruct = this.classInfoProvider.reflectionTypes().fieldInfo();
      context.generate(builder, invocation.getArguments().get(1));
      context.generate(builder, invocation.getArguments().get(0));
      WasmFunctionType readerType = infoStruct.rawReaderFunctionType(rawWasmType);
      builder.structGet(infoStruct.structure(), infoStruct.readerIndex()).cast(readerType.getReference()).callReference(readerType);
   }

   private void generateWrite(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      FieldInfoStruct infoStruct = this.classInfoProvider.reflectionTypes().fieldInfo();
      ValueType valueParamType = invocation.getMethod().parameterType(1);
      if (valueParamType instanceof ValueType.Primitive) {
         WasmType.Number rawWasmType = switch (((ValueType.Primitive)valueParamType).getKind()) {
            case BOOLEAN, BYTE, SHORT, CHARACTER, INTEGER -> WasmType.INT32;
            case LONG -> WasmType.INT64;
            case FLOAT -> WasmType.FLOAT32;
            case DOUBLE -> WasmType.FLOAT64;
         };
         context.generate(builder, invocation.getArguments().get(1));
         context.generate(builder, invocation.getArguments().get(2));
         context.generate(builder, invocation.getArguments().get(0));
         WasmFunctionType writerType = infoStruct.rawWriterFunctionType(rawWasmType);
         builder.structGet(infoStruct.structure(), infoStruct.writerIndex()).cast(writerType.getReference()).callReference(writerType);
      } else {
         context.generate(builder, invocation.getArguments().get(0));
         CachedValue cachedFieldInfo = context.valueCache().create(infoStruct.structure().getReference(), builder);
         builder.structGet(infoStruct.structure(), infoStruct.writerIndex());
         context.generate(builder, invocation.getArguments().get(1));
         context.generate(builder, invocation.getArguments().get(2));
         cachedFieldInfo.emit(builder);
         builder.structGet(infoStruct.structure(), infoStruct.writerConverterIndex()).callReference(infoStruct.writerConverterType());
         cachedFieldInfo.release();
      }
   }
}
