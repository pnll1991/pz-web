package org.teavm.backend.wasm.generate.reflection;

import java.util.List;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.dependency.DependencyInfo;
import org.teavm.model.MethodReference;
import org.teavm.runtime.reflect.GenericTypeInfo;
import org.teavm.runtime.reflect.MethodReflectionInfo;

public class MethodReflectionInfoStruct {
   private final DependencyInfo dependency;
   private final WasmGCClassInfoProvider classInfoProvider;
   private WasmStructure structure;
   private int genericReturnTypeIndex = -1;
   private int parameterInfosIndex = -1;
   private int annotationsIndex = -1;
   private int typeParametersIndex = -1;

   MethodReflectionInfoStruct(WasmGCNameProvider names, WasmModule module, DependencyInfo dependency, WasmGCClassInfoProvider classInfoProvider) {
      this.dependency = dependency;
      this.classInfoProvider = classInfoProvider;
      String name = names.topLevel(names.suggestForClass(MethodReflectionInfo.class.getName()));
      this.structure = new WasmStructure(name, this::initFields);
      module.types.add(this.structure);
   }

   private void initFields(List<WasmField> fields) {
      if (this.dependency.getMethod(new MethodReference(MethodReflectionInfo.class, "genericReturnType", GenericTypeInfo.class)) != null) {
         this.genericReturnTypeIndex = fields.size();
         fields.add(new WasmField(WasmType.STRUCT, "genericReturnType"));
      }

      if (this.dependency.getMethod(new MethodReference(MethodReflectionInfo.class, "parameterInfoCount", int.class)) != null) {
         this.parameterInfosIndex = fields.size();
         fields.add(new WasmField(this.classInfoProvider.reflectionTypes().parameterInfo().array(), "parameterInfos"));
      }

      if (this.dependency.getMethod(new MethodReference(MethodReflectionInfo.class, "annotationCount", int.class)) != null) {
         this.annotationsIndex = fields.size();
         AnnotationInfoStruct annotStruct = this.classInfoProvider.reflectionTypes().annotationInfo();
         fields.add(new WasmField(annotStruct.array(), "annotations"));
      }

      if (this.dependency.getMethod(new MethodReference(MethodReflectionInfo.class, "typeParameterCount", int.class)) != null) {
         this.typeParametersIndex = fields.size();
         TypeVariableInfoStruct typeParamStruct = this.classInfoProvider.reflectionTypes().typeVariableInfo();
         fields.add(new WasmField(typeParamStruct.array(), "typeParameters"));
      }
   }

   public WasmStructure structure() {
      return this.structure;
   }

   public int genericReturnTypeIndex() {
      this.init();
      return this.genericReturnTypeIndex;
   }

   public int parameterInfosIndex() {
      this.init();
      return this.parameterInfosIndex;
   }

   public int annotationsIndex() {
      this.init();
      return this.annotationsIndex;
   }

   public int typeParametersIndex() {
      this.init();
      return this.typeParametersIndex;
   }

   private void init() {
      this.structure.init();
   }
}
