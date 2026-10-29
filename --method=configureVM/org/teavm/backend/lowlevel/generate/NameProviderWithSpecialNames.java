package org.teavm.backend.lowlevel.generate;

import java.util.HashMap;
import java.util.Map;
import org.teavm.interop.Export;
import org.teavm.interop.Import;
import org.teavm.model.AnnotationReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class NameProviderWithSpecialNames implements NameProvider {
   private NameProvider underlyingProvider;
   private ClassReaderSource classSource;
   private Map<MethodReference, String> cache = new HashMap<>();

   public NameProviderWithSpecialNames(NameProvider underlyingProvider, ClassReaderSource classSource) {
      this.underlyingProvider = underlyingProvider;
      this.classSource = classSource;
   }

   @Override
   public String forMethod(MethodReference method) {
      return this.cache.computeIfAbsent(method, r -> {
         String special = this.getSpecialName(r);
         return special != null ? special : this.underlyingProvider.forMethod(method);
      });
   }

   private String getSpecialName(MethodReference methodReference) {
      MethodReader method = this.classSource.resolve(methodReference);
      if (method == null) {
         return null;
      }

      AnnotationReader exportAnnot = method.getAnnotations().get(Export.class.getName());
      if (exportAnnot != null) {
         return exportAnnot.getValue("name").getString();
      }

      AnnotationReader importAnnot = method.getAnnotations().get(Import.class.getName());
      return importAnnot != null ? importAnnot.getValue("name").getString() : null;
   }

   @Override
   public String forVirtualMethod(MethodDescriptor method) {
      return this.underlyingProvider.forVirtualMethod(method);
   }

   @Override
   public String forStaticField(FieldReference field) {
      return this.underlyingProvider.forStaticField(field);
   }

   @Override
   public String forMemberField(FieldReference field) {
      return this.underlyingProvider.forMemberField(field);
   }

   @Override
   public String forClass(String className) {
      return this.underlyingProvider.forClass(className);
   }

   @Override
   public String forClassInitializer(String className) {
      return this.underlyingProvider.forClassInitializer(className);
   }

   @Override
   public String forClassSystemInitializer(ValueType type) {
      return this.underlyingProvider.forClassSystemInitializer(type);
   }

   @Override
   public String forClassClass(String className) {
      return this.underlyingProvider.forClassClass(className);
   }

   @Override
   public String forClassInstance(ValueType type) {
      return this.underlyingProvider.forClassInstance(type);
   }

   @Override
   public String forSupertypeFunction(ValueType type) {
      return this.underlyingProvider.forSupertypeFunction(type);
   }

   @Override
   public String createTopLevelName(String template) {
      return this.underlyingProvider.createTopLevelName(template);
   }
}
