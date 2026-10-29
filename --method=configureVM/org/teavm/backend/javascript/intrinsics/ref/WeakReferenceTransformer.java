package org.teavm.backend.javascript.intrinsics.ref;

import java.lang.ref.WeakReference;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodHolder;

public class WeakReferenceTransformer implements ClassHolderTransformer {
   @Override
   public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
      if (cls.getName().equals(WeakReference.class.getName())) {
         for (MethodHolder method : cls.getMethods()) {
            switch (method.getName()) {
               case "<init>":
                  if (method.parameterCount() == 2) {
                     method.setProgram(null);
                     method.getModifiers().add(ElementModifier.NATIVE);
                  }
                  break;
               case "get":
               case "clear":
                  method.setProgram(null);
                  method.getModifiers().add(ElementModifier.NATIVE);
            }
         }
      }
   }
}
