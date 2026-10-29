package org.teavm.dependency;

import java.util.Arrays;
import org.teavm.hppc.IntHashSet;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ValueType;

class Transition {
   DependencyNode source;
   DependencyNode destination;
   DependencyTypeFilter filter;
   IntHashSet pendingTypes;
   private byte destSubsetOfSrc;

   Transition(DependencyNode source, DependencyNode destination, DependencyTypeFilter filter) {
      this.source = source;
      this.destination = destination;
      this.filter = filter;
   }

   void consume(DependencyType type) {
      if (!this.destination.hasType(type) && this.filterType(type) && this.destination.filter(type)) {
         this.propagate(type);
      }
   }

   private void propagate(DependencyType type) {
      if (this.destination.typeSet != this.source.typeSet) {
         if (this.shouldMergeDomains()) {
            this.mergeDomains(new DependencyType[]{type});
         } else {
            this.destination.propagate(type);
         }
      }
   }

   private void propagate(DependencyType[] types) {
      if (this.destination.typeSet != this.source.typeSet) {
         if (this.shouldMergeDomains()) {
            this.mergeDomains(types);
         } else {
            this.destination.propagate(types);
         }
      }
   }

   private void mergeDomains(DependencyType[] types) {
      this.destination.dependencyAnalyzer.merging = true;
      this.destination.moveToSeparateDomain();
      this.destination.scheduleMultipleTypes(types, () -> {
         for (DependencyNode node : this.destination.typeSet.domain()) {
            node.typeSet = this.source.typeSet;
            this.source.typeSet.addDomain(node);
         }

         this.source.typeSet.invalidate();
      });
      this.destination.dependencyAnalyzer.merging = false;
   }

   private boolean shouldMergeDomains() {
      if (!this.source.dependencyAnalyzer.domainOptimizationEnabled() || this.filter != null || !this.isDestSubsetOfSrc()) {
         return false;
      }

      if (this.destination.dependencyAnalyzer.merging) {
         return false;
      }

      if (this.destination.typeSet == null) {
         return true;
      }

      if (this.destination.typeSet != this.source.typeSet
         && this.destination.typeSet.origin != this.source
         && this.destination.typeSet.typeCount() <= this.source.typeSet.typeCount()) {
         if (this.destination.splitCount > 4) {
            return false;
         }

         if (this.destination.typeSet.typeCount() == this.source.typeSet.typeCount() && this.destination.typeSet.origin != this.destination) {
            return false;
         }

         for (DependencyType type : this.destination.getTypesInternal()) {
            if (!this.source.hasType(type)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   void consume(DependencyType[] types) {
      int j = 0;
      boolean copied = false;
      if (this.filter == null) {
         for (DependencyType type : types) {
            boolean added = false;
            if (!this.destination.hasType(type) && this.destination.filter(type)) {
               types[j++] = type;
               added = true;
            }

            if (!added && !copied) {
               copied = true;
               types = (DependencyType[])types.clone();
            }
         }
      } else {
         for (DependencyType type : types) {
            boolean added = false;
            if (this.filterType(type) && !this.destination.hasType(type) && this.destination.filter(type)) {
               types[j++] = type;
               added = true;
            }

            if (!added && !copied) {
               copied = true;
               types = (DependencyType[])types.clone();
            }
         }
      }

      if (j != 0) {
         if (j == 1) {
            this.propagate(types[0]);
         } else {
            if (j < types.length) {
               types = Arrays.copyOf(types, j);
            }

            this.propagate(types);
         }
      }
   }

   boolean filterType(DependencyType type) {
      if (this.pendingTypes != null && this.pendingTypes.contains(type.index)) {
         return false;
      } else {
         return this.filter == null ? true : this.filter.match(type);
      }
   }

   boolean pointsToDomainOrigin() {
      return this.destination.typeSet == null || this.destination.typeSet.origin == this.destination;
   }

   boolean isDestSubsetOfSrc() {
      if (this.destSubsetOfSrc == 0) {
         this.destSubsetOfSrc = (byte)(this.calculateDestSubsetOfSrc() ? 2 : 1);
      }

      return this.destSubsetOfSrc == 2;
   }

   private boolean calculateDestSubsetOfSrc() {
      if (this.source.typeFilter == null) {
         return true;
      }

      if (this.destination.typeFilter == null) {
         return false;
      }

      ValueType sourceType = this.source.typeFilter;
      ValueType destType = this.destination.typeFilter;
      ClassHierarchy hierarchy = this.source.dependencyAnalyzer.getClassHierarchy();
      return hierarchy.isSuperType(sourceType, destType, false);
   }
}
