package org.teavm.model.lowlevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.common.DisjointSet;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.interop.Function;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ProgramReader;
import org.teavm.model.TextLocation;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.AbstractInstructionReader;
import org.teavm.model.instructions.InvocationType;

public class ExportDependencyListener extends AbstractDependencyListener {
   private Set<MethodReference> exportedMethods = new LinkedHashSet<>();
   private Set<? extends MethodReference> readonlyExportedMethods = Collections.unmodifiableSet(this.exportedMethods);
   private Map<ExportedMethodKey, MethodReference> resolvedMethods = new HashMap<>();
   private Map<? extends ExportedMethodKey, ? extends MethodReference> readonlyResolvedMethods = Collections.unmodifiableMap(this.resolvedMethods);
   private Characteristics characteristics;

   @Override
   public void started(DependencyAgent agent) {
      this.characteristics = new Characteristics(agent.getClassSource());
   }

   public Set<? extends MethodReference> getExportedMethods() {
      return this.readonlyExportedMethods;
   }

   public Map<? extends ExportedMethodKey, ? extends MethodReference> getResolvedMethods() {
      return this.readonlyResolvedMethods;
   }

   @Override
   public void methodReached(DependencyAgent agent, MethodDependency method) {
      if (method.getMethod() != null && method.getMethod().getProgram() != null) {
         ProgramReader program = method.getMethod().getProgram();
         ExportDependencyListener.FunctionGetFinder finder = new ExportDependencyListener.FunctionGetFinder(program.variableCount());

         for (BasicBlockReader block : program.getBasicBlocks()) {
            block.readAllInstructions(finder);
         }

         if (!finder.invocations.isEmpty()) {
            this.processInvocations(agent, method.getMethod(), finder);
         }
      }
   }

   private void processInvocations(DependencyAgent agent, MethodReader method, ExportDependencyListener.FunctionGetFinder finder) {
      int[] variableClasses = finder.variableClasses.pack(method.getProgram().variableCount());
      String[] stringConstants = new String[finder.stringConstants.length];
      ValueType[] classConstants = new ValueType[finder.classConstants.length];

      for (int i = 0; i < stringConstants.length; i++) {
         stringConstants[variableClasses[i]] = finder.stringConstants[i];
         classConstants[variableClasses[i]] = finder.classConstants[i];
      }

      Diagnostics diagnostics = agent.getDiagnostics();

      for (ExportDependencyListener.Invocation invocation : finder.invocations) {
         ValueType functionClass = classConstants[variableClasses[invocation.functionClassVar]];
         ValueType targetClass = classConstants[variableClasses[invocation.classVar]];
         String methodName = stringConstants[variableClasses[invocation.methodVar]];
         CallLocation location = new CallLocation(method.getReference(), invocation.location);
         boolean valid = true;
         if (!(functionClass instanceof ValueType.Object)) {
            diagnostics.error(location, "First argument must be class literal representing non-array and no-primitive class");
            valid = false;
         }

         if (!(targetClass instanceof ValueType.Object)) {
            diagnostics.error(location, "Second argument must be class literal representing non-array and no-primitive class");
            valid = false;
         }

         if (methodName == null) {
            diagnostics.error(location, "Third argument must be string literal");
            valid = false;
         }

         if (valid) {
            this.processInvocation(
               agent, location, ((ValueType.Object)functionClass).getClassName(), ((ValueType.Object)targetClass).getClassName(), methodName
            );
         }
      }
   }

   private void processInvocation(DependencyAgent agent, CallLocation location, String functionClassName, String targetClassName, String methodName) {
      Diagnostics diagnostics = agent.getDiagnostics();
      ClassHierarchy hierarchy = agent.getClassHierarchy();
      boolean valid = true;
      ClassReader functionClass = hierarchy.getClassSource().get(functionClassName);
      if (functionClass == null) {
         diagnostics.error(location, "Class '{{c0}}' not found in class path", functionClassName);
         valid = false;
      } else if (!this.characteristics.isFunction(functionClassName)) {
         diagnostics.error(location, "Class '{{c0}}' does not represent a function", functionClassName);
         valid = false;
      }

      ClassReader targetClass = hierarchy.getClassSource().get(targetClassName);
      if (targetClass == null) {
         diagnostics.error(location, "Class '{{c0}}' not found in class path", functionClassName);
         valid = false;
      }

      if (valid) {
         MethodReader sam = this.extractSingleMethod(diagnostics, location, functionClass);
         if (sam == null) {
            valid = false;
         }

         List<MethodReader> candidates = targetClass.getMethods()
            .stream()
            .filter(method -> method.getName().equals(methodName) && method.hasModifier(ElementModifier.STATIC))
            .collect(Collectors.toList());
         if (candidates.isEmpty()) {
            diagnostics.error(location, "There's no static method '" + methodName + "' in class '{{c0}}'", targetClass.getName());
            valid = false;
         }

         if (valid) {
            List<MethodReader> signatureCandidates = candidates.stream()
               .filter(method -> this.matchSignature(hierarchy, sam, method))
               .collect(Collectors.toList());
            if (signatureCandidates.isEmpty()) {
               if (candidates.size() == 1) {
                  diagnostics.error(
                     location, "Method '{{m0}}' does not match signature of function method '{{m1}}'", candidates.get(0).getReference(), sam.getReference()
                  );
               } else {
                  diagnostics.error(location, "None of '" + methodName + "' methods match signature of function method '{{m0}}'", sam.getReference());
               }
            } else {
               MethodReader resolvedMethod = this.findMostSpecific(diagnostics, location, hierarchy, signatureCandidates);
               if (resolvedMethod != null) {
                  MethodReference reference = resolvedMethod.getReference();
                  this.resolvedMethods.put(new ExportedMethodKey(functionClassName, targetClassName, methodName), reference);
                  this.exportedMethods.add(reference);
                  MethodDependency dep = agent.linkMethod(reference);
                  dep.addLocation(location);
                  dep.use();
               }
            }
         }
      }
   }

   private MethodReader extractSingleMethod(Diagnostics diagnostics, CallLocation location, ClassReader cls) {
      MethodReader candidate = null;

      for (MethodReader method : cls.getMethods()) {
         if (!method.hasModifier(ElementModifier.STATIC) && method.hasModifier(ElementModifier.ABSTRACT)) {
            if (candidate != null) {
               diagnostics.error(location, "Function class {{c0}} must have one abstract method, it has multiple", cls.getName());
               return null;
            }

            candidate = method;
         }
      }

      if (candidate == null) {
         diagnostics.error(location, "Function class {{c0}} must have one abstract method, it has none", cls.getName());
         return null;
      } else {
         return candidate;
      }
   }

   private MethodReader findMostSpecific(Diagnostics diagnostics, CallLocation location, ClassHierarchy hierarchy, List<MethodReader> methods) {
      MethodReader mostSpecificSoFar = methods.get(0);

      for (int i = 1; i < methods.size(); i++) {
         MethodReader candidate = methods.get(i);
         if (this.matchSignature(hierarchy, mostSpecificSoFar, candidate)) {
            mostSpecificSoFar = candidate;
         } else if (!this.matchSignature(hierarchy, candidate, mostSpecificSoFar)) {
            diagnostics.error(location, "Ambiguous methods found for this export, examples are '{{m0}}' and {{m1}}", candidate, mostSpecificSoFar);
            return null;
         }
      }

      return mostSpecificSoFar;
   }

   private boolean matchSignature(ClassHierarchy hierarchy, MethodReader functionMethod, MethodReader candidateMethod) {
      if (functionMethod.parameterCount() > candidateMethod.parameterCount()) {
         return false;
      }

      for (int i = 0; i < functionMethod.parameterCount(); i++) {
         if (!hierarchy.isSuperType(functionMethod.parameterType(i), candidateMethod.parameterType(i), false)) {
            return false;
         }
      }

      return true;
   }

   static class FunctionGetFinder extends AbstractInstructionReader {
      DisjointSet variableClasses = new DisjointSet();
      String[] stringConstants;
      ValueType[] classConstants;
      List<ExportDependencyListener.Invocation> invocations = new ArrayList<>();
      private TextLocation location;

      FunctionGetFinder(int variableCount) {
         for (int i = 0; i < variableCount; i++) {
            this.variableClasses.create();
         }

         this.stringConstants = new String[variableCount];
         this.classConstants = new ValueType[variableCount];
      }

      @Override
      public void location(TextLocation location) {
         this.location = location;
      }

      @Override
      public void classConstant(VariableReader receiver, ValueType cst) {
         this.classConstants[receiver.getIndex()] = cst;
      }

      @Override
      public void stringConstant(VariableReader receiver, String cst) {
         this.stringConstants[receiver.getIndex()] = cst;
      }

      @Override
      public void assign(VariableReader receiver, VariableReader assignee) {
         this.variableClasses.union(receiver.getIndex(), assignee.getIndex());
      }

      @Override
      public void invoke(
         VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments, InvocationType type
      ) {
         if (method.getClassName().equals(Function.class.getName())
            && method.getName().equals("get")
            && type == InvocationType.SPECIAL
            && instance == null
            && arguments.size() == 3) {
            ExportDependencyListener.Invocation invocation = new ExportDependencyListener.Invocation(
               this.location, arguments.get(0).getIndex(), arguments.get(1).getIndex(), arguments.get(2).getIndex()
            );
            this.invocations.add(invocation);
         }
      }
   }

   static class Invocation {
      TextLocation location;
      int functionClassVar;
      int classVar;
      int methodVar;

      Invocation(TextLocation location, int functionClassVar, int classVar, int methodVar) {
         this.location = location;
         this.functionClassVar = functionClassVar;
         this.classVar = classVar;
         this.methodVar = methodVar;
      }
   }
}
