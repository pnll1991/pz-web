package org.teavm.dependency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.interop.SupportedOn;
import org.teavm.interop.UnsupportedOn;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.AnnotationReader;
import org.teavm.model.AnnotationValue;
import org.teavm.model.BasicBlock;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InstructionVisitor;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.optimization.UnreachableBasicBlockEliminator;
import org.teavm.model.util.ProgramUtils;

public class ReferenceResolver {
   private ClassReaderSource classSource;
   private MethodReference currentMethod;
   private Diagnostics diagnostics;
   private Program program;
   private boolean modified;
   private List<Instruction> instructionsToAdd = new ArrayList<>();
   private Map<FieldReference, ReferenceResolver.FieldWrapper> fieldCache = new HashMap<>();
   private Map<String, Map<MethodDescriptor, Optional<MethodReader>>> methodCache = new HashMap<>(1000, 0.5F);
   private Set<String> platformTags = new HashSet<>();
   private UnreachableBasicBlockEliminator unreachableBlockEliminator;
   private Map<MethodReference, List<Runnable>> pendingErrors = new HashMap<>();
   private Set<MethodReference> usedMethods = new HashSet<>();
   private boolean shouldStop;
   private InstructionVisitor visitor = new AbstractInstructionVisitor() {
      @Override
      public void visit(InvokeInstruction insn) {
         if (!ReferenceResolver.this.resolve(insn)) {
            ReferenceResolver.this.shouldStop = true;
         }
      }

      @Override
      public void visit(GetFieldInstruction insn) {
         if (!ReferenceResolver.this.resolve(insn)) {
            ReferenceResolver.this.shouldStop = true;
         }
      }

      @Override
      public void visit(PutFieldInstruction insn) {
         if (!ReferenceResolver.this.resolve(insn)) {
            ReferenceResolver.this.shouldStop = true;
         }
      }

      @Override
      public void visit(CastInstruction insn) {
         if (!ReferenceResolver.this.typeAvailable(insn.getTargetType())) {
            ReferenceResolver.this.emitExceptionThrow(insn.getLocation(), ClassCastException.class.getName(), "Can't cast");
            ReferenceResolver.this.truncateBlock(insn);
            ReferenceResolver.this.shouldStop = true;
         }
      }

      @Override
      public void visit(IsInstanceInstruction insn) {
         if (!ReferenceResolver.this.typeAvailable(insn.getType())) {
            IntegerConstantInstruction falseCst = new IntegerConstantInstruction();
            falseCst.setLocation(insn.getLocation());
            falseCst.setReceiver(insn.getReceiver());
            insn.replace(falseCst);
         }
      }

      @Override
      public void visit(InitClassInstruction insn) {
         if (!ReferenceResolver.this.checkClass(insn, insn.getClassName())) {
            ReferenceResolver.this.shouldStop = true;
         }
      }

      @Override
      public void visit(ConstructInstruction insn) {
         if (!ReferenceResolver.this.checkClass(insn, insn.getType())) {
            ReferenceResolver.this.shouldStop = true;
         }
      }

      @Override
      public void visit(ConstructArrayInstruction insn) {
         if (!ReferenceResolver.this.checkType(insn, insn.getItemType())) {
            ReferenceResolver.this.shouldStop = true;
         }
      }

      @Override
      public void visit(ConstructMultiArrayInstruction insn) {
         if (!ReferenceResolver.this.checkType(insn, insn.getItemType())) {
            ReferenceResolver.this.shouldStop = true;
         }
      }

      @Override
      public void visit(ClassConstantInstruction insn) {
         if (!ReferenceResolver.this.checkType(insn, insn.getConstant())) {
            ReferenceResolver.this.shouldStop = true;
         }
      }
   };

   public ReferenceResolver(ClassReaderSource classSource, String[] platformTags, Diagnostics diagnostics) {
      this.classSource = classSource;
      this.platformTags.addAll(List.of(platformTags));
      this.unreachableBlockEliminator = new UnreachableBasicBlockEliminator();
      this.diagnostics = diagnostics;
   }

   public void use(MethodReference method) {
      this.usedMethods.add(method);
      List<Runnable> errors = this.pendingErrors.remove(method);
      if (errors != null) {
         for (Runnable error : errors) {
            error.run();
         }
      }
   }

   public Program resolve(MethodHolder method, Program program) {
      this.currentMethod = method.getReference();
      this.program = program;

      for (BasicBlock block : program.getBasicBlocks()) {
         this.shouldStop = false;

         for (Instruction insn : block) {
            insn.acceptVisitor(this.visitor);
            if (this.shouldStop) {
               break;
            }
         }
      }

      if (this.modified) {
         this.unreachableBlockEliminator.optimize(program);
         this.modified = false;
      }

      this.program = null;
      this.currentMethod = null;
      return program;
   }

   private boolean resolve(InvokeInstruction instruction) {
      MethodReference calledRef = instruction.getMethod();
      if (!this.checkClass(instruction, calledRef.getClassName())) {
         return false;
      } else {
         return instruction.getType() == InvocationType.SPECIAL ? this.resolveSpecial(instruction, calledRef) : this.resolveVirtual(instruction, calledRef);
      }
   }

   private boolean resolve(GetFieldInstruction instruction) {
      FieldReader resolvedField = this.resolve(instruction.getField());
      if (resolvedField == null) {
         this.reportError(instruction.getLocation(), "Field {{f0}} was not found", instruction.getField());
         this.emitExceptionThrow(instruction.getLocation(), NoSuchFieldError.class.getName(), "Field not found: " + instruction.getField());
         this.truncateBlock(instruction);
         return false;
      } else {
         instruction.setField(resolvedField.getReference());
         return true;
      }
   }

   private boolean resolve(PutFieldInstruction instruction) {
      FieldReader resolvedField = this.resolve(instruction.getField());
      if (resolvedField == null) {
         this.reportError(instruction.getLocation(), "Field {{f0}} was not found", instruction.getField());
         this.emitExceptionThrow(instruction.getLocation(), NoSuchFieldError.class.getName(), "Field not found: " + instruction.getField());
         this.truncateBlock(instruction);
         return false;
      } else {
         instruction.setField(resolvedField.getReference());
         return true;
      }
   }

   private boolean resolveSpecial(InvokeInstruction instruction, MethodReference calledRef) {
      MethodReader resolvedMethod = this.resolve(calledRef);
      if (resolvedMethod == null) {
         this.reportError(instruction.getLocation(), "Method {{m0}} was not found", calledRef);
         this.emitExceptionThrow(instruction.getLocation(), NoSuchMethodError.class.getName(), "Method not found: " + instruction.getMethod());
         this.truncateBlock(instruction);
         return false;
      }

      if (!this.checkMethod(instruction, resolvedMethod.getReference())) {
         return false;
      }

      instruction.setMethod(resolvedMethod.getReference());
      return true;
   }

   private boolean resolveVirtual(InvokeInstruction instruction, MethodReference calledRef) {
      MethodReader resolvedMethod = this.resolve(calledRef);
      if (resolvedMethod == null) {
         this.reportError(instruction.getLocation(), "Method {{m0}} was not found", calledRef);
         this.emitExceptionThrow(instruction.getLocation(), NoSuchMethodError.class.getName(), "Method not found: " + instruction.getMethod());
         this.truncateBlock(instruction);
         return false;
      }

      if (!this.checkMethod(instruction, resolvedMethod.getReference())) {
         return false;
      }

      boolean isFinal = false;
      if (!resolvedMethod.hasModifier(ElementModifier.FINAL) && resolvedMethod.getLevel() != AccessLevel.PRIVATE) {
         ClassReader cls = this.classSource.get(resolvedMethod.getOwnerName());
         if (cls.hasModifier(ElementModifier.FINAL)) {
            isFinal = true;
         }
      } else {
         isFinal = true;
      }

      if (isFinal) {
         instruction.setType(InvocationType.SPECIAL);
         instruction.setMethod(resolvedMethod.getReference());
      }

      return true;
   }

   public FieldReader resolve(FieldReference field) {
      return this.fieldCache.computeIfAbsent(field, f -> {
         FieldReader result = this.classSource.resolve(f);
         return new ReferenceResolver.FieldWrapper(result);
      }).value;
   }

   private boolean typeAvailable(ValueType type) {
      while (type instanceof ValueType.Array) {
         type = ((ValueType.Array)type).getItemType();
      }

      return type instanceof ValueType.Object ? this.classAvailable(((ValueType.Object)type).getClassName()) : true;
   }

   private boolean classAvailable(String className) {
      ClassReader cls = this.classSource.get(className);
      return cls == null ? false : this.checkPlatformSupported(cls.getAnnotations());
   }

   private boolean checkType(Instruction instruction, ValueType type) {
      while (type instanceof ValueType.Array) {
         type = ((ValueType.Array)type).getItemType();
      }

      return type instanceof ValueType.Object ? this.checkClass(instruction, ((ValueType.Object)type).getClassName()) : true;
   }

   private boolean checkClass(Instruction instruction, String className) {
      ClassReader cls = this.classSource.get(className);
      if (cls == null) {
         this.reportError(instruction.getLocation(), "Class {{c0}} was not found", className);
         this.emitExceptionThrow(instruction.getLocation(), NoClassDefFoundError.class.getName(), "Class not found: " + className);
         this.truncateBlock(instruction);
         return false;
      } else if (!this.checkPlatformSupported(cls.getAnnotations())) {
         this.reportError(instruction.getLocation(), "Class {{c0}} is not supported on current target", className);
         this.emitExceptionThrow(instruction.getLocation(), NoClassDefFoundError.class.getName(), "Class not found: " + className);
         this.truncateBlock(instruction);
         return false;
      } else {
         return true;
      }
   }

   private boolean checkMethod(Instruction instruction, MethodReference methodRef) {
      ClassReader cls = this.classSource.get(methodRef.getClassName());
      if (cls != null) {
         MethodReader methodReader = cls.getMethod(methodRef.getDescriptor());
         if (methodReader != null && !this.checkPlatformSupported(methodReader.getAnnotations())) {
            this.reportError(instruction.getLocation(), "Method {{m0}} is not supported on current target", methodRef);
            this.emitExceptionThrow(instruction.getLocation(), NoSuchMethodError.class.getName(), "Method not found: " + methodRef);
            this.truncateBlock(instruction);
            return false;
         }
      }

      return true;
   }

   private boolean checkPlatformSupported(AnnotationContainerReader annotations) {
      AnnotationReader supportedAnnot = annotations.get(SupportedOn.class.getName());
      AnnotationReader unsupportedAnnot = annotations.get(UnsupportedOn.class.getName());
      if (supportedAnnot != null) {
         for (AnnotationValue value : supportedAnnot.getValue("value").getList()) {
            if (this.platformTags.contains(value.getString())) {
               return true;
            }
         }

         return false;
      } else if (unsupportedAnnot != null) {
         for (AnnotationValue value : unsupportedAnnot.getValue("value").getList()) {
            if (this.platformTags.contains(value.getString())) {
               return false;
            }
         }

         return true;
      } else {
         return true;
      }
   }

   public MethodReader resolve(MethodReference method) {
      return this.methodCache.computeIfAbsent(method.getClassName(), k -> new HashMap<>(100, 0.5F)).computeIfAbsent(method.getDescriptor(), k -> {
         MethodReader methodReader = this.classSource.resolve(method);
         return Optional.ofNullable(methodReader);
      }).orElse(null);
   }

   private void truncateBlock(Instruction instruction) {
      this.modified = true;
      ProgramUtils.truncateBlock(instruction);
      instruction.insertNextAll(this.instructionsToAdd);
      this.instructionsToAdd.clear();
      instruction.delete();
   }

   private void emitExceptionThrow(TextLocation location, String exceptionName, String text) {
      Variable exceptionVar = this.program.createVariable();
      ConstructInstruction newExceptionInsn = new ConstructInstruction();
      newExceptionInsn.setType(exceptionName);
      newExceptionInsn.setReceiver(exceptionVar);
      newExceptionInsn.setLocation(location);
      this.instructionsToAdd.add(newExceptionInsn);
      Variable constVar = this.program.createVariable();
      StringConstantInstruction constInsn = new StringConstantInstruction();
      constInsn.setConstant(text);
      constInsn.setReceiver(constVar);
      constInsn.setLocation(location);
      this.instructionsToAdd.add(constInsn);
      InvokeInstruction initExceptionInsn = new InvokeInstruction();
      initExceptionInsn.setInstance(exceptionVar);
      initExceptionInsn.setMethod(new MethodReference(exceptionName, "<init>", ValueType.object("java.lang.String"), ValueType.VOID));
      initExceptionInsn.setType(InvocationType.SPECIAL);
      initExceptionInsn.setArguments(constVar);
      initExceptionInsn.setLocation(location);
      this.instructionsToAdd.add(initExceptionInsn);
      RaiseInstruction raiseInsn = new RaiseInstruction();
      raiseInsn.setException(exceptionVar);
      raiseInsn.setLocation(location);
      this.instructionsToAdd.add(raiseInsn);
   }

   private void reportError(TextLocation location, String message, Object param) {
      MethodReference method = this.currentMethod;
      if (this.usedMethods.contains(method)) {
         this.diagnostics.error(new CallLocation(method, location), message, param);
      } else {
         this.pendingErrors
            .computeIfAbsent(method, k -> new ArrayList<>())
            .add(() -> this.diagnostics.error(new CallLocation(method, location), message, param));
      }
   }

   private static class FieldWrapper {
      final FieldReader value;

      FieldWrapper(FieldReader value) {
         this.value = value;
      }
   }
}
