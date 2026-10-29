package org.teavm.backend.wasm.generate.methods;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.teavm.ast.RegularMethodNode;
import org.teavm.ast.VariableNode;
import org.teavm.ast.decompilation.Decompiler;
import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCInitializerContributor;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfo;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCStandardClasses;
import org.teavm.backend.wasm.generate.classes.WasmGCSupertypeFunctionProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCTypeMapper;
import org.teavm.backend.wasm.generate.reflection.ClassInfoStruct;
import org.teavm.backend.wasm.generate.strings.WasmGCStringProvider;
import org.teavm.backend.wasm.intrinsics.WasmGCBodyIntrinsic;
import org.teavm.backend.wasm.intrinsics.WasmGCInlineIntrinsic;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmBreak;
import org.teavm.backend.wasm.model.instruction.WasmCatchClause;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmTry;
import org.teavm.backend.wasm.model.instruction.WasmUnreachable;
import org.teavm.backend.wasm.transformation.CoroutineTransformation;
import org.teavm.backend.wasm.types.PreciseTypeInference;
import org.teavm.backend.wasm.types.PreciseValueType;
import org.teavm.backend.wasm.types.WasmGCVariableCategoryProvider;
import org.teavm.backend.wasm.vtable.WasmGCVirtualTableProvider;
import org.teavm.dependency.DependencyInfo;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.interop.Async;
import org.teavm.interop.Export;
import org.teavm.interop.Import;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.AnnotationValue;
import org.teavm.model.BasicBlock;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassHolder;
import org.teavm.model.ElementModifier;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.ListableClassHolderSource;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.util.InstructionVariableMapper;
import org.teavm.model.util.RegisterAllocator;
import org.teavm.model.util.UsageExtractor;
import org.teavm.parsing.resource.ResourceProvider;
import org.teavm.vm.intrinsic.IntrinsicProvider;

public class WasmGCMethodGenerator implements BaseWasmFunctionRepository {
   private WasmModule module;
   private ClassHierarchy hierarchy;
   private ListableClassHolderSource classes;
   private ResourceProvider resources;
   private ClassLoader classLoader;
   private WasmGCVirtualTableProvider virtualTables;
   private ClassInitializerInfo classInitInfo;
   private WasmFunctionTypes functionTypes;
   private WasmGCSupertypeFunctionProvider supertypeFunctions;
   public final WasmGCNameProvider names;
   private Diagnostics diagnostics;
   private WasmGCTypeMapper typeMapper;
   private IntrinsicProvider<WasmGCBodyIntrinsic> bodyIntrinsics;
   private IntrinsicProvider<WasmGCInlineIntrinsic> inlineIntrinsics;
   private Queue<Runnable> queue = new ArrayDeque<>();
   private Map<MethodReference, WasmFunction> staticMethods = new HashMap<>();
   private Map<MethodReference, WasmFunction> instanceMethods = new HashMap<>();
   private boolean friendlyToDebugger;
   private Decompiler decompiler;
   private WasmGCGenerationContext context;
   private WasmFunction dummyInitializer;
   private WasmGCClassInfoProvider classInfoProvider;
   private WasmGCStandardClasses standardClasses;
   private WasmGCStringProvider strings;
   private boolean strict;
   private String entryPoint;
   private Consumer<WasmGCInitializerContributor> initializerContributors;
   private boolean compactMode;
   private DependencyInfo dependency;
   private Set<MethodReference> asyncMethods = Set.of();
   private Set<MethodReference> asyncSplitMethods = Set.of();
   private CoroutineTransformation coroutineTransformation;

   public WasmGCMethodGenerator(
      WasmModule module,
      ClassHierarchy hierarchy,
      ListableClassHolderSource classes,
      ResourceProvider resources,
      ClassLoader classLoader,
      WasmGCVirtualTableProvider virtualTables,
      ClassInitializerInfo classInitInfo,
      WasmFunctionTypes functionTypes,
      WasmGCNameProvider names,
      Diagnostics diagnostics,
      IntrinsicProvider<WasmGCBodyIntrinsic> bodyIntrinsics,
      IntrinsicProvider<WasmGCInlineIntrinsic> inlineIntrinsics,
      DependencyInfo dependency,
      boolean strict,
      String entryPoint,
      Consumer<WasmGCInitializerContributor> initializerContributors
   ) {
      this.module = module;
      this.hierarchy = hierarchy;
      this.classes = classes;
      this.resources = resources;
      this.classLoader = classLoader;
      this.virtualTables = virtualTables;
      this.classInitInfo = classInitInfo;
      this.functionTypes = functionTypes;
      this.names = names;
      this.diagnostics = diagnostics;
      this.bodyIntrinsics = bodyIntrinsics;
      this.inlineIntrinsics = inlineIntrinsics;
      this.dependency = dependency;
      this.strict = strict;
      this.entryPoint = entryPoint;
      this.initializerContributors = initializerContributors;
   }

   public void setAsyncMethods(Set<MethodReference> asyncMethods) {
      this.asyncMethods = asyncMethods;
   }

   public void setAsyncSplitMethods(Set<MethodReference> asyncSplitMethods) {
      this.asyncSplitMethods = asyncSplitMethods;
   }

   public void setCompactMode(boolean compactMode) {
      this.compactMode = compactMode;
   }

   public void setTypeMapper(WasmGCTypeMapper typeMapper) {
      this.typeMapper = typeMapper;
   }

   public void setFriendlyToDebugger(boolean friendlyToDebugger) {
      this.friendlyToDebugger = friendlyToDebugger;
   }

   public void setClassInfoProvider(WasmGCClassInfoProvider classInfoProvider) {
      this.classInfoProvider = classInfoProvider;
   }

   public void setStandardClasses(WasmGCStandardClasses standardClasses) {
      this.standardClasses = standardClasses;
   }

   public void setSupertypeFunctions(WasmGCSupertypeFunctionProvider supertypeFunctions) {
      this.supertypeFunctions = supertypeFunctions;
   }

   public void setStrings(WasmGCStringProvider strings) {
      this.strings = strings;
   }

   public boolean process() {
      if (this.queue.isEmpty()) {
         return false;
      }

      while (!this.queue.isEmpty()) {
         this.queue.remove().run();
      }

      return true;
   }

   public boolean hasSomethingToGenerate() {
      return !this.queue.isEmpty();
   }

   @Override
   public WasmFunction forStaticMethod(MethodReference methodReference) {
      return this.staticMethods.computeIfAbsent(methodReference, this::createStaticFunction);
   }

   private WasmFunction createStaticFunction(MethodReference methodReference) {
      ClassHolder cls = this.classes.get(methodReference.getClassName());
      MethodHolder method = null;
      if (cls != null) {
         method = cls.getMethod(methodReference.getDescriptor());
      }

      WasmType returnType = this.typeMapper.mapType(methodReference.getReturnType());
      WasmType[] parameterTypes = new WasmType[methodReference.parameterCount()];
      boolean isImported = method.hasModifier(ElementModifier.NATIVE) && method.getAnnotations().get(Import.class.getName()) != null;

      for (int i = 0; i < parameterTypes.length; i++) {
         ValueType type = methodReference.parameterType(i);
         parameterTypes[i] = isImported && isBufferType(type) ? WasmType.INT32 : this.typeMapper.mapType(type);
      }

      WasmFunction function = new WasmFunction(this.functionTypes.of(returnType, parameterTypes));
      function.setName(this.names.topLevel(this.names.suggestForMethod(methodReference)));
      this.module.functions.add(function);
      function.setJavaMethod(methodReference);
      if (method != null) {
         if (method.hasModifier(ElementModifier.STATIC)) {
            MethodHolder finalMethod = method;
            this.queue.add(() -> this.generateMethodBody(finalMethod, function));
         }

         AnnotationHolder exportAnnot = method.getAnnotations().get(Export.class.getName());
         if (exportAnnot != null) {
            function.setExportName(exportAnnot.getValue("name").getString());
         }
      }

      return function;
   }

   static boolean isBufferType(ValueType type) {
      if (!(type instanceof ValueType.Object)) {
         return false;
      }

      switch (((ValueType.Object)type).getClassName()) {
         case "java.nio.Buffer":
         case "java.nio.ByteBuffer":
         case "java.nio.ShortBuffer":
         case "java.nio.CharBuffer":
         case "java.nio.IntBuffer":
         case "java.nio.LongBuffer":
         case "java.nio.FloatBuffer":
         case "java.nio.DoubleBuffer":
            return true;
         default:
            return false;
      }
   }

   @Override
   public WasmFunction forInstanceMethod(MethodReference methodReference) {
      return this.instanceMethods.computeIfAbsent(methodReference, this::createInstanceFunction);
   }

   private WasmFunction createInstanceFunction(MethodReference methodReference) {
      WasmType returnType = this.typeMapper.mapType(methodReference.getReturnType());
      WasmType[] parameterTypes = new WasmType[methodReference.parameterCount() + 1];
      boolean compactMethod = this.compactMode && this.typeMapper.mapType(ValueType.object(methodReference.getClassName())) instanceof WasmType.Reference;
      parameterTypes[0] = compactMethod ? WasmType.ANY : this.typeMapper.mapType(ValueType.object(methodReference.getClassName()));

      for (int i = 0; i < methodReference.parameterCount(); i++) {
         parameterTypes[i + 1] = this.typeMapper.mapType(methodReference.parameterType(i));
      }

      WasmFunction function = new WasmFunction(this.functionTypes.of(returnType, parameterTypes));
      function.setName(this.names.topLevel(this.names.suggestForMethod(methodReference)));
      this.module.functions.add(function);
      function.setJavaMethod(methodReference);
      ClassHolder cls = this.classes.get(methodReference.getClassName());
      if (cls != null) {
         MethodHolder method = cls.getMethod(methodReference.getDescriptor());
         if (method != null && !method.hasModifier(ElementModifier.STATIC)) {
            this.queue.add(() -> this.generateMethodBody(method, function));
         }

         AnnotationHolder exportAnnot = method.getAnnotations().get(Export.class.getName());
         if (exportAnnot != null) {
            function.setExportName(exportAnnot.getValue("name").getString());
         }
      }

      return function;
   }

   private void generateMethodBody(MethodHolder method, WasmFunction function) {
      try {
         WasmGCBodyIntrinsic bodyIntrinsic = this.bodyIntrinsics.getIntrinsic(method.getReference());
         if (bodyIntrinsic != null) {
            this.generateCustomMethodBody(bodyIntrinsic, method.getReference(), function);
         } else if (!method.hasModifier(ElementModifier.NATIVE)) {
            this.generateRegularMethodBody(method, function);
         } else {
            this.generateNativeMethodBody(method, function);
         }
      } catch (RuntimeException e) {
         StringWriter buffer = new StringWriter();
         PrintWriter printWriter = new PrintWriter(buffer);
         e.printStackTrace(printWriter);
         this.diagnostics.error(new CallLocation(method.getReference()), "Failed generating method body due to internal exception: " + buffer);
         function.getBody().clear();
         function.getBody().add(new WasmUnreachable());
      }
   }

   private void generateCustomMethodBody(WasmGCBodyIntrinsic intrinsic, MethodReference method, WasmFunction function) {
      intrinsic.apply(method, function);
      boolean isSuspend = this.asyncMethods.contains(method);
      if (isSuspend) {
         if (this.coroutineTransformation == null) {
            this.coroutineTransformation = new CoroutineTransformation(this.functionTypes, this, this.classInfoProvider);
         }

         this.coroutineTransformation.transform(function);
      }
   }

   private void generateRegularMethodBody(MethodHolder method, WasmFunction function) {
      Objects.requireNonNull(method.getProgram());
      this.eliminateMultipleNullConstantUsages(method.getProgram());
      Decompiler decompiler = this.getDecompiler();
      WasmGCVariableCategoryProvider categoryProvider = new WasmGCVariableCategoryProvider(this.hierarchy);
      boolean methodCompact = this.compactMode
         && !method.hasModifier(ElementModifier.STATIC)
         && this.typeMapper.mapType(ValueType.object(method.getOwnerName())) instanceof WasmType.Reference;
      categoryProvider.setCompactMode(methodCompact);
      RegisterAllocator allocator = new RegisterAllocator(categoryProvider);
      allocator.allocateRegisters(method.getReference(), method.getProgram(), this.friendlyToDebugger);
      RegularMethodNode ast = decompiler.decompileRegular(method);
      int firstVar = method.hasModifier(ElementModifier.STATIC) ? 1 : 0;
      PreciseTypeInference typeInference = new PreciseTypeInference(method.getProgram(), method.getReference(), this.hierarchy);
      typeInference.setPhisSkipped(true);
      typeInference.setBackPropagation(true);
      typeInference.ensure();
      int registerCount = 0;

      for (int i = 0; i < method.getProgram().variableCount(); i++) {
         registerCount = Math.max(registerCount, method.getProgram().variableAt(i).getRegister() + 1);
      }

      int[] originalIndexToIndex = new int[registerCount];
      Arrays.fill(originalIndexToIndex, -1);

      for (VariableNode varNode : ast.getVariables()) {
         originalIndexToIndex[varNode.getOriginalIndex()] = varNode.getIndex();
      }

      int[] variableRepresentatives = new int[registerCount];
      Arrays.fill(variableRepresentatives, -1);

      for (int i = 0; i < method.getProgram().variableCount(); i++) {
         Variable variable = method.getProgram().variableAt(i);
         int varNodeIndex = variable.getRegister() >= 0 ? originalIndexToIndex[variable.getRegister()] : -1;
         if (varNodeIndex >= 0 && variableRepresentatives[varNodeIndex] < 0 && typeInference.typeOf(variable) != null) {
            variableRepresentatives[varNodeIndex] = variable.getIndex();
         }
      }

      for (int i = 0; i < method.getProgram().variableCount(); i++) {
         Variable variable = method.getProgram().variableAt(i);
         int varNodeIndex = variable.getRegister() >= 0 ? originalIndexToIndex[variable.getRegister()] : -1;
         if (varNodeIndex >= 0 && variableRepresentatives[varNodeIndex] < 0) {
            variableRepresentatives[varNodeIndex] = variable.getIndex();
         }
      }

      boolean[] nonNullableVars = new boolean[ast.getVariables().size()];
      PreciseValueType[] preciseTypes = new PreciseValueType[ast.getVariables().size()];
      boolean isSuspend = this.asyncMethods.contains(method.getReference()) && method.getAnnotations().get(Async.class.getName()) == null;

      for (int i = firstVar; i < ast.getVariables().size(); i++) {
         Variable representative = method.getProgram().variableAt(variableRepresentatives[i]);
         PreciseValueType inferredType = typeInference.typeOf(representative);
         if (inferredType == null) {
            inferredType = new PreciseValueType(ValueType.object("java.lang.Object"), false);
         }

         preciseTypes[i] = inferredType;
         nonNullableVars[i] = !isSuspend && inferredType.isArrayUnwrap;
      }

      if (!isSuspend) {
         this.calculateNonNullableVars(nonNullableVars, ast);
      }

      for (int i = firstVar; i < ast.getVariables().size(); i++) {
         VariableNode localVar = ast.getVariables().get(i);
         PreciseValueType inferredType = preciseTypes[i];
         WasmType type;
         if (i == 0 && this.compactMode) {
            type = WasmType.ANY;
         } else if (inferredType.isArrayUnwrap && inferredType.valueType != null) {
            WasmArray arrayType = this.classInfoProvider.getClassInfo(inferredType.valueType).getArray();
            type = nonNullableVars[i] ? arrayType.getNonNullReference() : arrayType.getReference();
         } else {
            type = this.typeMapper.mapType(inferredType.valueType);
         }

         WasmLocal wasmLocal = new WasmLocal(type, localVar.getName());
         function.add(wasmLocal);
      }

      this.addInitializerErase(method, function);
      WasmGCInstructionGenerationVisitor visitor = new WasmGCInstructionGenerationVisitor(
         this.getGenerationContext(), method.getReference(), function, firstVar, isSuspend, typeInference, this.asyncSplitMethods
      );
      this.wrapSynchronizedMethod(method, visitor, function, ast);
      if (isSuspend) {
         if (this.coroutineTransformation == null) {
            this.coroutineTransformation = new CoroutineTransformation(this.functionTypes, this, this.classInfoProvider);
         }

         this.coroutineTransformation.transform(function);
      }
   }

   private void wrapSynchronizedMethod(MethodHolder method, WasmGCInstructionGenerationVisitor visitor, WasmFunction function, RegularMethodNode ast) {
      if (method.hasModifier(ElementModifier.SYNCHRONIZED)) {
         WasmInstructionBuilder builder = function.getBody().builder();
         this.generateMonitor(method, function, builder);
         visitor.monitorEnter(builder);
         WasmInstructionBuilder catchBlock = builder.block(function.getType().getSingleReturnType());
         WasmInstructionBuilder tryWrapper = catchBlock.block(WasmType.EXN);
         WasmTry tryCatch = tryWrapper.try_();
         tryCatch.getCatches().add(new WasmCatchClause(null, true, tryWrapper.list));
         visitor.setReturnBlock(catchBlock.list);
         visitor.generate(ast.getBody(), tryCatch.getBody());
         tryCatch.getBody().add(new WasmBreak(catchBlock.list));
         tryWrapper.unreachable();
         catchBlock.typeInference.typeStack.add(WasmType.EXN);
         this.generateMonitor(method, function, catchBlock);
         visitor.monitorExit(catchBlock);
         catchBlock.throw_(null);
         this.generateMonitor(method, function, builder);
         visitor.monitorExit(builder);
      } else {
         visitor.generate(ast.getBody(), function.getBody());
         if ((function.getBody().getLast() == null || !function.getBody().getLast().isTerminating())
            && method.getResultType() != ValueType.VOID
            && !visitor.isShortenedReturn()) {
            function.getBody().add(new WasmUnreachable());
         }
      }
   }

   private void generateMonitor(MethodHolder method, WasmFunction function, WasmInstructionBuilder builder) {
      if (method.hasModifier(ElementModifier.STATIC)) {
         this.generateClassLiteral(builder, method.getOwnerName());
      } else {
         builder.getLocal(function.getLocalVariables().get(0));
      }
   }

   private void generateClassLiteral(WasmInstructionBuilder builder, String className) {
      WasmGlobal arg = this.context.classInfoProvider().getClassInfo(className).getPointer();
      ClassInfoStruct classInfoType = this.context.classInfoProvider().reflectionTypes().classInfo();
      builder.getGlobal(arg).call(classInfoType.classObjectFunction());
   }

   private void eliminateMultipleNullConstantUsages(final Program program) {
      final boolean[] nulls = new boolean[program.variableCount()];
      final int[] usageCount = new int[program.variableCount()];
      final TextLocation[] locations = new TextLocation[program.variableCount()];
      UsageExtractor usageExtractor = new UsageExtractor();

      for (BasicBlock block : program.getBasicBlocks()) {
         for (Instruction insn : block) {
            insn.acceptVisitor(usageExtractor);
            Variable[] usedVars = usageExtractor.getUsedVariables();
            if (usedVars != null) {
               for (Variable usedVar : usedVars) {
                  usageCount[usedVar.getIndex()]++;
               }
            }

            if (insn instanceof NullConstantInstruction) {
               int index = ((NullConstantInstruction)insn).getReceiver().getIndex();
               nulls[index] = true;
               locations[index] = insn.getLocation();
            }
         }

         for (Phi phi : block.getPhis()) {
            for (Incoming input : phi.getIncomings()) {
               usageCount[input.getValue().getIndex()]++;
            }
         }
      }

      for (int i = 0; i < program.variableCount(); i++) {
         if (nulls[i]) {
            if (usageCount[i] <= 1) {
               nulls[i] = false;
            }
         } else {
            usageCount[i] = 0;
         }
      }

      var mapFunction = new Function<Variable, Variable>() {
         Instruction instruction;

         public Variable apply(Variable variable) {
            if (variable.getIndex() < nulls.length && nulls[variable.getIndex()] && usageCount[variable.getIndex()]++ != 0) {
               NullConstantInstruction nullConstant = new NullConstantInstruction();
               nullConstant.setReceiver(program.createVariable());
               nullConstant.setLocation(locations[variable.getIndex()]);
               this.instruction.insertPrevious(nullConstant);
               return nullConstant.getReceiver();
            } else {
               return variable;
            }
         }
      };
      InstructionVariableMapper mapper = new InstructionVariableMapper(mapFunction);

      for (BasicBlock block : program.getBasicBlocks()) {
         for (Instruction insn : block) {
            mapFunction.instruction = insn;
            insn.acceptVisitor(mapper);
         }

         for (Phi phi : block.getPhis()) {
            for (Incoming input : phi.getIncomings()) {
               int index = input.getValue().getIndex();
               if (index < nulls.length && nulls[index] && usageCount[index]++ > 0) {
                  NullConstantInstruction nullConstant = new NullConstantInstruction();
                  nullConstant.setReceiver(program.createVariable());
                  nullConstant.setLocation(locations[index]);
                  input.setValue(nullConstant.getReceiver());
                  input.getSource().getLastInstruction().insertPrevious(nullConstant);
               }
            }
         }
      }
   }

   private void calculateNonNullableVars(boolean[] nonNullVars, RegularMethodNode ast) {
      NonNullVarsCalculator calculator = new NonNullVarsCalculator(nonNullVars);
      ast.getBody().acceptVisitor(calculator);
   }

   private void generateNativeMethodBody(MethodHolder method, WasmFunction function) {
      AnnotationHolder importAnnot = method.getAnnotations().get(Import.class.getName());
      if (importAnnot == null) {
         this.diagnostics.error(new CallLocation(method.getReference()), "Method is not annotated with {{c0}}", Import.class.getName());
      } else {
         function.setImportName(importAnnot.getValue("name").getString());
         AnnotationValue moduleName = importAnnot.getValue("module");
         function.setImportModule(moduleName != null ? moduleName.getString() : "teavm");
      }
   }

   private void addInitializerErase(MethodReader method, WasmFunction function) {
      if (method.hasModifier(ElementModifier.STATIC)
         && method.getName().equals("<clinit>")
         && method.parameterCount() == 0
         && this.classInitInfo.isDynamicInitializer(method.getOwnerName())) {
         ClassInfoStruct classInfoStruct = this.classInfoProvider.reflectionTypes().classInfo();
         WasmGCClassInfo classInfo = this.classInfoProvider.getClassInfo(method.getOwnerName());
         WasmInstructionBuilder body = function.getBody().builder();
         body.funcRef(this.getDummyInitializer()).setGlobal(classInfo.getInitializerPointer());
         if (classInfoStruct.initializerIndex() >= 0) {
            body.getGlobal(classInfo.getPointer())
               .funcRef(this.getDummyInitializer())
               .structSet(classInfoStruct.structure(), classInfoStruct.initializerIndex());
         }
      }
   }

   private Decompiler getDecompiler() {
      if (this.decompiler == null) {
         this.decompiler = new Decompiler(this.classes, Set.of(), this.friendlyToDebugger);
      }

      return this.decompiler;
   }

   public WasmGCGenerationContext getGenerationContext() {
      if (this.context == null) {
         this.context = new WasmGCGenerationContext(
            this.module,
            this.virtualTables,
            this.typeMapper,
            this.functionTypes,
            this.classes,
            this.resources,
            this.classLoader,
            this.hierarchy,
            this,
            this.supertypeFunctions,
            this.classInfoProvider,
            this.standardClasses,
            this.strings,
            this.inlineIntrinsics,
            this.names,
            this.strict,
            this.entryPoint,
            this.initializerContributors,
            this.diagnostics,
            this.classInitInfo,
            this.dependency
         );
      }

      return this.context;
   }

   public WasmFunction getDummyInitializer() {
      if (this.dummyInitializer == null) {
         this.dummyInitializer = new WasmFunction(this.functionTypes.of(null));
         this.dummyInitializer.setName(this.names.topLevel("teavm@dummyInitializer"));
         this.dummyInitializer.setReferenced(true);
         this.module.functions.add(this.dummyInitializer);
      }

      return this.dummyInitializer;
   }
}
