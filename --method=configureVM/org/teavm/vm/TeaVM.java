package org.teavm.vm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.teavm.cache.AlwaysStaleCacheStatus;
import org.teavm.cache.AnnotationAwareCacheStatus;
import org.teavm.cache.CacheStatus;
import org.teavm.cache.EmptyProgramCache;
import org.teavm.cache.ProgramDependencyExtractor;
import org.teavm.common.ServiceRepository;
import org.teavm.dependency.BootstrapMethodSubstitutor;
import org.teavm.dependency.ClassSourcePacker;
import org.teavm.dependency.DependencyAnalyzer;
import org.teavm.dependency.DependencyInfo;
import org.teavm.dependency.DependencyListener;
import org.teavm.dependency.DependencyPlugin;
import org.teavm.dependency.Linker;
import org.teavm.dependency.MethodDependency;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.diagnostics.AccumulationDiagnostics;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.diagnostics.ProblemProvider;
import org.teavm.extension.ExtensionEnvironment;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderSource;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReference;
import org.teavm.model.Instruction;
import org.teavm.model.ListableClassHolderSource;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.MutableClassHolderSource;
import org.teavm.model.Program;
import org.teavm.model.ProgramCache;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ClassInitializerAnalysis;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.optimization.ArrayUnwrapMotion;
import org.teavm.model.optimization.ClassInitElimination;
import org.teavm.model.optimization.ConstantConditionElimination;
import org.teavm.model.optimization.DefaultInliningStrategy;
import org.teavm.model.optimization.Devirtualization;
import org.teavm.model.optimization.GlobalValueNumbering;
import org.teavm.model.optimization.Inlining;
import org.teavm.model.optimization.InliningStrategy;
import org.teavm.model.optimization.LoopInvariantMotion;
import org.teavm.model.optimization.MethodOptimization;
import org.teavm.model.optimization.MethodOptimizationContext;
import org.teavm.model.optimization.RedundantJumpElimination;
import org.teavm.model.optimization.RedundantNullCheckElimination;
import org.teavm.model.optimization.RedundantPhiElimination;
import org.teavm.model.optimization.RepeatedFieldReadElimination;
import org.teavm.model.optimization.ScalarReplacement;
import org.teavm.model.optimization.SystemArrayCopyOptimization;
import org.teavm.model.optimization.UnreachableBasicBlockElimination;
import org.teavm.model.optimization.UnusedVariableElimination;
import org.teavm.model.text.ListingBuilder;
import org.teavm.model.transformation.ClassInitializerInsertionTransformer;
import org.teavm.model.util.ModelUtils;
import org.teavm.model.util.ProgramUtils;
import org.teavm.model.util.RegisterAllocator;
import org.teavm.model.util.VariableCategoryProvider;
import org.teavm.parsing.resource.ResourceProvider;
import org.teavm.vm.spi.ClassFilter;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMHostExtension;
import org.teavm.vm.spi.TeaVMPlugin;

public class TeaVM implements TeaVMHost, ServiceRepository {
   public static final MethodDescriptor MAIN_METHOD_DESC = new MethodDescriptor("main", ValueType.arrayOf(ValueType.object("java.lang.String")), ValueType.VOID);
   public static final MethodDescriptor SHORT_MAIN_METHOD_DESC = new MethodDescriptor("main", ValueType.VOID);
   private static final MethodDescriptor CLINIT_DESC = new MethodDescriptor("<clinit>", ValueType.VOID);
   private final DependencyAnalyzer dependencyAnalyzer;
   private final AccumulationDiagnostics diagnostics = new AccumulationDiagnostics();
   private final ClassLoader classLoader;
   private final ResourceProvider resourceProvider;
   private String entryPoint;
   private String entryPointName = "main";
   private final Set<String> preservedClasses = new HashSet<>();
   private final Set<String> readonlyPreservedClasses = Collections.unmodifiableSet(this.preservedClasses);
   private final Map<Class<?>, Object> services = new HashMap<>();
   private final Properties properties = new Properties();
   private ProgramCache programCache = EmptyProgramCache.INSTANCE;
   private CacheStatus rawCacheStatus = AlwaysStaleCacheStatus.INSTANCE;
   private TeaVMOptimizationLevel optimizationLevel = TeaVMOptimizationLevel.SIMPLE;
   private TeaVMProgressListener progressListener;
   private boolean cancelled;
   private ListableClassHolderSource writtenClasses;
   private TeaVMTarget target;
   private Map<Class<?>, TeaVMHostExtension> extensions = new HashMap<>();
   private Set<? extends MethodReference> virtualMethods;
   private AnnotationAwareCacheStatus cacheStatus;
   private ProgramDependencyExtractor programDependencyExtractor = new ProgramDependencyExtractor();
   private List<Predicate<MethodReference>> additionalVirtualMethods = new ArrayList<>();
   private int lastKnownClasses;
   private int compileProgressReportStart;
   private int compileProgressReportLimit;
   private int compileProgressLimit;
   private int compileProgressValue;
   private ClassSourcePacker classSourcePacker;
   private ClassInitializerInfo classInitializerInfo;
   private TeaVMTargetController targetController = new TeaVMTargetController() {
      @Override
      public boolean wasCancelled() {
         return TeaVM.this.wasCancelled();
      }

      @Override
      public ClassLoader getClassLoader() {
         return TeaVM.this.classLoader;
      }

      @Override
      public ClassReaderSource getUnprocessedClassSource() {
         return TeaVM.this.dependencyAnalyzer.getClassSource();
      }

      @Override
      public ResourceProvider getResourceProvider() {
         return TeaVM.this.resourceProvider;
      }

      @Override
      public CacheStatus getCacheStatus() {
         return TeaVM.this.cacheStatus;
      }

      @Override
      public DependencyInfo getDependencyInfo() {
         return TeaVM.this.dependencyAnalyzer;
      }

      @Override
      public Diagnostics getDiagnostics() {
         return TeaVM.this.diagnostics;
      }

      @Override
      public Properties getProperties() {
         return TeaVM.this.properties;
      }

      @Override
      public ServiceRepository getServices() {
         return TeaVM.this;
      }

      @Override
      public String getEntryPoint() {
         return TeaVM.this.entryPoint;
      }

      @Override
      public String getEntryPointName() {
         return TeaVM.this.entryPointName;
      }

      @Override
      public Set<String> getPreservedClasses() {
         return TeaVM.this.readonlyPreservedClasses;
      }

      @Override
      public boolean isFriendlyToDebugger() {
         return TeaVM.this.optimizationLevel == TeaVMOptimizationLevel.SIMPLE;
      }

      @Override
      public boolean isVirtual(MethodReference method) {
         return TeaVM.this.isVirtual(method);
      }

      @Override
      public TeaVMProgressFeedback reportProgress(int progress) {
         progress = progress * (TeaVM.this.compileProgressReportLimit - TeaVM.this.compileProgressReportStart) / 1000 + TeaVM.this.compileProgressReportStart;
         return TeaVM.this.progressListener.progressReached(progress);
      }

      @Override
      public void addVirtualMethods(Predicate<MethodReference> methods) {
         TeaVM.this.addVirtualMethods(methods);
      }

      @Override
      public ClassInitializerInfo getClassInitializerInfo() {
         return TeaVM.this.classInitializerInfo;
      }

      @Override
      public TeaVMOptimizationLevel getOptimizationLevel() {
         return TeaVM.this.optimizationLevel;
      }

      @Override
      public ExtensionEnvironment extensionEnvironment() {
         return TeaVM.this.dependencyAnalyzer.extensionEnvironment();
      }
   };

   TeaVM(TeaVMBuilder builder) {
      this.target = builder.target;
      this.classLoader = builder.classLoader;
      this.resourceProvider = builder.resourceProvider;
      this.classSourcePacker = builder.classSourcePacker;
      this.dependencyAnalyzer = builder.dependencyAnalyzerFactory
         .create(
            builder.classSource,
            this.resourceProvider,
            this.classLoader,
            this,
            this.diagnostics,
            builder.referenceCache,
            this.target.getPlatformTags(),
            this::getProperties
         );
      this.services.put(ExtensionEnvironment.class, this.dependencyAnalyzer.extensionEnvironment());
      this.dependencyAnalyzer.setObfuscated(builder.obfuscated);
      this.dependencyAnalyzer.setStrict(builder.strict);
      this.progressListener = new TeaVMProgressListener() {
         @Override
         public TeaVMProgressFeedback progressReached(int progress) {
            return TeaVMProgressFeedback.CONTINUE;
         }

         @Override
         public TeaVMProgressFeedback phaseStarted(TeaVMPhase phase, int count) {
            return TeaVMProgressFeedback.CONTINUE;
         }
      };

      for (ClassHolderTransformer transformer : this.target.getTransformers()) {
         this.dependencyAnalyzer.addClassTransformer(transformer);
      }

      for (DependencyListener listener : this.target.getDependencyListeners()) {
         this.dependencyAnalyzer.addDependencyListener(listener);
      }

      for (TeaVMHostExtension extension : this.target.getHostExtensions()) {
         for (Class<?> extensionType : this.getExtensionTypes(extension)) {
            this.extensions.put(extensionType, extension);
         }
      }
   }

   @Override
   public void addVirtualMethods(Predicate<MethodReference> virtualMethods) {
      this.additionalVirtualMethods.add(virtualMethods);
   }

   @Override
   public void addClassFilter(ClassFilter filter) {
      this.dependencyAnalyzer.addClassFilter(filter);
   }

   @Override
   public void add(DependencyListener listener) {
      this.dependencyAnalyzer.addDependencyListener(listener);
   }

   @Override
   public void add(ClassHolderTransformer transformer) {
      this.dependencyAnalyzer.addClassTransformer(transformer);
   }

   @Override
   public void add(MethodReference methodRef, BootstrapMethodSubstitutor substitutor) {
      this.dependencyAnalyzer.addBootstrapMethodSubstitutor(methodRef, substitutor);
   }

   @Override
   public void add(MethodReference methodRef, DependencyPlugin dependencyPlugin) {
      this.dependencyAnalyzer.addDependencyPlugin(methodRef, dependencyPlugin);
   }

   @Override
   public ClassLoader getClassLoader() {
      return this.classLoader;
   }

   @Override
   public ResourceProvider getResourceProvider() {
      return this.resourceProvider;
   }

   public void setProperties(Properties properties) {
      this.properties.clear();
      if (properties != null) {
         this.properties.putAll(properties);
      }
   }

   @Override
   public Properties getProperties() {
      return new Properties(this.properties);
   }

   public ProgramCache getProgramCache() {
      return this.programCache;
   }

   public void setProgramCache(ProgramCache programCache) {
      this.programCache = programCache;
   }

   public void setCacheStatus(CacheStatus cacheStatus) {
      this.rawCacheStatus = cacheStatus;
   }

   public TeaVMOptimizationLevel getOptimizationLevel() {
      return this.optimizationLevel;
   }

   public void setOptimizationLevel(TeaVMOptimizationLevel optimizationLevel) {
      this.optimizationLevel = optimizationLevel;
   }

   public TeaVMProgressListener getProgressListener() {
      return this.progressListener;
   }

   public void setProgressListener(TeaVMProgressListener progressListener) {
      this.progressListener = progressListener;
   }

   public boolean wasCancelled() {
      return this.cancelled;
   }

   public ProblemProvider getProblemProvider() {
      return this.diagnostics;
   }

   @Override
   public String[] getPlatformTags() {
      return this.target.getPlatformTags();
   }

   public void setEntryPoint(String entryPoint) {
      this.entryPoint = entryPoint;
   }

   public void setEntryPointName(String entryPointName) {
      this.entryPointName = entryPointName;
   }

   private void processEntryPoint() {
      this.dependencyAnalyzer.setEntryPoint(this.entryPoint);
      this.dependencyAnalyzer.addClassTransformer((c, context) -> {
         if (c.getName().equals(this.entryPoint)) {
            MethodHolder clinit = c.getMethod(CLINIT_DESC);
            if (clinit == null) {
               clinit = new MethodHolder(CLINIT_DESC);
               clinit.getModifiers().add(ElementModifier.STATIC);
               Program clinitProg = new Program();
               clinitProg.createVariable();
               BasicBlock block = clinitProg.createBasicBlock();
               block.add(new ExitInstruction());
               clinit.setProgram(clinitProg);
               c.addMethod(clinit);
            }
         }
      });
      ClassReader cls = this.dependencyAnalyzer.getClassSource().get(this.entryPoint);
      if (cls == null) {
         this.diagnostics.error(null, "There's no main class: '{{c0}}'", this.entryPoint);
      } else {
         this.dependencyAnalyzer.defer(() -> {
            MethodReader mainMethodReader = cls.getMethod(MAIN_METHOD_DESC);
            this.dependencyAnalyzer.linkClass(this.entryPoint).initClass(null);
            if (mainMethodReader != null) {
               MethodDependency mainMethod = this.dependencyAnalyzer.linkMethod(new MethodReference(this.entryPoint, MAIN_METHOD_DESC));
               mainMethod.getVariable(1).propagate(this.dependencyAnalyzer.getType(ValueType.arrayOf(ValueType.object("java.lang.String"))));
               mainMethod.getVariable(1).getArrayItem().propagate(this.dependencyAnalyzer.getClassType("java.lang.String"));
               if (!mainMethodReader.hasModifier(ElementModifier.STATIC)) {
                  mainMethod.getVariable(0).propagate(this.dependencyAnalyzer.getClassType(this.entryPoint));
                  this.initMainClassConstructor();
               }

               mainMethod.use();
            } else if (cls.getMethod(SHORT_MAIN_METHOD_DESC) != null) {
               MethodDependency mainMethod = this.dependencyAnalyzer.linkMethod(new MethodReference(this.entryPoint, SHORT_MAIN_METHOD_DESC));
               this.initMainClassConstructor();
               mainMethod.use();
            }
         });
      }
   }

   private void initMainClassConstructor() {
      MethodDependency ctor = this.dependencyAnalyzer.linkMethod(this.entryPoint, new MethodDescriptor("<init>", ValueType.VOID));
      ctor.propagate(0, this.dependencyAnalyzer.getClassType(this.entryPoint));
      ctor.use();
   }

   public void preserveType(String className) {
      this.dependencyAnalyzer.defer(() -> this.dependencyAnalyzer.linkClass(className).initClass(null));
      this.preservedClasses.add(className);
   }

   public ClassReaderSource getDependencyClassSource() {
      return this.dependencyAnalyzer.getClassSource();
   }

   public Collection<String> getClasses() {
      return this.dependencyAnalyzer.getReachableClasses();
   }

   public Collection<MethodReference> getMethods() {
      return this.dependencyAnalyzer.getReachableMethods();
   }

   public DependencyInfo getDependencyInfo() {
      return this.dependencyAnalyzer;
   }

   public ListableClassReaderSource getWrittenClasses() {
      return this.writtenClasses;
   }

   public void setLastKnownClasses(int lastKnownClasses) {
      this.lastKnownClasses = lastKnownClasses;
   }

   public void build(BuildTarget buildTarget, String outputName) {
      this.reportPhase(TeaVMPhase.DEPENDENCY_ANALYSIS, this.lastKnownClasses);
      if (!this.wasCancelled()) {
         this.target.setEntryPoint(this.entryPoint, this.entryPointName);
         this.dependencyAnalyzer.setAsyncSupported(this.target.isAsyncSupported());
         this.dependencyAnalyzer.setInterruptor(() -> {
            int progress = this.dependencyAnalyzer.getReachableClasses().size();
            this.cancelled = this.cancelled | this.progressListener.progressReached(progress) != TeaVMProgressFeedback.CONTINUE;
            return !this.cancelled;
         });
         this.target.contributeDependencies(this.dependencyAnalyzer);
         this.dependencyAnalyzer.initDependencies();
         this.processEntryPoint();
         if (this.target.needsSystemArrayCopyOptimization()) {
            this.dependencyAnalyzer.addDependencyListener(new StdlibDependencyListener());
         }

         this.dependencyAnalyzer.processDependencies();
         if (!this.wasCancelled() && this.diagnostics.getSevereProblems().isEmpty()) {
            this.dependencyAnalyzer.setInterruptor(null);
            this.dependencyAnalyzer.cleanup(this.classSourcePacker);
            this.cacheStatus = new AnnotationAwareCacheStatus(
               this.rawCacheStatus, this.dependencyAnalyzer.getIncrementalDependencies(), this.dependencyAnalyzer.getClassSource()
            );
            this.cacheStatus.addSynthesizedClasses(this.dependencyAnalyzer::isSynthesizedClass);
            if (!this.wasCancelled()) {
               boolean isLazy = this.optimizationLevel == TeaVMOptimizationLevel.SIMPLE;
               ListableClassHolderSource classSet;
               if (isLazy) {
                  this.classInitializerInfo = ClassInitializerInfo.EMPTY;
                  this.target.setController(this.targetController);
                  this.target
                     .analyzeBeforeOptimizations(
                        new TeaVM.ListableClassReaderSourceAdapter(
                           this.dependencyAnalyzer.getClassSource(), new LinkedHashSet<>(this.dependencyAnalyzer.getReachableClasses())
                        )
                     );
                  this.initCompileProgress(1000);
                  classSet = this.lazyPipeline();
               } else {
                  this.initCompileProgress(500);
                  classSet = this.eagerPipeline();
                  if (this.wasCancelled()) {
                     return;
                  }
               }

               if (!this.wasCancelled()) {
                  try {
                     if (!isLazy) {
                        this.compileProgressReportStart = 0;
                        this.compileProgressReportLimit = 1000;
                     }

                     this.target.emit(classSet, buildTarget, outputName);
                  } catch (IOException e) {
                     throw new RuntimeException("Error generating output files", e);
                  }
               }
            }
         }
      }
   }

   private void initCompileProgress(int limit) {
      this.reportPhase(TeaVMPhase.COMPILING, 1000);
      this.compileProgressReportStart = 0;
      this.compileProgressReportLimit = limit;
   }

   private ListableClassHolderSource eagerPipeline() {
      this.compileProgressValue = 0;
      this.compileProgressLimit = this.dependencyAnalyzer.getReachableClasses().size();
      if (this.optimizationLevel == TeaVMOptimizationLevel.ADVANCED) {
         this.compileProgressLimit *= 4;
      } else {
         this.compileProgressLimit *= 2;
      }

      ListableClassHolderSource classSet = this.link(this.dependencyAnalyzer);
      this.writtenClasses = classSet;
      if (this.wasCancelled()) {
         return null;
      }

      if (this.optimizationLevel != TeaVMOptimizationLevel.SIMPLE) {
         this.devirtualize(classSet);
         if (this.wasCancelled()) {
            return null;
         }

         ClassInitializerAnalysis classInitializerAnalysis = new ClassInitializerAnalysis(
            classSet, this.dependencyAnalyzer.getClassHierarchy(), this.entryPoint
         );
         classInitializerAnalysis.analyze(this.dependencyAnalyzer);
         this.classInitializerInfo = classInitializerAnalysis;
         this.insertClassInit(classSet);
         this.eliminateClassInit(classSet);
      } else {
         this.insertClassInit(classSet);
         this.classInitializerInfo = ClassInitializerInfo.EMPTY;
      }

      this.dependencyAnalyzer.cleanupTypes();
      this.target.setController(this.targetController);

      for (String className : classSet.getClassNames()) {
         ClassHolder cls = classSet.get(className);

         for (MethodHolder method : cls.getMethods()) {
            if (method.getProgram() != null) {
               this.target.beforeInlining(method.getProgram(), method);
            }
         }
      }

      this.inline(classSet);
      if (this.wasCancelled()) {
         return null;
      }

      this.target
         .analyzeBeforeOptimizations(
            new TeaVM.ListableClassReaderSourceAdapter(
               this.dependencyAnalyzer.getClassSource(), new LinkedHashSet<>(this.dependencyAnalyzer.getReachableClasses())
            )
         );
      this.optimize(classSet);
      return this.wasCancelled() ? null : classSet;
   }

   private ListableClassHolderSource lazyPipeline() {
      TeaVM.PostProcessingClassHolderSource result = new TeaVM.PostProcessingClassHolderSource();
      this.writtenClasses = result;
      return result;
   }

   private void insertClassInit(ListableClassHolderSource classes) {
      ClassInitializerInsertionTransformer clinitInsertion = new ClassInitializerInsertionTransformer(
         this.dependencyAnalyzer.getClassSource(), this.classInitializerInfo
      );

      for (String className : classes.getClassNames()) {
         ClassHolder cls = classes.get(className);

         for (MethodHolder method : cls.getMethods()) {
            Program program = method.getProgram();
            if (program != null) {
               clinitInsertion.apply(method, program);
            }
         }
      }
   }

   private void eliminateClassInit(ListableClassHolderSource classes) {
      for (String className : classes.getClassNames()) {
         ClassHolder cls = classes.get(className);

         for (MethodHolder method : cls.getMethods()) {
            Program program = method.getProgram();
            if (program != null) {
               for (BasicBlock block : program.getBasicBlocks()) {
                  for (Instruction instruction : block) {
                     if (instruction instanceof InitClassInstruction clinit && !this.classInitializerInfo.isDynamicInitializer(clinit.getClassName())) {
                        clinit.delete();
                     }
                  }
               }
            }
         }
      }

      Collection<? extends MethodReference> initializers = this.target.getInitializerMethods();
      if (initializers == null) {
         initializers = List.of(new MethodReference(this.entryPoint, CLINIT_DESC));
      }

      for (MethodReference initializer : initializers) {
         this.addInitializersToEntryPoint(classes, initializer);
      }
   }

   private void addInitializersToEntryPoint(ClassHolderSource classes, MethodReference methodRef) {
      ClassHolder cls = classes.get(methodRef.getClassName());
      if (cls != null) {
         MethodHolder method = cls.getMethod(methodRef.getDescriptor());
         if (method != null) {
            Program program = method.getProgram();
            BasicBlock block = program.basicBlockAt(0);
            Instruction first = block.getFirstInstruction();

            for (String className : this.classInitializerInfo.getInitializationOrder()) {
               if (this.target.filterClassInitializer(className)) {
                  InvokeInstruction invoke = new InvokeInstruction();
                  invoke.setMethod(new MethodReference(className, CLINIT_DESC));
                  first.insertPrevious(invoke);
               }
            }
         }
      }
   }

   public ListableClassHolderSource link(DependencyAnalyzer dependency) {
      Linker linker = new Linker(dependency);
      MutableClassHolderSource cutClasses = new MutableClassHolderSource();
      if (this.wasCancelled()) {
         return cutClasses;
      }

      for (String className : dependency.getReachableClasses()) {
         ClassReader clsReader = dependency.getClassSource().get(className);
         if (clsReader != null) {
            ClassHolder cls = ModelUtils.copyClass(clsReader);
            cutClasses.putClassHolder(cls);
            linker.link(cls);
         }

         this.reportCompileProgress(++this.compileProgressValue);
         if (this.wasCancelled()) {
            break;
         }
      }

      return cutClasses;
   }

   private void reportPhase(TeaVMPhase phase, int progressLimit) {
      if (this.progressListener.phaseStarted(phase, progressLimit) == TeaVMProgressFeedback.CANCEL) {
         this.cancelled = true;
      }
   }

   private void reportProgress(int progress) {
      if (this.progressListener.progressReached(progress) == TeaVMProgressFeedback.CANCEL) {
         this.cancelled = true;
      }
   }

   private void reportCompileProgress(int progress) {
      this.reportProgress(
         this.compileProgressReportStart + progress * (this.compileProgressReportLimit - this.compileProgressReportStart) / this.compileProgressLimit
      );
   }

   private void devirtualize(ListableClassHolderSource classes) {
      if (!this.wasCancelled()) {
         boolean shouldLog = System.getProperty("org.teavm.logDevirtualization", "false").equals("true");
         if (shouldLog) {
            System.out.println("Running devirtualization");
         }

         Devirtualization devirtualization = new Devirtualization(this.dependencyAnalyzer, this.dependencyAnalyzer.getClassHierarchy());

         for (String className : classes.getClassNames()) {
            ClassHolder cls = classes.get(className);

            for (MethodHolder method : cls.getMethods()) {
               if (method.getProgram() != null) {
                  devirtualization.apply(method);
               }
            }

            this.reportCompileProgress(++this.compileProgressValue);
            if (this.wasCancelled()) {
               break;
            }
         }

         this.virtualMethods = devirtualization.getVirtualMethods();
         if (shouldLog) {
            System.out.println("Devirtualization complete");
            System.out.println("Virtual calls: " + devirtualization.getVirtualCallSites());
            System.out.println("Direct calls: " + devirtualization.getDirectCallSites());
         }
      }
   }

   private void inline(ListableClassHolderSource classes) {
      if (this.optimizationLevel != TeaVMOptimizationLevel.SIMPLE) {
         InliningStrategy inliningStrategy;
         if (this.optimizationLevel == TeaVMOptimizationLevel.FULL) {
            inliningStrategy = new DefaultInliningStrategy(20, 7, 3000, false);
         } else {
            inliningStrategy = new DefaultInliningStrategy(100, 7, 300, true);
         }

         Inlining inlining = new Inlining(
            new ClassHierarchy(classes),
            this.dependencyAnalyzer,
            inliningStrategy,
            classes,
            this::isExternal,
            this.optimizationLevel == TeaVMOptimizationLevel.FULL,
            this.target.getInliningFilter()
         );
         List<MethodReference> methodReferences = inlining.getOrder();
         int classCount = classes.getClassNames().size();
         int initialValue = this.compileProgressValue;

         for (int i = 0; i < methodReferences.size(); i++) {
            MethodReference methodReference = methodReferences.get(i);
            ClassHolder cls = classes.get(methodReference.getClassName());
            if (cls != null) {
               MethodHolder method = cls.getMethod(methodReference.getDescriptor());
               if (method != null) {
                  if (method.getProgram() != null) {
                     if (!inlining.hasUsages(methodReference)) {
                        inlining.removeUsages(method.getProgram());
                        method.setProgram(null);
                     } else {
                        Program program = method.getProgram();
                        TeaVM.MethodOptimizationContextImpl context = new TeaVM.MethodOptimizationContextImpl(method);
                        inlining.apply(program, method.getReference());
                        new UnusedVariableElimination().optimize(context, program);
                     }
                  }

                  int newProgress = initialValue + classCount * i / methodReferences.size();
                  if (newProgress > this.compileProgressValue) {
                     this.compileProgressValue = newProgress;
                     this.reportCompileProgress(++this.compileProgressValue);
                     if (this.wasCancelled()) {
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   private void optimize(ListableClassHolderSource classSource) {
      for (String className : classSource.getClassNames()) {
         ClassHolder cls = classSource.get(className);

         for (MethodHolder method : cls.getMethods()) {
            this.optimizeMethod(method);
         }

         this.reportCompileProgress(++this.compileProgressValue);
         if (this.wasCancelled()) {
            break;
         }
      }
   }

   private void optimizeMethod(MethodHolder method) {
      if (method.getProgram() != null) {
         Program optimizedProgram = !this.cacheStatus.isStaleMethod(method.getReference())
            ? this.programCache.get(method.getReference(), this.cacheStatus)
            : null;
         if (optimizedProgram == null) {
            optimizedProgram = this.optimizeMethodCacheMiss(method, ProgramUtils.copy(method.getProgram()));
            Program finalProgram = optimizedProgram;
            this.programCache.store(method.getReference(), finalProgram, () -> this.programDependencyExtractor.extractDependencies(finalProgram));
         }

         method.setProgram(optimizedProgram);
      }
   }

   private Program optimizeMethodCacheMiss(MethodHolder method, Program optimizedProgram) {
      this.target.beforeOptimizations(optimizedProgram, method);
      if (optimizedProgram.basicBlockCount() > 0) {
         try {
            TeaVM.MethodOptimizationContextImpl context = new TeaVM.MethodOptimizationContextImpl(method);

            boolean changed;
            do {
               changed = false;

               for (MethodOptimization optimization : this.getOptimizations()) {
                  changed |= optimization.optimize(context, optimizedProgram);
               }
            } while (changed);

            this.target.afterOptimizations(optimizedProgram, method);
            VariableCategoryProvider categoryProvider = this.target.variableCategoryProvider();
            if (categoryProvider != null) {
               RegisterAllocator allocator = new RegisterAllocator(categoryProvider);
               allocator.allocateRegisters(method.getReference(), optimizedProgram, this.optimizationLevel == TeaVMOptimizationLevel.SIMPLE);
            }
         } catch (Exception | AssertionError e) {
            ListingBuilder listingBuilder = new ListingBuilder();

            try {
               String listing = listingBuilder.buildListing(optimizedProgram, "");
               System.err.println("Error optimizing program for method " + method.getReference() + ":\n" + listing);
            } catch (RuntimeException e2) {
               System.err.println("Error optimizing program for method " + method.getReference());
            }

            throw new RuntimeException(e);
         }
      }

      return optimizedProgram;
   }

   private List<MethodOptimization> getOptimizations() {
      List<MethodOptimization> optimizations = new ArrayList<>();
      optimizations.add(new RedundantJumpElimination());
      optimizations.add(new ArrayUnwrapMotion());
      if (this.optimizationLevel.ordinal() >= TeaVMOptimizationLevel.ADVANCED.ordinal()) {
         optimizations.add(new ScalarReplacement());
         optimizations.add(new LoopInvariantMotion());
      }

      if (this.optimizationLevel.ordinal() >= TeaVMOptimizationLevel.ADVANCED.ordinal()) {
         optimizations.add(new RepeatedFieldReadElimination());
      }

      optimizations.add(new GlobalValueNumbering(this.optimizationLevel == TeaVMOptimizationLevel.SIMPLE));
      optimizations.add(new RedundantNullCheckElimination());
      if (this.optimizationLevel.ordinal() >= TeaVMOptimizationLevel.ADVANCED.ordinal()) {
         optimizations.add(new ConstantConditionElimination());
         optimizations.add(new RedundantJumpElimination());
         optimizations.add(new UnusedVariableElimination());
      }

      optimizations.add(new ClassInitElimination());
      optimizations.add(new UnreachableBasicBlockElimination());
      optimizations.add(new UnusedVariableElimination());
      if (this.target.needsSystemArrayCopyOptimization()) {
         optimizations.add(new SystemArrayCopyOptimization());
      }

      optimizations.add(new RedundantPhiElimination());
      return optimizations;
   }

   public void build(File dir, String fileName) {
      this.build(new DirectoryBuildTarget(dir), fileName);
   }

   public void installPlugins() {
      for (TeaVMPlugin plugin : TeaVMPluginLoader.load(this.classLoader)) {
         plugin.install(this);
      }
   }

   @Override
   public <T> T getService(Class<T> type) {
      Object service = this.services.get(type);
      if (service == null) {
         throw new IllegalArgumentException("Service not registered: " + type.getName());
      } else {
         return type.cast(service);
      }
   }

   @Override
   public <T> void registerService(Class<T> type, T instance) {
      this.services.put(type, instance);
   }

   @Override
   public <T extends TeaVMHostExtension> T getExtension(Class<T> extensionType) {
      Object extension = this.extensions.get(extensionType);
      return extension != null ? extensionType.cast(extension) : null;
   }

   private Collection<Class<? extends TeaVMHostExtension>> getExtensionTypes(TeaVMHostExtension extension) {
      return Arrays.stream(extension.getClass().getInterfaces())
         .filter(cls -> cls.isInterface() && TeaVMHostExtension.class.isAssignableFrom((Class<?>)cls))
         .map(cls -> cls.asSubclass(TeaVMHostExtension.class))
         .collect(Collectors.toSet());
   }

   boolean isExternal(MethodReference method) {
      MethodDependencyInfo dep = this.dependencyAnalyzer.getMethod(method);
      return dep != null && dep.isCalled() ? true : this.isVirtual(method);
   }

   boolean isVirtual(MethodReference method) {
      return method.getName().equals("<clinit>")
         ? false
         : this.virtualMethods == null || this.virtualMethods.contains(method) || this.additionalVirtualMethods.stream().anyMatch(p -> p.test(method));
   }

   static class ListableClassReaderSourceAdapter implements ListableClassReaderSource {
      private ClassReaderSource classSource;
      private Set<String> classes;

      ListableClassReaderSourceAdapter(ClassReaderSource classSource, Set<String> classes) {
         this.classSource = classSource;
         this.classes = classes.stream().filter(className -> classSource.get(className) != null).collect(Collectors.toUnmodifiableSet());
      }

      @Override
      public Set<String> getClassNames() {
         return this.classes;
      }

      @Override
      public ClassReader get(String name) {
         return this.classes.contains(name) ? this.classSource.get(name) : null;
      }
   }

   class MethodOptimizationContextImpl implements MethodOptimizationContext {
      private MethodReader method;

      MethodOptimizationContextImpl(MethodReader method) {
         this.method = method;
      }

      @Override
      public MethodReader getMethod() {
         return this.method;
      }

      @Override
      public DependencyInfo getDependencyInfo() {
         return TeaVM.this.dependencyAnalyzer;
      }

      @Override
      public ClassReaderSource getClassSource() {
         return TeaVM.this.dependencyAnalyzer.getClassSource();
      }

      @Override
      public ClassHierarchy getHierarchy() {
         return TeaVM.this.dependencyAnalyzer.getClassHierarchy();
      }
   }

   class PostProcessingClassHolderSource implements ListableClassHolderSource {
      private Linker linker = new Linker(TeaVM.this.dependencyAnalyzer);
      private Map<String, ClassHolder> cache = new HashMap<>();
      private Set<String> classNames = Collections.unmodifiableSet(
         new HashSet<>(
            TeaVM.this.dependencyAnalyzer
               .getReachableClasses()
               .stream()
               .filter(className -> TeaVM.this.dependencyAnalyzer.getClassSource().get(className) != null)
               .collect(Collectors.toList())
         )
      );
      private ClassInitializerInsertionTransformer clinitInsertion = new ClassInitializerInsertionTransformer(
         TeaVM.this.dependencyAnalyzer.getClassSource(), TeaVM.this.classInitializerInfo
      );

      @Override
      public ClassHolder get(String name) {
         return this.cache
            .computeIfAbsent(
               name,
               className -> {
                  ClassReader classReader = TeaVM.this.dependencyAnalyzer.getClassSource().get(className);
                  if (!TeaVM.this.dependencyAnalyzer.getReachableClasses().contains(className)) {
                     return null;
                  }

                  if (classReader == null) {
                     return null;
                  }

                  ClassHolder cls = ModelUtils.copyClass(classReader, false);
                  this.linker.link(cls);

                  for (FieldHolder field : cls.getFields().toArray(new FieldHolder[0])) {
                     FieldReference fieldRef = new FieldReference(cls.getName(), field.getName());
                     if (TeaVM.this.dependencyAnalyzer.getField(fieldRef) == null) {
                        cls.removeField(field);
                     }
                  }

                  Function<MethodHolder, Program> programSupplier = methodx -> {
                     Program program = !TeaVM.this.cacheStatus.isStaleMethod(methodx.getReference())
                        ? TeaVM.this.programCache.get(methodx.getReference(), TeaVM.this.cacheStatus)
                        : null;
                     if (program == null) {
                        Program var5 = ProgramUtils.copy(classReader.getMethod(methodx.getDescriptor()).getProgram());
                        this.clinitInsertion.apply(methodx, var5);
                        TeaVM.this.target.beforeInlining(var5, methodx);
                        program = TeaVM.this.optimizeMethodCacheMiss(methodx, var5);
                        Program finalProgram = program;
                        TeaVM.this.programCache
                           .store(methodx.getReference(), finalProgram, () -> TeaVM.this.programDependencyExtractor.extractDependencies(finalProgram));
                     }

                     return program;
                  };

                  for (MethodHolder method : cls.getMethods().toArray(new MethodHolder[0])) {
                     MethodDependencyInfo methodDep = TeaVM.this.dependencyAnalyzer.getMethod(method.getReference());
                     if (methodDep != null && methodDep.isUsed()) {
                        MethodReader methodReader = classReader.getMethod(method.getDescriptor());
                        if (methodReader != null && methodReader.getProgram() != null) {
                           method.setProgramSupplier(programSupplier);
                        }
                     } else if (method.hasModifier(ElementModifier.STATIC)) {
                        cls.removeMethod(method);
                     } else {
                        method.getModifiers().add(ElementModifier.ABSTRACT);
                        method.getModifiers().remove(ElementModifier.NATIVE);
                        method.setProgram(null);
                     }
                  }

                  return cls;
               }
            );
      }

      @Override
      public Set<String> getClassNames() {
         return this.classNames;
      }
   }
}
