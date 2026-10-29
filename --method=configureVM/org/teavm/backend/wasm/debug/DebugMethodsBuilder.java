package org.teavm.backend.wasm.debug;

import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.model.MethodReference;

public class DebugMethodsBuilder extends DebugSectionBuilder implements DebugMethods {
   private DebugClasses classes;
   private DebugStrings strings;
   private ObjectIntMap<MethodReference> methods = new ObjectIntHashMap();

   public DebugMethodsBuilder(DebugClasses classes, DebugStrings strings) {
      super("teavm_mtd");
      this.classes = classes;
      this.strings = strings;
   }

   @Override
   public int methodPtr(MethodReference method) {
      int result = this.methods.getOrDefault(method, -1);
      if (result < 0) {
         result = this.methods.size();
         this.methods.put(method, result);
         this.blob.writeLEB(this.classes.classPtr(method.getClassName()));
         this.blob.writeLEB(this.strings.stringPtr(method.getName()));
      }

      return result;
   }
}
