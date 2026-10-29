package org.teavm.dependency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.teavm.hppc.ObjectArrayList;
import org.teavm.hppc.cursors.ObjectCursor;

class TypeSet {
   private static final int SMALL_TYPES_THRESHOLD = 3;
   static final DependencyType[] EMPTY_TYPES = new DependencyType[0];
   private DependencyAnalyzer dependencyAnalyzer;
   DependencyNode origin;
   private int[] smallTypes;
   private BitSet types;
   private int typesCount;
   private Object domain;
   ObjectArrayList<Transition> transitions;
   ArrayList<ConsumerWithNode> consumers;

   TypeSet(DependencyAnalyzer dependencyAnalyzer, DependencyNode origin) {
      this.dependencyAnalyzer = dependencyAnalyzer;
      this.origin = origin;
      this.domain = origin;
   }

   void addType(DependencyType type) {
      if (this.types == null && this.smallTypes == null) {
         this.smallTypes = new int[]{type.index};
      } else {
         if (this.smallTypes != null) {
            if (this.smallTypes.length != 3) {
               this.smallTypes = Arrays.copyOf(this.smallTypes, this.smallTypes.length + 1);
               this.smallTypes[this.smallTypes.length - 1] = type.index;
               return;
            }

            this.types = new BitSet(this.dependencyAnalyzer.types.size() * 2);

            for (int existingType : this.smallTypes) {
               this.types.set(existingType);
            }

            this.typesCount = this.smallTypes.length;
            this.smallTypes = null;
         }

         this.types.set(type.index);
         this.typesCount++;
      }
   }

   DependencyType[] getTypes() {
      if (this.types != null) {
         DependencyType[] types = new DependencyType[this.types.cardinality()];
         int j = 0;

         for (int index = this.types.nextSetBit(0); index >= 0; index = this.types.nextSetBit(index + 1)) {
            DependencyType type = this.dependencyAnalyzer.types.get(index);
            types[j++] = type;
         }

         return types;
      } else {
         if (this.smallTypes == null) {
            return EMPTY_TYPES;
         }

         DependencyType[] types = new DependencyType[this.smallTypes.length];

         for (int i = 0; i < types.length; i++) {
            DependencyType type = this.dependencyAnalyzer.types.get(this.smallTypes[i]);
            types[i] = type;
         }

         return types;
      }
   }

   boolean hasMoreTypesThan(int limit, Predicate<DependencyType> filter) {
      if (this.types != null) {
         if (filter == null) {
            return this.types.cardinality() > limit;
         }

         for (int index = this.types.nextSetBit(0); index >= 0; index = this.types.nextSetBit(index + 1)) {
            DependencyType type = this.dependencyAnalyzer.types.get(index);
            if (filter.test(type)) {
               if (--limit < 0) {
                  return true;
               }
            }
         }

         return false;
      } else if (this.smallTypes != null) {
         if (this.smallTypes.length <= limit) {
            return false;
         }

         if (filter == null) {
            return true;
         }

         for (int i = 0; i < this.smallTypes.length; i++) {
            DependencyType type = this.dependencyAnalyzer.types.get(this.smallTypes[i]);
            if (filter.test(type)) {
               if (--limit < 0) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   DependencyType[] getTypesForNode(DependencyNode sourceNode, DependencyNode targetNode, DependencyTypeFilter filter) {
      int j = 0;
      DependencyType[] types;
      if (this.types != null) {
         int[] filteredTypes = null;
         if (this.typesCount > 15) {
            filteredTypes = filter != null ? filter.tryExtract(this.types) : null;
            if (filteredTypes == null) {
               filteredTypes = sourceNode.getFilter().tryExtract(this.types);
            }

            if (filteredTypes == null) {
               filteredTypes = targetNode.getFilter().tryExtract(this.types);
            }
         }

         if (filteredTypes != null) {
            types = new DependencyType[filteredTypes.length];

            for (int index : filteredTypes) {
               DependencyType type = this.dependencyAnalyzer.types.get(index);
               if (sourceNode.filter(type) && !targetNode.hasType(type) && targetNode.filter(type) && (filter == null || filter.match(type))) {
                  types[j++] = type;
               }
            }
         } else {
            types = new DependencyType[this.typesCount];

            for (int index = this.types.nextSetBit(0); index >= 0; index = this.types.nextSetBit(index + 1)) {
               DependencyType type = this.dependencyAnalyzer.types.get(index);
               if (sourceNode.filter(type) && !targetNode.hasType(type) && targetNode.filter(type) && (filter == null || filter.match(type))) {
                  types[j++] = type;
               }
            }
         }
      } else {
         if (this.smallTypes == null) {
            return EMPTY_TYPES;
         }

         types = new DependencyType[this.smallTypes.length];

         for (int i = 0; i < types.length; i++) {
            DependencyType type = this.dependencyAnalyzer.types.get(this.smallTypes[i]);
            if (sourceNode.filter(type) && !targetNode.hasType(type) && targetNode.filter(type) && (filter == null || filter.match(type))) {
               types[j++] = type;
            }
         }
      }

      if (j == 0) {
         return EMPTY_TYPES;
      }

      if (j < types.length) {
         types = Arrays.copyOf(types, j);
      }

      return types;
   }

   boolean hasType(DependencyType type) {
      if (this.smallTypes != null) {
         for (int i = 0; i < this.smallTypes.length; i++) {
            if (this.smallTypes[i] == type.index) {
               return true;
            }
         }

         return false;
      } else {
         return this.types != null && this.types.get(type.index);
      }
   }

   boolean hasAnyType() {
      return this.types != null || this.smallTypes != null;
   }

   TypeSet copy(DependencyNode origin) {
      TypeSet result = new TypeSet(this.dependencyAnalyzer, origin);
      result.types = this.types != null ? (BitSet)this.types.clone() : null;
      result.smallTypes = this.smallTypes;
      result.typesCount = this.typesCount;
      return result;
   }

   void invalidate() {
      this.transitions = null;
      this.consumers = null;
   }

   Set<? extends DependencyNode> domain() {
      if (this.domain == null) {
         return Set.of();
      } else {
         return this.domain instanceof DependencyNode ? Set.of((DependencyNode)this.domain) : (Set)this.domain;
      }
   }

   void addDomain(DependencyNode node) {
      if (this.domain == null) {
         this.domain = node;
      } else if (this.domain instanceof DependencyNode) {
         if (this.domain == node) {
            return;
         }

         this.domain = new HashSet<>(Set.of(this.domain));
      }

      Set<DependencyNode> set = (Set<DependencyNode>)this.domain;
      set.add(node);
   }

   void addDomain(Collection<? extends DependencyNode> nodes) {
      if (!nodes.isEmpty()) {
         if (this.domain == null) {
            if (nodes.size() == 1) {
               this.domain = nodes.iterator().next();
            }
         } else {
            if (this.domain instanceof DependencyNode) {
               if (nodes.contains(this.domain) && nodes.size() == 1) {
                  return;
               }

               this.domain = new HashSet<>(Set.of(this.domain));
            }

            Set<DependencyNode> set = (Set<DependencyNode>)this.domain;
            set.addAll(nodes);
         }
      }
   }

   void removeDomain(Collection<? extends DependencyNode> nodes) {
      if (this.domain != null && !nodes.isEmpty()) {
         if (this.domain instanceof DependencyNode) {
            if (nodes.contains(this.domain)) {
               this.domain = null;
            }
         } else {
            Set<DependencyNode> set = (Set<DependencyNode>)this.domain;
            set.removeAll(nodes);
            if (set.isEmpty()) {
               this.domain = null;
            } else if (set.size() == 1) {
               this.domain = set.iterator().next();
            }
         }
      }
   }

   ObjectArrayList<Transition> getTransitions() {
      if (this.transitions == null) {
         this.transitions = new ObjectArrayList();

         for (DependencyNode node : this.domain()) {
            if (node.transitions != null) {
               for (ObjectCursor<Transition> cursor : node.transitionList) {
                  Transition transition = (Transition)cursor.value;
                  if (transition.filter != null || transition.destination.typeSet != this) {
                     this.transitions.add(transition);
                  }
               }
            }
         }
      }

      return this.transitions;
   }

   List<ConsumerWithNode> getConsumers() {
      if (this.domain == null) {
         return List.of();
      } else if (this.domain instanceof DependencyNode node) {
         return node.followers == null ? List.of() : List.of(new ConsumerWithNode(node.followers.toArray(new DependencyConsumer[0]), node));
      } else {
         if (this.consumers == null) {
            this.consumers = new ArrayList<>();

            for (DependencyNode node : this.domain()) {
               if (node.followers != null) {
                  this.consumers.add(new ConsumerWithNode(node.followers.toArray(new DependencyConsumer[0]), node));
               }
            }

            this.consumers.trimToSize();
         }

         return this.consumers;
      }
   }

   int typeCount() {
      return this.smallTypes != null ? this.smallTypes.length : (this.types != null ? this.typesCount : 0);
   }

   void cleanup() {
      this.origin = null;
      this.domain = null;
      this.transitions = null;
      this.consumers = null;
   }
}
