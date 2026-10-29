package org.teavm.backend.wasm.debug.parser;

import java.util.ArrayList;
import org.teavm.backend.wasm.debug.info.ClassInfo;
import org.teavm.backend.wasm.debug.info.PackageInfo;

public class DebugClassParser extends DebugSectionParser {
   private DebugStringParser strings;
   private DebugPackageParser packages;
   private DebugClassParser.ClassInfoImpl[] classes;

   public DebugClassParser(DebugStringParser strings, DebugPackageParser packages) {
      super("teavm_cls", strings, packages);
      this.strings = strings;
      this.packages = packages;
   }

   public ClassInfo getClass(int ptr) {
      return this.classes[ptr];
   }

   @Override
   protected void doParse() {
      ArrayList<DebugClassParser.ClassInfoImpl> classes = new ArrayList<>();

      while (this.ptr < this.data.length) {
         PackageInfo pkg = this.packages.getPackage(this.readLEB());
         String name = this.strings.getString(this.readLEB());
         classes.add(new DebugClassParser.ClassInfoImpl(pkg, name));
      }

      this.classes = classes.toArray(new DebugClassParser.ClassInfoImpl[0]);
   }

   private static class ClassInfoImpl extends ClassInfo {
      private PackageInfo pkg;
      private String name;

      ClassInfoImpl(PackageInfo pkg, String name) {
         this.pkg = pkg;
         this.name = name;
      }

      @Override
      public PackageInfo pkg() {
         return this.pkg;
      }

      @Override
      public String name() {
         return this.name;
      }
   }
}
