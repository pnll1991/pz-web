package org.teavm.backend.wasm.generate.strings;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCInitializerContributor;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfo;
import org.teavm.backend.wasm.generate.classes.WasmGCStandardClasses;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmMemorySegment;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.backend.wasm.model.instruction.WasmStructNewDefault;
import org.teavm.backend.wasm.render.WasmBinaryWriter;
import org.teavm.backend.wasm.runtime.StringInternPool;
import org.teavm.backend.wasm.runtime.WasmGCSupport;
import org.teavm.dependency.DependencyInfo;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.model.MethodReference;

public class WasmGCStringPool implements WasmGCStringProvider, WasmGCInitializerContributor {
   private WasmGCStandardClasses standardClasses;
   private WasmModule module;
   private WasmBinaryWriter binaryWriter = new WasmBinaryWriter();
   private Map<String, WasmGCStringConstant> stringMap = new LinkedHashMap<>();
   private BaseWasmFunctionRepository functionProvider;
   private WasmFunction initNextStringFunction;
   private WasmFunction initStringsFunction;
   private WasmArray stringsArray;
   private WasmGCNameProvider names;
   private WasmFunctionTypes functionTypes;
   private DependencyInfo dependencyInfo;

   public WasmGCStringPool(
      WasmGCStandardClasses standardClasses,
      WasmModule module,
      BaseWasmFunctionRepository functionProvider,
      WasmGCNameProvider names,
      WasmFunctionTypes functionTypes,
      DependencyInfo dependencyInfo
   ) {
      this.standardClasses = standardClasses;
      this.module = module;
      this.functionProvider = functionProvider;
      this.names = names;
      this.functionTypes = functionTypes;
      this.dependencyInfo = dependencyInfo;
   }

   @Override
   public void contributeToInitializerDefinitions(WasmFunction function) {
      WasmMemorySegment segment = new WasmMemorySegment();
      this.module.getSegments().add(segment);
      segment.setData(this.binaryWriter.getData());
   }

   @Override
   public void contributeToInitializer(WasmFunction function) {
      if (this.initNextStringFunction != null) {
         WasmInstructionBuilder body = function.getBody().builder();
         if (this.hasIntern()) {
            WasmFunction internInit = this.functionProvider.forStaticMethod(new MethodReference(StringInternPool.class, "<clinit>", void.class));
            body.call(internInit);
         }

         Iterator<WasmGCStringConstant> stringIterator = this.stringMap.values().iterator();

         while (stringIterator.hasNext()) {
            int elementCount;
            for (elementCount = 0; elementCount < 10000 && stringIterator.hasNext(); elementCount++) {
               body.getGlobal(stringIterator.next().global);
            }

            body.arrayNewFixed(this.stringsArray, elementCount).call(this.initStringsFunction);
         }
      }
   }

   @Override
   public WasmGCStringConstant getStringConstant(String string) {
      return this.stringMap.computeIfAbsent(string, s -> {
         if (this.initNextStringFunction == null) {
            this.createInitNextStringFunction();
            this.createInitStringsFunction();
         }

         this.binaryWriter.writeLEB(string.length());
         this.writeWTF8(string, this.binaryWriter);
         String brief = string.length() > 16 ? string.substring(0, 16) : string;
         String globalName = this.names.topLevel("teavm@string<" + this.stringMap.size() + ">" + WasmGCNameProvider.sanitize(brief));
         WasmType.CompositeReference globalType = this.standardClasses.stringClass().getStructure().getNonNullReference();
         WasmGlobal global = new WasmGlobal(globalName, globalType);
         global.getInitialValue().add(new WasmStructNewDefault(this.standardClasses.stringClass().getStructure()));
         global.setImmutable(true);
         this.module.globals.add(global);
         return new WasmGCStringConstant(this.stringMap.size(), global);
      });
   }

   private void writeWTF8(String s, WasmBinaryWriter writer) {
      for (int i = 0; i < s.length(); i++) {
         int c = s.charAt(i);
         if (c < 128) {
            writer.writeByte(c);
         } else if (c < 2048) {
            writer.writeByte(192 | c >> 6 & 31);
            writer.writeByte(128 | c & 63);
         } else if (c < 65536) {
            writer.writeByte(224 | c >> 12 & 31);
            writer.writeByte(128 | c >> 6 & 63);
            writer.writeByte(128 | c & 63);
         }
      }
   }

   private void createInitStringsFunction() {
      this.stringsArray = new WasmArray(
         this.names.topLevel("teavm@stringArray"), this.standardClasses.stringClass().getStructure().getNonNullReference().asStorage()
      );
      this.module.types.add(this.stringsArray);
      WasmFunction function = new WasmFunction(this.functionTypes.of(null, this.stringsArray.getNonNullReference()));
      function.setName(this.names.topLevel("teavm@initStrings"));
      this.module.functions.add(function);
      WasmLocal stringsLocal = new WasmLocal(this.stringsArray.getNonNullReference(), "strings");
      WasmLocal indexLocal = new WasmLocal(WasmType.INT32, "index");
      WasmLocal lengthLocal = new WasmLocal(WasmType.INT32, "length");
      function.add(stringsLocal);
      function.add(indexLocal);
      function.add(lengthLocal);
      WasmInstructionBuilder body = function.getBody().builder();
      body.getLocal(stringsLocal).arrayLength().setLocal(lengthLocal);
      body.i32Const(0).setLocal(indexLocal);
      WasmInstructionBuilder loop = body.loop();
      loop.getLocal(stringsLocal).getLocal(indexLocal).arrayGet(this.stringsArray).call(this.initNextStringFunction);
      loop.getLocal(indexLocal).i32Const(1).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.ADD).teeLocal(indexLocal);
      loop.getLocal(lengthLocal).intBinary(WasmIntType.INT32, WasmIntBinaryOperation.LT_UNSIGNED).branch(loop);
      this.initStringsFunction = function;
   }

   private void createInitNextStringFunction() {
      WasmGCClassInfo stringTypeInfo = this.standardClasses.stringClass();
      WasmFunction nextCharArrayFunction = this.functionProvider.forStaticMethod(new MethodReference(WasmGCSupport.class, "nextCharArray", char[].class));
      WasmFunction function = new WasmFunction(this.functionTypes.of(null, stringTypeInfo.getStructure().getNonNullReference()));
      function.setName(this.names.topLevel("teavm@initNextString"));
      WasmLocal stringLocal = new WasmLocal(stringTypeInfo.getType());
      function.add(stringLocal);
      WasmInstructionBuilder body = function.getBody().builder();
      body.getLocal(stringLocal).call(nextCharArrayFunction).structSet(stringTypeInfo.getStructure(), 2);
      body.getLocal(stringLocal).getGlobal(stringTypeInfo.getVirtualTablePointer()).structSet(stringTypeInfo.getStructure(), 0);
      if (this.hasIntern()) {
         WasmFunction queryFunction = this.functionProvider.forStaticMethod(new MethodReference(StringInternPool.class, "query", String.class, String.class));
         body.getLocal(stringLocal).call(queryFunction).drop();
         this.functionProvider.forStaticMethod(new MethodReference(StringInternPool.class, "<clinit>", void.class));
      }

      this.module.functions.add(function);
      this.initNextStringFunction = function;
   }

   private boolean hasIntern() {
      MethodDependencyInfo intern = this.dependencyInfo.getMethod(new MethodReference(String.class, "intern", String.class));
      return intern != null && intern.isUsed();
   }
}
