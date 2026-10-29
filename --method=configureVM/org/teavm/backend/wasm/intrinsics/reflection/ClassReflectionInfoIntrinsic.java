package org.teavm.backend.wasm.intrinsics.reflection;

import java.util.function.ToIntFunction;
import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.methods.WasmGCGenerationUtil;
import org.teavm.backend.wasm.generate.reflection.ClassReflectionInfoStruct;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;

public class ClassReflectionInfoIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;

   public ClassReflectionInfoIntrinsic(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      switch (invocation.getMethod().getName()) {
         case "annotationCount":
            this.collectionCount(invocation, context, builder, ClassReflectionInfoStruct::annotationsIndex);
            break;
         case "annotation": {
            WasmArray array = this.classInfoProvider.reflectionTypes().annotationInfo().array();
            this.collectionElement(invocation, context, builder, array, ClassReflectionInfoStruct::annotationsIndex);
            break;
         }
         case "fieldCount":
            this.collectionCount(invocation, context, builder, ClassReflectionInfoStruct::fieldsIndex);
            break;
         case "field": {
            WasmArray array = this.classInfoProvider.reflectionTypes().fieldInfo().array();
            this.collectionElement(invocation, context, builder, array, ClassReflectionInfoStruct::fieldsIndex);
            break;
         }
         case "methodCount":
            this.collectionCount(invocation, context, builder, ClassReflectionInfoStruct::methodsIndex);
            break;
         case "method": {
            WasmArray array = this.classInfoProvider.reflectionTypes().methodInfo().array();
            this.collectionElement(invocation, context, builder, array, ClassReflectionInfoStruct::methodsIndex);
            break;
         }
         case "typeParameterCount":
            this.collectionCount(invocation, context, builder, ClassReflectionInfoStruct::typeParametersIndex);
            break;
         case "typeParameter": {
            WasmArray array = this.classInfoProvider.reflectionTypes().typeVariableInfo().array();
            this.collectionElement(invocation, context, builder, array, ClassReflectionInfoStruct::typeParametersIndex);
            break;
         }
         case "innerClassCount":
            this.collectionCount(invocation, context, builder, ClassReflectionInfoStruct::innerClassesIndex);
            break;
         case "innerClass": {
            WasmArray array = this.classInfoProvider.reflectionTypes().classInfo().array();
            this.collectionElement(invocation, context, builder, array, ClassReflectionInfoStruct::innerClassesIndex);
            break;
         }
         default:
            throw new IllegalArgumentException(invocation.getMethod().getName());
      }
   }

   private void collectionCount(
      InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder, ToIntFunction<ClassReflectionInfoStruct> fieldIndex
   ) {
      ClassReflectionInfoStruct infoStruct = this.classInfoProvider.reflectionTypes().classReflectionInfo();
      WasmGCGenerationUtil.getArrayLengthOfNullable(builder, b -> {
         context.generate(b, invocation.getArguments().get(0));
         b.structGet(infoStruct.structure(), fieldIndex.applyAsInt(infoStruct));
      });
   }

   private void collectionElement(
      InvocationExpr invocation,
      WasmGCInlineIntrinsicContext context,
      WasmInstructionBuilder builder,
      WasmArray array,
      ToIntFunction<ClassReflectionInfoStruct> fieldIndex
   ) {
      ClassReflectionInfoStruct infoStruct = this.classInfoProvider.reflectionTypes().classReflectionInfo();
      context.generate(builder, invocation.getArguments().get(0));
      builder.structGet(infoStruct.structure(), fieldIndex.applyAsInt(infoStruct));
      context.generate(builder, invocation.getArguments().get(1));
      builder.arrayGet(array);
   }
}
