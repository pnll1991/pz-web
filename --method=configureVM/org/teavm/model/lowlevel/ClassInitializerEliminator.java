package org.teavm.model.lowlevel;

import java.util.HashMap;
import java.util.Map;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Structure;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.Program;
import org.teavm.model.instructions.InitClassInstruction;

public class ClassInitializerEliminator {
   private ClassReaderSource unprocessedClassSource;
   private Map<String, Boolean> cache = new HashMap<>();

   public ClassInitializerEliminator(ClassReaderSource unprocessedClassSource) {
      this.unprocessedClassSource = unprocessedClassSource;
   }

   public void apply(Program program) {
      for (int i = 0; i < program.basicBlockCount(); i++) {
         for (Instruction insn : program.basicBlockAt(i)) {
            if (insn instanceof InitClassInstruction && !this.filter(((InitClassInstruction)insn).getClassName())) {
               insn.delete();
            }
         }
      }
   }

   private boolean filter(String className) {
      return this.cache.computeIfAbsent(className, key -> this.clinitNeeded(key) && !this.isStaticInit(key) && !this.isStructure(key));
   }

   private boolean clinitNeeded(String className) {
      ClassReader cls = this.unprocessedClassSource.get(className);
      return cls == null ? true : cls.getMethod(new MethodDescriptor("<clinit>", void.class)) != null;
   }

   private boolean isStaticInit(String className) {
      ClassReader cls = this.unprocessedClassSource.get(className);
      return cls == null ? false : cls.getAnnotations().get(StaticInit.class.getName()) != null;
   }

   private boolean isStructure(String className) {
      while (!className.equals(Structure.class.getName())) {
         ClassReader cls = this.unprocessedClassSource.get(className);
         if (cls == null) {
            return false;
         }

         if (cls.getParent() == null) {
            return false;
         }

         className = cls.getParent();
      }

      return true;
   }
}
