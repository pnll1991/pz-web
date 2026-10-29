package org.teavm.parsing.resource;

import java.io.IOException;
import java.io.InputStream;
import org.teavm.asm.ClassReader;
import org.teavm.asm.tree.ClassNode;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderSource;
import org.teavm.model.ReferenceCache;
import org.teavm.parsing.Parser;

public class ResourceBytecodeClassHolderSource implements ClassHolderSource {
   private Parser parser;
   private ResourceProvider resourceProvider;

   public ResourceBytecodeClassHolderSource(ResourceProvider resourceProvider, ReferenceCache referenceCache) {
      this.resourceProvider = resourceProvider;
      this.parser = new Parser(referenceCache);
   }

   @Override
   public ClassHolder get(String name) {
      ClassNode clsNode = new ClassNode();
      String resourceName = name.replace('.', '/') + ".class";
      Resource resource = this.resourceProvider.getResource(resourceName);
      if (resource == null) {
         return null;
      }

      try (InputStream input = resource.open()) {
         ClassReader reader = new ClassReader(input);
         reader.accept(clsNode, 0);
      } catch (IOException e) {
         throw new RuntimeException(e);
      }

      return this.parser.parseClass(clsNode);
   }
}
