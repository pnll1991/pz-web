package org.teavm.backend.wasm.debug.info;

public class Location {
   private FileInfo file;
   private int line;
   private InliningLocation inlining;

   public Location(FileInfo file, int line, InliningLocation inlining) {
      this.file = file;
      this.line = line;
      this.inlining = inlining;
   }

   public FileInfo file() {
      return this.file;
   }

   public int line() {
      return this.line;
   }

   public InliningLocation inlining() {
      return this.inlining;
   }

   public int depth() {
      int result = 0;

      for (Location loc = this; loc != null; loc = loc.inlining != null ? loc.inlining.location() : null) {
         result++;
      }

      return result;
   }
}
