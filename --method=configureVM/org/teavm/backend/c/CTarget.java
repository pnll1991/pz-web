package org.teavm.backend.c;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.teavm.ast.InvocationExpr;
import org.teavm.ast.decompilation.Decompiler;
import org.teavm.backend.c.analyze.CDependencyListener;
import org.teavm.backend.c.analyze.InteropDependencyListener;
import org.teavm.backend.c.generate.BufferedCodeWriter;
import org.teavm.backend.c.generate.CNameProvider;
import org.teavm.backend.c.generate.CallSiteGenerator;
import org.teavm.backend.c.generate.ClassGenerationContext;
import org.teavm.backend.c.generate.ClassGenerator;
import org.teavm.backend.c.generate.CodeGenerationVisitor;
import org.teavm.backend.c.generate.CodeGeneratorUtil;
import org.teavm.backend.c.generate.CodeWriter;
import org.teavm.backend.c.generate.FileNameProvider;
import org.teavm.backend.c.generate.GenerationContext;
import org.teavm.backend.c.generate.IncludeManager;
import org.teavm.backend.c.generate.MethodConvertersGenerator;
import org.teavm.backend.c.generate.OutputFileUtil;
import org.teavm.backend.c.generate.SimpleFileNameProvider;
import org.teavm.backend.c.generate.SimpleIncludeManager;
import org.teavm.backend.c.generate.SimpleStringPool;
import org.teavm.backend.c.generate.StringPoolGenerator;
import org.teavm.backend.c.generators.ArrayGenerator;
import org.teavm.backend.c.generators.CResourcesGenerator;
import org.teavm.backend.c.generators.ClassMethodsGenerator;
import org.teavm.backend.c.generators.Generator;
import org.teavm.backend.c.generators.GeneratorFactory;
import org.teavm.backend.c.generators.ReferenceQueueGenerator;
import org.teavm.backend.c.generators.WeakReferenceGenerator;
import org.teavm.backend.c.intrinsic.AddressIntrinsic;
import org.teavm.backend.c.intrinsic.CResourcesIntrinsic;
import org.teavm.backend.c.intrinsic.ConsoleIntrinsic;
import org.teavm.backend.c.intrinsic.ExceptionHandlingIntrinsic;
import org.teavm.backend.c.intrinsic.FunctionClassIntrinsic;
import org.teavm.backend.c.intrinsic.FunctionIntrinsic;
import org.teavm.backend.c.intrinsic.GCIntrinsic;
import org.teavm.backend.c.intrinsic.IntegerIntrinsic;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.c.intrinsic.IntrinsicContext;
import org.teavm.backend.c.intrinsic.IntrinsicFactory;
import org.teavm.backend.c.intrinsic.LongIntrinsic;
import org.teavm.backend.c.intrinsic.MemoryTraceIntrinsic;
import org.teavm.backend.c.intrinsic.MutatorIntrinsic;
import org.teavm.backend.c.intrinsic.ObjectIntrinsic;
import org.teavm.backend.c.intrinsic.RuntimeClassIntrinsic;
import org.teavm.backend.c.intrinsic.ShadowStackIntrinsic;
import org.teavm.backend.c.intrinsic.StringIntrinsic;
import org.teavm.backend.c.intrinsic.StringsIntrinsic;
import org.teavm.backend.c.intrinsic.StructureIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.AnnotationConstructorIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.AnnotationDataIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.AnnotationInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.AnnotationValueArrayIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.ClassInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.ClassReflectionInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.DerivedClassInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.FieldInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.FieldReflectionInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.GenericArrayInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.GenericTypeInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.MethodInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.MethodReflectionInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.ParameterInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.ParameterizedTypeInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.ProxyIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.ProxyIntrinsicContext;
import org.teavm.backend.c.intrinsic.reflection.ProxyMethodIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.RawTypeInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.StringInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.TypeVariableInfoIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.TypeVariableReferenceIntrinsic;
import org.teavm.backend.c.intrinsic.reflection.WildcardTypeInfoIntrinsic;
import org.teavm.backend.c.runtime.CResources;
import org.teavm.backend.c.transform.CFileSystemTransformer;
import org.teavm.backend.c.transform.ClassLoaderCResourceTransformation;
import org.teavm.backend.c.transform.SynchronizedMethodTransformer;
import org.teavm.backend.lowlevel.analyze.LowLevelInliningFilterFactory;
import org.teavm.backend.lowlevel.dependency.BufferDependencyListener;
import org.teavm.backend.lowlevel.dependency.ExceptionHandlingDependencyListener;
import org.teavm.backend.lowlevel.dependency.StringsDependencyListener;
import org.teavm.backend.lowlevel.dependency.WeakReferenceDependencyListener;
import org.teavm.backend.lowlevel.generate.NameProvider;
import org.teavm.backend.lowlevel.generate.NameProviderWithSpecialNames;
import org.teavm.backend.lowlevel.transform.CoroutineTransformation;
import org.teavm.backend.lowlevel.transform.WeakReferenceTransformation;
import org.teavm.cache.EmptyMethodNodeCache;
import org.teavm.cache.MethodNodeCache;
import org.teavm.classlib.ReflectionSupplier;
import org.teavm.common.JsonUtil;
import org.teavm.dependency.ClassDependency;
import org.teavm.dependency.DependencyAnalyzer;
import org.teavm.dependency.DependencyListener;
import org.teavm.interop.Address;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassReader;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.ListableClassHolderSource;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ClassMetadataRequirements;
import org.teavm.model.classes.TagRegistry;
import org.teavm.model.classes.VirtualTableBuilder;
import org.teavm.model.classes.VirtualTableProvider;
import org.teavm.model.lowlevel.CallSiteDescriptor;
import org.teavm.model.lowlevel.Characteristics;
import org.teavm.model.lowlevel.ClassInitializerEliminator;
import org.teavm.model.lowlevel.ClassInitializerTransformer;
import org.teavm.model.lowlevel.ExportDependencyListener;
import org.teavm.model.lowlevel.LowLevelNullCheckFilter;
import org.teavm.model.lowlevel.ShadowStackTransformer;
import org.teavm.model.lowlevel.WriteBarrierInsertion;
import org.teavm.model.optimization.InliningFilterFactory;
import org.teavm.model.transformation.BoundCheckInsertion;
import org.teavm.model.transformation.ClassPatch;
import org.teavm.model.transformation.NullCheckInsertion;
import org.teavm.model.util.AsyncMethodFinder;
import org.teavm.model.util.DefaultVariableCategoryProvider;
import org.teavm.model.util.VariableCategoryProvider;
import org.teavm.reflection.AnnotationGenerationHelper;
import org.teavm.reflection.ReflectionDependencyListener;
import org.teavm.runtime.Allocator;
import org.teavm.runtime.CallSite;
import org.teavm.runtime.CallSiteLocation;
import org.teavm.runtime.EventQueue;
import org.teavm.runtime.ExceptionHandling;
import org.teavm.runtime.Fiber;
import org.teavm.runtime.GC;
import org.teavm.runtime.RuntimeArray;
import org.teavm.runtime.RuntimeClass;
import org.teavm.runtime.RuntimeObject;
import org.teavm.runtime.reflect.AnnotationData;
import org.teavm.runtime.reflect.ClassInfo;
import org.teavm.runtime.reflect.ClassReflectionInfo;
import org.teavm.runtime.reflect.FieldReflectionInfo;
import org.teavm.runtime.reflect.GenericTypeInfo;
import org.teavm.runtime.reflect.MethodInfo;
import org.teavm.runtime.reflect.MethodReflectionInfo;
import org.teavm.runtime.reflect.ParameterInfo;
import org.teavm.runtime.reflect.TypeVariableInfo;
import org.teavm.vm.BuildTarget;
import org.teavm.vm.TeaVMTarget;
import org.teavm.vm.TeaVMTargetController;
import org.teavm.vm.spi.TeaVMHostExtension;

public class CTarget implements TeaVMTarget, TeaVMCHost {
   private static final Set<MethodReference> VIRTUAL_METHODS = new HashSet<>(Arrays.asList(new MethodReference(Object.class, "clone", Object.class)));
   private static final String[] RUNTIME_FILES = new String[]{
      "core.c",
      "core.h",
      "date.c",
      "date.h",
      "definitions.h",
      "exceptions.h",
      "fiber.c",
      "fiber.h",
      "file.c",
      "file.h",
      "heapdump.c",
      "heapdump.h",
      "heaptrace.c",
      "heaptrace.h",
      "log.c",
      "log.h",
      "memory.c",
      "memory.h",
      "references.c",
      "references.h",
      "resource.c",
      "resource.h",
      "runtime.h",
      "stack.c",
      "stack.h",
      "string.c",
      "string.h",
      "stringhash.c",
      "stringhash.h",
      "time.c",
      "time.h",
      "virtcall.c",
      "virtcall.h",
      "arrayclass.c",
      "arrayclass.h",
      "uchar.h",
      "reflection.c",
      "reflection.h",
      "proxy.h"
   };
   private TeaVMTargetController controller;
   private CNameProvider rawNameProvider;
   private FileNameProvider fileNames = new SimpleFileNameProvider();
   private ClassInitializerEliminator classInitializerEliminator;
   private ClassInitializerTransformer classInitializerTransformer;
   private ShadowStackTransformer shadowStackTransformer;
   private WriteBarrierInsertion writeBarrierInsertion;
   private NullCheckInsertion nullCheckInsertion;
   private BoundCheckInsertion boundCheckInsertion = new BoundCheckInsertion();
   private ExportDependencyListener exportDependencyListener = new ExportDependencyListener();
   private int minHeapSize = 4194304;
   private int maxHeapSize = 134217728;
   private List<IntrinsicFactory> intrinsicFactories = new ArrayList<>();
   private List<GeneratorFactory> generatorFactories = new ArrayList<>();
   private Characteristics characteristics;
   private Set<MethodReference> asyncMethods;
   private boolean hasThreads;
   private MethodNodeCache astCache = EmptyMethodNodeCache.INSTANCE;
   private boolean incremental;
   private boolean lineNumbersGenerated;
   private SimpleStringPool stringPool;
   private boolean heapDump;
   private boolean obfuscated;
   private List<CallSiteDescriptor> callSites = new ArrayList<>();
   private ReflectionDependencyListener reflection;
   private ProxyIntrinsicContext proxyContext;

   public CTarget(CNameProvider nameProvider) {
      this.rawNameProvider = nameProvider;
   }

   public void setMinHeapSize(int minHeapSize) {
      this.minHeapSize = minHeapSize;
   }

   public void setMaxHeapSize(int maxHeapSize) {
      this.maxHeapSize = maxHeapSize;
   }

   public void setIncremental(boolean incremental) {
      this.incremental = incremental;
   }

   public void setLineNumbersGenerated(boolean lineNumbersGenerated) {
      this.lineNumbersGenerated = lineNumbersGenerated;
   }

   public void setHeapDump(boolean heapDump) {
      this.heapDump = heapDump;
   }

   public void setAstCache(MethodNodeCache astCache) {
      this.astCache = astCache;
   }

   public void setObfuscated(boolean obfuscated) {
      this.obfuscated = obfuscated;
   }

   public void setFileNames(FileNameProvider fileNames) {
      this.fileNames = fileNames;
   }

   @Override
   public List<ClassHolderTransformer> getTransformers() {
      List<ClassHolderTransformer> transformers = new ArrayList<>();
      transformers.add(new ClassPatch());
      transformers.add(new CDependencyListener());
      transformers.add(new WeakReferenceTransformation());
      transformers.add(new CFileSystemTransformer());
      transformers.add(new SynchronizedMethodTransformer());
      transformers.add(new ClassLoaderCResourceTransformation());
      return transformers;
   }

   @Override
   public List<DependencyListener> getDependencyListeners() {
      return Arrays.asList(new CDependencyListener(), this.exportDependencyListener, new InteropDependencyListener(), new WeakReferenceDependencyListener());
   }

   @Override
   public void setController(TeaVMTargetController controller) {
      this.controller = controller;
      this.characteristics = new Characteristics(controller.getUnprocessedClassSource());
      this.classInitializerEliminator = new ClassInitializerEliminator(controller.getUnprocessedClassSource());
      this.classInitializerTransformer = new ClassInitializerTransformer();
      this.shadowStackTransformer = new ShadowStackTransformer(this.characteristics);
      this.nullCheckInsertion = new NullCheckInsertion(new LowLevelNullCheckFilter(this.characteristics));
      this.writeBarrierInsertion = new WriteBarrierInsertion(this.characteristics);
      controller.addVirtualMethods(m -> VIRTUAL_METHODS.contains(m) || this.reflection.isVirtual(m));
   }

   @Override
   public List<TeaVMHostExtension> getHostExtensions() {
      return Collections.singletonList(this);
   }

   @Override
   public void addIntrinsic(IntrinsicFactory intrinsicFactory) {
      this.intrinsicFactories.add(intrinsicFactory);
   }

   @Override
   public void addGenerator(GeneratorFactory generatorFactory) {
      this.generatorFactories.add(generatorFactory);
   }

   @Override
   public VariableCategoryProvider variableCategoryProvider() {
      return new DefaultVariableCategoryProvider();
   }

   @Override
   public void contributeDependencies(DependencyAnalyzer dependencyAnalyzer) {
      dependencyAnalyzer.linkMethod(new MethodReference(Class.class, "createClass", ClassInfo.class, Class.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(Allocator.class, "allocate", RuntimeClass.class, Address.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(Allocator.class, "allocateArray", RuntimeClass.class, int.class, Address.class)).use();
      dependencyAnalyzer.linkMethod(
            new MethodReference(Allocator.class, "allocateMultiArray", RuntimeClass.class, Address.class, int.class, RuntimeArray.class)
         )
         .use();
      dependencyAnalyzer.linkMethod(new MethodReference(Allocator.class, "<clinit>", void.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(GC.class, "fixHeap", void.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(GC.class, "tryShrink", void.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(GC.class, "collectGarbage", void.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(GC.class, "collectGarbageFull", void.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(ExceptionHandling.class, "throwException", Throwable.class, void.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(ExceptionHandling.class, "throwClassCastException", void.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(ExceptionHandling.class, "throwNullPointerException", void.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(ExceptionHandling.class, "throwArrayIndexOutOfBoundsException", void.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(NullPointerException.class, "<init>", void.class))
         .propagateClass(0, NullPointerException.class.getName())
         .use();
      dependencyAnalyzer.linkMethod(new MethodReference(ExceptionHandling.class, "catchException", Throwable.class)).use();
      dependencyAnalyzer.linkClass("java.lang.String");
      dependencyAnalyzer.linkClass("java.lang.Class");
      dependencyAnalyzer.linkField(new FieldReference("java.lang.String", "hashCode"));
      ClassDependency runtimeClassDep = dependencyAnalyzer.linkClass(RuntimeClass.class.getName());
      ClassDependency runtimeObjectDep = dependencyAnalyzer.linkClass(RuntimeObject.class.getName());
      ClassDependency runtimeArrayDep = dependencyAnalyzer.linkClass(RuntimeArray.class.getName());

      for (ClassDependency classDep : Arrays.asList(runtimeClassDep, runtimeObjectDep, runtimeArrayDep)) {
         for (FieldReader field : classDep.getClassReader().getFields()) {
            dependencyAnalyzer.linkField(field.getReference());
         }
      }

      dependencyAnalyzer.linkMethod(new MethodReference(Fiber.class, "isResuming", boolean.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(Fiber.class, "isSuspending", boolean.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(Fiber.class, "current", Fiber.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(Fiber.class, "startMain", String[].class, void.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(EventQueue.class, "process", void.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(Thread.class, "setCurrentThread", Thread.class, void.class)).use();
      ClassReader fiberClass = dependencyAnalyzer.getClassSource().get(Fiber.class.getName());

      for (MethodReader method : fiberClass.getMethods()) {
         if (method.getName().startsWith("pop") || method.getName().equals("push")) {
            dependencyAnalyzer.linkMethod(method.getReference()).use();
         }
      }

      dependencyAnalyzer.linkClass(CallSite.class.getName());
      dependencyAnalyzer.linkClass(CallSiteLocation.class.getName());
      dependencyAnalyzer.linkMethod(new MethodReference(CResources.class, "create", String.class, int.class, int.class, CResources.Resource.class)).use();
      dependencyAnalyzer.addDependencyListener(new ExceptionHandlingDependencyListener());
      dependencyAnalyzer.addDependencyListener(new StringsDependencyListener());
      dependencyAnalyzer.addDependencyListener(new BufferDependencyListener());
      ArrayList<ReflectionSupplier> reflectionSuppliers = new ArrayList<>();

      for (ReflectionSupplier supplier : ServiceLoader.load(ReflectionSupplier.class, dependencyAnalyzer.getClassLoader())) {
         reflectionSuppliers.add(supplier);
      }

      this.reflection = new ReflectionDependencyListener(reflectionSuppliers, new AnnotationGenerationHelper());
      dependencyAnalyzer.addDependencyListener(this.reflection);
   }

   @Override
   public void analyzeBeforeOptimizations(ListableClassReaderSource classSource) {
      AsyncMethodFinder asyncFinder = new AsyncMethodFinder(this.controller.getDependencyInfo().getCallGraph(), this.controller.getDependencyInfo());
      asyncFinder.find(classSource);
      this.asyncMethods = new HashSet<>(asyncFinder.getAsyncMethods());
      this.asyncMethods.addAll(asyncFinder.getAsyncFamilyMethods());
      this.hasThreads = asyncFinder.hasAsyncMethods();
   }

   @Override
   public void beforeInlining(Program program, MethodReader method) {
      this.nullCheckInsertion.transformProgram(program, method.getReference());
   }

   @Override
   public void beforeOptimizations(Program program, MethodReader method) {
      this.boundCheckInsertion.transformProgram(program, method.getReference());
   }

   @Override
   public void afterOptimizations(Program program, MethodReader method) {
      this.classInitializerEliminator.apply(program);
      new CoroutineTransformation(this.controller.getUnprocessedClassSource(), this.asyncMethods, this.hasThreads).apply(program, method.getReference());
      ShadowStackTransformer shadowStackTransformer = !this.incremental ? this.shadowStackTransformer : new ShadowStackTransformer(this.characteristics);
      shadowStackTransformer.apply(program, method);
      this.writeBarrierInsertion.apply(program);
   }

   @Override
   public void emit(ListableClassHolderSource classes, BuildTarget buildTarget, String outputName) throws IOException {
      VirtualTableProvider vtableProvider = !this.incremental ? this.createVirtualTableProvider(classes) : null;
      ClassHierarchy hierarchy = new ClassHierarchy(classes);
      TagRegistry tagRegistry = !this.incremental ? new TagRegistry(classes, hierarchy) : null;
      Decompiler decompiler = new Decompiler(classes, new HashSet<>(), this.controller.isFriendlyToDebugger());
      NameProviderWithSpecialNames nameProvider = new NameProviderWithSpecialNames(this.rawNameProvider, this.controller.getUnprocessedClassSource());
      List<Intrinsic> intrinsics = new ArrayList<>();
      intrinsics.add(new ShadowStackIntrinsic());
      intrinsics.add(new AddressIntrinsic());
      intrinsics.add(new StructureIntrinsic(this.characteristics));
      intrinsics.add(new GCIntrinsic());
      intrinsics.add(new MemoryTraceIntrinsic());
      intrinsics.add(new MutatorIntrinsic());
      intrinsics.add(new ExceptionHandlingIntrinsic());
      intrinsics.add(new FunctionIntrinsic(this.characteristics, this.exportDependencyListener.getResolvedMethods()));
      intrinsics.add(new FunctionClassIntrinsic());
      intrinsics.add(new RuntimeClassIntrinsic());
      intrinsics.add(new ObjectIntrinsic());
      intrinsics.add(new StringIntrinsic());
      intrinsics.add(new CTarget.FiberIntrinsic());
      intrinsics.add(new LongIntrinsic());
      intrinsics.add(new IntegerIntrinsic());
      intrinsics.add(new StringsIntrinsic());
      intrinsics.add(new ConsoleIntrinsic());
      intrinsics.add(new CResourcesIntrinsic());
      this.proxyContext = new ProxyIntrinsicContext();
      intrinsics.add(new ProxyIntrinsic());
      intrinsics.add(new ProxyMethodIntrinsic(this.reflection, this.proxyContext));
      intrinsics.add(new StringInfoIntrinsic());
      intrinsics.add(new ClassInfoIntrinsic());
      intrinsics.add(new ClassReflectionInfoIntrinsic());
      intrinsics.add(new AnnotationInfoIntrinsic());
      intrinsics.add(new AnnotationConstructorIntrinsic());
      intrinsics.add(new AnnotationDataIntrinsic(classes));
      intrinsics.add(new AnnotationValueArrayIntrinsic());
      intrinsics.add(new DerivedClassInfoIntrinsic());
      intrinsics.add(new FieldInfoIntrinsic());
      intrinsics.add(new FieldReflectionInfoIntrinsic());
      intrinsics.add(new MethodInfoIntrinsic());
      intrinsics.add(new MethodReflectionInfoIntrinsic());
      intrinsics.add(new ParameterInfoIntrinsic());
      intrinsics.add(new TypeVariableInfoIntrinsic());
      intrinsics.add(new GenericTypeInfoIntrinsic());
      intrinsics.add(new ParameterizedTypeInfoIntrinsic());
      intrinsics.add(new TypeVariableReferenceIntrinsic());
      intrinsics.add(new GenericArrayInfoIntrinsic());
      intrinsics.add(new WildcardTypeInfoIntrinsic());
      intrinsics.add(new RawTypeInfoIntrinsic());
      List<Generator> generators = new ArrayList<>();
      generators.add(new ArrayGenerator());
      generators.add(new ClassMethodsGenerator());
      generators.add(new WeakReferenceGenerator());
      generators.add(new ReferenceQueueGenerator());
      generators.add(
         new CResourcesGenerator(
            this.controller.getProperties(), this.controller.getResourceProvider(), this.controller.getClassLoader(), this.controller.extensionEnvironment()
         )
      );
      new ArrayList();
      this.stringPool = new SimpleStringPool();
      boolean vmAssertions = Boolean.parseBoolean(System.getProperty("teavm.c.vmAssertions", "false"));
      boolean gcStats = Boolean.parseBoolean(System.getProperty("teavm.c.gcStats", "false"));
      ClassMetadataRequirements metadataRequirements = new ClassMetadataRequirements(this.controller.getDependencyInfo());
      LinkedHashSet<ValueType> types = new LinkedHashSet<>();
      LinkedHashSet<ValueType> typeLiterals = new LinkedHashSet<>();
      GenerationContext context = new GenerationContext(
         vtableProvider,
         this.characteristics,
         this.controller.getDependencyInfo(),
         this.stringPool,
         nameProvider,
         this.fileNames,
         this.controller.getDiagnostics(),
         classes,
         this.controller.getUnprocessedClassSource(),
         hierarchy,
         intrinsics,
         generators,
         this.asyncMethods::contains,
         buildTarget,
         this.controller.getClassInitializerInfo(),
         this.incremental,
         vmAssertions,
         vmAssertions || this.heapDump,
         this.obfuscated,
         types,
         typeLiterals,
         metadataRequirements
      );

      for (String clsName : classes.getClassNames()) {
         types.add(ValueType.object(clsName));
      }

      BufferedCodeWriter specialWriter = new BufferedCodeWriter(false);
      BufferedCodeWriter configHeaderWriter = new BufferedCodeWriter(false);
      configHeaderWriter.println("#pragma once");
      if (this.incremental) {
         configHeaderWriter.println("#define TEAVM_INCREMENTAL 1");
      }

      if (vmAssertions) {
         configHeaderWriter.println("#define TEAVM_MEMORY_TRACE 1");
      }

      if (this.heapDump) {
         configHeaderWriter.println("#define TEAVM_HEAP_DUMP 1");
      }

      if (this.obfuscated) {
         configHeaderWriter.println("#define TEAVM_OBFUSCATED 1");
      }

      if (gcStats) {
         configHeaderWriter.println("#define TEAVM_GC_STATS 1");
      }

      MethodConvertersGenerator methodConvertersGenerator = new MethodConvertersGenerator(context.isIncremental(), this.fileNames);
      ClassGenerator classGenerator = new ClassGenerator(
         context, tagRegistry, decompiler, this.controller.getCacheStatus(), types, this.reflection, methodConvertersGenerator
      );
      classGenerator.setAstCache(this.astCache);
      if (!context.isIncremental()) {
         classGenerator.setCallSites(this.callSites);
      }

      IntrinsicFactoryContextImpl intrinsicFactoryContext = new IntrinsicFactoryContextImpl(
         this.controller.getUnprocessedClassSource(),
         this.controller.getResourceProvider(),
         this.controller.getClassLoader(),
         this.controller.getServices(),
         this.controller.getProperties()
      );

      for (IntrinsicFactory intrinsicFactory : this.intrinsicFactories) {
         context.addIntrinsic(intrinsicFactory.createIntrinsic(intrinsicFactoryContext));
      }

      for (GeneratorFactory generatorFactory : this.generatorFactories) {
         context.addGenerator(generatorFactory.createGenerator(intrinsicFactoryContext));
      }

      this.generateClasses(context, classes, classGenerator, buildTarget, types);
      this.generateSpecialFunctions(context, specialWriter);
      OutputFileUtil.write(configHeaderWriter, "config.h", buildTarget);
      OutputFileUtil.write(specialWriter, "special.c", buildTarget);

      for (String runtimeFile : RUNTIME_FILES) {
         this.copyResource(runtimeFile, buildTarget);
      }

      this.generateCoreGen(buildTarget, context);
      this.generateCoreDefs(buildTarget, context);
      this.generateArrayClassGen(buildTarget, context, classes.getClassNames().size());
      this.generateReflectionGen(buildTarget, context);
      this.generateProxyGen(buildTarget, context);
      this.generateCallSites(buildTarget, context, classes.getClassNames());
      this.generateStrings(buildTarget, context);
      methodConvertersGenerator.endForBuild(buildTarget);
      List<ValueType> filteredTypes = types.stream().filter(c -> ClassGenerator.needsVirtualTable(this.characteristics, c)).collect(Collectors.toList());
      this.generateMainFile(context, classes, filteredTypes, typeLiterals, buildTarget);
      this.generateAllFile(context, classes, filteredTypes, buildTarget);
   }

   private void copyResource(String name, BuildTarget buildTarget) throws IOException {
      BufferedCodeWriter writer = new BufferedCodeWriter(false);
      this.emitResource(writer, name);
      OutputFileUtil.write(writer, name, buildTarget);
   }

   private void emitResource(CodeWriter writer, String resourceName) {
      ClassLoader classLoader = CTarget.class.getClassLoader();

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(classLoader.getResourceAsStream("org/teavm/backend/c/" + resourceName)))) {
         while (true) {
            String line = reader.readLine();
            if (line == null) {
               return;
            }

            writer.println(line);
         }
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   private void generateClasses(
      GenerationContext context, ListableClassHolderSource classes, ClassGenerator classGenerator, BuildTarget buildTarget, Collection<ValueType> types
   ) throws IOException {
      for (String className : classes.getClassNames()) {
         ClassHolder cls = classes.get(className);
         if (cls != null) {
            if (this.isSpecialClass(context, className)) {
               if (cls.getParent().equals(AnnotationData.class.getName())) {
                  BufferedCodeWriter headerWriter = new BufferedCodeWriter(false);
                  String name = this.fileNames.fileName(className);
                  classGenerator.generateAnnotationDataClass(headerWriter, cls.getName(), name);
                  OutputFileUtil.write(headerWriter, name + ".h", buildTarget);
               }
            } else {
               BufferedCodeWriter writer = new BufferedCodeWriter(this.lineNumbersGenerated);
               BufferedCodeWriter headerWriter = new BufferedCodeWriter(false);
               if (cls != null) {
                  classGenerator.generateClass(writer, headerWriter, cls);
               }

               String name = this.fileNames.fileName(className);
               OutputFileUtil.write(writer, name + ".c", buildTarget);
               OutputFileUtil.write(headerWriter, name + ".h", buildTarget);
               if (this.incremental) {
                  this.stringPool.reset();
               }
            }
         }
      }

      for (ValueType type : types) {
         if (!(type instanceof ValueType.Object)) {
            BufferedCodeWriter writer = new BufferedCodeWriter(false);
            BufferedCodeWriter headerWriter = new BufferedCodeWriter(false);
            classGenerator.generateType(writer, headerWriter, type);
            String name = this.fileNames.fileName(type);
            OutputFileUtil.write(writer, name + ".c", buildTarget);
            OutputFileUtil.write(headerWriter, name + ".h", buildTarget);
            if (this.incremental) {
               this.stringPool.reset();
            }
         }
      }
   }

   private boolean isSpecialClass(GenerationContext context, String name) {
      return context.getCharacteristics().isReflectionSpecial(name);
   }

   private void generateCoreGen(BuildTarget buildTarget, GenerationContext context) throws IOException {
      BufferedCodeWriter writer = new BufferedCodeWriter(false);
      SimpleIncludeManager includes = new SimpleIncludeManager(context.getFileNames(), writer);
      includes.init("core_gen.h");
      includes.includeClass("java.lang.Object");
      includes.includeClass("java.lang.Class");
      writer.print("#define TEAVM_CREATE_CLASS_OBJECT ")
         .println(context.getNames().forMethod(new MethodReference(Class.class, "createClass", ClassInfo.class, Class.class)));
      OutputFileUtil.write(writer, "core_gen.h", buildTarget);
   }

   private void generateCoreDefs(BuildTarget buildTarget, GenerationContext context) throws IOException {
      BufferedCodeWriter writer = new BufferedCodeWriter(false);
      SimpleIncludeManager includes = new SimpleIncludeManager(context.getFileNames(), writer);
      includes.init("core_defs.h");
      if (context.getMetadataRequirements().hasName()) {
         writer.println("#define TEAVM_CLASS_NAME_USED 1");
      }

      if (context.getMetadataRequirements().hasSimpleName()) {
         writer.println("#define TEAVM_CLASS_SIMPLE_NAME_USED 1");
      }

      if (context.getMetadataRequirements().hasDeclaringClass()) {
         writer.println("#define TEAVM_CLASS_DECLARING_CLASS_USED 1");
      }

      if (context.getMetadataRequirements().hasEnclosingClass()) {
         writer.println("#define TEAVM_CLASS_ENCLOSING_CLASS_USED 1");
      }

      if (context.getMetadataRequirements().hasClassInit()) {
         writer.println("#define TEAVM_CLASS_INIT_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(ClassInfo.class, "initializeNewInstance", Object.class, boolean.class)) != null) {
         writer.println("#define TEAVM_CLASS_INIT_NEW_INSTANCE_USED 1");
      }

      if (context.getMetadataRequirements().hasGetInterfaces()) {
         writer.println("#define TEAVM_CLASS_SUPERINTERFACES_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(ClassInfo.class, "reflection", ClassReflectionInfo.class)) != null) {
         writer.println("#define TEAVM_CLASS_REFLECTION_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(ClassReflectionInfo.class, "fieldCount", int.class)) != null) {
         writer.println("#define TEAVM_CLASS_REFLECTION_FIELDS_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(ClassReflectionInfo.class, "methodCount", int.class)) != null) {
         writer.println("#define TEAVM_CLASS_REFLECTION_METHODS_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(ClassReflectionInfo.class, "annotationCount", int.class)) != null) {
         writer.println("#define TEAVM_CLASS_REFLECTION_ANNOTATIONS_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(MethodInfo.class, "checkedExceptionCount", int.class)) != null) {
         writer.println("#define TEAVM_METHOD_EXCEPTION_TYPES_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(MethodReflectionInfo.class, "annotationCount", int.class)) != null) {
         writer.println("#define TEAVM_METHOD_ANNOTATIONS_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(MethodReflectionInfo.class, "parameterInfoCount", int.class)) != null) {
         writer.println("#define TEAVM_METHOD_PARAMETER_ANNOTATIONS_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(FieldReflectionInfo.class, "annotationCount", int.class)) != null) {
         writer.println("#define TEAVM_FIELD_ANNOTATIONS_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(FieldReflectionInfo.class, "genericType", GenericTypeInfo.class)) != null) {
         writer.println("#define TEAVM_FIELD_GENERIC_TYPE_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(MethodReflectionInfo.class, "genericReturnType", GenericTypeInfo.class)) != null
         || context.getDependencies().getMethod(new MethodReference(ParameterInfo.class, "genericType", GenericTypeInfo.class)) != null) {
         writer.println("#define TEAVM_METHOD_GENERIC_TYPES_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(MethodReflectionInfo.class, "typeParameterCount", int.class)) != null) {
         writer.println("#define TEAVM_METHOD_TYPE_PARAMS_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(ClassReflectionInfo.class, "typeParameterCount", int.class)) != null) {
         writer.println("#define TEAVM_CLASS_REFLECTION_TYPE_PARAMS_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(TypeVariableInfo.class, "boundCount", int.class)) != null) {
         writer.println("#define TEAVM_TYPE_VARIABLE_BOUNDS_USED 1");
      }

      if (context.getDependencies().getMethod(new MethodReference(ClassReflectionInfo.class, "innerClassCount", int.class)) != null) {
         writer.println("#define TEAVM_CLASS_REFLECTION_INNER_CLASSES_USED 1");
      }

      OutputFileUtil.write(writer, "core_defs.h", buildTarget);
   }

   private void generateArrayClassGen(BuildTarget buildTarget, GenerationContext context, int classCount) throws IOException {
      BufferedCodeWriter writer = new BufferedCodeWriter(false);
      SimpleIncludeManager includes = new SimpleIncludeManager(context.getFileNames(), writer);
      includes.init("arrayclass_gen.h");
      includes.includeClass("java.lang.Object");
      writer.print("#define TEAVM_OBJECT_CLASS ").println(context.getNames().forClassClass("java.lang.Object"));
      writer.print("#define TEAVM_OBJECT_CLASS_PTR ").println(context.getNames().forClassInstance(ValueType.object("java.lang.Object")));
      writer.println("#define TEAVM_DYNAMIC_CLASS_POOL_CAPACITY " + classCount * 8);
      OutputFileUtil.write(writer, "arrayclass_gen.h", buildTarget);
   }

   private void generateReflectionGen(BuildTarget buildTarget, GenerationContext context) throws IOException {
      BufferedCodeWriter writer = new BufferedCodeWriter(false);
      SimpleIncludeManager includes = new SimpleIncludeManager(context.getFileNames(), writer);
      includes.init("reflection_gen.h");

      for (CTarget.BoxedType boxedType : List.of(
         new CTarget.BoxedType(ValueType.BOOLEAN, "boolean", "Boolean"),
         new CTarget.BoxedType(ValueType.BYTE, "byte", "Byte"),
         new CTarget.BoxedType(ValueType.SHORT, "short", "Short"),
         new CTarget.BoxedType(ValueType.CHARACTER, "char", "Character"),
         new CTarget.BoxedType(ValueType.INTEGER, "int", "Integer"),
         new CTarget.BoxedType(ValueType.LONG, "long", "Long"),
         new CTarget.BoxedType(ValueType.FLOAT, "float", "Float"),
         new CTarget.BoxedType(ValueType.DOUBLE, "double", "Double")
      )) {
         String className = "java.lang." + boxedType.className;
         ClassReader cls = context.getClassSource().get(className);
         if (cls != null) {
            MethodReference boxMethodRef = new MethodReference(className, "valueOf", boxedType.type, ValueType.object(className));
            MethodReader boxMethod = cls.getMethod(boxMethodRef.getDescriptor());
            if (boxMethod != null && boxMethod.getProgram() != null) {
               includes.includeClass(className);
               writer.print("#define TEAVM_REFLECTION_BOX_")
                  .print(boxedType.primitiveName.toUpperCase())
                  .print(" ")
                  .println(context.getNames().forMethod(boxMethodRef));
            }

            MethodReference unboxMethodRef = new MethodReference(className, boxedType.primitiveName + "Value", boxedType.type);
            MethodReader unboxMethod = cls.getMethod(unboxMethodRef.getDescriptor());
            if (unboxMethod != null && unboxMethod.getProgram() != null) {
               includes.includeClass(className);
               writer.print("#define TEAVM_REFLECTION_UNBOX_")
                  .print(boxedType.primitiveName.toUpperCase())
                  .print(" ")
                  .println(context.getNames().forMethod(unboxMethodRef));
            }
         }
      }

      OutputFileUtil.write(writer, "reflection_gen.h", buildTarget);
   }

   private void generateCallSites(BuildTarget buildTarget, GenerationContext context, Collection<? extends String> classNames) throws IOException {
      BufferedCodeWriter writer = new BufferedCodeWriter(false);
      IncludeManager includes = new SimpleIncludeManager(context.getFileNames(), writer);
      includes.init("callsites.c");
      if (!this.incremental) {
         this.generateFastCallSites(context, writer, includes, classNames);
      }

      OutputFileUtil.write(writer, "callsites.c", buildTarget);
   }

   private void generateFastCallSites(GenerationContext context, CodeWriter writer, IncludeManager includes, Collection<? extends String> classNames) {
      List<CallSiteDescriptor> callSites = this.callSites;
      new CallSiteGenerator(context, writer, includes, "teavm_callSites").generate(callSites);
      if (this.obfuscated) {
         this.generateCallSitesJson(context.getBuildTarget(), callSites);
      }
   }

   private void generateCallSitesJson(BuildTarget buildTarget, List<? extends CallSiteDescriptor> callSites) {
      try (
         OutputStream output = buildTarget.createResource("callsites.json");
         Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
      ) {
         writer.append("[\n");
         boolean first = true;

         for (CallSiteDescriptor descriptor : callSites) {
            if (!first) {
               writer.append(",\n");
            }

            first = false;
            writer.append("{\"id\":").append(Integer.toString(descriptor.getId()));
            writer.append(",\"locations\":[");
            org.teavm.model.lowlevel.CallSiteLocation[] locations = descriptor.getLocations();
            if (locations != null) {
               boolean firstLocation = true;

               for (org.teavm.model.lowlevel.CallSiteLocation location : locations) {
                  if (!firstLocation) {
                     writer.append(",");
                  }

                  firstLocation = false;
                  writer.append("{\"class\":");
                  appendJsonString(writer, location.getClassName());
                  writer.append(",\"method\":");
                  appendJsonString(writer, location.getMethodName());
                  writer.append(",\"file\":");
                  appendJsonString(writer, location.getFileName());
                  writer.append(",\"line\":").append(Integer.toString(location.getLineNumber()));
                  writer.append("}");
               }
            }

            writer.append("]}");
         }

         if (!first) {
            writer.append("\n");
         }

         writer.append("]");
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   private static void appendJsonString(Writer writer, String string) throws IOException {
      if (string == null) {
         writer.append("null");
      } else {
         writer.append("\"");
         JsonUtil.writeEscapedString(writer, string);
         writer.append("\"");
      }
   }

   private void generateStrings(BuildTarget buildTarget, GenerationContext context) throws IOException {
      BufferedCodeWriter writer = new BufferedCodeWriter(false);
      IncludeManager includes = new SimpleIncludeManager(context.getFileNames(), writer);
      includes.init("strings.c");
      BufferedCodeWriter headerWriter = new BufferedCodeWriter(false);
      headerWriter.println("#pragma once");
      headerWriter.println("#include \"runtime.h\"");
      headerWriter.println("extern void teavm_initStringPool();");
      if (!this.incremental) {
         headerWriter.println("extern TeaVM_String* teavm_stringPool[];");
         headerWriter.println("#define TEAVM_GET_STRING(i) teavm_stringPool[i]");
         headerWriter.println("#define TEAVM_GET_STRING_ADDRESS(i) (teavm_stringPool + i)");
         includes.includePath("strings.h");
         includes.includePath("stringhash.h");
         StringPoolGenerator poolGenerator = new StringPoolGenerator(context, "teavm_stringPool");
         poolGenerator.generate(writer);
         writer.println("void teavm_initStringPool() {").indent();
         poolGenerator.generateStringPoolHeaders(writer, includes);
         writer.outdent().println("}");
      } else {
         writer.println("void teavm_initStringPool() {}");
      }

      OutputFileUtil.write(writer, "strings.c", buildTarget);
      OutputFileUtil.write(headerWriter, "strings.h", buildTarget);
   }

   private VirtualTableProvider createVirtualTableProvider(ListableClassHolderSource classes) {
      VirtualTableBuilder builder = new VirtualTableBuilder(classes);
      LinkedHashSet<MethodReference> methodsOnCallSites = new LinkedHashSet<>();
      methodsOnCallSites.addAll(VirtualTableBuilder.getMethodsUsedOnCallSites(classes, true));
      methodsOnCallSites.addAll(this.reflection.getVirtualCallSites());
      builder.setMethodsUsedAtCallSites(methodsOnCallSites);
      builder.setMethodCalledVirtually(this.controller::isVirtual);
      return builder.build();
   }

   private void generateSpecialFunctions(GenerationContext context, CodeWriter writer) {
      IncludeManager includes = new SimpleIncludeManager(context.getFileNames(), writer);
      includes.init("special.c");
      includes.includePath("core.h");
      includes.includePath("string.h");
      ClassGenerationContext classContext = new ClassGenerationContext(context, includes, writer.fragment(), null, null);
      this.generateThrowCCE(classContext, writer);
      this.generateAllocateStringArray(classContext, writer, includes);
      this.generateAllocateCharArray(classContext, writer, includes);
      this.generateCreateString(classContext, writer, includes);
   }

   private void generateThrowCCE(ClassGenerationContext classContext, CodeWriter writer) {
      MethodReference methodRef = new MethodReference(ExceptionHandling.class, "throwClassCastException", void.class);
      classContext.importMethod(methodRef, true);
      writer.println("void* teavm_throwClassCastException() {").indent();
      String methodName = classContext.getContext().getNames().forMethod(methodRef);
      writer.println(methodName + "();");
      writer.println("return NULL;");
      writer.outdent().println("}");
   }

   private void generateAllocateStringArray(ClassGenerationContext context, CodeWriter writer, IncludeManager includes) {
      NameProvider names = context.getContext().getNames();
      MethodReference allocMethod = new MethodReference(Allocator.class, "allocateArray", RuntimeClass.class, int.class, Address.class);
      context.importMethod(allocMethod, true);
      ValueType arrayType = ValueType.parse(String[].class);
      writer.println("TeaVM_Array* teavm_allocateStringArray(int32_t size) {").indent();
      String allocateArrayName = names.forMethod(allocMethod);
      String stringClassName = names.forClassInstance(arrayType);
      writer.print("return (TeaVM_Array*) " + allocateArrayName + "(");
      CodeGeneratorUtil.writeTypeReference(writer, context.getContext(), includes, arrayType);
      writer.println(", size);");
      writer.outdent().println("}");
   }

   private void generateAllocateCharArray(ClassGenerationContext context, CodeWriter writer, IncludeManager includes) {
      NameProvider names = context.getContext().getNames();
      MethodReference allocMethod = new MethodReference(Allocator.class, "allocateArray", RuntimeClass.class, int.class, Address.class);
      context.importMethod(allocMethod, true);
      includes.includeType(ValueType.parse(char[].class));
      writer.println("TeaVM_Array* teavm_allocateCharArray(int32_t size) {").indent();
      String allocateArrayName = names.forMethod(allocMethod);
      String charClassName = names.forClassInstance(ValueType.arrayOf(ValueType.CHARACTER));
      writer.println("return (TeaVM_Array*) " + allocateArrayName + "(&" + charClassName + ", size);");
      writer.outdent().println("}");
   }

   private void generateCreateString(ClassGenerationContext context, CodeWriter writer, IncludeManager includes) {
      NameProvider names = context.getContext().getNames();
      context.importMethod(CodeGenerationVisitor.ALLOC_METHOD, true);
      includes.includeClass(String.class.getName());
      writer.println("TeaVM_String* teavm_createString(TeaVM_Array* array) {").indent();
      writer.print("TeaVM_String* str = (TeaVM_String*) ")
         .print(names.forMethod(CodeGenerationVisitor.ALLOC_METHOD))
         .print("(&")
         .print(names.forClassInstance(ValueType.object("java.lang.String")))
         .println(");");
      writer.print("str->characters = array;");
      writer.println("return str;");
      writer.outdent().println("}");
   }

   private void generateProxyGen(BuildTarget buildTarget, GenerationContext context) throws IOException {
      BufferedCodeWriter writer = new BufferedCodeWriter(false);
      SimpleIncludeManager includes = new SimpleIncludeManager(context.getFileNames(), writer);
      includes.init("proxy.c");
      includes.includePath("proxy.h");
      includes.includePath("core.h");
      includes.includePath("reflection.h");
      includes.includePath("strings.h");
      NameProvider names = context.getNames();
      MethodReference allocArrayRef = new MethodReference(Allocator.class, "allocateArray", RuntimeClass.class, int.class, Address.class);
      MethodReference getDeclaredMethodRef = new MethodReference(Class.class, "getDeclaredMethod", String.class, Class[].class, Method.class);
      MethodReference appendInterfaceRef = new MethodReference(Proxy.class, "appendInterface", ClassInfo.class, void.class);
      MethodReference registerClassRef = new MethodReference(Proxy.class, "registerClass", ClassInfo.class, void.class);
      new ClassGenerationContext(context, includes, writer.fragment(), null, null);
      ValueType.Object classType = ValueType.object(Class.class.getName());
      int methodCount = this.proxyContext.getMethodCount();
      writer.println("static void* teavm_proxyMethods[" + Math.max(1, methodCount) + "];");
      writer.println();
      writer.println("void* teavm_getProxyMethod(int32_t index) {").indent();
      writer.println("return teavm_proxyMethods[index];");
      writer.outdent().println("}");
      writer.println();
      if (this.reflection.isProxyReached() && methodCount > 0) {
         includes.includeClass(Class.class.getName());
         includes.includeClass(Allocator.class.getName());
         writer.println("static void* teavm_proxy_getDeclaredMethod(TeaVM_Class* cls, void* name, int32_t count, TeaVM_ClassPtr* paramTypes) {").indent();
         writer.print("TeaVM_Array* params = (TeaVM_Array*) ")
            .print(names.forMethod(allocArrayRef))
            .print("(teavm_getArrayClass((TeaVM_Class*) &")
            .print(names.forClassInstance(classType))
            .println("), count);");
         writer.println("for (int32_t i = 0; i < count; i++) {").indent();
         writer.println("TEAVM_ARRAY_AT(params, void*, i) = teavm_getClassObject(teavm_reflection_extractType(&paramTypes[i]));");
         writer.outdent().println("}");
         writer.print("return ").print(names.forMethod(getDeclaredMethodRef)).println("(teavm_getClassObject(cls), name, params);");
         writer.outdent().println("}");
         writer.println();
      }

      writer.println("void teavm_proxy_registerClasses(void) {").indent();
      if (this.reflection.isProxyReached()) {
         includes.includeClass(Class.class.getName());
         includes.includeClass(Proxy.class.getName());

         for (Entry<MethodReference, Integer> entry : this.proxyContext.getMethodIndices().entrySet()) {
            MethodReference methodToAcquire = entry.getKey();
            int index = entry.getValue();
            int paramCount = methodToAcquire.parameterCount();
            ValueType.Object declaringType = ValueType.object(methodToAcquire.getClassName());
            includes.includeType(declaringType);
            context.addType(declaringType);
            int nameIndex = context.getStringPool().getStringIndex(methodToAcquire.getName());
            writer.print("teavm_proxyMethods[" + index + "] = teavm_proxy_getDeclaredMethod(")
               .print("(TeaVM_Class*) &")
               .print(names.forClassInstance(declaringType))
               .print(", TEAVM_GET_STRING(" + nameIndex + "), ")
               .print(paramCount + ", ");
            if (paramCount == 0) {
               writer.println("NULL);");
            } else {
               writer.print("(TeaVM_ClassPtr[]) {");

               for (int i = 0; i < paramCount; i++) {
                  if (i > 0) {
                     writer.print(", ");
                  }

                  this.writeClassPtr(writer, context, includes, methodToAcquire.parameterType(i));
               }

               writer.println("});");
            }
         }

         for (Entry<? extends List<? extends String>, ? extends String> entry : this.reflection.getProxyClasses().entrySet()) {
            List<? extends String> itfList = (List<? extends String>)entry.getKey();
            String cls = entry.getValue();

            for (String itf : itfList) {
               ValueType.Object itfType = ValueType.object(itf);
               includes.includeType(itfType);
               context.addType(itfType);
               writer.print(names.forMethod(appendInterfaceRef)).print("((TeaVM_Class*) &").print(names.forClassInstance(itfType)).println(");");
            }

            ValueType.Object clsType = ValueType.object(cls);
            includes.includeType(clsType);
            context.addType(clsType);
            writer.print(names.forMethod(registerClassRef)).print("((TeaVM_Class*) &").print(names.forClassInstance(clsType)).println(");");
         }
      }

      writer.outdent().println("}");
      OutputFileUtil.write(writer, "proxy.c", buildTarget);
   }

   private void writeClassPtr(CodeWriter writer, GenerationContext context, IncludeManager includes, ValueType type) {
      int arrayDegree = 0;

      while (type instanceof ValueType.Array) {
         arrayDegree++;
         type = ((ValueType.Array)type).getItemType();
      }

      includes.includeType(type);
      context.addType(type);
      writer.print("{(TeaVM_Class*) &").print(context.getNames().forClassInstance(type)).print(", " + arrayDegree + "}");
   }

   private void generateMainFile(
      GenerationContext context,
      ListableClassHolderSource classes,
      List<? extends ValueType> types,
      Collection<? extends ValueType> typeLiterals,
      BuildTarget buildTarget
   ) throws IOException {
      BufferedCodeWriter writer = new BufferedCodeWriter(false);
      IncludeManager includes = new SimpleIncludeManager(this.fileNames, writer);
      includes.init("main.c");
      includes.includePath("runtime.h");
      includes.includePath("strings.h");
      this.generateArrayOfClassReferences(context, writer, includes, types);
      this.generateMain(context, writer, includes, classes, types, typeLiterals);
      OutputFileUtil.write(writer, "main.c", buildTarget);
   }

   private void generateAllFile(GenerationContext context, ListableClassHolderSource classes, List<? extends ValueType> types, BuildTarget buildTarget) throws IOException {
      List<String> allFiles = this.getGeneratedFiles(context, classes, types);
      BufferedCodeWriter writer = new BufferedCodeWriter(false);
      writer.println("#define _XOPEN_SOURCE");
      writer.println("#define __USE_XOPEN");
      writer.println("#define _GNU_SOURCE");
      IncludeManager includes = new SimpleIncludeManager(this.fileNames, writer);
      includes.init("all.c");

      for (String file : allFiles) {
         includes.includePath(file);
      }

      OutputFileUtil.write(writer, "all.c", buildTarget);
      writer = new BufferedCodeWriter(false);

      for (String file : allFiles) {
         writer.println(file);
      }

      OutputFileUtil.write(writer, "all.txt", buildTarget);
   }

   private List<String> getGeneratedFiles(GenerationContext context, ListableClassHolderSource classes, List<? extends ValueType> types) {
      List<String> files = new ArrayList<>();
      files.add("callsites.c");
      files.add("core.c");
      files.add("date.c");
      files.add("fiber.c");
      files.add("file.c");
      files.add("heapdump.c");
      files.add("heaptrace.c");
      files.add("log.c");
      files.add("memory.c");
      files.add("references.c");
      files.add("resource.c");
      files.add("proxy.c");
      files.add("special.c");
      files.add("stack.c");
      files.add("string.c");
      files.add("stringhash.c");
      files.add("strings.c");
      files.add("time.c");
      files.add("virtcall.c");
      files.add("arrayclass.c");
      files.add("reflection.c");
      files.add("reflection_signatures.c");

      for (String className : classes.getClassNames()) {
         if (!this.isSpecialClass(context, className)) {
            files.add(this.fileNames.fileName(className) + ".c");
         }
      }

      for (ValueType type : types) {
         if (!(type instanceof ValueType.Object)) {
            files.add(this.fileNames.fileName(type) + ".c");
         }
      }

      files.add("main.c");
      files.sort(String::compareTo);
      return files;
   }

   private void generateArrayOfClassReferences(GenerationContext context, CodeWriter writer, IncludeManager includes, List<? extends ValueType> types) {
      List<? extends ValueType> filteredTypes = types.stream()
         .filter(typex -> !(typex instanceof ValueType.Object) || !this.isSpecialClass(context, ((ValueType.Object)typex).getClassName()))
         .collect(Collectors.toList());
      writer.print("TeaVM_Class* teavm_classReferences[" + filteredTypes.size() + "] = {").indent();
      boolean first = true;

      for (ValueType type : filteredTypes) {
         if (!first) {
            writer.print(", ");
         }

         writer.println();
         first = false;
         String typeName = context.getNames().forClassInstance(type);
         includes.includeType(type);
         writer.print("(TeaVM_Class*) &" + typeName);
      }

      if (!first) {
         writer.println();
      }

      writer.outdent().println("};");
      writer.println("int32_t teavm_classReferencesCount = " + filteredTypes.size() + ";");
   }

   private void generateMain(
      GenerationContext context,
      CodeWriter writer,
      IncludeManager includes,
      ListableClassHolderSource classes,
      List<? extends ValueType> types,
      Collection<? extends ValueType> typeLiterals
   ) {
      String mainFunctionName = this.controller.getEntryPointName();
      if (mainFunctionName == null) {
         mainFunctionName = "main";
      }

      ClassGenerationContext classContext = new ClassGenerationContext(context, includes, writer.fragment(), null, null);
      writer.println("int " + mainFunctionName + "(int argc, char** argv) {").indent();
      writer.println("teavm_beforeInit();");
      writer.println("teavm_initHeap(" + this.minHeapSize + ", " + this.maxHeapSize + ");");
      this.generateVirtualTableHeaders(context, writer);
      writer.println("teavm_initStringPool();");

      for (ValueType type : types) {
         if (!(type instanceof ValueType.Object) || !this.isSpecialClass(context, ((ValueType.Object)type).getClassName())) {
            includes.includeType(type);
            writer.println(context.getNames().forClassSystemInitializer(type) + "();");
         }
      }

      writer.println("teavm_afterInitClasses();");
      this.generateStaticInitializerCalls(classContext, writer, classes);

      for (ValueType type : typeLiterals) {
         if (!(type instanceof ValueType.Object) || !this.isSpecialClass(context, ((ValueType.Object)type).getClassName())) {
            writer.print("teavm_getClassObject((TeaVM_Class*) ");
            CodeGeneratorUtil.writeTypeReference(writer, context, includes, type);
            writer.println(");");
         }
      }

      if (context.getClassInitializerInfo().isDynamicInitializer("java.lang.String")) {
         writer.println(context.getNames().forClassInitializer("java.lang.String") + "();");
      }

      this.generateFiberStart(classContext, writer);
      writer.println("return 0;");
      writer.outdent().println("}");
   }

   private void generateStaticInitializerCalls(ClassGenerationContext context, CodeWriter writer, ListableClassReaderSource classes) {
      NameProvider names = context.getContext().getNames();
      Characteristics characteristics = context.getContext().getCharacteristics();
      MethodDescriptor clinitDescriptor = new MethodDescriptor("<clinit>", ValueType.VOID);
      if (classes.getClassNames().contains(GC.class.getName())) {
         MethodReference methodRef = new MethodReference(GC.class.getName(), clinitDescriptor);
         context.importMethod(methodRef, true);
         writer.println(names.forMethod(methodRef) + "();");
      }

      for (String className : classes.getClassNames()) {
         if (!className.equals(GC.class.getName())) {
            ClassReader cls = classes.get(className);
            if ((
                  characteristics.isStaticInit(cls.getName())
                     || characteristics.isStructure(cls.getName())
                     || characteristics.isReflectionSpecial(cls.getName())
               )
               && cls.getMethod(clinitDescriptor) != null) {
               MethodReference methodRef = new MethodReference(className, clinitDescriptor);
               context.importMethod(methodRef, true);
               writer.println(names.forMethod(methodRef) + "();");
            }
         }
      }
   }

   private void generateVirtualTableHeaders(GenerationContext context, CodeWriter writer) {
      writer.println("teavm_classClass = (TeaVM_Class*) &" + context.getNames().forClassInstance(ValueType.object("java.lang.Class")) + ";");
      writer.println("teavm_objectClass = (TeaVM_Class*) &" + context.getNames().forClassInstance(ValueType.object("java.lang.Object")) + ";");
      writer.println("teavm_stringClass = (TeaVM_Class*) &" + context.getNames().forClassInstance(ValueType.object("java.lang.String")) + ";");
      writer.println("teavm_charArrayClass = (TeaVM_Class*) &" + context.getNames().forClassInstance(ValueType.arrayOf(ValueType.CHARACTER)) + ";");
      writer.println("teavm_initClasses();");
   }

   private void generateFiberStart(ClassGenerationContext context, CodeWriter writer) {
      NameProvider names = context.getContext().getNames();
      MethodReference startRef = new MethodReference(Fiber.class, "startMain", String[].class, void.class);
      MethodReference processRef = new MethodReference(EventQueue.class, "process", void.class);
      context.importMethod(startRef, true);
      context.importMethod(processRef, true);
      writer.println(names.forMethod(startRef) + "(teavm_parseArguments(argc, argv));");
      writer.println(names.forMethod(processRef) + "();");
   }

   private void generateCallToMainMethod(IntrinsicContext context, InvocationExpr invocation) {
      NameProvider names = context.names();
      MethodReference method = new MethodReference(this.controller.getEntryPoint(), "main", ValueType.parse(String[].class), ValueType.parse(void.class));
      context.importMethod(method, true);
      String mainMethod = names.forMethod(method);
      context.writer().print(mainMethod + "(");
      context.emit(invocation.getArguments().get(0));
      context.writer().print(")");
   }

   @Override
   public String[] getPlatformTags() {
      return new String[]{"c", "low_level", "own_gc"};
   }

   @Override
   public boolean isAsyncSupported() {
      return true;
   }

   @Override
   public InliningFilterFactory getInliningFilter() {
      return new LowLevelInliningFilterFactory(this.characteristics);
   }

   private static class BoxedType {
      ValueType type;
      String primitiveName;
      String className;

      BoxedType(ValueType type, String primitiveName, String className) {
         this.type = type;
         this.primitiveName = primitiveName;
         this.className = className;
      }
   }

   class FiberIntrinsic implements Intrinsic {
      @Override
      public boolean canHandle(MethodReference method) {
         if (!method.getClassName().equals(Fiber.class.getName())) {
            return false;
         }

         switch (method.getName()) {
            case "runMain":
            case "setCurrentThread":
               return true;
            default:
               return false;
         }
      }

      @Override
      public void apply(IntrinsicContext context, InvocationExpr invocation) {
         switch (invocation.getMethod().getName()) {
            case "runMain":
               CTarget.this.generateCallToMainMethod(context, invocation);
               break;
            case "setCurrentThread":
               MethodReference methodRef = new MethodReference(Thread.class, "setCurrentThread", Thread.class, void.class);
               context.importMethod(methodRef, true);
               context.writer().print(context.names().forMethod(methodRef)).print("(");
               context.emit(invocation.getArguments().get(0));
               context.writer().print(")");
         }
      }
   }
}
