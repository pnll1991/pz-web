package org.teavm.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.teavm.common.OptionalPredicate;
import org.teavm.hppc.ObjectByteHashMap;
import org.teavm.hppc.ObjectByteMap;

public class ClassHierarchy {
   private final ClassReaderSource classSource;
   private final Map<String, OptionalPredicate<ValueType>> superclassPredicateCache = new HashMap<>();
   private final Map<String, Map<MethodDescriptor, Optional<MethodReader>>> resolveMethodCache = new HashMap<>();
   private final Map<String, Map<String, Optional<FieldReader>>> resolveFieldCache = new HashMap<>();
   OptionalPredicate<ValueType> objectPredicate = (value, defaultResult) -> !(value instanceof ValueType.Primitive);

   public ClassHierarchy(ClassReaderSource classSource) {
      this.classSource = classSource;
      this.superclassPredicateCache.put("java.lang.Object", (c, d) -> true);
   }

   public ClassReaderSource getClassSource() {
      return this.classSource;
   }

   public boolean isSuperType(ValueType superType, ValueType subType, boolean defaultValue) {
      if (superType.equals(subType)) {
         return true;
      } else if (superType instanceof ValueType.Primitive || subType instanceof ValueType.Primitive) {
         return false;
      } else if (superType.isObject("java.lang.Object")) {
         return true;
      } else if (superType instanceof ValueType.Object && subType instanceof ValueType.Object) {
         return this.isSuperType(((ValueType.Object)superType).getClassName(), ((ValueType.Object)subType).getClassName(), defaultValue);
      } else {
         return superType instanceof ValueType.Array & subType instanceof ValueType.Array
            ? this.isSuperType(((ValueType.Array)superType).getItemType(), ((ValueType.Array)subType).getItemType(), defaultValue)
            : false;
      }
   }

   public boolean isSuperType(String superType, String subType, boolean defaultValue) {
      return subType.equals(superType) ? true : this.getSuperclassPredicate(superType).test(ValueType.object(subType), defaultValue);
   }

   public MethodReader resolve(MethodReference method) {
      return this.resolve(method.getClassName(), method.getDescriptor());
   }

   public MethodReader resolve(String className, MethodDescriptor method) {
      Map<MethodDescriptor, Optional<MethodReader>> cache = this.resolveMethodCache.computeIfAbsent(className, k -> new HashMap<>());
      Optional<MethodReader> opt = cache.get(method);
      if (opt == null) {
         MethodReader reader = null;
         ClassReader cls = this.classSource.get(className);
         if (cls != null) {
            reader = cls.getMethod(method);
            if (reader == null && cls.getParent() != null) {
               reader = this.resolve(cls.getParent(), method);
            }

            if (reader == null) {
               for (String itf : cls.getInterfaces()) {
                  reader = this.resolve(itf, method);
                  if (reader != null) {
                     break;
                  }
               }
            }
         }

         opt = Optional.ofNullable(reader);
         cache.put(method, opt);
      }

      return opt.orElse(null);
   }

   public FieldReader resolve(FieldReference field) {
      return this.resolve(field.getClassName(), field.getFieldName());
   }

   public FieldReader resolve(String className, String fieldName) {
      Map<String, Optional<FieldReader>> cache = this.resolveFieldCache.computeIfAbsent(className, k -> new HashMap<>());
      Optional<FieldReader> opt = cache.get(fieldName);
      if (opt == null) {
         FieldReader reader = null;
         ClassReader cls = this.classSource.get(className);
         if (cls != null) {
            if (cls.getParent() != null) {
               reader = this.resolve(cls.getParent(), fieldName);
            }

            if (reader == null) {
               for (String itf : cls.getInterfaces()) {
                  reader = this.resolve(itf, fieldName);
                  if (reader != null) {
                     break;
                  }
               }
            }
         }

         opt = Optional.ofNullable(reader);
         cache.put(fieldName, opt);
      }

      return opt.orElse(null);
   }

   public OptionalPredicate<ValueType> getSuperclassPredicate(String superclass) {
      return superclass.equals("java.lang.Object")
         ? this.objectPredicate
         : this.superclassPredicateCache.computeIfAbsent(superclass, x$0 -> new ClassHierarchy.SuperclassPredicate(x$0));
   }

   public OptionalPredicate<ValueType> getSupertypePredicate(ValueType supertype) {
      if (supertype instanceof ValueType.Object) {
         return this.getSuperclassPredicate(((ValueType.Object)supertype).getClassName());
      } else {
         return supertype instanceof ValueType.Array
            ? new ClassHierarchy.SuperArrayPredicate(this.getSupertypePredicate(((ValueType.Array)supertype).getItemType()))
            : (value, defaultResult) -> value == supertype;
      }
   }

   private static class SuperArrayPredicate implements OptionalPredicate<ValueType> {
      private OptionalPredicate<ValueType> itemPredicate;

      SuperArrayPredicate(OptionalPredicate<ValueType> itemPredicate) {
         this.itemPredicate = itemPredicate;
      }

      public boolean test(ValueType value, boolean defaultResult) {
         return value instanceof ValueType.Array ? this.itemPredicate.test(((ValueType.Array)value).getItemType(), defaultResult) : false;
      }
   }

   private class SuperclassPredicate implements OptionalPredicate<ValueType> {
      private final String superclass;
      private final ObjectByteMap<String> cache = new ObjectByteHashMap(100, 0.5);

      SuperclassPredicate(String superclass) {
         this.superclass = superclass;
      }

      public boolean test(ValueType value, boolean defaultResult) {
         if (!(value instanceof ValueType.Object)) {
            return false;
         }

         String className = ((ValueType.Object)value).getClassName();
         switch (this.test(className)) {
            case 1:
               return true;
            case 2:
               return false;
            default:
               return defaultResult;
         }
      }

      byte test(String value) {
         if (value.equals(this.superclass)) {
            return 1;
         }

         byte result = this.cache.get(value);
         if (result == 0) {
            result = this.testCacheMiss(value);
            this.cache.put(value, result);
         }

         return result;
      }

      byte testCacheMiss(String value) {
         if (value.equals(this.superclass)) {
            return 1;
         }

         ClassReader cls = ClassHierarchy.this.classSource.get(value);
         if (cls == null) {
            return 2;
         }

         if (cls.getParent() != null && this.test(cls.getParent()) == 1) {
            return 1;
         }

         for (String itf : cls.getInterfaces()) {
            if (this.test(itf) == 1) {
               return 1;
            }
         }

         return 2;
      }
   }
}
