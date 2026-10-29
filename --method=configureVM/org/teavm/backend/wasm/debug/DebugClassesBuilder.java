package org.teavm.backend.wasm.debug;

import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;

public class DebugClassesBuilder extends DebugSectionBuilder implements DebugClasses {
   private DebugPackages packages;
   private DebugStrings strings;
   private ObjectIntMap<String> classes = new ObjectIntHashMap();

   public DebugClassesBuilder(DebugPackages packages, DebugStrings strings) {
      super("teavm_cls");
      this.packages = packages;
      this.strings = strings;
   }

   @Override
   public int classPtr(String className) {
      int result = this.classes.getOrDefault(className, -1);
      if (result < 0) {
         result = this.classes.size();
         this.classes.put(className, result);
         int packagePtr = 0;
         int index = 0;

         while (true) {
            int next = className.indexOf(46, index);
            if (next < 0) {
               this.blob.writeLEB(packagePtr);
               this.blob.writeLEB(this.strings.stringPtr(className.substring(index)));
               break;
            }

            packagePtr = this.packages.packagePtr(packagePtr, className.substring(index, next));
            index = next + 1;
         }
      }

      return result;
   }
}
