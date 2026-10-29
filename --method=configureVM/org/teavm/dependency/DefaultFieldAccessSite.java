package org.teavm.dependency;

import java.io.Serializable;
import java.util.Objects;
import org.teavm.callgraph.FieldAccessSite;
import org.teavm.model.FieldReference;
import org.teavm.model.TextLocation;

class DefaultFieldAccessSite implements FieldAccessSite, Serializable {
   private TextLocation location;
   private DefaultCallGraphNode callee;
   private FieldReference field;

   DefaultFieldAccessSite(TextLocation location, DefaultCallGraphNode callee, FieldReference field) {
      this.location = location;
      this.callee = callee;
      this.field = field;
   }

   @Override
   public TextLocation getLocation() {
      return null;
   }

   public DefaultCallGraphNode getCallee() {
      return this.callee;
   }

   @Override
   public FieldReference getField() {
      return null;
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.location, this.field);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return !(obj instanceof DefaultFieldAccessSite other)
            ? false
            : Objects.equals(this.location, other.location) && Objects.equals(this.field, other.field);
      }
   }
}
