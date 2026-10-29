package org.teavm.vm;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.teavm.dependency.DependencyAnalyzer;
import org.teavm.dependency.DependencyListener;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ListableClassHolderSource;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.optimization.InliningFilterFactory;
import org.teavm.model.util.VariableCategoryProvider;
import org.teavm.vm.spi.TeaVMHostExtension;

public interface TeaVMTarget {
   List<ClassHolderTransformer> getTransformers();

   List<DependencyListener> getDependencyListeners();

   default void setEntryPoint(String entryPoint, String name) {
   }

   void setController(TeaVMTargetController var1);

   List<TeaVMHostExtension> getHostExtensions();

   VariableCategoryProvider variableCategoryProvider();

   void contributeDependencies(DependencyAnalyzer var1);

   default void beforeInlining(Program program, MethodReader method) {
   }

   default void analyzeBeforeOptimizations(ListableClassReaderSource classSource) {
   }

   void beforeOptimizations(Program var1, MethodReader var2);

   void afterOptimizations(Program var1, MethodReader var2);

   void emit(ListableClassHolderSource var1, BuildTarget var2, String var3) throws IOException;

   String[] getPlatformTags();

   boolean isAsyncSupported();

   default InliningFilterFactory getInliningFilter() {
      return InliningFilterFactory.DEFAULT;
   }

   default Collection<? extends MethodReference> getInitializerMethods() {
      return null;
   }

   default boolean needsSystemArrayCopyOptimization() {
      return true;
   }

   default boolean filterClassInitializer(String initializer) {
      return true;
   }
}
