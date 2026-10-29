package org.teavm.backend.wasm.disasm;

public interface NameProvider {
   NameProvider EMPTY = new NameProvider() {
      @Override
      public String function(int index) {
         return null;
      }

      @Override
      public String local(int functionIndex, int index) {
         return null;
      }

      @Override
      public String type(int index) {
         return null;
      }

      @Override
      public String global(int index) {
         return null;
      }

      @Override
      public String field(int typeIndex, int index) {
         return null;
      }
   };

   String function(int var1);

   String local(int var1, int var2);

   String type(int var1);

   String global(int var1);

   String field(int var1, int var2);
}
