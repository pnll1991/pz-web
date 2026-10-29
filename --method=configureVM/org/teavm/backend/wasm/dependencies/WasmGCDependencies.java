package org.teavm.backend.wasm.dependencies;

import java.util.Arrays;
import java.util.List;
import org.teavm.backend.wasm.WasmRuntime;
import org.teavm.backend.wasm.runtime.WasmGCSupport;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyAnalyzer;
import org.teavm.dependency.MethodDependency;
import org.teavm.interop.Address;
import org.teavm.model.ClassReader;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.runtime.EventQueue;
import org.teavm.runtime.Fiber;
import org.teavm.runtime.heap.Heap;

public class WasmGCDependencies {
   private DependencyAnalyzer analyzer;

   public WasmGCDependencies(DependencyAnalyzer analyzer) {
      this.analyzer = analyzer;
   }

   public void contribute() {
      this.analyzer.linkClass("java.lang.Class");
      this.contributeWasmRuntime();
      this.contributeMathUtils();
      this.contributeExceptionUtils();
      this.contributeInitializerUtils();
      this.contributeString();
      this.contributeBuffers();
      this.contributeFiber();
      this.analyzer.addDependencyListener(new WasmGCReferenceQueueDependency());
      this.analyzer.addDependencyListener(new WasmGCResourceDependency());
      this.analyzer.addDependencyListener(new SystemArrayCopyDependencySupport());
      this.analyzer.addDependencyListener(new WasmGCSignatureDependencyListener());
      this.analyzer.addDependencyListener(new WasmGCExportDependencyListener());
   }

   public void contributeStandardExports() {
      this.analyzer.linkMethod(new MethodReference(WasmGCSupport.class, "createStringArray", int.class, String[].class)).use();
      this.analyzer.linkMethod(new MethodReference(WasmGCSupport.class, "createStringBuilder", StringBuilder.class)).use();
      this.analyzer
         .linkMethod(new MethodReference(WasmGCSupport.class, "setToStringArray", String[].class, int.class, String.class, void.class))
         .propagate(1, this.analyzer.getType(ValueType.arrayOf(ValueType.object("java.lang.String"))))
         .propagate(3, this.analyzer.getClassType("java.lang.String"))
         .use();
      this.analyzer
         .linkMethod(new MethodReference(StringBuilder.class, "append", char.class, StringBuilder.class))
         .propagate(0, this.analyzer.getClassType("java.lang.StringBuilder"))
         .use();
      this.analyzer
         .linkMethod(new MethodReference(StringBuilder.class, "toString", String.class))
         .propagate(0, this.analyzer.getClassType("java.lang.StringBuilder"))
         .use();
      this.analyzer.linkMethod(new MethodReference(String.class, "length", int.class)).propagate(0, this.analyzer.getClassType("java.lang.String")).use();
      this.analyzer
         .linkMethod(new MethodReference(String.class, "charAt", int.class, char.class))
         .propagate(0, this.analyzer.getClassType("java.lang.String"))
         .use();
   }

   private void contributeWasmRuntime() {
      for (Class cls : List.of(int.class, long.class, float.class, double.class)) {
         this.analyzer.linkMethod(new MethodReference(WasmRuntime.class, "lt", cls, cls, boolean.class)).use();
         this.analyzer.linkMethod(new MethodReference(WasmRuntime.class, "gt", cls, cls, boolean.class)).use();
      }

      for (Class cls : List.of(int.class, long.class)) {
         this.analyzer.linkMethod(new MethodReference(WasmRuntime.class, "ltu", cls, cls, boolean.class)).use();
         this.analyzer.linkMethod(new MethodReference(WasmRuntime.class, "gtu", cls, cls, boolean.class)).use();
      }

      for (Class cls : List.of(float.class, double.class)) {
         this.analyzer.linkMethod(new MethodReference(WasmRuntime.class, "min", cls, cls, cls)).use();
         this.analyzer.linkMethod(new MethodReference(WasmRuntime.class, "max", cls, cls, cls)).use();
      }
   }

   private void contributeMathUtils() {
      for (Class type : Arrays.asList(int.class, long.class, float.class, double.class)) {
         MethodReference method = new MethodReference(WasmRuntime.class, "compare", type, type, int.class);
         this.analyzer.linkMethod(method).use();
      }

      for (Class type : Arrays.asList(float.class, double.class)) {
         MethodReference method = new MethodReference(WasmRuntime.class, "compareLess", type, type, int.class);
         this.analyzer.linkMethod(method).use();
      }

      for (Class type : Arrays.asList(int.class, long.class)) {
         MethodReference method = new MethodReference(WasmRuntime.class, "compareUnsigned", type, type, int.class);
         this.analyzer.linkMethod(method).use();
      }

      for (Class type : Arrays.asList(float.class, double.class)) {
         MethodReference method = new MethodReference(WasmRuntime.class, "remainder", type, type, type);
         this.analyzer.linkMethod(method).use();
      }
   }

   private void contributeExceptionUtils() {
      this.analyzer.linkMethod(new MethodReference(WasmGCSupport.class, "npe", NullPointerException.class)).use();
      this.analyzer.linkMethod(new MethodReference(WasmGCSupport.class, "aiiobe", ArrayIndexOutOfBoundsException.class)).use();
      this.analyzer.linkMethod(new MethodReference(WasmGCSupport.class, "cce", ClassCastException.class)).use();
      this.analyzer.linkMethod(new MethodReference(WasmGCSupport.class, "throwCloneNotSupportedException", void.class)).use();
      this.analyzer.linkMethod(new MethodReference(WasmGCSupport.class, "defaultClone", Object.class, Object.class)).use();
      this.analyzer.linkMethod(new MethodReference(NullPointerException.class, "<init>", void.class)).use();
      this.analyzer.linkMethod(new MethodReference(Throwable.class, "getMessage", String.class)).propagate(0, Throwable.class).use();
      this.analyzer.addDependencyListener(new AbstractDependencyListener() {
         @Override
         public void classReached(DependencyAgent agent, String className) {
            if (className.equals(Throwable.class.getName())) {
               agent.linkMethod(new MethodReference(Throwable.class, "getMessage", String.class)).propagate(0, Throwable.class).use();
            }
         }
      });
   }

   private void contributeInitializerUtils() {
      this.analyzer.linkMethod(new MethodReference(WasmGCSupport.class, "nextCharArray", char[].class)).use();
   }

   private void contributeString() {
      this.analyzer.addDependencyListener(new StringInternDependencySupport());
   }

   private void contributeBuffers() {
      this.analyzer.linkMethod(new MethodReference(Heap.class, "init", Address.class, int.class, int.class, void.class)).use();
   }

   private void contributeFiber() {
      this.analyzer.linkMethod(new MethodReference(Fiber.class, "isPresentAndResuming", Fiber.class, boolean.class)).propagate(1, Fiber.class).use();
      this.analyzer.linkMethod(new MethodReference(Fiber.class, "isPresentAndSuspending", Fiber.class, boolean.class)).propagate(1, Fiber.class).use();
      this.analyzer.linkMethod(new MethodReference(Fiber.class, "current", Fiber.class)).use();
      this.analyzer.linkMethod(new MethodReference(Thread.class, "setCurrentThread", Thread.class, void.class)).use();
      MethodDependency offerMethod = this.analyzer.linkMethod(new MethodReference(EventQueue.class, "offer", EventQueue.Event.class, long.class, int.class));
      MethodDependency runEventMethod = this.analyzer.linkMethod(new MethodReference(EventQueue.class, "run", EventQueue.Event.class, void.class));
      offerMethod.getVariable(1).addConsumer(type -> {
         runEventMethod.getVariable(1).propagate(type);
         runEventMethod.use();
      });
      ClassReader fiberClass = this.analyzer.getClassSource().get(Fiber.class.getName());

      for (MethodReader method : fiberClass.getMethods()) {
         if (method.getName().startsWith("pop")) {
            this.analyzer.linkMethod(method.getReference()).propagate(0, Fiber.class).use();
         } else if (method.getName().equals("reversePush")) {
            this.analyzer.linkMethod(method.getReference()).propagate(2, Fiber.class).use();
         }
      }
   }
}
