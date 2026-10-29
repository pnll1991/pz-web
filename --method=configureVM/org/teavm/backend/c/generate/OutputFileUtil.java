package org.teavm.backend.c.generate;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.teavm.vm.BuildTarget;

public final class OutputFileUtil {
   private OutputFileUtil() {
   }

   public static void write(BufferedCodeWriter code, String name, BuildTarget buildTarget) throws IOException {
      try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(buildTarget.createResource(name), StandardCharsets.UTF_8))) {
         code.writeTo(writer, name);
      }
   }
}
