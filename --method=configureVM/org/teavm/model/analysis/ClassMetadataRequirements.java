package org.teavm.model.analysis;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.teavm.dependency.DependencyInfo;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class ClassMetadataRequirements {
   private static final MethodReference GET_NAME_METHOD = new MethodReference(Class.class, "getName", String.class);
   private static final MethodReference GET_SIMPLE_NAME_METHOD = new MethodReference(Class.class, "getSimpleName", String.class);
   private static final MethodReference GET_SUPERCLASS_METHOD = new MethodReference(Class.class, "getSuperclass", Class.class);
   private static final MethodReference IS_ASSIGNABLE_METHOD = new MethodReference(Class.class, "isAssignableFrom", Class.class, boolean.class);
   private static final MethodReference GET_DECLARING_CLASS_METHOD = new MethodReference(Class.class, "getDeclaringClass", Class.class);
   private static final MethodReference GET_ENCLOSING_CLASS_METHOD = new MethodReference(Class.class, "getEnclosingClass", Class.class);
   private static final MethodReference NEW_ARRAY = new MethodReference(Array.class, "newInstance", Class.class, int.class, Object.class);
   private static final MethodReference ARRAY_GET = new MethodReference(Array.class, "get", Object.class, int.class, Object.class);
   private static final MethodReference ARRAY_SET = new MethodReference(Array.class, "set", Object.class, int.class, Object.class, void.class);
   private static final MethodReference ARRAY_LENGTH = new MethodReference(Array.class, "getLength", Object.class, int.class);
   private static final MethodReference ARRAY_COPY = new MethodReference(
      System.class, "arraycopy", Object.class, int.class, Object.class, int.class, int.class, void.class
   );
   static final MethodReference typeVarConstructor = new MethodReference(
      "java.lang.reflect.TypeVariableImpl", "create", ValueType.object("java.lang.String"), ValueType.object("java.lang.reflect.TypeVariableImpl")
   );
   private static final ClassMetadataRequirements.ClassInfo EMPTY_INFO = new ClassMetadataRequirements.ClassInfo();
   private Map<ValueType, ClassMetadataRequirements.ClassInfo> requirements = new HashMap<>();
   private boolean hasArrayGet;
   private boolean hasArraySet;
   private boolean hasArrayLength;
   private boolean hasArrayCopy;
   private boolean hasEnumConstants;
   private boolean hasSuperclass;
   private boolean hasIsAssignable;
   private boolean hasArrayNewInstance;
   private boolean hasNewInstance;
   private boolean hasClassInit;
   private boolean hasEnclosingClass;
   private boolean hasDeclaringClass;
   private boolean hasSimpleName;
   private boolean hasName;
   private boolean hasGetAnnotations;
   private boolean hasGetInterfaces;
   private boolean hasGetFields;
   private boolean hasGetMethods;
   private boolean hasGenerics;

   public ClassMetadataRequirements(DependencyInfo dependencyInfo) {
      MethodDependencyInfo getNameMethod = dependencyInfo.getMethod(GET_NAME_METHOD);
      if (getNameMethod != null) {
         this.hasName = true;
         this.addClassesRequiringName(this.requirements, getNameMethod.getVariable(0).getClassValueNode().getTypes());
      }

      MethodDependencyInfo getSimpleNameMethod = dependencyInfo.getMethod(GET_SIMPLE_NAME_METHOD);
      if (getSimpleNameMethod != null) {
         this.hasSimpleName = true;
         ValueType[] types = getSimpleNameMethod.getVariable(0).getClassValueNode().getTypes();
         this.addClassesRequiringName(this.requirements, types);

         for (ValueType type : types) {
            ClassMetadataRequirements.ClassInfo classInfo = this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo());
            classInfo.simpleName = true;
            classInfo.enclosingClass = true;
         }
      }

      MethodDependencyInfo getSuperclassMethod = dependencyInfo.getMethod(GET_SUPERCLASS_METHOD);
      if (getSuperclassMethod != null) {
         this.hasSuperclass = true;
         ValueType[] types = getSuperclassMethod.getVariable(0).getClassValueNode().getTypes();

         for (ValueType type : types) {
            this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo()).superclass = true;
         }
      }

      MethodDependencyInfo isAssignableMethod = dependencyInfo.getMethod(IS_ASSIGNABLE_METHOD);
      if (isAssignableMethod != null) {
         this.hasIsAssignable = true;
         ValueType[] types = isAssignableMethod.getVariable(0).getClassValueNode().getTypes();

         for (ValueType type : types) {
            this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo()).isAssignable = true;
         }
      }

      MethodDependencyInfo getDeclaringClassMethod = dependencyInfo.getMethod(GET_DECLARING_CLASS_METHOD);
      if (getDeclaringClassMethod != null) {
         this.hasDeclaringClass = true;
         ValueType[] types = getDeclaringClassMethod.getVariable(0).getClassValueNode().getTypes();

         for (ValueType type : types) {
            this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo()).declaringClass = true;
         }
      }

      MethodDependencyInfo getEnclosingClassMethod = dependencyInfo.getMethod(GET_ENCLOSING_CLASS_METHOD);
      if (getEnclosingClassMethod != null) {
         this.hasEnclosingClass = true;
         ValueType[] types = getEnclosingClassMethod.getVariable(0).getClassValueNode().getTypes();

         for (ValueType type : types) {
            this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo()).enclosingClass = true;
         }
      }

      MethodDependencyInfo newArrayMethod = dependencyInfo.getMethod(NEW_ARRAY);
      if (newArrayMethod != null) {
         this.hasArrayNewInstance = true;
         ValueType[] types = newArrayMethod.getVariable(1).getClassValueNode().getTypes();

         for (ValueType type : types) {
            this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo()).newArray = true;
         }
      }

      MethodDependencyInfo arrayGet = dependencyInfo.getMethod(ARRAY_GET);
      if (arrayGet != null) {
         this.hasArrayGet = arrayGet.isUsed();
         ValueType[] types = arrayGet.getVariable(1).getTypes();

         for (ValueType type : types) {
            this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo()).arrayGet = true;
         }
      }

      MethodDependencyInfo arraySet = dependencyInfo.getMethod(ARRAY_SET);
      if (arraySet != null) {
         this.hasArraySet = arraySet.isUsed();
         ValueType[] types = arraySet.getVariable(1).getTypes();

         for (ValueType type : types) {
            this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo()).arraySet = true;
         }
      }

      MethodDependencyInfo arrayLength = dependencyInfo.getMethod(ARRAY_LENGTH);
      if (arrayLength != null) {
         this.hasArrayLength = arrayLength.isUsed();
         ValueType[] types = arrayLength.getVariable(1).getTypes();

         for (ValueType type : types) {
            this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo()).arrayLength = true;
         }
      }

      MethodDependencyInfo arrayCopy = dependencyInfo.getMethod(ARRAY_COPY);
      if (arrayCopy != null) {
         this.hasArrayCopy = arrayCopy.isUsed();
         ValueType[] types = arrayCopy.getVariable(1).getTypes();

         for (ValueType type : types) {
            this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo()).arrayCopy = true;
         }
      }

      MethodDependencyInfo clone = dependencyInfo.getMethod(new MethodReference(Object.class, "cloneObject", Object.class));
      if (clone != null) {
         ValueType[] types = clone.getVariable(0).getTypes();

         for (ValueType type : types) {
            this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo()).cloneMethod = true;
         }
      }

      MethodDependencyInfo enumConstantsDep = dependencyInfo.getMethod(new MethodReference(Class.class, "getEnumConstantsFast", Object[].class));
      if (enumConstantsDep != null && enumConstantsDep.isUsed()) {
         this.hasEnumConstants = true;
         ValueType[] types = enumConstantsDep.getVariable(0).getClassValueNode().getTypes();

         for (ValueType type : types) {
            this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo()).enumConstants = true;
         }
      }

      MethodDependencyInfo getAnnotations = dependencyInfo.getMethod(new MethodReference(Class.class, "getDeclaredAnnotations", Annotation[].class));
      if (getAnnotations != null && getAnnotations.isUsed()) {
         this.hasGetAnnotations = true;
         ValueType[] types = getAnnotations.getVariable(0).getClassValueNode().getTypes();

         for (ValueType type : types) {
            this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo()).annotations = true;
         }
      }

      MethodDependencyInfo getInterfaces = dependencyInfo.getMethod(new MethodReference(Class.class, "getInterfaces", Class[].class));
      if (getInterfaces != null && getInterfaces.isUsed()) {
         this.hasGetInterfaces = true;
         ValueType[] types = getInterfaces.getVariable(0).getClassValueNode().getTypes();

         for (ValueType type : types) {
            this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo()).interfaces = true;
         }
      }

      MethodDependencyInfo getFields = dependencyInfo.getMethod(new MethodReference(Class.class, "getDeclaredFields", Field[].class));
      if (getFields != null && getFields.isUsed()) {
         this.hasGetFields = true;
      }

      MethodDependencyInfo getMethods = dependencyInfo.getMethod(new MethodReference(Class.class, "getDeclaredMethods", Method[].class));
      if (getMethods != null && getMethods.isUsed()) {
         this.hasGetMethods = true;
      }

      MethodDependencyInfo getConstructors = dependencyInfo.getMethod(new MethodReference(Class.class, "getDeclaredConstructors", Constructor[].class));
      if (getConstructors != null && getConstructors.isUsed()) {
         this.hasGetMethods = true;
      }

      MethodDependencyInfo newInstance = dependencyInfo.getMethod(new MethodReference(Class.class, "newInstance", Object.class));
      if (newInstance != null && newInstance.isUsed()) {
         this.hasNewInstance = true;
      }

      MethodDependencyInfo classInit = dependencyInfo.getMethod(new MethodReference(Class.class, "initialize", void.class));
      if (classInit != null && classInit.isUsed()) {
         this.hasClassInit = true;
         ValueType[] types = classInit.getVariable(0).getClassValueNode().getTypes();

         for (ValueType type : types) {
            this.requirements.computeIfAbsent(type, k -> new ClassMetadataRequirements.ClassInfo()).classInit = true;
         }
      }

      this.hasGenerics = dependencyInfo.getMethod(typeVarConstructor) != null;
   }

   public ClassMetadataRequirements.Info getInfo(String className) {
      return this.getInfo(ValueType.object(className));
   }

   public ClassMetadataRequirements.Info getInfo(ValueType className) {
      ClassMetadataRequirements.ClassInfo result = this.requirements.get(className);
      if (result == null) {
         result = EMPTY_INFO;
      }

      return result;
   }

   public boolean hasArrayGet() {
      return this.hasArrayGet;
   }

   public boolean hasArraySet() {
      return this.hasArraySet;
   }

   public boolean hasArrayLength() {
      return this.hasArrayLength;
   }

   public boolean hasArrayCopy() {
      return this.hasArrayCopy;
   }

   public boolean hasEnumConstants() {
      return this.hasEnumConstants;
   }

   public boolean hasSuperclass() {
      return this.hasSuperclass;
   }

   public boolean hasIsAssignable() {
      return this.hasIsAssignable;
   }

   public boolean hasArrayNewInstance() {
      return this.hasArrayNewInstance;
   }

   public boolean hasEnclosingClass() {
      return this.hasEnclosingClass;
   }

   public boolean hasDeclaringClass() {
      return this.hasDeclaringClass;
   }

   public boolean hasSimpleName() {
      return this.hasSimpleName;
   }

   public boolean hasName() {
      return this.hasName;
   }

   public boolean hasGetAnnotations() {
      return this.hasGetAnnotations;
   }

   public boolean hasGetInterfaces() {
      return this.hasGetInterfaces;
   }

   public boolean hasGetFields() {
      return this.hasGetFields;
   }

   public boolean hasGetMethods() {
      return this.hasGetMethods;
   }

   public boolean hasNewInstance() {
      return this.hasNewInstance;
   }

   public boolean hasClassInit() {
      return this.hasClassInit;
   }

   public boolean hasGenerics() {
      return this.hasGenerics;
   }

   private void addClassesRequiringName(Map<ValueType, ClassMetadataRequirements.ClassInfo> target, ValueType[] source) {
      for (ValueType typeName : source) {
         target.computeIfAbsent(typeName, k -> new ClassMetadataRequirements.ClassInfo()).name = true;
      }
   }

   static class ClassInfo implements ClassMetadataRequirements.Info {
      boolean name;
      boolean simpleName;
      boolean declaringClass;
      boolean enclosingClass;
      boolean superclass;
      boolean isAssignable;
      boolean newArray;
      boolean arrayLength;
      boolean arrayGet;
      boolean arraySet;
      boolean arrayCopy;
      boolean cloneMethod;
      boolean enumConstants;
      boolean annotations;
      boolean interfaces;
      boolean classInit;

      @Override
      public boolean name() {
         return this.name;
      }

      @Override
      public boolean simpleName() {
         return this.simpleName;
      }

      @Override
      public boolean declaringClass() {
         return this.declaringClass;
      }

      @Override
      public boolean enclosingClass() {
         return this.enclosingClass;
      }

      @Override
      public boolean superclass() {
         return this.superclass;
      }

      @Override
      public boolean isAssignable() {
         return this.isAssignable;
      }

      @Override
      public boolean newArray() {
         return this.newArray;
      }

      @Override
      public boolean arrayLength() {
         return this.arrayLength;
      }

      @Override
      public boolean arrayCopy() {
         return this.arrayCopy;
      }

      @Override
      public boolean arrayGet() {
         return this.arrayGet;
      }

      @Override
      public boolean arraySet() {
         return this.arraySet;
      }

      @Override
      public boolean cloneMethod() {
         return this.cloneMethod;
      }

      @Override
      public boolean enumConstants() {
         return this.enumConstants;
      }

      @Override
      public boolean annotations() {
         return this.annotations;
      }

      @Override
      public boolean interfaces() {
         return this.interfaces;
      }

      @Override
      public boolean classInit() {
         return this.classInit;
      }
   }

   public interface Info {
      boolean name();

      boolean simpleName();

      boolean declaringClass();

      boolean enclosingClass();

      boolean superclass();

      boolean isAssignable();

      boolean newArray();

      boolean arrayLength();

      boolean arrayGet();

      boolean arraySet();

      boolean arrayCopy();

      boolean cloneMethod();

      boolean enumConstants();

      boolean annotations();

      boolean interfaces();

      boolean classInit();
   }
}
