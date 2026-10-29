package org.teavm.backend.wasm.generate.methods;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCInitializerContributor;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCStandardClasses;
import org.teavm.backend.wasm.generate.classes.WasmGCSupertypeFunctionProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCTypeMapper;
import org.teavm.backend.wasm.generate.strings.WasmGCStringProvider;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmTag;
import org.teavm.backend.wasm.runtime.WasmGCSupport;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTableProvider;
import org.teavm.dependency.DependencyInfo;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.parsing.resource.ResourceProvider;
import org.teavm.vm.intrinsic.IntrinsicProvider;

public class WasmGCGenerationContext implements BaseWasmGenerationContext {
   private WasmModule module;
   private WasmGCClassInfoProvider classInfoProvider;
   private WasmGCStandardClasses standardClasses;
   private WasmGCStringProvider strings;
   private WasmGCVirtualTableProvider virtualTables;
   private WasmGCTypeMapper typeMapper;
   private WasmFunctionTypes functionTypes;
   private ListableClassReaderSource classes;
   private ResourceProvider resources;
   private ClassLoader classLoader;
   private ClassHierarchy hierarchy;
   private BaseWasmFunctionRepository functions;
   private WasmGCSupertypeFunctionProvider supertypeFunctions;
   private IntrinsicProvider<WasmGCInlineIntrinsic> callSiteIntrinsics;
   private WasmFunction npeMethod;
   private WasmFunction aaiobeMethod;
   private WasmFunction cceMethod;
   private WasmTag exceptionTag;
   private Map<String, Set<String>> interfaceImplementors;
   private WasmGCNameProvider names;
   private boolean strict;
   private String entryPoint;
   private Consumer<WasmGCInitializerContributor> initializerContributors;
   private Diagnostics diagnostics;
   private ClassInitializerInfo classInitInfo;
   private DependencyInfo dependency;

   public WasmGCGenerationContext(
      WasmModule module,
      WasmGCVirtualTableProvider virtualTables,
      WasmGCTypeMapper typeMapper,
      WasmFunctionTypes functionTypes,
      ListableClassReaderSource classes,
      ResourceProvider resources,
      ClassLoader classLoader,
      ClassHierarchy hierarchy,
      BaseWasmFunctionRepository functions,
      WasmGCSupertypeFunctionProvider supertypeFunctions,
      WasmGCClassInfoProvider classInfoProvider,
      WasmGCStandardClasses standardClasses,
      WasmGCStringProvider strings,
      IntrinsicProvider<WasmGCInlineIntrinsic> callSiteIntrinsics,
      WasmGCNameProvider names,
      boolean strict,
      String entryPoint,
      Consumer<WasmGCInitializerContributor> initializerContributors,
      Diagnostics diagnostics,
      ClassInitializerInfo classInitInfo,
      DependencyInfo dependency
   ) {
      this.module = module;
      this.virtualTables = virtualTables;
      this.typeMapper = typeMapper;
      this.functionTypes = functionTypes;
      this.classes = classes;
      this.resources = resources;
      this.classLoader = classLoader;
      this.hierarchy = hierarchy;
      this.functions = functions;
      this.supertypeFunctions = supertypeFunctions;
      this.classInfoProvider = classInfoProvider;
      this.standardClasses = standardClasses;
      this.strings = strings;
      this.callSiteIntrinsics = callSiteIntrinsics;
      this.names = names;
      this.strict = strict;
      this.entryPoint = entryPoint;
      this.initializerContributors = initializerContributors;
      this.diagnostics = diagnostics;
      this.classInitInfo = classInitInfo;
      this.dependency = dependency;
   }

   public WasmGCClassInfoProvider classInfoProvider() {
      return this.classInfoProvider;
   }

   public WasmGCNameProvider names() {
      return this.names;
   }

   public WasmGCStandardClasses standardClasses() {
      return this.standardClasses;
   }

   public WasmGCStringProvider strings() {
      return this.strings;
   }

   public String entryPoint() {
      return this.entryPoint;
   }

   public WasmGCVirtualTableProvider virtualTables() {
      return this.virtualTables;
   }

   public WasmGCTypeMapper typeMapper() {
      return this.typeMapper;
   }

   @Override
   public BaseWasmFunctionRepository functions() {
      return this.functions;
   }

   public WasmGCSupertypeFunctionProvider supertypeFunctions() {
      return this.supertypeFunctions;
   }

   @Override
   public WasmFunctionTypes functionTypes() {
      return this.functionTypes;
   }

   @Override
   public WasmTag getExceptionTag() {
      if (this.exceptionTag == null) {
         this.exceptionTag = new WasmTag(this.functionTypes.of(null, this.classInfoProvider.getClassInfo("java.lang.Throwable").getStructure().getReference()));
         this.exceptionTag.setExportName("teavm.javaException");
         this.module.tags.add(this.exceptionTag);
      }

      return this.exceptionTag;
   }

   public ListableClassReaderSource classes() {
      return this.classes;
   }

   public boolean isStrict() {
      return this.strict;
   }

   public ClassLoader classLoader() {
      return this.classLoader;
   }

   @Override
   public ResourceProvider resources() {
      return this.resources;
   }

   public ClassHierarchy hierarchy() {
      return this.hierarchy;
   }

   public WasmFunction npeMethod() {
      if (this.npeMethod == null) {
         this.npeMethod = this.functions().forStaticMethod(new MethodReference(WasmGCSupport.class, "npe", NullPointerException.class));
      }

      return this.npeMethod;
   }

   public WasmFunction aaiobeMethod() {
      if (this.aaiobeMethod == null) {
         this.aaiobeMethod = this.functions().forStaticMethod(new MethodReference(WasmGCSupport.class, "aiiobe", ArrayIndexOutOfBoundsException.class));
      }

      return this.aaiobeMethod;
   }

   public WasmFunction cceMethod() {
      if (this.cceMethod == null) {
         this.cceMethod = this.functions().forStaticMethod(new MethodReference(WasmGCSupport.class, "cce", ClassCastException.class));
      }

      return this.cceMethod;
   }

   public WasmModule module() {
      return this.module;
   }

   public IntrinsicProvider<WasmGCInlineIntrinsic> callSiteIntrinsics() {
      return this.callSiteIntrinsics;
   }

   public Diagnostics diagnostics() {
      return this.diagnostics;
   }

   public ClassInitializerInfo classInitInfo() {
      return this.classInitInfo;
   }

   public DependencyInfo dependency() {
      return this.dependency;
   }

   public Collection<String> getInterfaceImplementors(String className) {
      if (this.interfaceImplementors == null) {
         this.fillInterfaceImplementors();
      }

      Set<String> result = this.interfaceImplementors.get(className);
      return result != null ? result : List.of();
   }

   private void fillInterfaceImplementors() {
      this.interfaceImplementors = new HashMap<>();

      for (String className : this.classes.getClassNames()) {
         ClassReader cls = this.classes.get(className);
         if (!cls.hasModifier(ElementModifier.INTERFACE)) {
            for (String itf : cls.getInterfaces()) {
               this.addInterfaceImplementor(className, itf);
            }
         }
      }
   }

   private void addInterfaceImplementor(String implementorName, String interfaceName) {
      Set<String> implementorsByKey = this.interfaceImplementors.computeIfAbsent(interfaceName, k -> new LinkedHashSet<>());
      if (implementorsByKey.add(implementorName)) {
         ClassReader itf = this.classes.get(implementorName);
         if (itf != null) {
            for (String parentItf : itf.getInterfaces()) {
               this.addInterfaceImplementor(implementorName, parentItf);
            }
         }
      }
   }

   public void addToInitializer(final Consumer<WasmFunction> initializer) {
      this.initializerContributors.accept(new WasmGCInitializerContributor() {
         @Override
         public void contributeToInitializerDefinitions(WasmFunction function) {
         }

         @Override
         public void contributeToInitializer(WasmFunction function) {
            initializer.accept(function);
         }
      });
   }
}
