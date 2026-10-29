package org.teavm.ast;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodReference;

public abstract class MethodNode {
   private MethodReference reference;
   private Set<ElementModifier> modifiers = EnumSet.noneOf(ElementModifier.class);

   public MethodNode(MethodReference reference) {
      this.reference = reference;
      this.modifiers = EnumSet.copyOf(this.modifiers);
   }

   public MethodReference getReference() {
      return this.reference;
   }

   public Set<ElementModifier> getModifiers() {
      return this.modifiers;
   }

   public abstract void acceptVisitor(MethodNodeVisitor var1);

   public abstract boolean isAsync();

   public abstract List<VariableNode> getVariables();
}
