package org.teavm.vm;

import org.teavm.dependency.ClassSourcePacker;
import org.teavm.dependency.DependencyAnalyzerFactory;
import org.teavm.dependency.PreciseDependencyAnalyzer;
import org.teavm.interop.PlatformMarker;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ReferenceCache;
import org.teavm.parsing.ClasspathClassHolderSource;
import org.teavm.parsing.ClasspathResourceProvider;
import org.teavm.parsing.resource.ResourceProvider;

public class TeaVMBuilder {
   TeaVMTarget target;
   ClassReaderSource classSource;
   ClassLoader classLoader;
   ResourceProvider resourceProvider;
   ReferenceCache referenceCache = new ReferenceCache();
   DependencyAnalyzerFactory dependencyAnalyzerFactory = PreciseDependencyAnalyzer::new;
   ClassSourcePacker classSourcePacker = (src, names) -> src;
   boolean obfuscated;
   boolean strict;

   public TeaVMBuilder(TeaVMTarget target) {
      this.target = target;
      this.classLoader = TeaVMBuilder.class.getClassLoader();
      this.resourceProvider = !isBootstrap() ? new ClasspathResourceProvider(this.classLoader) : null;
      this.classSource = !isBootstrap() ? new ClasspathClassHolderSource(this.resourceProvider, this.referenceCache, this.classLoader) : name -> null;
   }

   public ClassReaderSource getClassSource() {
      return this.classSource;
   }

   public TeaVMBuilder setClassSource(ClassReaderSource classSource) {
      this.classSource = classSource;
      return this;
   }

   public ClassLoader getClassLoader() {
      return this.classLoader;
   }

   public TeaVMBuilder setClassLoader(ClassLoader classLoader) {
      this.classLoader = classLoader;
      return this;
   }

   public ResourceProvider getResourceProvider() {
      return this.resourceProvider;
   }

   public TeaVMBuilder setResourceProvider(ResourceProvider resourceProvider) {
      this.resourceProvider = resourceProvider;
      return this;
   }

   public DependencyAnalyzerFactory getDependencyAnalyzerFactory() {
      return this.dependencyAnalyzerFactory;
   }

   public TeaVMBuilder setDependencyAnalyzerFactory(DependencyAnalyzerFactory dependencyAnalyzerFactory) {
      this.dependencyAnalyzerFactory = dependencyAnalyzerFactory;
      return this;
   }

   public TeaVMBuilder setReferenceCache(ReferenceCache referenceCache) {
      this.referenceCache = referenceCache;
      return this;
   }

   public TeaVMBuilder setClassSourcePacker(ClassSourcePacker classSourcePacker) {
      this.classSourcePacker = classSourcePacker;
      return this;
   }

   public TeaVMBuilder setObfuscated(boolean obfuscated) {
      this.obfuscated = obfuscated;
      return this;
   }

   public TeaVMBuilder setStrict(boolean strict) {
      this.strict = strict;
      return this;
   }

   public TeaVM build() {
      return new TeaVM(this);
   }

   @PlatformMarker
   private static boolean isBootstrap() {
      return false;
   }
}
