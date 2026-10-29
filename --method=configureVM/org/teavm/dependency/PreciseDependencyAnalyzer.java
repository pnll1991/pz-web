package org.teavm.dependency;

import java.util.Properties;
import java.util.function.Supplier;
import org.teavm.common.ServiceRepository;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;
import org.teavm.model.ReferenceCache;
import org.teavm.model.ValueType;
import org.teavm.parsing.resource.ResourceProvider;

public class PreciseDependencyAnalyzer extends DependencyAnalyzer {
   private DependencyNode allArrayItemsNode;
   private static final int DEGREE_THRESHOLD = 2;

   public PreciseDependencyAnalyzer(
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
   }

   @Override
   protected void processMethod(MethodDependency methodDep) {
      DependencyGraphBuilder graphBuilder = new DependencyGraphBuilder(this);
      graphBuilder.buildGraph(methodDep);
   }

   @Override
   DependencyNode createParameterNode(MethodReference method, ValueType type, int index) {
      DependencyNode node = this.createNode(type);
      node.method = method;
      if (shouldTag) {
         node.setTag(method + ":" + index);
      }

      return node;
   }

   @Override
   DependencyNode createResultNode(MethodReference method) {
      DependencyNode node = this.createNode(method.getReturnType());
      node.method = method;
      if (shouldTag) {
         node.setTag(method + ":RESULT");
      }

      return node;
   }

   @Override
   DependencyNode createThrownNode(MethodReference method) {
      DependencyNode node = this.createNode();
      node.method = method;
      if (shouldTag) {
         node.setTag(method + ":THROWN");
      }

      return node;
   }

   @Override
   DependencyNode createFieldNode(FieldReference field, ValueType type) {
      DependencyNode node = this.createNode(type);
      if (shouldTag) {
         node.setTag(field.getClassName() + "#" + field.getFieldName());
      }

      return node;
   }

   @Override
   DependencyNode createArrayItemNode(DependencyNode parent) {
      ValueType itemTypeFilter = parent.typeFilter instanceof ValueType.Array ? ((ValueType.Array)parent.typeFilter).getItemType() : null;
      DependencyNode node;
      if (parent.degree > 2) {
         if (this.allArrayItemsNode == null) {
            this.allArrayItemsNode = this.createNode(null);
            if (DependencyAnalyzer.shouldTag) {
               this.allArrayItemsNode.tag = "<any array item>";
            }
         }

         node = this.allArrayItemsNode;
      } else {
         node = this.createNode(itemTypeFilter);
         node.degree = parent.degree + 1;
         node.method = parent.method;
         if (DependencyAnalyzer.shouldTag) {
            node.tag = parent.tag + "[";
         }
      }

      return node;
   }

   @Override
   DependencyNode createClassValueNode(int degree, DependencyNode parent) {
      DependencyNode node = this.createNode();
      node.degree = degree;
      node.classValueNode = node;
      node.classNodeParent = parent;
      if (DependencyAnalyzer.shouldTag) {
         node.tag = parent.tag + "@";
      }

      return node;
   }

   @Override
   boolean domainOptimizationEnabled() {
      return true;
   }

   @Override
   public boolean isPrecise() {
      return true;
   }
}
