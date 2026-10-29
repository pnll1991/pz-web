package org.teavm.backend.wasm.generate.reflection;

import java.util.List;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmNumType;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.dependency.DependencyInfo;
import org.teavm.model.MethodReference;
import org.teavm.runtime.StringInfo;
import org.teavm.runtime.reflect.DerivedClassInfo;
import org.teavm.runtime.reflect.FieldInfo;
import org.teavm.runtime.reflect.FieldReflectionInfo;

public class FieldInfoStruct {
   private final WasmGCNameProvider names;
   private final WasmModule module;
   private final WasmFunctionTypes functionTypes;
   private final DependencyInfo dependencies;
   private final WasmGCClassInfoProvider classInfoProvider;
   private WasmStructure structure;
   private int nameIndex = -1;
   private int modifiersIndex = -1;
   private int typeIndex = -1;
   private int readerIndex = -1;
   private int readerConverterIndex = -1;
   private int writerIndex = -1;
   private int writerConverterIndex = -1;
   private int reflectionIndex = -1;
   private WasmFunctionType readerConverterType;
   private WasmFunctionType writerConverterType;
   private WasmFunctionType[] numericReaderTypes = new WasmFunctionType[WasmNumType.values().length];
   private WasmFunctionType objectReaderType;
   private WasmFunctionType[] numericWriterTypes = new WasmFunctionType[WasmNumType.values().length];
   private WasmFunctionType objectWriterType;
   private WasmArray array;

   FieldInfoStruct(
      WasmGCNameProvider names, WasmModule module, WasmFunctionTypes functionTypes, DependencyInfo dependencies, WasmGCClassInfoProvider classInfoProvider
   ) {
      this.names = names;
      this.module = module;
      this.functionTypes = functionTypes;
      this.dependencies = dependencies;
      this.classInfoProvider = classInfoProvider;
      String name = names.topLevel(names.suggestForClass(FieldInfo.class.getName()));
      this.structure = new WasmStructure(name, this::initFields);
      module.types.add(this.structure);
   }

   private void initFields(List<WasmField> fields) {
      if (this.dependencies.getMethod(new MethodReference(FieldInfo.class, "name", StringInfo.class)) != null) {
         this.nameIndex = fields.size();
         WasmType.CompositeReference strType = this.classInfoProvider.getClassInfo("java.lang.String").getType();
         fields.add(new WasmField(strType, "name"));
      }

      if (this.dependencies.getMethod(new MethodReference(FieldInfo.class, "modifiers", int.class)) != null) {
         this.modifiersIndex = fields.size();
         fields.add(new WasmField(WasmType.INT32, "modifiers"));
      }

      if (this.dependencies.getMethod(new MethodReference(FieldInfo.class, "type", DerivedClassInfo.class)) != null) {
         this.typeIndex = fields.size();
         WasmType.CompositeReference infoType = this.classInfoProvider.reflectionTypes().derivedClassInfo().structure().getReference();
         fields.add(new WasmField(infoType, "type"));
      }

      if (this.needsAnyReader()) {
         this.readerIndex = fields.size();
         fields.add(new WasmField(WasmType.FUNC, "reader"));
      }

      if (this.dependencies.getMethod(new MethodReference(FieldInfo.class, "read", Object.class, Object.class)) != null) {
         WasmType.CompositeReference objType = this.classInfoProvider.getClassInfo("java.lang.Object").getType();
         this.readerConverterType = this.functionTypes.of(objType, WasmType.FUNC, objType);
         this.readerConverterIndex = fields.size();
         fields.add(new WasmField(this.readerConverterType, "readerConverter"));
      }

      if (this.needsAnyWriter()) {
         this.writerIndex = fields.size();
         fields.add(new WasmField(WasmType.FUNC, "writer"));
      }

      if (this.dependencies.getMethod(new MethodReference(FieldInfo.class, "write", Object.class, Object.class, void.class)) != null) {
         WasmType.CompositeReference objType = this.classInfoProvider.getClassInfo("java.lang.Object").getType();
         this.writerConverterType = this.functionTypes.of(null, WasmType.FUNC, objType, objType);
         this.writerConverterIndex = fields.size();
         fields.add(new WasmField(this.writerConverterType, "writerConverter"));
      }

      if (this.dependencies.getMethod(new MethodReference(FieldInfo.class, "reflection", FieldReflectionInfo.class)) != null) {
         this.reflectionIndex = fields.size();
         WasmStructure reflectionStruct = this.classInfoProvider.reflectionTypes().fieldReflectionInfo().structure();
         fields.add(new WasmField(reflectionStruct, "reflection"));
      }
   }

   private boolean needsAnyReader() {
      return this.dependencies.getMethod(new MethodReference(FieldInfo.class, "read", Object.class, Object.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "readAsBoolean", Object.class, boolean.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "readAsByte", Object.class, byte.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "readAsShort", Object.class, short.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "readAsChar", Object.class, char.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "readAsInt", Object.class, int.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "readAsLong", Object.class, long.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "readAsFloat", Object.class, float.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "readAsDouble", Object.class, double.class)) != null;
   }

   private boolean needsAnyWriter() {
      return this.dependencies.getMethod(new MethodReference(FieldInfo.class, "write", Object.class, Object.class, void.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "write", Object.class, boolean.class, void.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "write", Object.class, byte.class, void.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "write", Object.class, short.class, void.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "write", Object.class, char.class, void.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "write", Object.class, int.class, void.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "write", Object.class, long.class, void.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "write", Object.class, float.class, void.class)) != null
         || this.dependencies.getMethod(new MethodReference(FieldInfo.class, "write", Object.class, double.class, void.class)) != null;
   }

   public WasmStructure structure() {
      return this.structure;
   }

   public WasmArray array() {
      if (this.array == null) {
         String name = this.names.topLevel(this.names.suggestForArray(this.names.suggestForClass(FieldInfo.class.getName())));
         this.array = new WasmArray(name, this.structure.getReference().asStorage());
         this.module.types.add(this.array);
      }

      return this.array;
   }

   public int nameIndex() {
      this.init();
      return this.nameIndex;
   }

   public int modifiersIndex() {
      this.init();
      return this.modifiersIndex;
   }

   public int typeIndex() {
      this.init();
      return this.typeIndex;
   }

   public int readerIndex() {
      this.init();
      return this.readerIndex;
   }

   public int readerConverterIndex() {
      this.init();
      return this.readerConverterIndex;
   }

   public int writerIndex() {
      this.init();
      return this.writerIndex;
   }

   public int writerConverterIndex() {
      this.init();
      return this.writerConverterIndex;
   }

   public int reflectionIndex() {
      this.init();
      return this.reflectionIndex;
   }

   public WasmFunctionType readerConverterType() {
      this.init();
      return this.readerConverterType;
   }

   public WasmFunctionType writerConverterType() {
      this.init();
      return this.writerConverterType;
   }

   public WasmFunctionType rawReaderFunctionType(WasmType rawWasmType) {
      WasmType.CompositeReference objType = this.classInfoProvider.getClassInfo("java.lang.Object").getType();
      if (rawWasmType instanceof WasmType.Number numType) {
         int idx = numType.number.ordinal();
         if (this.numericReaderTypes[idx] == null) {
            this.numericReaderTypes[idx] = this.functionTypes.of(rawWasmType, objType);
         }

         return this.numericReaderTypes[idx];
      } else {
         if (this.objectReaderType == null) {
            this.objectReaderType = this.functionTypes.of(objType, objType);
         }

         return this.objectReaderType;
      }
   }

   public WasmFunctionType rawWriterFunctionType(WasmType rawWasmType) {
      WasmType.CompositeReference objType = this.classInfoProvider.getClassInfo("java.lang.Object").getType();
      if (rawWasmType instanceof WasmType.Number numType) {
         int idx = numType.number.ordinal();
         if (this.numericWriterTypes[idx] == null) {
            this.numericWriterTypes[idx] = this.functionTypes.of(null, objType, rawWasmType);
         }

         return this.numericWriterTypes[idx];
      } else {
         if (this.objectWriterType == null) {
            this.objectWriterType = this.functionTypes.of(null, objType, objType);
         }

         return this.objectWriterType;
      }
   }

   private void init() {
      this.structure.init();
   }
}
