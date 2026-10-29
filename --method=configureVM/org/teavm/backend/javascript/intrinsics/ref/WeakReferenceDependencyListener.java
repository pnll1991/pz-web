package org.teavm.backend.javascript.intrinsics.ref;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.FieldDependency;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;

public class WeakReferenceDependencyListener extends AbstractDependencyListener {
   private DependencyNode initRef;

   @Override
   public void methodReached(DependencyAgent agent, MethodDependency method) {
      this.initRef = agent.createNode();
      if (method.getMethod().getOwnerName().equals(WeakReference.class.getName())) {
         this.referenceMethodReached(agent, method);
      } else if (method.getMethod().getOwnerName().equals(ReferenceQueue.class.getName())) {
         this.queueMethodReached(agent, method);
      }
   }

   private void referenceMethodReached(DependencyAgent agent, MethodDependency method) {
      switch (method.getMethod().getName()) {
         case "<init>":
            if (method.getParameterCount() == 3) {
               FieldDependency fieldx = agent.linkField(new FieldReference(method.getMethod().getOwnerName(), "value"));
               method.getVariable(1).connect(fieldx.getValue());
               method.getVariable(2).connect(this.initRef);
            }
            break;
         case "get":
            FieldDependency field = agent.linkField(new FieldReference(method.getMethod().getOwnerName(), "value"));
            field.getValue().connect(method.getResult());
      }
   }

   private void queueMethodReached(DependencyAgent agent, MethodDependency method) {
      switch (method.getMethod().getName()) {
         case "poll": {
            MethodDependency reportMethod = agent.linkMethod(new MethodReference(ReferenceQueue.class, "reportNext", Reference.class, boolean.class));
            this.initRef.connect(method.getResult());
            reportMethod.use();
            break;
         }
         case "<init>":
            agent.linkField(new FieldReference(ReferenceQueue.class.getName(), "inner"));
            agent.linkField(new FieldReference(ReferenceQueue.class.getName(), "registry"));
            break;
         case "registerCallback": {
            MethodDependency reportMethod = agent.linkMethod(new MethodReference(ReferenceQueue.class, "reportNext", Reference.class, boolean.class));
            this.initRef.connect(reportMethod.getVariable(1));
            reportMethod.use();
         }
      }
   }
}
