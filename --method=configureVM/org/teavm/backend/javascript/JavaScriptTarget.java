package org.teavm.backend.javascript;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import org.teavm.ast.ControlFlowEntry;
import org.teavm.backend.javascript.codegen.AliasProvider;
import org.teavm.backend.javascript.codegen.DefaultAliasProvider;
import org.teavm.backend.javascript.codegen.DefaultNamingStrategy;
import org.teavm.backend.javascript.codegen.MinifyingAliasProvider;
import org.teavm.backend.javascript.codegen.OutputSourceWriter;
import org.teavm.backend.javascript.codegen.OutputSourceWriterBuilder;
import org.teavm.backend.javascript.codegen.RememberedSource;
import org.teavm.backend.javascript.codegen.RememberingSourceWriter;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.intrinsics.ref.ReferenceQueueGenerator;
import org.teavm.backend.javascript.intrinsics.ref.ReferenceQueueTransformer;
import org.teavm.backend.javascript.intrinsics.ref.WeakReferenceDependencyListener;
import org.teavm.backend.javascript.intrinsics.ref.WeakReferenceGenerator;
import org.teavm.backend.javascript.intrinsics.ref.WeakReferenceTransformer;
import org.teavm.backend.javascript.intrinsics.reflection.ReflectionIntrinsics;
import org.teavm.backend.javascript.rendering.NameFrequencyEstimator;
import org.teavm.backend.javascript.rendering.Renderer;
import org.teavm.backend.javascript.rendering.RenderingContext;
import org.teavm.backend.javascript.rendering.RenderingUtil;
import org.teavm.backend.javascript.rendering.RuntimeRenderer;
import org.teavm.backend.javascript.spi.GeneratedBy;
import org.teavm.backend.javascript.spi.Generator;
import org.teavm.backend.javascript.spi.InjectedBy;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.MethodContributor;
import org.teavm.backend.javascript.spi.MethodContributorContext;
import org.teavm.backend.javascript.templating.JavaScriptTemplateFactory;
import org.teavm.cache.EmptyMethodNodeCache;
import org.teavm.cache.MethodNodeCache;
import org.teavm.classlib.ReflectionSupplier;
import org.teavm.debugging.information.DebugInformationEmitter;
import org.teavm.debugging.information.DummyDebugInformationEmitter;
import org.teavm.debugging.information.SourceLocation;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyAnalyzer;
import org.teavm.dependency.DependencyListener;
import org.teavm.dependency.DependencyType;
import org.teavm.dependency.MethodDependency;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.interop.PlatformMarker;
import org.teavm.model.BasicBlock;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReference;
import org.teavm.model.ListableClassHolderSource;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.transformation.BoundCheckInsertion;
import org.teavm.model.transformation.NullCheckFilter;
import org.teavm.model.transformation.NullCheckInsertion;
import org.teavm.model.util.DefaultVariableCategoryProvider;
import org.teavm.model.util.VariableCategoryProvider;
import org.teavm.reflection.AnnotationGenerationHelper;
import org.teavm.reflection.ReflectionDependencyListener;
import org.teavm.runtime.reflect.ClassInfo;
import org.teavm.vm.BuildTarget;
import org.teavm.vm.RenderingException;
import org.teavm.vm.TeaVM;
import org.teavm.vm.TeaVMTarget;
import org.teavm.vm.TeaVMTargetController;
import org.teavm.vm.spi.RendererListener;
import org.teavm.vm.spi.TeaVMHostExtension;

public class JavaScriptTarget implements TeaVMTarget, TeaVMJavaScriptHost {
   private static final NumberFormat STATS_NUM_FORMAT = new DecimalFormat("#,##0");
   private static final NumberFormat STATS_PERCENT_FORMAT = new DecimalFormat("0.000 %");
   private static final MethodReference CURRENT_THREAD = new MethodReference(Thread.class, "currentThread", Thread.class);
   private TeaVMTargetController controller;
   private boolean obfuscated = true;
   private boolean stackTraceIncluded;
   private final Map<MethodReference, Generator> methodGenerators = new HashMap<>();
   private final Map<MethodReference, Injector> methodInjectors = new HashMap<>();
   private final List<Function<ProviderContext, Generator>> generatorProviders = new ArrayList<>();
   private final List<Function<ProviderContext, Injector>> injectorProviders = new ArrayList<>();
   private final List<RendererListener> rendererListeners = new ArrayList<>();
   private DebugInformationEmitter debugEmitter;
   private MethodNodeCache astCache = EmptyMethodNodeCache.INSTANCE;
   private final Set<MethodReference> asyncMethods = new HashSet<>();
   private List<MethodContributor> customVirtualMethods = new ArrayList<>();
   private List<MethodContributor> forcedFunctionMethods = new ArrayList<>();
   private boolean strict;
   private BoundCheckInsertion boundCheckInsertion = new BoundCheckInsertion();
   private NullCheckInsertion nullCheckInsertion = new NullCheckInsertion(NullCheckFilter.EMPTY);
   private final Map<String, String> importedModules = new LinkedHashMap<>();
   private JavaScriptTemplateFactory templateFactory;
   private JSModuleType moduleType = JSModuleType.UMD;
   private List<ExportedDeclaration> exports = new ArrayList<>();
   private int maxTopLevelNames = 80000;
   private ReflectionDependencyListener reflection;

   @Override
   public List<ClassHolderTransformer> getTransformers() {
      return List.of(new WeakReferenceTransformer(), new ReferenceQueueTransformer());
   }

   @Override
   public List<DependencyListener> getDependencyListeners() {
      return Collections.emptyList();
   }

   @Override
   public void setController(TeaVMTargetController controller) {
      this.controller = controller;
      this.templateFactory = new JavaScriptTemplateFactory(controller.getClassLoader(), controller.getDependencyInfo().getClassSource());
      WeakReferenceGenerator weakRefGenerator = new WeakReferenceGenerator(this.templateFactory);
      this.methodGenerators.put(new MethodReference(WeakReference.class, "<init>", Object.class, ReferenceQueue.class, void.class), weakRefGenerator);
      this.methodGenerators.put(new MethodReference(WeakReference.class, "get", Object.class), weakRefGenerator);
      this.methodGenerators.put(new MethodReference(WeakReference.class, "clear", void.class), weakRefGenerator);
      ReferenceQueueGenerator refQueueGenerator = new ReferenceQueueGenerator();
      this.methodGenerators.put(new MethodReference(ReferenceQueue.class, "<init>", void.class), refQueueGenerator);
      this.methodGenerators.put(new MethodReference(ReferenceQueue.class, "poll", Reference.class), refQueueGenerator);
   }

   @Override
   public void add(RendererListener listener) {
      this.rendererListeners.add(listener);
   }

   @Override
   public void add(MethodReference methodRef, Generator generator) {
      this.methodGenerators.put(methodRef, generator);
   }

   @Override
   public void add(MethodReference methodRef, Injector injector) {
      this.methodInjectors.put(methodRef, injector);
   }

   @Override
   public void addGeneratorProvider(Function<ProviderContext, Generator> provider) {
      this.generatorProviders.add(provider);
   }

   @Override
   public void addInjectorProvider(Function<ProviderContext, Injector> provider) {
      this.injectorProviders.add(provider);
   }

   public void setObfuscated(boolean obfuscated) {
      this.obfuscated = obfuscated;
   }

   public MethodNodeCache getAstCache() {
      return this.astCache;
   }

   public void setAstCache(MethodNodeCache methodAstCache) {
      this.astCache = methodAstCache;
   }

   public DebugInformationEmitter getDebugEmitter() {
      return this.debugEmitter;
   }

   public void setDebugEmitter(DebugInformationEmitter debugEmitter) {
      this.debugEmitter = debugEmitter;
   }

   public void setStrict(boolean strict) {
      this.strict = strict;
   }

   public void setModuleType(JSModuleType moduleType) {
      this.moduleType = moduleType;
   }

   @Override
   public VariableCategoryProvider variableCategoryProvider() {
      return new DefaultVariableCategoryProvider();
   }

   public void setStackTraceIncluded(boolean stackTraceIncluded) {
      this.stackTraceIncluded = stackTraceIncluded;
   }

   public void setMaxTopLevelNames(int maxTopLevelNames) {
      this.maxTopLevelNames = maxTopLevelNames;
   }

   @Override
   public List<TeaVMHostExtension> getHostExtensions() {
      return Collections.singletonList(this);
   }

   @Override
   public void contributeDependencies(DependencyAnalyzer dependencyAnalyzer) {
      ArrayList<ReflectionSupplier> reflectionSuppliers = new ArrayList<>();

      for (ReflectionSupplier supplier : ServiceLoader.load(ReflectionSupplier.class, dependencyAnalyzer.getClassLoader())) {
         reflectionSuppliers.add(supplier);
      }

      this.reflection = new ReflectionDependencyListener(reflectionSuppliers, new AnnotationGenerationHelper());
      this.addVirtualMethods((ctx, method) -> this.reflection.isVirtual(method));
      DependencyType stringType = dependencyAnalyzer.getClassType("java.lang.String");
      dependencyAnalyzer.linkMethod(new MethodReference(Class.class, "create", ClassInfo.class, Class.class)).use();
      MethodDependency dep = dependencyAnalyzer.linkMethod(new MethodReference(String.class, "<init>", Object.class, void.class));
      dep.getVariable(0).propagate(stringType);
      dep.use();
      dependencyAnalyzer.linkField(new FieldReference(String.class.getName(), "characters"));
      dep = dependencyAnalyzer.linkMethod(new MethodReference(Object.class, "toString", String.class));
      dep.getVariable(0).propagate(dependencyAnalyzer.getClassType("java.lang.Object"));
      dep.use();
      MethodDependency exceptionCons = dependencyAnalyzer.linkMethod(new MethodReference(RuntimeException.class, "<init>", String.class, void.class));
      exceptionCons.getVariable(0).propagate(dependencyAnalyzer.getClassType(RuntimeException.class.getName()));
      exceptionCons.getVariable(1).propagate(stringType);
      exceptionCons.use();
      if (this.strict) {
         exceptionCons = dependencyAnalyzer.linkMethod(new MethodReference(ArrayIndexOutOfBoundsException.class, "<init>", void.class));
         exceptionCons.getVariable(0).propagate(dependencyAnalyzer.getClassType(ArrayIndexOutOfBoundsException.class.getName()));
         exceptionCons.use();
         exceptionCons = dependencyAnalyzer.linkMethod(new MethodReference(NullPointerException.class, "<init>", void.class));
         exceptionCons.getVariable(0).propagate(dependencyAnalyzer.getClassType(NullPointerException.class.getName()));
         exceptionCons.use();
         exceptionCons = dependencyAnalyzer.linkMethod(new MethodReference(ClassCastException.class, "<init>", void.class));
         exceptionCons.getVariable(0).propagate(dependencyAnalyzer.getClassType(ClassCastException.class.getName()));
         exceptionCons.use();
      }

      if (this.stackTraceIncluded) {
         includeStackTraceMethods(dependencyAnalyzer);
      }

      dependencyAnalyzer.linkMethod(new MethodReference(Throwable.class, "getMessage", String.class)).use();
      dependencyAnalyzer.linkMethod(new MethodReference(Throwable.class, "getCause", Throwable.class)).use();
      dependencyAnalyzer.addDependencyListener(new AbstractDependencyListener() {
         @Override
         public void methodReached(DependencyAgent agent, MethodDependency method) {
            if (method.getReference().equals(JavaScriptTarget.CURRENT_THREAD)) {
               method.use();
               agent.linkMethod(new MethodReference(Thread.class, "setCurrentThread", Thread.class, void.class)).use();
            }
         }
      });
      dependencyAnalyzer.addDependencyListener(new WeakReferenceDependencyListener());
      dependencyAnalyzer.addDependencyListener(this.reflection);
   }

   public static void includeStackTraceMethods(DependencyAnalyzer dependencyAnalyzer) {
      DependencyType stringType = dependencyAnalyzer.getClassType("java.lang.String");
      MethodDependency dep = dependencyAnalyzer.linkMethod(
         new MethodReference(StackTraceElement.class, "<init>", String.class, String.class, String.class, int.class, void.class)
      );
      dep.getVariable(0).propagate(dependencyAnalyzer.getClassType(StackTraceElement.class.getName()));
      dep.getVariable(1).propagate(stringType);
      dep.getVariable(2).propagate(stringType);
      dep.getVariable(3).propagate(stringType);
      dep.use();
      dep = dependencyAnalyzer.linkMethod(new MethodReference(Throwable.class, "setStackTrace", StackTraceElement[].class, void.class));
      dep.getVariable(0).propagate(dependencyAnalyzer.getClassType(Throwable.class.getName()));
      dep.getVariable(1).propagate(dependencyAnalyzer.getType(ValueType.parse(StackTraceElement[].class)));
      dep.getVariable(1).getArrayItem().propagate(dependencyAnalyzer.getClassType(StackTraceElement.class.getName()));
      dep.use();
   }

   @Override
   public void emit(ListableClassHolderSource classes, BuildTarget target, String outputName) {
      try (
         OutputStream output = target.createResource(outputName);
         Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
      ) {
         this.emit(classes, writer, target);
      } catch (IOException e) {
         throw new RenderingException(e);
      }
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

   private void emit(ListableClassHolderSource classes, Writer writer, BuildTarget target) {
      AliasProvider aliasProvider = this.obfuscated ? new MinifyingAliasProvider(this.maxTopLevelNames) : new DefaultAliasProvider(this.maxTopLevelNames);
      DefaultNamingStrategy naming = new DefaultNamingStrategy(aliasProvider, this.controller.getUnprocessedClassSource());
      DebugInformationEmitter debugEmitterToUse = this.debugEmitter;
      if (debugEmitterToUse == null) {
         debugEmitterToUse = new DummyDebugInformationEmitter();
      }

      JavaScriptTarget.MethodContributorContextImpl methodContributorContext = new JavaScriptTarget.MethodContributorContextImpl(classes);
      RenderingContext renderingContext = new RenderingContext(
         debugEmitterToUse,
         this.controller.getUnprocessedClassSource(),
         classes,
         this.controller.getResourceProvider(),
         this.controller.getClassLoader(),
         this.controller.getServices(),
         this.controller.getProperties(),
         naming,
         this.controller.getDependencyInfo(),
         m -> this.isVirtual(methodContributorContext, m),
         m -> this.isForcedFunction(methodContributorContext, m),
         this.controller.getClassInitializerInfo(),
         this.strict
      ) {
         @Override
         public String importModule(String name) {
            return JavaScriptTarget.this.importModule(name);
         }
      };
      renderingContext.setMinifying(this.obfuscated);
      new ReflectionIntrinsics(
            this.methodInjectors, this.methodGenerators, this.injectorProviders, classes, this.reflection, this.controller.getDependencyInfo()
         )
         .apply();
      if (!this.controller.wasCancelled()) {
         OutputSourceWriterBuilder builder = new OutputSourceWriterBuilder(naming);
         builder.setMinified(this.obfuscated);

         for (String className : classes.getClassNames()) {
            ClassHolder cls = classes.get(className);

            for (MethodHolder method : cls.getMethods()) {
               this.preprocessNativeMethod(method);
            }
         }

         for (Entry<MethodReference, Injector> entry : this.methodInjectors.entrySet()) {
            renderingContext.addInjector(entry.getKey(), entry.getValue());
         }

         RememberingSourceWriter rememberingWriter = new RememberingSourceWriter(this.debugEmitter != null);
         RememberingSourceWriter metadataWriter = new RememberingSourceWriter(this.debugEmitter != null);
         Renderer renderer = new Renderer(
            rememberingWriter,
            metadataWriter,
            this.asyncMethods,
            renderingContext,
            this.controller.getDiagnostics(),
            this.methodGenerators,
            this.astCache,
            this.controller.getCacheStatus(),
            this.templateFactory,
            this.exports,
            this.controller.getEntryPoint()
         );
         renderer.setProperties(this.controller.getProperties());
         renderer.setProgressConsumer(this.controller::reportProgress);

         for (RendererListener listener : this.rendererListeners) {
            listener.begin(renderer, target);
         }

         if (renderer.render(classes, this.controller.isFriendlyToDebugger())) {
            RememberedSource declarations = rememberingWriter.save();
            rememberingWriter.clear();
            RememberedSource metadata = metadataWriter.save();
            metadataWriter.clear();
            renderer.renderStringPool();
            renderer.renderStringConstants();
            renderer.renderCompatibilityStubs();
            this.emitMainMethod(classes, rememberingWriter);

            for (RendererListener listener : this.rendererListeners) {
               listener.complete();
            }

            RememberedSource epilogue = rememberingWriter.save();
            rememberingWriter.clear();
            RuntimeRenderer runtimeRenderer = new RuntimeRenderer(classes, rememberingWriter, this.controller.getClassInitializerInfo());
            runtimeRenderer.prepareAstParts(renderer.isThreadLibraryUsed());
            declarations.replay(runtimeRenderer.sink, 2);
            metadata.replay(runtimeRenderer.sink, 2);
            epilogue.replay(runtimeRenderer.sink, 2);
            runtimeRenderer.removeUnusedParts();
            runtimeRenderer.renderRuntime();
            RememberedSource runtime = rememberingWriter.save();
            rememberingWriter.clear();
            runtimeRenderer.renderEpilogue();
            RememberedSource runtimeEpilogue = rememberingWriter.save();
            rememberingWriter.clear();
            naming.additionalScopeName();
            naming.functionName("$rt_exports");

            for (String module : this.importedModules.values()) {
               naming.functionName(module);
            }

            for (ExportedDeclaration export : this.exports) {
               export.nameFreq.accept(naming);
            }

            NameFrequencyEstimator frequencyEstimator = new NameFrequencyEstimator();
            runtime.replay(frequencyEstimator, 2);
            runtimeEpilogue.replay(frequencyEstimator, 2);
            declarations.replay(frequencyEstimator, 2);
            metadata.replay(frequencyEstimator, 2);
            epilogue.replay(frequencyEstimator, 2);
            frequencyEstimator.apply(naming);
            OutputSourceWriter sourceWriter = builder.build(writer);
            sourceWriter.setDebugInformationEmitter(debugEmitterToUse);
            this.printWrapperStart(sourceWriter);
            if (frequencyEstimator.hasAdditionalScope()) {
               sourceWriter.append("let ").append(naming.additionalScopeName()).ws().append('=').ws().append("{};").softNewLine();
            }

            int start = sourceWriter.getOffset();
            runtime.write(sourceWriter, 0);
            declarations.write(sourceWriter, 0);
            metadata.write(sourceWriter, 0);
            runtimeEpilogue.write(sourceWriter, 0);
            epilogue.write(sourceWriter, 0);
            this.printModuleEnd(sourceWriter);
            sourceWriter.finish();
            int totalSize = sourceWriter.getOffset() - start;
            this.printStats(sourceWriter, totalSize);
         }
      }
   }

   private void emitMainMethod(ClassReaderSource classes, SourceWriter writer) {
      String alias = "$rt_export_main";
      MethodReference ref = new MethodReference(this.controller.getEntryPoint(), TeaVM.MAIN_METHOD_DESC);
      MethodReader mainMethod = classes.resolve(ref);
      boolean hasMainMethod = false;
      boolean instanceEntryPoint = false;
      if (mainMethod != null) {
         instanceEntryPoint = !mainMethod.hasModifier(ElementModifier.STATIC);
         hasMainMethod = true;
      } else {
         ref = new MethodReference(this.controller.getEntryPoint(), TeaVM.SHORT_MAIN_METHOD_DESC);
         mainMethod = classes.resolve(ref);
         if (mainMethod != null) {
            instanceEntryPoint = true;
            hasMainMethod = true;
         }
      }

      if (hasMainMethod) {
         if (instanceEntryPoint) {
            writer.startVariableDeclaration()
               .appendFunction(alias)
               .appendFunction("$rt_instanceMainStarter")
               .append("(")
               .appendMethod(ref)
               .append(",")
               .ws()
               .appendMethod(this.controller.getEntryPoint(), "<init>", ValueType.VOID)
               .append(",")
               .ws()
               .appendClass(this.controller.getEntryPoint())
               .append(")")
               .endDeclaration();
         } else {
            writer.startVariableDeclaration().appendFunction(alias).appendFunction("$rt_mainStarter").append("(").appendMethod(ref);
            writer.append(")").endDeclaration();
         }

         writer.appendFunction(alias).append(".").append("javaException").ws().append("=").ws().appendFunction("$rt_javaException").append(";").newLine();
         this.exports.add(new ExportedDeclaration(w -> w.appendFunction(alias), n -> n.functionName(alias), this.controller.getEntryPointName()));
      }
   }

   private void printWrapperStart(SourceWriter writer) {
      writer.append("\"use strict\";").newLine();
      this.printModuleStart(writer);
   }

   private String importModule(String name) {
      return this.importedModules.computeIfAbsent(name, n -> "$rt_imported_" + this.importedModules.size());
   }

   private void printModuleStart(SourceWriter writer) {
      switch (this.moduleType) {
         case UMD:
            this.printUmdStart(writer);
            break;
         case COMMON_JS:
            this.printCommonJsStart(writer);
            break;
         case NONE:
            this.printIIFStart(writer);
            break;
         case ES2015:
            this.printES2015Start(writer);
      }
   }

   private void printUmdStart(SourceWriter writer) {
      writer.append("(function(module)").appendBlockStart();
      writer.appendIf().append("typeof define").ws().append("===").ws().append("'function'").ws().append("&&").ws().append("define.amd)").appendBlockStart();
      writer.append("define(['exports'");

      for (String moduleName : this.importedModules.keySet()) {
         writer.append(',').ws().append('"').append(RenderingUtil.escapeString(moduleName)).append('"');
      }

      writer.append("],").ws().append("function(exports");

      for (String moduleAlias : this.importedModules.values()) {
         writer.append(',').ws().appendFunction(moduleAlias);
      }

      writer.append(")").ws().appendBlockStart();
      writer.append("module(exports");

      for (String moduleAlias : this.importedModules.values()) {
         writer.append(',').ws().appendFunction(moduleAlias);
      }

      writer.append(");").softNewLine();
      writer.outdent().append("});").softNewLine();
      writer.appendElseIf()
         .append("typeof exports")
         .ws()
         .append("===")
         .ws()
         .append("'object'")
         .ws()
         .append("&&")
         .ws()
         .append("exports")
         .ws()
         .append("!==")
         .ws()
         .append("null")
         .ws()
         .append("&&")
         .ws()
         .append("typeof exports.nodeName")
         .ws()
         .append("!==")
         .ws()
         .append("'string')")
         .appendBlockStart();
      writer.append("module(exports");

      for (String moduleName : this.importedModules.keySet()) {
         writer.append(',').ws().append("require(\"").append(RenderingUtil.escapeString(moduleName)).append("\")");
      }

      writer.append(");").softNewLine();
      writer.appendElse();
      writer.append("module(");
      writer.outdent()
         .append("typeof self")
         .ws()
         .append("!==")
         .ws()
         .append("'undefined'")
         .ws()
         .append("?")
         .ws()
         .append("self")
         .ws()
         .append(":")
         .ws()
         .append("this");
      writer.append(");").softNewLine();
      writer.appendBlockEnd();
      writer.outdent().append("}(");
      writer.append("function(").appendFunction("$rt_exports");

      for (String moduleName : this.importedModules.values()) {
         writer.append(",").ws().appendFunction(moduleName);
      }

      writer.append(")").appendBlockStart();
   }

   private void printIIFStart(SourceWriter writer) {
      for (ExportedDeclaration export : this.exports) {
         writer.append("var ").appendGlobal(export.alias).append(";").softNewLine();
      }

      writer.append("(function()").appendBlockStart();

      for (Entry<String, String> entry : this.importedModules.entrySet()) {
         String moduleName = entry.getKey();
         String alias = entry.getValue();
         writer.append("let ")
            .appendFunction(alias)
            .ws()
            .append('=')
            .ws()
            .append("this[\"")
            .append(RenderingUtil.escapeString(moduleName))
            .append("\"];")
            .softNewLine();
      }
   }

   private void printCommonJsStart(SourceWriter writer) {
      for (Entry<String, String> entry : this.importedModules.entrySet()) {
         String moduleName = entry.getKey();
         String alias = entry.getValue();
         writer.append("let ")
            .appendFunction(alias)
            .ws()
            .append('=')
            .ws()
            .append("require(\"")
            .append(RenderingUtil.escapeString(moduleName))
            .append("\");")
            .softNewLine();
      }
   }

   private void printES2015Start(SourceWriter writer) {
      for (Entry<String, String> entry : this.importedModules.entrySet()) {
         String moduleName = entry.getKey();
         String alias = entry.getValue();
         writer.append("import")
            .ws()
            .append("*")
            .ws()
            .append("as ")
            .appendFunction(alias)
            .append(" from")
            .ws()
            .append("\"")
            .append(RenderingUtil.escapeString(moduleName))
            .append("\";")
            .softNewLine();
      }
   }

   private void printModuleEnd(SourceWriter writer) {
      switch (this.moduleType) {
         case UMD:
            this.printUmdEnd(writer);
            break;
         case COMMON_JS:
            this.printCommonJsEnd(writer);
            break;
         case NONE:
            this.printIFFEnd(writer);
            break;
         case ES2015:
            this.printES2015End(writer);
      }
   }

   private void printUmdEnd(SourceWriter writer) {
      for (ExportedDeclaration export : this.exports) {
         writer.appendFunction("$rt_exports").append(".").append(export.alias).ws().append("=").ws();
         export.name.accept(writer);
         writer.append(";").softNewLine();
      }

      writer.outdent().append("}));").newLine();
   }

   private void printCommonJsEnd(SourceWriter writer) {
      for (ExportedDeclaration export : this.exports) {
         writer.append("exports.").append(export.alias).ws().append("=").ws();
         export.name.accept(writer);
         writer.append(";").softNewLine();
      }
   }

   private void printIFFEnd(SourceWriter writer) {
      for (ExportedDeclaration export : this.exports) {
         writer.appendGlobal(export.alias).ws().append("=").ws();
         export.name.accept(writer);
         writer.append(";").softNewLine();
      }

      writer.outdent().append("})();");
   }

   private void printES2015End(SourceWriter writer) {
      writer.append("export").ws().append("{").ws();
      boolean first = true;

      for (ExportedDeclaration export : this.exports) {
         if (!first) {
            writer.append(",").ws();
         }

         first = false;
         export.name.accept(writer);
         writer.append(" as ").append(export.alias);
      }

      writer.ws().append("};").softNewLine();
   }

   private void printStats(OutputSourceWriter writer, int totalSize) {
      if (Boolean.parseBoolean(System.getProperty("teavm.js.stats", "false"))) {
         System.out.println("Total output size: " + STATS_NUM_FORMAT.format(totalSize));
         System.out.println("Metadata size: " + this.getSizeWithPercentage(writer.getSectionSize(1), totalSize));
         System.out.println("String pool size: " + this.getSizeWithPercentage(writer.getSectionSize(0), totalSize));
         ObjectIntHashMap<String> packageSizeMap = new ObjectIntHashMap();

         for (String className : writer.getClassesInStats()) {
            String packageName = className.substring(0, className.lastIndexOf(46) + 1);
            int classSize = writer.getClassSize(className);
            packageSizeMap.put(packageName, packageSizeMap.getOrDefault(packageName, 0) + classSize);
         }

         String[] packageNames = (String[])packageSizeMap.keys().toArray(String.class);
         Arrays.sort(packageNames, Comparator.comparing(p -> -packageSizeMap.getOrDefault(p, 0)));

         for (String packageName : packageNames) {
            System.out.println("Package '" + packageName + "' size: " + this.getSizeWithPercentage(packageSizeMap.get(packageName), totalSize));
         }
      }
   }

   private String getSizeWithPercentage(int size, int totalSize) {
      return STATS_NUM_FORMAT.format(size) + " (" + STATS_PERCENT_FORMAT.format((double)size / totalSize) + ")";
   }

   private void preprocessNativeMethod(MethodHolder method) {
      if (method.getModifiers().contains(ElementModifier.NATIVE)
         && this.methodGenerators.get(method.getReference()) == null
         && this.methodInjectors.get(method.getReference()) == null) {
         boolean found = false;
         ProviderContext context = new JavaScriptTarget.ProviderContextImpl(method.getReference());

         for (Function<ProviderContext, Generator> provider : this.generatorProviders) {
            Generator generator = provider.apply(context);
            if (generator != null) {
               this.methodGenerators.put(method.getReference(), generator);
               found = true;
               break;
            }
         }

         for (Function<ProviderContext, Injector> provider : this.injectorProviders) {
            Injector injector = provider.apply(context);
            if (injector != null) {
               this.methodInjectors.put(method.getReference(), injector);
               found = true;
               break;
            }
         }

         if (!found) {
            if (isBootstrap()
               || method.getAnnotations().get(GeneratedBy.class.getName()) == null && method.getAnnotations().get(InjectedBy.class.getName()) == null) {
               method.getModifiers().remove(ElementModifier.NATIVE);
               Program program = new Program();
               method.setProgram(program);

               for (int i = 0; i <= method.parameterCount(); i++) {
                  program.createVariable();
               }

               BasicBlock block = program.createBasicBlock();
               Variable exceptionVar = program.createVariable();
               ConstructInstruction newExceptionInsn = new ConstructInstruction();
               newExceptionInsn.setType(NoSuchMethodError.class.getName());
               newExceptionInsn.setReceiver(exceptionVar);
               block.add(newExceptionInsn);
               Variable constVar = program.createVariable();
               StringConstantInstruction constInsn = new StringConstantInstruction();
               constInsn.setConstant("Native method implementation not found: " + method.getReference());
               constInsn.setReceiver(constVar);
               block.add(constInsn);
               InvokeInstruction initExceptionInsn = new InvokeInstruction();
               initExceptionInsn.setInstance(exceptionVar);
               initExceptionInsn.setMethod(new MethodReference(NoSuchMethodError.class, "<init>", String.class, void.class));
               initExceptionInsn.setType(InvocationType.SPECIAL);
               initExceptionInsn.setArguments(constVar);
               block.add(initExceptionInsn);
               RaiseInstruction raiseInsn = new RaiseInstruction();
               raiseInsn.setException(exceptionVar);
               block.add(raiseInsn);
               this.controller
                  .getDiagnostics()
                  .error(new CallLocation(method.getReference()), "Native method {{m0}} has no implementation", method.getReference());
            }
         }
      }
   }

   @PlatformMarker
   private static boolean isBootstrap() {
      return false;
   }

   private void emitCFG(DebugInformationEmitter emitter, ControlFlowEntry[] cfg) {
      for (ControlFlowEntry entry : cfg) {
         SourceLocation location = map(entry.from);
         SourceLocation[] successors = new SourceLocation[entry.to.length];

         for (int i = 0; i < entry.to.length; i++) {
            successors[i] = map(entry.to[i]);
         }

         emitter.addSuccessors(location, successors);
      }
   }

   private static SourceLocation map(TextLocation location) {
      return location != null && !location.isEmpty() ? new SourceLocation(location.getFileName(), location.getLine()) : null;
   }

   @Override
   public String[] getPlatformTags() {
      return new String[]{"javascript"};
   }

   @Override
   public void addVirtualMethods(MethodContributor virtualMethods) {
      this.customVirtualMethods.add(virtualMethods);
   }

   @Override
   public void addForcedFunctionMethods(MethodContributor forcedFunctionMethods) {
      this.forcedFunctionMethods.add(forcedFunctionMethods);
   }

   @Override
   public boolean isAsyncSupported() {
      return true;
   }

   private boolean isVirtual(MethodContributorContext context, MethodReference method) {
      if (this.controller.isVirtual(method)) {
         return true;
      }

      for (MethodContributor predicate : this.customVirtualMethods) {
         if (predicate.isContributing(context, method)) {
            return true;
         }
      }

      return false;
   }

   private boolean isForcedFunction(MethodContributorContext context, MethodReference method) {
      for (MethodContributor predicate : this.forcedFunctionMethods) {
         if (predicate.isContributing(context, method)) {
            return true;
         }
      }

      return false;
   }

   private static class MethodContributorContextImpl implements MethodContributorContext {
      private ClassReaderSource classSource;

      MethodContributorContextImpl(ClassReaderSource classSource) {
         this.classSource = classSource;
      }

      @Override
      public ClassReaderSource getClassSource() {
         return this.classSource;
      }
   }

   class ProviderContextImpl implements ProviderContext {
      private MethodReference method;

      ProviderContextImpl(MethodReference method) {
         this.method = method;
      }

      @Override
      public MethodReference getMethod() {
         return this.method;
      }

      @Override
      public ClassReaderSource getClassSource() {
         return JavaScriptTarget.this.controller.getUnprocessedClassSource();
      }

      @Override
      public <T> T getService(Class<T> type) {
         return JavaScriptTarget.this.controller.getServices().getService(type);
      }
   }
}
