package org.teavm.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClassHolder extends ElementHolder implements ClassReader {
   private GenericTypeParameter[] genericParameters;
   private String parent = Object.class.getName();
   private GenericValueType.Object genericParent;
   private Set<String> interfaces = new LinkedHashSet<>();
   private Set<GenericValueType.Object> genericInterfaces = new LinkedHashSet<>();
   private Map<MethodDescriptor, MethodHolder> methods = new LinkedHashMap<>();
   private Map<String, FieldHolder> fields = new LinkedHashMap<>();
   private String ownerName;
   private String simpleName;
   private String declaringClassName;
   private List<String> innerClasses = new ArrayList<>();

   public ClassHolder(String name) {
      super(name);
   }

   @Override
   public GenericTypeParameter[] getGenericParameters() {
      return this.genericParameters != null ? (GenericTypeParameter[])this.genericParameters.clone() : null;
   }

   public void setGenericParameters(GenericTypeParameter[] genericParameters) {
      this.genericParameters = genericParameters != null ? (GenericTypeParameter[])genericParameters.clone() : null;
   }

   @Override
   public String getParent() {
      return this.parent;
   }

   public void setParent(String parent) {
      this.parent = parent;
   }

   @Override
   public GenericValueType.Object getGenericParent() {
      return this.genericParent;
   }

   public void setGenericParent(GenericValueType.Object genericParent) {
      this.genericParent = genericParent;
   }

   @Override
   public Set<String> getInterfaces() {
      return this.interfaces;
   }

   @Override
   public Set<GenericValueType.Object> getGenericInterfaces() {
      return this.genericInterfaces;
   }

   public MethodHolder getMethod(MethodDescriptor method) {
      return this.methods.get(method);
   }

   @Override
   public Collection<MethodHolder> getMethods() {
      return this.methods.values();
   }

   public void addMethod(MethodHolder method) {
      if (method.getOwner() != null) {
         throw new IllegalArgumentException("Method " + method.getDescriptor() + " is already in another class (" + method.getOwner().getName() + ")");
      }

      method.setOwner(this);
      MethodHolder oldMethod = this.methods.put(method.getDescriptor(), method);
      if (oldMethod != null) {
         oldMethod.setOwner(null);
      }
   }

   public void removeMethod(MethodHolder method) {
      if (method.getOwner() != this) {
         throw new IllegalArgumentException("Method " + method.getOwner().getName() + "." + method.getDescriptor() + " is not a member of " + this.getName());
      }

      this.methods.remove(method.getDescriptor());
      method.setOwner(null);
   }

   public void removeAllMethods() {
      for (MethodHolder method : this.methods.values()) {
         method.setOwner(null);
      }

      this.methods.clear();
   }

   public FieldHolder getField(String name) {
      return this.fields.get(name);
   }

   @Override
   public Collection<FieldHolder> getFields() {
      return this.fields.values();
   }

   public void addField(FieldHolder field) {
      if (field.getOwner() != null) {
         throw new IllegalArgumentException("Field " + field.getName() + " is already in another class (" + field.getOwner().getName() + ")");
      }

      field.setOwner(this);
      FieldHolder oldField = this.fields.put(field.getName(), field);
      if (oldField != null) {
         oldField.setOwner(null);
      }
   }

   public void removeField(FieldHolder field) {
      if (field.getOwner() != this) {
         throw new IllegalArgumentException("Field " + field.getOwner().getName() + "." + field.getName() + " is not a member of " + this.getName());
      }

      this.fields.remove(field.getName());
      field.setOwner(null);
   }

   public void removeAllFields() {
      for (FieldHolder field : this.fields.values()) {
         field.setOwner(null);
      }

      this.fields.clear();
   }

   @Override
   public String getOwnerName() {
      return this.ownerName;
   }

   public void setOwnerName(String ownerName) {
      this.ownerName = ownerName;
   }

   @Override
   public String getSimpleName() {
      return this.simpleName;
   }

   public void setSimpleName(String simpleName) {
      this.simpleName = simpleName;
   }

   @Override
   public String getDeclaringClassName() {
      return this.declaringClassName;
   }

   public void setDeclaringClassName(String declaringClassName) {
      this.declaringClassName = declaringClassName;
   }

   @Override
   public List<String> getInnerClasses() {
      return this.innerClasses;
   }
}
