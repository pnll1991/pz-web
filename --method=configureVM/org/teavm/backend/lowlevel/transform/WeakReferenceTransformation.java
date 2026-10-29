package org.teavm.backend.lowlevel.transform;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import org.teavm.interop.Intrinsified;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldHolder;
import org.teavm.model.MethodHolder;

public class WeakReferenceTransformation implements ClassHolderTransformer {
   @Override
   public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
      if (cls.getName().equals(WeakReference.class.getName()) || cls.getName().equals(ReferenceQueue.class.getName())) {
         for (FieldHolder field : cls.getFields().toArray(new FieldHolder[0])) {
            cls.removeField(field);
         }

         for (MethodHolder method : cls.getMethods()) {
            method.setProgram(null);
            method.getModifiers().add(ElementModifier.NATIVE);
            method.getAnnotations().add(new AnnotationHolder(Intrinsified.class.getName()));
         }
      }
   }
}
