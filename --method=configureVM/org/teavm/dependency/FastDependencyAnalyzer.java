package org.teavm.dependency;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import org.teavm.common.ServiceRepository;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ProgramReader;
import org.teavm.model.ReferenceCache;
import org.teavm.model.TryCatchBlockReader;
import org.teavm.model.ValueType;
import org.teavm.parsing.resource.ResourceProvider;

public class FastDependencyAnalyzer extends DependencyAnalyzer {
   DependencyNode instancesNode;
   DependencyNode classesNode;
   private Map<MethodReference, FastVirtualCallConsumer> virtualCallConsumers = new HashMap<>();
   private Map<ValueType, DependencyNode> subtypeNodes = new HashMap<>();

   public FastDependencyAnalyzer(
      ClassReaderSource classSource,
      ResourceProvider resourceProvider,
      ClassLoader classLoader,
      ServiceRepository services,
      Diagnostics diagnostics,
      ReferenceCache referenceCache,
      String[] platformTags,
      Supplier<Properties> properties
   ) {
      super(classSource, resourceProvider, classLoader, services, diagnostics, referenceCache, platformTags, properties);
      this.instancesNode = new DependencyNode(this, null);
      this.classesNode = new DependencyNode(this, null);
      this.instancesNode.addConsumer(type -> this.getSubtypeNode(type.getValueType()).propagate(type));
   }

   @Override
   protected void processMethod(MethodDependency methodDep) {
      MethodReader method = methodDep.getMethod();
      ProgramReader program = method.getProgram();
      if (program != null) {
         FastInstructionAnalyzer instructionAnalyzer = new FastInstructionAnalyzer(this);
         instructionAnalyzer.setCaller(method.getReference());

         for (BasicBlockReader block : program.getBasicBlocks()) {
            block.readAllInstructions(instructionAnalyzer);

            for (TryCatchBlockReader tryCatch : block.readTryCatchBlocks()) {
               if (tryCatch.getExceptionType() != null) {
                  this.linkClass(tryCatch.getExceptionType());
               }
            }
         }

         methodDep.variableNodes = new DependencyNode[program.variableCount()];

         for (int i = 0; i < methodDep.variableNodes.length; i++) {
            methodDep.variableNodes[i] = this.instancesNode;
         }
      }

      if (method.hasModifier(ElementModifier.SYNCHRONIZED)) {
         this.processAsyncMethod();
      }
   }

   private void processAsyncMethod() {
      if (this.asyncSupported) {
         this.linkMethod(AbstractInstructionAnalyzer.MONITOR_ENTER_METHOD).use();
      }

      this.linkMethod(AbstractInstructionAnalyzer.MONITOR_ENTER_SYNC_METHOD).use();
      if (this.asyncSupported) {
         this.linkMethod(AbstractInstructionAnalyzer.MONITOR_EXIT_METHOD).use();
      }

      this.linkMethod(AbstractInstructionAnalyzer.MONITOR_EXIT_SYNC_METHOD).use();
   }

   @Override
   DependencyNode createParameterNode(MethodReference method, ValueType type, int index) {
      return this.instancesNode;
   }

   @Override
   DependencyNode createResultNode(MethodReference method) {
      return this.instancesNode;
   }

   @Override
   DependencyNode createThrownNode(MethodReference method) {
      return this.instancesNode;
   }

   @Override
   DependencyNode createFieldNode(FieldReference field, ValueType type) {
      return this.instancesNode;
   }

   @Override
   DependencyNode createArrayItemNode(DependencyNode parent) {
      return this.instancesNode;
   }

   @Override
   DependencyNode createClassValueNode(int degree, DependencyNode parent) {
      return this.classesNode;
   }

   private DependencyNode getSubtypeNode(ValueType type) {
      return type.isObject("java.lang.Object") ? this.instancesNode : this.subtypeNodes.computeIfAbsent(type, key -> {
         DependencyNode node = this.createNode();
         this.defer(() -> {
            ValueType k = key;
            int degree = 0;

            while (k instanceof ValueType.Array) {
               degree++;
               k = ((ValueType.Array)k).getItemType();
            }

            if (k instanceof ValueType.Object) {
               ClassReader cls = this.getClassSource().get(((ValueType.Object)k).getClassName());
               if (cls != null) {
                  if (cls.getParent() != null) {
                     ValueType parentType = ValueType.object(cls.getParent());

                     for (int i = 0; i < degree; i++) {
                        parentType = ValueType.arrayOf(parentType);
                     }

                     node.connect(this.getSubtypeNode(parentType));
                  }

                  for (String itf : cls.getInterfaces()) {
                     ValueType parentType = ValueType.object(itf);

                     for (int i = 0; i < degree; i++) {
                        parentType = ValueType.arrayOf(parentType);
                     }

                     node.connect(this.getSubtypeNode(parentType));
                  }
               }
            } else {
               node.connect(this.getSubtypeNode(ValueType.object("java.lang.Object")));
            }
         });
         return node;
      });
   }

   FastVirtualCallConsumer getVirtualCallConsumer(MethodReference method) {
      return this.virtualCallConsumers.computeIfAbsent(method, key -> {
         FastVirtualCallConsumer consumer = new FastVirtualCallConsumer(this.instancesNode, key, this);
         this.defer(() -> this.getSubtypeNode(ValueType.object(method.getClassName())).addConsumer(consumer));
         return consumer;
      });
   }

   @Override
   boolean domainOptimizationEnabled() {
      return false;
   }

   @Override
   public void cleanup(ClassSourcePacker classSourcePacker) {
      this.virtualCallConsumers.clear();
      this.subtypeNodes.clear();
      super.cleanup(classSourcePacker);
   }

   @Override
   public boolean isPrecise() {
      return false;
   }
}
