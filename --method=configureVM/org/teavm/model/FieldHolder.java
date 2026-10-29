package org.teavm.model;

public class FieldHolder extends MemberHolder implements FieldReader {
   private ValueType type;
   private GenericValueType genericType;
   private Object initialValue;
   private ClassHolder owner;
   private FieldReference reference;

   public FieldHolder(String name) {
      super(name);
   }

   @Override
   public ValueType getType() {
      return this.type;
   }

   @Override
   public GenericValueType getGenericType() {
      return this.genericType;
   }

   public void setGenericType(GenericValueType genericType) {
      this.genericType = genericType;
   }

   public void setType(ValueType type) {
      this.type = type;
   }

   @Override
   public Object getInitialValue() {
      return this.initialValue;
   }

   public void setInitialValue(Object initialValue) {
      this.initialValue = initialValue;
   }

   ClassHolder getOwner() {
      return this.owner;
   }

   void setOwner(ClassHolder owner) {
      this.owner = owner;
      this.reference = null;
   }

   @Override
   public String getOwnerName() {
      return this.owner != null ? this.owner.getName() : null;
   }

   @Override
   public FieldReference getReference() {
      if (this.reference == null && this.owner != null) {
         this.reference = new FieldReference(this.getOwnerName(), this.getName());
      }

      return this.reference;
   }

   public void updateReference(ReferenceCache cache) {
      FieldReference reference = this.getReference();
      if (reference != null) {
         this.reference = cache.getCached(reference);
      }
   }
}
