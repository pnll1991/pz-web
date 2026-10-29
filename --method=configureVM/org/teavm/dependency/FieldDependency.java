package org.teavm.dependency;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.teavm.model.CallLocation;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;

public class FieldDependency implements FieldDependencyInfo {
   DependencyNode value;
   private FieldReader field;
   private boolean present;
   private FieldReference reference;
   List<LocationListener> locationListeners;
   Set<CallLocation> locations;
   boolean activated;

   FieldDependency(DependencyNode value, FieldReader field, FieldReference reference) {
      this.value = value;
      this.field = field;
      this.reference = reference;
   }

   public DependencyNode getValue() {
      return this.value;
   }

   public FieldReader getField() {
      return this.field;
   }

   @Override
   public FieldReference getReference() {
      return this.reference;
   }

   @Override
   public boolean isMissing() {
      return this.field == null && !this.present;
   }

   public FieldDependency addLocation(CallLocation location) {
      DefaultCallGraphNode node = this.value.dependencyAnalyzer.callGraph.getNode(location.getMethod());
      if (this.locations == null) {
         this.locations = new LinkedHashSet<>();
      }

      if (this.locations.add(location)) {
         node.addFieldAccess(this.reference, location.getSourceLocation());
         if (this.locationListeners != null) {
            for (LocationListener listener : this.locationListeners.toArray(new LocationListener[0])) {
               listener.locationAdded(location);
            }
         }
      }

      return this;
   }

   public void addLocationListener(LocationListener listener) {
      if (this.locationListeners == null) {
         this.locationListeners = new ArrayList<>();
         this.locationListeners.add(listener);
         if (this.locations != null) {
            for (CallLocation location : this.locations.toArray(new CallLocation[0])) {
               listener.locationAdded(location);
            }
         }
      }
   }

   void cleanup() {
      if (this.field != null) {
         this.field = null;
         this.present = true;
      }
   }
}
