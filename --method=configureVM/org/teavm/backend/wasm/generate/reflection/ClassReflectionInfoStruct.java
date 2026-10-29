package org.teavm.backend.wasm.generate.reflection;

import java.util.List;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.dependency.DependencyInfo;
import org.teavm.model.MethodReference;
import org.teavm.model.analysis.ClassMetadataRequirements;
import org.teavm.runtime.reflect.ClassReflectionInfo;

public class ClassReflectionInfoStruct {
   private final DependencyInfo dependencies;
   private final ClassMetadataRequirements metadataRequirements;
   private final ReflectionTypes reflectionTypes;
   private WasmStructure structure;
   private int annotationsIndex = -1;
   private int fieldsIndex = -1;
   private int methodsIndex = -1;
   private int typeParametersIndex = -1;
   private int innerClassesIndex = -1;

   ClassReflectionInfoStruct(
      WasmGCNameProvider names, WasmModule module, DependencyInfo dependencies, ClassMetadataRequirements metadataRequirements, ReflectionTypes reflectionTypes
   ) {
      this.dependencies = dependencies;
      this.metadataRequirements = metadataRequirements;
      this.reflectionTypes = reflectionTypes;
      String structName = names.topLevel(names.suggestForClass(ClassReflectionInfo.class.getName()));
      this.structure = new WasmStructure(structName, this::initFields);
      module.types.add(this.structure);
   }

   private void initFields(List<WasmField> fields) {
      if (this.metadataRequirements.hasGetAnnotations()) {
         this.annotationsIndex = fields.size();
         fields.add(new WasmField(this.reflectionTypes.annotationInfo().array(), "annotations"));
      }

      if (this.metadataRequirements.hasGetFields()) {
         this.fieldsIndex = fields.size();
         fields.add(new WasmField(this.reflectionTypes.fieldInfo().array(), "fields"));
      }

      if (this.metadataRequirements.hasGetMethods()) {
         this.methodsIndex = fields.size();
         fields.add(new WasmField(this.reflectionTypes.methodInfo().array(), "methods"));
      }

      if (this.dependencies.getMethod(new MethodReference(ClassReflectionInfo.class, "typeParameterCount", int.class)) != null) {
         this.typeParametersIndex = fields.size();
         fields.add(new WasmField(this.reflectionTypes.typeVariableInfo().array(), "typeParameters"));
      }

      if (this.dependencies.getMethod(new MethodReference(ClassReflectionInfo.class, "innerClassCount", int.class)) != null) {
         this.innerClassesIndex = fields.size();
         fields.add(new WasmField(this.reflectionTypes.classInfo().array(), "innerClasses"));
      }
   }

   public WasmStructure structure() {
      return this.structure;
   }

   public int annotationsIndex() {
      this.init();
      return this.annotationsIndex;
   }

   public int fieldsIndex() {
      this.init();
      return this.fieldsIndex;
   }

   public int methodsIndex() {
      this.init();
      return this.methodsIndex;
   }

   public int typeParametersIndex() {
      this.init();
      return this.typeParametersIndex;
   }

   public int innerClassesIndex() {
      this.init();
      return this.innerClassesIndex;
   }

   private void init() {
      this.structure.init();
   }
}
