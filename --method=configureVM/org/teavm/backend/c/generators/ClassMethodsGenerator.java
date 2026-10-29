package org.teavm.backend.c.generators;

import org.teavm.backend.c.generate.CodeGeneratorUtil;
import org.teavm.backend.c.generate.CodeWriter;
import org.teavm.interop.Address;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.runtime.Allocator;
import org.teavm.runtime.RuntimeClass;

public class ClassMethodsGenerator implements Generator {
   private static final MethodReference ALLOC_ARRAY_METHOD = new MethodReference(Allocator.class, "allocateArray", RuntimeClass.class, int.class, Address.class);

   @Override
   public boolean canHandle(MethodReference method) {
      if (!method.getClassName().equals("java.lang.Class")) {
         return false;
      }

      switch (method.getName()) {
         case "getDeclaredAnnotationsImpl":
         case "getReflectionStateC":
         case "setReflectionStateC":
         case "getDeclaredFieldsImpl":
         case "createMetadata":
         case "getTypeParametersImpl":
            return true;
         default:
            return false;
      }
   }

   @Override
   public void generate(GeneratorContext context, MethodReference method) {
      switch (method.getName()) {
         case "getDeclaredAnnotationsImpl":
            this.generateGetDeclaredAnnot(context);
            break;
         case "getDeclaredFieldsImpl":
            this.generateGetDeclaredFieldsImpl(context);
            break;
         case "getReflectionStateC":
            this.generateGetReflectionState(context);
            break;
         case "setReflectionStateC":
            this.generateSetReflectionState(context);
         case "createMetadata":
         default:
            break;
         case "getTypeParametersImpl":
            context.writer().print("NULL");
      }
   }

   private void generateGetDeclaredAnnot(GeneratorContext context) {
      ValueType.Object annotType = ValueType.object("java.lang.annotation.Annotation");
      context.importMethod(ALLOC_ARRAY_METHOD, true);
      CodeWriter writer = context.writer();
      context.includes().includePath("reflection-ext.h");
      writer.println("TeaVM_ClassReflection* reflect = teavm_class_reflect(" + context.parameterName(0) + ");");
      writer.println("if (reflect == NULL || reflect->annotationsRef == NULL) {").indent();
      writer.print("return ").print(context.names().forMethod(ALLOC_ARRAY_METHOD)).print("(");
      CodeGeneratorUtil.writeTypeReference(writer, context.mainContext(), context.includes(), ValueType.arrayOf(annotType));
      writer.print(", 0);").println();
      writer.outdent().println("}");
      writer.print("TeaVM_Array* array = ").print(context.names().forMethod(ALLOC_ARRAY_METHOD)).print("(");
      CodeGeneratorUtil.writeTypeReference(writer, context.mainContext(), context.includes(), ValueType.arrayOf(annotType));
      writer.print(", reflect->annotationCount);").println();
      writer.println("void** arrayData = TEAVM_ARRAY_DATA(array, void*);");
      writer.println("for (int32_t i = 0; i < reflect->annotationCount; ++i) {").indent();
      writer.println("arrayData[i] = reflect->annotationsRef[i];");
      writer.outdent().println("}");
      writer.println("return array;");
   }

   private void generateGetDeclaredFieldsImpl(GeneratorContext context) {
      CodeWriter writer = context.writer();
      context.includes().includePath("reflection-ext.h");
      writer.println("TeaVM_ClassReflection* reflect = teavm_class_reflect(" + context.parameterName(0) + ");");
      writer.println("return reflect != NULL ? reflect->fields : NULL;");
   }

   private void generateGetReflectionState(GeneratorContext context) {
      CodeWriter writer = context.writer();
      writer.println("return ((TeaVM_Class*) " + context.parameterName(0) + ")->reflectionState;");
   }

   private void generateSetReflectionState(GeneratorContext context) {
      CodeWriter writer = context.writer();
      writer.println("((TeaVM_Class*) " + context.parameterName(0) + ")->reflectionState = " + context.parameterName(1) + ";");
   }
}
