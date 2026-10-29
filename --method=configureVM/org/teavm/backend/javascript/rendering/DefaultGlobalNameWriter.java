package org.teavm.backend.javascript.rendering;

import java.util.function.Function;

public class DefaultGlobalNameWriter implements Function<String, NameEmitter> {
   public NameEmitter apply(String s) {
      return !s.startsWith("$rt_") && !s.startsWith("Long_") && !s.equals("Long") ? (w, prec) -> w.appendGlobal(s) : (w, intprec) -> w.appendFunction(s);
   }
}
