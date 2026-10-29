package org.teavm.backend.javascript.templating;

import java.util.HashMap;
import java.util.Map;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.rendering.AstWriter;
import org.teavm.backend.javascript.rendering.DefaultGlobalNameWriter;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.rhino.javascript.ast.AstNode;
import org.teavm.rhino.javascript.ast.ElementGet;
import org.teavm.rhino.javascript.ast.FunctionCall;
import org.teavm.rhino.javascript.ast.Name;
import org.teavm.rhino.javascript.ast.PropertyGet;
import org.teavm.rhino.javascript.ast.StringLiteral;

public class TemplatingAstWriter extends AstWriter {
   private Map<String, SourceFragment> fragments = new HashMap<>();
   private ClassInitializerInfo classInitializerInfo;
   private boolean topLevelOutput;

   public TemplatingAstWriter(SourceWriter writer, ClassInitializerInfo classInitializerInfo, boolean topLevelOutput) {
      super(writer, new DefaultGlobalNameWriter());
      this.classInitializerInfo = classInitializerInfo;
      this.topLevelOutput = topLevelOutput;
   }

   public void setFragment(String name, SourceFragment fragment) {
      this.fragments.put(name, fragment);
   }

   @Override
   protected boolean intrinsic(FunctionCall node, int precedence) {
      if (node.getTarget() instanceof Name) {
         Name name = (Name)node.getTarget();
         if (this.isTopLevelIdentifier(name.getIdentifier())) {
            return this.tryIntrinsicName(node, name.getIdentifier());
         }
      }

      return super.intrinsic(node, precedence);
   }

   private boolean tryIntrinsicName(FunctionCall node, String name) {
      switch (name) {
         case "teavm_javaClass":
            return this.writeJavaClass(node);
         case "teavm_javaMethod":
            return this.writeJavaMethod(node);
         case "teavm_javaConstructor":
            return this.writeJavaConstructor(node);
         case "teavm_javaClassInit":
            return this.writeJavaClassInit(node);
         case "teavm_fragment":
            return this.writeFragment(node);
         default:
            return false;
      }
   }

   private boolean writeJavaClass(FunctionCall node) {
      if (node.getArguments().size() != 1) {
         return false;
      }

      AstNode classArg = (AstNode)node.getArguments().get(0);
      if (!(classArg instanceof StringLiteral)) {
         return false;
      }

      this.writer.appendClass(((StringLiteral)classArg).getValue());
      return true;
   }

   private boolean writeJavaMethod(FunctionCall node) {
      if (node.getArguments().size() != 2) {
         return false;
      } else {
         AstNode classArg = (AstNode)node.getArguments().get(0);
         AstNode methodArg = (AstNode)node.getArguments().get(1);
         if (classArg instanceof StringLiteral && methodArg instanceof StringLiteral) {
            MethodReference method = new MethodReference(((StringLiteral)classArg).getValue(), MethodDescriptor.parse(((StringLiteral)methodArg).getValue()));
            this.writer.appendMethod(method);
            return true;
         } else {
            return false;
         }
      }
   }

   private boolean writeJavaConstructor(FunctionCall node) {
      if (node.getArguments().size() != 2) {
         return false;
      } else {
         AstNode classArg = (AstNode)node.getArguments().get(0);
         AstNode methodArg = (AstNode)node.getArguments().get(1);
         if (classArg instanceof StringLiteral && methodArg instanceof StringLiteral) {
            MethodReference method = new MethodReference(
               ((StringLiteral)classArg).getValue(), "<init>", MethodDescriptor.parseSignature(((StringLiteral)methodArg).getValue())
            );
            this.writer.appendInit(method);
            return true;
         } else {
            return false;
         }
      }
   }

   private boolean writeJavaClassInit(FunctionCall node) {
      if (node.getArguments().size() != 1) {
         return false;
      }

      AstNode classArg = (AstNode)node.getArguments().get(0);
      if (!(classArg instanceof StringLiteral)) {
         return false;
      }

      String className = ((StringLiteral)classArg).getValue();
      if (this.classInitializerInfo != null && !this.classInitializerInfo.isDynamicInitializer(className)) {
         this.writer.append("(()").sameLineWs().append("=>").ws().append("{})");
      } else {
         this.writer.appendClassInit(className);
      }

      return true;
   }

   private boolean writeFragment(FunctionCall node) {
      if (node.getArguments().size() != 1) {
         return false;
      }

      AstNode fragmentArg = (AstNode)node.getArguments().get(0);
      if (!(fragmentArg instanceof StringLiteral)) {
         return false;
      }

      SourceFragment fragment = this.fragments.get(((StringLiteral)fragmentArg).getValue());
      fragment.write(this.writer, 19);
      return true;
   }

   @Override
   protected void print(ElementGet node) {
      if (node.getElement() instanceof FunctionCall) {
         FunctionCall call = (FunctionCall)node.getElement();
         if (call.getTarget() instanceof Name) {
            Name name = (Name)call.getTarget();
            if (this.isTopLevelIdentifier(name.getIdentifier())) {
               switch (name.getIdentifier()) {
                  case "teavm_javaVirtualMethod":
                     if (this.writeJavaVirtualMethod(node, call)) {
                        return;
                     }
                     break;
                  case "teavm_javaField":
                     if (this.writeJavaField(node, call)) {
                        return;
                     }
               }
            }
         }
      }

      super.print(node);
   }

   @Override
   public void print(PropertyGet node) {
      if (node.getTarget() instanceof Name) {
         Name name = (Name)node.getTarget();
         if (this.isTopLevelIdentifier(name.getIdentifier()) && name.getIdentifier().equals("teavm_globals")) {
            boolean oldRootScope = this.rootScope;
            this.rootScope = false;
            this.writer.appendGlobal(node.getProperty().getIdentifier());
            this.rootScope = oldRootScope;
            return;
         }
      }

      super.print(node);
   }

   private boolean writeJavaVirtualMethod(ElementGet get, FunctionCall call) {
      AstNode arg = (AstNode)call.getArguments().get(0);
      if (!(arg instanceof StringLiteral)) {
         return false;
      }

      MethodDescriptor method = MethodDescriptor.parse(((StringLiteral)arg).getValue());
      this.print(get.getTarget());
      this.writer.append('.').appendVirtualMethod(method);
      return true;
   }

   private boolean writeJavaField(ElementGet get, FunctionCall call) {
      if (call.getArguments().size() != 2) {
         return false;
      } else {
         AstNode classArg = (AstNode)call.getArguments().get(0);
         AstNode fieldArg = (AstNode)call.getArguments().get(1);
         if (classArg instanceof StringLiteral && fieldArg instanceof StringLiteral) {
            String className = ((StringLiteral)classArg).getValue();
            String fieldName = ((StringLiteral)fieldArg).getValue();
            this.print(get.getTarget());
            this.writer.append('.').appendField(new FieldReference(className, fieldName));
            return true;
         } else {
            return false;
         }
      }
   }

   @Override
   public boolean isTopLevelOutput() {
      return this.topLevelOutput;
   }
}
