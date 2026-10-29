package org.teavm.backend.lowlevel.dependency;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.MethodReference;

public class WeakReferenceDependencyListener extends AbstractDependencyListener {
   private DependencyNode referentNode;
   private DependencyNode referenceNode;

   @Override
   public void started(DependencyAgent agent) {
      this.referentNode = agent.createNode();
      this.referenceNode = agent.createNode();
   }

   @Override
   public void methodReached(DependencyAgent agent, MethodDependency method) {
      MethodReference methodRef = method.getReference();
      if (methodRef.getClassName().equals(WeakReference.class.getName())) {
         switch (methodRef.getName()) {
            case "<init>":
               this.reachReferenceInit(agent, method);
               break;
            case "get":
               this.reachReferenceGet(method);
         }
      } else if (methodRef.getClassName().equals(ReferenceQueue.class.getName())) {
         switch (methodRef.getName()) {
            case "<init>":
               this.reachQueueInit(agent, method);
               break;
            case "poll":
               this.reachQueuePoll(method);
         }
      }

      super.methodReached(agent, method);
   }

   private void reachReferenceInit(DependencyAgent agent, MethodDependency method) {
      MethodDependency superMethod = agent.linkMethod(new MethodReference(Reference.class, "<init>", void.class));
      method.getVariable(0).connect(superMethod.getVariable(0));
      superMethod.use();
      method.getVariable(0).connect(this.referenceNode);
      method.getVariable(1).connect(this.referentNode);
   }

   private void reachReferenceGet(MethodDependency method) {
      this.referentNode.connect(method.getResult());
   }

   private void reachQueuePoll(MethodDependency method) {
      this.referenceNode.connect(method.getResult());
   }

   private void reachQueueInit(DependencyAgent agent, MethodDependency method) {
      MethodDependency superMethod = agent.linkMethod(new MethodReference(Object.class, "<init>", void.class));
      method.getVariable(0).connect(superMethod.getVariable(0));
      superMethod.use();
   }
}
