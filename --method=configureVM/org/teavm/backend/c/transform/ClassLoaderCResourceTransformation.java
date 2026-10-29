package org.teavm.backend.c.transform;

import java.io.InputStream;
import org.teavm.backend.c.runtime.CResources;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;

public class ClassLoaderCResourceTransformation implements ClassHolderTransformer {
   @Override
   public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
      if (cls.getName().equals(ClassLoader.class.getName())) {
         MethodHolder method = cls.getMethod(new MethodDescriptor("getResourceAsStream", String.class, InputStream.class));
         this.transformGetResourceAsStream(method);
      }
   }

   private void transformGetResourceAsStream(MethodHolder method) {
      Program program = new Program();
      program.createVariable();
      Variable nameVar = program.createVariable();
      BasicBlock block = program.createBasicBlock();
      InvokeInstruction invoke = new InvokeInstruction();
      invoke.setType(InvocationType.SPECIAL);
      invoke.setMethod(new MethodReference(CResources.class, "getResource", String.class, InputStream.class));
      invoke.setArguments(nameVar);
      invoke.setReceiver(program.createVariable());
      block.add(invoke);
      ExitInstruction exit = new ExitInstruction();
      exit.setValueToReturn(invoke.getReceiver());
      block.add(exit);
      method.setProgram(program);
   }
}
