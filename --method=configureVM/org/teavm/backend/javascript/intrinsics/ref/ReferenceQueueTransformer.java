package org.teavm.backend.javascript.intrinsics.ref;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldHolder;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.ValueType;

public class ReferenceQueueTransformer implements ClassHolderTransformer {
   @Override
   public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
      if (cls.getName().equals(ReferenceQueue.class.getName())) {
         FieldHolder field = new FieldHolder("inner");
         field.setType(ValueType.parse(Object.class));
         cls.addField(field);
         field = new FieldHolder("registry");
         field.setType(ValueType.parse(Object.class));
         cls.addField(field);
         MethodHolder pollMethod = cls.getMethod(new MethodDescriptor("poll", Reference.class));
         pollMethod.setProgram(null);
         pollMethod.getModifiers().add(ElementModifier.NATIVE);
         MethodHolder constructor = cls.getMethod(new MethodDescriptor("<init>", void.class));
         constructor.setProgram(null);
         constructor.getModifiers().add(ElementModifier.NATIVE);
      }
   }
}
