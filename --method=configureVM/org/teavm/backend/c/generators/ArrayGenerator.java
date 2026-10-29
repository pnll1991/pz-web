package org.teavm.backend.c.generators;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashSet;
import org.teavm.backend.c.generate.CodeWriter;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.dependency.ValueDependencyInfo;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class ArrayGenerator implements Generator {
   private static final int[] primitives = new int[]{2, 3, 4, 5, 6, 7, 8, 1};
   private static final String[] primitiveWrappers = new String[]{"Byte", "Short", "Character", "Integer", "Long", "Float", "Double", "Boolean"};
   private static final ValueType[] primitiveTypes = new ValueType[]{
      ValueType.BYTE, ValueType.SHORT, ValueType.CHARACTER, ValueType.INTEGER, ValueType.LONG, ValueType.FLOAT, ValueType.DOUBLE, ValueType.BOOLEAN
   };

   @Override
   public boolean canHandle(MethodReference method) {
      if (!method.getClassName().equals(Array.class.getName())) {
         return false;
      }

      switch (method.getName()) {
         case "getImpl":
            return true;
         default:
            return false;
      }
   }

   @Override
   public void generate(GeneratorContext context, MethodReference method) {
      String array = context.parameterName(1);
      String index = context.parameterName(2);
      context.writer().println("TeaVM_Class* componentType = (TeaVM_Class*) TEAVM_CLASS_OF(" + array + ")->itemType;");
      context.writer().println("int32_t flags = componentType->flags;");
      context.writer().println("if (((flags & 15) >> 4) != 0) {").indent();
      context.writer().println("switch ((flags >> 4) & 15) {").indent();
      MethodDependencyInfo dependency = context.dependencies().getMethod(new MethodReference(Array.class, "getImpl", Object.class, int.class, Object.class));
      ValueDependencyInfo arrayDependency = dependency.getVariable(1);
      HashSet<ValueType> types = new HashSet<>(Arrays.asList(arrayDependency.getTypes()));

      for (int i = 0; i < primitiveWrappers.length; i++) {
         ValueType.Array typeName = ValueType.arrayOf(primitiveTypes[i]);
         if (types.contains(typeName)) {
            String wrapper = "java.lang." + primitiveWrappers[i];
            MethodReference methodRef = new MethodReference(wrapper, "valueOf", primitiveTypes[i], ValueType.object(wrapper));
            String type = CodeWriter.strictTypeAsString(primitiveTypes[i]);
            context.writer().println("case " + primitives[i] + ":").indent();
            context.importMethod(methodRef, true);
            context.writer().println("return " + context.names().forMethod(methodRef) + "(TEAVM_ARRAY_AT(" + array + ", " + type + ", " + index + "));");
            context.writer().outdent();
         }
      }

      context.writer().outdent().println("}").outdent().println("}");
      context.writer().println("return TEAVM_ARRAY_AT(" + array + ", void*, " + index + ");");
   }
}
