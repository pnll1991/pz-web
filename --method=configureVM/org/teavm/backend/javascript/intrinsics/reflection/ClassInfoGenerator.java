package org.teavm.backend.javascript.intrinsics.reflection;

import java.lang.reflect.Array;
import java.util.Set;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.rendering.Precedence;
import org.teavm.backend.javascript.spi.Generator;
import org.teavm.backend.javascript.spi.GeneratorContext;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.InjectorContext;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class ClassInfoGenerator implements Injector, Generator {
   private boolean enumMetadataGenerated;

   @Override
   public void generate(InjectorContext context, MethodReference methodRef) {
      switch (methodRef.getName()) {
         case "classObject":
            context.getWriter().appendFunction("$rt_cls").append("(");
            context.writeExpr(context.getArgument(0), Precedence.min());
            context.getWriter().append(")");
            break;
         case "primitiveKind":
         case "modifiers":
         case "name":
         case "simpleName":
         case "itemType":
         case "parent":
         case "declaringClass":
         case "enclosingClass":
         case "reflection":
            context.writeExpr(context.getArgument(0));
            context.getWriter().append("[").appendFunction("$rt_meta").append("].").append(methodRef.getName());
            break;
         case "isSuperTypeOf":
            context.getWriter().appendFunction("$rt_isAssignable").append("(");
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append(",").ws();
            context.writeExpr(context.getArgument(0), Precedence.min());
            context.getWriter().append(")");
            break;
         case "superinterfaceCount":
            context.writeExpr(context.getArgument(0));
            context.getWriter().append("[").appendFunction("$rt_meta").append("].superinterfaces.length");
            break;
         case "superinterface":
            context.writeExpr(context.getArgument(0));
            context.getWriter().append("[").appendFunction("$rt_meta").append("].superinterfaces[");
            context.writeExpr(context.getArgument(1));
            context.getWriter().append("]");
            break;
         case "initialize":
            context.writeExpr(context.getArgument(0));
            context.getWriter().append("[").appendFunction("$rt_meta").append("].clinit()");
            break;
         case "newInstance":
            context.getWriter().append("new ");
            context.writeExpr(context.getArgument(0), Precedence.MEMBER_ACCESS);
            break;
         case "initializeNewInstance":
            context.getWriter().appendFunction("$rt_callDefaultConstructor").append("(");
            context.writeExpr(context.getArgument(0), Precedence.min());
            context.getWriter().append(",").ws();
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append(")");
            this.writeSimpleConstructors(context);
            break;
         case "arrayType":
            context.getWriter().appendFunction("$rt_arraycls").append("(");
            context.writeExpr(context.getArgument(0), Precedence.min());
            context.getWriter().append(")");
            break;
         case "enumConstant":
            this.writeEnumMetadata(context);
            context.getWriter().appendFunction("$rt_enumConstants").append("(");
            context.writeExpr(context.getArgument(0), Precedence.min());
            context.getWriter().append(")[");
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append("]");
            break;
         case "enumConstantCount":
            this.writeEnumMetadata(context);
            context.getWriter().appendFunction("$rt_enumConstants").append("(");
            context.writeExpr(context.getArgument(0), Precedence.min());
            context.getWriter().append(").length");
            break;
         case "arrayLength":
            context.getWriter().appendFunction("$rt_arrayLength").append("(");
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append(")");
            break;
         case "getItem":
            context.getWriter().appendFunction("$rt_arrayGet").append("(");
            context.writeExpr(context.getArgument(0), Precedence.min());
            context.getWriter().append(",").ws();
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append(",").ws();
            context.writeExpr(context.getArgument(2), Precedence.min());
            context.getWriter().append(")");
            break;
         case "putItem":
            context.getWriter().appendFunction("$rt_arrayPut").append("(");
            context.writeExpr(context.getArgument(0), Precedence.min());
            context.getWriter().append(",").ws();
            context.writeExpr(context.getArgument(1), Precedence.min());
            context.getWriter().append(",").ws();
            context.writeExpr(context.getArgument(2), Precedence.min());
            context.getWriter().append(",").ws();
            context.writeExpr(context.getArgument(3), Precedence.min());
            context.getWriter().append(")");
            break;
         case "rewind":
            context.getWriter().appendFunction("$rt_allClassesRewind");
            context.getWriter().append("()");
            break;
         case "hasNext":
            context.getWriter().appendFunction("$rt_allClassesHasNext");
            context.getWriter().append("()");
            break;
         case "next":
            context.getWriter().appendFunction("$rt_allClassesNext");
            context.getWriter().append("()");
      }
   }

   @Override
   public void generate(GeneratorContext context, SourceWriter writer, MethodReference methodRef) {
      switch (methodRef.getName()) {
         case "newArrayInstance":
            this.generateNewArrayInstance(context, writer);
      }
   }

   private void generateNewArrayInstance(GeneratorContext context, SourceWriter writer) {
      MethodDependencyInfo dependency = context.getDependency()
         .getMethod(new MethodReference(Array.class, "newInstance", Class.class, int.class, Object.class));
      writer.append("switch").ws().append("(").append(context.getParameterName(0)).append(".primitiveKind)").appendBlockStart();
      String length = context.getParameterName(1);
      Set<ValueType> types = Set.of(dependency.getResult().getTypes());
      this.writeArrayClause(types, ValueType.BOOLEAN, writer, 1, "$rt_createBooleanArray", length);
      this.writeArrayClause(types, ValueType.BYTE, writer, 2, "$rt_createByteArray", length);
      this.writeArrayClause(types, ValueType.SHORT, writer, 3, "$rt_createShortArray", length);
      this.writeArrayClause(types, ValueType.CHARACTER, writer, 4, "$rt_createCharArray", length);
      this.writeArrayClause(types, ValueType.INTEGER, writer, 5, "$rt_createIntArray", length);
      this.writeArrayClause(types, ValueType.LONG, writer, 6, "$rt_createLongArray", length);
      this.writeArrayClause(types, ValueType.FLOAT, writer, 7, "$rt_createFloatArray", length);
      this.writeArrayClause(types, ValueType.DOUBLE, writer, 8, "$rt_createDoubleArray", length);
      writer.append("default:")
         .ws()
         .append("return ")
         .appendFunction("$rt_createArray")
         .append("(")
         .append(context.getParameterName(0))
         .append(",")
         .ws()
         .append(length)
         .append(");")
         .softNewLine();
      writer.appendBlockEnd();
   }

   private void writeArrayClause(Set<ValueType> types, ValueType type, SourceWriter writer, int kind, String functionName, String length) {
      if (types.contains(ValueType.arrayOf(type))) {
         writer.append("case ").append(kind).append(":").ws();
         writer.append("return ").append(functionName).append("(").append(length).append(");").softNewLine();
      }
   }

   private void writeEnumMetadata(InjectorContext context) {
      if (!this.enumMetadataGenerated) {
         SourceWriter writer = context.getMetadataWriter();
         this.enumMetadataGenerated = true;
         writer.appendFunction("$rt_enumConstantsMetadata").append("([").softNewLine().indent();
         boolean first = true;

         for (String clsName : context.getClassSource().getClassNames()) {
            ClassReader cls = context.getClassSource().get(clsName);
            if (cls.hasModifier(ElementModifier.ENUM)) {
               if (!first) {
                  writer.append(",").ws();
               }

               first = false;
               writer.appendClass(clsName).append(",").ws().append("()").sameLineWs().append("=>").sameLineWs().append("[");
               boolean firstField = true;

               for (FieldReader field : cls.getFields()) {
                  if (field.hasModifier(ElementModifier.STATIC) && field.hasModifier(ElementModifier.ENUM)) {
                     if (!firstField) {
                        writer.append(",").ws();
                     }

                     firstField = false;
                     writer.appendStaticField(field.getReference());
                  }
               }

               writer.append("]");
            }
         }

         writer.outdent().append("]);").newLine();
      }
   }

   private void writeSimpleConstructors(InjectorContext context) {
      MethodDependencyInfo methodDep = context.getDependencies().getMethod(new MethodReference(Class.class, "newInstance", Object.class));
      if (methodDep != null) {
         SourceWriter writer = context.getMetadataWriter();
         writer.appendFunction("$rt_simpleConstructors").append("([");
         boolean first = true;

         for (ValueType type : methodDep.getVariable(0).getClassValueNode().getTypes()) {
            if (type instanceof ValueType.Object) {
               String className = ((ValueType.Object)type).getClassName();
               ClassReader cls = context.getClassSource().get(className);
               if (cls != null && !cls.hasModifier(ElementModifier.ABSTRACT) && !cls.hasModifier(ElementModifier.INTERFACE)) {
                  MethodReader ctor = cls.getMethod(new MethodDescriptor("<init>", void.class));
                  if (ctor != null && !ctor.hasModifier(ElementModifier.ABSTRACT) && ctor.getLevel() == AccessLevel.PUBLIC) {
                     if (!first) {
                        writer.append(",").ws();
                     }

                     first = false;
                     writer.appendClass(cls.getName()).append(",").ws().appendMethod(ctor.getReference());
                  }
               }
            }
         }

         writer.append("]);").newLine();
      }
   }
}
