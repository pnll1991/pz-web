package org.teavm.debugging.information;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.teavm.backend.javascript.codegen.LocationProvider;
import org.teavm.common.RecordArray;
import org.teavm.common.RecordArrayBuilder;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.ReferenceCache;

public class DebugInformationBuilder implements DebugInformationEmitter {
   private LocationProvider locationProvider;
   private DebugInformation debugInformation;
   private DebugInformationBuilder.MappedList files = new DebugInformationBuilder.MappedList();
   private DebugInformationBuilder.MappedList classes = new DebugInformationBuilder.MappedList();
   private DebugInformationBuilder.MappedList fields = new DebugInformationBuilder.MappedList();
   private DebugInformationBuilder.MappedList methods = new DebugInformationBuilder.MappedList();
   private DebugInformationBuilder.MappedList variableNames = new DebugInformationBuilder.MappedList();
   private List<Long> exactMethods = new ArrayList<>();
   private Map<Long, Integer> exactMethodMap = new HashMap<>();
   private RecordArrayBuilder statementStartMapping = new RecordArrayBuilder(2, 0);
   private List<DebugInformationBuilder.LayerBuilder> layers = new ArrayList<>();
   private DebugInformationBuilder.LayerBuilder currentLayer;
   private int currentLayerIndex;
   private RecordArrayBuilder callSiteMapping = new RecordArrayBuilder(4, 0);
   private Map<Integer, RecordArrayBuilder> variableMappings = new HashMap<>();
   private int currentClassMetadata = -1;
   private List<DebugInformationBuilder.ClassMetadata> classesMetadata = new ArrayList<>();
   private List<RecordArrayBuilder> cfgs = new ArrayList<>();
   private ReferenceCache referenceCache;

   public DebugInformationBuilder(ReferenceCache referenceCache) {
      this.referenceCache = referenceCache;
      this.currentLayer = new DebugInformationBuilder.LayerBuilder();
      this.layers.add(this.currentLayer);
   }

   public LocationProvider getLocationProvider() {
      return this.locationProvider;
   }

   @Override
   public void setLocationProvider(LocationProvider locationProvider) {
      this.locationProvider = locationProvider;
   }

   @Override
   public void emitLocation(String fileName, int line) {
      this.debugInformation = null;
      int fileIndex = this.files.index(fileName);
      if (!Objects.equals(this.currentLayer.currentFileName, fileName)) {
         this.add(this.currentLayer.fileMapping, fileIndex);
         this.currentLayer.currentFileName = fileName;
      }

      if (this.currentLayer.currentLine != line) {
         this.add(this.currentLayer.lineMapping, line);
         this.currentLayer.currentLine = line;
      }
   }

   @Override
   public void enterLocation() {
      if (++this.currentLayerIndex >= this.layers.size()) {
         this.layers.add(new DebugInformationBuilder.LayerBuilder());
      }

      this.currentLayer = this.layers.get(this.currentLayerIndex);
   }

   @Override
   public void exitLocation() {
      this.emitClass(null);
      this.emitMethod(null);
      this.emitLocation(null, -1);
      this.currentLayer = this.layers.get(--this.currentLayerIndex);
   }

   private RecordArrayBuilder.Record add(RecordArrayBuilder builder) {
      if (builder.size() > 1) {
         RecordArrayBuilder.Record lastRecord = builder.get(builder.size() - 1);
         if (lastRecord.get(0) == this.locationProvider.getLine() && lastRecord.get(1) == this.locationProvider.getColumn()) {
            return lastRecord;
         }
      }

      RecordArrayBuilder.Record record = builder.add();
      record.set(0, this.locationProvider.getLine());
      record.set(1, this.locationProvider.getColumn());
      return record;
   }

   private RecordArrayBuilder.Record add(RecordArrayBuilder builder, int value) {
      RecordArrayBuilder.Record record = this.add(builder);
      record.set(2, value);
      return record;
   }

   @Override
   public void emitClass(String className) {
      this.debugInformation = null;
      int classIndex = this.classes.index(className);
      if (!Objects.equals(className, this.currentLayer.currentClass)) {
         this.add(this.currentLayer.classMapping, classIndex);
         this.currentLayer.currentClass = className;
      }
   }

   @Override
   public void emitMethod(MethodDescriptor method) {
      this.debugInformation = null;
      int methodIndex = this.methods.index(method != null ? method.toString() : null);
      if (!Objects.equals(method, this.currentLayer.currentMethod)) {
         this.add(this.currentLayer.methodMapping, methodIndex);
         this.currentLayer.currentMethod = method;
      }

      if (this.currentLayer.currentClass != null) {
         int classIndex = this.classes.index(this.currentLayer.currentClass);
         long fullIndex = (long)classIndex << 32 | methodIndex;
         if (!this.exactMethodMap.containsKey(fullIndex)) {
            this.exactMethodMap.put(fullIndex, this.exactMethods.size());
            this.exactMethods.add(fullIndex);
         }
      }
   }

   @Override
   public void emitStatementStart() {
      RecordArrayBuilder.Record record = this.statementStartMapping.add();
      record.set(0, this.locationProvider.getLine());
      record.set(1, this.locationProvider.getColumn());
   }

   @Override
   public void emitVariable(String[] sourceNames, String generatedName) {
      int[] sourceIndexes = new int[sourceNames.length];

      for (int i = 0; i < sourceIndexes.length; i++) {
         sourceIndexes[i] = this.variableNames.index(sourceNames[i]);
      }

      Arrays.sort(sourceIndexes);
      int generatedIndex = this.variableNames.index(generatedName);
      RecordArrayBuilder mapping = this.variableMappings.computeIfAbsent(generatedIndex, k -> new RecordArrayBuilder(2, 1));
      RecordArrayBuilder.Record record = this.add(mapping);
      RecordArrayBuilder.SubArray array = record.getArray(0);

      for (int sourceIndex : sourceIndexes) {
         array.add(sourceIndex);
      }
   }

   @Override
   public DeferredCallSite emitCallSite() {
      final RecordArrayBuilder.Record record = this.add(this.callSiteMapping, 0);
      return new DeferredCallSite() {
         @Override
         public void setVirtualMethod(MethodReference method) {
            record.set(2, 2);
            record.set(3, this.getExactMethodIndex(method));
         }

         @Override
         public void setStaticMethod(MethodReference method) {
            record.set(2, 1);
            record.set(3, this.getExactMethodIndex(method));
         }

         @Override
         public void clean() {
            record.set(2, 0);
            record.set(3, 0);
         }

         private int getExactMethodIndex(MethodReference method) {
            int methodIndex = DebugInformationBuilder.this.methods.index(method.getDescriptor().toString());
            int classIndex = DebugInformationBuilder.this.classes.index(method.getClassName());
            long fullIndex = (long)classIndex << 32 | methodIndex;
            Integer exactMethodIndex = DebugInformationBuilder.this.exactMethodMap.get(fullIndex);
            if (exactMethodIndex == null) {
               exactMethodIndex = DebugInformationBuilder.this.exactMethods.size();
               DebugInformationBuilder.this.exactMethodMap.put(fullIndex, exactMethodIndex);
               DebugInformationBuilder.this.exactMethods.add(fullIndex);
            }

            return exactMethodIndex;
         }
      };
   }

   @Override
   public void addClass(String jsName, String className, String parentName) {
      int classIndex = this.classes.index(className);
      int parentIndex = this.classes.index(parentName);

      while (classIndex >= this.classesMetadata.size()) {
         this.classesMetadata.add(new DebugInformationBuilder.ClassMetadata());
      }

      this.currentClassMetadata = classIndex;
      DebugInformationBuilder.ClassMetadata metadata = this.classesMetadata.get(classIndex);
      metadata.parentIndex = parentIndex;
      metadata.jsName = jsName;
   }

   @Override
   public void addField(String fieldName, String jsName) {
      DebugInformationBuilder.ClassMetadata metadata = this.classesMetadata.get(this.currentClassMetadata);
      int fieldIndex = this.fields.index(fieldName);
      int jsIndex = this.fields.index(jsName);
      metadata.fieldMap.put(jsIndex, fieldIndex);
   }

   @Override
   public void addSuccessors(SourceLocation location, SourceLocation[] successors) {
      int fileIndex = this.files.index(location.getFileName());

      while (this.cfgs.size() <= fileIndex) {
         this.cfgs.add(new RecordArrayBuilder(1, 1));
      }

      RecordArrayBuilder cfg = this.cfgs.get(fileIndex);

      while (cfg.size() <= location.getLine()) {
         cfg.add();
      }

      RecordArrayBuilder.Record record = cfg.get(location.getLine());
      if (record.get(0) == 0) {
         record.set(0, 1);
      }

      RecordArrayBuilder.SubArray array = record.getArray(0);

      for (SourceLocation succ : successors) {
         if (succ == null) {
            record.set(0, 2);
         } else {
            array.add(this.files.index(succ.getFileName()));
            array.add(succ.getLine());
         }
      }
   }

   private RecordArrayBuilder compress(RecordArrayBuilder builder) {
      int lastValue = 0;
      RecordArrayBuilder compressed = new RecordArrayBuilder(builder.getRecordSize(), builder.getArraysPerRecord());

      for (int i = 0; i < builder.size(); i++) {
         RecordArrayBuilder.Record record = builder.get(i);
         if (i == 0 || lastValue != record.get(2)) {
            lastValue = record.get(2);
            RecordArrayBuilder.Record compressedRecord = compressed.add();

            for (int j = 0; j < builder.getRecordSize(); j++) {
               compressedRecord.set(j, record.get(j));
            }
         }
      }

      return compressed;
   }

   private void compressAndSortArrays(RecordArrayBuilder builder) {
      for (int i = 0; i < builder.size(); i++) {
         RecordArrayBuilder.Record record = builder.get(i);

         for (int j = 0; j < builder.getArraysPerRecord(); j++) {
            RecordArrayBuilder.SubArray array = record.getArray(j);
            int[] data = array.getData();
            Arrays.sort(data);
            array.clear();
            if (data.length > 0) {
               int last = data[0];
               array.add(last);

               for (int k = 1; k < data.length; k++) {
                  if (data[k] != last) {
                     last = data[k];
                     array.add(last);
                  }
               }
            }
         }
      }
   }

   public DebugInformation getDebugInformation() {
      if (this.debugInformation == null) {
         this.debugInformation = new DebugInformation(this.referenceCache);
         this.debugInformation.fileNames = this.files.getItems();
         this.debugInformation.classNames = this.classes.getItems();
         this.debugInformation.fields = this.fields.getItems();
         this.debugInformation.methods = this.methods.getItems();
         this.debugInformation.variableNames = this.variableNames.getItems();
         this.debugInformation.exactMethods = new long[this.exactMethods.size()];

         for (int i = 0; i < this.exactMethods.size(); i++) {
            this.debugInformation.exactMethods[i] = this.exactMethods.get(i);
         }

         this.debugInformation.exactMethodMap = new HashMap<>(this.exactMethodMap);
         this.debugInformation.statementStartMapping = this.statementStartMapping.build();
         DebugInformation.Layer[] builtLayers = new DebugInformation.Layer[this.layers.size()];

         for (int i = 0; i < builtLayers.length; i++) {
            DebugInformationBuilder.LayerBuilder layerBuilder = this.layers.get(i);
            DebugInformation.Layer builtLayer = new DebugInformation.Layer();
            builtLayer.fileMapping = this.compress(layerBuilder.fileMapping).build();
            builtLayer.lineMapping = this.compress(layerBuilder.lineMapping).build();
            builtLayer.classMapping = this.compress(layerBuilder.classMapping).build();
            builtLayer.methodMapping = this.compress(layerBuilder.methodMapping).build();
            builtLayers[i] = builtLayer;
         }

         this.debugInformation.layers = builtLayers;
         this.debugInformation.callSiteMapping = this.callSiteMapping.build();
         this.debugInformation.variableMappings = new RecordArray[this.variableNames.list.size()];

         for (int var : this.variableMappings.keySet()) {
            RecordArrayBuilder mapping = this.variableMappings.get(var);
            this.compressAndSortArrays(mapping);
            this.debugInformation.variableMappings[var] = mapping.build();
         }

         List<DebugInformation.ClassMetadata> builtMetadata = new ArrayList<>(this.classes.list.size());

         for (int i = 0; i < this.classes.list.size(); i++) {
            DebugInformation.ClassMetadata mappedMetadata = new DebugInformation.ClassMetadata();
            mappedMetadata.id = i;
            if (i < this.classesMetadata.size()) {
               DebugInformationBuilder.ClassMetadata origMetadata = this.classesMetadata.get(i);
               mappedMetadata.jsName = origMetadata.jsName;
               mappedMetadata.fieldMap.putAll(origMetadata.fieldMap);
               mappedMetadata.parentId = origMetadata.parentIndex >= 0 ? origMetadata.parentIndex : null;
            }

            builtMetadata.add(mappedMetadata);
         }

         this.debugInformation.classesMetadata = builtMetadata;
         RecordArray[] cfgs = new RecordArray[this.files.list.size()];

         for (int i = 0; i < this.cfgs.size(); i++) {
            if (this.cfgs.get(i) != null) {
               cfgs[i] = this.cfgs.get(i).build();
            }
         }

         this.debugInformation.controlFlowGraphs = cfgs;
         this.debugInformation.rebuild();
      }

      return this.debugInformation;
   }

   static class ClassMetadata {
      int parentIndex = -1;
      String jsName;
      Map<Integer, Integer> fieldMap = new HashMap<>();
   }

   static class LayerBuilder {
      private RecordArrayBuilder fileMapping = new RecordArrayBuilder(3, 0);
      private RecordArrayBuilder lineMapping = new RecordArrayBuilder(3, 0);
      private RecordArrayBuilder classMapping = new RecordArrayBuilder(3, 0);
      private RecordArrayBuilder methodMapping = new RecordArrayBuilder(3, 0);
      private MethodDescriptor currentMethod;
      private String currentClass;
      private String currentFileName;
      private int currentLine;
   }

   static class MappedList {
      private List<String> list = new ArrayList<>();
      private Map<String, Integer> map = new HashMap<>();

      public int index(String item) {
         if (item == null) {
            return -1;
         }

         Integer index = this.map.get(item);
         if (index == null) {
            index = this.list.size();
            this.list.add(item);
            this.map.put(item, index);
         }

         return index;
      }

      public String[] getItems() {
         return this.list.toArray(new String[0]);
      }

      public Map<String, Integer> getIndexes() {
         return new HashMap<>(this.map);
      }
   }
}
