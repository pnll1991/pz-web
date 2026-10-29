package org.teavm.model;

import java.util.HashMap;
import java.util.Map;

public class ReferenceCache {
   private Map<String, Map<MethodDescriptor, MethodReference>> referenceCache = new HashMap<>();
   private Map<FieldReference, FieldReference> fieldRefenceCache = new HashMap<>();
   private Map<MethodDescriptor, MethodDescriptor> descriptorCache = new HashMap<>();
   private Map<ValueType, ValueType> valueTypeCache = new HashMap<>();
   private Map<GenericValueType, GenericValueType> genericValueTypeCache = new HashMap<>();
   private Map<String, String> stringCache = new HashMap<>();
   private Map<String, MethodDescriptor> descriptorParseCache = new HashMap<>();
   private Map<String, ValueType> valueTypeParseCache = new HashMap<>();
   private Map<String, GenericValueType> genericValueTypeParseCache = new HashMap<>();

   public MethodReference getCached(MethodReference reference) {
      return this.getCached(reference.getClassName(), reference.getDescriptor());
   }

   public MethodReference getCached(String className, MethodDescriptor descriptor) {
      return this.referenceCache
         .computeIfAbsent(className, key -> new HashMap<>())
         .computeIfAbsent(this.getCached(descriptor), key -> new MethodReference(className, key));
   }

   public MethodDescriptor getCached(MethodDescriptor descriptor) {
      MethodDescriptor result = this.descriptorCache.get(descriptor);
      if (result == null) {
         result = descriptor;
         ValueType[] signature = descriptor.getSignature();
         boolean signatureChanged = false;

         for (int i = 0; i < signature.length; i++) {
            ValueType type = signature[i];
            if (type != null) {
               ValueType cachedType = this.getCached(type);
               if (type != cachedType) {
                  signatureChanged = true;
                  signature[i] = cachedType;
               }
            }
         }

         if (signatureChanged) {
            result = new MethodDescriptor(descriptor.getName(), signature);
         }

         this.descriptorCache.put(result, result);
      }

      return result;
   }

   public FieldReference getCached(FieldReference reference) {
      FieldReference result = this.fieldRefenceCache.get(reference);
      if (result == null) {
         result = reference;
         String classNameCached = this.getCached(reference.getClassName());
         String fieldNameCached = this.getCached(reference.getFieldName());
         if (classNameCached != reference.getClassName() || fieldNameCached != reference.getFieldName()) {
            result = new FieldReference(classNameCached, fieldNameCached);
         }

         this.fieldRefenceCache.put(result, result);
      }

      return result;
   }

   public ValueType getCached(ValueType valueType) {
      if (valueType instanceof ValueType.Primitive) {
         return valueType;
      }

      ValueType result = this.valueTypeCache.get(valueType);
      if (result == null) {
         result = valueType;
         if (result instanceof ValueType.Object) {
            String className = ((ValueType.Object)result).getClassName();
            String cachedClassName = this.getCached(className);
            if (cachedClassName != className) {
               result = ValueType.object(cachedClassName);
            }
         } else if (result instanceof ValueType.Array) {
            ValueType item = ((ValueType.Array)result).getItemType();
            ValueType cachedItem = this.getCached(item);
            if (item != cachedItem) {
               result = ValueType.arrayOf(cachedItem);
            }
         }

         this.valueTypeCache.put(result, result);
      }

      return result;
   }

   public GenericValueType getCached(GenericValueType valueType) {
      if (!(valueType instanceof GenericValueType.Primitive)
         && !(valueType instanceof GenericValueType.Variable)
         && !(valueType instanceof GenericValueType.Void)) {
         GenericValueType result = this.genericValueTypeCache.get(valueType);
         if (result == null) {
            result = valueType;
            if (!(result instanceof GenericValueType.Object objectType)) {
               if (result instanceof GenericValueType.Array) {
                  GenericValueType item = ((GenericValueType.Array)result).getItemType();
                  GenericValueType cachedItem = this.getCached(item);
                  if (item != cachedItem) {
                     result = new GenericValueType.Array(cachedItem);
                  }
               }
            } else {
               String className = objectType.getClassName();
               String cachedClassName = this.getCached(className);
               boolean changed = false;
               GenericValueType.Argument[] arguments = objectType.getArguments();

               for (int i = 0; i < arguments.length; i++) {
                  GenericValueType.Argument argument = arguments[i];
                  if (argument.getValue() != null) {
                     GenericValueType.Reference cachedValue = (GenericValueType.Reference)this.getCached(argument.getValue());
                     if (cachedValue != argument.getValue()) {
                        changed = true;
                        switch (argument.getKind()) {
                           case COVARIANT:
                              argument = GenericValueType.Argument.covariant(cachedValue);
                              break;
                           case CONTRAVARIANT:
                              argument = GenericValueType.Argument.contravariant(cachedValue);
                              break;
                           case INVARIANT:
                              argument = GenericValueType.Argument.invariant(cachedValue);
                        }

                        arguments[i] = argument;
                     }
                  }
               }

               GenericValueType.Object parent = objectType.getParent();
               GenericValueType.Object cachedParent = parent != null ? (GenericValueType.Object)this.getCached(parent) : null;
               if (changed || className != cachedClassName || parent != cachedParent) {
                  result = new GenericValueType.Object(parent, className, arguments);
               }
            }

            this.genericValueTypeCache.put(result, result);
         }

         return result;
      } else {
         return valueType;
      }
   }

   public String getCached(String s) {
      String result = this.stringCache.get(s);
      if (result == null) {
         result = s;
         this.stringCache.put(result, result);
      }

      return result;
   }

   public MethodDescriptor parseDescriptorCached(String value) {
      MethodDescriptor result = this.descriptorParseCache.get(value);
      if (result == null) {
         result = this.getCached(MethodDescriptor.parse(value));
         this.descriptorParseCache.put(value, result);
      }

      return result;
   }

   public ValueType parseValueTypeCached(String value) {
      ValueType result = this.valueTypeParseCache.get(value);
      if (result == null) {
         result = this.getCached(ValueType.parse(value));
         this.valueTypeParseCache.put(value, result);
      }

      return result;
   }

   public GenericValueType parseGenericValueTypeCached(String value) {
      GenericValueType result = this.genericValueTypeParseCache.get(value);
      if (result == null) {
         result = this.getCached(GenericValueType.parse(value, new GenericValueType.ParsePosition()));
         this.genericValueTypeParseCache.put(value, result);
      }

      return result;
   }
}
