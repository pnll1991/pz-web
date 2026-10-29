package org.teavm.parsing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.function.Function;
import org.teavm.interop.Remove;
import org.teavm.interop.Rename;
import org.teavm.interop.Superclass;
import org.teavm.model.AnnotationContainer;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.AnnotationValue;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReference;
import org.teavm.model.GenericTypeParameter;
import org.teavm.model.GenericValueType;
import org.teavm.model.Instruction;
import org.teavm.model.InvokeDynamicInstruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHandle;
import org.teavm.model.MethodHolder;
import org.teavm.model.Program;
import org.teavm.model.ReferenceCache;
import org.teavm.model.RuntimeConstant;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.PutFieldInstruction;

public class ClassRefsRenamer extends AbstractInstructionVisitor {
   private ReferenceCache referenceCache;
   private Function<String, String> classNameMapper;

   public ClassRefsRenamer(ReferenceCache referenceCache, Function<String, String> classNameMapper) {
      this.referenceCache = referenceCache;
      this.classNameMapper = classNameMapper;
   }

   public ClassHolder rename(ClassHolder cls) {
      String newName = this.classNameMapper.apply(cls.getName());
      ClassHolder renamedCls = !newName.equals(cls.getName()) ? new ClassHolder(newName) : cls;
      if (renamedCls != cls) {
         renamedCls.getModifiers().addAll(cls.getModifiers());
         renamedCls.setLevel(cls.getLevel());
      }

      String parent = cls.getParent();
      AnnotationHolder superclassAnnot = cls.getAnnotations().get(Superclass.class.getName());
      if (superclassAnnot != null) {
         parent = superclassAnnot.getValues().get("value").getString();
         if (parent.isEmpty()) {
            parent = null;
         }
      }

      renamedCls.setParent(parent != null ? this.classNameMapper.apply(parent) : null);
      if (renamedCls.getName().equals(renamedCls.getParent())) {
         renamedCls.setParent(null);
      }

      boolean methodsChanged = renamedCls != cls;
      ArrayList<MethodHolder> newMethods = new ArrayList<>();

      for (MethodHolder method : cls.getMethods()) {
         if (method.getAnnotations().get(Remove.class.getName()) != null) {
            methodsChanged = true;
         } else {
            MethodHolder newMethod = this.rename(method);
            if (newMethod != method) {
               methodsChanged = true;
            }

            newMethods.add(newMethod);
         }
      }

      if (methodsChanged) {
         cls.removeAllMethods();

         for (MethodHolder method : newMethods) {
            renamedCls.addMethod(method);
         }
      }

      boolean fieldsChanged = cls != renamedCls;
      ArrayList<FieldHolder> newFields = new ArrayList<>();

      for (FieldHolder field : cls.getFields()) {
         FieldHolder newField = this.rename(field);
         if (newField != field) {
            fieldsChanged = true;
         }

         newFields.add(this.rename(field));
      }

      if (fieldsChanged) {
         cls.removeAllFields();

         for (FieldHolder field : newFields) {
            renamedCls.addField(field);
         }
      }

      if (cls.getOwnerName() != null) {
         renamedCls.setOwnerName(this.classNameMapper.apply(cls.getOwnerName()));
      }

      if (cls.getDeclaringClassName() != null) {
         renamedCls.setDeclaringClassName(this.classNameMapper.apply(cls.getDeclaringClassName()));
      }

      this.rename(cls.getAnnotations(), renamedCls.getAnnotations());
      boolean interfacesChanged = cls != renamedCls;
      ArrayList<String> newInterfaces = new ArrayList<>();

      for (String iface : cls.getInterfaces()) {
         String mappedIfaceName = this.classNameMapper.apply(iface);
         if (mappedIfaceName.equals(renamedCls.getName())) {
            interfacesChanged = true;
         } else {
            if (!mappedIfaceName.equals(iface)) {
               interfacesChanged = true;
            }

            newInterfaces.add(mappedIfaceName);
         }
      }

      if (interfacesChanged) {
         renamedCls.getInterfaces().clear();
         renamedCls.getInterfaces().addAll(newInterfaces);
      }

      GenericValueType.Object genericParent = cls.getGenericParent();
      if (genericParent != null) {
         renamedCls.setGenericParent((GenericValueType.Object)this.rename(genericParent));
      }

      boolean genericInterfacesChanged = cls != renamedCls;
      ArrayList<GenericValueType.Object> newGenericInterfaces = new ArrayList<>();

      for (GenericValueType.Object genericInterface : cls.getGenericInterfaces()) {
         GenericValueType.Object newGenericInterface = (GenericValueType.Object)this.rename(genericInterface);
         if (newGenericInterface != genericInterface) {
            genericInterfacesChanged = true;
         }

         newGenericInterfaces.add(newGenericInterface);
      }

      if (genericInterfacesChanged) {
         renamedCls.getGenericInterfaces().clear();
         renamedCls.getGenericInterfaces().addAll(newGenericInterfaces);
      }

      if (cls.getGenericParameters() != null) {
         renamedCls.setGenericParameters(this.rename(cls.getGenericParameters()));
      }

      renamedCls.getInnerClasses().addAll(cls.getInnerClasses());
      renamedCls.getInnerClasses().replaceAll(this.classNameMapper::apply);
      return renamedCls;
   }

   public MethodHolder rename(MethodHolder method) {
      String methodName = method.getName();
      AnnotationHolder renameAnnot = method.getAnnotations().get(Rename.class.getName());
      if (renameAnnot != null) {
         methodName = renameAnnot.getValues().get("value").getString();
      }

      ValueType[] signature = method.getSignature();

      for (int i = 0; i < signature.length; i++) {
         signature[i] = this.rename(signature[i]);
      }

      MethodDescriptor newDescriptor = new MethodDescriptor(methodName, signature);
      MethodHolder renamedMethod = newDescriptor.equals(method.getDescriptor()) ? method : new MethodHolder(this.referenceCache.getCached(newDescriptor));
      if (renamedMethod != method) {
         renamedMethod.getModifiers().addAll(method.getModifiers());
         renamedMethod.setLevel(method.getLevel());
         renamedMethod.setProgram(method.getProgram());
      }

      this.rename(method.getAnnotations(), renamedMethod.getAnnotations());

      for (int i = 0; i < method.parameterCount(); i++) {
         this.rename(method.parameterAnnotation(i), renamedMethod.parameterAnnotation(i));
      }

      boolean thrownTypesChanged = renamedMethod != method;
      ArrayList<String> newThrownTypes = new ArrayList<>();

      for (String thrownType : method.getThrownTypes()) {
         String mappedThrownType = this.classNameMapper.apply(thrownType);
         if (!mappedThrownType.equals(thrownType)) {
            thrownTypesChanged = true;
         }

         newThrownTypes.add(mappedThrownType);
      }

      if (thrownTypesChanged) {
         method.setThrownTypes(newThrownTypes);
      }

      if (renamedMethod.getProgram() != null) {
         this.rename(renamedMethod.getProgram());
      }

      renamedMethod.setTypeParameters(this.rename(method.getTypeParameters()));
      GenericValueType genericResultType = method.getGenericResultType();
      boolean genericSignatureChanged = renamedMethod != method;
      if (genericResultType != null) {
         GenericValueType newGenericResultType = this.rename(genericResultType);
         if (newGenericResultType != genericResultType) {
            genericSignatureChanged = true;
         }

         genericResultType = newGenericResultType;
      }

      GenericValueType[] genericParameters = method.getGenericParameterTypes();
      if (genericParameters != null) {
         GenericValueType[] newGenericParameters = new GenericValueType[genericParameters.length];

         for (int i = 0; i < genericParameters.length; i++) {
            GenericValueType newGenericParameter = this.rename(genericParameters[i]);
            if (newGenericParameter != genericParameters[i]) {
               genericSignatureChanged = true;
            }

            newGenericParameters[i] = newGenericParameter;
         }

         genericParameters = newGenericParameters;
      }

      if (genericSignatureChanged) {
         renamedMethod.setGenericSignature(genericResultType, genericParameters);
      }

      return renamedMethod;
   }

   private GenericTypeParameter[] rename(GenericTypeParameter[] typeParameters) {
      for (int i = 0; i < typeParameters.length; i++) {
         typeParameters[i] = this.rename(typeParameters[i]);
      }

      return typeParameters;
   }

   private GenericTypeParameter rename(GenericTypeParameter typeParam) {
      String name = this.classNameMapper.apply(typeParam.getName());
      GenericValueType.Reference renamedClassBound = typeParam.getClassBound() != null
         ? (GenericValueType.Reference)this.rename(typeParam.getClassBound())
         : null;
      GenericValueType.Reference[] renamedInterfaceBounds = typeParam.getInterfaceBounds();
      boolean interfacesRenamed = false;

      for (int i = 0; i < renamedInterfaceBounds.length; i++) {
         GenericValueType.Reference bound = (GenericValueType.Reference)this.rename(renamedInterfaceBounds[i]);
         if (!bound.equals(renamedInterfaceBounds[i])) {
            interfacesRenamed = true;
            renamedInterfaceBounds[i] = bound;
         }
      }

      return !interfacesRenamed && Objects.equals(renamedClassBound, typeParam.getClassBound()) && !name.equals(typeParam.getName())
         ? typeParam
         : new GenericTypeParameter(name, renamedClassBound, renamedInterfaceBounds);
   }

   public FieldHolder rename(FieldHolder field) {
      field.setType(this.rename(field.getType()));
      field.setInitialValue(field.getInitialValue());
      this.rename(field.getAnnotations(), field.getAnnotations());
      GenericValueType genericType = field.getGenericType();
      if (genericType != null) {
         field.setGenericType(this.rename(genericType));
      }

      return field;
   }

   private ValueType rename(ValueType type) {
      if (type instanceof ValueType.Array) {
         ValueType itemType = ((ValueType.Array)type).getItemType();
         return this.referenceCache.getCached(ValueType.arrayOf(this.rename(itemType)));
      } else if (type instanceof ValueType.Object) {
         String className = ((ValueType.Object)type).getClassName();
         return this.referenceCache.getCached(ValueType.object(this.classNameMapper.apply(className)));
      } else {
         return type;
      }
   }

   private GenericValueType rename(GenericValueType type) {
      if (type instanceof GenericValueType.Array) {
         GenericValueType itemType = ((GenericValueType.Array)type).getItemType();
         return this.referenceCache.getCached(new GenericValueType.Array(this.rename(itemType)));
      }

      if (type instanceof GenericValueType.Object object) {
         String className = this.classNameMapper.apply(object.getClassName());
         GenericValueType.Object parent = object.getParent();
         if (parent != null) {
            parent = (GenericValueType.Object)this.rename(parent);
         }

         GenericValueType.Argument[] arguments = object.getArguments();

         for (int i = 0; i < arguments.length; i++) {
            GenericValueType.Argument argument = arguments[i];
            GenericValueType.Reference value = argument.getValue();
            if (value != null) {
               value = (GenericValueType.Reference)this.rename(value);
            }

            switch (argument.getKind()) {
               case INVARIANT:
                  arguments[i] = GenericValueType.Argument.invariant(value);
                  break;
               case COVARIANT:
                  arguments[i] = GenericValueType.Argument.covariant(value);
                  break;
               case CONTRAVARIANT:
                  arguments[i] = GenericValueType.Argument.contravariant(value);
            }
         }

         return this.referenceCache.getCached(new GenericValueType.Object(parent, className, arguments));
      } else {
         return type;
      }
   }

   private ValueType[] rename(ValueType[] types) {
      return Arrays.stream(types).map(this::rename).toArray(ValueType[]::new);
   }

   private RuntimeConstant rename(RuntimeConstant cst) {
      return switch (cst.getKind()) {
         case 5 -> new RuntimeConstant(this.rename(cst.getValueType()));
         case 6 -> new RuntimeConstant(this.rename(cst.getMethodType()));
         case 7 -> new RuntimeConstant(this.rename(cst.getMethodHandle()));
         default -> cst;
      };
   }

   private MethodHandle rename(MethodHandle handle) {
      return switch (handle.getKind()) {
         case GET_FIELD -> MethodHandle.fieldGetter(this.classNameMapper.apply(handle.getClassName()), handle.getName(), this.rename(handle.getValueType()));
         case GET_STATIC_FIELD -> MethodHandle.staticFieldGetter(
            this.classNameMapper.apply(handle.getClassName()), handle.getName(), this.rename(handle.getValueType())
         );
         case PUT_FIELD -> MethodHandle.fieldSetter(this.classNameMapper.apply(handle.getClassName()), handle.getName(), this.rename(handle.getValueType()));
         case PUT_STATIC_FIELD -> MethodHandle.staticFieldSetter(
            this.classNameMapper.apply(handle.getClassName()), handle.getName(), this.rename(handle.getValueType())
         );
         case INVOKE_VIRTUAL -> MethodHandle.virtualCaller(this.classNameMapper.apply(handle.getClassName()), handle.getName(), this.rename(handle.signature()));
         case INVOKE_STATIC -> MethodHandle.staticCaller(this.classNameMapper.apply(handle.getClassName()), handle.getName(), this.rename(handle.signature()));
         case INVOKE_SPECIAL -> MethodHandle.specialCaller(this.classNameMapper.apply(handle.getClassName()), handle.getName(), this.rename(handle.signature()));
         case INVOKE_CONSTRUCTOR -> MethodHandle.constructorCaller(
            this.classNameMapper.apply(handle.getClassName()), handle.getName(), this.rename(handle.signature())
         );
         case INVOKE_INTERFACE -> MethodHandle.interfaceCaller(
            this.classNameMapper.apply(handle.getClassName()), handle.getName(), this.rename(handle.signature())
         );
      };
   }

   private void rename(AnnotationContainer source, AnnotationContainer target) {
      ArrayList<AnnotationHolder> newAnnotations = new ArrayList<>();
      boolean annotationsChanged = target != source;

      for (AnnotationHolder annot : source.all()) {
         if (!annot.getType().equals(Rename.class.getName()) && !annot.getType().equals(Superclass.class.getName())) {
            AnnotationHolder newAnnot = this.rename(annot);
            if (newAnnot != annot) {
               annotationsChanged = true;
            }

            newAnnotations.add(newAnnot);
         } else {
            annotationsChanged = true;
         }
      }

      if (annotationsChanged) {
         target.removeAll();

         for (AnnotationHolder newAnnot : newAnnotations) {
            target.add(newAnnot);
         }
      }
   }

   private AnnotationHolder rename(AnnotationHolder annot) {
      String newName = this.classNameMapper.apply(annot.getType());
      AnnotationHolder renamedAnnot = newName.equals(annot.getType()) ? annot : new AnnotationHolder(this.classNameMapper.apply(annot.getType()));
      if (renamedAnnot != annot) {
         for (Entry<String, AnnotationValue> entry : annot.getValues().entrySet()) {
            renamedAnnot.getValues().put(entry.getKey(), this.rename(entry.getValue()));
         }
      } else {
         for (Entry<String, AnnotationValue> entry : annot.getValues().entrySet()) {
            entry.setValue(this.rename(entry.getValue()));
         }
      }

      return renamedAnnot;
   }

   private AnnotationValue rename(AnnotationValue value) {
      return switch (value.getType()) {
         case 8 -> {
            ValueType newJavaClass = this.rename(value.getJavaClass());
            yield newJavaClass.equals(value.getJavaClass()) ? value : new AnnotationValue(newJavaClass);
         }
         case 9 -> {
            ArrayList<AnnotationValue> newList = new ArrayList<>();
            boolean changed = false;

            for (AnnotationValue item : value.getList()) {
               AnnotationValue newItem = this.rename(item);
               if (newItem != item) {
                  changed = true;
               }

               newList.add(newItem);
            }

            yield changed ? new AnnotationValue(newList) : value;
         }
         default -> value;
         case 11 -> {
            AnnotationHolder newAnnot = this.rename((AnnotationHolder)value.getAnnotation());
            yield newAnnot != value.getAnnotation() ? new AnnotationValue(newAnnot) : value;
         }
      };
   }

   public void rename(Program program) {
      for (int i = 0; i < program.basicBlockCount(); i++) {
         BasicBlock basicBlock = program.basicBlockAt(i);

         for (Instruction insn : basicBlock) {
            insn.acceptVisitor(this);
         }

         for (TryCatchBlock tryCatch : basicBlock.getTryCatchBlocks()) {
            if (tryCatch.getExceptionType() != null) {
               tryCatch.setExceptionType(this.classNameMapper.apply(tryCatch.getExceptionType()));
            }
         }
      }
   }

   @Override
   public void visit(ClassConstantInstruction insn) {
      insn.setConstant(this.rename(insn.getConstant()));
   }

   @Override
   public void visit(CastInstruction insn) {
      insn.setTargetType(this.rename(insn.getTargetType()));
   }

   @Override
   public void visit(ConstructArrayInstruction insn) {
      insn.setItemType(this.rename(insn.getItemType()));
   }

   @Override
   public void visit(ConstructInstruction insn) {
      insn.setType(this.classNameMapper.apply(insn.getType()));
   }

   @Override
   public void visit(ConstructMultiArrayInstruction insn) {
      insn.setItemType(this.rename(insn.getItemType()));
   }

   @Override
   public void visit(GetFieldInstruction insn) {
      String className = this.classNameMapper.apply(insn.getField().getClassName());
      insn.setField(this.referenceCache.getCached(new FieldReference(className, insn.getField().getFieldName())));
      insn.setFieldType(this.rename(insn.getFieldType()));
   }

   @Override
   public void visit(PutFieldInstruction insn) {
      String className = this.classNameMapper.apply(insn.getField().getClassName());
      if (className != insn.getField().getClassName()) {
         insn.setField(this.referenceCache.getCached(new FieldReference(className, insn.getField().getFieldName())));
      }

      insn.setFieldType(this.rename(insn.getFieldType()));
   }

   @Override
   public void visit(InvokeInstruction insn) {
      String className = this.classNameMapper.apply(insn.getMethod().getClassName());
      ValueType[] signature = insn.getMethod().getSignature();
      boolean changed = true;

      for (int i = 0; i < signature.length; i++) {
         ValueType type = signature[i];
         ValueType newType = this.rename(type);
         if (newType != null) {
            changed = true;
         }

         signature[i] = newType;
      }

      if (changed) {
         insn.setMethod(this.referenceCache.getCached(className, new MethodDescriptor(insn.getMethod().getName(), signature)));
      }
   }

   @Override
   public void visit(InvokeDynamicInstruction insn) {
      ValueType[] signature = insn.getMethod().getSignature();

      for (int i = 0; i < signature.length; i++) {
         signature[i] = this.rename(signature[i]);
      }

      insn.setMethod(new MethodDescriptor(insn.getMethod().getName(), signature));

      for (int i = 0; i < insn.getBootstrapArguments().size(); i++) {
         insn.getBootstrapArguments().set(i, this.rename(insn.getBootstrapArguments().get(i)));
      }
   }

   @Override
   public void visit(IsInstanceInstruction insn) {
      insn.setType(this.rename(insn.getType()));
   }

   @Override
   public void visit(InitClassInstruction insn) {
      insn.setClassName(this.classNameMapper.apply(insn.getClassName()));
   }
}
