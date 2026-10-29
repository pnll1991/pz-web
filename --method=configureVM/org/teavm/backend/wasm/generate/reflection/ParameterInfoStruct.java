package org.teavm.backend.wasm.generate.reflection;

import java.util.List;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.model.WasmArray;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.dependency.DependencyInfo;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.GenericTypeInfo;
import org.teavm.runtime.reflect.ParameterInfo;

public class ParameterInfoStruct {
   private final DependencyInfo dependency;
   private final WasmGCClassInfoProvider classInfoProvider;
   private WasmStructure structure;
   private WasmArray array;
   private int annotationsIndex = -1;
   private int genericTypeIndex = -1;

   public ParameterInfoStruct(WasmGCNameProvider names, WasmModule module, DependencyInfo dependency, WasmGCClassInfoProvider classInfoProvider) {
      this.dependency = dependency;
      this.classInfoProvider = classInfoProvider;
      String name = names.topLevel(names.suggestForClass(ParameterInfo.class.getName()));
      this.structure = new WasmStructure(name, this::initFields);
      module.types.add(this.structure);
      String arrayName = names.topLevel(names.suggestForArray(names.suggestForClass(ParameterInfo.class.getName())));
      this.array = new WasmArray(arrayName, this.structure.getReference().asStorage());
      module.types.add(this.array);
   }

   private void initFields(List<WasmField> fields) {
      if (this.dependency.getMethod(new MethodReference(ParameterInfo.class, "annotationCount", int.class)) != null) {
         this.annotationsIndex = fields.size();
         AnnotationInfoStruct annotStruct = this.classInfoProvider.reflectionTypes().annotationInfo();
         fields.add(new WasmField(annotStruct.array(), "annotations"));
      }

      if (this.dependency.getMethod(new MethodReference(ParameterInfo.class, "genericType", GenericTypeInfo.class)) != null) {
         this.genericTypeIndex = fields.size();
         fields.add(new WasmField(WasmType.STRUCT, "genericType"));
      }
   }

   public WasmStructure structure() {
      return this.structure;
   }

   public WasmArray array() {
      return this.array;
   }

   public int annotationsIndex() {
      this.init();
      return this.annotationsIndex;
   }

   public int genericTypeIndex() {
      this.init();
      return this.genericTypeIndex;
   }

   private void init() {
      this.structure.init();
   }
}
