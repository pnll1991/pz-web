package org.teavm.backend.javascript.codegen;

import java.util.HashMap;
import java.util.Map;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;

public class DefaultNamingStrategy implements NamingStrategy {
   private static final byte NO_CLASSIFIER = 0;
   private static final byte INIT_CLASSIFIER = 1;
   private final AliasProvider aliasProvider;
   private final ClassReaderSource classSource;
   private final Map<MethodDescriptor, String> aliases = new HashMap<>();
   private final Map<DefaultNamingStrategy.Key, ScopedName> privateAliases = new HashMap<>();
   private final Map<String, ScopedName> classAliases = new HashMap<>();
   private final Map<FieldReference, String> fieldAliases = new HashMap<>();
   private final Map<FieldReference, ScopedName> staticFieldAliases = new HashMap<>();
   private final Map<String, ScopedName> functionAliases = new HashMap<>();
   private final Map<String, ScopedName> classInitAliases = new HashMap<>();
   private String additionalScopeName;

   public DefaultNamingStrategy(AliasProvider aliasProvider, ClassReaderSource classSource) {
      this.aliasProvider = aliasProvider;
      this.classSource = classSource;
   }

   @Override
   public ScopedName className(String cls) {
      return this.classAliases.computeIfAbsent(cls, key -> this.aliasProvider.getClassAlias(cls));
   }

   @Override
   public String instanceMethodName(MethodDescriptor method) {
      String alias = this.aliases.get(method);
      if (alias == null) {
         alias = this.aliasProvider.getMethodAlias(method);
         this.aliases.put(method, alias);
      }

      return alias;
   }

   @Override
   public ScopedName methodName(MethodReference method) {
      return this.getFullNameFor(method, (byte)0);
   }

   @Override
   public ScopedName initializerName(MethodReference method) {
      return this.getFullNameFor(method, (byte)1);
   }

   private ScopedName getFullNameFor(MethodReference method, byte classifier) {
      MethodReference originalMethod = method;
      method = this.getRealMethod(method);
      if (method == null) {
         method = originalMethod;
      }

      return this.privateAliases.computeIfAbsent(new DefaultNamingStrategy.Key(classifier, method), key -> this.aliasProvider.getStaticMethodAlias(key.data));
   }

   @Override
   public String instanceFieldName(FieldReference field) {
      String alias = this.fieldAliases.get(field);
      if (alias == null) {
         FieldReference realField = this.getRealField(field);
         if (realField.equals(field)) {
            alias = this.aliasProvider.getFieldAlias(realField);
         } else {
            alias = this.instanceFieldName(realField);
         }

         this.fieldAliases.put(field, alias);
      }

      return alias;
   }

   @Override
   public ScopedName fieldName(FieldReference field) {
      ScopedName alias = this.staticFieldAliases.get(field);
      if (alias == null) {
         FieldReference realField = this.getRealField(field);
         if (realField.equals(field)) {
            alias = this.aliasProvider.getStaticFieldAlias(realField);
         } else {
            alias = this.fieldName(realField);
         }

         this.staticFieldAliases.put(field, alias);
      }

      return alias;
   }

   @Override
   public ScopedName functionName(String name) {
      return this.functionAliases.computeIfAbsent(name, this.aliasProvider::getFunctionAlias);
   }

   @Override
   public ScopedName classInitializerName(String className) {
      return this.classInitAliases.computeIfAbsent(className, this.aliasProvider::getClassInitAlias);
   }

   @Override
   public String additionalScopeName() {
      if (this.additionalScopeName == null) {
         this.additionalScopeName = this.aliasProvider.getAdditionalScopeName();
      }

      return this.additionalScopeName;
   }

   @Override
   public void reserveName(String name) {
      this.aliasProvider.reserveName(name);
   }

   private MethodReference getRealMethod(MethodReference methodRef) {
      String className = methodRef.getClassName();

      while (className != null) {
         ClassReader cls = this.classSource.get(className);
         if (cls == null) {
            return null;
         }

         MethodReader method = cls.getMethod(methodRef.getDescriptor());
         if (method != null) {
            if (method.getLevel() == AccessLevel.PRIVATE && !className.equals(methodRef.getClassName())) {
               return null;
            }

            return method.getReference();
         }

         className = cls.getParent();
      }

      return null;
   }

   private FieldReference getRealField(FieldReference fieldRef) {
      String cls = fieldRef.getClassName();

      while (cls != null) {
         ClassReader clsReader = this.classSource.get(cls);
         if (clsReader == null) {
            break;
         }

         FieldReader fieldReader = clsReader.getField(fieldRef.getFieldName());
         if (fieldReader != null) {
            return fieldReader.getReference();
         }

         cls = clsReader.getParent();
      }

      return fieldRef;
   }

   static final class Key {
      final MethodReference data;
      int hash;
      final byte classifier;

      Key(byte classifier, MethodReference data) {
         this.classifier = classifier;
         this.data = data;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else {
            return !(o instanceof DefaultNamingStrategy.Key key) ? false : this.classifier == key.classifier && this.data.equals(key.data);
         }
      }

      @Override
      public int hashCode() {
         if (this.hash == 0) {
            this.hash = (this.classifier * 31 + this.data.hashCode()) * 17;
            if (this.hash == 0) {
               this.hash++;
            }
         }

         return this.hash;
      }
   }
}
