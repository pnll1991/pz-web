package org.teavm.backend.javascript.rendering;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.codegen.SourceWriterSink;
import org.teavm.backend.javascript.templating.AstRemoval;
import org.teavm.backend.javascript.templating.RemovablePartsFinder;
import org.teavm.backend.javascript.templating.TemplatingAstTransformer;
import org.teavm.backend.javascript.templating.TemplatingAstWriter;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.rhino.javascript.CompilerEnvirons;
import org.teavm.rhino.javascript.ast.AstRoot;
import org.teavm.vm.RenderingException;

public class RuntimeRenderer {
   private final List<AstRoot> runtimeAstParts = new ArrayList<>();
   private final List<AstRoot> epilogueAstParts = new ArrayList<>();
   private final RemovablePartsFinder removablePartsFinder = new RemovablePartsFinder();
   private final ClassReaderSource classSource;
   private final SourceWriter writer;
   private final ClassInitializerInfo classInitializerInfo;
   private final Set<String> topLevelNames = new HashSet<>();
   public final SourceWriterSink sink = new SourceWriterSink() {
      @Override
      public SourceWriterSink appendFunction(String name) {
         RuntimeRenderer.this.removablePartsFinder.markUsedDeclaration(name);
         return this;
      }
   };

   public RuntimeRenderer(ClassReaderSource classSource, SourceWriter writer, ClassInitializerInfo classInitializerInfo) {
      this.classSource = classSource;
      this.writer = writer;
      this.classInitializerInfo = classInitializerInfo;
   }

   public void prepareAstParts(boolean threadLibraryUsed) {
      this.runtimeAstParts.add(this.prepareAstPart("runtime.js"));
      this.runtimeAstParts.add(this.prepareAstPart("primitive.js"));
      this.runtimeAstParts.add(this.prepareAstPart("numeric.js"));
      this.runtimeAstParts.add(this.prepareAstPart("long.js"));
      this.runtimeAstParts.add(this.prepareAstPart("array.js"));
      this.runtimeAstParts.add(this.prepareAstPart("string.js"));
      this.runtimeAstParts.add(this.prepareAstPart("reflection.js"));
      this.runtimeAstParts.add(this.prepareAstPart("exception.js"));
      this.runtimeAstParts.add(this.prepareAstPart("check.js"));
      this.runtimeAstParts.add(this.prepareAstPart("console.js"));
      this.runtimeAstParts.add(this.prepareAstPart("metadata.js"));
      this.runtimeAstParts.add(this.prepareAstPart(threadLibraryUsed ? "thread.js" : "simpleThread.js"));
      this.epilogueAstParts.add(this.prepareAstPart("types.js"));
   }

   public void renderRuntime() {
      for (AstRoot ast : this.runtimeAstParts) {
         this.renderRuntimePart(ast);
      }
   }

   public void renderEpilogue() {
      for (AstRoot ast : this.epilogueAstParts) {
         this.renderRuntimePart(ast);
      }
   }

   private AstRoot prepareAstPart(String name) {
      AstRoot ast = this.parseRuntime(name);
      ast.visit(new StringConstantElimination());
      new TemplatingAstTransformer(this.classSource).visit(ast);
      this.removablePartsFinder.visit(ast);
      this.topLevelNames.addAll(ast.getSymbolTable().keySet());
      return ast;
   }

   private void renderRuntimePart(AstRoot ast) {
      TemplatingAstWriter astWriter = new TemplatingAstWriter(this.writer, this.classInitializerInfo, true);

      for (String name : this.topLevelNames) {
         astWriter.declareNameEmitter(name, (w, prec) -> w.appendFunction(name));
      }

      astWriter.print(ast);
   }

   private AstRoot parseRuntime(String name) {
      CompilerEnvirons env = new CompilerEnvirons();
      env.setRecoverFromErrors(true);
      env.setLanguageVersion(180);
      JSParser factory = new JSParser(env);
      ClassLoader loader = RuntimeRenderer.class.getClassLoader();

      try (
         InputStream input = loader.getResourceAsStream("org/teavm/backend/javascript/" + name);
         Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
      ) {
         return factory.parse(reader, null, 0);
      } catch (IOException e) {
         throw new RenderingException(e);
      }
   }

   public void removeUnusedParts() {
      AstRemoval removal = new AstRemoval(this.removablePartsFinder.getAllRemovableParts());

      for (AstRoot part : this.runtimeAstParts) {
         removal.visit(part);
      }

      for (AstRoot part : this.epilogueAstParts) {
         removal.visit(part);
      }
   }
}
