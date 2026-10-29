package org.teavm.dependency;

import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;

public class ClassDependency implements ClassDependencyInfo {
   private DependencyAnalyzer analyzer;
   private String className;
   private ClassReader classReader;
   boolean present;
   boolean activated;

   ClassDependency(DependencyAnalyzer analyzer, String className, ClassReader classReader) {
      this.analyzer = analyzer;
      this.className = className;
      this.classReader = classReader;
   }

   @Override
   public String getClassName() {
      return this.className;
   }

   @Override
   public boolean isMissing() {
      return this.classReader == null && !this.present;
   }

   public ClassReader getClassReader() {
      return this.classReader;
   }

   public void initClass(CallLocation location) {
      if (!this.isMissing()) {
         this.analyzer.initClass(this, location);
      }
   }

   void cleanup() {
      if (this.classReader != null) {
         this.present = true;
         this.classReader = null;
      }
   }
}
