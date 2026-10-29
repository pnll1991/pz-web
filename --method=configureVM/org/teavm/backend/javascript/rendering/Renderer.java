package org.teavm.backend.javascript.rendering;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.IntFunction;
import org.teavm.ast.AsyncMethodNode;
import org.teavm.ast.ControlFlowEntry;
import org.teavm.ast.MethodNode;
import org.teavm.ast.RegularMethodNode;
import org.teavm.ast.analysis.LocationGraphBuilder;
import org.teavm.ast.decompilation.DecompilationException;
import org.teavm.ast.decompilation.Decompiler;
import org.teavm.backend.javascript.ExportedDeclaration;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.spi.GeneratedBy;
import org.teavm.backend.javascript.spi.Generator;
import org.teavm.backend.javascript.spi.InjectedBy;
import org.teavm.backend.javascript.templating.JavaScriptTemplateFactory;
import org.teavm.cache.AstCacheEntry;
import org.teavm.cache.AstDependencyExtractor;
import org.teavm.cache.CacheStatus;
import org.teavm.cache.MethodNodeCache;
import org.teavm.common.ServiceRepository;
import org.teavm.dependency.DependencyInfo;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderSource;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReference;
import org.teavm.model.ListableClassHolderSource;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ClassMetadataRequirements;
import org.teavm.model.util.AsyncMethodFinder;
import org.teavm.vm.RenderingException;
import org.teavm.vm.TeaVMProgressFeedback;

public class Renderer implements RenderingManager {
   public static final int SECTION_STRING_POOL = 0;
   public static final int SECTION_METADATA = 1;
   private final SourceWriter writer;
   private final ListableClassReaderSource classSource;
   private final ClassReaderSource originalClassSource;
   private final ClassLoader classLoader;
   private final Properties properties = new Properties();
   private final ServiceRepository services;
   private final Set<MethodReference> asyncMethods;
   private RenderingContext context;
   private List<Renderer.PostponedFieldInitializer> postponedFieldInitializers = new ArrayList<>();
   private IntFunction<TeaVMProgressFeedback> progressConsumer = p -> TeaVMProgressFeedback.CONTINUE;
   private MethodBodyRenderer methodBodyRenderer;
   private Map<String, Generator> generatorCache = new HashMap<>();
   private Map<MethodReference, Generator> generators;
   private MethodNodeCache astCache;
   private CacheStatus cacheStatus;
   private JavaScriptTemplateFactory templateFactory;
   private boolean threadLibraryUsed;
   private AstDependencyExtractor dependencyExtractor = new AstDependencyExtractor();
   private List<ExportedDeclaration> exports;
   private String entryPoint;
   private VariableNameGenerator variableNameGenerator;
   public static final MethodDescriptor CLINIT_METHOD = new MethodDescriptor("<clinit>", ValueType.VOID);

   public Renderer(
      SourceWriter writer,
      SourceWriter metadataWriter,
      Set<MethodReference> asyncMethods,
      RenderingContext context,
      Diagnostics diagnostics,
      Map<MethodReference, Generator> generators,
      MethodNodeCache astCache,
      CacheStatus cacheStatus,
      JavaScriptTemplateFactory templateFactory,
      List<ExportedDeclaration> exports,
      String entryPoint
   ) {
      this.writer = writer;
      this.classSource = context.getClassSource();
      this.originalClassSource = context.getInitialClassSource();
      this.classLoader = context.getClassLoader();
      this.services = context.getServices();
      this.asyncMethods = new HashSet<>(asyncMethods);
      this.context = context;
      this.variableNameGenerator = new VariableNameGenerator(context.isMinifying());
      this.methodBodyRenderer = new MethodBodyRenderer(
         context, diagnostics, context.isMinifying(), asyncMethods, writer, metadataWriter, this.variableNameGenerator
      );
      this.generators = generators;
      this.astCache = astCache;
      this.cacheStatus = cacheStatus;
      this.templateFactory = templateFactory;
      this.exports = exports;
      this.entryPoint = entryPoint;
   }

   @Override
   public SourceWriter getWriter() {
      return this.writer;
   }

   @Override
   public String getEntryPoint() {
      return this.entryPoint;
   }

   @Override
   public void exportMethod(MethodReference method, String alias) {
      this.exports.add(new ExportedDeclaration(w -> w.appendMethod(method), n -> n.methodName(method), alias));
   }

   @Override
   public void exportClass(String className, String alias) {
      this.exports.add(new ExportedDeclaration(w -> w.appendClass(className), n -> n.className(className), alias));
   }

   @Override
   public void exportFunction(String functionName, String alias) {
      this.exports.add(new ExportedDeclaration(w -> w.appendFunction(functionName), n -> n.functionName(functionName), alias));
   }

   public boolean isThreadLibraryUsed() {
      return this.threadLibraryUsed;
   }

   @Override
   public ListableClassReaderSource getClassSource() {
      return this.classSource;
   }

   @Override
   public ClassReaderSource getOriginalClassSource() {
      return this.originalClassSource;
   }

   @Override
   public ClassLoader getClassLoader() {
      return this.classLoader;
   }

   @Override
   public Properties getProperties() {
      Properties properties = new Properties();
      properties.putAll(this.properties);
      return properties;
   }

   public void setProgressConsumer(IntFunction<TeaVMProgressFeedback> progressConsumer) {
      this.progressConsumer = progressConsumer;
   }

   public void setProperties(Properties properties) {
      this.properties.clear();
      this.properties.putAll(properties);
   }

   public void renderStringPool() throws RenderingException {
      if (!this.context.getStringPool().isEmpty()) {
         this.writer.markSectionStart(0);
         this.writer.appendFunction("$rt_stringPool").append("([");

         for (int i = 0; i < this.context.getStringPool().size(); i++) {
            if (i > 0) {
               this.writer.append(',').ws();
            }

            RenderingUtil.writeString(this.writer, this.context.getStringPool().get(i));
         }

         this.writer.append("]);").newLine();
         this.writer.markSectionEnd();
      }
   }

   public void renderStringConstants() throws RenderingException {
      for (Renderer.PostponedFieldInitializer initializer : this.postponedFieldInitializers) {
         this.writer.markSectionStart(0);
         this.writer.appendStaticField(initializer.field).ws().append("=").ws();
         this.context.constantToString(this.writer, initializer.value);
         this.writer.append(";").softNewLine();
         this.writer.markSectionEnd();
      }
   }

   public void renderReflection() {
   }

   public void renderCompatibilityStubs() throws RenderingException {
      this.renderJavaStringToString();
      this.renderJavaObjectToString();
      this.renderTeaVMClass();
   }

   private void renderJavaStringToString() {
      this.writer
         .appendClass("java.lang.String")
         .append(".prototype.toString")
         .ws()
         .append("=")
         .ws()
         .append("function()")
         .ws()
         .append("{")
         .indent()
         .softNewLine();
      this.writer.append("return ").appendFunction("$rt_ustr").append("(this);").softNewLine();
      this.writer.outdent().append("};").newLine();
      this.writer
         .appendClass("java.lang.String")
         .append(".prototype.valueOf")
         .ws()
         .append("=")
         .ws()
         .appendClass("java.lang.String")
         .append(".prototype.toString;")
         .softNewLine();
   }

   private void renderJavaObjectToString() {
      this.writer
         .appendClass("java.lang.Object")
         .append(".prototype.toString")
         .ws()
         .append("=")
         .ws()
         .append("function()")
         .ws()
         .append("{")
         .indent()
         .softNewLine();
      this.writer
         .append("return ")
         .appendFunction("$rt_ustr")
         .append("(")
         .appendMethod(Object.class, "toString", String.class)
         .append("(this));")
         .softNewLine();
      this.writer.outdent().append("};").newLine();
   }

   private void renderTeaVMClass() {
      this.writer
         .appendClass("java.lang.Object")
         .append(".prototype.__teavm_class__")
         .ws()
         .append("=")
         .ws()
         .append("function()")
         .ws()
         .append("{")
         .indent()
         .softNewLine();
      this.writer.append("return ").appendFunction("$dbg_class").append("(this);").softNewLine();
      this.writer.outdent().append("};").newLine();
   }

   public boolean render(ListableClassHolderSource classes, boolean isFriendlyToDebugger) {
      ArrayList<ClassHolder> sequence = new ArrayList<>();
      HashSet<String> visited = new HashSet<>();

      for (String className : classes.getClassNames()) {
         this.orderClasses(classes, className, visited, sequence);
      }

      AsyncMethodFinder asyncFinder = new AsyncMethodFinder(this.context.getDependencyInfo().getCallGraph(), this.context.getDependencyInfo());
      asyncFinder.find(classes);
      this.asyncMethods.addAll(asyncFinder.getAsyncMethods());
      HashSet<MethodReference> splitMethods = new HashSet<>(this.asyncMethods);
      splitMethods.addAll(asyncFinder.getAsyncFamilyMethods());
      Decompiler decompiler = new Decompiler(classes, splitMethods, isFriendlyToDebugger);
      int index = 0;

      for (ClassHolder cls : sequence) {
         this.writer.markClassStart(cls.getName());
         this.renderDeclaration(cls);
         this.renderMethodBodies(cls, decompiler);
         this.writer.markClassEnd();
         if (this.progressConsumer.apply(1000 * ++index / sequence.size()) == TeaVMProgressFeedback.CANCEL) {
            return false;
         }
      }

      this.renderClassMetadata(sequence);
      return true;
   }

   private void orderClasses(ClassHolderSource classes, String className, Set<String> visited, List<ClassHolder> order) {
      if (visited.add(className)) {
         ClassHolder cls = classes.get(className);
         if (cls != null) {
            if (cls.getParent() != null) {
               this.orderClasses(classes, cls.getParent(), visited, order);
            }

            for (String iface : cls.getInterfaces()) {
               this.orderClasses(classes, iface, visited, order);
            }

            order.add(cls);
         }
      }
   }

   private void renderDeclaration(ClassHolder cls) throws RenderingException {
      List<FieldHolder> nonStaticFields = new ArrayList<>();
      List<FieldHolder> staticFields = new ArrayList<>();

      for (FieldHolder field : cls.getFields()) {
         if (field.getModifiers().contains(ElementModifier.STATIC)) {
            staticFields.add(field);
         } else {
            nonStaticFields.add(field);
         }
      }

      if (nonStaticFields.isEmpty() && !cls.getName().equals("java.lang.Object")) {
         this.renderShortClassFunctionDeclaration(cls);
      } else {
         this.renderFullClassFunctionDeclaration(cls, nonStaticFields);
      }

      for (FieldHolder field : staticFields) {
         Object value = field.getInitialValue();
         if (value == null) {
            value = getDefaultValue(field.getType());
         }

         FieldReference fieldRef = new FieldReference(cls.getName(), field.getName());
         if (value instanceof String) {
            this.context.lookupString((String)value);
            this.postponedFieldInitializers.add(new Renderer.PostponedFieldInitializer(fieldRef, (String)value));
            value = null;
         }

         this.writer.startVariableDeclaration().appendStaticField(fieldRef);
         this.context.constantToString(this.writer, value);
         this.writer.endDeclaration();
      }
   }

   private void renderFullClassFunctionDeclaration(ClassReader cls, List<FieldHolder> nonStaticFields) {
      boolean thisAliased = false;
      this.writer.startFunctionDeclaration().appendClass(cls.getName()).append("()").ws().append("{").indent().softNewLine();
      if (nonStaticFields.size() > 1) {
         thisAliased = true;
         this.writer.append("let a").ws().append("=").ws().append("this;").ws();
      }

      if (!cls.readModifiers().contains(ElementModifier.INTERFACE) && cls.getParent() != null) {
         this.writer.appendClass(cls.getParent()).append(".call(").append(thisAliased ? "a" : "this").append(");").softNewLine();
      }

      for (FieldHolder field : nonStaticFields) {
         Object value = field.getInitialValue();
         if (value == null) {
            value = getDefaultValue(field.getType());
         }

         FieldReference fieldRef = new FieldReference(cls.getName(), field.getName());
         this.writer.append(thisAliased ? "a" : "this").append(".").appendField(fieldRef).ws().append("=").ws();
         this.context.constantToString(this.writer, value);
         this.writer.append(";").softNewLine();
      }

      if (cls.getName().equals("java.lang.Object")) {
         this.writer.append("this.$id$").ws().append('=').ws().append("0;").softNewLine();
      }

      this.writer.outdent().append("}");
      this.writer.endDeclaration();
   }

   private void renderShortClassFunctionDeclaration(ClassReader cls) {
      this.writer.startVariableDeclaration().appendClass(cls.getName()).appendFunction("$rt_classWithoutFields").append("(");
      if (cls.hasModifier(ElementModifier.INTERFACE)) {
         this.writer.append("0");
      } else if (!cls.getParent().equals("java.lang.Object")) {
         this.writer.appendClass(cls.getParent());
      }

      this.writer.append(")").endDeclaration();
   }

   private void renderMethodBodies(ClassHolder cls, Decompiler decompiler) {
      this.writer.emitClass(cls.getName());
      MethodReader clinit = this.classSource.get(cls.getName()).getMethod(CLINIT_METHOD);
      if (clinit != null && this.context.isDynamicInitializer(cls.getName())) {
         this.renderCallClinit(clinit, cls);
      }

      boolean needsInitializers = !cls.hasModifier(ElementModifier.INTERFACE) && !cls.hasModifier(ElementModifier.ABSTRACT);

      for (MethodHolder method : cls.getMethods()) {
         if (this.filterMethod(method)) {
            boolean isFunction = this.context.isForcedFunction(method.getReference());
            if (isFunction) {
               this.writer.startFunctionDeclaration();
            } else {
               this.writer.startVariableDeclaration();
            }

            this.renderBody(method, decompiler, isFunction);
            this.writer.endDeclaration();
            if (needsInitializers && !method.hasModifier(ElementModifier.STATIC) && method.getName().equals("<init>")) {
               this.renderInitializer(method);
            }
         }
      }

      this.writer.emitClass(null);
   }

   private boolean filterMethod(MethodReader method) {
      if (method.hasModifier(ElementModifier.ABSTRACT)) {
         return false;
      } else {
         return method.getAnnotations().get(InjectedBy.class.getName()) != null || this.context.getInjector(method.getReference()) != null
            ? false
            : method.hasModifier(ElementModifier.NATIVE) || method.getProgram() != null;
      }
   }

   private void renderCallClinit(MethodReader clinit, ClassReader cls) {
      boolean isAsync = this.asyncMethods.contains(clinit.getReference());
      FieldReference clinitCalledField = new FieldReference(cls.getName(), "$_teavm_clinitCalled_$");
      if (isAsync) {
         this.writer.startVariableDeclaration().appendStaticField(clinitCalledField).append("false").endDeclaration();
      }

      this.writer.startVariableDeclaration().appendClassInit(cls.getName());
      this.writer.append("()").sameLineWs().append("=>").ws().append("{").softNewLine().indent();
      if (isAsync) {
         this.writer.append("let ").append(this.context.pointerName()).ws().append("=").ws().append("0").append(";").softNewLine();
         this.writer.append("if").ws().append("(").appendFunction("$rt_resuming").append("())").ws().append("{").indent().softNewLine();
         this.writer.append(this.context.pointerName()).ws().append("=").ws().appendFunction("$rt_nativeThread").append("().pop();").softNewLine();
         this.writer.outdent().append("}").ws();
         this.writer.append("else if").ws().append("(").appendStaticField(clinitCalledField).append(")").ws().append("{").indent().softNewLine();
         this.writer.append("return;").softNewLine();
         this.writer.outdent().append("}").softNewLine();
         renderAsyncPrologue(this.writer, this.context);
         this.writer.append("case 0:").indent().softNewLine();
         this.writer.appendStaticField(clinitCalledField).ws().append('=').ws().append("true;").softNewLine();
      } else {
         this.renderEraseClinit(cls);
      }

      if (isAsync) {
         this.writer.append(this.context.pointerName()).ws().append("=").ws().append("1;").softNewLine();
         this.writer.outdent().append("case 1:").indent().softNewLine();
      }

      this.writer.appendMethod(new MethodReference(cls.getName(), clinit.getDescriptor())).append("();").softNewLine();
      if (isAsync) {
         this.writer.append("if").ws().append("(").appendFunction("$rt_suspending").append("())").ws().append("{").indent().softNewLine();
         this.writer.append("break " + this.context.mainLoopName() + ";").softNewLine();
         this.writer.outdent().append("}").softNewLine();
         this.renderEraseClinit(cls);
         this.writer.append("return;").softNewLine().outdent();
         renderAsyncEpilogue(this.writer);
         this.writer.appendFunction("$rt_nativeThread").append("().push(" + this.context.pointerName() + ");").softNewLine();
      }

      this.writer.outdent().append("}").endDeclaration();
   }

   private void renderEraseClinit(ClassReader cls) {
      this.writer
         .appendClassInit(cls.getName())
         .ws()
         .append("=")
         .ws()
         .appendFunction("$rt_eraseClinit")
         .append("(")
         .appendClass(cls.getName())
         .append(");")
         .softNewLine();
   }

   private void renderClassMetadata(List<? extends ClassReader> classReaders) {
      ClassMetadataRequirements metadataRequirements = new ClassMetadataRequirements(this.context.getDependencyInfo());
      this.writer.markSectionStart(1);
      this.writer.appendFunction("$rt_packages").append("([");
      ObjectIntMap<String> packageIndexes = this.generatePackageMetadata(classReaders, metadataRequirements);
      this.writer.append("]);").newLine();

      for (int i = 0; i < classReaders.size(); i += 50) {
         int j = Math.min(i + 50, classReaders.size());
         this.renderClassMetadataPortion(classReaders.subList(i, j), packageIndexes, metadataRequirements);
      }

      this.writer.markSectionEnd();
   }

   private void renderClassMetadataPortion(
      List<? extends ClassReader> classes, ObjectIntMap<String> packageIndexes, ClassMetadataRequirements metadataRequirements
   ) {
      this.writer.appendFunction("$rt_metadata").append("([");
      boolean first = true;

      for (ClassReader cls : classes) {
         if (!first) {
            this.writer.append(',').softNewLine();
         }

         first = false;
         this.writer.emitClass(cls.getName());
         this.writer.appendClass(cls.getName()).append(",").ws();
         String className = cls.getName();
         ClassMetadataRequirements.Info requiredMetadata = metadataRequirements.getInfo(className);
         if (requiredMetadata.name()) {
            int dotIndex = className.lastIndexOf(46) + 1;
            String packageName = className.substring(0, dotIndex);
            className = className.substring(dotIndex);
            this.writer.append("\"").append(RenderingUtil.escapeString(className)).append("\"").append(",").ws();
            this.writer.append(String.valueOf(packageIndexes.getOrDefault(packageName, -1)));
         } else {
            this.writer.append("0");
         }

         this.writer.append(",").ws();
         if (cls.getParent() != null) {
            this.writer.appendClass(cls.getParent());
         } else {
            this.writer.append("0");
         }

         this.writer.append(',').ws();
         this.writer.append("[");
         ArrayList<String> interfaces = new ArrayList<>(cls.getInterfaces());

         for (int i = 0; i < interfaces.size(); i++) {
            String iface = interfaces.get(i);
            if (i > 0) {
               this.writer.append(",").ws();
            }

            this.writer.appendClass(iface);
         }

         this.writer.append("],").ws();
         int modifiers = ElementModifier.encodeModifiers(cls);
         this.writer.append(modifiers).append(',').ws();
         if (!requiredMetadata.enclosingClass() && !requiredMetadata.declaringClass() && !requiredMetadata.simpleName()) {
            this.writer.append("0");
         } else {
            this.writer.append('[');
            if (requiredMetadata.enclosingClass() && cls.getOwnerName() != null) {
               this.writer.appendClass(cls.getOwnerName());
            } else {
               this.writer.append('0');
            }

            this.writer.append(',');
            if (requiredMetadata.declaringClass() && cls.getDeclaringClassName() != null) {
               this.writer.appendClass(cls.getDeclaringClassName());
            } else {
               this.writer.append('0');
            }

            this.writer.append(',');
            if (requiredMetadata.simpleName() && cls.getSimpleName() != null) {
               this.writer.append("\"").append(RenderingUtil.escapeString(cls.getSimpleName())).append("\"");
            } else {
               this.writer.append('0');
            }

            this.writer.append(']');
         }

         this.writer.append(",").ws();
         MethodReader clinit = this.classSource.get(cls.getName()).getMethod(CLINIT_METHOD);
         if (clinit != null && this.context.isDynamicInitializer(cls.getName())) {
            this.writer.append("()").sameLineWs().append("=>").ws().appendClassInit(cls.getName()).append("()");
         } else {
            this.writer.append('0');
         }

         this.writer.append(',').ws();
         Map<MethodDescriptor, MethodReference> virtualMethods = new LinkedHashMap<>();
         this.collectMethodsToCopyFromInterfaces(this.classSource.get(cls.getName()), virtualMethods);

         for (MethodReader method : cls.getMethods()) {
            if (this.filterMethod(method) && !method.readModifiers().contains(ElementModifier.STATIC) && method.getLevel() != AccessLevel.PRIVATE) {
               virtualMethods.put(method.getDescriptor(), method.getReference());
            }
         }

         this.renderVirtualDeclarations(virtualMethods.values());
         this.writer.emitClass(null);
      }

      this.writer.append("]);").newLine();
   }

   private ObjectIntMap<String> generatePackageMetadata(List<? extends ClassReader> classes, ClassMetadataRequirements metadataRequirements) {
      Renderer.PackageNode root = new Renderer.PackageNode(null);

      for (ClassReader cls : classes) {
         ClassMetadataRequirements.Info requiredMetadata = metadataRequirements.getInfo(cls.getName());
         if (requiredMetadata.name()) {
            String className = cls.getName();
            int dotIndex = className.lastIndexOf(46);
            if (dotIndex >= 0) {
               this.addPackageName(root, className.substring(0, dotIndex));
            }
         }
      }

      ObjectIntMap<String> indexes = new ObjectIntHashMap();
      this.writePackageStructure(root, -1, "", indexes);
      this.writer.softNewLine();
      return indexes;
   }

   private int writePackageStructure(Renderer.PackageNode node, int startIndex, String prefix, ObjectIntMap<String> indexes) {
      int index = startIndex;

      for (Renderer.PackageNode child : node.children.values()) {
         if (index >= 0) {
            this.writer.append(",").ws();
         }

         this.writer.append(String.valueOf(startIndex)).append(",").ws().append("\"").append(RenderingUtil.escapeString(child.name)).append("\"");
         String fullName = prefix + child.name + ".";
         indexes.put(fullName, ++index);
         index = this.writePackageStructure(child, index, fullName, indexes);
      }

      return index;
   }

   private void addPackageName(Renderer.PackageNode node, String name) {
      String[] parts = name.split("\\.");

      for (String part : parts) {
         node = node.children.computeIfAbsent(part, Renderer.PackageNode::new);
      }
   }

   private void collectMethodsToCopyFromInterfaces(ClassReader cls, Map<MethodDescriptor, MethodReference> target) {
      Set<MethodDescriptor> implementedMethods = new HashSet<>();
      ClassReader superclass = cls;

      while (superclass != null) {
         for (MethodReader method : superclass.getMethods()) {
            if (method.getLevel() != AccessLevel.PRIVATE
               && !method.hasModifier(ElementModifier.STATIC)
               && !method.hasModifier(ElementModifier.ABSTRACT)
               && !method.getName().equals("<init>")) {
               implementedMethods.add(method.getDescriptor());
            }
         }

         superclass = superclass.getParent() != null ? this.classSource.get(superclass.getParent()) : null;
      }

      Set<String> visitedClasses = new HashSet<>();

      for (ClassReader var9 = cls; var9 != null; var9 = var9.getParent() != null ? this.classSource.get(var9.getParent()) : null) {
         for (String ifaceName : var9.getInterfaces()) {
            ClassReader iface = this.classSource.get(ifaceName);
            if (iface != null) {
               this.collectMethodsToCopyFromInterfacesImpl(iface, target, implementedMethods, visitedClasses);
            }
         }
      }
   }

   private void collectMethodsToCopyFromInterfacesImpl(
      ClassReader cls, Map<MethodDescriptor, MethodReference> target, Set<MethodDescriptor> implementedMethods, Set<String> visitedClasses
   ) {
      if (visitedClasses.add(cls.getName())) {
         for (String ifaceName : cls.getInterfaces()) {
            ClassReader iface = this.classSource.get(ifaceName);
            if (iface != null) {
               this.collectMethodsToCopyFromInterfacesImpl(iface, target, implementedMethods, visitedClasses);
            }
         }

         for (MethodReader method : cls.getMethods()) {
            if (!method.hasModifier(ElementModifier.STATIC) && !method.hasModifier(ElementModifier.ABSTRACT)) {
               MethodDescriptor descriptor = method.getDescriptor();
               if (!implementedMethods.contains(descriptor)) {
                  target.put(descriptor, method.getReference());
               }
            }
         }
      }
   }

   private static Object getDefaultValue(ValueType type) {
      if (type instanceof ValueType.Primitive primitive) {
         switch (primitive.getKind()) {
            case BOOLEAN:
               return false;
            case BYTE:
               return (byte)0;
            case SHORT:
               return (short)0;
            case INTEGER:
               return 0;
            case CHARACTER:
               return '\u0000';
            case LONG:
               return 0L;
            case FLOAT:
               return 0.0F;
            case DOUBLE:
               return 0.0;
         }
      }

      return null;
   }

   private void renderInitializer(MethodReader method) {
      MethodReference ref = method.getReference();
      this.writer.emitMethod(ref.getDescriptor());
      this.writer.startVariableDeclaration().appendInit(ref);
      if (ref.parameterCount() != 1) {
         this.writer.append("(");
      }

      for (int i = 0; i < ref.parameterCount(); i++) {
         if (i > 0) {
            this.writer.append(",").ws();
         }

         this.writer.append(this.variableNameForInitializer(i));
      }

      if (ref.parameterCount() != 1) {
         this.writer.append(")");
      }

      this.writer.sameLineWs().append("=>").ws().append("{").softNewLine().indent();
      String instanceName = this.variableNameForInitializer(ref.parameterCount());
      this.writer.append("let " + instanceName).ws().append("=").ws().append("new ").appendClass(ref.getClassName()).append("();").softNewLine();
      this.writer.appendMethod(ref).append("(" + instanceName);

      for (int i = 0; i < ref.parameterCount(); i++) {
         this.writer.append(",").ws();
         this.writer.append(this.variableNameForInitializer(i));
      }

      this.writer.append(");").softNewLine();
      this.writer.append("return " + instanceName + ";").softNewLine();
      this.writer.outdent().append("}").endDeclaration();
      this.writer.emitMethod(null);
   }

   private String variableNameForInitializer(int index) {
      return this.context.isMinifying() ? this.variableNameGenerator.minifiedVariableName(index) : "var_" + index;
   }

   private void renderVirtualDeclarations(Collection<MethodReference> methods) {
      if (methods.stream().noneMatch(this::isVirtual)) {
         this.writer.append('0');
      } else {
         this.writer.append("[");
         boolean first = true;

         for (MethodReference method : methods) {
            if (this.isVirtual(method)) {
               this.writer.emitMethod(method.getDescriptor());
               if (!first) {
                  this.writer.append(",").ws();
               }

               first = false;
               this.emitVirtualDeclaration(method);
               this.writer.emitMethod(null);
            }
         }

         this.writer.append("]");
      }
   }

   private void emitVirtualDeclaration(MethodReference ref) {
      String methodName = this.context.getNaming().instanceMethodName(ref.getDescriptor());
      this.writer.append("\"").append(methodName).append("\"");
      this.writer.append(",").ws();
      this.emitVirtualFunctionWrapper(ref);
   }

   private void emitVirtualFunctionWrapper(MethodReference method) {
      boolean forced = this.context.isForcedFunction(method);
      if (forced) {
         this.writer.appendFunction("$rt_wrapFunctionVararg").append("(").appendMethod(method).append(")");
      } else if (method.parameterCount() <= 4 && !forced) {
         this.writer.appendFunction("$rt_wrapFunction" + method.parameterCount());
         this.writer.append("(").appendMethod(method).append(")");
      } else {
         this.writer.append("function(");
         List<String> args = new ArrayList<>();

         for (int i = 1; i <= method.parameterCount(); i++) {
            args.add(this.variableNameForInitializer(i));
         }

         for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
               this.writer.append(",").ws();
            }

            this.writer.append(args.get(i));
         }

         this.writer.append(")").ws().append("{").ws();
         if (method.getDescriptor().getResultType() != ValueType.VOID) {
            this.writer.append("return ");
         }

         this.writer.appendMethod(method).append("(");
         this.writer.append("this");

         for (String arg : args) {
            this.writer.append(",").ws().append(arg);
         }

         this.writer.append(");").ws().append("}");
      }
   }

   private void renderBody(MethodHolder method, Decompiler decompiler, boolean isFunction) {
      MethodReference ref = method.getReference();
      this.writer.emitMethod(ref.getDescriptor());
      this.writer.appendMethod(ref);
      if (method.hasModifier(ElementModifier.NATIVE)) {
         this.renderNativeBody(method, this.classSource, isFunction);
      } else {
         this.renderRegularBody(method, decompiler, isFunction);
      }

      this.writer.outdent().append("}");
      this.writer.emitMethod(null);
   }

   private void renderNativeBody(MethodHolder method, ClassReaderSource classes, boolean isFunction) {
      MethodReference reference = method.getReference();
      Generator generator = this.generators.get(reference);
      if (generator == null) {
         AnnotationHolder annotHolder = method.getAnnotations().get(GeneratedBy.class.getName());
         if (annotHolder == null) {
            throw new DecompilationException(
               "Method " + method.getOwnerName() + "." + method.getDescriptor() + " is native, but no " + GeneratedBy.class.getName() + " annotation found"
            );
         }

         ValueType annotValue = annotHolder.getValues().get("value").getJavaClass();
         String generatorClassName = ((ValueType.Object)annotValue).getClassName();
         generator = this.generatorCache.computeIfAbsent(generatorClassName, name -> this.createGenerator(name, method, classes));
      }

      boolean async = this.asyncMethods.contains(reference);
      this.renderMethodPrologue(reference, method.getModifiers(), isFunction);
      this.methodBodyRenderer.renderNative(generator, async, reference);
      this.threadLibraryUsed = this.threadLibraryUsed | this.methodBodyRenderer.isThreadLibraryUsed();
   }

   private Generator createGenerator(String name, MethodHolder method, ClassReaderSource classes) {
      Class<?> generatorClass;
      try {
         generatorClass = Class.forName(name, true, this.context.getClassLoader());
      } catch (ClassNotFoundException e) {
         throw new DecompilationException(
            "Error instantiating generator " + name + " for native method " + method.getOwnerName() + "." + method.getDescriptor()
         );
      }

      Constructor<?>[] constructors = generatorClass.getConstructors();
      if (constructors.length != 1) {
         throw new DecompilationException(
            "Error instantiating generator " + name + " for native method " + method.getOwnerName() + "." + method.getDescriptor()
         );
      }

      Constructor<?> constructor = constructors[0];
      Class<?>[] parameterTypes = constructor.getParameterTypes();
      Object[] arguments = new Object[parameterTypes.length];

      for (int i = 0; i < arguments.length; i++) {
         Class<?> parameterType = parameterTypes[i];
         if (parameterType.equals(ClassReaderSource.class)) {
            arguments[i] = classes;
         } else if (parameterType.equals(Properties.class)) {
            arguments[i] = this.context.getProperties();
         } else if (parameterType.equals(DependencyInfo.class)) {
            arguments[i] = this.context.getDependencyInfo();
         } else if (parameterType.equals(ServiceRepository.class)) {
            arguments[i] = this.context.getServices();
         } else if (parameterType.equals(JavaScriptTemplateFactory.class)) {
            arguments[i] = this.templateFactory;
         } else {
            Object service = this.context.getServices().getService(parameterType);
            if (service == null) {
               throw new DecompilationException(
                  "Error instantiating generator "
                     + name
                     + " for native method "
                     + method.getOwnerName()
                     + "."
                     + method.getDescriptor()
                     + ". Its constructor requires "
                     + parameterType
                     + " as its parameter #"
                     + (i + 1)
                     + " which is not available."
               );
            }
         }
      }

      try {
         return (Generator)constructor.newInstance(arguments);
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
         throw new DecompilationException(
            "Error instantiating generator " + name + " for native method " + method.getOwnerName() + "." + method.getDescriptor(), e
         );
      }
   }

   private void renderRegularBody(MethodHolder method, Decompiler decompiler, boolean isFunction) {
      MethodReference reference = method.getReference();
      boolean async = this.asyncMethods.contains(reference);
      MethodNode node;
      if (async) {
         node = this.decompileAsync(decompiler, method);
      } else {
         AstCacheEntry entry = this.decompileRegular(decompiler, method);
         node = entry.method;
      }

      this.methodBodyRenderer.setCurrentMethod(node);
      this.renderMethodPrologue(method.getReference(), method.getModifiers(), isFunction);
      this.methodBodyRenderer.render(node, async);
      this.threadLibraryUsed = this.threadLibraryUsed | this.methodBodyRenderer.isThreadLibraryUsed();
   }

   private void renderMethodPrologue(MethodReference reference, Set<ElementModifier> modifier, boolean isFunction) {
      this.methodBodyRenderer.renderParameters(reference, modifier, isFunction);
      if (!isFunction) {
         this.writer.sameLineWs().append("=>");
      }

      this.writer.ws().append("{").indent().softNewLine();
   }

   private AstCacheEntry decompileRegular(Decompiler decompiler, MethodHolder method) {
      if (this.astCache == null) {
         return this.decompileRegularCacheMiss(decompiler, method);
      }

      AstCacheEntry entry = !this.cacheStatus.isStaleMethod(method.getReference()) ? this.astCache.get(method.getReference(), this.cacheStatus) : null;
      if (entry == null) {
         entry = this.decompileRegularCacheMiss(decompiler, method);
         RegularMethodNode finalNode = entry.method;
         this.astCache.store(method.getReference(), entry, () -> this.dependencyExtractor.extract(finalNode));
      }

      return entry;
   }

   private AstCacheEntry decompileRegularCacheMiss(Decompiler decompiler, MethodHolder method) {
      RegularMethodNode node = decompiler.decompileRegular(method);
      ControlFlowEntry[] cfg = LocationGraphBuilder.build(node.getBody());
      return new AstCacheEntry(node, cfg);
   }

   private AsyncMethodNode decompileAsync(Decompiler decompiler, MethodHolder method) {
      if (this.astCache == null) {
         return decompiler.decompileAsync(method);
      }

      AsyncMethodNode node = !this.cacheStatus.isStaleMethod(method.getReference()) ? this.astCache.getAsync(method.getReference(), this.cacheStatus) : null;
      if (node == null) {
         node = decompiler.decompileAsync(method);
         AsyncMethodNode finalNode = node;
         this.astCache.storeAsync(method.getReference(), node, () -> this.dependencyExtractor.extract(finalNode));
      }

      return node;
   }

   static void renderAsyncPrologue(SourceWriter writer, RenderingContext context) {
      writer.append(context.mainLoopName()).append(":").ws().append("while").ws().append("(true)").ws().append("{").ws();
      writer.append("switch").ws().append("(").append(context.pointerName()).append(")").ws().append('{').softNewLine();
   }

   static void renderAsyncEpilogue(SourceWriter writer) {
      writer.append("default:").ws().appendFunction("$rt_invalidPointer").append("();").softNewLine();
      writer.append("}}").softNewLine();
   }

   @Override
   public <T> T getService(Class<T> type) {
      return this.services.getService(type);
   }

   private boolean isVirtual(MethodReference method) {
      return this.context.isVirtual(method);
   }

   static class PackageNode {
      String name;
      Map<String, Renderer.PackageNode> children = new HashMap<>();

      PackageNode(String name) {
         this.name = name;
      }
   }

   private static class PostponedFieldInitializer {
      FieldReference field;
      String value;

      PostponedFieldInitializer(FieldReference field, String value) {
         this.field = field;
         this.value = value;
      }
   }
}
