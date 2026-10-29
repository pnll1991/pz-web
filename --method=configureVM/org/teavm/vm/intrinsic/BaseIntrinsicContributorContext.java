package org.teavm.vm.intrinsic;

import java.util.Properties;
import java.util.Set;
import org.teavm.dependency.DependencyInfo;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.parsing.resource.ResourceProvider;
import org.teavm.vm.TeaVMTargetController;

public class BaseIntrinsicContributorContext implements IntrinsicContributorContext {
   private TeaVMTargetController controller;
   private ClassHierarchy hierarchy;
   private ListableClassReaderSource classes;
   private Set<MethodReference> asyncSplitMethods;

   public BaseIntrinsicContributorContext(
      TeaVMTargetController controller, ClassHierarchy hierarchy, ListableClassReaderSource classes, Set<MethodReference> asyncSplitMethods
   ) {
      this.controller = controller;
      this.hierarchy = hierarchy;
      this.classes = classes;
      this.asyncSplitMethods = asyncSplitMethods;
   }

   @Override
   public ClassHierarchy hierarchy() {
      return this.hierarchy;
   }

   @Override
   public ListableClassReaderSource classes() {
      return this.classes;
   }

   @Override
   public ClassLoader classLoader() {
      return this.controller.getClassLoader();
   }

   @Override
   public ResourceProvider resources() {
      return this.controller.getResourceProvider();
   }

   @Override
   public String entryPoint() {
      return this.controller.getEntryPoint();
   }

   @Override
   public Diagnostics diagnostics() {
      return this.controller.getDiagnostics();
   }

   @Override
   public ClassInitializerInfo classInitInfo() {
      return this.controller.getClassInitializerInfo();
   }

   @Override
   public DependencyInfo dependency() {
      return this.controller.getDependencyInfo();
   }

   @Override
   public boolean isAsyncMethod(MethodReference method) {
      return this.asyncSplitMethods.contains(method);
   }

   @Override
   public Properties properties() {
      return this.controller.getProperties();
   }
}
