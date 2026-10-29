package org.teavm.debugging.information;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import org.teavm.common.IntegerArray;
import org.teavm.common.RecordArray;
import org.teavm.common.RecordArrayBuilder;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.ReferenceCache;

public class DebugInformation {
   String[] fileNames;
   Map<String, Integer> fileNameMap;
   String[] classNames;
   Map<String, Integer> classNameMap;
   String[] fields;
   Map<String, Integer> fieldMap;
   String[] methods;
   Map<String, Integer> methodMap;
   String[] variableNames;
   Map<String, Integer> variableNameMap;
   long[] exactMethods;
   Map<Long, Integer> exactMethodMap;
   RecordArray[] fileDescriptions;
   DebugInformation.Layer[] layers;
   RecordArray callSiteMapping;
   RecordArray statementStartMapping;
   RecordArray[] variableMappings;
   RecordArray[] lineCallSites;
   RecordArray[] controlFlowGraphs;
   List<DebugInformation.ClassMetadata> classesMetadata;
   Map<String, DebugInformation.ClassMetadata> classMetadataByJsName;
   RecordArray methodEntrances;
   DebugInformation.MethodTree methodTree;
   ReferenceCache referenceCache;

   public DebugInformation() {
      this(new ReferenceCache());
   }

   public DebugInformation(ReferenceCache referenceCache) {
      this.referenceCache = referenceCache;
   }

   public String[] getFilesNames() {
      return (String[])this.fileNames.clone();
   }

   public String[] getVariableNames() {
      return (String[])this.variableNames.clone();
   }

   public String getFileName(int fileNameId) {
      return this.fileNames[fileNameId];
   }

   public String[] getClassNames() {
      return (String[])this.classNames.clone();
   }

   public String getClassName(int classNameId) {
      return this.classNames[classNameId];
   }

   public MethodDescriptor[] getMethods() {
      MethodDescriptor[] descriptors = new MethodDescriptor[this.methods.length];

      for (int i = 0; i < descriptors.length; i++) {
         descriptors[i] = this.referenceCache.parseDescriptorCached(this.methods[i]);
      }

      return descriptors;
   }

   public MethodDescriptor getMethod(int methodId) {
      return this.referenceCache.parseDescriptorCached(this.methods[methodId]);
   }

   public MethodReference[] getExactMethods() {
      MethodReference[] result = new MethodReference[this.exactMethods.length];

      for (int i = 0; i < result.length; i++) {
         result[i] = this.getExactMethod(i);
      }

      return result;
   }

   private Integer getExactMethodIndex(MethodReference methodRef) {
      Integer classIndex = this.classNameMap.get(methodRef.getClassName());
      if (classIndex == null) {
         return null;
      }

      Integer methodIndex = this.methodMap.get(methodRef.getDescriptor().toString());
      return methodIndex == null ? null : this.getExactMethodIndex(classIndex, methodIndex);
   }

   public MethodReference getExactMethod(int index) {
      long item = this.exactMethods[index];
      int classIndex = (int)(item >>> 32);
      int methodIndex = (int)item;
      return this.referenceCache.getCached(this.classNames[classIndex], this.referenceCache.parseDescriptorCached(this.methods[methodIndex]));
   }

   public int getExactMethodId(int classNameId, int methodId) {
      long full = (long)classNameId << 32 | methodId;
      Integer id = this.exactMethodMap.get(full);
      return id != null ? id : -1;
   }

   public int layerCount() {
      return this.layers.length;
   }

   public ExactMethodIterator iterateOverExactMethods(int layerIndex) {
      return new ExactMethodIterator(this, this.layers[layerIndex]);
   }

   public Collection<GeneratedLocation> getGeneratedLocations(String fileName, int line) {
      Integer fileIndex = this.fileNameMap.get(fileName);
      if (fileIndex == null) {
         return Collections.emptyList();
      }

      RecordArray description = fileIndex >= 0 ? this.fileDescriptions[fileIndex] : null;
      if (description == null) {
         return Collections.emptyList();
      }

      if (line >= description.size()) {
         return Collections.emptyList();
      }

      int[] data = description.get(line).getArray(0);
      GeneratedLocation[] resultArray = new GeneratedLocation[data.length / 2];

      for (int i = 0; i < resultArray.length; i++) {
         int genLine = data[i * 2];
         int genColumn = data[i * 2 + 1];
         resultArray[i] = new GeneratedLocation(genLine, genColumn);
      }

      return Arrays.asList(resultArray);
   }

   public Collection<GeneratedLocation> getGeneratedLocations(SourceLocation sourceLocation) {
      return this.getGeneratedLocations(sourceLocation.getFileName(), sourceLocation.getLine());
   }

   public SourceLocationIterator iterateOverSourceLocations() {
      return new SourceLocationIterator(this);
   }

   private LayerSourceLocationIterator iterateOverSourceLocations(int layer) {
      return new LayerSourceLocationIterator(this, this.layers[layer]);
   }

   public SourceLocation getSourceLocation(int line, int column) {
      return this.getSourceLocation(new GeneratedLocation(line, column));
   }

   public SourceLocation getSourceLocation(int line, int column, int layerIndex) {
      return this.getSourceLocation(new GeneratedLocation(line, column), layerIndex);
   }

   public SourceLocation getSourceLocation(GeneratedLocation generatedLocation) {
      return this.getSourceLocation(generatedLocation, this.autodetectLayer(generatedLocation));
   }

   public SourceLocation getSourceLocation(GeneratedLocation generatedLocation, int layerIndex) {
      if (layerIndex >= 0 && layerIndex < this.layers.length) {
         DebugInformation.Layer layer = this.layers[layerIndex];
         String fileName = this.componentByKey(layer.fileMapping, this.fileNames, generatedLocation);
         int lineNumberIndex = this.indexByKey(layer.lineMapping, generatedLocation);
         int lineNumber = lineNumberIndex >= 0 ? layer.lineMapping.get(lineNumberIndex).get(2) : -1;
         return new SourceLocation(fileName, lineNumber);
      } else {
         return null;
      }
   }

   public MethodReference getMethodAt(GeneratedLocation generatedLocation) {
      return this.getMethodAt(generatedLocation, this.autodetectLayer(generatedLocation));
   }

   public MethodReference getMethodAt(GeneratedLocation generatedLocation, int layerIndex) {
      if (layerIndex >= 0 && layerIndex < this.layers.length) {
         DebugInformation.Layer layer = this.layers[layerIndex];
         String className = this.componentByKey(layer.classMapping, this.classNames, generatedLocation);
         if (className == null) {
            return null;
         }

         String method = this.componentByKey(layer.methodMapping, this.methods, generatedLocation);
         return method == null ? null : this.referenceCache.getCached(className, this.referenceCache.parseDescriptorCached(method));
      } else {
         return null;
      }
   }

   private int autodetectLayer(GeneratedLocation generatedLocation) {
      int layerIndex = 0;
      int i = 1;

      while (i < this.layers.length && this.componentByKey(this.layers[i].classMapping, this.classNames, generatedLocation) != null) {
         layerIndex = i++;
      }

      return layerIndex;
   }

   public MethodReference getMethodAt(int line, int column) {
      return this.getMethodAt(new GeneratedLocation(line, column));
   }

   public String[] getVariableMeaningAt(int line, int column, String variable) {
      return this.getVariableMeaningAt(new GeneratedLocation(line, column), variable);
   }

   public String[] getVariableMeaningAt(GeneratedLocation location, String variable) {
      Integer varIndex = this.variableNameMap.get(variable);
      if (varIndex == null) {
         return new String[0];
      }

      RecordArray mapping = this.variableMappings[varIndex];
      if (mapping == null) {
         return new String[0];
      }

      int keyIndex = this.indexByKey(mapping, location);
      if (keyIndex < 0) {
         return new String[0];
      }

      GeneratedLocation keyLocation = key(mapping.get(keyIndex));
      if (!Objects.equals(this.getMethodAt(keyLocation), this.getMethodAt(location))) {
         return new String[0];
      }

      int[] valueIndexes = mapping.get(keyIndex).getArray(0);
      String[] result = new String[valueIndexes.length];

      for (int i = 0; i < result.length; i++) {
         result[i] = valueIndexes[i] >= 0 ? this.variableNames[valueIndexes[i]] : null;
      }

      return result;
   }

   public SourceLocation[] getFollowingLines(SourceLocation location) {
      Integer fileIndex = this.fileNameMap.get(location.getFileName());
      if (fileIndex == null) {
         return null;
      }

      RecordArray cfg = this.controlFlowGraphs[fileIndex];
      if (cfg == null) {
         return null;
      }

      if (location.getLine() >= cfg.size()) {
         return null;
      }

      int type = cfg.get(location.getLine()).get(0);
      if (type == 0) {
         return null;
      }

      int[] data = cfg.get(location.getLine()).getArray(0);
      int length = data.length / 2;
      int size = length;
      if (type == 2) {
         size++;
      }

      SourceLocation[] result = new SourceLocation[size];

      for (int i = 0; i < length; i++) {
         result[i] = new SourceLocation(this.fileNames[data[i * 2]], data[i * 2 + 1]);
      }

      return result;
   }

   public String getFieldMeaning(String className, String jsName) {
      Integer classIndex = this.classNameMap.get(className);
      if (classIndex == null) {
         return null;
      }

      Integer jsIndex = this.fieldMap.get(jsName);
      if (jsIndex == null) {
         return null;
      }

      while (classIndex != null) {
         DebugInformation.ClassMetadata cls = this.classesMetadata.get(classIndex);
         Integer fieldIndex = cls.fieldMap.get(jsIndex);
         if (fieldIndex != null) {
            return this.fields[fieldIndex];
         }

         classIndex = cls.parentId;
      }

      return null;
   }

   public String getClassNameByJsName(String className) {
      DebugInformation.ClassMetadata cls = this.classMetadataByJsName.get(className);
      return cls != null ? this.classNames[cls.id] : null;
   }

   public DebuggerCallSite getCallSite(GeneratedLocation location) {
      int keyIndex = this.indexByKey(this.callSiteMapping, location);
      return keyIndex >= 0 ? this.getCallSite(keyIndex) : null;
   }

   private DebuggerCallSite getCallSite(int index) {
      RecordArray.Record record = this.callSiteMapping.get(index);
      int type = record.get(2);
      int method = record.get(3);
      switch (type) {
         case 0:
            return null;
         case 1:
            return new DebuggerStaticCallSite(this.getExactMethod(method));
         case 2:
            return new DebuggerVirtualCallSite(this.getExactMethod(method));
         default:
            throw new AssertionError("Unrecognized call site type: " + type);
      }
   }

   public DebuggerCallSite getCallSite(int line, int column) {
      return this.getCallSite(new GeneratedLocation(line, column));
   }

   public GeneratedLocation[] getMethodEntrances(MethodReference methodRef) {
      Integer index = this.getExactMethodIndex(methodRef);
      if (index == null) {
         return new GeneratedLocation[0];
      }

      int[] data = this.methodEntrances.get(index).getArray(0);
      GeneratedLocation[] entrances = new GeneratedLocation[data.length / 2];

      for (int i = 0; i < entrances.length; i++) {
         entrances[i] = new GeneratedLocation(data[i * 2], data[i * 2 + 1]);
      }

      return entrances;
   }

   public MethodReference[] getDirectOverridingMethods(MethodReference methodRef) {
      Integer methodIndex = this.getExactMethodIndex(methodRef);
      if (methodIndex == null) {
         return new MethodReference[0];
      }

      int start = this.methodTree.offsets[methodIndex];
      int end = this.methodTree.offsets[methodIndex + 1];
      MethodReference[] result = new MethodReference[end - start];

      for (int i = 0; i < result.length; i++) {
         result[i] = this.getExactMethod(this.methodTree.data[i]);
      }

      return result;
   }

   public MethodReference[] getOverridingMethods(MethodReference methodRef) {
      Set<MethodReference> overridingMethods = new HashSet<>();
      this.getOverridingMethods(methodRef, overridingMethods);
      return overridingMethods.toArray(new MethodReference[0]);
   }

   private void getOverridingMethods(MethodReference methodRef, Set<MethodReference> overridingMethods) {
      if (overridingMethods.add(methodRef)) {
         for (MethodReference overridingMethod : this.getDirectOverridingMethods(methodRef)) {
            this.getOverridingMethods(overridingMethod, overridingMethods);
         }
      }
   }

   public DebuggerCallSite[] getCallSites(SourceLocation location) {
      Integer fileIndex = this.fileNameMap.get(location.getFileName());
      if (fileIndex == null) {
         return new DebuggerCallSite[0];
      }

      RecordArray mapping = this.lineCallSites[fileIndex];
      if (location.getLine() >= mapping.size()) {
         return new DebuggerCallSite[0];
      }

      int[] callSiteIds = mapping.get(location.getLine()).getArray(0);
      DebuggerCallSite[] callSites = new DebuggerCallSite[callSiteIds.length];

      for (int i = 0; i < callSiteIds.length; i++) {
         callSites[i] = this.getCallSite(callSiteIds[i]);
      }

      return callSites;
   }

   public List<GeneratedLocation> getStatementStartLocations() {
      return new DebugInformation.LocationList(this.statementStartMapping);
   }

   public GeneratedLocation getStatementLocation(GeneratedLocation location) {
      int index = this.indexByKey(this.statementStartMapping, location);
      if (index < 0) {
         return new GeneratedLocation(0, 0);
      }

      RecordArray.Record record = this.statementStartMapping.get(index);
      return new GeneratedLocation(record.get(0), record.get(1));
   }

   public GeneratedLocation getNextStatementLocation(GeneratedLocation location) {
      int index = this.indexByKey(this.statementStartMapping, location);
      if (index >= this.statementStartMapping.size()) {
         return new GeneratedLocation(0, 0);
      }

      RecordArray.Record record = this.statementStartMapping.get(index + 1);
      return new GeneratedLocation(record.get(0), record.get(1));
   }

   private <T> T componentByKey(RecordArray mapping, T[] values, GeneratedLocation location) {
      int keyIndex = this.indexByKey(mapping, location);
      int valueIndex = keyIndex >= 0 ? mapping.get(keyIndex).get(2) : -1;
      return valueIndex >= 0 ? values[valueIndex] : null;
   }

   private int indexByKey(RecordArray mapping, GeneratedLocation location) {
      int index = this.binarySearchLocation(mapping, location.getLine(), location.getColumn());
      return index >= 0 ? index : -index - 2;
   }

   private int valueByKey(RecordArray mapping, GeneratedLocation location) {
      int index = this.indexByKey(mapping, location);
      return index >= 0 ? mapping.get(index).get(2) : -1;
   }

   public void write(OutputStream output) throws IOException {
      DebugInformationWriter writer = new DebugInformationWriter(new DataOutputStream(output));
      writer.write(this);
   }

   public void writeAsSourceMaps(Writer output, String sourceRoot, String sourceFile) throws IOException {
      new SourceMapsWriter(output).write(sourceFile, sourceRoot, this);
   }

   public static DebugInformation read(InputStream input) throws IOException {
      return read(input, new ReferenceCache());
   }

   public static DebugInformation read(InputStream input, ReferenceCache referenceCache) throws IOException {
      DebugInformationReader reader = new DebugInformationReader(input, referenceCache);
      return reader.read();
   }

   void rebuild() {
      this.rebuildMaps();
      this.rebuildFileDescriptions();
      this.rebuildEntrances();
      this.rebuildMethodTree();
      this.rebuildLineCallSites();
      this.rebuildClassMap();
   }

   void rebuildMaps() {
      this.fileNameMap = this.mapArray(this.fileNames);
      this.classNameMap = this.mapArray(this.classNames);
      this.fieldMap = this.mapArray(this.fields);
      this.methodMap = this.mapArray(this.methods);
      this.variableNameMap = this.mapArray(this.variableNames);
      this.exactMethodMap = new HashMap<>();

      for (int i = 0; i < this.exactMethods.length; i++) {
         this.exactMethodMap.put(this.exactMethods[i], i);
      }
   }

   private Map<String, Integer> mapArray(String[] array) {
      Map<String, Integer> map = new HashMap<>();

      for (int i = 0; i < array.length; i++) {
         map.put(array[i], i);
      }

      return map;
   }

   void rebuildFileDescriptions() {
      RecordArrayBuilder[] builders = new RecordArrayBuilder[this.fileNames.length];

      for (int i = 0; i < builders.length; i++) {
         builders[i] = new RecordArrayBuilder(0, 1);
      }

      for (int layer = 0; layer < this.layers.length; layer++) {
         for (LayerSourceLocationIterator iter = this.iterateOverSourceLocations(layer); !iter.isEndReached(); iter.next()) {
            if (iter.getFileNameId() >= 0 && iter.getLine() >= 0) {
               RecordArrayBuilder builder = builders[iter.getFileNameId()];

               while (builder.size() <= iter.getLine()) {
                  builder.add();
               }

               GeneratedLocation loc = iter.getLocation();
               RecordArrayBuilder.SubArray array = builder.get(iter.getLine()).getArray(0);
               array.add(loc.getLine());
               array.add(loc.getColumn());
            }
         }
      }

      this.fileDescriptions = new RecordArray[builders.length];

      for (int i = 0; i < this.fileDescriptions.length; i++) {
         this.fileDescriptions[i] = builders[i].build();
      }
   }

   void rebuildEntrances() {
      RecordArrayBuilder builder = new RecordArrayBuilder(0, 1);

      for (int i = 0; i < this.exactMethods.length; i++) {
         builder.add();
      }

      GeneratedLocation prevLocation = new GeneratedLocation(0, 0);
      MethodReference prevMethod = null;
      int prevMethodId = -1;
      RecordArray lineMapping = this.layers[0].lineMapping;
      ExactMethodIterator iter = this.iterateOverExactMethods(0);

      while (!iter.isEndReached()) {
         int id = iter.getExactMethodId();
         if (prevMethod != null) {
            for (int lineIndex = Math.max(0, this.indexByKey(lineMapping, prevLocation));
               lineIndex < lineMapping.size() && key(lineMapping.get(lineIndex)).compareTo(iter.getLocation()) < 0;
               lineIndex++
            ) {
               int line = lineMapping.get(lineIndex).get(2);
               if (line >= 0) {
                  GeneratedLocation firstLineLoc = key(lineMapping.get(lineIndex));
                  RecordArrayBuilder.SubArray array = builder.get(prevMethodId).getArray(0);
                  array.add(firstLineLoc.getLine());
                  array.add(firstLineLoc.getColumn());
                  break;
               }
            }
         }

         prevMethod = iter.getExactMethod();
         prevMethodId = id;
         prevLocation = iter.getLocation();
         iter.next();
      }

      this.methodEntrances = builder.build();
   }

   void rebuildMethodTree() {
      long[] exactMethods = (long[])this.exactMethods.clone();
      Arrays.sort(exactMethods);
      IntegerArray methods = new IntegerArray(1);
      int lastClass = -1;

      for (int i = 0; i < exactMethods.length; i++) {
         long exactMethod = exactMethods[i];
         int classIndex = (int)(exactMethod >>> 32);
         if (classIndex != lastClass) {
            if (lastClass >= 0) {
               DebugInformation.ClassMetadata clsData = this.classesMetadata.get(lastClass);
               clsData.methods = methods.getAll();
               methods.clear();
            }

            lastClass = classIndex;
         }

         int methodIndex = (int)exactMethod;
         methods.add(methodIndex);
      }

      if (lastClass >= 0) {
         DebugInformation.ClassMetadata clsData = this.classesMetadata.get(lastClass);
         clsData.methods = methods.getAll();
         Arrays.sort(clsData.methods);
      }

      int[] start = new int[exactMethods.length];
      Arrays.fill(start, -1);
      IntegerArray data = new IntegerArray(1);
      IntegerArray next = new IntegerArray(1);

      for (int i = 0; i < this.classesMetadata.size(); i++) {
         DebugInformation.ClassMetadata clsData = this.classesMetadata.get(i);
         if (clsData.parentId != null && clsData.methods != null) {
            for (int methodIndex : clsData.methods) {
               DebugInformation.ClassMetadata superclsData = this.classesMetadata.get(clsData.parentId);

               for (Integer parentId = clsData.parentId; superclsData != null; superclsData = parentId != null ? this.classesMetadata.get(parentId) : null) {
                  if (superclsData.methods != null && Arrays.binarySearch(superclsData.methods, methodIndex) >= 0) {
                     int childMethod = this.getExactMethodIndex(i, methodIndex);
                     int parentMethod = this.getExactMethodIndex(parentId, methodIndex);
                     int ptr = start[parentMethod];
                     start[parentMethod] = data.size();
                     data.add(childMethod);
                     next.add(ptr);
                     break;
                  }

                  parentId = superclsData.parentId;
               }
            }
         }
      }

      DebugInformation.MethodTree methodTree = new DebugInformation.MethodTree();
      methodTree.offsets = new int[start.length + 1];
      methodTree.data = new int[data.size()];
      int index = 0;

      for (int i = 0; i < start.length; i++) {
         for (int ptr = start[i]; ptr != -1; ptr = next.get(ptr)) {
            methodTree.data[index++] = data.get(ptr);
         }

         methodTree.offsets[i + 1] = index;
      }

      this.methodTree = methodTree;
   }

   private Integer getExactMethodIndex(int classIndex, int methodIndex) {
      long entry = (long)classIndex << 32 | methodIndex;
      return this.exactMethodMap.get(entry);
   }

   private void rebuildLineCallSites() {
      this.lineCallSites = new RecordArray[this.fileNames.length];
      RecordArrayBuilder[] builders = new RecordArrayBuilder[this.fileNames.length];

      for (int i = 0; i < this.lineCallSites.length; i++) {
         builders[i] = new RecordArrayBuilder(0, 1);
      }

      for (int i = 0; i < this.callSiteMapping.size(); i++) {
         RecordArray.Record callSiteRec = this.callSiteMapping.get(i);
         GeneratedLocation loc = key(callSiteRec);
         int callSiteType = callSiteRec.get(2);
         if (callSiteType != 0) {
            int layerIndex = this.autodetectLayer(loc);
            int line = this.valueByKey(this.layers[layerIndex].lineMapping, loc);
            int fileId = this.valueByKey(this.layers[layerIndex].fileMapping, loc);
            if (fileId >= 0 && line >= 0) {
               RecordArrayBuilder builder = builders[fileId];

               while (builder.size() <= line) {
                  builder.add();
               }

               builder.get(line).getArray(0).add(i);
            }
         }
      }

      for (int i = 0; i < this.lineCallSites.length; i++) {
         this.lineCallSites[i] = builders[i].build();
      }
   }

   static GeneratedLocation key(RecordArray.Record record) {
      return new GeneratedLocation(record.get(0), record.get(1));
   }

   private void rebuildClassMap() {
      this.classMetadataByJsName = new HashMap<>();

      for (DebugInformation.ClassMetadata cls : this.classesMetadata) {
         if (cls.jsName != null) {
            this.classMetadataByJsName.put(cls.jsName, cls);
         }
      }
   }

   private int binarySearchLocation(RecordArray array, int row, int column) {
      int l = 0;
      int u = array.size() - 1;

      while (true) {
         int i = (l + u) / 2;
         RecordArray.Record e = array.get(i);
         int cmp = Integer.compare(row, e.get(0));
         if (cmp == 0) {
            cmp = Integer.compare(column, e.get(1));
         }

         if (cmp == 0) {
            return i;
         }

         if (cmp < 0) {
            u = i - 1;
            if (u < l) {
               return -i - 1;
            }
         } else {
            l = i + 1;
            if (l > u) {
               return -i - 2;
            }
         }
      }
   }

   static class ClassMetadata {
      int id;
      Integer parentId;
      Map<Integer, Integer> fieldMap = new HashMap<>();
      int[] methods;
      String jsName;
   }

   static class Layer {
      RecordArray fileMapping;
      RecordArray classMapping;
      RecordArray methodMapping;
      RecordArray lineMapping;
   }

   static class LocationList extends AbstractList<GeneratedLocation> implements RandomAccess {
      private RecordArray recordArray;

      public LocationList(RecordArray recordArray) {
         this.recordArray = recordArray;
      }

      public GeneratedLocation get(int index) {
         RecordArray.Record record = this.recordArray.get(index);
         return new GeneratedLocation(record.get(0), record.get(1));
      }

      @Override
      public int size() {
         return this.recordArray.size();
      }
   }

   class MethodTree {
      int[] data;
      int[] offsets;

      public MethodReference[] getOverridingMethods(int index) {
         if (index >= 0 && index <= this.offsets.length - 1) {
            int start = this.offsets[index];
            int end = this.offsets[index + 1];
            MethodReference[] references = new MethodReference[end - start];

            for (int i = 0; i < references.length; i++) {
               long item = DebugInformation.this.exactMethods[this.data[start + i]];
               int classIndex = (int)(item >>> 32);
               int methodIndex = (int)item;
               references[i] = DebugInformation.this.referenceCache
                  .getCached(
                     DebugInformation.this.classNames[classIndex],
                     DebugInformation.this.referenceCache.parseDescriptorCached(DebugInformation.this.methods[methodIndex])
                  );
            }

            return references;
         } else {
            return new MethodReference[0];
         }
      }
   }
}
