package org.teavm.dependency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.teavm.model.AccessLevel;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.IncomingReader;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.PhiReader;
import org.teavm.model.Program;
import org.teavm.model.TryCatchBlockReader;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.text.ListingBuilder;

class DependencyGraphBuilder {
   private static final MethodDescriptor GET_CLASS = new MethodDescriptor("getClass", Class.class);
   private DependencyAnalyzer dependencyAnalyzer;
   private DependencyNode[] nodes;
   private DependencyNode resultNode;
   private Program program;
   private DefaultCallGraphNode caller;
   private DependencyGraphBuilder.ExceptionConsumer currentExceptionConsumer;
   private AbstractInstructionAnalyzer reader = new AbstractInstructionAnalyzer() {
      @Override
      public void assign(VariableReader receiver, VariableReader assignee) {
         DependencyNode valueNode = DependencyGraphBuilder.this.nodes[assignee.getIndex()];
         DependencyNode receiverNode = DependencyGraphBuilder.this.nodes[receiver.getIndex()];
         if (valueNode != null && receiverNode != null) {
            valueNode.connect(receiverNode);
         }
      }

      @Override
      public void cast(VariableReader receiver, VariableReader value, ValueType targetType, boolean weak) {
         super.cast(receiver, value, targetType, weak);
         if (!weak) {
            DependencyGraphBuilder.this.currentExceptionConsumer
               .consume(DependencyGraphBuilder.this.dependencyAnalyzer.getClassType("java.lang.ClassCastException"));
         }

         DependencyNode valueNode = DependencyGraphBuilder.this.nodes[value.getIndex()];
         DependencyNode receiverNode = DependencyGraphBuilder.this.nodes[receiver.getIndex()];
         ClassReaderSource classSource = DependencyGraphBuilder.this.dependencyAnalyzer.getClassSource();
         if (targetType instanceof ValueType.Object) {
            String targetClsName = ((ValueType.Object)targetType).getClassName();
            ClassReader targetClass = classSource.get(targetClsName);
            if (targetClass != null && !targetClass.getName().equals("java.lang.Object")) {
               if (valueNode != null && receiverNode != null) {
                  valueNode.connect(receiverNode, DependencyGraphBuilder.this.dependencyAnalyzer.getSuperClassFilter(ValueType.object(targetClass.getName())));
               }

               return;
            }
         } else if (targetType instanceof ValueType.Array) {
            ValueType itemType = targetType;

            while (itemType instanceof ValueType.Array) {
               itemType = ((ValueType.Array)itemType).getItemType();
            }

            if (itemType instanceof ValueType.Object) {
               ClassReader targetClass = classSource.get(((ValueType.Object)itemType).getClassName());
               if (targetClass == null) {
                  valueNode.connect(receiverNode);
                  return;
               }
            }

            if (valueNode != null && receiverNode != null) {
               valueNode.connect(receiverNode, DependencyGraphBuilder.this.dependencyAnalyzer.getSuperClassFilter(targetType));
            }

            return;
         }

         if (valueNode != null && receiverNode != null) {
            valueNode.connect(receiverNode);
         }
      }

      @Override
      public void exit(VariableReader valueToReturn) {
         if (valueToReturn != null) {
            DependencyNode node = DependencyGraphBuilder.this.nodes[valueToReturn.getIndex()];
            if (node != null) {
               node.connect(DependencyGraphBuilder.this.resultNode);
            }
         }
      }

      @Override
      public void raise(VariableReader exception) {
         DependencyGraphBuilder.this.nodes[exception.getIndex()].addConsumer(DependencyGraphBuilder.this.currentExceptionConsumer);
      }

      @Override
      public void unwrapArray(VariableReader receiver, VariableReader array, ArrayElementType elementType) {
         DependencyNode arrayNode = DependencyGraphBuilder.this.nodes[array.getIndex()];
         DependencyNode receiverNode = DependencyGraphBuilder.this.nodes[receiver.getIndex()];
         if (arrayNode != null && receiverNode != null) {
            arrayNode.connect(receiverNode);
         }
      }

      @Override
      public void cloneArray(VariableReader receiver, VariableReader array) {
         DependencyNode arrayNode = this.getNode(array);
         DependencyNode receiverNode = this.getNode(receiver);
         if (arrayNode != null && receiverNode != null) {
            arrayNode.addConsumer(receiverNode::propagate);
            arrayNode.getArrayItem().connect(receiverNode.getArrayItem());
         }

         MethodDependency cloneDep = this.getAnalyzer().linkMethod(CLONE_METHOD);
         cloneDep.addLocation(this.getCallLocation());
         if (arrayNode != null) {
            arrayNode.connect(cloneDep.getVariable(0));
         }

         cloneDep.use();
      }

      @Override
      public void getElement(VariableReader receiver, VariableReader array, VariableReader index, ArrayElementType type) {
         if (!this.isPrimitive(type)) {
            DependencyNode arrayNode = DependencyGraphBuilder.this.nodes[array.getIndex()];
            DependencyNode receiverNode = DependencyGraphBuilder.this.nodes[receiver.getIndex()];
            if (arrayNode != null && receiverNode != null && receiverNode != arrayNode.getArrayItem()) {
               arrayNode.getArrayItem().connect(receiverNode);
            }
         }
      }

      @Override
      public void putElement(VariableReader array, VariableReader index, VariableReader value, ArrayElementType type) {
         if (!this.isPrimitive(type)) {
            DependencyNode valueNode = DependencyGraphBuilder.this.nodes[value.getIndex()];
            DependencyNode arrayNode = DependencyGraphBuilder.this.nodes[array.getIndex()];
            if (valueNode != null && arrayNode != null && valueNode != arrayNode.getArrayItem()) {
               valueNode.connect(arrayNode.getArrayItem());
            }
         }
      }

      private boolean isPrimitive(ArrayElementType type) {
         return type != ArrayElementType.OBJECT;
      }

      @Override
      protected void invokeSpecial(VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments) {
         if (!this.handleSpecialMethod(receiver, instance, method)) {
            CallLocation callLocation = this.getCallLocation();
            if (instance == null) {
               DependencyGraphBuilder.this.dependencyAnalyzer.linkClass(method.getClassName()).initClass(callLocation);
            } else {
               DependencyGraphBuilder.this.dependencyAnalyzer.linkClass(method.getClassName());
            }

            MethodDependency methodDep = DependencyGraphBuilder.this.dependencyAnalyzer.linkMethod(method);
            methodDep.addLocation(callLocation);
            methodDep.use(false);
            if (!methodDep.isMissing()) {
               DependencyNode[] targetParams = methodDep.getVariables();

               for (int i = 0; i < arguments.size(); i++) {
                  DependencyNode value = DependencyGraphBuilder.this.nodes[arguments.get(i).getIndex()];
                  DependencyNode param = targetParams[i + 1];
                  if (value != null && param != null) {
                     value.connect(param);
                  }
               }

               if (instance != null) {
                  DependencyGraphBuilder.this.nodes[instance.getIndex()].connect(targetParams[0]);
               }

               if (methodDep.getResult() != null && receiver != null) {
                  DependencyNode receiverNode = DependencyGraphBuilder.this.nodes[receiver.getIndex()];
                  if (methodDep.getResult() != null && receiverNode != null) {
                     methodDep.getResult().connect(receiverNode);
                  }
               }

               methodDep.getThrown().addConsumer(DependencyGraphBuilder.this.currentExceptionConsumer);
               this.initClass(method.getClassName());
            }
         }
      }

      @Override
      protected void invokeVirtual(VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments) {
         ClassReader cls = DependencyGraphBuilder.this.dependencyAnalyzer.getClassSource().get(method.getClassName());
         if (cls != null) {
            MethodReader methodHolder = cls.getMethod(method.getDescriptor());
            if (methodHolder != null && methodHolder.getLevel() == AccessLevel.PRIVATE) {
               this.invokeSpecial(receiver, instance, method, arguments);
               return;
            }
         }

         if (!this.handleSpecialMethod(receiver, instance, method)) {
            DependencyNode[] actualArgs = new DependencyNode[arguments.size() + 1];

            for (int i = 0; i < arguments.size(); i++) {
               actualArgs[i + 1] = DependencyGraphBuilder.this.nodes[arguments.get(i).getIndex()];
            }

            actualArgs[0] = this.getNode(instance);
            DependencyConsumer listener = new VirtualCallConsumer(
               method.getClassName(),
               method.getDescriptor(),
               DependencyGraphBuilder.this.dependencyAnalyzer,
               actualArgs,
               receiver != null ? this.getNode(receiver) : null,
               this.getCallLocation(),
               DependencyGraphBuilder.this.currentExceptionConsumer
            );
            this.getNode(instance).addConsumer(listener);
            DependencyGraphBuilder.this.dependencyAnalyzer
               .getClassSource()
               .overriddenMethods(method)
               .forEach(methodImpl -> DependencyGraphBuilder.this.dependencyAnalyzer.linkMethod(methodImpl.getReference()).addLocation(this.getCallLocation()));
         }
      }

      private boolean handleSpecialMethod(VariableReader receiver, VariableReader instance, MethodReference method) {
         if (method.getDescriptor().equals(DependencyGraphBuilder.GET_CLASS)) {
            this.invokeGetClass(receiver, instance);
            return true;
         }

         if (method.getClassName().equals("java.lang.Class")) {
            switch (method.getName()) {
               case "getComponentType":
                  this.invokeGetComponentType(receiver, instance, method);
                  return true;
               case "getSuperclass":
                  this.invokeGetSuperclass(receiver, instance, method);
                  return true;
               case "getInterfaces":
                  this.invokeGetInterfaces(receiver, instance, method);
                  return true;
            }
         }

         return false;
      }

      private void invokeGetClass(VariableReader receiver, VariableReader instance) {
         MethodDependency getClassDep = DependencyGraphBuilder.this.dependencyAnalyzer.linkMethod("java.lang.Object", DependencyGraphBuilder.GET_CLASS);
         getClassDep.addLocation(this.getCallLocation());
         this.getNode(instance).addConsumer(t -> {
            getClassDep.getVariable(0).propagate(t);
            if (receiver != null) {
               this.getNode(receiver).getClassValueNode().propagate(t);
            }
         });
         if (receiver != null) {
            this.getNode(receiver).propagate(DependencyGraphBuilder.this.dependencyAnalyzer.getClassType("java.lang.Class"));
         }

         getClassDep.use();
      }

      private void invokeGetComponentType(VariableReader receiver, VariableReader instance, MethodReference methodReference) {
         MethodDependency methodDep = DependencyGraphBuilder.this.dependencyAnalyzer.linkMethod(methodReference);
         methodDep.use();
         DependencyNode instanceNode = this.getNode(instance);
         DependencyNode receiverNode = receiver != null ? this.getNode(receiver) : null;
         if (receiverNode != null) {
            receiverNode.propagate(DependencyGraphBuilder.this.dependencyAnalyzer.classType);
         }

         instanceNode.getClassValueNode().addConsumer(t -> {
            if (t.getValueType() instanceof ValueType.Array) {
               ValueType itemType = ((ValueType.Array)t.getValueType()).getItemType();
               if (receiverNode != null) {
                  receiverNode.getClassValueNode().propagate(DependencyGraphBuilder.this.dependencyAnalyzer.getType(itemType));
               }

               methodDep.getVariable(0).propagate(t);
            }
         });
      }

      private void invokeGetSuperclass(VariableReader receiver, VariableReader instance, MethodReference methodReference) {
         MethodDependency methodDep = DependencyGraphBuilder.this.dependencyAnalyzer.linkMethod(methodReference);
         methodDep.use();
         DependencyNode instanceNode = this.getNode(instance);
         DependencyNode receiverNode = receiver != null ? this.getNode(receiver) : null;
         if (receiverNode != null) {
            receiverNode.propagate(DependencyGraphBuilder.this.dependencyAnalyzer.classType);
         }

         instanceNode.getClassValueNode().addConsumer(type -> {
            ValueType valueType = type.getValueType();
            if (valueType instanceof ValueType.Object) {
               String className = ((ValueType.Object)valueType).getClassName();
               ClassReader cls = DependencyGraphBuilder.this.dependencyAnalyzer.getClassSource().get(className);
               if (cls != null && cls.getParent() != null && receiverNode != null) {
                  receiverNode.getClassValueNode().propagate(DependencyGraphBuilder.this.dependencyAnalyzer.getClassType(cls.getParent()));
               }

               methodDep.getVariable(0).getClassValueNode().propagate(type);
               methodDep.getVariable(0).propagate(DependencyGraphBuilder.this.dependencyAnalyzer.getClassType("java.lang.Class"));
            }
         });
      }

      private void invokeGetInterfaces(VariableReader receiver, VariableReader instance, MethodReference methodReference) {
         MethodDependency methodDep = DependencyGraphBuilder.this.dependencyAnalyzer.linkMethod(methodReference);
         methodDep.use();
         DependencyNode instanceNode = this.getNode(instance);
         DependencyNode receiverNode = receiver != null ? this.getNode(receiver) : null;
         receiverNode.propagate(DependencyGraphBuilder.this.dependencyAnalyzer.getType(ValueType.arrayOf(ValueType.object("java.lang.Class"))));
         if (receiverNode != null) {
            receiverNode.getArrayItem().propagate(DependencyGraphBuilder.this.dependencyAnalyzer.classType);
         }

         instanceNode.getClassValueNode().addConsumer(type -> {
            ValueType valueType = type.getValueType();
            if (valueType instanceof ValueType.Object) {
               String className = ((ValueType.Object)valueType).getClassName();
               if (receiverNode != null) {
                  ClassReader cls = DependencyGraphBuilder.this.dependencyAnalyzer.getClassSource().get(className);
                  if (cls != null) {
                     for (String iface : cls.getInterfaces()) {
                        receiverNode.getArrayItem().getClassValueNode().propagate(DependencyGraphBuilder.this.dependencyAnalyzer.getClassType(iface));
                     }
                  }
               }

               methodDep.getVariable(0).propagate(DependencyGraphBuilder.this.dependencyAnalyzer.getClassType("java.lang.Class"));
               methodDep.getVariable(0).getClassValueNode().propagate(type);
            }
         });
      }

      @Override
      public void nullCheck(VariableReader receiver, VariableReader value) {
         super.nullCheck(receiver, value);
         DependencyGraphBuilder.this.currentExceptionConsumer
            .consume(DependencyGraphBuilder.this.dependencyAnalyzer.getClassType("java.lang.NullPointerException"));
      }

      @Override
      protected DependencyNode getNode(VariableReader variable) {
         return DependencyGraphBuilder.this.nodes[variable.getIndex()];
      }

      @Override
      protected DependencyAnalyzer getAnalyzer() {
         return DependencyGraphBuilder.this.dependencyAnalyzer;
      }
   };

   DependencyGraphBuilder(DependencyAnalyzer dependencyAnalyzer) {
      this.dependencyAnalyzer = dependencyAnalyzer;
   }

   public void buildGraph(MethodDependency dep) {
      this.caller = this.dependencyAnalyzer.callGraph.getNode(dep.getReference());
      MethodHolder method = dep.method;
      if (method.getProgram() != null && method.getProgram().basicBlockCount() != 0) {
         this.program = method.getProgram();
         this.resultNode = dep.getResult();
         DataFlowGraphBuilder dfgBuilder = new DataFlowGraphBuilder();
         boolean[] significantParams = new boolean[dep.getParameterCount()];
         significantParams[0] = true;

         for (int i = 1; i < dep.getParameterCount(); i++) {
            ValueType arg = method.parameterType(i - 1);
            if (!(arg instanceof ValueType.Primitive)) {
               significantParams[i] = true;
            }
         }

         int[] nodeMapping = dfgBuilder.buildMapping(
            this.program, significantParams, !(method.getResultType() instanceof ValueType.Primitive) && method.getResultType() != ValueType.VOID
         );
         if (DependencyAnalyzer.shouldLog) {
            System.out.println("Method reached: " + method.getReference());
            System.out.print(new ListingBuilder().buildListing(this.program, "    "));

            for (int i = 0; i < nodeMapping.length; i++) {
               System.out.print(i + ":" + nodeMapping[i] + " ");
            }

            System.out.println();
            System.out.println();
         }

         int nodeClassCount = 0;

         for (int i = 0; i < nodeMapping.length; i++) {
            nodeClassCount = Math.max(nodeClassCount, nodeMapping[i] + 1);
         }

         DependencyNode[] nodeClasses = Arrays.copyOf(dep.getVariables(), nodeClassCount);
         MethodReference ref = method.getReference();

         for (int i = dep.getVariableCount(); i < nodeClasses.length; i++) {
            nodeClasses[i] = this.dependencyAnalyzer.createNode();
            nodeClasses[i].method = ref;
            if (DependencyAnalyzer.shouldTag) {
               nodeClasses[i].setTag(dep.getMethod().getReference() + ":" + i);
            }
         }

         this.nodes = new DependencyNode[dep.getMethod().getProgram().variableCount()];

         for (int i = 0; i < this.nodes.length; i++) {
            int mappedNode = nodeMapping[i];
            this.nodes[i] = mappedNode >= 0 ? nodeClasses[mappedNode] : null;
         }

         dep.setVariables(this.nodes);
         this.reader.setCaller(this.caller.getMethod());

         for (int i = 0; i < this.program.basicBlockCount(); i++) {
            BasicBlockReader block = this.program.basicBlockAt(i);
            this.currentExceptionConsumer = this.createExceptionConsumer(dep, block);
            block.readAllInstructions(this.reader);

            for (PhiReader phi : block.readPhis()) {
               DependencyNode receiverNode = this.nodes[phi.getReceiver().getIndex()];

               for (IncomingReader incoming : phi.readIncomings()) {
                  DependencyNode incomingNode = this.nodes[incoming.getValue().getIndex()];
                  if (incomingNode != null && receiverNode != null) {
                     incomingNode.connect(receiverNode);
                  }
               }
            }

            for (TryCatchBlockReader tryCatch : block.readTryCatchBlocks()) {
               if (tryCatch.getExceptionType() != null) {
                  this.dependencyAnalyzer.linkClass(tryCatch.getExceptionType());
               }
            }
         }

         if (method.hasModifier(ElementModifier.SYNCHRONIZED)) {
            List<DependencyNode> syncNodes = new ArrayList<>();
            if (this.dependencyAnalyzer.asyncSupported) {
               MethodDependency methodDep = this.dependencyAnalyzer.linkMethod(AbstractInstructionAnalyzer.MONITOR_ENTER_METHOD);
               syncNodes.add(methodDep.getVariable(1));
               methodDep.use();
            }

            MethodDependency methodDep = this.dependencyAnalyzer.linkMethod(AbstractInstructionAnalyzer.MONITOR_ENTER_SYNC_METHOD);
            syncNodes.add(methodDep.getVariable(1));
            methodDep.use();
            if (this.dependencyAnalyzer.asyncSupported) {
               methodDep = this.dependencyAnalyzer.linkMethod(AbstractInstructionAnalyzer.MONITOR_EXIT_METHOD);
               syncNodes.add(methodDep.getVariable(1));
               methodDep.use();
            }

            methodDep = this.dependencyAnalyzer.linkMethod(AbstractInstructionAnalyzer.MONITOR_EXIT_SYNC_METHOD);
            syncNodes.add(methodDep.getVariable(1));
            methodDep.use();
            if (method.hasModifier(ElementModifier.STATIC)) {
               for (DependencyNode node : syncNodes) {
                  node.propagate(this.dependencyAnalyzer.getClassType("java.lang.Class"));
               }
            } else {
               for (DependencyNode node : syncNodes) {
                  this.nodes[0].connect(node);
               }
            }
         }
      }
   }

   private DependencyGraphBuilder.ExceptionConsumer createExceptionConsumer(MethodDependency methodDep, BasicBlockReader block) {
      List<? extends TryCatchBlockReader> tryCatchBlocks = block.readTryCatchBlocks();
      ClassReader[] exceptions = new ClassReader[tryCatchBlocks.size()];
      DependencyNode[] vars = new DependencyNode[tryCatchBlocks.size()];

      for (int i = 0; i < tryCatchBlocks.size(); i++) {
         TryCatchBlockReader tryCatch = tryCatchBlocks.get(i);
         if (tryCatch.getExceptionType() != null) {
            exceptions[i] = this.dependencyAnalyzer.getClassSource().get(tryCatch.getExceptionType());
         }

         if (tryCatch.getHandler().getExceptionVariable() != null) {
            vars[i] = methodDep.getVariable(tryCatch.getHandler().getExceptionVariable().getIndex());
         }
      }

      return new DependencyGraphBuilder.ExceptionConsumer(this.dependencyAnalyzer, exceptions, vars, methodDep);
   }

   static class ExceptionConsumer implements DependencyConsumer {
      private DependencyAnalyzer analyzer;
      private ClassReader[] exceptions;
      private DependencyNode[] vars;
      private MethodDependency method;

      ExceptionConsumer(DependencyAnalyzer analyzer, ClassReader[] exceptions, DependencyNode[] vars, MethodDependency method) {
         this.analyzer = analyzer;
         this.exceptions = exceptions;
         this.vars = vars;
         this.method = method;
      }

      @Override
      public void consume(DependencyType type) {
         ClassHierarchy hierarchy = this.analyzer.getClassHierarchy();

         for (int i = 0; i < this.exceptions.length; i++) {
            if (this.exceptions[i] == null || hierarchy.isSuperType(ValueType.object(this.exceptions[i].getName()), type.getValueType(), false)) {
               if (this.vars[i] != null) {
                  this.vars[i].propagate(type);
               }

               return;
            }
         }

         this.method.getThrown().propagate(type);
      }
   }
}
