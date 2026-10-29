package org.teavm.backend.javascript.rendering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.teavm.backend.javascript.codegen.NamingStrategy;
import org.teavm.backend.javascript.codegen.SourceWriterSink;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public class NameFrequencyEstimator implements SourceWriterSink {
   static final MethodReference MONITOR_ENTER_METHOD = new MethodReference(Object.class, "monitorEnter", Object.class, void.class);
   static final MethodReference MONITOR_ENTER_SYNC_METHOD = new MethodReference(Object.class, "monitorEnterSync", Object.class, void.class);
   static final MethodReference MONITOR_EXIT_METHOD = new MethodReference(Object.class, "monitorExit", Object.class, void.class);
   static final MethodReference MONITOR_EXIT_SYNC_METHOD = new MethodReference(Object.class, "monitorExitSync", Object.class, void.class);
   private Map<String, NameFrequencyEstimator.Entry> entries = new HashMap<>();
   private Set<String> reservedNames = new HashSet<>();
   private boolean hasAdditionalScope;

   public boolean hasAdditionalScope() {
      return this.hasAdditionalScope;
   }

   @Override
   public SourceWriterSink appendClass(String cls) {
      String key = "c:" + cls;
      NameFrequencyEstimator.Entry entry = this.entries.get(key);
      if (entry == null) {
         entry = new NameFrequencyEstimator.Entry();
         entry.operation = naming -> naming.className(cls).scoped;
         this.entries.put(key, entry);
      }

      entry.frequency++;
      return this;
   }

   @Override
   public SourceWriterSink appendField(FieldReference field) {
      String key = "f:" + field;
      NameFrequencyEstimator.Entry entry = this.entries.get(key);
      if (entry == null) {
         entry = new NameFrequencyEstimator.Entry();
         entry.operation = naming -> {
            naming.instanceFieldName(field);
            return false;
         };
         this.entries.put(key, entry);
      }

      entry.frequency++;
      return this;
   }

   @Override
   public SourceWriterSink appendStaticField(FieldReference field) {
      String key = "sf:" + field;
      NameFrequencyEstimator.Entry entry = this.entries.get(key);
      if (entry == null) {
         entry = new NameFrequencyEstimator.Entry();
         entry.operation = naming -> naming.fieldName(field).scoped;
         this.entries.put(key, entry);
      }

      entry.frequency++;
      return this;
   }

   @Override
   public SourceWriterSink appendVirtualMethod(MethodDescriptor method) {
      String key = "r:" + method;
      NameFrequencyEstimator.Entry entry = this.entries.get(key);
      if (entry == null) {
         entry = new NameFrequencyEstimator.Entry();
         entry.operation = naming -> {
            naming.instanceMethodName(method);
            return false;
         };
         this.entries.put(key, entry);
      }

      entry.frequency++;
      return this;
   }

   @Override
   public SourceWriterSink appendMethod(MethodReference method) {
      String key = "R:" + method;
      NameFrequencyEstimator.Entry entry = this.entries.get(key);
      if (entry == null) {
         entry = new NameFrequencyEstimator.Entry();
         entry.operation = naming -> naming.methodName(method).scoped;
         this.entries.put(key, entry);
      }

      entry.frequency++;
      return this;
   }

   @Override
   public SourceWriterSink appendFunction(String name) {
      String key = "n:" + name;
      NameFrequencyEstimator.Entry entry = this.entries.get(key);
      if (entry == null) {
         entry = new NameFrequencyEstimator.Entry();
         entry.operation = naming -> naming.functionName(name).scoped;
         this.entries.put(key, entry);
      }

      entry.frequency++;
      return this;
   }

   @Override
   public SourceWriterSink appendGlobal(String name) {
      this.reservedNames.add(name);
      return this;
   }

   @Override
   public SourceWriterSink appendInit(MethodReference method) {
      String key = "I:" + method;
      NameFrequencyEstimator.Entry entry = this.entries.get(key);
      if (entry == null) {
         entry = new NameFrequencyEstimator.Entry();
         entry.operation = naming -> naming.initializerName(method).scoped;
         this.entries.put(key, entry);
      }

      entry.frequency++;
      return this;
   }

   @Override
   public SourceWriterSink appendClassInit(String className) {
      String key = "C:" + className;
      NameFrequencyEstimator.Entry entry = this.entries.get(key);
      if (entry == null) {
         entry = new NameFrequencyEstimator.Entry();
         entry.operation = naming -> naming.classInitializerName(className).scoped;
         this.entries.put(key, entry);
      }

      entry.frequency++;
      return this;
   }

   public void apply(NamingStrategy naming) {
      for (String name : this.reservedNames) {
         naming.reserveName(name);
      }

      ArrayList<NameFrequencyEstimator.Entry> entryList = new ArrayList<>(this.entries.values());
      entryList.sort((o1, o2) -> Integer.compare(o2.frequency, o1.frequency));

      for (NameFrequencyEstimator.Entry entry : entryList) {
         this.hasAdditionalScope = this.hasAdditionalScope | entry.operation.perform(naming);
      }
   }

   private static class Entry {
      NameFrequencyEstimator.NamingOperation operation;
      int frequency;
   }

   private interface NamingOperation {
      boolean perform(NamingStrategy var1);
   }
}
