package org.teavm.backend.javascript.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.teavm.ast.AsyncMethodNode;
import org.teavm.ast.AsyncMethodPart;
import org.teavm.ast.MethodNode;
import org.teavm.ast.MethodNodeVisitor;
import org.teavm.ast.RegularMethodNode;
import org.teavm.ast.VariableNode;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.spi.Generator;
import org.teavm.backend.javascript.spi.GeneratorContext;
import org.teavm.dependency.DependencyInfo;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.parsing.resource.ResourceProvider;

public class MethodBodyRenderer implements MethodNodeVisitor, GeneratorContext {
   private RenderingContext context;
   private Diagnostics diagnostics;
   private boolean minifying;
   private boolean async;
   private Set<MethodReference> asyncMethods;
   private SourceWriter writer;
   private StatementRenderer statementRenderer;
   private boolean threadLibraryUsed;

   public MethodBodyRenderer(
      RenderingContext context,
      Diagnostics diagnostics,
      boolean minifying,
      Set<MethodReference> asyncMethods,
      SourceWriter writer,
      SourceWriter metadataWriter,
      VariableNameGenerator variableNameGenerator
   ) {
      this.context = context;
      this.diagnostics = diagnostics;
      this.minifying = minifying;
      this.asyncMethods = asyncMethods;
      this.writer = writer;
      this.statementRenderer = new StatementRenderer(context, writer, metadataWriter, variableNameGenerator);
   }

   public void setCurrentMethod(MethodNode node) {
      this.statementRenderer.setCurrentMethod(node);
   }

   public boolean isThreadLibraryUsed() {
      return this.threadLibraryUsed;
   }

   @Override
   public DependencyInfo getDependency() {
      return this.context.getDependencyInfo();
   }

   public void renderNative(Generator generator, boolean async, MethodReference reference) {
      this.threadLibraryUsed = false;
      this.async = async;
      this.statementRenderer.setAsync(async);
      generator.generate(this, this.writer, reference);
   }

   public void render(MethodNode node, boolean async) {
      this.threadLibraryUsed = false;
      this.async = async;
      this.statementRenderer.setAsync(async);
      this.prepareVariables(node);
      node.acceptVisitor(this);
      this.statementRenderer.clear();
   }

   private void prepareVariables(MethodNode method) {
      for (int i = 0; i < method.getVariables().size(); i++) {
         this.writer.emitVariables(new String[]{method.getVariables().get(i).getName()}, this.statementRenderer.variableName(i));
      }
   }

   public void renderParameters(MethodReference reference, Set<ElementModifier> modifiers) {
      this.renderParameters(reference, modifiers, false);
   }

   public void renderParameters(MethodReference reference, Set<ElementModifier> modifiers, boolean forceParentheses) {
      int startParam = 0;
      if (modifiers.contains(ElementModifier.STATIC)) {
         startParam = 1;
      }

      int count = reference.parameterCount() - startParam + 1;
      if (count != 1 || forceParentheses) {
         this.writer.append("(");
      }

      for (int i = startParam; i <= reference.parameterCount(); i++) {
         if (i > startParam) {
            this.writer.append(",").ws();
         }

         this.writer.append(this.statementRenderer.variableName(i));
      }

      if (count != 1 || forceParentheses) {
         this.writer.append(")");
      }
   }

   private void appendMonitor(StatementRenderer statementRenderer, MethodNode methodNode) {
      if (methodNode.getModifiers().contains(ElementModifier.STATIC)) {
         this.writer.appendFunction("$rt_cls").append("(").appendClass(methodNode.getReference().getClassName()).append(")");
      } else {
         this.writer.append(statementRenderer.variableName(0));
      }
   }

   @Override
   public void visit(RegularMethodNode method) {
      this.statementRenderer.setAsync(false);
      this.async = false;
      int variableCount = 0;

      for (VariableNode var : method.getVariables()) {
         variableCount = Math.max(variableCount, var.getIndex() + 1);
      }

      TryCatchFinder tryCatchFinder = new TryCatchFinder();
      method.getBody().acceptVisitor(tryCatchFinder);
      boolean hasTryCatch = tryCatchFinder.tryCatchFound;
      List<String> variableNames = new ArrayList<>();

      for (int i = method.getReference().parameterCount() + 1; i < variableCount; i++) {
         variableNames.add(this.statementRenderer.variableName(i));
      }

      if (hasTryCatch) {
         variableNames.add("$$je");
      }

      if (!variableNames.isEmpty()) {
         this.writer.append("let ");

         for (int i = 0; i < variableNames.size(); i++) {
            if (i > 0) {
               this.writer.append(",").ws();
            }

            this.writer.append(variableNames.get(i));
         }

         this.writer.append(";").softNewLine();
      }

      this.statementRenderer.setEnd(true);
      this.statementRenderer.setCurrentPart(0);
      if (method.getModifiers().contains(ElementModifier.SYNCHRONIZED)) {
         this.writer.appendMethod(NameFrequencyEstimator.MONITOR_ENTER_SYNC_METHOD);
         this.writer.append("(");
         this.appendMonitor(this.statementRenderer, method);
         this.writer.append(");").softNewLine();
         this.writer.append("try").ws().append("{").softNewLine().indent();
      }

      method.getBody().acceptVisitor(this.statementRenderer);
      if (method.getModifiers().contains(ElementModifier.SYNCHRONIZED)) {
         this.writer.outdent().append("}").ws().append("finally").ws().append("{").indent().softNewLine();
         this.writer.appendMethod(NameFrequencyEstimator.MONITOR_EXIT_SYNC_METHOD);
         this.writer.append("(");
         this.appendMonitor(this.statementRenderer, method);
         this.writer.append(");").softNewLine();
         this.writer.outdent().append("}").softNewLine();
      }
   }

   @Override
   public void visit(AsyncMethodNode methodNode) {
      this.threadLibraryUsed = true;
      this.statementRenderer.setAsync(true);
      this.async = true;
      MethodReference ref = methodNode.getReference();
      int variableCount = 0;

      for (VariableNode var : methodNode.getVariables()) {
         variableCount = Math.max(variableCount, var.getIndex() + 1);
      }

      List<String> variableNames = new ArrayList<>();

      for (int i = ref.parameterCount() + 1; i < variableCount; i++) {
         variableNames.add(this.statementRenderer.variableName(i));
      }

      TryCatchFinder tryCatchFinder = new TryCatchFinder();

      for (AsyncMethodPart part : methodNode.getBody()) {
         if (!tryCatchFinder.tryCatchFound) {
            part.getStatement().acceptVisitor(tryCatchFinder);
         }
      }

      boolean hasTryCatch = tryCatchFinder.tryCatchFound;
      if (hasTryCatch) {
         variableNames.add("$$je");
      }

      variableNames.add(this.context.pointerName());
      variableNames.add(this.context.tempVarName());
      this.writer.append("let ");

      for (int i = 0; i < variableNames.size(); i++) {
         if (i > 0) {
            this.writer.append(",").ws();
         }

         this.writer.append(variableNames.get(i));
      }

      this.writer.append(";").softNewLine();
      int firstToSave = 0;
      if (methodNode.getModifiers().contains(ElementModifier.STATIC)) {
         firstToSave = 1;
      }

      String popName = this.minifying ? "l" : "pop";
      String pushName = this.minifying ? "s" : "push";
      this.writer.append(this.context.pointerName()).ws().append('=').ws().append("0;").softNewLine();
      this.writer.append("if").ws().append("(").appendFunction("$rt_resuming").append("())").ws().append("{").indent().softNewLine();
      this.writer.append("let ").append(this.context.threadName()).ws().append('=').ws().appendFunction("$rt_nativeThread").append("();").softNewLine();
      this.writer.append(this.context.pointerName()).ws().append('=').ws().append(this.context.threadName()).append(".").append(popName).append("();");

      for (int i = variableCount - 1; i >= firstToSave; i--) {
         this.writer
            .append(this.statementRenderer.variableName(i))
            .ws()
            .append('=')
            .ws()
            .append(this.context.threadName())
            .append(".")
            .append(popName)
            .append("();");
      }

      this.writer.softNewLine();
      this.writer.outdent().append("}").softNewLine();
      if (methodNode.getModifiers().contains(ElementModifier.SYNCHRONIZED)) {
         this.writer.append("try").ws().append('{').indent().softNewLine();
      }

      Renderer.renderAsyncPrologue(this.writer, this.context);

      for (int i = 0; i < methodNode.getBody().size(); i++) {
         this.writer.append("case ").append(i).append(":").indent().softNewLine();
         if (i == 0 && methodNode.getModifiers().contains(ElementModifier.SYNCHRONIZED)) {
            this.writer.appendMethod(NameFrequencyEstimator.MONITOR_ENTER_METHOD);
            this.writer.append("(");
            this.appendMonitor(this.statementRenderer, methodNode);
            this.writer.append(");").softNewLine();
            this.statementRenderer.emitSuspendChecker();
         }

         AsyncMethodPart part = methodNode.getBody().get(i);
         this.statementRenderer.setEnd(true);
         this.statementRenderer.setCurrentPart(i);
         part.getStatement().acceptVisitor(this.statementRenderer);
         this.writer.outdent();
      }

      Renderer.renderAsyncEpilogue(this.writer);
      if (methodNode.getModifiers().contains(ElementModifier.SYNCHRONIZED)) {
         this.writer.outdent().append("}").ws().append("finally").ws().append('{').indent().softNewLine();
         this.writer.append("if").ws().append("(!").appendFunction("$rt_suspending").append("())").ws().append("{").indent().softNewLine();
         this.writer.appendMethod(NameFrequencyEstimator.MONITOR_EXIT_METHOD).append("(");
         this.appendMonitor(this.statementRenderer, methodNode);
         this.writer.append(");").softNewLine();
         this.writer.outdent().append('}').softNewLine();
         this.writer.outdent().append('}').softNewLine();
      }

      this.writer.appendFunction("$rt_nativeThread").append("().").append(pushName).append("(");

      for (int i = firstToSave; i < variableCount; i++) {
         this.writer.append(this.statementRenderer.variableName(i)).append(',').ws();
      }

      this.writer.append(this.context.pointerName()).append(");");
      this.writer.softNewLine();
   }

   @Override
   public String getParameterName(int index) {
      return this.statementRenderer.variableName(index);
   }

   @Override
   public ListableClassReaderSource getClassSource() {
      return this.context.getClassSource();
   }

   @Override
   public ResourceProvider getResourceProvider() {
      return this.context.getResourceProvider();
   }

   @Override
   public ClassReaderSource getInitialClassSource() {
      return this.context.getInitialClassSource();
   }

   @Override
   public ClassLoader getClassLoader() {
      return this.context.getClassLoader();
   }

   @Override
   public Properties getProperties() {
      return new Properties(this.context.getProperties());
   }

   @Override
   public <T> T getService(Class<T> type) {
      return this.context.getServices().getService(type);
   }

   @Override
   public boolean isAsync() {
      return this.async;
   }

   @Override
   public boolean isAsync(MethodReference method) {
      return this.asyncMethods.contains(method);
   }

   @Override
   public Diagnostics getDiagnostics() {
      return this.diagnostics;
   }

   @Override
   public boolean isDynamicInitializer(String className) {
      return this.context.isDynamicInitializer(className);
   }

   @Override
   public String importModule(String name) {
      return this.context.importModule(name);
   }

   @Override
   public int lookupString(String string) {
      return this.context.lookupString(string);
   }
}
