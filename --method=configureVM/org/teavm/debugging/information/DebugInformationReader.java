package org.teavm.debugging.information;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.teavm.common.RecordArray;
import org.teavm.common.RecordArrayBuilder;
import org.teavm.model.ReferenceCache;

class DebugInformationReader {
   private InputStream input;
   private int lastNumber;
   private ReferenceCache referenceCache;

   DebugInformationReader(InputStream input, ReferenceCache referenceCache) {
      this.input = input;
      this.referenceCache = referenceCache;
   }

   public DebugInformation read() throws IOException {
      DebugInformation debugInfo = new DebugInformation(this.referenceCache);
      debugInfo.fileNames = this.readStrings();
      debugInfo.classNames = this.readStrings();
      debugInfo.fields = this.readStrings();
      debugInfo.methods = this.readStrings();
      debugInfo.variableNames = this.readStrings();
      debugInfo.exactMethods = this.readExactMethods();
      debugInfo.layers = new DebugInformation.Layer[this.input.read()];

      for (int i = 0; i < debugInfo.layers.length; i++) {
         DebugInformation.Layer layer = new DebugInformation.Layer();
         layer.fileMapping = this.readMapping();
         layer.lineMapping = this.readMapping();
         layer.classMapping = this.readMapping();
         layer.methodMapping = this.readMapping();
         debugInfo.layers[i] = layer;
      }

      debugInfo.statementStartMapping = this.readBooleanMapping();
      debugInfo.callSiteMapping = this.readCallSiteMapping();
      debugInfo.variableMappings = this.readVariableMappings(debugInfo.variableNames.length);
      debugInfo.classesMetadata = this.readClassesMetadata(debugInfo.classNames.length);
      debugInfo.controlFlowGraphs = this.readCFGs(debugInfo.fileNames.length);
      debugInfo.rebuild();
      return debugInfo;
   }

   private RecordArray[] readVariableMappings(int count) throws IOException {
      RecordArray[] mappings = new RecordArray[count];
      int varCount = this.readUnsignedNumber();

      for (int lastVar = 0; varCount-- > 0; mappings[lastVar] = this.readMultiMapping()) {
         lastVar += this.readUnsignedNumber();
      }

      return mappings;
   }

   private List<DebugInformation.ClassMetadata> readClassesMetadata(int count) throws IOException {
      List<DebugInformation.ClassMetadata> classes = new ArrayList<>(count);

      for (int i = 0; i < count; i++) {
         DebugInformation.ClassMetadata cls = new DebugInformation.ClassMetadata();
         classes.add(cls);
         cls.id = i;
         cls.jsName = this.readNullableString();
         cls.parentId = this.readUnsignedNumber() - 1;
         if (cls.parentId.equals(-1)) {
            cls.parentId = null;
         }

         int entryCount = this.readUnsignedNumber();
         this.resetRelativeNumber();

         for (int j = 0; j < entryCount; j++) {
            int key = this.readRelativeNumber();
            int value = this.readUnsignedNumber();
            cls.fieldMap.put(key, value);
         }
      }

      return classes;
   }

   private RecordArray[] readCFGs(int count) throws IOException {
      RecordArray[] cfgs = new RecordArray[count];

      for (int i = 0; i < count; i++) {
         cfgs[i] = this.readCFG();
      }

      return cfgs;
   }

   private RecordArray readCFG() throws IOException {
      RecordArrayBuilder builder = new RecordArrayBuilder(1, 1);
      int size = this.readUnsignedNumber();

      for (int i = 0; i < size; i++) {
         builder.add();
      }

      int[] types = this.readRle(size);
      int nonEmptyItems = 0;

      for (int i = 0; i < size; i++) {
         int type = types[i];
         builder.get(i).set(0, type);
         if (type != 0) {
            nonEmptyItems++;
         }
      }

      int[] sizes = this.readRle(nonEmptyItems);
      int j = 0;
      int totalSize = 0;

      for (int sz : sizes) {
         totalSize += sz;
      }

      int[] files = this.readRle(totalSize);
      int[] lines = this.readRle(totalSize);
      int lastFile = 0;
      int lastLine = 0;
      int index = 0;

      for (int i = 0; i < sizes.length; i++) {
         while (types[j] == 0) {
            j++;
         }

         size = sizes[i];
         RecordArrayBuilder.SubArray array = builder.get(j++).getArray(0);

         for (int k = 0; k < size; k++) {
            lastFile += this.processSign(files[index]);
            lastLine += this.processSign(lines[index]);
            array.add(lastFile);
            array.add(lastLine);
            index++;
         }
      }

      return builder.build();
   }

   private int processSign(int number) {
      boolean negative = (number & 1) != 0;
      number >>>= 1;
      return !negative ? number : -number;
   }

   private RecordArray readMultiMapping() throws IOException {
      RecordArrayBuilder builder = this.readLinesAndColumns(2, 1);

      for (int i = 0; i < builder.size(); i++) {
         int count = this.readUnsignedNumber();
         RecordArrayBuilder.SubArray array = builder.get(i).getArray(0);
         int last = 0;

         for (int j = 0; j < count; j++) {
            last += this.readNumber();
            array.add(last);
         }
      }

      return builder.build();
   }

   private RecordArray readBooleanMapping() throws IOException {
      RecordArrayBuilder builder = this.readLinesAndColumns(2, 0);
      return builder.build();
   }

   private RecordArray readMapping() throws IOException {
      RecordArrayBuilder builder = this.readLinesAndColumns(3, 0);
      this.readValues(builder);
      return builder.build();
   }

   private RecordArray readCallSiteMapping() throws IOException {
      RecordArrayBuilder builder = this.readLinesAndColumns(4, 0);
      this.readValues(builder);
      this.readCallSites(builder);
      return builder.build();
   }

   private RecordArrayBuilder readLinesAndColumns(int fields, int arrays) throws IOException {
      RecordArrayBuilder builder = new RecordArrayBuilder(fields, arrays);
      int size = this.readUnsignedNumber();

      for (int i = 0; i < size; i++) {
         builder.add();
      }

      int[] lines = this.extractLines(this.readRle(builder.size()));
      int[] columns = this.extractColumns(this.readRle(builder.size()), lines);

      for (int i = 0; i < builder.size(); i++) {
         RecordArrayBuilder.Record record = builder.get(i);
         record.set(0, lines[i]);
         record.set(1, columns[i]);
      }

      return builder;
   }

   private void readValues(RecordArrayBuilder builder) throws IOException {
      int[] values = this.extractValues(this.readRle(builder.size()));

      for (int i = 0; i < builder.size(); i++) {
         builder.get(i).set(2, values[i]);
      }
   }

   private void readCallSites(RecordArrayBuilder builder) throws IOException {
      int sz = 0;

      for (int i = 0; i < builder.size(); i++) {
         if (builder.get(i).get(2) != 0) {
            sz++;
         }
      }

      int[] data = this.readRle(sz);
      int j = 0;
      int last = 0;

      for (int i = 0; i < builder.size(); i++) {
         if (builder.get(i).get(2) != 0) {
            last += this.processSign(data[j++]);
            builder.get(i).set(3, last);
         }
      }
   }

   private int[] extractLines(int[] lines) {
      int last = 0;

      for (int i = 0; i < lines.length; i++) {
         last += lines[i];
         lines[i] = last;
      }

      return lines;
   }

   private int[] extractColumns(int[] columns, int[] lines) {
      int last = 0;
      int lastLine = -1;

      for (int i = 0; i < columns.length; i++) {
         if (lines[i] != lastLine) {
            lastLine = lines[i];
            last = 0;
         }

         last += columns[i];
         columns[i] = last;
      }

      return columns;
   }

   private int[] extractValues(int[] values) {
      int last = 0;

      for (int i = 0; i < values.length; i++) {
         int value = values[i];
         if (value == 0) {
            values[i] = -1;
         } else {
            last += this.processSign(value - 1);
            values[i] = last;
         }
      }

      return values;
   }

   private String[] readStrings() throws IOException {
      String[] array = new String[this.readUnsignedNumber()];

      for (int i = 0; i < array.length; i++) {
         array[i] = this.readString();
      }

      return array;
   }

   private long[] readExactMethods() throws IOException {
      long[] result = new long[this.readUnsignedNumber()];
      int lastClass = 0;
      int lastMethod = 0;

      for (int i = 0; i < result.length; i++) {
         lastClass += this.readNumber();
         lastMethod += this.readNumber();
         result[i] = (long)lastClass << 32 | lastMethod;
      }

      return result;
   }

   private int[] readRle(int size) throws IOException {
      int[] array = new int[size];
      int i = 0;

      while (i < size) {
         int count = this.readUnsignedNumber();
         boolean repeat = (count & 1) != 0;
         count >>>= 1;
         if (!repeat) {
            while (count-- > 0) {
               array[i++] = this.readUnsignedNumber();
            }
         } else {
            int n = this.readUnsignedNumber();

            while (count-- > 0) {
               array[i++] = n;
            }
         }
      }

      return array;
   }

   private int readNumber() throws IOException {
      return this.processSign(this.readUnsignedNumber());
   }

   private int readUnsignedNumber() throws IOException {
      int number = 0;
      int shift = 0;

      byte b;
      do {
         int r = this.input.read();
         if (r < 0) {
            throw new EOFException();
         }

         b = (byte)r;
         number |= (b & 127) << shift;
         shift += 7;
      } while ((b & 128) != 0);

      return number;
   }

   private int readRelativeNumber() throws IOException {
      this.lastNumber = this.lastNumber + this.readNumber();
      return this.lastNumber;
   }

   private void resetRelativeNumber() {
      this.lastNumber = 0;
   }

   private String readString() throws IOException {
      return this.readStringChars(this.readUnsignedNumber());
   }

   private String readNullableString() throws IOException {
      int size = this.readUnsignedNumber();
      return size > 0 ? this.readStringChars(size - 1) : null;
   }

   private String readStringChars(int size) throws IOException {
      byte[] bytes = new byte[size];
      int pos = 0;

      while (pos < bytes.length) {
         int read = this.input.read(bytes, pos, bytes.length - pos);
         if (read == -1) {
            throw new EOFException();
         }

         pos += read;
      }

      return new String(bytes, StandardCharsets.UTF_8);
   }
}
