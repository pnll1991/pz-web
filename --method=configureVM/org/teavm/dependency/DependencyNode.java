package org.teavm.dependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.teavm.hppc.ObjectArrayList;
import org.teavm.hppc.ObjectObjectHashMap;
import org.teavm.hppc.cursors.ObjectCursor;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class DependencyNode implements ValueDependencyInfo {
   DependencyAnalyzer dependencyAnalyzer;
   List<DependencyConsumer> followers;
   TypeSet typeSet;
   ObjectObjectHashMap<DependencyNode, Transition> transitions;
   ObjectArrayList<Transition> transitionList;
   String tag;
   private DependencyNode arrayItemNode;
   DependencyNode classValueNode;
   DependencyNode classNodeParent;
   private boolean classNodeComplete;
   int degree;
   boolean locked;
   MethodReference method;
   ValueType typeFilter;
   private DependencyTypeFilter cachedTypeFilter;
   int splitCount;
   public int propagateCount;

   DependencyNode(DependencyAnalyzer dependencyAnalyzer, ValueType typeFilter) {
      this.dependencyAnalyzer = dependencyAnalyzer;
      this.typeFilter = typeFilter;
   }

   public void propagate(DependencyType type) {
      if (!this.hasType(type) && this.filter(type) && this.dependencyAnalyzer.filterType(type.getValueType())) {
         this.propagateCount++;
         this.moveToSeparateDomain();
         this.typeSet.addType(type);
         this.scheduleSingleType(type);
      }
   }

   private void scheduleSingleType(DependencyType type) {
      if (DependencyAnalyzer.shouldLog) {
         for (DependencyNode node : this.typeSet.domain()) {
            if (node.filter(type)) {
               System.out.println(node.tag + " -> " + type.getValueType());
            }
         }
      }

      Transition[] transitions = (Transition[])this.typeSet.getTransitions().toArray(Transition.class);
      List<ConsumerWithNode> consumerEntries = this.typeSet.getConsumers();

      for (Transition transition : transitions) {
         if (transition.source.filter(type) && transition.filterType(type)) {
            this.dependencyAnalyzer.schedulePropagation(transition, type);
         }
      }

      for (ConsumerWithNode entry : consumerEntries) {
         if (entry.node.filter(type)) {
            for (DependencyConsumer consumer : entry.consumers) {
               this.dependencyAnalyzer.schedulePropagation(consumer, type);
            }
         }
      }
   }

   public void propagate(DependencyType[] newTypes) {
      if (newTypes.length != 0) {
         if (newTypes.length == 1) {
            this.propagate(newTypes[0]);
         } else {
            int j = 0;
            boolean copied = false;

            for (int i = 0; i < newTypes.length; i++) {
               DependencyType type = newTypes[i];
               if (!this.hasType(type) && this.filter(type) && this.dependencyAnalyzer.filterType(type.getValueType())) {
                  newTypes[j++] = type;
               } else if (!copied) {
                  copied = true;
                  newTypes = (DependencyType[])newTypes.clone();
               }
            }

            if (j != 0) {
               if (j == 1) {
                  this.propagate(newTypes[0]);
               } else {
                  this.propagateCount++;
                  if (j < newTypes.length) {
                     newTypes = Arrays.copyOf(newTypes, j);
                  }

                  this.moveToSeparateDomain();

                  for (DependencyType newType : newTypes) {
                     this.typeSet.addType(newType);
                  }

                  this.scheduleMultipleTypes(newTypes, null);
               }
            }
         }
      }
   }

   void scheduleMultipleTypes(DependencyType[] newTypes, Runnable action) {
      if (DependencyAnalyzer.shouldLog) {
         for (DependencyNode node : this.typeSet.domain()) {
            for (DependencyType type : newTypes) {
               if (node.filter(type)) {
                  System.out.println(node.tag + " -> " + type.getValueType());
               }
            }
         }
      }

      ObjectArrayList<Transition> transitions = new ObjectArrayList(this.typeSet.getTransitions());
      List<ConsumerWithNode> consumerEntries = this.typeSet.getConsumers();
      if (action != null) {
         action.run();
      }

      for (ObjectCursor<Transition> cursor : transitions) {
         Transition transition = (Transition)cursor.value;
         DependencyType[] typesToPropagate = newTypes;
         if (transition.source.typeFilter != null || transition.filter != null) {
            int j = 0;

            for (int i = 0; i < typesToPropagate.length; i++) {
               DependencyType type = typesToPropagate[i];
               if (transition.source.filter(type) && transition.filterType(type)) {
                  typesToPropagate[j++] = type;
               } else if (typesToPropagate == newTypes) {
                  typesToPropagate = (DependencyType[])typesToPropagate.clone();
               }
            }

            if (j < typesToPropagate.length) {
               if (j == 0) {
                  continue;
               }

               if (j == 1) {
                  this.dependencyAnalyzer.schedulePropagation(transition, typesToPropagate[0]);
                  continue;
               }

               typesToPropagate = Arrays.copyOf(typesToPropagate, j);
            }
         }

         this.dependencyAnalyzer.schedulePropagation(transition, typesToPropagate);
      }

      Iterator var16 = consumerEntries.iterator();

      while (true) {
         ConsumerWithNode entry;
         DependencyType[] filteredTypes;
         while (true) {
            if (!var16.hasNext()) {
               return;
            }

            entry = (ConsumerWithNode)var16.next();
            filteredTypes = newTypes;
            DependencyNode node = entry.node;
            if (node.typeFilter == null) {
               break;
            }

            int j = 0;

            for (int i = 0; i < filteredTypes.length; i++) {
               DependencyType type = filteredTypes[i];
               if (node.filter(type)) {
                  filteredTypes[j++] = type;
               } else if (filteredTypes == newTypes) {
                  filteredTypes = (DependencyType[])filteredTypes.clone();
               }
            }

            if (j != 0) {
               if (j < filteredTypes.length) {
                  filteredTypes = Arrays.copyOf(filteredTypes, j);
               }
               break;
            }
         }

         for (DependencyConsumer consumer : entry.consumers) {
            this.dependencyAnalyzer.schedulePropagation(consumer, filteredTypes);
         }
      }
   }

   boolean filter(DependencyType type) {
      return this.typeFilter == null ? true : this.getFilter().match(type);
   }

   DependencyTypeFilter getFilter() {
      if (this.cachedTypeFilter == null) {
         if (this.typeFilter == null) {
            this.cachedTypeFilter = t -> true;
         } else {
            this.cachedTypeFilter = this.dependencyAnalyzer.getSuperClassFilter(this.typeFilter);
         }
      }

      return this.cachedTypeFilter;
   }

   public void addConsumer(DependencyConsumer consumer) {
      if (this.followers == null) {
         this.followers = new ArrayList<>(1);
      }

      if (!this.followers.contains(consumer)) {
         this.followers.add(consumer);
         if (this.typeSet != null) {
            this.typeSet.consumers = null;
         }

         this.propagateTypes(consumer);
      }
   }

   public void connect(DependencyNode node, DependencyTypeFilter filter) {
      if (this.connectWithoutChildNodes(node, filter)) {
         this.connectArrayItemNodes(node);
         if (this.classNodeParent == null
            && this.classValueNode != null
            && this.classValueNode != this
            && this.filter(this.dependencyAnalyzer.classType)
            && node.filter(this.dependencyAnalyzer.classType)
            && (filter == null || filter.match(this.dependencyAnalyzer.classType))) {
            this.classValueNode.connect(node.getClassValueNode());
         }
      }
   }

   private boolean connectWithoutChildNodes(DependencyNode node, DependencyTypeFilter filter) {
      if (this == node) {
         return false;
      }

      if (node == null) {
         throw new IllegalArgumentException("Node must not be null");
      }

      if (this.transitions == null) {
         this.transitions = new ObjectObjectHashMap();
         this.transitionList = new ObjectArrayList();
      }

      if (this.transitions.containsKey(node)) {
         return false;
      }

      Transition transition = new Transition(this, node, filter);
      this.transitions.put(node, transition);
      this.transitionList.add(transition);
      if (DependencyAnalyzer.shouldLog) {
         System.out.println("Connecting " + this.tag + " to " + node.tag);
      }

      if (this.typeSet != null) {
         if (this.typeSet == node.typeSet) {
            return false;
         }

         if (this.typeSet.transitions != null) {
            this.typeSet.transitions.add(transition);
         }

         DependencyType[] types = node.typeSet == null && filter == null && node.typeFilter == null
            ? this.getTypesInternal()
            : this.getTypesInternal(filter, this, node);
         if (types.length > 0) {
            if (node.typeSet == null) {
               node.propagate(types);
            } else {
               this.dependencyAnalyzer.schedulePropagation(transition, types);
            }
         }
      }

      return true;
   }

   private void connectArrayItemNodes(DependencyNode node) {
      if (isArray(this.typeFilter) && isArray(node.typeFilter)) {
         if (Objects.equals(this.typeFilter, node.typeFilter)) {
            if (this.arrayItemNode != null && node.arrayItemNode == null) {
               node.arrayItemNode = this.arrayItemNode;
               return;
            }

            if (node.arrayItemNode != null && this.arrayItemNode == null) {
               this.arrayItemNode = node.arrayItemNode;
               return;
            }

            if (node.arrayItemNode == null && this.arrayItemNode == null) {
               node.arrayItemNode = this.getArrayItem();
               return;
            }
         }

         this.getArrayItem().connect(node.getArrayItem());
         node.getArrayItem().connect(this.getArrayItem());
      }
   }

   private static boolean isArray(ValueType type) {
      return type != null && !type.isObject("java.lang.Object") ? type instanceof ValueType.Array : true;
   }

   private void connectClassValueNodes() {
      if (!this.classNodeComplete) {
         this.classNodeComplete = true;
         if (this.classNodeParent != null && this.classNodeParent.transitions != null) {
            if (this.classNodeParent.filter(this.dependencyAnalyzer.classType)) {
               for (Transition transition : (Transition[])this.classNodeParent.transitionList.toArray(Transition.class)) {
                  if (transition.destination.classNodeParent == null
                     && transition.destination.filter(this.dependencyAnalyzer.classType)
                     && (transition.filter == null || transition.filter.match(this.dependencyAnalyzer.classType))) {
                     this.connect(transition.destination.getClassValueNode());
                  }
               }
            }
         }
      }
   }

   private void propagateTypes(DependencyConsumer transition) {
      if (this.typeSet != null) {
         this.dependencyAnalyzer.schedulePropagation(transition, this.getTypesInternal());
      }
   }

   public void connect(DependencyNode node) {
      this.connect(node, null);
   }

   public DependencyNode getArrayItem() {
      if (this.arrayItemNode == null) {
         this.arrayItemNode = this.dependencyAnalyzer.createArrayItemNode(this);
      }

      return this.arrayItemNode;
   }

   public DependencyNode getClassValueNode() {
      if (this.classValueNode == null) {
         this.classValueNode = this.dependencyAnalyzer.createClassValueNode(this.degree, this);
         this.classValueNode.connectClassValueNodes();
      }

      return this.classValueNode;
   }

   @Override
   public boolean hasArrayType() {
      return this.arrayItemNode != null && this.arrayItemNode.typeSet != null && this.arrayItemNode.typeSet.hasAnyType();
   }

   public boolean hasType(DependencyType type) {
      return this.typeSet != null && this.typeSet.hasType(type);
   }

   @Override
   public boolean hasType(ValueType type) {
      return this.hasType(this.dependencyAnalyzer.getType(type));
   }

   @Override
   public ValueType[] getTypes() {
      if (this.typeSet == null) {
         return new ValueType[0];
      }

      DependencyType[] types = this.typeSet.getTypes();
      ValueType[] result = new ValueType[types.length];
      int i = 0;

      for (DependencyType type : types) {
         if (this.filter(type)) {
            result[i++] = type.getValueType();
         }
      }

      return i == result.length ? result : Arrays.copyOf(result, i);
   }

   @Override
   public boolean hasMoreTypesThan(int limit) {
      return this.typeSet == null ? false : this.typeSet.hasMoreTypesThan(limit, this.typeFilter != null ? this.getFilter()::match : null);
   }

   DependencyType[] getTypesInternal() {
      if (this.typeSet == null) {
         return new DependencyType[0];
      }

      DependencyType[] types = this.typeSet.getTypes();
      if (this.typeFilter == null) {
         return types;
      }

      DependencyType[] result = new DependencyType[types.length];
      int i = 0;

      for (DependencyType type : types) {
         if (this.filter(type)) {
            result[i++] = type;
         }
      }

      return i == result.length ? result : Arrays.copyOf(result, i);
   }

   private DependencyType[] getTypesInternal(DependencyTypeFilter filter, DependencyNode sourceNode, DependencyNode targetNode) {
      return this.typeSet == null ? TypeSet.EMPTY_TYPES : this.typeSet.getTypesForNode(sourceNode, targetNode, filter);
   }

   public String getTag() {
      return this.tag;
   }

   public void setTag(String tag) {
      this.tag = tag;
   }

   void moveToSeparateDomain() {
      if (this.typeSet == null) {
         Collection<DependencyNode> domain = this.findDomain();
         this.typeSet = new TypeSet(this.dependencyAnalyzer, this);
         this.typeSet.addDomain(domain);

         for (DependencyNode node : domain) {
            node.typeSet = this.typeSet;
         }
      } else if (this.typeSet.origin != this) {
         Collection<DependencyNode> domain = this.findDomain();
         if (!domain.contains(this.typeSet.origin)) {
            this.typeSet.removeDomain(domain);
            this.typeSet.invalidate();
            this.typeSet = this.typeSet.copy(this);
            this.typeSet.addDomain(domain);

            for (DependencyNode node : domain) {
               node.typeSet = this.typeSet;
               node.splitCount++;
            }
         }
      }
   }

   private Collection<DependencyNode> findDomain() {
      if (!this.dependencyAnalyzer.domainOptimizationEnabled()) {
         return Collections.singleton(this);
      }

      Set<DependencyNode> visited = new LinkedHashSet<>(50);
      Deque<DependencyNode> stack = new ArrayDeque<>(50);
      stack.push(this);

      while (!stack.isEmpty()) {
         DependencyNode node = stack.pop();
         if (visited.add(node)) {
            if (visited.size() > 100) {
               break;
            }

            if (node.transitions != null) {
               for (ObjectCursor<Transition> cursor : node.transitionList) {
                  Transition transition = (Transition)cursor.value;
                  if (transition.filter == null
                     && transition.destination.typeSet == this.typeSet
                     && !visited.contains(transition.destination)
                     && transition.isDestSubsetOfSrc()) {
                     stack.push(transition.destination);
                  }
               }
            }
         }
      }

      return visited;
   }
}
