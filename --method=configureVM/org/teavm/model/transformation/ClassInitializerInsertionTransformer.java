package org.teavm.model.transformation;

import org.teavm.model.BasicBlock;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.Program;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.model.instructions.InitClassInstruction;

public class ClassInitializerInsertionTransformer {
   private static final MethodDescriptor clinitDescriptor = new MethodDescriptor("<clinit>", void.class);
   private ClassReaderSource classes;
   private ClassInitializerInfo classInitializerInfo;

   public ClassInitializerInsertionTransformer(ClassReaderSource classes, ClassInitializerInfo classInitializerInfo) {
      this.classes = classes;
      this.classInitializerInfo = classInitializerInfo;
   }

   public void apply(MethodReader method, Program program) {
      if (program.basicBlockCount() != 0) {
         ClassReader cls = this.classes.get(method.getOwnerName());
         boolean hasClinit = cls.getMethod(clinitDescriptor) != null && this.classInitializerInfo.isDynamicInitializer(cls.getName());
         if (hasClinit && needsClinitCall(method)) {
            BasicBlock entryBlock = program.basicBlockAt(0);
            InitClassInstruction initInsn = new InitClassInstruction();
            initInsn.setClassName(method.getOwnerName());
            entryBlock.addFirst(initInsn);
         }
      }
   }

   private static boolean needsClinitCall(MethodReader method) {
      if (method.getName().equals("<clinit>")) {
         return false;
      } else {
         return method.getName().equals("<init>") ? true : method.hasModifier(ElementModifier.STATIC);
      }
   }
}
