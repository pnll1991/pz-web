package org.teavm.dependency;

import java.util.BitSet;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntSet;
import org.teavm.hppc.cursors.IntCursor;
import org.teavm.model.CallLocation;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.ValueType;

class VirtualCallConsumer implements DependencyConsumer {
   private static final int SMALL_TYPES_THRESHOLD = 16;
   private final MethodDescriptor methodDesc;
   private final DependencyAnalyzer analyzer;
   private final DependencyNode[] parameters;
   private final DependencyNode result;
   private final CallLocation location;
   private IntSet smallKnownTypes;
   private BitSet knownTypes;
   private DependencyGraphBuilder.ExceptionConsumer exceptionConsumer;
   private DependencyTypeFilter filter;
   private boolean isPolymorphic;
   private MethodDependency monomorphicCall;

   VirtualCallConsumer(
      String filterClass,
      MethodDescriptor methodDesc,
      DependencyAnalyzer analyzer,
      DependencyNode[] parameters,
      DependencyNode result,
      CallLocation location,
      DependencyGraphBuilder.ExceptionConsumer exceptionConsumer
   ) {
      this.filter = analyzer.getSuperClassFilter(ValueType.object(filterClass));
      this.methodDesc = methodDesc;
      this.analyzer = analyzer;
      this.parameters = parameters;
      this.result = result;
      this.location = location;
      this.exceptionConsumer = exceptionConsumer;
   }

   private boolean addKnownType(int index) {
      if (this.knownTypes != null) {
         if (this.knownTypes.get(index)) {
            return false;
         }

         this.knownTypes.set(index);
         return true;
      } else {
         if (this.smallKnownTypes == null) {
            this.smallKnownTypes = new IntHashSet();
         }

         if (!this.smallKnownTypes.add(index)) {
            return false;
         }

         if (this.smallKnownTypes.size() > 16) {
            this.knownTypes = new BitSet();

            for (IntCursor cursor : this.smallKnownTypes) {
               this.knownTypes.set(cursor.value);
            }

            this.smallKnownTypes = null;
         }

         return true;
      }
   }

   @Override
   public void consume(DependencyType type) {
      if (this.filter.match(type)) {
         if (this.addKnownType(type.index)) {
            String className;
            if (type.getValueType() instanceof ValueType.Object) {
               className = ((ValueType.Object)type.getValueType()).getClassName();
            } else {
               if (!(type.getValueType() instanceof ValueType.Array)) {
                  return;
               }

               className = "java.lang.Object";
            }

            MethodDependency methodDep = this.analyzer.linkMethod(className, this.methodDesc);
            methodDep.addLocation(this.location);
            if (!methodDep.isMissing()) {
               methodDep.use(false);
               if (this.isPolymorphic) {
                  methodDep.external = true;
               } else if (this.monomorphicCall == null) {
                  this.monomorphicCall = methodDep;
               } else {
                  this.monomorphicCall.external = true;
                  this.monomorphicCall = null;
                  methodDep.external = true;
                  this.isPolymorphic = true;
               }

               DependencyNode[] targetParams = methodDep.getVariables();
               if (this.parameters[0] != null && targetParams[0] != null) {
                  this.parameters[0].connect(targetParams[0], this.analyzer.getSuperClassFilter(ValueType.object(methodDep.getMethod().getOwnerName())));
               }

               for (int i = 1; i < this.parameters.length; i++) {
                  if (this.parameters[i] != null && targetParams[i] != null) {
                     this.parameters[i].connect(targetParams[i]);
                  }
               }

               if (this.result != null && methodDep.getResult() != null) {
                  methodDep.getResult().connect(this.result);
               }

               methodDep.getThrown().addConsumer(this.exceptionConsumer);
            }
         }
      }
   }
}
