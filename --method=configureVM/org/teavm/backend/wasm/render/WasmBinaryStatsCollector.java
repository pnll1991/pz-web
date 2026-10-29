package org.teavm.backend.wasm.render;

public interface WasmBinaryStatsCollector {
   WasmBinaryStatsCollector EMPTY = new WasmBinaryStatsCollector() {
      @Override
      public void addClassCodeSize(String className, int bytes) {
      }

      @Override
      public void addClassMetadataSize(String className, int bytes) {
      }

      @Override
      public void addStringsSize(int bytes) {
      }

      @Override
      public void addSectionSize(String name, int bytes) {
      }
   };

   void addClassCodeSize(String var1, int var2);

   void addClassMetadataSize(String var1, int var2);

   void addStringsSize(int var1);

   void addSectionSize(String var1, int var2);
}
