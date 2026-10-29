package org.teavm.backend.javascript.intrinsics.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.rendering.RenderingUtil;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.InjectorContext;
import org.teavm.dependency.DependencyInfo;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.AnnotationReader;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.GenericTypeParameter;
import org.teavm.model.GenericValueType;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.reflection.AnnotationGenerationHelper;
import org.teavm.reflection.ReflectionDependencyListener;

public class ClassReflectionInfoGenerator implements Injector {
   private ReflectionDependencyListener reflection;
   private DependencyInfo dependencyInfo;
   private boolean annotationsGenerated;
   private boolean metadataGenerated;
   private boolean fieldAnnotationsRequired;
   private boolean fieldGenericTypeRequired;
   private boolean methodAnnotationsRequired;
   private boolean methodTypeParametersRequired;
   private boolean methodGenericReturnTypeRequired;
   private boolean methodGenericParamTypesRequired;
   private boolean methodParamAnnotationsRequired;
   private boolean methodCheckedExceptionTypesRequired;
   private boolean innerClassesRequired;
   private Set<String> innerClassesAccessed = new HashSet<>();

   public ClassReflectionInfoGenerator(ReflectionDependencyListener reflection, DependencyInfo dependencyInfo) {
      this.reflection = reflection;
      this.dependencyInfo = dependencyInfo;
      this.fieldAnnotationsRequired = dependencyInfo.getMethod(new MethodReference(Field.class, "getDeclaredAnnotations", Annotation[].class)) != null;
      this.fieldGenericTypeRequired = dependencyInfo.getMethod(new MethodReference(Field.class, "getGenericType", Type.class)) != null;
      this.methodAnnotationsRequired = dependencyInfo.getMethod(new MethodReference(Executable.class, "getDeclaredAnnotations", Annotation[].class)) != null;
      this.methodTypeParametersRequired = dependencyInfo.getMethod(new MethodReference(Executable.class, "getTypeParameters", TypeVariable[].class)) != null;
      this.methodGenericReturnTypeRequired = dependencyInfo.getMethod(new MethodReference(Method.class, "getGenericReturnType", Type.class)) != null;
      this.methodGenericParamTypesRequired = dependencyInfo.getMethod(new MethodReference(Executable.class, "getGenericParameterTypes", Type[].class)) != null;
      this.methodParamAnnotationsRequired = dependencyInfo.getMethod(new MethodReference(Executable.class, "getParameterAnnotations", Annotation[][].class))
         != null;
      this.methodCheckedExceptionTypesRequired = dependencyInfo.getMethod(new MethodReference(Executable.class, "getExceptionTypes", Class[].class)) != null;
      MethodDependencyInfo classesMethod = dependencyInfo.getMethod(new MethodReference(Class.class, "getDeclaredClasses", Class[].class));
      if (classesMethod != null) {
         this.innerClassesRequired = true;

         for (ValueType type : classesMethod.getVariable(0).getClassValueNode().getTypes()) {
            if (type instanceof ValueType.Object) {
               this.innerClassesAccessed.add(((ValueType.Object)type).getClassName());
            }
         }
      }
   }

   @Override
   public void generate(InjectorContext context, MethodReference methodRef) {
      switch (methodRef.getDescriptor().getName()) {
         case "annotationCount":
            this.generateAnnotations(context);
            this.writeArrayLength(context, "annotations");
            break;
         case "annotation":
            this.generateAnnotations(context);
            this.writeArrayGet(context, "annotations");
            break;
         case "fieldCount":
            this.generateMetadata(context);
            this.writeArrayLength(context, "fields");
            break;
         case "field":
            this.generateMetadata(context);
            this.writeArrayGet(context, "fields");
            break;
         case "methodCount":
            this.generateMetadata(context);
            this.writeArrayLength(context, "methods");
            break;
         case "method":
            this.generateMetadata(context);
            this.writeArrayGet(context, "methods");
            break;
         case "typeParameterCount":
            this.generateMetadata(context);
            this.writeArrayLength(context, "typeParameters");
            break;
         case "typeParameter":
            this.generateMetadata(context);
            this.writeArrayGet(context, "typeParameters");
            break;
         case "innerClassCount":
            this.generateMetadata(context);
            this.writeArrayLength(context, "innerClasses");
            break;
         case "innerClass":
            this.generateMetadata(context);
            this.writeArrayGet(context, "innerClasses");
      }
   }

   private void writeArrayLength(InjectorContext context, String name) {
      context.writeExpr(context.getArgument(0));
      context.getWriter().append("." + name + ".length");
   }

   private void writeArrayGet(InjectorContext context, String name) {
      context.writeExpr(context.getArgument(0));
      context.getWriter().append("." + name + "[");
      context.writeExpr(context.getArgument(1));
      context.getWriter().append("]");
   }

   private void generateAnnotations(InjectorContext context) {
      if (!this.annotationsGenerated) {
         this.annotationsGenerated = true;
         MethodDependencyInfo methodDep = context.getDependencies().getMethod(new MethodReference(Class.class, "getDeclaredAnnotations", Annotation[].class));
         if (methodDep != null) {
            List<ClassReflectionInfoGenerator.ClassWithAnnotations> classes = Arrays.stream(methodDep.getVariable(0).getClassValueNode().getTypes())
               .filter(t -> t instanceof ValueType.Object)
               .map(t -> ((ValueType.Object)t).getClassName())
               .map(n -> this.collectAnnotations(context.getClassSource(), n))
               .filter(Objects::nonNull)
               .collect(Collectors.toList());
            if (!classes.isEmpty()) {
               SourceWriter writer = context.getMetadataWriter();
               writer.appendFunction("$rt_classAnnotationMetadata").append("([");
               boolean first = true;

               for (ClassReflectionInfoGenerator.ClassWithAnnotations entry : classes) {
                  if (!first) {
                     writer.append(",").ws();
                  }

                  first = false;
                  writer.appendClass(entry.cls.getName()).append(",").ws();
                  this.generateAnnotations(writer, context.getClassSource(), entry.annotations);
               }

               writer.append("]);").newLine();
            }
         }
      }
   }

   private void generateAnnotations(SourceWriter writer, ClassReaderSource classes, List<AnnotationReader> annotations) {
      writer.append("[");
      boolean first = true;

      for (AnnotationReader annot : annotations) {
         if (!first) {
            writer.append(",").ws();
         }

         first = false;
         AnnotationsGenerator.generate(writer, classes, annot);
      }

      writer.append("]");
   }

   private ClassReflectionInfoGenerator.ClassWithAnnotations collectAnnotations(ClassReaderSource classes, String className) {
      ClassReader cls = classes.get(className);
      if (cls == null) {
         return null;
      }

      List<AnnotationReader> annotations = AnnotationGenerationHelper.collectRuntimeAnnotations(classes, cls.getAnnotations().all());
      return annotations.isEmpty() ? null : new ClassReflectionInfoGenerator.ClassWithAnnotations(cls, annotations);
   }

   private void generateMetadata(InjectorContext context) {
      if (!this.metadataGenerated) {
         this.metadataGenerated = true;
         SourceWriter writer = context.getMetadataWriter();
         writer.appendFunction("$rt_reflection").append("([").indent().softNewLine();
         boolean first = true;
         Set<String> classesWithReflectableTypeParameters = this.getClassesWithReflectableTypeParameters();

         for (String className : context.getClassSource().getClassNames()) {
            ClassReader cls = context.getClassSource().get(className);
            List<? extends String> innerClasses = this.extractInnerClasses(cls);
            if (this.reflection.getClassesWithReflectableFields().contains(className)
               || this.reflection.getClassesWithReflectableMethods().contains(className)
               || classesWithReflectableTypeParameters.contains(className)
               || !innerClasses.isEmpty()) {
               if (!first) {
                  writer.append(",").ws().softNewLine();
               }

               first = false;
               writer.appendClass(cls.getName()).append(",").ws().append("{").softNewLine().indent();
               boolean needsComma = false;
               GenericTypeParameter[] genericParams = cls.getGenericParameters();
               if (classesWithReflectableTypeParameters.contains(className) && genericParams != null && genericParams.length > 0) {
                  this.generateTypeParameters(context, cls, null, cls.getGenericParameters());
                  needsComma = true;
               }

               List<FieldReader> reflectableFields = this.resolveReflectableFields(cls);
               if (!reflectableFields.isEmpty()) {
                  if (needsComma) {
                     writer.append(',').softNewLine();
                  }

                  this.generateFields(context, cls, reflectableFields);
                  needsComma = true;
               }

               List<MethodReader> reflectableMethods = this.resolveReflectableMethods(cls);
               if (!reflectableMethods.isEmpty()) {
                  if (needsComma) {
                     writer.append(',').softNewLine();
                  }

                  this.generateMethods(context, cls, reflectableMethods);
                  needsComma = true;
               }

               if (!innerClasses.isEmpty()) {
                  if (needsComma) {
                     writer.append(',').softNewLine();
                  }

                  writer.append("c:").ws();
                  this.generateInnerClasses(context, innerClasses);
               }

               writer.softNewLine().outdent().append("}");
            }
         }

         writer.outdent().append("]);").newLine();
      }
   }

   private List<FieldReader> resolveReflectableFields(ClassReader cls) {
      if (!this.reflection.getClassesWithReflectableFields().contains(cls.getName())) {
         return Collections.emptyList();
      }

      Set<String> accessibleFields = this.reflection.getAccessibleFields(cls.getName());
      if (accessibleFields != null && !accessibleFields.isEmpty()) {
         ArrayList<FieldReader> resolvedFields = new ArrayList<>();

         for (FieldReader field : cls.getFields()) {
            if (accessibleFields.contains(field.getName())) {
               resolvedFields.add(field);
            }
         }

         return resolvedFields;
      } else {
         return Collections.emptyList();
      }
   }

   private List<MethodReader> resolveReflectableMethods(ClassReader cls) {
      if (!this.reflection.getClassesWithReflectableMethods().contains(cls.getName())) {
         return Collections.emptyList();
      }

      Set<MethodDescriptor> accessibleMethods = this.reflection.getAccessibleMethods(cls.getName());
      if (accessibleMethods != null && !accessibleMethods.isEmpty()) {
         ArrayList<MethodReader> resolvedMethods = new ArrayList<>();

         for (MethodReader method : cls.getMethods()) {
            if (accessibleMethods.contains(method.getDescriptor())) {
               resolvedMethods.add(method);
            }
         }

         return resolvedMethods;
      } else {
         return Collections.emptyList();
      }
   }

   private Set<String> getClassesWithReflectableTypeParameters() {
      MethodDependencyInfo methodDep = this.dependencyInfo.getMethod(new MethodReference(Class.class, "getTypeParameters", TypeVariable[].class));
      if (methodDep == null) {
         return Collections.emptySet();
      }

      LinkedHashSet<String> result = new LinkedHashSet<>();

      for (ValueType type : methodDep.getVariable(0).getClassValueNode().getTypes()) {
         if (type instanceof ValueType.Object) {
            result.add(((ValueType.Object)type).getClassName());
         }
      }

      return result;
   }

   private void generateTypeParameters(InjectorContext context, ClassReader cls, MethodReader method, GenericTypeParameter[] typeParameters) {
      SourceWriter writer = context.getMetadataWriter();
      writer.append("p:").ws().append('[').indent();
      boolean first = true;

      for (GenericTypeParameter typeParam : typeParameters) {
         if (!first) {
            writer.append(",");
         }

         writer.softNewLine();
         first = false;
         writer.append('[');
         writer.append('"').append(RenderingUtil.escapeString(typeParam.getName())).append("\"");
         List<GenericValueType.Reference> bounds = typeParam.extractAllBounds();
         if (!bounds.isEmpty()) {
            writer.append(',').ws().append('[');
            this.generateGenericType(context, cls, method, bounds.get(0));

            for (int i = 1; i < bounds.size(); i++) {
               writer.append(',').ws();
               this.generateGenericType(context, cls, method, bounds.get(i));
            }

            writer.append(']');
         }

         writer.append(']');
      }

      writer.softNewLine().outdent().append("]");
   }

   private void generateFields(InjectorContext context, ClassReader cls, List<FieldReader> fields) {
      SourceWriter writer = context.getMetadataWriter();
      writer.append("f:").ws().append('[').indent();
      boolean first = true;

      for (FieldReader field : fields) {
         if (!first) {
            writer.append(",");
         }

         writer.softNewLine();
         first = false;
         writer.append('[');
         writer.append('"').append(RenderingUtil.escapeString(field.getName())).append("\",").ws();
         writer.append(ElementModifier.asModifiersInfo(field.readModifiers(), field.getLevel())).append(",").ws();
         RenderingUtil.typeToClsString(writer, field.getType());
         writer.append(',').ws();
         this.renderGetter(writer, field);
         writer.append(',').ws();
         this.renderSetter(writer, field);
         List<AnnotationReader> annotations = this.fieldAnnotationsRequired
            ? AnnotationGenerationHelper.collectRuntimeAnnotations(context.getClassSource(), field.getAnnotations().all())
            : Collections.emptyList();
         GenericValueType genericType = this.fieldGenericTypeRequired ? field.getGenericType() : null;
         if (!annotations.isEmpty() || genericType != null) {
            writer.append(',').ws().append('{');
            boolean needsComma = false;
            if (!annotations.isEmpty()) {
               writer.append("a:").ws();
               this.generateAnnotations(writer, context.getClassSource(), annotations);
               needsComma = true;
            }

            if (genericType != null) {
               if (needsComma) {
                  writer.append(',').ws();
               }

               writer.append("t:").ws();
               this.generateGenericType(context, cls, null, genericType);
            }

            writer.append('}');
         }

         writer.append(']');
      }

      writer.softNewLine().outdent().append("]");
   }

   private void renderGetter(SourceWriter writer, FieldReader field) {
      if (!this.reflection.isRead(field.getReference())) {
         writer.append("0");
      } else {
         writer.append("o").sameLineWs().append("=>").ws();
         this.fieldAccess(writer, field);
      }
   }

   private void renderSetter(SourceWriter writer, FieldReader field) {
      if (!this.reflection.isWritten(field.getReference())) {
         writer.append("0");
      } else {
         writer.append("(o,").sameLineWs().append("v)").sameLineWs().append("=>").ws();
         this.fieldAccess(writer, field);
         writer.ws().append('=').ws().append("v");
      }
   }

   private void fieldAccess(SourceWriter writer, FieldReader field) {
      if (field.hasModifier(ElementModifier.STATIC)) {
         writer.appendStaticField(field.getReference());
      } else {
         writer.append("o.").appendField(field.getReference());
      }
   }

   private void generateMethods(InjectorContext context, ClassReader cls, List<MethodReader> methods) {
      SourceWriter writer = context.getMetadataWriter();
      writer.append("m:").ws().append('[').indent();
      boolean first = true;

      for (MethodReader method : methods) {
         if (!first) {
            writer.append(",");
         }

         writer.softNewLine();
         first = false;
         writer.append('[');
         writer.append('"').append(RenderingUtil.escapeString(method.getName())).append("\",").ws();
         writer.append(ElementModifier.asModifiersInfo(method.readModifiers(), method.getLevel())).append(",").ws();
         RenderingUtil.typeToClsString(writer, method.getResultType());
         writer.append(',').ws();
         if (method.parameterCount() == 0) {
            writer.append("0");
         } else {
            writer.append('[');
            RenderingUtil.typeToClsString(writer, method.parameterType(0));

            for (int i = 1; i < method.parameterCount(); i++) {
               writer.append(",").ws();
               RenderingUtil.typeToClsString(writer, method.parameterType(i));
            }

            writer.append(']');
         }

         writer.append(',').ws();
         if (!method.hasModifier(ElementModifier.STATIC) && method.getLevel() != AccessLevel.PRIVATE && !method.getName().equals("<init>")) {
            writer.append("o").sameLineWs().append("=>").ws().append("o.").appendVirtualMethod(method.getDescriptor());
         } else if (this.reflection.isCalled(method.getReference())) {
            writer.appendMethod(method.getReference());
         } else {
            writer.append("0");
         }

         List<AnnotationReader> annotations = this.methodAnnotationsRequired
            ? AnnotationGenerationHelper.collectRuntimeAnnotations(context.getClassSource(), method.getAnnotations().all())
            : Collections.emptyList();
         GenericTypeParameter[] typeParameters = this.methodTypeParametersRequired ? method.getTypeParameters() : null;
         GenericValueType genericReturnType = this.methodGenericReturnTypeRequired && !method.getName().equals("<init>") ? method.getGenericResultType() : null;
         GenericValueType[] genericParamTypes = this.methodGenericParamTypesRequired ? method.getGenericParameterTypes() : null;
         List<List<AnnotationReader>> paramAnnotationsList = this.methodParamAnnotationsRequired
            ? this.collectAllParamAnnotations(method, context.getClassSource())
            : null;
         boolean hasParamGenericTypes = genericParamTypes != null && genericParamTypes.length > 0;
         boolean hasParamAnnotations = paramAnnotationsList != null;
         List<? extends String> thrownTypes = this.methodCheckedExceptionTypesRequired ? method.getThrownTypes() : Collections.emptyList();
         boolean hasReflectionInfo = !annotations.isEmpty()
            || typeParameters != null && typeParameters.length > 0
            || genericReturnType != null
            || hasParamGenericTypes
            || hasParamAnnotations;
         boolean hasExceptionTypes = !thrownTypes.isEmpty();
         if (hasReflectionInfo) {
            writer.append(',').ws().append('{');
            boolean needsComma = false;
            if (!annotations.isEmpty()) {
               writer.append("a:").ws();
               this.generateAnnotations(writer, context.getClassSource(), annotations);
               needsComma = true;
            }

            if (typeParameters != null && typeParameters.length > 0) {
               if (needsComma) {
                  writer.append(',').ws();
               }

               this.generateTypeParameters(context, cls, method, typeParameters);
               needsComma = true;
            }

            if (genericReturnType != null) {
               if (needsComma) {
                  writer.append(',').ws();
               }

               writer.append("r:").ws();
               this.generateGenericType(context, cls, method, genericReturnType);
               needsComma = true;
            }

            if (hasParamGenericTypes || hasParamAnnotations) {
               if (needsComma) {
                  writer.append(',').ws();
               }

               writer.append("s:").ws().append('[');

               for (int j = 0; j < method.parameterCount(); j++) {
                  if (j > 0) {
                     writer.append(',').ws();
                  }

                  GenericValueType genericParamType = genericParamTypes != null && j < genericParamTypes.length ? genericParamTypes[j] : null;
                  List<AnnotationReader> paramAnnots = paramAnnotationsList != null && j < paramAnnotationsList.size()
                     ? paramAnnotationsList.get(j)
                     : Collections.emptyList();
                  if (genericParamType == null && paramAnnots.isEmpty()) {
                     writer.append("0");
                  } else {
                     writer.append('{');
                     boolean needsComma2 = false;
                     if (genericParamType != null) {
                        writer.append("t:").ws();
                        this.generateGenericType(context, cls, method, genericParamType);
                        needsComma2 = true;
                     }

                     if (!paramAnnots.isEmpty()) {
                        if (needsComma2) {
                           writer.append(',').ws();
                        }

                        writer.append("a:").ws();
                        this.generateAnnotations(writer, context.getClassSource(), paramAnnots);
                     }

                     writer.append('}');
                  }
               }

               writer.append(']');
            }

            writer.append('}');
         } else if (hasExceptionTypes) {
            writer.append(',').ws().append("0");
         }

         if (hasExceptionTypes) {
            writer.append(',').ws().append('[');
            boolean firstEx = true;

            for (String thrownType : thrownTypes) {
               if (!firstEx) {
                  writer.append(',').ws();
               }

               firstEx = false;
               writer.appendClass(thrownType.replace('/', '.'));
            }

            writer.append(']');
         }

         writer.append(']');
      }

      writer.softNewLine().outdent().append("]");
   }

   private List<List<AnnotationReader>> collectAllParamAnnotations(MethodReader method, ClassReaderSource classes) {
      AnnotationContainerReader[] paramAnnots = method.getParameterAnnotations();
      if (paramAnnots == null) {
         return null;
      }

      ArrayList<List<AnnotationReader>> result = new ArrayList<>();
      boolean hasAny = false;

      for (AnnotationContainerReader container : paramAnnots) {
         List<AnnotationReader> annots = AnnotationGenerationHelper.collectRuntimeAnnotations(classes, container.all());
         result.add(annots);
         if (!annots.isEmpty()) {
            hasAny = true;
         }
      }

      return hasAny ? result : null;
   }

   private void generateGenericType(InjectorContext context, ClassReader cls, MethodReader method, GenericValueType type) {
      SourceWriter writer = context.getMetadataWriter();
      writer.append('[');
      if (type.canBeRepresentedAsRaw()) {
         writer.append("6,").ws();
         RenderingUtil.typeToClsString(writer, type.asValueType());
      } else if (type instanceof GenericValueType.Object paramType) {
         writer.append("0,").ws().appendClass(paramType.getFullClassName());
         if (paramType.getParent() != null || paramType.getArguments().length > 0) {
            writer.append(",").ws();
            writer.append('[');
            boolean first = true;

            for (GenericValueType.Argument arg : paramType.getArguments()) {
               if (!first) {
                  writer.append(",").ws();
               }

               first = false;
               this.generateGenericType(context, cls, method, arg);
            }

            writer.append(']');
            if (paramType.getParent() != null) {
               writer.append(",").ws();
               this.generateGenericType(context, cls, method, paramType.getParent());
            }
         }
      } else if (type instanceof GenericValueType.Variable) {
         writer.append("1,").ws();
         this.generateTypeVarRef(context, cls, method, (GenericValueType.Variable)type);
      } else if (type instanceof GenericValueType.Array) {
         writer.append("2,").ws();
         this.generateGenericType(context, cls, method, ((GenericValueType.Array)type).getItemType());
      }

      writer.append(']');
   }

   private void generateTypeVarRef(InjectorContext context, ClassReader cls, MethodReader method, GenericValueType.Variable varRef) {
      int level = 0;
      SourceWriter writer = context.getMetadataWriter();
      if (method != null) {
         GenericTypeParameter[] params = method.getTypeParameters();

         for (int i = 0; i < params.length; i++) {
            if (params[i].getName().equals(varRef.getName())) {
               writer.append(i);
               return;
            }
         }

         level++;
      }

      while (cls != null) {
         GenericTypeParameter[] params = cls.getGenericParameters();
         if (params != null && params.length > 0) {
            for (int i = 0; i < params.length; i++) {
               if (params[i].getName().equals(varRef.getName())) {
                  writer.append(i);
                  if (level > 0) {
                     writer.append(",").ws().append(level);
                  }

                  return;
               }
            }
         }

         level++;
         String ownerName = cls.getOwnerName();
         cls = ownerName != null ? context.getClassSource().get(ownerName) : null;
      }
   }

   private void generateGenericType(InjectorContext context, ClassReader cls, MethodReader method, GenericValueType.Argument arg) {
      SourceWriter writer = context.getMetadataWriter();
      switch (arg.getKind()) {
         case COVARIANT:
            writer.append("[3,").ws();
            this.generateGenericType(context, cls, method, arg.getValue());
            writer.append(']');
            break;
         case CONTRAVARIANT:
            writer.append("[4,").ws();
            this.generateGenericType(context, cls, method, arg.getValue());
            writer.append(']');
            break;
         case INVARIANT:
            this.generateGenericType(context, cls, method, arg.getValue());
            break;
         case ANY:
            writer.append("[5]");
      }
   }

   private void generateInnerClasses(InjectorContext context, List<? extends String> innerClasses) {
      SourceWriter writer = context.getMetadataWriter();
      writer.append("[").appendClass(innerClasses.get(0)).append(",").ws();

      for (int i = 1; i < innerClasses.size(); i++) {
         writer.appendClass(innerClasses.get(i)).append(",").ws();
      }

      writer.append("]");
   }

   private List<? extends String> extractInnerClasses(ClassReader cls) {
      if (this.innerClassesRequired && this.innerClassesAccessed.contains(cls.getName())) {
         ArrayList<String> filtered = new ArrayList<>();

         for (String innerCls : cls.getInnerClasses()) {
            if (this.dependencyInfo.getClass(innerCls) != null) {
               filtered.add(innerCls);
            }
         }

         return filtered;
      } else {
         return Collections.emptyList();
      }
   }

   private static class ClassWithAnnotations {
      private ClassReader cls;
      private List<AnnotationReader> annotations;

      ClassWithAnnotations(ClassReader cls, List<AnnotationReader> annotations) {
         this.cls = cls;
         this.annotations = annotations;
      }
   }
}
