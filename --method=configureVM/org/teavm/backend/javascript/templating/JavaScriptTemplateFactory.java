package org.teavm.backend.javascript.templating;

import org.teavm.backend.javascript.ast.AstUtil;
import org.teavm.model.ClassReaderSource;

public class JavaScriptTemplateFactory {
   private ClassLoader classLoader;
   private ClassReaderSource classSource;

   public JavaScriptTemplateFactory(ClassLoader classLoader, ClassReaderSource classSource) {
      this.classLoader = classLoader;
      this.classSource = classSource;
   }

   public JavaScriptTemplate createFromResource(String path) {
      return new JavaScriptTemplate(AstUtil.parseFromResources(this.classLoader, path), this.classSource);
   }
}
