package org.teavm.model.transformation;

import java.util.HashSet;
import java.util.Set;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.Program;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.util.DominatorWalker;
import org.teavm.model.util.DominatorWalkerCallback;
import org.teavm.model.util.DominatorWalkerContext;

public class ClassInitInsertion {
   private static final MethodDescriptor CLINIT = new MethodDescriptor("<clinit>", void.class);
   private ClassReaderSource classSource;

   public ClassInitInsertion(ClassReaderSource classSource) {
      this.classSource = classSource;
   }

   public void apply(MethodReader method, Program program) {
      if (program.basicBlockCount() != 0) {
         String currentClass = null;
         if (method.hasModifier(ElementModifier.STATIC)) {
            currentClass = method.getOwnerName();
         }

         ClassInitInsertion.Visitor visitor = new ClassInitInsertion.Visitor(currentClass);
         new DominatorWalker(program).walk(visitor);
      }
   }

   static class State {
      Set<String> newInitializedClasses = new HashSet<>();
      Set<String> oldInitializedClasses;
   }

   class Visitor extends AbstractInstructionVisitor implements DominatorWalkerCallback<ClassInitInsertion.State> {
      private String currentClass;
      private DominatorWalkerContext context;
      Set<String> initializedClasses = new HashSet<>();
      private ClassInitInsertion.State state;

      Visitor(String currentClass) {
         this.currentClass = currentClass;
         if (currentClass != null) {
            this.initializedClasses.add(currentClass);
         }
      }

      @Override
      public void setContext(DominatorWalkerContext context) {
         this.context = context;
      }

      public ClassInitInsertion.State visit(BasicBlock block) {
         this.state = new ClassInitInsertion.State();
         if (this.context.isExceptionHandler(block.getIndex())) {
            this.markAllClassesAsNotInitialized();
            if (this.currentClass != null) {
               this.markClassAsInitialized(this.currentClass);
            }
         }

         for (Instruction instruction : block) {
            instruction.acceptVisitor(this);
         }

         return this.state;
      }

      public void endVisit(BasicBlock block, ClassInitInsertion.State state) {
         if (state.oldInitializedClasses != null) {
            this.initializedClasses.clear();
            this.initializedClasses.addAll(state.oldInitializedClasses);
         } else {
            this.initializedClasses.removeAll(state.newInitializedClasses);
         }
      }

      @Override
      public void visit(GetFieldInstruction insn) {
         if (insn.getInstance() == null) {
            this.initializeClass(insn.getField().getClassName(), insn);
         }
      }

      @Override
      public void visit(PutFieldInstruction insn) {
         if (insn.getInstance() == null) {
            this.initializeClass(insn.getField().getClassName(), insn);
         }
      }

      @Override
      public void visit(InvokeInstruction insn) {
         if (insn.getInstance() == null) {
            this.markClassAsInitialized(insn.getMethod().getClassName());
         }
      }

      @Override
      public void visit(InitClassInstruction insn) {
         this.markClassAsInitialized(insn.getClassName());
      }

      private void initializeClass(String className, Instruction instruction) {
         if (this.markClassAsInitialized(className)) {
            ClassReader cls = ClassInitInsertion.this.classSource.get(className);
            if (cls == null || cls.getMethod(ClassInitInsertion.CLINIT) != null) {
               InitClassInstruction initInsn = new InitClassInstruction();
               initInsn.setClassName(className);
               initInsn.setLocation(instruction.getLocation());
               instruction.insertPrevious(initInsn);
            }
         }
      }

      boolean markClassAsInitialized(String className) {
         if (this.initializedClasses.add(className)) {
            if (this.state.newInitializedClasses != null) {
               this.state.newInitializedClasses.add(className);
            }

            return true;
         } else {
            return false;
         }
      }

      private void markAllClassesAsNotInitialized() {
         if (this.state.newInitializedClasses != null) {
            this.state.oldInitializedClasses = new HashSet<>();

            for (String className : this.initializedClasses) {
               if (!this.state.newInitializedClasses.contains(className)) {
                  this.state.oldInitializedClasses.add(className);
               }
            }

            this.state.newInitializedClasses = null;
         }

         this.initializedClasses.clear();
      }
   }
}
