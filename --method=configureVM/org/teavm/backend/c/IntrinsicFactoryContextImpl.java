package org.teavm.backend.c;

import java.util.Properties;
import org.teavm.backend.c.generators.GeneratorFactoryContext;
import org.teavm.backend.c.generators.ReflectionGeneratorFactoryContext;
import org.teavm.backend.c.intrinsic.IntrinsicFactoryContext;
import org.teavm.common.ServiceRepository;
import org.teavm.model.ClassReaderSource;
import org.teavm.parsing.resource.ResourceProvider;

class IntrinsicFactoryContextImpl implements IntrinsicFactoryContext, GeneratorFactoryContext, ReflectionGeneratorFactoryContext {
   private ClassReaderSource classSource;
   private ResourceProvider resourceProvider;
   private ClassLoader classLoader;
   private ServiceRepository services;
   private Properties properties;

   IntrinsicFactoryContextImpl(
      ClassReaderSource classSource, ResourceProvider resourceProvider, ClassLoader classLoader, ServiceRepository services, Properties properties
   ) {
      this.classSource = classSource;
      this.resourceProvider = resourceProvider;
      this.classLoader = classLoader;
      this.services = services;
      this.properties = properties;
   }

   @Override
   public ClassReaderSource getClassSource() {
      return this.classSource;
   }

   @Override
   public ResourceProvider getResourceProvider() {
      return this.resourceProvider;
   }

   @Override
   public ClassLoader getClassLoader() {
      return this.classLoader;
   }

   @Override
   public ServiceRepository getServices() {
      return this.services;
   }

   @Override
   public Properties getProperties() {
      return this.properties;
   }
}
