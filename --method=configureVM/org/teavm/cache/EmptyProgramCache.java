package org.teavm.cache;

import java.util.function.Supplier;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ProgramCache;

public class EmptyProgramCache implements ProgramCache {
   public static final EmptyProgramCache INSTANCE = new EmptyProgramCache();

   private EmptyProgramCache() {
   }

   @Override
   public Program get(MethodReference method, CacheStatus status) {
      return null;
   }

   @Override
   public void store(MethodReference method, Program program, Supplier<String[]> dependencies) {
   }
}
