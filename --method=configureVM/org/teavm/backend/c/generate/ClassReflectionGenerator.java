package org.teavm.backend.c.generate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.teavm.backend.lowlevel.generate.NameProvider;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.AnnotationReader;
import org.teavm.model.AnnotationValue;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.GenericTypeParameter;
import org.teavm.model.GenericValueType;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ClassMetadataRequirements;
import org.teavm.model.classes.VirtualTable;
import org.teavm.reflection.AnnotationGenerationHelper;
import org.teavm.reflection.ReflectionDependencyListener;
import org.teavm.runtime.reflect.ClassReflectionInfo;
import org.teavm.runtime.reflect.FieldReflectionInfo;
import org.teavm.runtime.reflect.GenericTypeInfo;
import org.teavm.runtime.reflect.MethodInfo;
import org.teavm.runtime.reflect.MethodReflectionInfo;
import org.teavm.runtime.reflect.ParameterInfo;
import org.teavm.runtime.reflect.TypeVariableInfo;

class ClassReflectionGenerator {
   private GenerationContext context;
   private CodeWriter writer;
   private IncludeManager includes;
   private ReflectionDependencyListener reflection;
   private Collection<ValueType> types;
   private MethodConvertersGenerator methodConvertersGenerator;
   private boolean needMethodAnnotations;
   private boolean needMethodParamAnnotations;
   private boolean needCheckedExceptions;
   private boolean needFieldAnnotations;
   private boolean needFieldGenericType;
   private boolean needMethodGenericTypes;
   private boolean needMethodTypeParams;
   private boolean needTypeParameters;
   private boolean needBounds;
   private boolean needInnerClasses;
   private Set<String> innerClassesAccessed = new HashSet<>();

   ClassReflectionGenerator(
      GenerationContext context, ReflectionDependencyListener reflection, Collection<ValueType> types, MethodConvertersGenerator methodConvertersGenerator
   ) {
      this.context = context;
      this.reflection = reflection;
      this.types = types;
      this.methodConvertersGenerator = methodConvertersGenerator;
      this.needMethodAnnotations = context.getDependencies().getMethod(new MethodReference(MethodReflectionInfo.class, "annotationCount", int.class)) != null;
      this.needMethodParamAnnotations = context.getDependencies().getMethod(new MethodReference(MethodReflectionInfo.class, "parameterInfoCount", int.class))
         != null;
      this.needCheckedExceptions = context.getDependencies().getMethod(new MethodReference(MethodInfo.class, "checkedExceptionCount", int.class)) != null;
      this.needFieldAnnotations = context.getDependencies().getMethod(new MethodReference(FieldReflectionInfo.class, "annotationCount", int.class)) != null;
      this.needFieldGenericType = context.getDependencies().getMethod(new MethodReference(FieldReflectionInfo.class, "genericType", GenericTypeInfo.class))
         != null;
      this.needMethodGenericTypes = context.getDependencies()
               .getMethod(new MethodReference(MethodReflectionInfo.class, "genericReturnType", GenericTypeInfo.class))
            != null
         || context.getDependencies().getMethod(new MethodReference(ParameterInfo.class, "genericType", GenericTypeInfo.class)) != null;
      this.needMethodTypeParams = context.getDependencies().getMethod(new MethodReference(MethodReflectionInfo.class, "typeParameterCount", int.class)) != null;
      this.needTypeParameters = context.getDependencies().getMethod(new MethodReference(ClassReflectionInfo.class, "typeParameterCount", int.class)) != null;
      this.needBounds = context.getDependencies().getMethod(new MethodReference(TypeVariableInfo.class, "boundCount", int.class)) != null;
      MethodDependencyInfo classesMethod = context.getDependencies().getMethod(new MethodReference(Class.class, "getDeclaredClasses", Class[].class));
      if (classesMethod != null) {
         this.needInnerClasses = true;

         for (ValueType type : classesMethod.getVariable(0).getClassValueNode().getTypes()) {
            if (type instanceof ValueType.Object) {
               this.innerClassesAccessed.add(((ValueType.Object)type).getClassName());
            }
         }
      }
   }

   void prepare(CodeWriter writer, IncludeManager includes) {
      this.methodConvertersGenerator.startForClass(writer, includes);
   }

   void finish() {
      this.methodConvertersGenerator.endForClass();
   }

   void generate(CodeWriter writer, IncludeManager includes, ClassReader cls) {
      List<AnnotationReader> annotations = this.extractAnnotations(cls);
      List<FieldReader> fields = this.extractFields(cls);
      List<MethodReader> methods = this.extractMethods(cls);
      GenericTypeParameter[] typeParameters = this.extractTypeParameters(cls);
      List<String> innerClasses = this.extractInnerClasses(cls);
      if (annotations.isEmpty() && fields.isEmpty() && methods.isEmpty() && typeParameters.length == 0 && innerClasses.isEmpty()) {
         writer.print("NULL");
      } else {
         this.writer = writer;
         this.includes = includes;
         includes.includePath("reflection.h");
         writer.println("&(TeaVM_ClassReflection) {").indent();
         boolean needComma = false;
         if (!annotations.isEmpty()) {
            writer.print(".annotations = ");
            this.generateAnnotations(annotations);
            needComma = true;
         }

         if (!fields.isEmpty()) {
            if (needComma) {
               writer.println(",");
            }

            writer.print(".fields = ");
            this.generateFields(cls, fields);
            needComma = true;
         }

         if (!methods.isEmpty()) {
            if (needComma) {
               writer.println(",");
            }

            writer.print(".methods = ");
            this.generateMethods(methods);
            needComma = true;
         }

         if (typeParameters.length > 0) {
            if (needComma) {
               writer.println(",");
            }

            writer.print(".typeParameters = ");
            this.generateTypeParameters(typeParameters, cls);
            needComma = true;
         }

         if (!innerClasses.isEmpty()) {
            if (needComma) {
               writer.println(",");
            }

            writer.print(".innerClasses = ");
            this.generateInnerClasses(innerClasses);
         }

         writer.println();
         writer.outdent().print("}");
         this.writer = null;
         this.includes = null;
      }
   }

   private void generateFields(ClassReader cls, List<FieldReader> fields) {
      this.writer
         .print("(TeaVM_FieldInfoList*) &(struct { int32_t count; TeaVM_FieldInfo data[")
         .print(String.valueOf(fields.size()))
         .println("];}) {")
         .indent();
      this.writer.print(".count = ").print(String.valueOf(fields.size())).println(",");
      this.writer.print(".data = ").println("{").indent();
      this.generateField(cls, fields.get(0));

      for (int i = 1; i < fields.size(); i++) {
         this.writer.println(",");
         this.generateField(cls, fields.get(i));
      }

      this.writer.println().outdent().println("}");
      this.writer.outdent().print("}");
   }

   private void generateField(ClassReader cls, FieldReader field) {
      this.includes.addInclude("<stddef.h>");
      NameProvider names = this.context.getNames();
      this.writer.println("{").indent();
      int nameIndex = this.context.getStringPool().getStringIndex(field.getName());
      this.writer.print(".name = TEAVM_GET_STRING_ADDRESS(").print(String.valueOf(nameIndex)).println("),");
      int modifiers = ElementModifier.asModifiersInfo(field.readModifiers(), field.getLevel());
      this.writer.print(".modifiers = ").print(String.valueOf(modifiers)).println(",");
      List<AnnotationReader> fieldAnnotations = this.needFieldAnnotations
         ? AnnotationGenerationHelper.collectRuntimeAnnotations(this.context.getClassSource(), field.getAnnotations().all())
         : List.of();
      GenericValueType fieldGenericType = this.needFieldGenericType ? field.getGenericType() : null;
      if (fieldGenericType != null
         && fieldGenericType.canBeRepresentedAsRaw()
         && fieldGenericType.asValueType() != null
         && fieldGenericType.asValueType().equals(field.getType())) {
         fieldGenericType = null;
      }

      if (!fieldAnnotations.isEmpty() || fieldGenericType != null) {
         this.writer.print(".reflection = &(TeaVM_FieldReflectionInfo) {");
         boolean needReflectionComma = false;
         if (!fieldAnnotations.isEmpty()) {
            this.writer.print(" .annotations = ");
            this.generateAnnotations(fieldAnnotations);
            needReflectionComma = true;
         }

         if (fieldGenericType != null) {
            if (needReflectionComma) {
               this.writer.print(", ");
            } else {
               this.writer.print(" ");
            }

            this.writer.print(".genericType = ");
            this.generateGenericType(fieldGenericType, this.context.getClassSource().get(field.getOwnerName()), null);
         }

         this.writer.println(" },");
      }

      this.writer.print(".type = ");
      this.writeType(field.getType());
      this.writer.println(",");
      this.writer.print(".location = { ");
      if (field.hasModifier(ElementModifier.STATIC)) {
         this.writer.print(".memory = &").print(names.forStaticField(field.getReference()));
      } else {
         this.writer
            .print(".instance = (int16_t) offsetof(")
            .print(names.forClass(cls.getName()))
            .print(", ")
            .print(names.forMemberField(field.getReference()))
            .print(")");
      }

      this.writer.println(" }");
      this.writer.outdent().print("}");
   }

   private void generateMethods(List<MethodReader> methods) {
      this.writer
         .print("(TeaVM_MethodInfoList*) &(struct { int32_t count; TeaVM_MethodInfo data[")
         .print(String.valueOf(methods.size()))
         .println("];}) {")
         .indent();
      this.writer.print(".count = ").print(String.valueOf(methods.size())).println(",");
      this.writer.print(".data = ").println("{").indent();
      this.generateMethod(methods.get(0));

      for (int i = 1; i < methods.size(); i++) {
         this.writer.println(",");
         this.generateMethod(methods.get(i));
      }

      this.writer.println().outdent().println("}");
      this.writer.outdent().print("}");
   }

   private void generateMethod(MethodReader method) {
      this.includes.addInclude("<stddef.h>");
      this.writer.println("{").indent();
      int nameIndex = this.context.getStringPool().getStringIndex(method.getName());
      this.writer.print(".name = TEAVM_GET_STRING_ADDRESS(").print(String.valueOf(nameIndex)).println("),");
      int modifiers = ElementModifier.asModifiersInfo(method.readModifiers(), method.getLevel());
      this.writer.print(".modifiers = ").print(String.valueOf(modifiers)).println(",");
      this.writer.print(".returnType = ");
      this.writeType(method.getResultType());
      if (method.parameterCount() > 0) {
         this.writer.println(",");
         this.writer.print(".parameterTypes = ");
         this.writer
            .print("(TeaVM_ClassPtrList*) &(struct { int32_t count; TeaVM_ClassPtr data[")
            .print(String.valueOf(method.parameterCount()))
            .println("];}) {")
            .indent();
         this.writer.print(".count = ").print(String.valueOf(method.parameterCount())).println(",");
         this.writer.print(".data = ").println("{").indent();

         for (int i = 0; i < method.parameterCount(); i++) {
            if (i > 0) {
               this.writer.println(",");
            }

            this.writeType(method.parameterType(i));
         }

         this.writer.println();
         this.writer.outdent().println("}");
         this.writer.outdent().print("}");
      }

      if (this.needCheckedExceptions) {
         List<? extends String> thrownTypes = method.getThrownTypes();
         if (thrownTypes != null && !thrownTypes.isEmpty()) {
            this.writer.println(",");
            this.writer.print(".checkedExceptionTypes = ");
            this.writer
               .print("(TeaVM_ClassRefList*) &(struct { int32_t count; TeaVM_Class* data[")
               .print(String.valueOf(thrownTypes.size()))
               .println("];}) {")
               .indent();
            this.writer.print(".count = ").print(String.valueOf(thrownTypes.size())).println(",");
            this.writer.print(".data = ").println("{").indent();

            for (int i = 0; i < thrownTypes.size(); i++) {
               if (i > 0) {
                  this.writer.println(",");
               }

               ValueType.Object exType = ValueType.object(thrownTypes.get(i));
               this.includes.includeType(exType);
               this.types.add(exType);
               this.writer.print("(TeaVM_Class*) &").print(this.context.getNames().forClassInstance(exType));
            }

            this.writer.println();
            this.writer.outdent().println("}");
            this.writer.outdent().print("}");
         }
      }

      if (this.needMethodAnnotations || this.needMethodParamAnnotations || this.needMethodGenericTypes || this.needMethodTypeParams) {
         List<AnnotationReader> methodAnnotations = this.needMethodAnnotations
            ? AnnotationGenerationHelper.collectRuntimeAnnotations(this.context.getClassSource(), method.getAnnotations().all())
            : List.of();
         List<List<AnnotationReader>> paramAnnotations = this.needMethodParamAnnotations ? this.collectParamAnnotations(method) : null;
         boolean hasParamAnnotations = paramAnnotations != null && this.hasNonEmptyList(paramAnnotations);
         ClassReader cls = this.context.getClassSource().get(method.getOwnerName());
         GenericValueType genericReturnType = this.needMethodGenericTypes ? method.getGenericResultType() : null;
         if (genericReturnType != null
            && genericReturnType.canBeRepresentedAsRaw()
            && genericReturnType.asValueType() != null
            && genericReturnType.asValueType().equals(method.getResultType())) {
            genericReturnType = null;
         }

         GenericValueType[] genericParamTypes = null;
         boolean hasGenericParams = false;
         if (this.needMethodGenericTypes) {
            genericParamTypes = method.getGenericParameterTypes();
            if (genericParamTypes != null) {
               for (int i = 0; i < genericParamTypes.length; i++) {
                  GenericValueType pt = genericParamTypes[i];
                  if (!pt.canBeRepresentedAsRaw() || !pt.asValueType().equals(method.parameterType(i))) {
                     hasGenericParams = true;
                     break;
                  }
               }

               if (!hasGenericParams) {
                  genericParamTypes = null;
               }
            }
         }

         GenericTypeParameter[] methodTypeParams = this.needMethodTypeParams ? method.getTypeParameters() : null;
         if (!methodAnnotations.isEmpty()
            || hasParamAnnotations
            || genericReturnType != null
            || hasGenericParams
            || methodTypeParams != null && methodTypeParams.length > 0) {
            this.writer.println(",");
            this.writer.print(".reflection = &(TeaVM_MethodReflectionInfo) {").indent();
            boolean needReflectionComma = false;
            if (genericReturnType != null) {
               this.writer.println();
               this.writer.print(".genericReturnType = ");
               this.generateGenericType(genericReturnType, cls, method);
               needReflectionComma = true;
            }

            if (!methodAnnotations.isEmpty()) {
               if (needReflectionComma) {
                  this.writer.println(",");
               } else {
                  this.writer.println();
               }

               this.writer.print(".annotations = ");
               this.generateAnnotations(methodAnnotations);
               needReflectionComma = true;
            }

            if (hasParamAnnotations || hasGenericParams) {
               if (needReflectionComma) {
                  this.writer.println(",");
               } else {
                  this.writer.println();
               }

               this.writer.print(".parameterInfos = ");
               this.generateParameterInfos(method, paramAnnotations, genericParamTypes, cls);
               needReflectionComma = true;
            }

            if (methodTypeParams != null && methodTypeParams.length > 0) {
               if (needReflectionComma) {
                  this.writer.println(",");
               } else {
                  this.writer.println();
               }

               this.writer.print(".typeParameters = ");
               this.generateTypeParameters(methodTypeParams, cls, method);
            }

            this.writer.println();
            this.writer.outdent().print("}");
         }
      }

      if (this.reflection.isCalled(method.getReference())) {
         this.writer.println(",");
         this.writer.println(".caller = &(TeaVM_MethodCaller) {").indent();
         String conv = this.methodConvertersGenerator
            .getSignatureFunction(!method.hasModifier(ElementModifier.STATIC), method.getResultType(), method.getParameterTypes());
         this.writer.println(".converter = &" + conv + ",");
         this.writer.print(".functionRef = { ");
         if (!method.hasModifier(ElementModifier.STATIC) && method.getLevel() != AccessLevel.PRIVATE && !method.getName().equals("<init>")) {
            VirtualTable vt = this.context.getVirtualTableProvider().lookup(method.getOwnerName());
            String vtableClass = null;
            if (vt != null) {
               VirtualTable containingVt = vt.findMethodContainer(method.getDescriptor());
               if (containingVt != null) {
                  vtableClass = containingVt.getClassName();
               }
            }

            String offset;
            if (vtableClass == null) {
               offset = "0";
            } else {
               this.includes.addInclude("<stddef.h>");
               offset = "(int16_t) offsetof("
                  + this.context.getNames().forClassClass(vtableClass)
                  + ","
                  + this.context.getNames().forVirtualMethod(method.getDescriptor())
                  + ")";
            }

            this.writer.print(".vtOffset = " + offset);
         } else {
            this.writer.print(".directFnRef = &" + this.context.getNames().forMethod(method.getReference()));
         }

         this.writer.print(" }");
         if (method.getName().equals("<init>")) {
            this.writer.println(",");
            this.writer.print(".forceDirect = true");
         }

         this.writer.println();
         this.writer.outdent().print("}");
      }

      this.writer.println();
      this.writer.outdent().print("}");
   }

   private List<List<AnnotationReader>> collectParamAnnotations(MethodReader method) {
      AnnotationContainerReader[] paramAnnots = method.getParameterAnnotations();
      if (paramAnnots == null) {
         return null;
      }

      ArrayList<List<AnnotationReader>> result = new ArrayList<>();

      for (AnnotationContainerReader container : paramAnnots) {
         result.add(AnnotationGenerationHelper.collectRuntimeAnnotations(this.context.getClassSource(), container.all()));
      }

      return result;
   }

   private boolean hasNonEmptyList(List<List<AnnotationReader>> lists) {
      for (List<AnnotationReader> list : lists) {
         if (!list.isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private void generateParameterInfos(
      MethodReader method, List<List<AnnotationReader>> paramAnnotations, GenericValueType[] genericParamTypes, ClassReader contextClass
   ) {
      int count = method.parameterCount();
      this.writer
         .print("(TeaVM_ParameterInfoList*) &(struct { int32_t count; TeaVM_ParameterInfo data[")
         .print(String.valueOf(count))
         .println("]; }) {")
         .indent();
      this.writer.print(".count = ").print(String.valueOf(count)).println(",");
      this.writer.print(".data = ").println("{").indent();

      for (int i = 0; i < count; i++) {
         if (i > 0) {
            this.writer.println(",");
         }

         List<AnnotationReader> annots = paramAnnotations != null && i < paramAnnotations.size() ? paramAnnotations.get(i) : List.of();
         GenericValueType paramGenericType = null;
         if (genericParamTypes != null && i < genericParamTypes.length) {
            GenericValueType pt = genericParamTypes[i];
            if (!pt.canBeRepresentedAsRaw() || !pt.asValueType().equals(method.parameterType(i))) {
               paramGenericType = pt;
            }
         }

         if (annots.isEmpty() && paramGenericType == null) {
            this.writer.print("{ 0 }");
         } else {
            this.writer.print("{");
            if (!annots.isEmpty()) {
               this.writer.print(" .annotations = ");
               this.generateAnnotations(annots);
            }

            if (paramGenericType != null) {
               this.writer.print(" .genericType = ");
               this.generateGenericType(paramGenericType, contextClass, method);
            }

            this.writer.print(" }");
         }
      }

      this.writer.println();
      this.writer.outdent().println("}");
      this.writer.outdent().print("}");
   }

   private void generateAnnotations(List<AnnotationReader> annotations) {
      this.writer.println("(TeaVM_AnnotationInfoList*) &(struct { int32_t count; TeaVM_AnnotationInfo data[" + annotations.size() + "]; }) {").indent();
      this.writer.print(".count = ").print(String.valueOf(annotations.size())).println(",");
      this.writer.println(".data = {").indent();
      this.generateAnnotation(annotations.get(0));

      for (int i = 1; i < annotations.size(); i++) {
         this.writer.println(",");
         this.generateAnnotation(annotations.get(i));
      }

      this.writer.println();
      this.writer.outdent().println("}");
      this.writer.outdent().print("}");
   }

   private void generateAnnotation(AnnotationReader annotation) {
      String implClass = annotation.getType() + "$$_impl";
      String dataClass = annotation.getType() + "$$_nativeData";
      MethodReference ctor = new MethodReference(implClass, "create", ValueType.object(dataClass), ValueType.object(implClass));
      this.includes.includeClass(dataClass);
      this.includes.includeClass(ctor.getClassName());
      this.writer.println("{").indent();
      this.writer.print(".constructor = &").print(this.context.getNames().forMethod(ctor)).println(",");
      this.writer.print(".data = ");
      this.generateAnnotationData(annotation);
      this.writer.println();
      this.writer.outdent().print("}");
   }

   private void generateAnnotationData(AnnotationReader annotation) {
      ClassReader cls = this.context.getClassSource().get(annotation.getType());
      if (cls == null) {
         this.writer.print("NULL");
      } else {
         List<MethodReader> fields = ClassGenerator.getAnnotationDataFields(this.context, cls);
         if (fields.isEmpty()) {
            this.writer.print("NULL");
         } else {
            String dataClass = annotation.getType() + "$$_nativeData";
            this.includes.includeClass(dataClass);
            this.writer.print("&(").print(this.context.getNames().forClass(dataClass)).println(") {").indent();
            boolean first = true;

            for (MethodReader method : fields) {
               if (!first) {
                  this.writer.println(",");
               }

               first = false;
               String fieldName = this.context.getNames().forMemberField(new FieldReference(dataClass, method.getName()));
               this.writer.print(".").print(fieldName).print(" = ");
               AnnotationValue value = annotation.getValue(method.getName());
               if (value == null) {
                  value = method.getAnnotationDefault();
               }

               this.generateAnnotationValue(method.getResultType(), value);
            }

            this.writer.outdent().print("}");
         }
      }
   }

   private void generateAnnotationValue(ValueType type, AnnotationValue value) {
      switch (value.getType()) {
         case 0:
            this.writer.print(value.getBoolean() ? "1" : "0");
            break;
         case 1:
            this.writer.print("(int8_t) ");
            this.writer.print(String.valueOf(value.getByte()));
            break;
         case 2:
            this.writer.print("(int16_t) ");
            this.writer.print(String.valueOf(value.getShort()));
            break;
         case 3:
            CodeGeneratorUtil.writeIntValue(this.writer, value.getInt());
            break;
         case 4:
            CodeGeneratorUtil.writeValue(this.writer, value.getLong());
            break;
         case 5:
            CodeGeneratorUtil.writeValue(this.writer, this.includes, value.getFloat());
            break;
         case 6:
            CodeGeneratorUtil.writeValue(this.writer, this.includes, value.getDouble());
            break;
         case 7:
            this.writer.print("(TeaVM_Object**) TEAVM_GET_STRING_ADDRESS(" + this.context.getStringPool().getStringIndex(value.getString()) + ")");
            break;
         case 8:
            this.writeType(value.getJavaClass());
            break;
         case 9:
            ValueType itemType = ((ValueType.Array)type).getItemType();
            List<AnnotationValue> list = value.getList();
            this.writer.print("(");
            ClassGenerator.generateAnnotationFieldType(this.context, this.includes, this.writer, type);
            this.writer.print(") &(struct { int32_t count; ");
            ClassGenerator.generateAnnotationFieldType(this.context, this.includes, this.writer, itemType);
            this.writer.println(" data[" + list.size() + "]; }) {").indent();
            this.writer.print(".count = ").print(String.valueOf(list.size())).println(",");
            this.writer.print(".data = {").indent();
            if (!list.isEmpty()) {
               this.writer.println();
               this.generateAnnotationValue(itemType, list.get(0));

               for (int i = 1; i < list.size(); i++) {
                  this.writer.println(",");
                  this.generateAnnotationValue(itemType, list.get(i));
               }

               this.writer.println();
            }

            this.writer.outdent().println("}");
            this.writer.outdent().print("}");
            break;
         case 10:
            ClassReader enumCls = this.context.getClassSource().get(value.getEnumValue().getClassName());
            if (enumCls == null) {
               this.writer.print("NULL");
            } else {
               int index = 0;

               for (FieldReader field : enumCls.getFields()) {
                  if (field.hasModifier(ElementModifier.ENUM)) {
                     if (field.getName().equals(value.getEnumValue().getFieldName())) {
                        break;
                     }

                     index++;
                  }
               }

               this.writer.print(String.valueOf(index));
            }
            break;
         case 11:
            this.generateAnnotationData(value.getAnnotation());
            break;
         case 12:
            this.writer.print("(uint16_t) ");
            CodeGeneratorUtil.writeIntValue(this.writer, value.getInt());
      }
   }

   private void writeType(ValueType cls) {
      int degree;
      for (degree = 0; cls instanceof ValueType.Array; degree++) {
         cls = ((ValueType.Array)cls).getItemType();
      }

      this.includes.includeType(cls);
      this.types.add(cls);
      this.writer
         .print("{ .baseClass = (TeaVM_Class*) &")
         .print(this.context.getNames().forClassInstance(cls))
         .print(", .arrayDegree = ")
         .print(String.valueOf(degree))
         .print(" }");
   }

   private List<AnnotationReader> extractAnnotations(ClassReader cls) {
      if (!this.context.getMetadataRequirements().hasGetAnnotations()) {
         return List.of();
      }

      ClassMetadataRequirements.Info info = this.context.getMetadataRequirements().getInfo(cls.getName());
      return info != null && info.annotations()
         ? AnnotationGenerationHelper.collectRuntimeAnnotations(this.context.getClassSource(), cls.getAnnotations().all())
         : List.of();
   }

   private List<FieldReader> extractFields(ClassReader cls) {
      Set<String> accessibleFields = this.reflection.getAccessibleFields(cls.getName());
      if (accessibleFields != null && !accessibleFields.isEmpty()) {
         boolean skipPrivates = ReflectionDependencyListener.shouldSkipPrivates(cls);
         ArrayList<FieldReader> fields = new ArrayList<>();

         for (FieldReader field : cls.getFields()) {
            if (accessibleFields.contains(field.getName())
               && (!skipPrivates || field.getLevel() != AccessLevel.PRIVATE && field.getLevel() != AccessLevel.PACKAGE_PRIVATE)) {
               fields.add(field);
            }
         }

         return fields;
      } else {
         return List.of();
      }
   }

   private List<MethodReader> extractMethods(ClassReader cls) {
      Set<MethodDescriptor> accessibleMethods = this.reflection.getAccessibleMethods(cls.getName());
      if (accessibleMethods != null && !accessibleMethods.isEmpty()) {
         boolean skipPrivates = ReflectionDependencyListener.shouldSkipPrivates(cls);
         ArrayList<MethodReader> methods = new ArrayList<>();

         for (MethodReader method : cls.getMethods()) {
            if (accessibleMethods.contains(method.getDescriptor())
               && (!skipPrivates || method.getLevel() != AccessLevel.PRIVATE && method.getLevel() != AccessLevel.PACKAGE_PRIVATE)) {
               methods.add(method);
            }
         }

         return methods;
      } else {
         return List.of();
      }
   }

   private GenericTypeParameter[] extractTypeParameters(ClassReader cls) {
      if (!this.needTypeParameters) {
         return new GenericTypeParameter[0];
      }

      GenericTypeParameter[] params = cls.getGenericParameters();
      return params != null ? params : new GenericTypeParameter[0];
   }

   private List<String> extractInnerClasses(ClassReader cls) {
      if (this.needInnerClasses && this.innerClassesAccessed.contains(cls.getName())) {
         ArrayList<String> result = new ArrayList<>();

         for (String innerCls : cls.getInnerClasses()) {
            if (this.context.getDependencies().getClass(innerCls) != null) {
               result.add(innerCls);
            }
         }

         return result;
      } else {
         return List.of();
      }
   }

   private void generateInnerClasses(List<String> innerClasses) {
      this.writer
         .print("(TeaVM_ClassRefList*) &(struct { int32_t count; TeaVM_Class* data[")
         .print(String.valueOf(innerClasses.size()))
         .println("];}) {")
         .indent();
      this.writer.print(".count = ").print(String.valueOf(innerClasses.size())).println(",");
      this.writer.print(".data = ").println("{").indent();

      for (int i = 0; i < innerClasses.size(); i++) {
         if (i > 0) {
            this.writer.println(",");
         }

         ValueType.Object innerType = ValueType.object(innerClasses.get(i));
         this.includes.includeType(innerType);
         this.types.add(innerType);
         this.writer.print("(TeaVM_Class*) &").print(this.context.getNames().forClassInstance(innerType));
      }

      this.writer.println();
      this.writer.outdent().println("}");
      this.writer.outdent().print("}");
   }

   private void generateTypeParameters(GenericTypeParameter[] params, ClassReader cls) {
      this.generateTypeParameters(params, cls, null);
   }

   private void generateTypeParameters(GenericTypeParameter[] params, ClassReader cls, MethodReader method) {
      this.writer
         .print("(TeaVM_TypeVariableInfoList*) &(struct { int32_t count; TeaVM_TypeVariableInfo data[")
         .print(String.valueOf(params.length))
         .println("]; }) {")
         .indent();
      this.writer.print(".count = ").print(String.valueOf(params.length)).println(",");
      this.writer.print(".data = ").println("{").indent();

      for (int i = 0; i < params.length; i++) {
         if (i > 0) {
            this.writer.println(",");
         }

         GenericTypeParameter param = params[i];
         int nameIndex = this.context.getStringPool().getStringIndex(param.getName());
         this.writer.print("{ .name = TEAVM_GET_STRING_ADDRESS(").print(String.valueOf(nameIndex)).print(")");
         if (this.needBounds) {
            List<GenericValueType.Reference> bounds = param.extractAllBounds();
            if (!bounds.isEmpty()) {
               this.writer.println(",");
               this.writer.print(".boundCount = ").print(String.valueOf(bounds.size())).println(",");
               this.writer.print(".bounds = ");
               this.generateBounds(bounds, cls, method);
            }
         }

         this.writer.print(" }");
      }

      this.writer.println();
      this.writer.outdent().println("}");
      this.writer.outdent().print("}");
   }

   private void generateBounds(List<GenericValueType.Reference> bounds, ClassReader contextClass, MethodReader contextMethod) {
      this.writer.print("(TeaVM_GenericTypeInfo*[").print(String.valueOf(bounds.size())).print("]) {").indent();

      for (int i = 0; i < bounds.size(); i++) {
         this.writer.println();
         if (i > 0) {
            this.writer.println(",");
         }

         this.generateGenericType(bounds.get(i), contextClass, contextMethod);
      }

      this.writer.println();
      this.writer.outdent().print("}");
   }

   private void generateGenericType(GenericValueType type, ClassReader contextClass, MethodReader contextMethod) {
      this.includes.includePath("reflection.h");
      if (type.canBeRepresentedAsRaw()) {
         this.generateRawType(type.asValueType());
      } else if (type instanceof GenericValueType.Object objectType) {
         this.generateParameterizedType(objectType, contextClass, contextMethod);
      } else if (type instanceof GenericValueType.Variable varType) {
         this.generateTypeVariableRef(varType.getName(), contextClass, contextMethod);
      } else {
         if (!(type instanceof GenericValueType.Array arrayType)) {
            throw new IllegalArgumentException("Unsupported generic type: " + type);
         }

         this.writer.print("&(TeaVM_GenericTypeInfo) { .kind = 2, .itemType = ");
         this.generateGenericType(arrayType.getItemType(), contextClass, contextMethod);
         this.writer.print(" }");
      }
   }

   private void generateParameterizedType(GenericValueType.Object objectType, ClassReader contextClass, MethodReader contextMethod) {
      ValueType.Object vt = ValueType.object(objectType.getFullClassName());
      this.includes.includeType(vt);
      this.types.add(vt);
      GenericValueType.Argument[] args = objectType.getArguments();
      this.writer.print("&(TeaVM_GenericTypeInfo) { .kind = 0, .parameterized = {").indent();
      this.writer.println();
      this.writer.print(".rawType = (TeaVM_Class*) &").print(this.context.getNames().forClassInstance(vt)).println(",");
      this.writer.print(".actualTypeArgumentCount = ").print(String.valueOf(args.length)).println(",");
      this.writer.print(".actualTypeArguments = ");
      if (args.length > 0) {
         this.writer.print("(TeaVM_GenericTypeInfo*[").print(String.valueOf(args.length)).print("]) {").indent();

         for (int i = 0; i < args.length; i++) {
            this.writer.println();
            if (i > 0) {
               this.writer.println(",");
            }

            this.generateTypeArgument(args[i], contextClass, contextMethod);
         }

         this.writer.println();
         this.writer.outdent().print("}");
      } else {
         this.writer.print("NULL");
      }

      this.writer.println(",");
      GenericValueType.Object parent = objectType.getParent();
      if (parent != null) {
         this.writer.print(".ownerType = ");
         this.generateGenericType(parent, contextClass, contextMethod);
      } else {
         this.writer.print(".ownerType = NULL");
      }

      this.writer.println();
      this.writer.outdent().print("} }");
   }

   private void generateTypeArgument(GenericValueType.Argument arg, ClassReader contextClass, MethodReader contextMethod) {
      switch (arg.getKind()) {
         case INVARIANT:
            this.generateGenericType(arg.getValue(), contextClass, contextMethod);
            break;
         case ANY:
            this.writer.print("&(TeaVM_GenericTypeInfo) { .kind = 5 }");
            break;
         case COVARIANT:
            this.writer.print("&(TeaVM_GenericTypeInfo) { .kind = 3, .bound = ");
            this.generateGenericType(arg.getValue(), contextClass, contextMethod);
            this.writer.print(" }");
            break;
         case CONTRAVARIANT:
            this.writer.print("&(TeaVM_GenericTypeInfo) { .kind = 4, .bound = ");
            this.generateGenericType(arg.getValue(), contextClass, contextMethod);
            this.writer.print(" }");
            break;
         default:
            throw new IllegalArgumentException("Unknown argument kind: " + arg.getKind());
      }
   }

   private void generateTypeVariableRef(String varName, ClassReader contextClass, MethodReader contextMethod) {
      int level = 0;
      if (contextMethod != null) {
         GenericTypeParameter[] params = contextMethod.getTypeParameters();

         for (int i = 0; i < params.length; i++) {
            if (params[i].getName().equals(varName)) {
               this.writeTypeVariableRef(0, i);
               return;
            }
         }

         level++;
      }

      ClassReader currentClass = contextClass;

      while (currentClass != null) {
         GenericTypeParameter[] params = currentClass.getGenericParameters();
         if (params != null) {
            for (int i = 0; i < params.length; i++) {
               if (params[i].getName().equals(varName)) {
                  this.writeTypeVariableRef(level, i);
                  return;
               }
            }
         }

         level++;
         if (currentClass.getOwnerName() == null) {
            break;
         }

         currentClass = this.context.getClassSource().get(currentClass.getOwnerName());
      }

      throw new IllegalArgumentException("Unknown type variable: " + varName);
   }

   private void writeTypeVariableRef(int level, int index) {
      this.writer
         .print("&(TeaVM_GenericTypeInfo) { .kind = 1, .typeVar = { .level = ")
         .print(String.valueOf(level))
         .print(", .index = ")
         .print(String.valueOf(index))
         .print(" } }");
   }

   private void generateRawType(ValueType type) {
      this.includes.includePath("reflection.h");
      int degree = 0;

      ValueType current;
      for (current = type; current instanceof ValueType.Array; degree++) {
         current = ((ValueType.Array)current).getItemType();
      }

      this.includes.includeType(current);
      this.types.add(current);
      this.writer
         .print("&(TeaVM_GenericTypeInfo) { .kind = 6, .classPtr = ")
         .print("{ .baseClass = (TeaVM_Class*) &")
         .print(this.context.getNames().forClassInstance(current))
         .print(", .arrayDegree = ")
         .print(String.valueOf(degree))
         .print(" } }");
   }
}
