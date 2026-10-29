package org.teavm.backend.wasm;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Supplier;
import org.teavm.backend.c.analyze.InteropDependencyListener;
import org.teavm.backend.wasm.debug.CompositeDebugLines;
import org.teavm.backend.wasm.debug.DebugLines;
import org.teavm.backend.wasm.debug.ExternalDebugFile;
import org.teavm.backend.wasm.debug.GCDebugInfoBuilder;
import org.teavm.backend.wasm.debug.sourcemap.SourceMapBuilder;
import org.teavm.backend.wasm.dependencies.WasmGCDependencies;
import org.teavm.backend.wasm.generate.WasmGCDeclarationsGenerator;
import org.teavm.backend.wasm.generate.WasmGCInitializerRegistry;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCCustomTypeMapperFactory;
import org.teavm.backend.wasm.generate.classes.WasmGCTypeMapper;
import org.teavm.backend.wasm.generate.strings.WasmGCStringProvider;
import org.teavm.backend.wasm.intrinsics.WasmGCAsyncTypeMapperFactory;
import org.teavm.backend.wasm.intrinsics.WasmGCBodyIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCCodeGenContext;
import org.teavm.backend.wasm.intrinsics.WasmGCCodeGenContributor;
import org.teavm.backend.wasm.intrinsics.WasmGCCodeGenRegistry;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCIntrinsics;
import org.teavm.backend.wasm.intrinsics.WasmGCResourcesIntrinsic;
import org.teavm.backend.wasm.model.WasmCustomSection;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmMemorySegment;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmTag;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.optimization.WasmUsageCounter;
import org.teavm.backend.wasm.render.WasmBinaryRenderer;
import org.teavm.backend.wasm.render.WasmBinaryStatsCollector;
import org.teavm.backend.wasm.render.WasmBinaryVersion;
import org.teavm.backend.wasm.render.WasmBinaryWriter;
import org.teavm.backend.wasm.runtime.StringInternPool;
import org.teavm.backend.wasm.runtime.WasmGCResources;
import org.teavm.backend.wasm.transformation.BaseClassesTransformation;
import org.teavm.backend.wasm.transformation.ClassLoaderResourceTransformation;
import org.teavm.backend.wasm.transformation.EntryPointTransformation;
import org.teavm.backend.wasm.transformation.ReferenceQueueTransformation;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTableProvider;
import org.teavm.classlib.ReflectionSupplier;
import org.teavm.common.JsonUtil;
import org.teavm.dependency.DependencyAnalyzer;
import org.teavm.dependency.DependencyInfo;
import org.teavm.dependency.DependencyListener;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.interop.Address;
import org.teavm.interop.Async;
import org.teavm.interop.Export;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ElementModifier;
import org.teavm.model.ListableClassHolderSource;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.lowlevel.Characteristics;
import org.teavm.model.lowlevel.LowLevelNullCheckFilter;
import org.teavm.model.optimization.InliningFilterFactory;
import org.teavm.model.transformation.BoundCheckInsertion;
import org.teavm.model.transformation.NullCheckInsertion;
import org.teavm.model.util.AsyncMethodFinder;
import org.teavm.model.util.VariableCategoryProvider;
import org.teavm.reflection.AnnotationGenerationHelper;
import org.teavm.reflection.ReflectionDependencyListener;
import org.teavm.runtime.heap.Heap;
import org.teavm.vm.BuildTarget;
import org.teavm.vm.TeaVMTarget;
import org.teavm.vm.TeaVMTargetController;
import org.teavm.vm.intrinsic.BaseIntrinsicContributorContext;
import org.teavm.vm.intrinsic.DefaultIntrinsicRegistry;
import org.teavm.vm.intrinsic.IntrinsicRegistry;
import org.teavm.vm.spi.TeaVMHostExtension;

public class WasmGCTarget implements TeaVMTarget, TeaVMWasmGCHost {
   private TeaVMTargetController controller;
   private NullCheckInsertion nullCheckInsertion;
   private BoundCheckInsertion boundCheckInsertion = new BoundCheckInsertion();
   private boolean strict;
   private boolean obfuscated;
   private boolean debugInfo;
   private boolean compactMode;
   private SourceMapBuilder sourceMapBuilder;
   private String sourceMapLocation;
   private WasmDebugInfoLocation debugLocation = WasmDebugInfoLocation.EXTERNAL;
   private WasmDebugInfoLevel debugLevel = WasmDebugInfoLevel.FULL;
   private int bufferHeapMinSize = 2097152;
   private boolean sharedBuffer;
   private List<WasmGCCustomTypeMapperFactory> customTypeMapperFactories = new ArrayList<>();
   private EntryPointTransformation entryPointTransformation = new EntryPointTransformation();
   private List<Supplier<Collection<MethodReference>>> additionalMethodsOnCallSites = new ArrayList<>();
   private List<WasmGCCodeGenContributor> intrinsicContributors = new ArrayList<>();
   private ReflectionDependencyListener reflection;
   private int dataSize;

   public WasmGCTarget() {
      this.customTypeMapperFactories.add(new WasmGCAsyncTypeMapperFactory());
   }

   public void setObfuscated(boolean obfuscated) {
      this.obfuscated = obfuscated;
   }

   public void setStrict(boolean strict) {
      this.strict = strict;
   }

   public void setDebugInfo(boolean debug) {
      this.debugInfo = debug;
   }

   public void setDebugInfoLevel(WasmDebugInfoLevel debugLevel) {
      this.debugLevel = debugLevel;
   }

   public void setDebugInfoLocation(WasmDebugInfoLocation debugLocation) {
      this.debugLocation = debugLocation;
   }

   public void setSourceMapBuilder(SourceMapBuilder sourceMapBuilder) {
      this.sourceMapBuilder = sourceMapBuilder;
   }

   public void setSourceMapLocation(String sourceMapLocation) {
      this.sourceMapLocation = sourceMapLocation;
   }

   public void setBufferHeapMinSize(int bufferHeapMinSize) {
      this.bufferHeapMinSize = bufferHeapMinSize;
   }

   public void setSharedBuffer(boolean sharedBuffer) {
      this.sharedBuffer = sharedBuffer;
   }

   public void setCompactMode(boolean compactMode) {
      this.compactMode = compactMode;
   }

   @Override
   public void addCustomTypeMapperFactory(WasmGCCustomTypeMapperFactory customTypeMapperFactory) {
      this.customTypeMapperFactories.add(customTypeMapperFactory);
   }

   @Override
   public void addMethodsOnCallSites(Supplier<Collection<MethodReference>> methodsOnCallSites) {
      this.additionalMethodsOnCallSites.add(methodsOnCallSites);
   }

   @Override
   public void setController(TeaVMTargetController controller) {
      Characteristics characteristics = new Characteristics(controller.getUnprocessedClassSource());
      this.nullCheckInsertion = new NullCheckInsertion(new LowLevelNullCheckFilter(characteristics));
      this.controller = controller;
      controller.addVirtualMethods(this.reflection::isVirtual);
   }

   @Override
   public void setEntryPoint(String entryPoint, String name) {
      this.entryPointTransformation.setEntryPoint(entryPoint);
      this.entryPointTransformation.setEntryPointName(name);
   }

   @Override
   public VariableCategoryProvider variableCategoryProvider() {
      return null;
   }

   @Override
   public List<DependencyListener> getDependencyListeners() {
      return List.of(new InteropDependencyListener());
   }

   @Override
   public List<ClassHolderTransformer> getTransformers() {
      return List.of(
         new BaseClassesTransformation(), new ClassLoaderResourceTransformation(), new ReferenceQueueTransformation(), this.entryPointTransformation
      );
   }

   @Override
   public void contributeDependencies(DependencyAnalyzer dependencyAnalyzer) {
      WasmGCDependencies deps = new WasmGCDependencies(dependencyAnalyzer);
      deps.contribute();
      deps.contributeStandardExports();
      ArrayList<ReflectionSupplier> reflectionSuppliers = new ArrayList<>();

      for (ReflectionSupplier supplier : ServiceLoader.load(ReflectionSupplier.class, dependencyAnalyzer.getClassLoader())) {
         reflectionSuppliers.add(supplier);
      }

      this.reflection = new ReflectionDependencyListener(reflectionSuppliers, new AnnotationGenerationHelper());
      dependencyAnalyzer.addDependencyListener(this.reflection);
   }

   @Override
   public List<TeaVMHostExtension> getHostExtensions() {
      return List.of(this);
   }

   @Override
   public void beforeInlining(Program program, MethodReader method) {
      if (this.strict) {
         this.nullCheckInsertion.transformProgram(program, method.getReference());
      }
   }

   @Override
   public void beforeOptimizations(Program program, MethodReader method) {
      if (this.strict) {
         this.boundCheckInsertion.transformProgram(program, method.getReference());
      }
   }

   @Override
   public void afterOptimizations(Program program, MethodReader method) {
   }

   @Override
   public String[] getPlatformTags() {
      return new String[]{"webassembly-gc"};
   }

   @Override
   public boolean isAsyncSupported() {
      return true;
   }

   @Override
   public InliningFilterFactory getInliningFilter() {
      return ignore -> methodRef -> {
         MethodReader method = this.controller.getUnprocessedClassSource().getMethod(methodRef);
         return method == null ? true : method.getAnnotations().get(Async.class.getName()) == null;
      };
   }

   @Override
   public void contributeToCodeGen(WasmGCCodeGenContributor contributor) {
      this.intrinsicContributors.add(contributor);
   }

   @Override
   public void emit(ListableClassHolderSource classes, BuildTarget buildTarget, String outputName) throws IOException {
      WasmModule module = new WasmModule();
      module.memoryImportName = "memory";
      module.memoryImportModule = "env";
      this.addMethodsOnCallSites(this.reflection::getVirtualCallSites);
      DefaultIntrinsicRegistry<WasmGCInlineIntrinsic> inlineIntrinsics = new DefaultIntrinsicRegistry<>(classes);
      DefaultIntrinsicRegistry<WasmGCBodyIntrinsic> bodyIntrinsics = new DefaultIntrinsicRegistry<>(classes);
      GCDebugInfoBuilder debugInfoBuilder = new GCDebugInfoBuilder();
      LinkedHashSet<MethodReference> methodsOnCallSites = new LinkedHashSet<>();

      for (Supplier<Collection<MethodReference>> provider : this.additionalMethodsOnCallSites) {
         methodsOnCallSites.addAll(provider.get());
      }

      AsyncMethodFinder asyncMethodFinder = new AsyncMethodFinder(this.controller.getDependencyInfo().getCallGraph(), this.controller.getDependencyInfo());
      asyncMethodFinder.find(classes);
      WasmGCDeclarationsGenerator declarationsGenerator = new WasmGCDeclarationsGenerator(
         module,
         classes,
         this.controller.getUnprocessedClassSource(),
         this.controller.getResourceProvider(),
         this.controller.getClassLoader(),
         this.controller.getClassInitializerInfo(),
         this.controller.getDependencyInfo(),
         this.controller.getDiagnostics(),
         this.customTypeMapperFactories,
         this.controller::isVirtual,
         this.strict,
         this.controller.getEntryPoint(),
         methodsOnCallSites,
         inlineIntrinsics,
         bodyIntrinsics
      );
      declarationsGenerator.setAsyncMethodFinder(asyncMethodFinder);
      declarationsGenerator.setFriendlyToDebugger(this.controller.isFriendlyToDebugger());
      declarationsGenerator.setCompactMode(this.compactMode);
      WasmGCModuleGenerator moduleGenerator = new WasmGCModuleGenerator(declarationsGenerator);
      WasmGCCodeGenContext codeGenContext = this.createCodeGenContext(classes, declarationsGenerator, asyncMethodFinder.getAsyncFamilyMethods());
      WasmGCCodeGenRegistry codeGenRegistry = this.createCodeGenRegistry(inlineIntrinsics, bodyIntrinsics);
      WasmGCIntrinsics.apply(this.reflection, codeGenContext, inlineIntrinsics, bodyIntrinsics);

      for (WasmGCCodeGenContributor contributor : this.intrinsicContributors) {
         contributor.contribute(codeGenContext, codeGenRegistry);
      }

      WasmGCResourcesIntrinsic resourceGenerator = new WasmGCResourcesIntrinsic(
         this.controller.getProperties(), codeGenContext, this.controller.extensionEnvironment()
      );
      inlineIntrinsics.registerIntrinsic(WasmGCResources.class, resourceGenerator);
      MethodDependencyInfo internMethod = this.controller.getDependencyInfo().getMethod(new MethodReference(String.class, "intern", String.class));
      if (internMethod != null && internMethod.isUsed()) {
         WasmFunction removeStringEntryFunction = moduleGenerator.generateReportGarbageCollectedStringFunction();
         removeStringEntryFunction.setExportName("teavm.reportGarbageCollectedString");
      }

      MethodReference exceptionMessageRef = new MethodReference(Throwable.class, "getMessage", String.class);
      if (this.controller.getDependencyInfo().getMethod(exceptionMessageRef) != null) {
         WasmFunction exceptionMessageFunction = declarationsGenerator.functions().forInstanceMethod(exceptionMessageRef);
         exceptionMessageFunction.setExportName("teavm.exceptionMessage");
      }

      MethodReference refQueueSupplyRef = new MethodReference(ReferenceQueue.class, "supply", Reference.class, void.class);
      if (this.controller.getDependencyInfo().getMethod(refQueueSupplyRef) != null) {
         WasmFunction refQueueSupplyFunction = declarationsGenerator.functions().forInstanceMethod(refQueueSupplyRef);
         refQueueSupplyFunction.setExportName("teavm.reportGarbageCollectedValue");
      }

      boolean buffersHeap = needsBuffersHeap(this.controller.getDependencyInfo());
      if (buffersHeap) {
         declarationsGenerator.functions().forStaticMethod(new MethodReference(Heap.class, "init", Address.class, int.class, int.class, void.class));
      }

      for (String clsName : classes.getClassNames()) {
         ClassHolder cls = classes.get(clsName);

         for (MethodHolder method : cls.getMethods()) {
            if (method.hasModifier(ElementModifier.STATIC) && method.getProgram() != null && method.getAnnotations().get(Export.class.getName()) != null) {
               declarationsGenerator.functions().forStaticMethod(method.getReference());
            }
         }
      }

      moduleGenerator.generate();
      resourceGenerator.writeModule(module);
      this.generateExceptionExports(declarationsGenerator);
      this.adjustModuleMemory(module, moduleGenerator, buffersHeap);
      this.emitWasmFile(module, buildTarget, outputName, debugInfoBuilder);
   }

   private void generateExceptionExports(WasmGCDeclarationsGenerator declarationsGenerator) {
      int nativeExceptionField = declarationsGenerator.classInfoProvider().getThrowableNativeOffset();
      if (nativeExceptionField >= 0) {
         WasmStructure throwableType = declarationsGenerator.classInfoProvider().getClassInfo("java.lang.Throwable").getStructure();
         WasmFunction getFunction = new WasmFunction(declarationsGenerator.functionTypes.of(WasmType.EXTERN, throwableType.getReference()));
         getFunction.setName("teavm.getJsException");
         getFunction.setExportName("teavm.getJsException");
         WasmLocal getParam = new WasmLocal(throwableType.getReference(), "javaException");
         getFunction.add(getParam);
         getFunction.getBody().builder().getLocal(getParam).structGet(throwableType, nativeExceptionField);
         declarationsGenerator.module.functions.add(getFunction);
         WasmFunction setFunction = new WasmFunction(declarationsGenerator.functionTypes.of(null, throwableType.getReference(), WasmType.EXTERN));
         setFunction.setName("teavm.setJsException");
         setFunction.setExportName("teavm.setJsException");
         WasmLocal setParam = new WasmLocal(throwableType.getReference(), "javaException");
         WasmLocal setValue = new WasmLocal(WasmType.EXTERN, "jsException");
         setFunction.add(setParam);
         setFunction.add(setValue);
         setFunction.getBody().builder().getLocal(setParam).getLocal(setValue).structSet(throwableType, nativeExceptionField);
         declarationsGenerator.module.functions.add(setFunction);
      }
   }

   private WasmGCCodeGenContext createCodeGenContext(
      final ListableClassReaderSource classes, final WasmGCDeclarationsGenerator generator, final Set<MethodReference> asyncSplitMethods
   ) {
      class ContextImpl extends BaseIntrinsicContributorContext implements WasmGCCodeGenContext {
         private WasmGCInitializerRegistry initializerRegistry = generator::addToInitializer;

         ContextImpl() {
            super(WasmGCTarget.this.controller, generator.hierarchy, classes, asyncSplitMethods);
         }

         @Override
         public WasmModule module() {
            return generator.module;
         }

         @Override
         public WasmFunctionTypes functionTypes() {
            return generator.functionTypes;
         }

         @Override
         public BaseWasmFunctionRepository functions() {
            return generator.functions();
         }

         @Override
         public WasmGCNameProvider names() {
            return generator.names();
         }

         @Override
         public WasmGCStringProvider strings() {
            return generator.strings();
         }

         @Override
         public WasmGCTypeMapper typeMapper() {
            return generator.typeMapper();
         }

         @Override
         public WasmTag exceptionTag() {
            return generator.exceptionTag();
         }

         @Override
         public String entryPoint() {
            return WasmGCTarget.this.controller.getEntryPoint();
         }

         @Override
         public WasmGCClassInfoProvider classInfoProvider() {
            return generator.classInfoProvider();
         }

         @Override
         public WasmGCVirtualTableProvider virtualTables() {
            return generator.virtualTables;
         }

         @Override
         public WasmGCInitializerRegistry initializerRegistry() {
            return this.initializerRegistry;
         }
      }

      return new ContextImpl();
   }

   private WasmGCCodeGenRegistry createCodeGenRegistry(
      final IntrinsicRegistry<WasmGCInlineIntrinsic> callSiteIntrinsics, final IntrinsicRegistry<WasmGCBodyIntrinsic> bodyIntrinsics
   ) {
      return new WasmGCCodeGenRegistry() {
         @Override
         public IntrinsicRegistry<WasmGCInlineIntrinsic> inlineIntrinsics() {
            return callSiteIntrinsics;
         }

         @Override
         public IntrinsicRegistry<WasmGCBodyIntrinsic> bodyIntrinsics() {
            return bodyIntrinsics;
         }
      };
   }

   private void adjustModuleMemory(WasmModule module, WasmGCModuleGenerator moduleGenerator, boolean buffersHeap) {
      int memorySize = 0;

      for (WasmMemorySegment segment : module.getSegments()) {
         memorySize = Math.max(memorySize, segment.getOffset() + segment.getLength());
      }

      WasmGlobal heapOffset = new WasmGlobal("heapOffset", WasmType.INT32);
      heapOffset.setImmutable(true);
      heapOffset.setImportModule("teavmMemory");
      heapOffset.setImportName("heapOffset");
      module.globals.add(heapOffset);
      WasmGlobal maxSize = new WasmGlobal("maxSize", WasmType.INT32);
      maxSize.setImmutable(true);
      maxSize.setImportModule("teavmMemory");
      maxSize.setImportName("maxSize");
      module.globals.add(maxSize);
      this.dataSize = memorySize;
      if (buffersHeap) {
         memorySize = ((memorySize - 1) / 256 + 1) * 256;
         moduleGenerator.initBuffersHeap(heapOffset, this.bufferHeapMinSize, maxSize);
         memorySize += this.bufferHeapMinSize;
      }

      int pages = (memorySize - 1) / 65536 + 1;
      module.setMinMemorySize(pages);
      module.setMaxMemorySize(32768);
      module.sharedMemory = this.sharedBuffer;
   }

   private static boolean needsBuffersHeap(DependencyInfo dependencyInfo) {
      return dependencyInfo.getReachableMethods().stream().anyMatch(m -> m.getClassName().equals(Heap.class.getName()) && !m.getName().equals("init"));
   }

   private void emitWasmFile(WasmModule module, BuildTarget buildTarget, String outputName, GCDebugInfoBuilder debugInfoBuilder) throws IOException {
      WasmBinaryWriter binaryWriter = new WasmBinaryWriter();
      DebugLines debugLines = null;
      if (this.debugInfo) {
         if (this.sourceMapBuilder != null) {
            debugLines = new CompositeDebugLines(debugInfoBuilder.lines(), this.sourceMapBuilder);
         } else {
            debugLines = debugInfoBuilder.lines();
         }
      } else if (this.sourceMapBuilder != null) {
         debugLines = this.sourceMapBuilder;
      }

      if (!outputName.endsWith(".wasm")) {
         outputName = outputName + ".wasm";
      }

      if (this.sourceMapBuilder != null && this.sourceMapLocation != null) {
         WasmBinaryWriter sourceMapBinding = new WasmBinaryWriter();
         sourceMapBinding.writeAsciiString(this.sourceMapLocation);
         WasmCustomSection sourceMapSection = new WasmCustomSection("sourceMappingURL", sourceMapBinding.getData());
         module.add(sourceMapSection);
      }

      WasmBinaryRenderer binaryRenderer = new WasmBinaryRenderer(
         binaryWriter, WasmBinaryVersion.V_0x1, this.obfuscated, null, null, debugLines, null, WasmBinaryStatsCollector.EMPTY
      );
      this.optimizeIndexes(module);
      module.prepareForRendering();
      binaryRenderer.render(module, this.customSections(debugInfoBuilder, module));
      byte[] data = binaryWriter.getData();

      try (OutputStream output = buildTarget.createResource(outputName)) {
         output.write(data);
      }

      if (this.debugLocation == WasmDebugInfoLocation.EXTERNAL && this.debugInfo) {
         byte[] debugInfoData = ExternalDebugFile.write(debugInfoBuilder.build());
         if (debugInfoData != null) {
            try (OutputStream output = buildTarget.createResource(outputName + ".teadbg")) {
               output.write(debugInfoData);
            }
         }
      }
   }

   private Supplier<Collection<? extends WasmCustomSection>> customSections(GCDebugInfoBuilder debugInfoBuilder, WasmModule module) {
      return () -> {
         ArrayList<WasmCustomSection> list = new ArrayList<>();
         if (this.debugLocation == WasmDebugInfoLocation.EMBEDDED && this.debugInfo) {
            list.addAll(debugInfoBuilder.build());
         }

         byte[] reqs = this.writeMemoryRequirements(module);
         list.add(new WasmCustomSection("teavm.memoryRequirements", reqs));
         list.add(new WasmCustomSection("teavm.imports", this.writeImportedModules(module)));
         return list;
      };
   }

   private byte[] writeMemoryRequirements(WasmModule module) {
      String data = "{\"min\":" + module.getMinMemorySize() + ", \"dataSize\":" + this.dataSize + ", \"shared\": " + this.sharedBuffer + "}";
      return data.getBytes(StandardCharsets.UTF_8);
   }

   private byte[] writeImportedModules(WasmModule module) {
      StringWriter writer = new StringWriter();

      try {
         writer.write("[");
         boolean first = true;

         for (WasmGlobal global : module.globals) {
            if (global.getImportModule() != null && global.getImportName() != null) {
               if (!first) {
                  writer.write(",");
               }

               first = false;
               writer.write("{\"module\":\"");
               JsonUtil.writeEscapedString(writer, global.getImportModule());
               writer.write("\",\"name\":\"");
               JsonUtil.writeEscapedString(writer, global.getImportName());
               writer.write("\"}");
            }
         }

         writer.write("]");
      } catch (IOException e) {
         throw new RuntimeException(e);
      }

      return writer.toString().getBytes(StandardCharsets.UTF_8);
   }

   private void optimizeIndexes(WasmModule module) {
      WasmUsageCounter usageCounter = new WasmUsageCounter();
      usageCounter.applyToModule(module);
      module.functions.sort(Comparator.comparingInt(f -> -usageCounter.usages(f)));
      module.globals.sort(Comparator.comparingInt(g -> -usageCounter.usages(g)));
      module.types.sort(Comparator.comparingInt(t -> -usageCounter.usages(t)));
   }

   @Override
   public boolean needsSystemArrayCopyOptimization() {
      return false;
   }

   @Override
   public boolean filterClassInitializer(String initializer) {
      return !initializer.equals(StringInternPool.class.getName());
   }
}
