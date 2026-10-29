package org.teavm.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.model.ClassReader;
import org.teavm.model.FieldReader;
import org.teavm.model.GenericTypeParameter;
import org.teavm.model.GenericValueType;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;

class CachedClassReader extends CachedElement implements ClassReader {
   String parent;
   GenericTypeParameter[] parameters;
   GenericValueType.Object genericParent;
   String owner;
   String declaringClass;
   String simpleName;
   Set<String> interfaces;
   Set<GenericValueType.Object> genericInterfaces;
   Map<MethodDescriptor, CachedMethod> methods;
   Map<String, CachedField> fields;
   List<String> innerClasses = new ArrayList<>();

   @Override
   public GenericTypeParameter[] getGenericParameters() {
      return this.parameters != null ? (GenericTypeParameter[])this.parameters.clone() : new GenericTypeParameter[0];
   }

   @Override
   public String getParent() {
      return this.parent;
   }

   @Override
   public GenericValueType.Object getGenericParent() {
      return this.genericParent;
   }

   @Override
   public Set<String> getInterfaces() {
      return this.interfaces;
   }

   @Override
   public Set<GenericValueType.Object> getGenericInterfaces() {
      return this.genericInterfaces;
   }

   @Override
   public MethodReader getMethod(MethodDescriptor method) {
      return this.methods.get(method);
   }

   @Override
   public Collection<? extends MethodReader> getMethods() {
      return this.methods.values();
   }

   @Override
   public FieldReader getField(String name) {
      return this.fields.get(name);
   }

   @Override
   public Collection<? extends FieldReader> getFields() {
      return this.fields.values();
   }

   @Override
   public String getOwnerName() {
      return this.owner;
   }

   @Override
   public String getSimpleName() {
      return this.simpleName;
   }

   @Override
   public String getDeclaringClassName() {
      return this.declaringClass;
   }

   @Override
   public List<? extends String> getInnerClasses() {
      return this.innerClasses;
   }
}
