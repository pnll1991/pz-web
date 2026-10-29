package org.teavm.backend.wasm.generate.classes;

public class WasmGCStandardClasses {
   private WasmGCClassInfoProvider classGenerator;
   private WasmGCClassInfo classClassInfo;
   private WasmGCClassInfo stringClassInfo;
   private WasmGCClassInfo objectClassInfo;

   public WasmGCStandardClasses(WasmGCClassInfoProvider classGenerator) {
      this.classGenerator = classGenerator;
   }

   public WasmGCClassInfo classClass() {
      if (this.classClassInfo == null) {
         this.classClassInfo = this.classGenerator.getClassInfo("java.lang.Class");
      }

      return this.classClassInfo;
   }

   public WasmGCClassInfo stringClass() {
      if (this.stringClassInfo == null) {
         this.stringClassInfo = this.classGenerator.getClassInfo("java.lang.String");
      }

      return this.stringClassInfo;
   }

   public WasmGCClassInfo objectClass() {
      if (this.objectClassInfo == null) {
         this.objectClassInfo = this.classGenerator.getClassInfo("java.lang.Object");
      }

      return this.objectClassInfo;
   }
}
