package org.teavm.backend.javascript.ast;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.teavm.backend.javascript.rendering.JSParser;
import org.teavm.rhino.javascript.CompilerEnvirons;
import org.teavm.rhino.javascript.ast.AstNode;

public final class AstUtil {
   private AstUtil() {
   }

   public static AstNode parse(String string) {
      CompilerEnvirons env = new CompilerEnvirons();
      env.setRecoverFromErrors(true);
      env.setLanguageVersion(180);
      env.setIdeMode(true);
      JSParser factory = new JSParser(env);
      return factory.parse(string, null, 0);
   }

   public static AstNode parseFromResources(ClassLoader classLoader, String path) {
      try (InputStream input = classLoader.getResourceAsStream(path)) {
         return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }
}
