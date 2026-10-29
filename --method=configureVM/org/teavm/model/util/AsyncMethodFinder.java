package org.teavm.model.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.teavm.callgraph.CallGraph;
import org.teavm.callgraph.CallGraphNode;
import org.teavm.callgraph.CallSite;
import org.teavm.dependency.DependencyInfo;
import org.teavm.interop.Async;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ProgramReader;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.AbstractInstructionReader;
import org.teavm.runtime.Fiber;

public class AsyncMethodFinder {
   private Set<MethodReference> asyncMethods = new HashSet<>();
   private DependencyInfo dependency;
   private Map<MethodReference, Boolean> asyncFamilyMethods = new HashMap<>();
   private Set<MethodReference> readonlyAsyncMethods = Collections.unmodifiableSet(this.asyncMethods);
   private Set<MethodReference> readonlyAsyncFamilyMethods = Collections.unmodifiableSet(this.asyncFamilyMethods.keySet());
   private CallGraph callGraph;
   private ListableClassReaderSource classSource;
   private boolean hasAsyncMethods;

   public AsyncMethodFinder(CallGraph callGraph, DependencyInfo dependency) {
      this.callGraph = callGraph;
      this.dependency = dependency;
   }

   public Set<MethodReference> getAsyncMethods() {
      return this.readonlyAsyncMethods;
   }

   public Set<MethodReference> getAsyncFamilyMethods() {
      return this.readonlyAsyncFamilyMethods;
   }

   public void find(ListableClassReaderSource classSource) {
      this.classSource = classSource;
      this.hasAsyncMethods = this.findAsyncMethods();

      for (String clsName : classSource.getClassNames()) {
         ClassReader cls = classSource.get(clsName);

         for (MethodReader method : cls.getMethods()) {
            if (this.dependency.getReachableMethods().contains(method.getReference())
               && !this.asyncMethods.contains(method.getReference())
               && method.getAnnotations().get(Async.class.getName()) != null) {
               this.add(method.getReference(), new AsyncMethodFinder.CallStack(method.getReference(), null));
            }
         }
      }

      if (this.hasAsyncMethods) {
         for (String clsName : classSource.getClassNames()) {
            ClassReader cls = classSource.get(clsName);

            for (MethodReader method : cls.getMethods()) {
               if (this.dependency.getReachableMethods().contains(method.getReference())
                  && !this.asyncMethods.contains(method.getReference())
                  && method.getProgram() != null
                  && this.hasMonitor(method)) {
                  this.add(method.getReference(), new AsyncMethodFinder.CallStack(method.getReference(), null));
               }
            }
         }
      }

      for (MethodReference method : this.asyncMethods) {
         this.addOverriddenToFamily(method);
      }

      for (String clsName : classSource.getClassNames()) {
         ClassReader cls = classSource.get(clsName);

         for (MethodReader method : cls.getMethods()) {
            this.addToFamily(method.getReference());
         }
      }

      for (Entry<MethodReference, Boolean> entry : new ArrayList<>(this.asyncFamilyMethods.entrySet())) {
         if (!entry.getValue()) {
            this.asyncFamilyMethods.remove(entry.getKey());
         }
      }
   }

   private boolean findAsyncMethods() {
      boolean result = false;

      label35:
      for (String clsName : this.classSource.getClassNames()) {
         ClassReader cls = this.classSource.get(clsName);

         for (MethodReader method : cls.getMethods()) {
            if (this.asyncMethods.contains(method.getReference()) && method.getProgram() != null && this.hasMonitor(method)) {
               result = true;
               break label35;
            }
         }
      }

      boolean hasThreads = this.dependency.getReachableMethods().contains(new MethodReference(Thread.class, "start", void.class));
      return result && hasThreads;
   }

   public boolean hasAsyncMethods() {
      return this.hasAsyncMethods;
   }

   private boolean hasMonitor(MethodReader method) {
      if (method.hasModifier(ElementModifier.SYNCHRONIZED)) {
         return true;
      }

      ProgramReader program = method.getProgram();
      AsyncMethodFinder.AsyncInstructionReader insnReader = new AsyncMethodFinder.AsyncInstructionReader();

      for (int i = 0; i < program.basicBlockCount(); i++) {
         program.basicBlockAt(i).readAllInstructions(insnReader);
         if (insnReader.async) {
            return true;
         }
      }

      return false;
   }

   private void add(MethodReference methodRef, AsyncMethodFinder.CallStack stack) {
      if (!methodRef.getClassName().equals(Fiber.class.getName())) {
         if (this.asyncMethods.add(methodRef)) {
            CallGraphNode node = this.callGraph.getNode(methodRef);
            if (node != null) {
               ClassReader cls = this.classSource.get(methodRef.getClassName());
               if (cls != null) {
                  MethodReader method = cls.getMethod(methodRef.getDescriptor());
                  if (method != null) {
                     if (this.hasAsyncMethods
                        || !methodRef.getClassName().equals("java.lang.Object")
                        || !methodRef.getName().equals("monitorEnter") && !methodRef.getName().equals("monitorExit")
                        || methodRef.parameterCount() != 1) {
                        for (CallSite callSite : node.getCallerCallSites()) {
                           for (CallGraphNode caller : callSite.getCallers()) {
                              this.add(caller.getMethod(), new AsyncMethodFinder.CallStack(caller.getMethod(), stack));
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void addOverriddenToFamily(MethodReference methodRef) {
      this.asyncFamilyMethods.put(methodRef, true);
      ClassReader cls = this.classSource.get(methodRef.getClassName());
      if (cls != null) {
         for (MethodReference overriddenMethod : this.findOverriddenMethods(cls, methodRef.getDescriptor())) {
            this.addOverriddenToFamily(overriddenMethod);
         }
      }
   }

   private boolean addToFamily(MethodReference methodRef) {
      Boolean cachedResult = this.asyncFamilyMethods.get(methodRef);
      if (cachedResult != null) {
         return cachedResult;
      }

      boolean result = this.addToFamilyCacheMiss(methodRef);
      this.asyncFamilyMethods.put(methodRef, result);
      return result;
   }

   private boolean addToFamilyCacheMiss(MethodReference methodRef) {
      if (this.asyncMethods.contains(methodRef)) {
         return true;
      }

      ClassReader cls = this.classSource.get(methodRef.getClassName());
      if (cls == null) {
         return false;
      }

      for (MethodReference overriddenMethod : this.findOverriddenMethods(cls, methodRef.getDescriptor())) {
         if (this.addToFamily(overriddenMethod)) {
            return true;
         }
      }

      return false;
   }

   private Set<MethodReference> findOverriddenMethods(ClassReader cls, MethodDescriptor methodDesc) {
      List<String> parents = new ArrayList<>();
      if (cls.getParent() != null) {
         parents.add(cls.getParent());
      }

      parents.addAll(cls.getInterfaces());
      Set<String> visited = new HashSet<>();
      Set<MethodReference> overridden = new HashSet<>();

      for (String parent : parents) {
         this.findOverriddenMethods(parent, methodDesc, overridden, visited);
      }

      return overridden;
   }

   private void findOverriddenMethods(String className, MethodDescriptor methodDesc, Set<MethodReference> result, Set<String> visited) {
      if (visited.add(className)) {
         if (!methodDesc.getName().equals("<init>") && !methodDesc.getName().equals("<clinit>")) {
            ClassReader cls = this.classSource.get(className);
            if (cls != null) {
               MethodReader method = cls.getMethod(methodDesc);
               if (method != null) {
                  if (!method.hasModifier(ElementModifier.STATIC) && !method.hasModifier(ElementModifier.FINAL)) {
                     result.add(method.getReference());
                  }
               } else {
                  if (cls.getParent() != null) {
                     this.findOverriddenMethods(cls.getParent(), methodDesc, result, visited);
                  }

                  for (String iface : cls.getInterfaces()) {
                     this.findOverriddenMethods(iface, methodDesc, result, visited);
                  }
               }
            }
         }
      }
   }

   static class AsyncInstructionReader extends AbstractInstructionReader {
      boolean async;

      @Override
      public void monitorEnter(VariableReader objectRef) {
         this.async = true;
      }
   }

   static class CallStack {
      MethodReference method;
      AsyncMethodFinder.CallStack next;

      CallStack(MethodReference method, AsyncMethodFinder.CallStack next) {
         this.method = method;
         this.next = next;
      }

      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder();

         for (AsyncMethodFinder.CallStack stack = this; stack != null; stack = stack.next) {
            sb.append("\n    calling " + stack.method);
         }

         return sb.toString();
      }
   }
}
