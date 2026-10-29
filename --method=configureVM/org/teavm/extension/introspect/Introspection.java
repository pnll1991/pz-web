package org.teavm.extension.introspect;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.ElementReader;
import org.teavm.model.GenericValueType;
import org.teavm.model.ValueType;

public class Introspection implements IntrospectWorld {
   private static final int RECORD = 65536;
   private ClassReaderSource classSource;
   private ClassHierarchy hierarchy;
   private Map<ValueType, IntrospectClassImpl<?>> classes = new HashMap<>();
   private ClassLoader classLoader;

   public Introspection(ClassHierarchy hierarchy, ClassLoader classLoader) {
      this.classSource = hierarchy.getClassSource();
      this.hierarchy = hierarchy;
      this.classLoader = classLoader;
   }

   public ClassLoader getClassLoader() {
      return this.classLoader;
   }

   public ClassReaderSource getClassSource() {
      return this.classSource;
   }

   public ClassHierarchy getHierarchy() {
      return this.hierarchy;
   }

   public IntrospectClassImpl<?> getClass(ValueType type) {
      return this.classes.computeIfAbsent(type, t -> new IntrospectClassImpl(type, this));
   }

   public <T> IntrospectClassImpl<T> findClass(Class<T> cls) {
      return (IntrospectClassImpl<T>)this.getClass(ValueType.parse(cls));
   }

   public IntrospectClassImpl<?> findClass(String name) {
      return this.classSource.get(name) == null ? null : this.getClass(ValueType.object(name));
   }

   IntrospectType convertGenericType(GenericValueType type, IntrospectClassImpl<?> declaringClass, IntrospectMethodImpl declaringMethod) {
      if (type instanceof GenericValueType.Primitive || type instanceof GenericValueType.Void) {
         return this.getClass(type.asValueType());
      }

      if (type instanceof GenericValueType.Variable) {
         String name = ((GenericValueType.Variable)type).getName();
         if (declaringMethod != null) {
            IntrospectTypeVariableImpl tv = declaringMethod.typeParameter(name);
            if (tv != null) {
               return tv;
            }
         }

         if (declaringClass != null) {
            IntrospectTypeVariableImpl tv = declaringClass.typeParameter(name);
            if (tv != null) {
               return tv;
            }
         }

         return new IntrospectTypeVariableImpl(name);
      } else if (type instanceof GenericValueType.Object obj) {
         return (IntrospectType)(obj.getArguments().length == 0 && obj.getParent() == null
            ? this.getClass(ValueType.object(obj.getClassName()))
            : new IntrospectParameterizedTypeImpl(obj, declaringClass, declaringMethod, this));
      } else if (type instanceof GenericValueType.Array arr) {
         ValueType rawType = arr.asValueType();
         return (IntrospectType)(rawType != null ? this.getClass(rawType) : new IntrospectGenericArrayImpl(arr, declaringClass, declaringMethod, this));
      } else {
         throw new AssertionError("Unknown generic type: " + type);
      }
   }

   public static int getModifiers(ElementReader element) {
      int modifiers = 0;
      switch (element.getLevel()) {
         case PUBLIC:
            modifiers |= 1;
            break;
         case PROTECTED:
            modifiers |= 4;
            break;
         case PRIVATE:
            modifiers |= 2;
         case PACKAGE_PRIVATE:
      }

      Set<ElementModifier> modifierSet = element.readModifiers();
      if (modifierSet.contains(ElementModifier.ABSTRACT)) {
         modifiers |= 1024;
      }

      if (modifierSet.contains(ElementModifier.FINAL)) {
         modifiers |= 16;
      }

      if (modifierSet.contains(ElementModifier.INTERFACE)) {
         modifiers |= 512;
      }

      if (modifierSet.contains(ElementModifier.NATIVE)) {
         modifiers |= 256;
      }

      if (modifierSet.contains(ElementModifier.STATIC)) {
         modifiers |= 8;
      }

      if (modifierSet.contains(ElementModifier.STRICT)) {
         modifiers |= 2048;
      }

      if (modifierSet.contains(ElementModifier.SYNCHRONIZED)) {
         modifiers |= 32;
      }

      if (modifierSet.contains(ElementModifier.TRANSIENT)) {
         modifiers |= 128;
      }

      if (modifierSet.contains(ElementModifier.VOLATILE)) {
         modifiers |= 64;
      }

      if (modifierSet.contains(ElementModifier.RECORD)) {
         modifiers |= 65536;
      }

      return modifiers;
   }
}
