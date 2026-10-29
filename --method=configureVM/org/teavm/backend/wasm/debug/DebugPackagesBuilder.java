package org.teavm.backend.wasm.debug;

import java.util.Objects;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;

public class DebugPackagesBuilder extends DebugSectionBuilder implements DebugPackages {
   private DebugStrings strings;
   private ObjectIntMap<DebugPackagesBuilder.PackageInfo> packages = new ObjectIntHashMap();

   public DebugPackagesBuilder(DebugStrings strings) {
      super("teavm_pkg");
      this.strings = strings;
   }

   @Override
   public int packagePtr(int basePackage, String name) {
      DebugPackagesBuilder.PackageInfo key = new DebugPackagesBuilder.PackageInfo(basePackage, name);
      int result = this.packages.getOrDefault(key, -1);
      if (result < 0) {
         result = this.packages.size() + 1;
         this.packages.put(key, result);
         this.blob.writeLEB(basePackage);
         this.blob.writeLEB(this.strings.stringPtr(name));
      }

      return result;
   }

   private static class PackageInfo {
      private int parent;
      private String name;

      PackageInfo(int parent, String name) {
         this.parent = parent;
         this.name = name;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else if (o != null && this.getClass() == o.getClass()) {
            DebugPackagesBuilder.PackageInfo that = (DebugPackagesBuilder.PackageInfo)o;
            return this.parent == that.parent && this.name.equals(that.name);
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.parent, this.name);
      }
   }
}
