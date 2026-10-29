package org.teavm.backend.javascript.templating;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.IntFunction;
import org.teavm.backend.javascript.rendering.NameEmitter;
import org.teavm.backend.javascript.spi.GeneratorContext;
import org.teavm.model.ClassReaderSource;
import org.teavm.rhino.javascript.Node;
import org.teavm.rhino.javascript.ast.AstNode;
import org.teavm.rhino.javascript.ast.FunctionNode;
import org.teavm.rhino.javascript.ast.Name;

public class JavaScriptTemplate {
   private TemplatingFunctionIndex functionIndex = new TemplatingFunctionIndex();

   public JavaScriptTemplate(AstNode node, ClassReaderSource classSource) {
      new TemplatingAstTransformer(classSource).visit(node);
      this.functionIndex.visit(node);
   }

   public JavaScriptTemplate.FragmentBuilder builder(String functionName) {
      FunctionNode function = this.functionIndex.getFunction(functionName);
      if (function == null) {
         throw new IllegalArgumentException("Function " + functionName + " was not found in JS template");
      } else {
         return new JavaScriptTemplate.FragmentBuilder(function);
      }
   }

   public static class FragmentBuilder {
      private FunctionNode node;
      private IntFunction<SourceFragment> parameters;
      private Map<String, SourceFragment> fragments = new HashMap<>();

      private FragmentBuilder(FunctionNode node) {
         this.node = node;
      }

      public JavaScriptTemplate.FragmentBuilder withParameters(IntFunction<SourceFragment> parameters) {
         this.parameters = parameters;
         return this;
      }

      public JavaScriptTemplate.FragmentBuilder withContext(GeneratorContext context) {
         return this.withParameters(param -> (writer, precedence) -> writer.append(context.getParameterName(param)));
      }

      public JavaScriptTemplate.FragmentBuilder withFragment(String name, SourceFragment fragment) {
         this.fragments.put(name, fragment);
         return this;
      }

      public SourceFragment build() {
         IntFunction<SourceFragment> intParameters = this.parameters;
         HashMap<String, NameEmitter> nameParameters = new HashMap<>();

         for (int i = 0; i < this.node.getParams().size(); i++) {
            AstNode param = (AstNode)this.node.getParams().get(i);
            if (param instanceof Name) {
               SourceFragment sourceFragment = intParameters.apply(i + 1);
               nameParameters.put(((Name)param).getIdentifier(), sourceFragment::write);
            }
         }

         SourceFragment thisFragment = this.parameters.apply(0);
         AstNode body = this.node.getBody();
         return (writer, precedence) -> {
            TemplatingAstWriter astWriter = new TemplatingAstWriter(writer, null, false);

            for (Entry<String, NameEmitter> entry : nameParameters.entrySet()) {
               astWriter.declareNameEmitter(entry.getKey(), entry.getValue());
            }

            for (Entry<String, SourceFragment> entry : this.fragments.entrySet()) {
               astWriter.setFragment(entry.getKey(), entry.getValue());
            }

            if (thisFragment != null) {
               astWriter.declareNameEmitter("this", thisFragment::write);
            }

            for (Node child = body.getFirstChild(); child != null; child = child.getNext()) {
               astWriter.print((AstNode)child);
               writer.softNewLine();
            }
         };
      }
   }
}
