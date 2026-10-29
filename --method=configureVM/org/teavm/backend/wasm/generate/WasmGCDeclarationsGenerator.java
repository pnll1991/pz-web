package org.teavm.backend.wasm.generate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.classes.WasmGCClassGenerator;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCCustomTypeMapperFactory;
import org.teavm.backend.wasm.generate.classes.WasmGCSupertypeFunctionProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCTypeMapper;
import org.teavm.backend.wasm.generate.methods.WasmGCMethodGenerator;
import org.teavm.backend.wasm.generate.strings.WasmGCStringProvider;
import org.teavm.backend.wasm.intrinsics.WasmGCBodyIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmCompositeType;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmTag;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTableProvider;
import org.teavm.dependency.DependencyInfo;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ListableClassHolderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.model.analysis.ClassMetadataRequirements;
import org.teavm.model.classes.TagRegistry;
import org.teavm.model.classes.VirtualTableBuilder;
import org.teavm.model.util.AsyncMethodFinder;
import org.teavm.parsing.resource.ResourceProvider;
import org.teavm.vm.intrinsic.IntrinsicProvider;

public class WasmGCDeclarationsGenerator {
   public final ClassHierarchy hierarchy;
   public final WasmModule module;
   public final WasmFunctionTypes functionTypes;
   private final WasmGCClassGenerator classGenerator;
   private final WasmGCMethodGenerator methodGenerator;
   public final WasmGCVirtualTableProvider virtualTables;
   private List<WasmGCInitializerContributor> initializerContributors = new ArrayList<>();
   private Collection<MethodReference> additionalMethodsOnCallSites;

   public WasmGCDeclarationsGenerator(
      WasmModule module,
      ListableClassHolderSource classes,
      ClassReaderSource originalClasses,
      ResourceProvider resources,
      ClassLoader classLoader,
      ClassInitializerInfo classInitializerInfo,
      DependencyInfo dependencyInfo,
      Diagnostics diagnostics,
      List<WasmGCCustomTypeMapperFactory> customTypeMapperFactories,
      Predicate<MethodReference> isVirtual,
      boolean strict,
      String entryPoint,
      Collection<MethodReference> additionalMethodsOnCallSites,
      IntrinsicProvider<WasmGCInlineIntrinsic> callSiteIntrinsics,
      IntrinsicProvider<WasmGCBodyIntrinsic> bodyIntrinsics
   ) {
      this.module = module;
      this.hierarchy = new ClassHierarchy(classes);
      this.additionalMethodsOnCallSites = additionalMethodsOnCallSites;
      this.virtualTables = this.createVirtualTableProvider(classes, isVirtual);
      this.functionTypes = new WasmFunctionTypes(module);
      WasmGCNameProvider names = new WasmGCNameProvider();
      this.methodGenerator = new WasmGCMethodGenerator(
         module,
         this.hierarchy,
         classes,
         resources,
         classLoader,
         this.virtualTables,
         classInitializerInfo,
         this.functionTypes,
         names,
         diagnostics,
         bodyIntrinsics,
         callSiteIntrinsics,
         dependencyInfo,
         strict,
         entryPoint,
         this.initializerContributors::add
      );
      TagRegistry tags = new TagRegistry(classes, this.hierarchy);
      ClassMetadataRequirements metadataRequirements = new ClassMetadataRequirements(dependencyInfo);
      this.classGenerator = new WasmGCClassGenerator(
         module,
         classes,
         originalClasses,
         this.hierarchy,
         dependencyInfo,
         this.functionTypes,
         tags,
         metadataRequirements,
         this.virtualTables,
         this.methodGenerator,
         names,
         classInitializerInfo,
         customTypeMapperFactories
      );
      this.methodGenerator.setClassInfoProvider(this.classGenerator);
      this.methodGenerator.setStrings(this.classGenerator.strings);
      this.methodGenerator.setSupertypeFunctions(this.classGenerator.getSupertypeProvider());
      this.methodGenerator.setStandardClasses(this.classGenerator.standardClasses);
      this.methodGenerator.setTypeMapper(this.classGenerator.typeMapper);
   }

   public void setCompactMode(boolean compactMode) {
      this.methodGenerator.setCompactMode(compactMode);
      this.classGenerator.setCompactMode(compactMode);
   }

   public void setFriendlyToDebugger(boolean friendlyToDebugger) {
      this.methodGenerator.setFriendlyToDebugger(friendlyToDebugger);
   }

   public WasmGCClassInfoProvider classInfoProvider() {
      return this.classGenerator;
   }

   public WasmGCTypeMapper typeMapper() {
      return this.classGenerator.typeMapper;
   }

   public BaseWasmFunctionRepository functions() {
      return this.methodGenerator;
   }

   public WasmGCSupertypeFunctionProvider supertypeFunctions() {
      return this.classGenerator.getSupertypeProvider();
   }

   public void setAsyncMethodFinder(AsyncMethodFinder asyncMethodFinder) {
      Set<MethodReference> asyncSplitMethods = asyncMethodFinder.getAsyncFamilyMethods();
      this.classGenerator.setAsyncSplitMethods(asyncSplitMethods);
      this.methodGenerator.setAsyncMethods(asyncMethodFinder.getAsyncMethods());
      this.methodGenerator.setAsyncSplitMethods(asyncSplitMethods);
   }

   public void generate() {
      int lastTypeIndex = 0;

      do {
         this.generateRound();

         for (int i = lastTypeIndex; i < this.module.types.size(); i++) {
            WasmCompositeType type = this.module.types.get(i);
            if (type instanceof WasmStructure) {
               ((WasmStructure)type).init();
            } else if (type instanceof WasmArray) {
               ((WasmArray)type).init();
            }
         }

         lastTypeIndex = this.module.types.size();
      } while (this.methodGenerator.hasSomethingToGenerate() || this.classGenerator.hasSomethingToGenerate());
   }

   private void generateRound() {
      boolean somethingGenerated;
      do {
         somethingGenerated = false;
         somethingGenerated |= this.methodGenerator.process();
         somethingGenerated |= this.classGenerator.process();
      } while (somethingGenerated);
   }

   public void contributeToInitializer(WasmFunction function) {
      ArrayList<WasmGCInitializerContributor> contributors = new ArrayList<>(List.of(this.classGenerator, this.classGenerator.strings));
      contributors.addAll(this.initializerContributors);

      for (WasmGCInitializerContributor contributor : contributors) {
         contributor.contributeToInitializerDefinitions(function);
      }

      for (WasmGCInitializerContributor contributor : contributors) {
         contributor.contributeToInitializer(function);
      }
   }

   private WasmGCVirtualTableProvider createVirtualTableProvider(ListableClassHolderSource classes, Predicate<MethodReference> isVirtual) {
      LinkedHashSet<MethodReference> methodsOnCallSites = new LinkedHashSet<>(VirtualTableBuilder.getMethodsUsedOnCallSites(classes, true));
      methodsOnCallSites.addAll(this.additionalMethodsOnCallSites);
      return new WasmGCVirtualTableProvider(classes, methodsOnCallSites, isVirtual);
   }

   public WasmFunction dummyInitializer() {
      return this.methodGenerator.getDummyInitializer();
   }

   public WasmGCNameProvider names() {
      return this.methodGenerator.names;
   }

   public WasmGCStringProvider strings() {
      return this.classGenerator.strings;
   }

   public WasmTag exceptionTag() {
      return this.methodGenerator.getGenerationContext().getExceptionTag();
   }

   public void addToInitializer(Consumer<WasmFunction> contributor) {
      this.methodGenerator.getGenerationContext().addToInitializer(contributor);
   }
}
