package org.teavm.backend.c.generate;

import java.util.List;
import org.teavm.ast.MethodNode;
import org.teavm.ast.RegularMethodNode;
import org.teavm.ast.VariableNode;
import org.teavm.backend.c.analyze.VolatileDefinitionFinder;
import org.teavm.backend.lowlevel.generate.NameProvider;
import org.teavm.hppc.IntContainer;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.lowlevel.CallSiteDescriptor;

public class CodeGenerator {
   private GenerationContext context;
   private ClassGenerationContext classContext;
   private CodeWriter writer;
   private CodeWriter localsWriter;
   private NameProvider names;
   private IncludeManager includes;
   private List<CallSiteDescriptor> callSites;

   public CodeGenerator(ClassGenerationContext classContext, CodeWriter writer, IncludeManager includes) {
      this.classContext = classContext;
      this.context = classContext.getContext();
      this.writer = writer;
      this.names = this.context.getNames();
      this.includes = includes;
   }

   public ClassGenerationContext getClassContext() {
      return this.classContext;
   }

   public void setCallSites(List<CallSiteDescriptor> callSites) {
      this.callSites = callSites;
   }

   public void generateMethod(RegularMethodNode methodNode) {
      generateMethodSignature(this.writer, this.names, methodNode.getReference(), methodNode.getModifiers().contains(ElementModifier.STATIC), true);
      this.writer.print(" {").indent().println();
      this.localsWriter = this.writer.fragment();
      CodeGenerationVisitor visitor = this.generateMethodBody(methodNode);
      this.generateLocals(methodNode, visitor.getTemporaries(), visitor.getSpilledVariables());
      this.writer.outdent().println("}");
   }

   private CodeGenerationVisitor generateMethodBody(RegularMethodNode methodNode) {
      VolatileDefinitionFinder volatileDefinitions = new VolatileDefinitionFinder();
      volatileDefinitions.findVolatileDefinitions(methodNode.getBody());
      CodeGenerationVisitor visitor = new CodeGenerationVisitor(this.classContext, this.writer, this.includes, this.callSites, volatileDefinitions);
      visitor.setAsync(this.context.isAsync(methodNode.getReference()));
      visitor.setCallingMethod(methodNode.getReference());
      methodNode.getBody().acceptVisitor(visitor);
      return visitor;
   }

   public static void generateMethodSignature(CodeWriter writer, NameProvider names, MethodReference methodRef, boolean isStatic, boolean withNames) {
      writer.printType(methodRef.getReturnType()).print(" ").print(names.forMethod(methodRef)).print("(");
      generateMethodParameters(writer, methodRef.getDescriptor(), isStatic, withNames);
      writer.print(")");
   }

   public static void generateMethodParameters(CodeWriter writer, MethodDescriptor methodRef, boolean isStatic, boolean withNames) {
      if (methodRef.parameterCount() != 0 || !isStatic) {
         int start = 0;
         if (!isStatic) {
            writer.print("void*");
            if (withNames) {
               writer.print(" teavm_this_");
            }
         } else {
            writer.printType(methodRef.parameterType(0));
            if (withNames) {
               writer.print(" teavm_local_1");
            }

            start++;
         }

         for (int i = start; i < methodRef.parameterCount(); i++) {
            writer.print(", ").printType(methodRef.parameterType(i));
            if (withNames) {
               writer.print(" ").print("teavm_local_").print(String.valueOf(i + 1));
            }
         }
      }
   }

   private void generateLocals(MethodNode methodNode, int[] temporaryCount, IntContainer spilledVariables) {
      int start = methodNode.getReference().parameterCount() + 1;

      for (int i = 0; i < start; i++) {
         if (spilledVariables.contains(i)) {
            VariableNode variableNode = methodNode.getVariables().get(i);
            this.localsWriter.printVolatileType(variableNode.getType()).print(" teavm_spill_").print(String.valueOf(i)).println(";");
         }
      }

      for (int i = start; i < methodNode.getVariables().size(); i++) {
         VariableNode variableNode = methodNode.getVariables().get(i);
         if (variableNode.getType() != null) {
            this.localsWriter.printType(variableNode.getType()).print(" teavm_local_").print(String.valueOf(i)).println(";");
            if (spilledVariables.contains(i)) {
               this.localsWriter.printVolatileType(variableNode.getType()).print(" teavm_spill_").print(String.valueOf(i)).println(";");
            }
         }
      }

      for (CVariableType type : CVariableType.values()) {
         for (int i = 0; i < temporaryCount[type.ordinal()]; i++) {
            this.localsWriter.print(type.text + " teavm_tmp_" + type.name().toLowerCase() + "_" + i).println(";");
         }
      }
   }
}
