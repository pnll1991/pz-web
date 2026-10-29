package org.teavm.backend.wasm.generate.reflection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfo;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.instruction.WasmInstructionBuilder;
import org.teavm.model.AnnotationValue;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class AnnotationDataStruct {
   private WasmModule module;
   private WasmGCNameProvider names;
   private String className;
   private ClassReaderSource classes;
   private BaseWasmFunctionRepository functions;
   private WasmGCClassInfoProvider classInfoProvider;
   private ReflectionTypes reflectionTypes;
   private WasmStructure structure;
   private Map<String, AnnotationDataStruct.Field> fieldsByName = new HashMap<>();
   private List<AnnotationDataStruct.Field> fields = new ArrayList<>();
   private WasmFunction constructor;

   AnnotationDataStruct(
      String className,
      String dataClassName,
      WasmGCNameProvider names,
      WasmModule module,
      ClassReaderSource classes,
      BaseWasmFunctionRepository functions,
      WasmGCClassInfoProvider classInfoProvider,
      ReflectionTypes reflectionTypes
   ) {
      this.module = module;
      this.names = names;
      this.classes = classes;
      this.reflectionTypes = reflectionTypes;
      this.functions = functions;
      this.classInfoProvider = classInfoProvider;
      this.className = className;
      this.structure = new WasmStructure(names.topLevel(dataClassName), this::initFields);
      module.types.add(this.structure);
   }

   public WasmStructure structure() {
      return this.structure;
   }

   private void initFields(List<WasmField> fields) {
      ClassReader cls = this.classes.get(this.className);
      if (cls != null) {
         for (MethodReader method : cls.getMethods()) {
            int index = fields.size();
            WasmField field = new WasmField(this.reflectionTypes.typeForAnnotation(method.getResultType()), method.getName());
            fields.add(field);
            AnnotationDataStruct.Field fieldData = new AnnotationDataStruct.Field(
               method.getName(), method.getResultType(), method.getAnnotationDefault(), index
            );
            this.fieldsByName.put(method.getName(), fieldData);
            this.fields.add(fieldData);
         }
      }
   }

   public AnnotationDataStruct.Field field(String name) {
      this.structure.init();
      return this.fieldsByName.get(name);
   }

   public List<? extends AnnotationDataStruct.Field> fields() {
      this.structure.init();
      return this.fields;
   }

   public WasmFunction constructor() {
      if (this.constructor == null) {
         WasmFunction fn = new WasmFunction(this.reflectionTypes.annotationInfo().constructorType());
         fn.setName(this.names.topLevel(this.names.suggestForClass(this.className) + "@constructor"));
         String dataClassName = this.className + "$$_nativeData";
         String implClassName = this.className + "$$_impl";
         WasmGCClassInfo wasmDataType = this.classInfoProvider.getClassInfo(implClassName);
         WasmLocal param = new WasmLocal(fn.getType().getParameterTypes().get(0));
         WasmLocal result = new WasmLocal(wasmDataType.getType());
         fn.add(param);
         fn.add(result);
         WasmInstructionBuilder body = fn.getBody().builder();
         body.structNewDefault(wasmDataType.getStructure())
            .teeLocal(result)
            .getGlobal(wasmDataType.getVirtualTablePointer())
            .structSet(wasmDataType.getStructure(), 0);
         MethodReference ctorRef = new MethodReference(implClassName, "<init>", ValueType.object(dataClassName), ValueType.VOID);
         WasmFunction ctor = this.functions.forInstanceMethod(ctorRef);
         body.getLocal(result).getLocal(param).cast(this.structure.getReference()).call(ctor);
         body.getLocal(result);
         this.module.functions.add(fn);
         this.constructor = fn;
      }

      return this.constructor;
   }

   public static class Field {
      public final String name;
      public final ValueType type;
      public final AnnotationValue defaultValue;
      public final int index;

      private Field(String name, ValueType type, AnnotationValue defaultValue, int index) {
         this.name = name;
         this.type = type;
         this.defaultValue = defaultValue;
         this.index = index;
      }
   }
}
