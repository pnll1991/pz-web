package org.teavm.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.teavm.hppc.ObjectByteHashMap;
import org.teavm.hppc.ObjectByteMap;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReference;

public final class AnnotationAwareCacheStatus implements CacheStatus {
   private static final byte UNKNOWN = 0;
   private static final byte STALE = 1;
   private static final byte UNDECIDED = 2;
   private static final byte FRESH = 3;
   private CacheStatus underlyingStatus;
   private IncrementalDependencyProvider dependencyProvider;
   private List<Predicate<String>> synthesizedClasses = new ArrayList<>();
   private ObjectByteMap<String> classStatusCache = new ObjectByteHashMap();
   private ClassReaderSource classSource;

   public AnnotationAwareCacheStatus(CacheStatus underlyingStatus, IncrementalDependencyProvider dependencyProvider, ClassReaderSource classSource) {
      this.underlyingStatus = underlyingStatus;
      this.dependencyProvider = dependencyProvider;
      this.classSource = classSource;
   }

   public void addSynthesizedClasses(Predicate<String> synthesizedClasses) {
      this.synthesizedClasses.add(synthesizedClasses);
   }

   @Override
   public boolean isStaleClass(String className) {
      return this.getClassStatus(className) == 1;
   }

   private byte getClassStatus(String className) {
      byte status = this.classStatusCache.getOrDefault(className, (byte)0);
      if (status == 0) {
         this.classStatusCache.put(className, (byte)2);
         status = this.computeClassStatus(className);
         this.classStatusCache.put(className, status);
      }

      return status;
   }

   private byte computeClassStatus(String className) {
      if (!this.isSynthesizedClass(className) && this.underlyingStatus.isStaleClass(className)) {
         return 1;
      }

      if (this.dependencyProvider.isNoCache(className)) {
         return 1;
      }

      if (this.hasStaleDependencies(this.dependencyProvider.getDependencies(className))) {
         return 1;
      }

      ClassReader cls = this.classSource.get(className);
      if (cls != null) {
         if (cls.getParent() != null && this.getClassStatus(cls.getParent()) == 1) {
            return 1;
         }

         for (String itf : cls.getInterfaces()) {
            if (this.getClassStatus(cls.getParent()) == 1) {
               return 1;
            }
         }
      }

      return 3;
   }

   @Override
   public boolean isStaleMethod(MethodReference method) {
      return this.isStaleClass(method.getClassName())
         || this.dependencyProvider.isNoCache(method)
         || this.hasStaleDependencies(this.dependencyProvider.getDependencies(method));
   }

   private boolean hasStaleDependencies(String[] dependencies) {
      for (String dependency : dependencies) {
         byte dependencyStatus = this.getClassStatus(dependency);
         if (dependencyStatus == 1) {
            return true;
         }
      }

      return false;
   }

   private boolean isSynthesizedClass(String className) {
      return this.synthesizedClasses.stream().anyMatch(p -> p.test(className));
   }
}
