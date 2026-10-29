package org.teavm.backend.c.generate;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.teavm.ast.ControlFlowEntry;
import org.teavm.ast.RegularMethodNode;
import org.teavm.ast.decompilation.Decompiler;
import org.teavm.backend.c.generators.Generator;
import org.teavm.backend.c.util.InteropUtil;
import org.teavm.backend.lowlevel.generate.ClassGeneratorUtil;
import org.teavm.cache.AstCacheEntry;
import org.teavm.cache.AstDependencyExtractor;
import org.teavm.cache.CacheStatus;
import org.teavm.cache.EmptyMethodNodeCache;
import org.teavm.cache.MethodNodeCache;
import org.teavm.interop.Address;
import org.teavm.interop.DelegateTo;
import org.teavm.interop.Import;
import org.teavm.interop.NoGcRoot;
import org.teavm.interop.Structure;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ClassMetadataRequirements;
import org.teavm.model.classes.TagRegistry;
import org.teavm.model.classes.VirtualTable;
import org.teavm.model.classes.VirtualTableEntry;
import org.teavm.model.lowlevel.CallSiteDescriptor;
import org.teavm.model.lowlevel.Characteristics;
import org.teavm.model.util.ReflectionUtil;
import org.teavm.reflection.ReflectionDependencyListener;
import org.teavm.runtime.CallSite;
import org.teavm.runtime.RuntimeArray;
import org.teavm.runtime.RuntimeClass;
import org.teavm.runtime.RuntimeObject;
import org.teavm.runtime.RuntimeReference;
import org.teavm.runtime.RuntimeReferenceQueue;
import org.teavm.runtime.reflect.ClassInfo;
import org.teavm.runtime.reflect.ClassReflectionInfo;

public class ClassGenerator {
   private static final Set<String> classesWithDeclaredStructures = new HashSet<>(
      Arrays.asList(
         "java.lang.Object",
         "java.lang.String",
         RuntimeArray.class.getName(),
         RuntimeClass.class.getName(),
         RuntimeObject.class.getName(),
         WeakReference.class.getName(),
         ReferenceQueue.class.getName(),
         RuntimeReferenceQueue.class.getName(),
         RuntimeReference.class.getName()
      )
   );
   private GenerationContext context;
   private Decompiler decompiler;
   private CacheStatus cacheStatus;
   private TagRegistry tagRegistry;
   private CodeGenerator codeGenerator;
   private FieldReference[] staticGcRoots;
   private FieldReference[] classLayout;
   private CodeWriter prologueWriter;
   private CodeWriter codeWriter;
   private CodeWriter initWriter;
   private CodeWriter headerWriter;
   private CodeWriter callSitesWriter;
   private IncludeManager includes;
   private IncludeManager headerIncludes;
   private MethodNodeCache astCache = EmptyMethodNodeCache.INSTANCE;
   private AstDependencyExtractor dependencyExtractor = new AstDependencyExtractor();
   private List<CallSiteDescriptor> callSites;
   private static final int VT_STRUCTURE_INITIALIZER_DEPTH_THRESHOLD = 9;
   private Set<ValueType> types;
   private ClassReflectionGenerator reflectionGenerator;
   private static final String TYPE_OBJECT = "TEAVM_FIELD_TYPE_OBJECT";

   public ClassGenerator(
      GenerationContext context,
      TagRegistry tagRegistry,
      Decompiler decompiler,
      CacheStatus cacheStatus,
      Set<ValueType> types,
      ReflectionDependencyListener reflection,
      MethodConvertersGenerator methodConvertersGenerator
   ) {
      this.context = context;
      this.tagRegistry = tagRegistry;
      this.decompiler = decompiler;
      this.cacheStatus = cacheStatus;
      this.types = types;
      this.reflectionGenerator = new ClassReflectionGenerator(context, reflection, types, methodConvertersGenerator);
   }

   public void setAstCache(MethodNodeCache astCache) {
      this.astCache = astCache;
   }

   public void setCallSites(List<CallSiteDescriptor> callSites) {
      this.callSites = callSites;
   }

   public void generateAnnotationDataClass(CodeWriter writer, String className, String fileName) {
      writer.println("#pragma once");
      this.includes = new SimpleIncludeManager(this.context.getFileNames(), writer);
      this.includes.init(fileName + ".c");
      ClassReader cls = this.context.getClassSource().get(className.substring(0, className.length() - "$$_nativeData".length()));
      List<MethodReader> fields = getAnnotationDataFields(this.context, cls);
      if (fields.isEmpty()) {
         writer.print("typedef void ").print(this.context.getNames().forClass(className)).println(";");
      } else {
         writer.println("typedef struct {").indent();

         for (MethodReader method : fields) {
            this.includes.includePath("reflection.h");
            String fieldName = this.context.getNames().forMemberField(new FieldReference(cls.getName(), method.getName()));
            generateAnnotationFieldType(this.context, this.includes, writer, method.getResultType());
            writer.print(" ").print(fieldName).println(";");
         }

         writer.outdent().print("} ").print(this.context.getNames().forClass(className)).println(";");
      }

      this.includes = null;
   }

   static List<MethodReader> getAnnotationDataFields(GenerationContext context, ClassReader cls) {
      ArrayList<MethodReader> result = new ArrayList<>();
      if (cls != null) {
         for (MethodReader method : cls.getMethods()) {
            if (context.getDependencies().getMethod(method.getReference()) != null) {
               result.add(method);
            }
         }
      }

      return result;
   }

   static void generateAnnotationFieldType(GenerationContext context, IncludeManager includes, CodeWriter writer, ValueType type) {
      if (type instanceof ValueType.Array) {
         ValueType itemType = ((ValueType.Array)type).getItemType();
         if (itemType instanceof ValueType.Primitive) {
            includes.includePath("reflection.h");
            switch (((ValueType.Primitive)itemType).getKind()) {
               case BOOLEAN:
               case BYTE:
                  writer.print("TeaVM_ByteArray*");
                  break;
               case SHORT:
                  writer.print("TeaVM_ShortArray*");
                  break;
               case CHARACTER:
                  writer.print("TeaVM_CharArray*");
                  break;
               case INTEGER:
                  writer.print("TeaVM_IntArray*");
                  break;
               case LONG:
                  writer.print("TeaVM_LongArray*");
                  break;
               case FLOAT:
                  writer.print("TeaVM_FloatArray*");
                  break;
               case DOUBLE:
                  writer.print("TeaVM_DoubleArray*");
            }
         } else if (itemType instanceof ValueType.Object) {
            includes.includePath("reflection.h");
            String name = ((ValueType.Object)itemType).getClassName();
            if (name.equals("java.lang.Class")) {
               writer.print("TeaVM_ClassArray*");
               return;
            }

            ClassReader cls = context.getClassSource().get(name);
            if (cls != null && cls.hasModifier(ElementModifier.ENUM)) {
               writer.print("TeaVM_ShortArray*");
               return;
            }

            writer.print("TeaVM_RefArray*");
         }
      } else if (type instanceof ValueType.Primitive) {
         includes.addInclude("<stdint.h>");
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
            case BYTE:
               writer.print("int8_t");
               break;
            case SHORT:
               writer.print("int16_t");
               break;
            case CHARACTER:
               writer.print("uint16_t");
               break;
            case INTEGER:
               writer.print("int32_t");
               break;
            case LONG:
               writer.print("int64_t");
               break;
            case FLOAT:
               writer.print("float");
               break;
            case DOUBLE:
               writer.print("double");
         }
      } else if (type instanceof ValueType.Object) {
         String name = ((ValueType.Object)type).getClassName();
         if (name.equals("java.lang.Class")) {
            includes.includePath("reflection.h");
            writer.print("TeaVM_ClassPtr");
            return;
         }

         ClassReader cls = context.getClassSource().get(name);
         if (cls != null && cls.hasModifier(ElementModifier.ENUM)) {
            includes.addInclude("<stdint.h>");
            writer.print("int16_t");
            return;
         }

         writer.print("void*");
      }
   }

   public void generateClass(CodeWriter writer, CodeWriter headerWriter, ClassHolder cls) {
      ValueType type = ValueType.object(cls.getName());
      this.init(writer, headerWriter, this.context.getFileNames().fileName(cls.getName()), type);
      this.generateStringPoolDecl(type);
      this.generateClassStructure(cls);
      this.generateClassStaticFields(cls);
      this.generateClassMethods(cls);
      this.generateInitializer(cls);
      this.generateVirtualTable(ValueType.object(cls.getName()));
      this.generateStaticGCRoots(cls.getName());
      this.generateLayoutArray(cls.getName());
      this.generateStringPool(type);
   }

   private void generateCallSites(List<? extends CallSiteDescriptor> callSites, String callSitesName) {
      CallSiteGenerator generator = new CallSiteGenerator(this.context, this.callSitesWriter, this.includes, callSitesName);
      generator.setStatic(true);
      generator.generate(callSites);
   }

   public void generateType(CodeWriter writer, CodeWriter headerWriter, ValueType type) {
      this.init(writer, headerWriter, this.context.getFileNames().fileName(type), type);
      this.generateStringPoolDecl(type);
      this.includes.includeType(type);
      this.generateVirtualTable(type);
      this.generateStringPool(type);
   }

   private void init(CodeWriter writer, CodeWriter headerWriter, String fileName, ValueType type) {
      this.staticGcRoots = null;
      this.classLayout = null;
      this.includes = new SimpleIncludeManager(this.context.getFileNames(), writer);
      this.includes.init(fileName + ".c");
      this.prologueWriter = writer.fragment();
      this.codeWriter = writer.fragment();
      this.headerWriter = headerWriter;
      headerWriter.println("#pragma once");
      this.headerIncludes = new SimpleIncludeManager(this.context.getFileNames(), headerWriter);
      this.headerIncludes.init(fileName + ".h");
      this.headerIncludes.includePath("runtime.h");
      String currentClassName = type instanceof ValueType.Object ? ((ValueType.Object)type).getClassName() : null;
      String sysInitializerName = this.context.getNames().forClassSystemInitializer(type);
      headerWriter.println("extern void " + sysInitializerName + "();");
      writer.println("void " + sysInitializerName + "() {").indent();
      this.initWriter = writer.fragment();
      writer.outdent().println("}");
      this.includes.includeType(type);
      ClassGenerationContext classContext = new ClassGenerationContext(this.context, this.includes, this.prologueWriter, this.initWriter, currentClassName);
      this.codeGenerator = new CodeGenerator(classContext, this.codeWriter, this.includes);
      if (!this.context.isIncremental()) {
         this.codeGenerator.setCallSites(this.callSites);
      }
   }

   private void generateStringPoolDecl(ValueType type) {
      if (this.context.isIncremental()) {
         String poolName = "strings_" + this.context.getNames().forClassInstance(type);
         this.codeWriter.println("TeaVM_String* " + poolName + "[];");
         this.codeWriter.println("#ifdef TEAVM_GET_STRING");
         this.codeWriter.println("#undef TEAVM_GET_STRING");
         this.codeWriter.println("#endif");
         this.codeWriter.println("#define TEAVM_GET_STRING(i) " + poolName + "[i]");
      }
   }

   private void generateStringPool(ValueType type) {
      if (this.context.isIncremental() && !this.context.getStringPool().getStrings().isEmpty()) {
         this.codeWriter.println("#undef TEAVM_GET_STRING");
         String poolName = "strings_" + this.context.getNames().forClassInstance(type);
         StringPoolGenerator poolGenerator = new StringPoolGenerator(this.context, poolName);
         this.includes.includePath("stringhash.h");
         poolGenerator.generate(this.codeWriter);
         poolGenerator.generateStringPoolHeaders(this.initWriter, this.includes);
      }
   }

   private void generateClassMethods(ClassHolder cls) {
      boolean needsVirtualTable = needsVirtualTable(this.context.getCharacteristics(), ValueType.object(cls.getName()));

      for (MethodHolder method : cls.getMethods()) {
         if (!method.hasModifier(ElementModifier.ABSTRACT)) {
            if (method.hasModifier(ElementModifier.NATIVE)) {
               if (!this.tryDelegateToMethod(cls, method) && !this.tryUsingGenerator(method)) {
                  if (this.context.getIntrinsic(method.getReference()) == null && method.getAnnotations().get(Import.class.getName()) == null) {
                     this.context
                        .getDiagnostics()
                        .error(
                           new CallLocation(method.getReference()),
                           "Method {{m0}} is native but has no {{c1}} annotation on it",
                           method.getReference(),
                           Import.class.getName()
                        );
                  }
               } else if (needsVirtualTable) {
                  this.addToVirtualTable(method);
               }
            } else if (method.getProgram() != null) {
               if (needsVirtualTable) {
                  this.addToVirtualTable(method);
               }

               if (this.context.isIncremental()) {
                  this.callSitesWriter = this.codeWriter.fragment();
               }

               this.generateMethodForwardDeclaration(method);
               AstCacheEntry entry = !this.cacheStatus.isStaleMethod(method.getReference()) ? this.astCache.get(method.getReference(), this.cacheStatus) : null;
               RegularMethodNode methodNode;
               if (entry == null) {
                  methodNode = this.decompiler.decompileRegular(method);
                  this.astCache
                     .store(method.getReference(), new AstCacheEntry(methodNode, new ControlFlowEntry[0]), () -> this.dependencyExtractor.extract(methodNode));
               } else {
                  methodNode = entry.method;
               }

               List<CallSiteDescriptor> callSites = null;
               if (this.context.isIncremental()) {
                  callSites = new ArrayList<>();
                  this.codeGenerator.setCallSites(callSites);
               }

               this.codeGenerator.generateMethod(methodNode);
               if (this.context.isIncremental()) {
                  this.generateCallSites(method.getReference(), callSites);
                  this.codeWriter.println("#undef TEAVM_ALLOC_STACK");
               }
            }
         }
      }
   }

   private void addToVirtualTable(MethodReader method) {
      if (this.context.isIncremental()) {
         if (!method.hasModifier(ElementModifier.STATIC) && method.getLevel() != AccessLevel.PRIVATE && !method.getName().equals("<init>")) {
            String className = this.context.getNames().forClassInstance(ValueType.object(method.getOwnerName()));
            String idVar = this.codeGenerator.getClassContext().getVirtualMethodId(method.getDescriptor());
            this.initWriter
               .println("teavm_vc_registerMethod(&" + className + ", " + idVar + ", &" + this.context.getNames().forMethod(method.getReference()) + ");");
         }
      }
   }

   private void generateMethodForwardDeclaration(MethodHolder method) {
      if (this.context.isIncremental()) {
         this.codeGenerator.getClassContext().importMethod(method.getReference(), method.hasModifier(ElementModifier.STATIC));
      } else {
         boolean isStatic = method.hasModifier(ElementModifier.STATIC);
         this.headerWriter.print("extern ");
         CodeGenerator.generateMethodSignature(this.headerWriter, this.context.getNames(), method.getReference(), isStatic, false);
         this.headerWriter.println(";");
      }
   }

   private void generateCallSites(MethodReference method, List<? extends CallSiteDescriptor> callSites) {
      String callSitesName;
      if (!callSites.isEmpty()) {
         callSitesName = "callsites_" + this.context.getNames().forMethod(method);
         this.includes.includeClass(CallSite.class.getName());
         this.generateCallSites(callSites, callSitesName);
      } else {
         callSitesName = "NULL";
      }

      this.callSitesWriter.println("#define TEAVM_ALLOC_STACK(size) TEAVM_ALLOC_STACK_DEF(size, " + callSitesName + ")");
   }

   private void generateInitializer(ClassHolder cls) {
      if (this.needsInitializer(cls)) {
         String initializerName = this.context.getNames().forClassInitializer(cls.getName());
         this.headerWriter.print("extern void ").print(initializerName).println("();");
         this.codeWriter.print("void ").print(initializerName).println("() {").indent();
         String classInstanceName = this.context.getNames().forClassInstance(ValueType.object(cls.getName()));
         String clinitName = this.context.getNames().forMethod(new MethodReference(cls.getName(), "<clinit>", ValueType.VOID));
         this.codeWriter.print("TeaVM_Class* cls = (TeaVM_Class*) &").print(classInstanceName).println(";");
         this.codeWriter.println("if (!(cls->flags & INT32_C(1))) {").indent();
         this.codeWriter.println("cls->flags |= INT32_C(1);");
         this.codeWriter.print(clinitName).println("();");
         this.codeWriter.outdent().println("}");
         this.codeWriter.outdent().println("}");
      }
   }

   private void generateClassStructure(ClassHolder cls) {
      if (this.needsData(cls)) {
         String name = this.context.getNames().forClass(cls.getName());
         boolean writeNeeded = !classesWithDeclaredStructures.contains(cls.getName());
         if (writeNeeded) {
            this.headerWriter.print("typedef struct ").print(name).println(" {").indent();
         }

         if (cls.getParent() == null || !cls.getParent().equals(Structure.class.getName())) {
            String parentName = cls.getParent();
            if (parentName == null) {
               parentName = RuntimeObject.class.getName();
            }

            if (writeNeeded) {
               this.headerIncludes.includeClass(parentName);
               this.headerWriter.print("struct ").print(this.context.getNames().forClass(parentName)).println(" parent;");
            }

            this.includes.includeClass(parentName);
         }

         FieldReference[] instanceFields = new FieldReference[cls.getFields().size()];
         int instanceIndex = 0;

         for (FieldHolder field : cls.getFields()) {
            if (!field.hasModifier(ElementModifier.STATIC) && !this.isMonitorField(field.getReference())) {
               String fieldName = this.context.getNames().forMemberField(field.getReference());
               if (writeNeeded) {
                  this.headerWriter.printStrictType(field.getType()).print(" ").print(fieldName).println(";");
               }

               if (this.isReferenceType(field.getType()) && !this.isBufferNextField(field.getReference())) {
                  instanceFields[instanceIndex++] = field.getReference();
               }
            }
         }

         if (instanceIndex > 0) {
            this.classLayout = Arrays.copyOf(instanceFields, instanceIndex);
         }

         if (writeNeeded) {
            this.headerWriter.outdent().print("} ").print(name).println(";");
         }
      }
   }

   private boolean isMonitorField(FieldReference field) {
      return field.getClassName().equals("java.lang.Object") && field.getFieldName().equals("monitor");
   }

   private boolean isBufferNextField(FieldReference field) {
      return field.getClassName().equals("java.nio.Buffer") && field.getFieldName().equals("nextRef");
   }

   private void generateClassStaticFields(ClassHolder cls) {
      CodeWriter fieldsWriter = this.codeWriter.fragment();
      FieldReference[] staticFields = new FieldReference[cls.getFields().size()];
      int staticIndex = 0;

      for (FieldHolder field : cls.getFields()) {
         if (field.hasModifier(ElementModifier.STATIC)) {
            String fieldName = this.context.getNames().forStaticField(field.getReference());
            this.headerWriter.print("extern ").printStrictType(field.getType()).print(" ").print(fieldName).println(";");
            fieldsWriter.printStrictType(field.getType()).print(" ").print(fieldName).println(";");
            if (this.isReferenceType(field.getType()) && field.getAnnotations().get(NoGcRoot.class.getName()) == null) {
               staticFields[staticIndex++] = field.getReference();
            }

            Object initialValue = field.getInitialValue();
            if (initialValue == null) {
               initialValue = getDefaultValue(field.getType());
            }

            this.initWriter.print(fieldName + " = ");
            CodeGeneratorUtil.writeValue(this.initWriter, this.context, this.includes, initialValue);
            this.initWriter.println(";");
         }
      }

      if (staticIndex > 0) {
         this.staticGcRoots = Arrays.copyOf(staticFields, staticIndex);
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
            case CHARACTER:
               return '\u0000';
            case INTEGER:
               return 0;
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

   private void generateVirtualTable(ValueType type) {
      if (needsVirtualTable(this.context.getCharacteristics(), type)) {
         this.generateIsSupertypeFunction(type);
         String className = null;
         if (type instanceof ValueType.Object) {
            className = ((ValueType.Object)type).getClassName();
            if (!this.context.isIncremental()) {
               this.generateVirtualTableStructure(className);
            }
         } else if (type instanceof ValueType.Array) {
            className = "java.lang.Object";
         }

         ClassReader cls = className != null ? this.context.getClassSource().get(className) : null;
         String structName;
         if (this.context.isIncremental()) {
            structName = className != null ? "TeaVM_DynamicClass" : "TeaVM_Class";
         } else {
            structName = className == null || cls != null && cls.hasModifier(ElementModifier.INTERFACE)
               ? "TeaVM_Class"
               : this.context.getNames().forClassClass(className);
         }

         if (className != null && !this.context.isIncremental()) {
            this.headerIncludes.includeClass(className);
         }

         String name = this.context.getNames().forClassInstance(type);
         String enumConstants;
         if (cls != null && cls.hasModifier(ElementModifier.ENUM)) {
            enumConstants = this.writeEnumConstants(cls, name);
         } else {
            enumConstants = this.getBufferFieldInitializer(cls);
            if (enumConstants == null) {
               enumConstants = "NULL";
            }
         }

         this.headerWriter.print("extern ").print(structName).print(" ").print(name).println(";");
         if (this.classLayout != null) {
            this.codeWriter.println("static int16_t teavm_classLayouts_" + name + "[" + (this.classLayout.length + 1) + "];");
         }

         this.codeWriter.print("alignas(8) ").print(structName).print(" ").print(name);
         if (className != null) {
            if (this.context.isIncremental()) {
               this.generateDynamicVirtualTable(name, type, enumConstants);
            } else {
               VirtualTable virtualTable = this.context.getVirtualTableProvider().lookup(className);
               if (cls.hasModifier(ElementModifier.INTERFACE)) {
                  this.codeWriter.println(" = {").indent();
                  this.generateRuntimeClassInitializer(type, enumConstants, false, 0);
                  this.codeWriter.outdent().print("}");
               } else if (virtualTable != null) {
                  boolean tooDeep = this.getInheritanceDepth(className) > 9;
                  if (tooDeep) {
                     this.initWriter.print(structName).print("* vt_0 = &").print(name).println(";");
                  } else {
                     this.codeWriter.println(" = {").indent();
                  }

                  this.generateVirtualTableContent(virtualTable, virtualTable, type, enumConstants, tooDeep, 0);
                  if (!tooDeep) {
                     this.codeWriter.outdent().print("}");
                  }
               } else {
                  this.codeWriter.println(" = {").indent();
                  this.codeWriter.println(".parent = {").indent();
                  this.generateRuntimeClassInitializer(type, enumConstants, false, 0);
                  this.codeWriter.outdent().println("}");
                  this.codeWriter.outdent().println("}");
               }
            }
         } else {
            this.codeWriter.println(" = {").indent();
            this.generateRuntimeClassInitializer(type, enumConstants, false, 0);
            this.codeWriter.outdent().println("}");
         }

         this.codeWriter.outdent().println(";");
      }
   }

   private String getBufferFieldInitializer(ClassReader cls) {
      if (cls == null) {
         return null;
      }

      for (FieldReader field : cls.getFields()) {
         if (ClassGeneratorUtil.isBufferObjectField(field)) {
            String structName = this.context.getNames().forClass(cls.getName());
            String fieldName = this.context.getNames().forMemberField(field.getReference());
            return "(void*) offsetof(" + structName + ", " + fieldName + ")";
         }
      }

      return null;
   }

   private int getInheritanceDepth(String className) {
      int depth = 0;

      while (true) {
         depth++;
         ClassReader cls = this.context.getClassSource().get(className);
         if (cls.getParent() == null) {
            return depth;
         }

         className = cls.getParent();
      }
   }

   private void generateDynamicVirtualTable(String name, ValueType type, String enumConstants) {
      this.codeWriter.println(".parent = {").indent();
      this.generateRuntimeClassInitializer(type, enumConstants, false, 0);
      this.codeWriter.outdent().println("}");
      String[] parentClasses;
      if (type instanceof ValueType.Object) {
         String className = ((ValueType.Object)type).getClassName();
         ClassReader cls = this.context.getClassSource().get(className);
         if (cls != null) {
            int count = cls.getInterfaces().size();
            if (cls.getParent() != null) {
               count++;
            }

            parentClasses = new String[count];
            cls.getInterfaces().toArray(parentClasses);
            if (cls.getParent() != null) {
               parentClasses[count - 1] = cls.getParent();
            }
         } else {
            parentClasses = new String[0];
         }
      } else {
         parentClasses = new String[]{"java.lang.Object"};
      }

      for (String parentClass : parentClasses) {
         this.includes.includeClass(parentClass);
         String parentClassName = this.context.getNames().forClassInstance(ValueType.object(parentClass));
         this.initWriter.println("teavm_vc_copyMethods(&" + parentClassName + ", &" + name + ");");
      }
   }

   private void generateVirtualTableContent(VirtualTable current, VirtualTable original, ValueType type, String enumConstants, boolean initMethod, int depth) {
      if (!initMethod) {
         this.codeWriter.println(".parent = {").indent();
      } else {
         String parentStructName = current.getParent() != null ? this.context.getNames().forClassClass(current.getParent().getClassName()) : "TeaVM_Class";
         this.initWriter
            .print(parentStructName)
            .print("* vt_")
            .print(String.valueOf(depth + 1))
            .print(" = (")
            .print(parentStructName)
            .print("*) vt_")
            .print(String.valueOf(depth))
            .println(";");
      }

      if (current.getParent() == null) {
         this.generateRuntimeClassInitializer(type, enumConstants, initMethod, depth + 1);
      } else {
         this.generateVirtualTableContent(current.getParent(), original, type, enumConstants, initMethod, depth + 1);
      }

      if (!initMethod) {
         this.codeWriter.outdent().print("}");
      }

      for (MethodDescriptor method : current.getMethods()) {
         if (method != null) {
            VirtualTableEntry entry = original.getEntry(method);
            if (entry != null) {
               if (!initMethod) {
                  this.codeWriter.println(",");
               }

               String methodName = this.context.getNames().forVirtualMethod(method);
               String implName = "&" + this.context.getNames().forMethod(entry.getImplementor());
               this.includes.includeClass(entry.getImplementor().getClassName());
               if (initMethod) {
                  this.initWriter.print("vt_").print(String.valueOf(depth)).print("->").print(methodName).print(" = ").print(implName).println(";");
               } else {
                  this.codeWriter.print(".").print(methodName).print(" = ").print(implName);
               }
            }
         }
      }

      if (!initMethod) {
         this.codeWriter.println();
      }
   }

   private String writeEnumConstants(ClassReader cls, String baseName) {
      List<FieldReader> fields = cls.getFields().stream().filter(f -> f.hasModifier(ElementModifier.ENUM)).collect(Collectors.toList());
      String name = baseName + "_enumConstants";
      this.codeWriter.print("static void* " + name + "[" + (fields.size() + 1) + "] = { ");
      this.codeWriter.print("(void*) (intptr_t) " + fields.size());

      for (FieldReader field : fields) {
         this.codeWriter.print(", ").print("&" + this.context.getNames().forStaticField(field.getReference()));
      }

      this.codeWriter.println(" };");
      return name;
   }

   private void generateRuntimeClassInitializer(ValueType type, String enumConstants, boolean initMethod, int depth) {
      int flags = 0;
      int modifiers = 0;
      String layout = "NULL";
      String initFunction = "NULL";
      String superinterfaceCount = "0";
      String superinterfaces = "NULL";
      String simpleName = null;
      String declaringClass = "NULL";
      String enclosingClass = "NULL";
      ClassMetadataRequirements.Info metadataReq = this.context.getMetadataRequirements().getInfo(type);
      String sizeExpr;
      int tag;
      String parent;
      String itemTypeExpr;
      if (type instanceof ValueType.Object) {
         String className = ((ValueType.Object)type).getClassName();
         ClassReader cls = this.context.getClassSource().get(className);
         if (className.equals(Object.class.getName())) {
            className = RuntimeObject.class.getName();
         }

         if (cls != null && this.needsData(cls)) {
            String structName = this.context.getNames().forClass(className);
            sizeExpr = "(int32_t) (intptr_t) TEAVM_ALIGN(sizeof(" + structName + "), sizeof(void*))";
         } else {
            sizeExpr = "0";
         }

         if (cls != null) {
            modifiers = ElementModifier.encodeModifiers(cls);
         }

         List<TagRegistry.Range> ranges = this.tagRegistry != null ? this.tagRegistry.getRanges(className) : null;
         tag = !this.context.isIncremental() && ranges != null && !ranges.isEmpty() ? ranges.get(0).lower : 0;
         if (cls != null && cls.getParent() != null && !cls.hasModifier(ElementModifier.INTERFACE)) {
            this.includes.includeClass(cls.getParent());
            parent = "(TeaVM_Class*) &" + this.context.getNames().forClassInstance(ValueType.object(cls.getParent()));
         } else {
            parent = "NULL";
         }

         itemTypeExpr = "NULL";
         layout = this.classLayout != null ? "teavm_classLayouts_" + this.context.getNames().forClassInstance(type) : "NULL";
         if (cls != null && this.needsInitializer(cls) && metadataReq.classInit()) {
            initFunction = this.context.getNames().forClassInitializer(className);
         }

         Set<String> interfaces = cls != null ? cls.getInterfaces() : Collections.emptySet();
         if (!interfaces.isEmpty() && metadataReq.interfaces()) {
            superinterfaceCount = Integer.toString(cls.getInterfaces().size());
            StringBuilder sb = new StringBuilder("(TeaVM_Class*[]) { ");
            boolean first = true;

            for (String itf : interfaces) {
               if (!first) {
                  sb.append(", ");
               }

               first = false;
               this.includes.includeClass(itf);
               sb.append("(TeaVM_Class*) &").append(this.context.getNames().forClassInstance(ValueType.object(itf)));
            }

            superinterfaces = sb.append(" }").toString();
         }

         flags = ClassGeneratorUtil.contributeToFlags(cls, flags);
         if (cls != null) {
            simpleName = metadataReq.simpleName() ? cls.getSimpleName() : null;
            if (metadataReq.declaringClass()
               && cls.getDeclaringClassName() != null
               && this.context.getDependencies().getClass(cls.getDeclaringClassName()) != null) {
               declaringClass = "(TeaVM_Class*) &" + this.context.getNames().forClassInstance(ValueType.object(cls.getDeclaringClassName()));
               this.includes.includeClass(cls.getDeclaringClassName());
            }

            if (metadataReq.enclosingClass() && cls.getOwnerName() != null && this.context.getDependencies().getClass(cls.getOwnerName()) != null) {
               enclosingClass = "(TeaVM_Class*) &" + this.context.getNames().forClassInstance(ValueType.object(cls.getOwnerName()));
               this.includes.includeClass(cls.getOwnerName());
            }
         }
      } else if (type instanceof ValueType.Array) {
         this.includes.includeClass("java.lang.Object");
         parent = "(TeaVM_Class*) &" + this.context.getNames().forClassInstance(ValueType.object("java.lang.Object"));
         tag = !this.context.isIncremental() ? this.tagRegistry.getRanges("java.lang.Object").get(0).lower : 0;
         ValueType itemType = ((ValueType.Array)type).getItemType();
         sizeExpr = "sizeof(" + CodeWriter.strictTypeAsString(itemType) + ")";
         this.includes.includeType(itemType);
         itemTypeExpr = "(TeaVM_Class*) &" + this.context.getNames().forClassInstance(itemType);
      } else if (type == ValueType.VOID) {
         parent = "NULL";
         tag = 0;
         sizeExpr = "0";
         itemTypeExpr = "NULL";
         flags = ClassGeneratorUtil.applyPrimitiveFlags(flags, type);
      } else {
         parent = "NULL";
         tag = Integer.MAX_VALUE;
         sizeExpr = "sizeof(" + CodeWriter.strictTypeAsString(type) + ")";
         flags = ClassGeneratorUtil.applyPrimitiveFlags(flags, type);
         itemTypeExpr = "NULL";
      }

      String metadataName = this.nameOfType(type);
      String nameRef = metadataName != null
         ? "(TeaVM_Object**) TEAVM_GET_STRING_ADDRESS(" + this.context.getStringPool().getStringIndex(metadataName) + ")"
         : "NULL";
      String superTypeFunction = this.context.getNames().forSupertypeFunction(type);
      ValueType arrayType = ValueType.arrayOf(type);
      String arrayTypeExpr;
      if (type instanceof ValueType.Primitive && this.types.contains(arrayType)) {
         this.includes.includeType(arrayType);
         arrayTypeExpr = "(TeaVM_Class*) &" + this.context.getNames().forClassInstance(arrayType);
      } else {
         arrayTypeExpr = "NULL";
      }

      if (simpleName == null) {
         simpleName = "NULL";
      } else {
         int simpleNameIndex = this.context.getStringPool().getStringIndex(simpleName);
         simpleName = "(TeaVM_Object**) TEAVM_GET_STRING_ADDRESS(" + simpleNameIndex + ")";
      }

      this.includes.includePath("strings.h");
      List<ClassGenerator.FieldInitializer> initializers = new ArrayList<>();
      initializers.add(new ClassGenerator.FieldInitializer("size", sizeExpr));
      initializers.add(new ClassGenerator.FieldInitializer("flags", String.valueOf(flags)));
      initializers.add(new ClassGenerator.FieldInitializer("tag", String.valueOf(tag)));
      initializers.add(new ClassGenerator.FieldInitializer("modifiers", String.valueOf(modifiers)));
      if (this.context.getMetadataRequirements().hasName()) {
         initializers.add(new ClassGenerator.FieldInitializer("name", nameRef));
      }

      if (this.context.getMetadataRequirements().hasSimpleName()) {
         initializers.add(new ClassGenerator.FieldInitializer("simpleName", simpleName));
      }

      initializers.add(new ClassGenerator.FieldInitializer("arrayType", arrayTypeExpr));
      initializers.add(new ClassGenerator.FieldInitializer("itemType", itemTypeExpr));
      initializers.add(new ClassGenerator.FieldInitializer("isSupertypeOf", "&" + superTypeFunction));
      initializers.add(new ClassGenerator.FieldInitializer("superclass", parent));
      if (this.context.getMetadataRequirements().hasGetInterfaces()) {
         initializers.add(new ClassGenerator.FieldInitializer("superinterfaceCount", superinterfaceCount));
         initializers.add(new ClassGenerator.FieldInitializer("superinterfaces", superinterfaces));
      }

      initializers.add(new ClassGenerator.FieldInitializer("layout", layout));
      initializers.add(new ClassGenerator.FieldInitializer("enumValues", enumConstants));
      if (this.context.getMetadataRequirements().hasDeclaringClass()) {
         initializers.add(new ClassGenerator.FieldInitializer("declaringClass", declaringClass));
      }

      if (this.context.getMetadataRequirements().hasEnclosingClass()) {
         initializers.add(new ClassGenerator.FieldInitializer("enclosingClass", enclosingClass));
      }

      if (this.context.getMetadataRequirements().hasClassInit()) {
         initializers.add(new ClassGenerator.FieldInitializer("init", initFunction));
      }

      if (this.context.getDependencies().getMethod(new MethodReference(ClassInfo.class, "initializeNewInstance", Object.class, boolean.class)) != null
         && type instanceof ValueType.Object objClass) {
         ClassReader cls = this.context.getClassSource().get(objClass.getClassName());
         if (cls != null) {
            MethodReader defaultConstructor = cls.getMethod(new MethodDescriptor("<init>", void.class));
            if (defaultConstructor != null && defaultConstructor.getLevel() == AccessLevel.PUBLIC && defaultConstructor.getProgram() != null) {
               String ctorName = this.context.getNames().forMethod(defaultConstructor.getReference());
               initializers.add(new ClassGenerator.FieldInitializer("initNewInstance", "&" + ctorName));
            }
         }
      }

      boolean reflectionGeneratorStarted = false;
      if (this.context.getDependencies().getMethod(new MethodReference(ClassInfo.class, "reflection", ClassReflectionInfo.class)) != null) {
         reflectionGeneratorStarted = true;
         this.reflectionGenerator.prepare(this.codeWriter, this.includes);
         if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object)type).getClassName();
            ClassReader cls = this.context.getClassSource().get(className);
            if (cls != null) {
               initializers.add(new ClassGenerator.FieldInitializer("reflection", w -> this.reflectionGenerator.generate(w, this.includes, cls)));
            }
         }
      }

      if (initMethod) {
         for (ClassGenerator.FieldInitializer initializer : initializers) {
            this.initWriter.print("vt_").print(String.valueOf(depth)).print("->").print(initializer.name).print(" = ");
            initializer.value.accept(this.initWriter);
            this.initWriter.println(";");
         }
      } else {
         for (int i = 0; i < initializers.size(); i++) {
            if (i > 0) {
               this.codeWriter.println(",");
            }

            ClassGenerator.FieldInitializer initializer = initializers.get(i);
            this.codeWriter.print(".").print(initializer.name).print(" = ");
            initializer.value.accept(this.codeWriter);
         }
      }

      if (this.context.isHeapDump() && type instanceof ValueType.Object) {
         ClassReader cls = this.context.getClassSource().get(((ValueType.Object)type).getClassName());
         this.generateHeapDumpMetadata(initMethod ? this.initWriter : this.codeWriter, cls, initMethod, depth);
      }

      if (!initMethod) {
         this.codeWriter.println();
      }

      if (reflectionGeneratorStarted) {
         this.reflectionGenerator.finish();
      }
   }

   private void generateHeapDumpMetadata(CodeWriter writer, ClassReader cls, boolean initMethod, int depth) {
      List<ClassGenerator.HeapDumpField> fields = this.getHeapDumpFields(cls);
      List<ClassGenerator.HeapDumpField> staticFields = this.getHeapDumpStaticFields(cls);
      if (!staticFields.isEmpty() || !fields.isEmpty()) {
         writer.println().println("#if TEAVM_HEAP_DUMP").indent();
         if (!fields.isEmpty()) {
            String structDecl = "struct { uint32_t count; TeaVM_FieldDescriptor data[" + fields.size() + "]; }";
            if (initMethod) {
               writer.print("static " + structDecl + " fieldDescriptors = ");
            } else {
               writer.println(",");
               writer.println(".fieldDescriptors = (TeaVM_FieldDescriptors*) &(" + structDecl + ") ");
            }

            writer.println("{").indent();
            this.generateHeapDumpFields(writer, fields);
            writer.outdent().print("}");
            if (initMethod) {
               writer.println(";");
               writer.println("vt_" + depth + "->fieldDescriptors = (TeaVM_FieldDescriptors*) &fieldDescriptors;");
            }
         }

         if (!staticFields.isEmpty()) {
            String structDecl = "struct { uint32_t count; TeaVM_StaticFieldDescriptor data[" + staticFields.size() + "]; }";
            if (initMethod) {
               writer.print("static " + structDecl + " staticFieldDescriptors = ");
            } else {
               writer.println(",");
               writer.println(".staticFieldDescriptors = (TeaVM_StaticFieldDescriptors*) &(" + structDecl + ") ");
            }

            writer.println("{").indent();
            this.generateHeapDumpFields(writer, staticFields);
            writer.outdent().print("}");
            if (initMethod) {
               writer.println(";");
               writer.print("vt_" + depth + "->staticFieldDescriptors = (TeaVM_StaticFieldDescriptors*) &staticFieldDescriptors;");
            }
         }

         writer.println().outdent().println("#endif");
      }
   }

   private void generateHeapDumpFields(CodeWriter codeWriter, List<ClassGenerator.HeapDumpField> fields) {
      codeWriter.println(".count = " + fields.size() + ",");
      codeWriter.println(".data = {").indent();

      for (int i = 0; i < fields.size(); i++) {
         if (i > 0) {
            codeWriter.println(",");
         }

         ClassGenerator.HeapDumpField field = fields.get(i);
         codeWriter.print("{ .name = u");
         StringPoolGenerator.generateSimpleStringLiteral(codeWriter, field.name);
         codeWriter.print(", .offset = " + field.offset + ", .type = " + field.type + " }");
      }

      codeWriter.println().outdent().println("}");
   }

   private List<ClassGenerator.HeapDumpField> getHeapDumpFields(ClassReader cls) {
      List<ClassGenerator.HeapDumpField> fields = new ArrayList<>();
      switch (cls.getName()) {
         case "java.lang.Object":
         case "java.lang.ref.ReferenceQueue":
         case "java.lang.ref.WeakReference":
         case "java.lang.ref.SoftReference":
            break;
         case "java.lang.ref.Reference":
            fields.add(new ClassGenerator.HeapDumpField("referent", "offsetof(TeaVM_Reference, object)", "TEAVM_FIELD_TYPE_OBJECT"));
            fields.add(new ClassGenerator.HeapDumpField("queue", "offsetof(TeaVM_Reference, queue)", "TEAVM_FIELD_TYPE_OBJECT"));
            break;
         default:
            for (FieldReader field : cls.getFields()) {
               if (!field.hasModifier(ElementModifier.STATIC) && this.isManaged(field)) {
                  String className = this.context.getNames().forClass(cls.getName());
                  String offset = "offsetof(" + className + ", " + this.context.getNames().forMemberField(field.getReference()) + ")";
                  fields.add(new ClassGenerator.HeapDumpField(field.getName(), offset, typeForHeapDump(field.getType())));
               }
            }
      }

      return fields;
   }

   private List<ClassGenerator.HeapDumpField> getHeapDumpStaticFields(ClassReader cls) {
      List<ClassGenerator.HeapDumpField> fields = new ArrayList<>();
      switch (cls.getName()) {
         default:
            for (FieldReader field : cls.getFields()) {
               if (field.hasModifier(ElementModifier.STATIC) && this.isManaged(field)) {
                  String offset = "(unsigned char*) &" + this.context.getNames().forStaticField(field.getReference());
                  fields.add(new ClassGenerator.HeapDumpField(field.getName(), offset, typeForHeapDump(field.getType())));
               }
            }
         case "java.lang.Object":
         case "java.lang.Class":
         case "java.lang.ref.ReferenceQueue":
         case "java.lang.ref.Reference":
         case "java.lang.ref.WeakReference":
         case "java.lang.ref.SoftReference":
            return fields;
      }
   }

   private boolean isManaged(FieldReader field) {
      ValueType type = field.getType();
      return !(type instanceof ValueType.Object) || this.context.getCharacteristics().isManaged(((ValueType.Object)type).getClassName());
   }

   static String typeForHeapDump(ValueType type) {
      String result = "127";
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
               result = "TEAVM_FIELD_TYPE_BOOLEAN";
               break;
            case BYTE:
               result = "TEAVM_FIELD_TYPE_BYTE";
               break;
            case SHORT:
               result = "TEAVM_FIELD_TYPE_SHORT";
               break;
            case CHARACTER:
               result = "TEAVM_FIELD_TYPE_CHAR";
               break;
            case INTEGER:
               result = "TEAVM_FIELD_TYPE_INT";
               break;
            case LONG:
               result = "TEAVM_FIELD_TYPE_LONG";
               break;
            case FLOAT:
               result = "TEAVM_FIELD_TYPE_FLOAT";
               break;
            case DOUBLE:
               result = "TEAVM_FIELD_TYPE_DOUBLE";
         }
      } else if (type instanceof ValueType.Array) {
         result = "TEAVM_FIELD_TYPE_ARRAY";
      } else {
         result = "TEAVM_FIELD_TYPE_OBJECT";
      }

      return result;
   }

   private void generateVirtualTableStructure(String className) {
      String name = this.context.getNames().forClassClass(className);
      this.headerWriter.print("typedef struct ").print(name).println(" {").indent();
      VirtualTable virtualTable = this.context.getVirtualTableProvider().lookup(className);
      if (virtualTable != null) {
         String parentName = "TeaVM_Class";
         int index = 0;
         if (virtualTable.getParent() != null) {
            this.headerIncludes.includeClass(virtualTable.getParent().getClassName());
            parentName = this.context.getNames().forClassClass(virtualTable.getParent().getClassName());
            index = virtualTable.getParent().size();
         }

         this.headerWriter.println(parentName + " parent;");
         int padIndex = 0;

         for (MethodDescriptor method : virtualTable.getMethods()) {
            if (method != null) {
               String methodName = this.context.getNames().forVirtualMethod(method);
               this.headerWriter.printType(method.getResultType()).print(" (*").print(methodName).print(")(");
               CodeGenerator.generateMethodParameters(this.headerWriter, method, false, false);
               this.headerWriter.print(")");
            } else {
               this.headerWriter.print("void (*pad" + padIndex++ + ")()");
            }

            this.headerWriter.println("; // " + index++);
         }
      }

      this.headerWriter.outdent().print("} ").print(name).println(";");
   }

   private boolean isReferenceType(ValueType type) {
      if (!(type instanceof ValueType.Object)) {
         return type instanceof ValueType.Array;
      }

      String className = ((ValueType.Object)type).getClassName();
      return !this.context.getCharacteristics().isStructure(className)
         && !this.context.getCharacteristics().isReflectionSpecial(className)
         && !this.context.getCharacteristics().isFunction(className)
         && !this.context.getCharacteristics().isResource(className)
         && !className.equals(Address.class.getName());
   }

   private void generateStaticGCRoots(String className) {
      if (this.staticGcRoots != null) {
         String suffix = this.context.getNames().forClassInstance(ValueType.object(className));
         String varName = "teavm_gc_localStaticRoots_" + suffix;
         this.codeWriter.println("static void** " + varName + "[" + this.staticGcRoots.length + "] = {").indent();
         boolean first = true;

         for (FieldReference field : this.staticGcRoots) {
            if (!first) {
               this.codeWriter.print(", ");
            }

            first = false;
            String name = this.context.getNames().forStaticField(field);
            this.codeWriter.print("(void**) &").print(name);
         }

         this.codeWriter.println().outdent().println("};");
         this.initWriter.println("teavm_registerStaticGcRoots(" + varName + ", " + this.staticGcRoots.length + ");");
      }
   }

   private void generateLayoutArray(String className) {
      if (this.classLayout != null) {
         String name = this.context.getNames().forClassInstance(ValueType.object(className));
         this.codeWriter.print("static int16_t teavm_classLayouts_" + name + "[" + (this.classLayout.length + 1) + "] = {").indent();
         this.codeWriter.println().print("INT16_C(" + this.classLayout.length + ")");

         for (FieldReference field : this.classLayout) {
            String structName = this.context.getNames().forClass(field.getClassName());
            String fieldName = this.context.getNames().forMemberField(field);
            this.codeWriter.print(", (int16_t) offsetof(" + structName + ", " + fieldName + ")");
         }

         this.codeWriter.println().outdent().println("};");
      }
   }

   private boolean needsData(ClassReader cls) {
      if (cls.hasModifier(ElementModifier.INTERFACE)) {
         return false;
      } else {
         return InteropUtil.isNative(cls) ? false : !cls.getName().equals(Structure.class.getName()) && !cls.getName().equals(Address.class.getName());
      }
   }

   public static boolean needsVirtualTable(Characteristics characteristics, ValueType type) {
      if (type instanceof ValueType.Object) {
         String className = ((ValueType.Object)type).getClassName();
         return characteristics.isManaged(className);
      } else {
         return type instanceof ValueType.Array ? needsVirtualTable(characteristics, ((ValueType.Array)type).getItemType()) : true;
      }
   }

   private boolean needsInitializer(ClassReader cls) {
      return !this.context.getCharacteristics().isStaticInit(cls.getName())
         && !this.context.getCharacteristics().isStructure(cls.getName())
         && !this.context.getCharacteristics().isReflectionSpecial(cls.getName())
         && cls.getMethod(new MethodDescriptor("<clinit>", ValueType.VOID)) != null
         && this.context.getClassInitializerInfo().isDynamicInitializer(cls.getName());
   }

   private boolean tryDelegateToMethod(ClassHolder cls, MethodHolder method) {
      AnnotationHolder delegateToAnnot = method.getAnnotations().get(DelegateTo.class.getName());
      if (delegateToAnnot == null) {
         return false;
      }

      String methodName = delegateToAnnot.getValue("value").getString();

      for (MethodHolder candidate : cls.getMethods()) {
         if (candidate.getName().equals(methodName)) {
            this.generateMethodForwardDeclaration(method);
            this.delegateToMethod(method, candidate);
            return true;
         }
      }

      return false;
   }

   private void delegateToMethod(MethodHolder callingMethod, MethodHolder delegateMethod) {
      CodeGenerator.generateMethodSignature(
         this.codeWriter, this.context.getNames(), callingMethod.getReference(), callingMethod.hasModifier(ElementModifier.STATIC), true
      );
      this.codeWriter.println(" {").indent();
      if (callingMethod.getResultType() != ValueType.VOID) {
         this.codeWriter.print("return ");
      }

      this.codeWriter.print(this.context.getNames().forMethod(delegateMethod.getReference())).print("(");
      boolean isStatic = callingMethod.hasModifier(ElementModifier.STATIC);
      int start = 0;
      if (!isStatic) {
         this.codeWriter.print("teavm_this_");
      } else {
         if (callingMethod.parameterCount() > 0) {
            this.codeWriter.print("teavm_local_1");
         }

         start++;
      }

      for (int i = start; i < callingMethod.parameterCount(); i++) {
         this.codeWriter.print(", ").print("teavm_local_").print(String.valueOf(i + 1));
      }

      this.codeWriter.println(");");
      this.codeWriter.outdent().println("}");
   }

   private boolean tryUsingGenerator(MethodHolder method) {
      MethodReference methodRef = method.getReference();
      Generator generator = this.context.getGenerator(methodRef);
      if (generator == null) {
         return false;
      }

      this.generateMethodForwardDeclaration(method);
      CodeWriter writerBefore = this.codeWriter.fragment();
      boolean isStatic = method.hasModifier(ElementModifier.STATIC);
      CodeGenerator.generateMethodSignature(this.codeWriter, this.context.getNames(), methodRef, isStatic, true);
      this.codeWriter.println(" {").indent();
      CodeWriter bodyWriter = this.codeWriter.fragment();
      this.codeWriter.outdent().println("}");
      GeneratorContextImpl generatorContext = new GeneratorContextImpl(
         this.codeGenerator.getClassContext(), bodyWriter, writerBefore, this.codeWriter, this.includes, this.callSites
      );
      generator.generate(generatorContext, methodRef);

      try {
         generatorContext.flush();
         return true;
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   public String nameOfType(ValueType type) {
      if (type instanceof ValueType.Primitive) {
         return ReflectionUtil.typeName(((ValueType.Primitive)type).getKind());
      } else if (type instanceof ValueType.Array) {
         return isArrayOfPrimitives(type) ? type.toString().replace('/', '.') : null;
      } else if (type == ValueType.VOID) {
         return "void";
      } else if (type instanceof ValueType.Object) {
         String name = ((ValueType.Object)type).getClassName();
         return this.context.getMetadataRequirements().getInfo(name).name() ? name : null;
      } else {
         throw new AssertionError();
      }
   }

   private static boolean isArrayOfPrimitives(ValueType type) {
      while (type instanceof ValueType.Array) {
         type = ((ValueType.Array)type).getItemType();
      }

      return type instanceof ValueType.Primitive || type == ValueType.VOID;
   }

   private void generateIsSupertypeFunction(ValueType type) {
      String name = this.context.getNames().forSupertypeFunction(type);
      this.headerWriter.println("extern int32_t " + name + "(TeaVM_Class*, TeaVM_Class*);");
      this.codeWriter.println("int32_t " + name + "(TeaVM_Class* supercls, TeaVM_Class* cls) {").indent();
      if (type instanceof ValueType.Object) {
         this.generateIsSuperclassFunction(((ValueType.Object)type).getClassName());
      } else if (type instanceof ValueType.Primitive) {
         this.generateIsSuperPrimitiveFunction((ValueType.Primitive)type);
      } else if (type == ValueType.VOID) {
         this.generateIsSuperclassFunction("java.lang.Void");
      } else if (type instanceof ValueType.Array) {
         this.generateIsSuperArrayFunction(((ValueType.Array)type).getItemType());
      }

      this.codeWriter.outdent().println("}");
   }

   private void generateIsSuperclassFunction(String className) {
      if (this.context.isIncremental()) {
         this.generateIncrementalSuperclassFunction(className);
      } else {
         this.generateFastIsSuperclassFunction(className);
      }
   }

   private void generateFastIsSuperclassFunction(String className) {
      List<TagRegistry.Range> ranges = this.tagRegistry.getRanges(className);
      if (ranges.isEmpty()) {
         this.codeWriter.println("return INT32_C(0);");
      } else {
         String tagName = this.context.getNames().forMemberField(new FieldReference(RuntimeClass.class.getName(), "tag"));
         this.codeWriter.println("int32_t tag = cls->" + tagName + ";");
         int lower = ranges.get(0).lower;
         int upper = ranges.get(ranges.size() - 1).upper;
         this.codeWriter.println("if (tag < " + lower + " || tag >= " + upper + ") return INT32_C(0);");

         for (int i = 1; i < ranges.size(); i++) {
            lower = ranges.get(i - 1).upper;
            upper = ranges.get(i).lower;
            this.codeWriter.println("if (tag >= " + lower + " && tag < " + upper + ") return INT32_C(0);");
         }

         this.codeWriter.println("return INT32_C(1);");
      }
   }

   private void generateIncrementalSuperclassFunction(String className) {
      String functionName = this.context.getNames().forSupertypeFunction(ValueType.object(className));
      ClassReader cls = this.context.getClassSource().get(className);
      if (cls != null) {
         this.includes.includeClass(className);
         String name = this.context.getNames().forClassInstance(ValueType.object(className));
         this.codeWriter.println("if (cls == (TeaVM_Class*) &" + name + ") return INT32_C(1);");
         this.codeWriter.println("if (cls->superclass != NULL && " + functionName + "(cls->superclass)) return INT32_C(1);");
         this.codeWriter.println("for (int32_t i = 0; i < cls->superinterfaceCount; ++i) {").indent();
         this.codeWriter.println("if (" + functionName + "(cls->superinterfaces[i])) return INT32_C(1);");
         this.codeWriter.outdent().println("}");
      }

      this.codeWriter.println("return INT32_C(0);");
   }

   private void generateIsSuperArrayFunction(ValueType itemType) {
      String itemTypeName = this.context.getNames().forMemberField(new FieldReference(RuntimeClass.class.getName(), "itemType"));
      this.codeWriter.println("TeaVM_Class* itemType = cls->" + itemTypeName + ";");
      this.codeWriter.println("if (itemType == NULL) return INT32_C(0);");
      if (itemType instanceof ValueType.Primitive) {
         this.codeWriter.println("return itemType == &" + this.context.getNames().forClassInstance(itemType) + ";");
      } else {
         this.codeWriter.println("return " + this.context.getNames().forSupertypeFunction(itemType) + "(itemType);");
      }
   }

   private void generateIsSuperPrimitiveFunction(ValueType.Primitive primitive) {
      switch (primitive.getKind()) {
         case BOOLEAN:
            this.generateIsSuperclassFunction("java.lang.Boolean");
            break;
         case BYTE:
            this.generateIsSuperclassFunction("java.lang.Byte");
            break;
         case SHORT:
            this.generateIsSuperclassFunction("java.lang.Short");
            break;
         case CHARACTER:
            this.generateIsSuperclassFunction("java.lang.Character");
            break;
         case INTEGER:
            this.generateIsSuperclassFunction("java.lang.Integer");
            break;
         case LONG:
            this.generateIsSuperclassFunction("java.lang.Long");
            break;
         case FLOAT:
            this.generateIsSuperclassFunction("java.lang.Float");
            break;
         case DOUBLE:
            this.generateIsSuperclassFunction("java.lang.Double");
      }
   }

   static class FieldInitializer {
      final String name;
      final Consumer<CodeWriter> value;

      FieldInitializer(String name, String value) {
         this(name, w -> w.print(value));
      }

      FieldInitializer(String name, Consumer<CodeWriter> value) {
         this.name = name;
         this.value = value;
      }
   }

   static class HeapDumpField {
      String name;
      String offset;
      String type;

      HeapDumpField(String name, String offset, String type) {
         this.name = name;
         this.offset = offset;
         this.type = type;
      }
   }
}
