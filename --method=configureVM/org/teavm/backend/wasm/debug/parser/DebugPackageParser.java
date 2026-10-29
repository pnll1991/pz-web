package org.teavm.backend.wasm.debug.parser;

import java.util.ArrayList;
import org.teavm.backend.wasm.debug.info.PackageInfo;

public class DebugPackageParser extends DebugSectionParser {
   private DebugStringParser strings;
   private DebugPackageParser.PackageInfoImpl[] packages;

   public DebugPackageParser(DebugStringParser strings) {
      super("teavm_pkg", strings);
      this.strings = strings;
   }

   public PackageInfo getPackage(int index) {
      return index == 0 ? null : this.packages[index - 1];
   }

   @Override
   protected void doParse() {
      ArrayList<DebugPackageParser.PackageBuilder> packageBuilders = new ArrayList<>();

      while (this.ptr < this.data.length) {
         packageBuilders.add(new DebugPackageParser.PackageBuilder(this.readLEB(), this.strings.getString(this.readLEB())));
      }

      this.packages = new DebugPackageParser.PackageInfoImpl[packageBuilders.size()];

      for (int i = 0; i < this.packages.length; i++) {
         this.packages[i] = new DebugPackageParser.PackageInfoImpl(packageBuilders.get(i).name);
      }

      for (int i = 0; i < this.packages.length; i++) {
         this.packages[i].parent = this.getPackage(packageBuilders.get(i).parent);
      }
   }

   private static class PackageBuilder {
      int parent;
      String name;

      PackageBuilder(int parent, String name) {
         this.parent = parent;
         this.name = name;
      }
   }

   private static class PackageInfoImpl extends PackageInfo {
      private PackageInfo parent;
      private String name;

      PackageInfoImpl(String name) {
         this.name = name;
      }

      @Override
      public PackageInfo parent() {
         return this.parent;
      }

      @Override
      public String name() {
         return this.name;
      }
   }
}
