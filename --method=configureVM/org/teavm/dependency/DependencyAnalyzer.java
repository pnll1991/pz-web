package org.teavm.dependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;
import org.teavm.asm.ClassReader;
import org.teavm.asm.tree.ClassNode;
import org.teavm.cache.IncrementalDependencyProvider;
import org.teavm.cache.IncrementalDependencyRegistration;
import org.teavm.callgraph.CallGraph;
import org.teavm.common.CachedFunction;
import org.teavm.common.ServiceRepository;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.extension.ExtensionEnvironmentImpl;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntSet;
import org.teavm.hppc.cursors.IntCursor;
import org.teavm.interop.PlatformMarker;
import org.teavm.model.AnnotationReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ReferenceCache;
import org.teavm.model.ValueType;
import org.teavm.model.optimization.UnreachableBasicBlockEliminator;
import org.teavm.model.util.ModelUtils;
import org.teavm.model.util.ProgramUtils;
import org.teavm.parsing.Parser;
import org.teavm.parsing.resource.ResourceProvider;
import org.teavm.vm.spi.ClassFilter;
import org.teavm.vm.spi.ClassFilterContext;

public abstract class DependencyAnalyzer implements DependencyInfo {
   private static final int PROPAGATION_STACK_THRESHOLD = 50;
   private static final MethodDescriptor CLINIT_METHOD = new MethodDescriptor("<clinit>", void.class);
   static final boolean shouldLog = System.getProperty("org.teavm.logDependencies", "false").equals("true");
   static final boolean shouldTag = System.getProperty("org.teavm.tagDependencies", "false").equals("true") || shouldLog;
   static final boolean dependencyReport = System.getProperty("org.teavm.dependencyReport", "false").equals("true");
   private int classNameSuffix;
   private ClassReaderSource unprocessedClassSource;
   private ResourceProvider resourceProvider;
   private DependencyClassSource classSource;
   ClassReaderSource agentClassSource;
   private ClassLoader classLoader;
   private Map<String, Map<MethodDescriptor, Optional<MethodHolder>>> methodReaderCache = new HashMap<>(1000, 0.5F);
   private Map<MethodReference, MethodDependency> implementationCache = new HashMap<>();
   private Map<String, Map<MethodDescriptor, MethodDependency>> methodCache = new HashMap<>();
   private Set<MethodReference> reachedMethods = new LinkedHashSet<>();
   private Set<MethodReference> readonlyReachedMethods = Collections.unmodifiableSet(this.reachedMethods);
   private CachedFunction<FieldReference, FieldDependency> fieldCache;
   private CachedFunction<String, ClassDependency> classCache;
   private List<DependencyListener> listeners = new ArrayList<>();
   private ServiceRepository services;
   private Deque<Transition> pendingTransitions = new ArrayDeque<>();
   private Deque<Runnable> tasks = new ArrayDeque<>();
   private Queue<Runnable> deferredTasks = new ArrayDeque<>();
   List<DependencyType> types = new ArrayList<>();
   private Map<ValueType, DependencyType> typeMap = new HashMap<>();
   private DependencyAnalyzerInterruptor interruptor;
   private boolean interrupted;
   private Diagnostics diagnostics;
   DefaultCallGraph callGraph = new DefaultCallGraph();
   private DependencyAgent agent;
   Map<MethodReference, DependencyPlugin> dependencyPlugins = new HashMap<>();
   private boolean completing;
   private Map<ValueType, DependencyTypeFilter> superClassFilters = new HashMap<>();
   private List<DependencyNode> allNodes = new ArrayList<>();
   private ClassHierarchy classHierarchy;
   DependencyAnalyzer.IncrementalCache incrementalCache = new DependencyAnalyzer.IncrementalCache();
   boolean asyncSupported;
   private ReferenceCache referenceCache;
   private Set<String> generatedClassNames = new HashSet<>();
   DependencyType classType;
   private List<ClassFilter> classFilters = new ArrayList<>();
   boolean merging;
   private ExtensionEnvironmentImpl extensionEnv;
   private int propagationDepth;
   private ClassFilterContext filterContext = new ClassFilterContext() {
      @Override
      public ClassReaderSource classes() {
         return DependencyAnalyzer.this.unprocessedClassSource;
      }
   };

   DependencyAnalyzer(
      ClassReaderSource classSource,
      ResourceProvider resourceProvider,
      ClassLoader classLoader,
      ServiceRepository services,
      Diagnostics diagnostics,
      ReferenceCache referenceCache,
      String[] platformTags,
      Supplier<Properties> properties
   ) {
      this.unprocessedClassSource = classSource;
      this.resourceProvider = resourceProvider;
      this.diagnostics = diagnostics;
      this.referenceCache = referenceCache;
      this.agent = new DependencyAgent(this);
      this.classSource = new DependencyClassSource(this.agent, classSource, diagnostics, this.incrementalCache, platformTags);
      this.agentClassSource = this.classSource;
      this.classHierarchy = new ClassHierarchy(this.classSource);
      this.classLoader = classLoader;
      this.services = services;
      this.fieldCache = new CachedFunction<>(preimage -> {
         FieldReader field = this.classSource.getReferenceResolver().resolve(preimage);
         if (field != null && !field.getReference().equals(preimage)) {
            return this.fieldCache.apply(field.getReference());
         }

         FieldDependency node = this.createFieldNode(preimage, field);
         if (field != null && field.getInitialValue() instanceof String) {
            node.getValue().propagate(this.getType(ValueType.object("java.lang.String")));
         }

         return node;
      });
      this.classCache = new CachedFunction<>(this::createClassDependency);
      this.classType = this.getClassType("java.lang.Class");
      this.extensionEnv = new ExtensionEnvironmentImpl(resourceProvider, this.classHierarchy, classLoader, diagnostics, properties);
   }

   public void addClassFilter(ClassFilter filter) {
      this.classFilters.add(filter);
   }

   public void setEntryPoint(String entryPoint) {
      this.classSource.setEntryPoint(entryPoint);
      this.agent.setEntryPoint(entryPoint);
   }

   public void setObfuscated(boolean obfuscated) {
      this.classSource.obfuscated = obfuscated;
   }

   public void setStrict(boolean strict) {
      this.classSource.strict = strict;
   }

   public void setAsyncSupported(boolean asyncSupported) {
      this.asyncSupported = asyncSupported;
   }

   public DependencyAgent getAgent() {
      return this.agent;
   }

   public DependencyAnalyzerInterruptor getInterruptor() {
      return this.interruptor;
   }

   public void setInterruptor(DependencyAnalyzerInterruptor interruptor) {
      this.interruptor = interruptor;
   }

   public boolean wasInterrupted() {
      return this.interrupted;
   }

   public ResourceProvider getResourceProvider() {
      return this.resourceProvider;
   }

   public DependencyType getType(ValueType valueType) {
      DependencyType type = this.typeMap.get(valueType);
      if (type == null) {
         type = new DependencyType(valueType, this.types.size());
         this.types.add(type);
         this.typeMap.put(valueType, type);
      }

      return type;
   }

   public DependencyType getClassType(String className) {
      return this.getType(ValueType.object(className));
   }

   public DependencyNode createNode() {
      return this.createNode(null);
   }

   DependencyNode createNode(ValueType typeFilter) {
      if (typeFilter != null && typeFilter.isObject("java.lang.Object")) {
         typeFilter = null;
      }

      DependencyNode node = new DependencyNode(this, typeFilter);
      this.allNodes.add(node);
      return node;
   }

   @Override
   public ClassReaderSource getClassSource() {
      return this.classSource != null ? this.classSource : this.agentClassSource;
   }

   public ClassReaderSource getUnprocessedClassSource() {
      return this.unprocessedClassSource;
   }

   public boolean isSynthesizedClass(String className) {
      return this.classSource != null ? this.classSource.isGeneratedClass(className) : this.generatedClassNames.contains(className);
   }

   public ClassHierarchy getClassHierarchy() {
      return this.classHierarchy;
   }

   @Override
   public ClassLoader getClassLoader() {
      return this.classLoader;
   }

   public String generateClassName() {
      return "$$teavm_generated_class$$" + this.classNameSuffix++;
   }

   public String submitClassFile(byte[] data) {
      ClassNode node = new ClassNode();
      ClassReader reader = new ClassReader(data);
      reader.accept(node, 0);
      this.submitClass(new Parser(this.referenceCache).parseClass(node));
      return node.name;
   }

   public void submitClass(ClassHolder cls) {
      if (this.completing) {
         throw new IllegalStateException("Can't submit class during completion phase");
      }

      this.classSource.submit(ModelUtils.copyClass(cls));
   }

   public void submitMethod(MethodReference methodRef, Program program) {
      if (!this.completing) {
         ClassHolder cls = this.classSource.get(methodRef.getClassName());
         if (cls == null) {
            throw new IllegalArgumentException("Class not found: " + methodRef.getClassName());
         }

         if (cls.getMethod(methodRef.getDescriptor()) != null) {
            throw new IllegalArgumentException("Method already exists: " + methodRef.getClassName());
         }

         MethodHolder method = new MethodHolder(methodRef.getDescriptor());
         method.getModifiers().add(ElementModifier.STATIC);
         method.setProgram(ProgramUtils.copy(program));
         new UnreachableBasicBlockEliminator().optimize(program);
         cls.addMethod(method);
      } else {
         MethodDependency dep = this.getMethod(methodRef);
         if (dep == null) {
            throw new IllegalArgumentException("Method was not reached: " + methodRef);
         }

         MethodHolder method = dep.method;
         if (!method.hasModifier(ElementModifier.NATIVE)) {
            throw new IllegalArgumentException("Method is not native: " + methodRef);
         }

         if (!dep.used) {
            return;
         }

         method.getModifiers().remove(ElementModifier.NATIVE);
         method.setProgram(ProgramUtils.copy(program));
         new UnreachableBasicBlockEliminator().optimize(method.getProgram());
         dep.used = false;
         this.lock(dep, false);
         this.deferredTasks.add(() -> {
            this.classSource.use(dep.method.getReference());
            this.processMethod(dep);
            dep.used = true;
         });
         this.processQueue();
      }
   }

   protected abstract void processMethod(MethodDependency var1);

   public void addDependencyListener(DependencyListener listener) {
      this.listeners.add(Objects.requireNonNull(listener));
   }

   public void addClassTransformer(ClassHolderTransformer transformer) {
      this.classSource.addTransformer(transformer);
   }

   void schedulePropagation(DependencyConsumer consumer, DependencyType type) {
      if (this.filterType(type.getValueType())) {
         if (this.propagationDepth < 50) {
            this.propagationDepth++;
            consumer.consume(type);
            this.propagationDepth--;
         } else {
            this.tasks.add(() -> consumer.consume(type));
         }
      }
   }

   void schedulePropagation(Transition consumer, DependencyType type) {
      if (consumer.destination.filter(type)) {
         if (this.filterType(type.getValueType())) {
            if (consumer.pendingTypes == null && this.propagationDepth < 50 && consumer.pointsToDomainOrigin() && consumer.destination.propagateCount < 20) {
               this.propagationDepth++;
               consumer.consume(type);
               this.propagationDepth--;
            } else {
               if (consumer.pendingTypes == null) {
                  this.pendingTransitions.add(consumer);
                  consumer.pendingTypes = new IntHashSet(50);
               }

               consumer.pendingTypes.add(type.index);
            }
         }
      }
   }

   void schedulePropagation(Transition consumer, DependencyType[] types) {
      if (types.length != 0) {
         if (types.length == 1) {
            this.schedulePropagation(consumer, types[0]);
         } else {
            if (consumer.pendingTypes == null && this.propagationDepth < 50 && consumer.pointsToDomainOrigin() && consumer.destination.propagateCount < 20) {
               this.propagationDepth++;
               consumer.consume(types);
               this.propagationDepth--;
            } else {
               if (consumer.pendingTypes == null) {
                  this.pendingTransitions.add(consumer);
                  consumer.pendingTypes = new IntHashSet(Math.max(50, types.length));
               }

               consumer.pendingTypes.ensureCapacity(types.length + consumer.pendingTypes.size());

               for (DependencyType type : types) {
                  consumer.pendingTypes.add(type.index);
               }
            }
         }
      }
   }

   void schedulePropagation(DependencyConsumer consumer, DependencyType[] types) {
      if (types.length != 0) {
         if (types.length == 1) {
            this.schedulePropagation(consumer, types[0]);
         } else {
            if (this.propagationDepth < 50) {
               this.propagationDepth++;

               for (DependencyType type : types) {
                  if (this.filterType(type.getValueType())) {
                     consumer.consume(type);
                  }
               }

               this.propagationDepth--;
            } else {
               this.tasks.add(() -> {
                  for (DependencyType typex : types) {
                     if (this.filterType(typex.getValueType())) {
                        consumer.consume(typex);
                     }
                  }
               });
            }
         }
      }
   }

   boolean filterType(ValueType type) {
      return this.classFilters.stream().allMatch(filter -> filter.accept(this.filterContext, type));
   }

   public void defer(Runnable task) {
      this.deferredTasks.add(task);
   }

   public ClassDependency linkClass(String className) {
      if (this.completing && this.getClass(className) == null) {
         throw new IllegalStateException("Can't link class during completion phase");
      }

      ClassDependency dep = this.classCache.apply(className);
      if (!dep.activated) {
         dep.activated = true;
         if (!dep.isMissing()) {
            this.deferredTasks.add(() -> {
               for (DependencyListener listener : this.listeners) {
                  listener.classReached(this.agent, className);
               }
            });
            org.teavm.model.ClassReader cls = dep.getClassReader();
            if (cls.getParent() != null && !this.classCache.caches(cls.getParent())) {
               ClassDependency parentDep = this.linkClass(cls.getParent());
               if (parentDep.isMissing()) {
                  this.diagnostics.error(null, "Class {{c0}} extends {{c1}}, which is missing in the classpath", cls.getName(), cls.getParent());
               }
            }

            for (String iface : cls.getInterfaces()) {
               if (!this.classCache.caches(iface)) {
                  ClassDependency itfDep = this.linkClass(iface);
                  if (itfDep.isMissing()) {
                     this.diagnostics.error(null, "Class {{c0}} implements {{c1}}, which is missing in the classpath", cls.getName(), iface);
                  }
               }
            }
         }
      }

      return dep;
   }

   private ClassDependency createClassDependency(String className) {
      org.teavm.model.ClassReader cls = this.classSource.get(className);
      return new ClassDependency(this, className, cls);
   }

   public MethodDependency linkMethod(String className, MethodDescriptor descriptor) {
      MethodDependency dep = this.getMethodDependency(className, descriptor);
      if (!dep.activated) {
         this.reachedMethods.add(dep.getReference());
         dep.activated = true;
         if (!dep.isMissing()) {
            this.defer(() -> {
               for (DependencyListener listener : this.listeners) {
                  listener.methodReached(this.agent, dep);
               }

               this.activateDependencyPlugin(dep);
            });
         }
      }

      return dep;
   }

   public MethodDependency linkMethod(MethodReference method) {
      return this.linkMethod(method.getClassName(), method.getDescriptor());
   }

   void initClass(ClassDependency cls, CallLocation location) {
      org.teavm.model.ClassReader reader = cls.getClassReader();
      MethodReader method = reader.getMethod(CLINIT_METHOD);
      if (method != null) {
         this.deferredTasks.add(() -> {
            MethodDependency initMethod = this.linkMethod(method.getReference());
            if (location != null) {
               initMethod.addLocation(location);
            }

            initMethod.use();
         });
      }
   }

   private MethodDependency createMethodDep(MethodReference methodRef, MethodHolder method) {
      ValueType[] arguments = methodRef.getParameterTypes();
      int paramCount = arguments.length + 1;
      DependencyNode[] parameterNodes = new DependencyNode[arguments.length + 1];
      parameterNodes[0] = this.createParameterNode(methodRef, ValueType.object(methodRef.getClassName()), 0);

      for (int i = 0; i < arguments.length; i++) {
         parameterNodes[i + 1] = this.createParameterNode(methodRef, arguments[i], i + 1);
      }

      DependencyNode resultNode;
      if (methodRef.getDescriptor().getResultType() == ValueType.VOID) {
         resultNode = null;
      } else {
         resultNode = this.createResultNode(methodRef);
      }

      DependencyNode thrown = this.createThrownNode(methodRef);
      MethodDependency dep = new MethodDependency(this, parameterNodes, paramCount, resultNode, thrown, method, methodRef);
      if (method != null) {
         this.deferredTasks.add(() -> this.linkClass(dep.getMethod().getOwnerName()).initClass(new CallLocation(dep.getMethod().getReference())));
      }

      return dep;
   }

   abstract DependencyNode createParameterNode(MethodReference var1, ValueType var2, int var3);

   abstract DependencyNode createResultNode(MethodReference var1);

   abstract DependencyNode createThrownNode(MethodReference var1);

   abstract DependencyNode createFieldNode(FieldReference var1, ValueType var2);

   abstract DependencyNode createArrayItemNode(DependencyNode var1);

   abstract DependencyNode createClassValueNode(int var1, DependencyNode var2);

   void scheduleMethodAnalysis(MethodDependency dep) {
      this.classSource.use(dep.getReference());
      this.deferredTasks.add(() -> this.processMethod(dep));
   }

   @Override
   public Collection<MethodReference> getReachableMethods() {
      return this.readonlyReachedMethods;
   }

   @Override
   public Collection<FieldReference> getReachableFields() {
      return this.fieldCache.getCachedPreimages();
   }

   @Override
   public Collection<String> getReachableClasses() {
      return this.classCache.getCachedPreimages();
   }

   public FieldDependency linkField(FieldReference fieldRef) {
      FieldDependency dep = this.fieldCache.apply(fieldRef);
      if (!dep.activated) {
         dep.activated = true;
         if (!dep.isMissing()) {
            for (DependencyListener listener : this.listeners) {
               listener.fieldReached(this.agent, dep);
            }
         }
      }

      return dep;
   }

   public FieldDependency getField(FieldReference fieldRef) {
      return this.fieldCache.getKnown(fieldRef);
   }

   public ClassDependency getClass(String className) {
      return this.classCache.getKnown(className);
   }

   private FieldDependency createFieldNode(FieldReference fieldRef, FieldReader field) {
      DependencyNode node = this.createFieldNode(fieldRef, field != null ? field.getType() : null);
      return new FieldDependency(node, field, fieldRef);
   }

   private void activateDependencyPlugin(MethodDependency methodDep) {
      this.attachDependencyPlugin(methodDep);
      if (methodDep.dependencyPlugin != null) {
         methodDep.dependencyPlugin.methodReached(this.agent, methodDep);
      }
   }

   private void attachDependencyPlugin(MethodDependency methodDep) {
      if (!methodDep.dependencyPluginAttached) {
         methodDep.dependencyPluginAttached = true;
         methodDep.dependencyPlugin = this.dependencyPlugins.get(methodDep.getReference());
         if (methodDep.dependencyPlugin == null && !isBootstrap()) {
            AnnotationReader depAnnot = methodDep.getMethod().getAnnotations().get(PluggableDependency.class.getName());
            if (depAnnot != null) {
               ValueType depType = depAnnot.getValue("value").getJavaClass();
               String depClassName = ((ValueType.Object)depType).getClassName();

               Class<?> depClass;
               try {
                  depClass = Class.forName(depClassName, true, this.classLoader);
               } catch (ClassNotFoundException e) {
                  throw new RuntimeException("Dependency plugin not found: " + depClassName, e);
               }

               try {
                  methodDep.dependencyPlugin = (DependencyPlugin)depClass.newInstance();
               } catch (IllegalAccessException | InstantiationException e) {
                  throw new RuntimeException("Can't instantiate dependency plugin " + depClassName, e);
               }
            }
         }
      }
   }

   @PlatformMarker
   private static boolean isBootstrap() {
      return false;
   }

   public MethodDependency getMethod(MethodReference methodRef) {
      return this.getMethod(methodRef.getClassName(), methodRef.getDescriptor());
   }

   public MethodDependency getMethod(String className, MethodDescriptor descriptor) {
      Map<MethodDescriptor, MethodDependency> map = this.methodCache.get(className);
      return map == null ? null : map.get(descriptor);
   }

   public MethodDependency getMethodImplementation(MethodReference methodRef) {
      return this.implementationCache.computeIfAbsent(methodRef, m -> {
         MethodReader resolved = this.agentClassSource.resolveImplementation(m);
         return resolved != null ? this.getMethod(resolved.getReference()) : null;
      });
   }

   private MethodHolder getMethodHolder(String className, MethodDescriptor descriptor) {
      return this.methodReaderCache
         .computeIfAbsent(className, k -> new HashMap<>(100, 0.5F))
         .computeIfAbsent(descriptor, k -> Optional.ofNullable(this.classSource.resolveMutableImplementation(className, k)))
         .orElse(null);
   }

   private MethodDependency getMethodDependency(String className, MethodDescriptor descriptor) {
      Map<MethodDescriptor, MethodDependency> map = this.methodCache.computeIfAbsent(className, k -> new HashMap<>());
      MethodDependency result = map.get(descriptor);
      if (result == null) {
         MethodHolder method = this.getMethodHolder(className, descriptor);
         if (method == null || method.getDescriptor().equals(descriptor) && method.getOwnerName().equals(className)) {
            MethodReference reference = method != null ? method.getReference() : new MethodReference(className, descriptor);
            result = this.createMethodDep(reference, method);
         } else {
            result = this.getMethodDependency(method.getOwnerName(), method.getDescriptor());
         }

         map.put(descriptor, result);
      }

      return result;
   }

   private void processQueue() {
      if (!this.interrupted) {
         label44:
         while (!this.deferredTasks.isEmpty() || !this.tasks.isEmpty() || !this.pendingTransitions.isEmpty()) {
            do {
               this.processNodeToNodeTransitionQueue();
               if (this.tasks.isEmpty()) {
                  this.propagationDepth = 50;

                  while (!this.deferredTasks.isEmpty()) {
                     this.deferredTasks.remove().run();
                  }

                  this.propagationDepth = 0;
                  continue label44;
               }

               while (!this.tasks.isEmpty()) {
                  this.tasks.remove().run();
               }
            } while (this.interruptor == null || this.interruptor.shouldContinue());

            this.interrupted = true;
            return;
         }
      }
   }

   private void processNodeToNodeTransitionQueue() {
      while (!this.pendingTransitions.isEmpty()) {
         Transition transition = this.pendingTransitions.remove();
         IntSet pendingTypes = transition.pendingTypes;
         transition.pendingTypes = null;
         if (pendingTypes.size() == 1) {
            DependencyType type = this.types.get(((IntCursor)pendingTypes.iterator().next()).value);
            transition.consume(type);
         } else {
            DependencyType[] typesToPropagate = new DependencyType[pendingTypes.size()];
            int index = 0;

            for (IntCursor cursor : pendingTypes) {
               typesToPropagate[index++] = this.types.get(cursor.value);
            }

            transition.consume(typesToPropagate);
         }
      }
   }

   public void initDependencies() {
      for (DependencyListener listener : this.listeners) {
         listener.started(this.agent);
      }
   }

   public void processDependencies() {
      this.interrupted = false;
      this.processQueue();
      if (!this.interrupted) {
         this.completing = true;
         this.lock();

         for (DependencyListener listener : this.listeners) {
            listener.completing(this.agent);
         }
      }

      for (DependencyListener listener : this.listeners) {
         listener.complete();
      }

      if (dependencyReport) {
         this.reportDependencies();
      }
   }

   private void reportDependencies() {
      List<DependencyAnalyzer.ReportEntry> report = new ArrayList<>();
      int domainCount = 0;

      for (DependencyNode node : this.allNodes) {
         String tag = node.tag + "";
         if (node.typeSet != null && node.typeSet.origin == node) {
            domainCount++;
            tag = tag + "{*}";
         }

         report.add(new DependencyAnalyzer.ReportEntry(tag, node.getTypes().length));
      }

      report.sort(Comparator.comparingInt(n -> -n.count));

      for (DependencyAnalyzer.ReportEntry entry : report) {
         System.out.println(entry.title + ": " + entry.count);
      }

      System.out.println("Total nodes: " + this.allNodes.size());
      System.out.println("Total domains: " + domainCount);
   }

   public void cleanup(ClassSourcePacker classSourcePacker) {
      for (DependencyNode node : this.allNodes) {
         node.followers = null;
         node.transitions = null;
         node.transitionList = null;
         node.method = null;
      }

      for (DependencyNode node : this.allNodes) {
         if (node.typeSet != null) {
            node.typeSet.cleanup();
         }
      }

      for (Map<?, MethodDependency> map : this.methodCache.values()) {
         for (MethodDependency methodDependency : map.values()) {
            methodDependency.locationListeners = null;
            methodDependency.locations = null;
            methodDependency.cleanup();
         }
      }

      for (FieldReference fieldRef : this.fieldCache.getCachedPreimages()) {
         FieldDependency field = this.fieldCache.getKnown(fieldRef);
         if (field != null) {
            field.locationListeners = null;
            field.locations = null;
            field.cleanup();
         }
      }

      for (String className : this.classCache.getCachedPreimages()) {
         ClassDependency cls = this.classCache.getKnown(className);
         cls.cleanup();
      }

      this.allNodes.clear();
      this.agent.cleanup();
      this.listeners.clear();
      this.classSource.dispose();
      this.agentClassSource = classSourcePacker.pack(
         this.classSource, ClassClosureAnalyzer.build(this.classSource, new ArrayList<>(this.classSource.cache.keySet()))
      );
      if (this.classSource != this.agentClassSource) {
         this.classHierarchy = new ClassHierarchy(this.agentClassSource);
         this.generatedClassNames.addAll(this.classSource.getGeneratedClassNames());
      }

      this.classSource.innerHierarchy = null;
      this.classSource = null;
      this.methodReaderCache = null;
   }

   public void cleanupTypes() {
      for (MethodReference reachableMethod : this.getReachableMethods()) {
         MethodDependency dependency = this.getMethod(reachableMethod);

         for (int i = dependency.getParameterCount() + 1; i < dependency.getVariableCount(); i++) {
            dependency.variableNodes[i] = null;
         }
      }
   }

   private void lock() {
      for (MethodReference method : this.getReachableMethods()) {
         this.lock(this.getMethod(method), true);
      }

      for (FieldReference field : this.getReachableFields()) {
         this.lock(this.getField(field));
      }
   }

   private void lock(MethodDependency dep, boolean lock) {
      for (DependencyNode node : dep.variableNodes) {
         if (node != null) {
            node.locked = lock;
         }
      }

      if (dep.resultNode != null) {
         dep.resultNode.locked = lock;
      }

      if (dep.thrown != null) {
         dep.thrown.locked = lock;
      }
   }

   private void lock(FieldDependency dep) {
      dep.value.locked = true;
   }

   public <T> T getService(Class<T> type) {
      return this.services.getService(type);
   }

   public Diagnostics getDiagnostics() {
      return this.diagnostics;
   }

   @Override
   public CallGraph getCallGraph() {
      return this.callGraph;
   }

   public ExtensionEnvironmentImpl extensionEnvironment() {
      return this.extensionEnv;
   }

   public void addBootstrapMethodSubstitutor(MethodReference method, BootstrapMethodSubstitutor substitutor) {
      this.classSource.bootstrapMethodSubstitutors.put(method, substitutor);
   }

   public void addDependencyPlugin(MethodReference method, DependencyPlugin dependencyPlugin) {
      this.dependencyPlugins.put(method, dependencyPlugin);
   }

   public IncrementalDependencyProvider getIncrementalDependencies() {
      return this.incrementalCache;
   }

   DependencyTypeFilter getSuperClassFilter(ValueType superType) {
      DependencyTypeFilter result = this.superClassFilters.get(superType);
      if (result == null) {
         if (superType instanceof ValueType.Array superArray) {
            if (superArray.getItemType() instanceof ValueType.Primitive) {
               result = new ExactTypeFilter(this.getType(superType));
            } else {
               result = new SuperArrayFilter(this, this.getSuperClassFilter(superArray.getItemType()));
            }
         } else if (superType instanceof ValueType.Object superClass) {
            if (superClass.getClassName().equals("java.lang.Object")) {
               result = t -> true;
            } else {
               result = new SuperClassFilter(this, superClass.getClassName());
            }
         } else {
            result = t -> false;
         }

         this.superClassFilters.put(superType, result);
      }

      return result;
   }

   abstract boolean domainOptimizationEnabled();

   static class IncrementalCache implements IncrementalDependencyProvider, IncrementalDependencyRegistration {
      private final String[] emptyArray = new String[0];
      private Map<String, DependencyAnalyzer.IncrementalItem> classes = new HashMap<>();
      private Map<MethodReference, DependencyAnalyzer.IncrementalItem> methods = new HashMap<>();

      @Override
      public boolean isNoCache(String className) {
         DependencyAnalyzer.IncrementalItem item = this.classes.get(className);
         return item != null && item.noCache;
      }

      @Override
      public boolean isNoCache(MethodReference method) {
         DependencyAnalyzer.IncrementalItem item = this.methods.get(method);
         return item != null && item.noCache;
      }

      @Override
      public String[] getDependencies(String className) {
         DependencyAnalyzer.IncrementalItem item = this.classes.get(className);
         return item != null && item.dependencies != null ? item.dependencies.toArray(new String[0]) : this.emptyArray;
      }

      @Override
      public String[] getDependencies(MethodReference method) {
         DependencyAnalyzer.IncrementalItem item = this.methods.get(method);
         return item != null && item.dependencies != null ? item.dependencies.toArray(new String[0]) : this.emptyArray;
      }

      @Override
      public void setNoCache(String className) {
         this.classes.computeIfAbsent(className, k -> new DependencyAnalyzer.IncrementalItem()).noCache = true;
      }

      @Override
      public void setNoCache(MethodReference method) {
         this.methods.computeIfAbsent(method, k -> new DependencyAnalyzer.IncrementalItem()).noCache = true;
      }

      @Override
      public void addDependencies(String className, String... dependencies) {
         DependencyAnalyzer.IncrementalItem item = this.classes.computeIfAbsent(className, k -> new DependencyAnalyzer.IncrementalItem());
         if (item.dependencies == null) {
            item.dependencies = new LinkedHashSet<>();
         }

         item.dependencies.addAll(Arrays.asList(dependencies));
      }

      @Override
      public void addDependencies(MethodReference method, String... dependencies) {
         DependencyAnalyzer.IncrementalItem item = this.methods.computeIfAbsent(method, k -> new DependencyAnalyzer.IncrementalItem());
         if (item.dependencies == null) {
            item.dependencies = new LinkedHashSet<>();
         }

         item.dependencies.addAll(Arrays.asList(dependencies));
      }
   }

   static class IncrementalItem {
      boolean noCache;
      Set<String> dependencies;
   }

   static class ReportEntry {
      String title;
      int count;

      ReportEntry(String title, int count) {
         this.title = title;
         this.count = count;
      }
   }
}
