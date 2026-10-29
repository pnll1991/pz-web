package org.teavm.extension;

import java.util.Iterator;
import java.util.Properties;
import java.util.function.Supplier;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.extension.diagnostics.SourceLocation;
import org.teavm.extension.introspect.IntrospectClass;
import org.teavm.extension.introspect.IntrospectClassImpl;
import org.teavm.extension.introspect.IntrospectFieldImpl;
import org.teavm.extension.introspect.IntrospectMethodImpl;
import org.teavm.extension.introspect.Introspection;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.MethodReader;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;
import org.teavm.parsing.resource.Resource;
import org.teavm.parsing.resource.ResourceProvider;

public class ExtensionEnvironmentImpl implements ExtensionEnvironment {
   private ResourceProvider resourceProvider;
   private Introspection introspection;
   private ClassLoader classLoader;
   private Diagnostics underlyingDiagnostics;
   private Supplier<Properties> propertiesSupplier;
   private Properties properties;
   private boolean error;
   private org.teavm.extension.diagnostics.Diagnostics diagnostics = new org.teavm.extension.diagnostics.Diagnostics() {
      public void error(SourceLocation location, String message, Object... params) {
         this.convertParams(params);
         ExtensionEnvironmentImpl.this.underlyingDiagnostics.error(this.convertLocation(location), message, params);
      }

      public void warning(SourceLocation location, String message, Object... params) {
         this.convertParams(params);
         ExtensionEnvironmentImpl.this.underlyingDiagnostics.warning(this.convertLocation(location), message, params);
      }

      private void convertParams(Object[] params) {
         for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof IntrospectMethodImpl) {
               params[i] = ((IntrospectMethodImpl)params[i]).method.getReference();
            } else if (params[i] instanceof IntrospectClassImpl) {
               params[i] = ((IntrospectClassImpl)params[i]).type;
            } else if (params[i] instanceof IntrospectFieldImpl) {
               params[i] = ((IntrospectFieldImpl)params[i]).field.getReference();
            } else if (params[i] instanceof Class) {
               params[i] = ValueType.parse((Class<?>)params[i]);
            }
         }
      }

      private CallLocation convertLocation(SourceLocation location) {
         if (location == null) {
            return null;
         }

         MethodReader method = ((IntrospectMethodImpl)location.method()).method;
         return location.fileName() != null
            ? new CallLocation(method.getReference(), new TextLocation(location.fileName(), location.lineNumber()))
            : new CallLocation(method.getReference());
      }
   };

   public ExtensionEnvironmentImpl(
      ResourceProvider resourceProvider,
      ClassHierarchy hierarchy,
      ClassLoader classLoader,
      Diagnostics underlyingDiagnostics,
      Supplier<Properties> propertiesSupplier
   ) {
      this.resourceProvider = resourceProvider;
      this.introspection = new Introspection(hierarchy, classLoader);
      this.classLoader = classLoader;
      this.underlyingDiagnostics = underlyingDiagnostics;
      this.propertiesSupplier = propertiesSupplier;
   }

   public Introspection introspection() {
      return this.introspection;
   }

   public boolean hasError() {
      return this.error;
   }

   public IntrospectClassImpl<?> findClass(String name) {
      return this.introspection.findClass(name);
   }

   public <T> IntrospectClass<T> findClass(Class<T> cls) {
      return this.introspection.findClass(cls);
   }

   public Iterator<org.teavm.extension.resource.Resource> resources(String name) {
      final Iterator<Resource> underlyingResources = this.resourceProvider.getResources(name);
      return new Iterator<org.teavm.extension.resource.Resource>() {
         @Override
         public boolean hasNext() {
            return underlyingResources.hasNext();
         }

         public org.teavm.extension.resource.Resource next() {
            Resource resource = underlyingResources.next();
            return resource::open;
         }
      };
   }

   public org.teavm.extension.diagnostics.Diagnostics diagnostics() {
      return this.diagnostics;
   }

   public ClassLoader classLoader() {
      return this.classLoader;
   }

   public String property(String name) {
      if (this.properties == null) {
         this.properties = this.propertiesSupplier.get();
      }

      return this.properties.getProperty(name);
   }

   public Diagnostics createDiagnostics() {
      return new Diagnostics() {
         @Override
         public void error(CallLocation location, String error, Object... params) {
            ExtensionEnvironmentImpl.this.error = true;
            ExtensionEnvironmentImpl.this.underlyingDiagnostics.error(location, error, params);
         }

         @Override
         public void warning(CallLocation location, String error, Object... params) {
            ExtensionEnvironmentImpl.this.error = true;
            ExtensionEnvironmentImpl.this.underlyingDiagnostics.warning(location, error, params);
         }
      };
   }
}
