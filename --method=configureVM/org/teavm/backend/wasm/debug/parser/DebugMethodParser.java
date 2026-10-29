package org.teavm.backend.wasm.debug.parser;

import java.util.ArrayList;
import org.teavm.backend.wasm.debug.info.ClassInfo;
import org.teavm.backend.wasm.debug.info.MethodInfo;

public class DebugMethodParser extends DebugSectionParser {
   private DebugStringParser strings;
   private DebugClassParser classes;
   private DebugMethodParser.MethodInfoImpl[] methods;

   public DebugMethodParser(DebugStringParser strings, DebugClassParser classes) {
      super("teavm_mtd");
      this.strings = strings;
      this.classes = classes;
   }

   public MethodInfo getMethod(int ptr) {
      return this.methods[ptr];
   }

   @Override
   protected void doParse() {
      ArrayList<DebugMethodParser.MethodInfoImpl> methods = new ArrayList<>();

      while (this.ptr < this.data.length) {
         ClassInfo cls = this.classes.getClass(this.readLEB());
         String name = this.strings.getString(this.readLEB());
         methods.add(new DebugMethodParser.MethodInfoImpl(cls, name));
      }

      this.methods = methods.toArray(new DebugMethodParser.MethodInfoImpl[0]);
   }

   private static class MethodInfoImpl extends MethodInfo {
      private ClassInfo cls;
      private String name;

      MethodInfoImpl(ClassInfo cls, String name) {
         this.cls = cls;
         this.name = name;
      }

      @Override
      public ClassInfo cls() {
         return this.cls;
      }

      @Override
      public String name() {
         return this.name;
      }
   }
}
