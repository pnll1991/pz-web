package org.teavm.backend.wasm.intrinsics.reflection;

import org.teavm.ast.InvocationExpr;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.reflection.AnnotationDataStruct;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsicContext;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmSignedType;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.ValueType;

public class AnnotationDataIntrinsic implements WasmGCInlineIntrinsic {
   private final WasmGCClassInfoProvider classInfoProvider;
   private final ListableClassReaderSource classes;
   private final String annotationClassName;

   public AnnotationDataIntrinsic(WasmGCClassInfoProvider classInfoProvider, ListableClassReaderSource classes, String annotationClassName) {
      this.classInfoProvider = classInfoProvider;
      this.classes = classes;
      this.annotationClassName = annotationClassName;
   }

   @Override
   public void apply(InvocationExpr invocation, WasmGCInlineIntrinsicContext context, WasmInstructionBuilder builder) {
      AnnotationDataStruct dataStruct = this.classInfoProvider.reflectionTypes().annotationData(this.annotationClassName);
      if (invocation.getMethod().getName().equals("constructor") && invocation.getArguments().isEmpty()) {
         WasmFunction fn = dataStruct.constructor();
         fn.setReferenced(true);
         builder.funcRef(fn);
      } else {
         AnnotationDataStruct.Field field = dataStruct.field(invocation.getMethod().getName());
         context.generate(builder, invocation.getArguments().get(0));
         WasmSignedType signedType = null;
         if (field.type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive)field.type).getKind()) {
               case BOOLEAN:
               case BYTE:
               case SHORT:
                  signedType = WasmSignedType.SIGNED;
                  break;
               case CHARACTER:
                  signedType = WasmSignedType.UNSIGNED;
            }
         } else if (field.type instanceof ValueType.Object) {
            ClassReader fieldCls = this.classes.get(((ValueType.Object)field.type).getClassName());
            if (fieldCls != null && fieldCls.hasModifier(ElementModifier.ENUM)) {
               signedType = WasmSignedType.SIGNED;
            }
         }

         builder.structGet(dataStruct.structure(), field.index, signedType);
      }
   }
}
