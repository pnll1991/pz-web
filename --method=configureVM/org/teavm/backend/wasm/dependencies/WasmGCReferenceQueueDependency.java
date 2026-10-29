package org.teavm.backend.wasm.dependencies;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.MethodReference;

public class WasmGCReferenceQueueDependency extends AbstractDependencyListener {
   private DependencyNode valueNode;
   private boolean refQueuePassedToRef;
   private boolean refQueuePoll;

   @Override
   public void started(DependencyAgent agent) {
      this.valueNode = agent.createNode();
   }

   @Override
   public void methodReached(DependencyAgent agent, MethodDependency method) {
      if (method.getMethod().getOwnerName().equals("java.lang.ref.WeakReference")) {
         switch (method.getMethod().getName()) {
            case "<init>":
               if (method.getMethod().parameterCount() == 2) {
                  this.refQueuePassedToRef = true;
                  this.checkRefQueue(agent);
               }

               method.getVariable(1).connect(this.valueNode);
               break;
            case "get":
               this.valueNode.connect(method.getResult());
         }
      } else if (method.getMethod().getOwnerName().equals(ReferenceQueue.class.getName()) && method.getMethod().getName().equals("poll")) {
         this.refQueuePoll = true;
         this.checkRefQueue(agent);
      }
   }

   private void checkRefQueue(DependencyAgent agent) {
      if (this.refQueuePassedToRef && this.refQueuePoll) {
         agent.linkMethod(new MethodReference(ReferenceQueue.class, "supply", Reference.class, void.class))
            .propagate(0, ReferenceQueue.class)
            .propagate(1, WeakReference.class)
            .use();
      }
   }
}
