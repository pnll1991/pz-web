package org.teavm.backend.wasm.generate.classes;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.reflection.ClassInfoStruct;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmConditional;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmInt32Constant;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.model.ValueType;
import org.teavm.model.classes.TagRegistry;

public class WasmGCSupertypeFunctionGenerator implements WasmGCSupertypeFunctionProvider {
   private Map<ValueType, WasmFunction> functions = new HashMap<>();
   private WasmModule module;
   private WasmGCClassGenerator classGenerator;
   private WasmGCNameProvider nameProvider;
   private TagRegistry tagRegistry;
   private WasmFunctionTypes functionTypes;
   private WasmFunctionType functionType;
   private Queue<Runnable> queue;
   private WasmFunction isArrayFunction;

   WasmGCSupertypeFunctionGenerator(
      WasmModule module,
      WasmGCClassGenerator classGenerator,
      WasmGCNameProvider nameProvider,
      TagRegistry tagRegistry,
      WasmFunctionTypes functionTypes,
      Queue<Runnable> queue
   ) {
      this.module = module;
      this.classGenerator = classGenerator;
      this.nameProvider = nameProvider;
      this.tagRegistry = tagRegistry;
      this.functionTypes = functionTypes;
      this.queue = queue;
   }

   @Override
   public WasmFunction getIsSupertypeFunction(ValueType type) {
      if (type instanceof ValueType.Array) {
         return this.getIsArraySupertypeFunction();
      }

      WasmFunction result = this.functions.get(type);
      if (result == null) {
         result = this.generateIsSupertypeFunction(type);
         this.functions.put(type, result);
      }

      return result;
   }

   private WasmFunction generateIsSupertypeFunction(ValueType type) {
      WasmFunction function = new WasmFunction(this.getFunctionType());
      WasmGCClassInfo classClass = this.classGenerator.standardClasses.classClass();
      function.setName(this.nameProvider.topLevel(this.nameProvider.suggestForType(type) + "@isSupertypes"));
      WasmLocal subtypeVar = new WasmLocal(classClass.getType(), "subtype");
      WasmLocal supertypeVar = new WasmLocal(classClass.getType(), "supertype");
      function.add(subtypeVar);
      function.add(supertypeVar);
      this.module.functions.add(function);
      this.queue.add(() -> {
         if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object)type).getClassName();
            this.generateIsClass(subtypeVar, className, function);
         } else {
            assert !(type instanceof ValueType.Array);
            WasmGlobal expected = this.classGenerator.getClassInfo(type).pointer;
            function.getBody().builder().getLocal(subtypeVar).getGlobal(expected).refEqual();
         }
      });
      return function;
   }

   private void generateIsClass(WasmLocal subtypeVar, String className, WasmFunction function) {
      List<TagRegistry.Range> ranges = this.tagRegistry.getRanges(className);
      if (ranges.isEmpty()) {
         function.getBody().add(new WasmInt32Constant(0));
      } else {
         WasmStructure classInfoStruct = this.classGenerator.reflectionTypes().classInfo().structure();
         int tagOffset = this.classGenerator.reflectionTypes().classInfo().tagIndex();
         WasmLocal tagVar = new WasmLocal(WasmType.INT32, "tag");
         function.add(tagVar);
         ranges.sort(Comparator.comparingInt(range -> range.lower));
         int lower = ranges.get(0).lower;
         int upper = ranges.get(ranges.size() - 1).upper;
         WasmInstructionBuilder body = function.getBody().builder();
         body.getLocal(subtypeVar).structGet(classInfoStruct, tagOffset).teeLocal(tagVar);
         body.i32Const(lower).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.LT_SIGNED);
         body.conditional().getThenBlock().builder().i32Const(0).return_();
         body.getLocal(tagVar).i32Const(upper).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.GE_SIGNED);
         body.conditional().getThenBlock().builder().i32Const(0).return_();

         for (int i = 1; i < ranges.size(); i++) {
            int lowerHole = ranges.get(i - 1).upper;
            int upperHole = ranges.get(i).lower;
            body.getLocal(tagVar).i32Const(lowerHole).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.GE_SIGNED);
            WasmInstructionBuilder condHoleThen = body.conditional().getThenBlock().builder();
            condHoleThen.getLocal(tagVar).i32Const(upperHole).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.LT_SIGNED);
            condHoleThen.conditional().getThenBlock().builder().i32Const(0).return_();
         }

         body.i32Const(1);
      }
   }

   WasmFunction getIsArraySupertypeFunction() {
      if (this.isArrayFunction != null) {
         return this.isArrayFunction;
      }

      WasmFunction function = new WasmFunction(this.getFunctionType());
      this.isArrayFunction = function;
      this.module.functions.add(function);
      ClassInfoStruct classInfoType = this.classGenerator.reflectionTypes().classInfo();
      function.setName(this.nameProvider.topLevel("teavm@isArrayType"));
      WasmLocal subtypeVar = new WasmLocal(classInfoType.structure().getReference(), "subtype");
      WasmLocal supertypeVar = new WasmLocal(classInfoType.structure().getReference(), "supertype");
      function.add(subtypeVar);
      function.add(supertypeVar);
      int itemOffset = classInfoType.itemTypeIndex();
      WasmInstructionBuilder body = function.getBody().builder();
      body.getLocal(supertypeVar).structGet(classInfoType.structure(), itemOffset).setLocal(supertypeVar);
      body.getLocal(subtypeVar).structGet(classInfoType.structure(), itemOffset).setLocal(subtypeVar);
      body.getLocal(subtypeVar).isNull();
      WasmConditional itemTest = body.conditional(WasmType.INT32);
      itemTest.getThenBlock().builder().i32Const(0);
      WasmFunctionType funcType = this.functionTypes.of(WasmType.INT32, classInfoType.structure().getReference(), classInfoType.structure().getReference());
      WasmInstructionBuilder elseBody = itemTest.getElseBlock().builder();
      elseBody.getLocal(subtypeVar)
         .getLocal(supertypeVar)
         .getLocal(supertypeVar)
         .structGet(classInfoType.structure(), classInfoType.supertypeFunctionIndex())
         .callReference(funcType);
      return function;
   }

   @Override
   public WasmFunctionType getFunctionType() {
      if (this.functionType == null) {
         WasmType.CompositeReference classInfoStruct = this.classGenerator.reflectionTypes().classInfo().structure().getReference();
         this.functionType = this.functionTypes.of(WasmType.INT32, classInfoStruct, classInfoStruct);
      }

      return this.functionType;
   }
}
