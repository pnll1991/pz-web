package org.teavm.debugging.information;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.teavm.common.IntegerArray;
import org.teavm.common.RecordArray;

class DebugInformationWriter {
   private DataOutput output;
   private int lastNumber;

   public DebugInformationWriter(DataOutput output) {
      this.output = output;
   }

   public void write(DebugInformation debugInfo) throws IOException {
      this.writeStringArray(debugInfo.fileNames);
      this.writeStringArray(debugInfo.classNames);
      this.writeStringArray(debugInfo.fields);
      this.writeStringArray(debugInfo.methods);
      this.writeStringArray(debugInfo.variableNames);
      this.writeExactMethods(debugInfo.exactMethods);
      this.output.write(debugInfo.layers.length);

      for (DebugInformation.Layer layer : debugInfo.layers) {
         this.writeMapping(layer.fileMapping);
         this.writeMapping(layer.lineMapping);
         this.writeMapping(layer.classMapping);
         this.writeMapping(layer.methodMapping);
      }

      this.writeLinesAndColumns(debugInfo.statementStartMapping);
      this.writeCallSiteMapping(debugInfo.callSiteMapping);
      this.writeVariableMappings(debugInfo);
      this.writeClassMetadata(debugInfo.classesMetadata);
      this.writeCFGs(debugInfo);
   }

   private void writeVariableMappings(DebugInformation debugInfo) throws IOException {
      int lastVar = 0;
      this.writeUnsignedNumber(this.nonNullVariableMappings(debugInfo));

      for (int i = 0; i < debugInfo.variableMappings.length; i++) {
         RecordArray mapping = debugInfo.variableMappings[i];
         if (mapping != null) {
            this.writeUnsignedNumber(i - lastVar);
            lastVar = i;
            this.writeMultiMapping(mapping);
         }
      }
   }

   private void writeClassMetadata(List<DebugInformation.ClassMetadata> classes) throws IOException {
      for (int i = 0; i < classes.size(); i++) {
         DebugInformation.ClassMetadata cls = classes.get(i);
         this.writeNullableString(cls.jsName);
         this.writeUnsignedNumber(cls.parentId != null ? cls.parentId + 1 : 0);
         this.writeUnsignedNumber(cls.fieldMap.size());
         List<Integer> keys = new ArrayList<>(cls.fieldMap.keySet());
         Collections.sort(keys);
         this.resetRelativeNumber();

         for (int key : keys) {
            this.writeRelativeNumber(key);
            this.writeUnsignedNumber(cls.fieldMap.get(key));
         }
      }
   }

   private int nonNullVariableMappings(DebugInformation debugInfo) {
      int count = 0;

      for (int i = 0; i < debugInfo.variableMappings.length; i++) {
         if (debugInfo.variableMappings[i] != null) {
            count++;
         }
      }

      return count;
   }

   private void writeStringArray(String[] array) throws IOException {
      this.writeUnsignedNumber(array.length);

      for (int i = 0; i < array.length; i++) {
         this.writeString(array[i]);
      }
   }

   private void writeExactMethods(long[] array) throws IOException {
      int lastClass = 0;
      int lastMethod = 0;
      this.writeUnsignedNumber(array.length);

      for (int i = 0; i < array.length; i++) {
         long item = array[i];
         int classIndex = (int)(item >> 32);
         int methodIndex = (int)item;
         this.writeNumber(classIndex - lastClass);
         lastClass = classIndex;
         this.writeNumber(methodIndex - lastMethod);
         lastMethod = methodIndex;
      }
   }

   private void writeMultiMapping(RecordArray mapping) throws IOException {
      this.writeLinesAndColumns(mapping);

      for (int i = 0; i < mapping.size(); i++) {
         int[] array = mapping.get(i).getArray(0);
         this.writeUnsignedNumber(array.length);
         int lastNumber = 0;

         for (int elem : array) {
            this.writeNumber(elem - lastNumber);
            lastNumber = elem;
         }
      }
   }

   private void writeMapping(RecordArray mapping) throws IOException {
      this.writeLinesAndColumns(mapping);
      this.writeRle(this.packValues(mapping));
   }

   private void writeCallSiteMapping(RecordArray mapping) throws IOException {
      this.writeLinesAndColumns(mapping);
      this.writeRle(this.packValues(mapping));
      this.writeRle(this.packCallSites(mapping));
   }

   private void writeLinesAndColumns(RecordArray mapping) throws IOException {
      this.writeUnsignedNumber(mapping.size());
      this.writeRle(this.packLines(mapping));
      this.writeRle(this.packColumns(mapping));
   }

   private int[] packLines(RecordArray mapping) {
      int[] lines = mapping.cut(0);
      int last = 0;

      for (int i = 0; i < lines.length; i++) {
         int next = lines[i];
         lines[i] -= last;
         last = next;
      }

      return lines;
   }

   private int[] packColumns(RecordArray mapping) {
      int[] columns = mapping.cut(1);
      int lastLine = -1;
      int lastColumn = 0;

      for (int i = 0; i < columns.length; i++) {
         if (lastLine != mapping.get(i).get(0)) {
            lastColumn = 0;
            lastLine = mapping.get(i).get(0);
         }

         int column = columns[i];
         columns[i] = column - lastColumn;
         lastColumn = column;
      }

      return columns;
   }

   private int[] packValues(RecordArray mapping) {
      int[] values = mapping.cut(2);
      int last = 0;

      for (int i = 0; i < values.length; i++) {
         int value = values[i];
         if (value == -1) {
            values[i] = 0;
         } else {
            values[i] = 1 + this.convertToSigned(value - last);
            last = value;
         }
      }

      return values;
   }

   private int[] packCallSites(RecordArray mapping) {
      int[] callSites = mapping.cut(3);
      int last = 0;
      int j = 0;

      for (int i = 0; i < callSites.length; i++) {
         int type = mapping.get(i).get(2);
         if (type != 0) {
            int callSite = callSites[i];
            callSites[j++] = this.convertToSigned(callSite - last);
            last = callSite;
         }
      }

      return Arrays.copyOf(callSites, j);
   }

   private void writeCFGs(DebugInformation debugInfo) throws IOException {
      for (int i = 0; i < debugInfo.controlFlowGraphs.length; i++) {
         this.writeCFG(debugInfo.controlFlowGraphs[i]);
      }
   }

   private void writeCFG(RecordArray mapping) throws IOException {
      if (mapping == null) {
         this.writeUnsignedNumber(0);
      } else {
         this.writeUnsignedNumber(mapping.size());
         this.writeRle(mapping.cut(0));
         IntegerArray sizes = new IntegerArray(1);
         IntegerArray files = new IntegerArray(1);
         IntegerArray lines = new IntegerArray(1);
         int lastFile = 0;
         int lastLine = 0;

         for (int i = 0; i < mapping.size(); i++) {
            int type = mapping.get(i).get(0);
            if (type != 0) {
               int[] data = mapping.get(i).getArray(0);
               sizes.add(data.length / 2);

               for (int j = 0; j < data.length; j += 2) {
                  int file = data[j];
                  int line = data[j + 1];
                  files.add(this.convertToSigned(file - lastFile));
                  lines.add(this.convertToSigned(line - lastLine));
                  lastFile = file;
                  lastLine = line;
               }
            }
         }

         this.writeRle(sizes.getAll());
         this.writeRle(files.getAll());
         this.writeRle(lines.getAll());
      }
   }

   private void writeNumber(int number) throws IOException {
      this.writeUnsignedNumber(this.convertToSigned(number));
   }

   private int convertToSigned(int number) {
      return number < 0 ? -number << 1 | 1 : number << 1;
   }

   private void writeUnsignedNumber(int number) throws IOException {
      do {
         byte b = (byte)(number & 127);
         if ((number & -128) != 0) {
            b = (byte)(b | 128);
         }

         number >>>= 7;
         this.output.writeByte(b);
      } while (number != 0);
   }

   private void writeRle(int[] array) throws IOException {
      int last = 0;
      int i = 0;

      while (i < array.length) {
         int e = array[i];
         int count = 1;
         int current = i++;

         while (i < array.length && array[i] == e) {
            count++;
            i++;
         }

         if (count > 1) {
            if (current > last) {
               this.writeUnsignedNumber(current - last << 1);

               while (last < current) {
                  this.writeUnsignedNumber(array[last++]);
               }
            }

            this.writeUnsignedNumber(count << 1 | 1);
            this.writeUnsignedNumber(e);
            last = i;
         }
      }

      if (array.length > last) {
         this.writeUnsignedNumber(array.length - last << 1);

         while (last < array.length) {
            this.writeUnsignedNumber(array[last++]);
         }
      }
   }

   private void writeRelativeNumber(int number) throws IOException {
      this.writeNumber(number - this.lastNumber);
      this.lastNumber = number;
   }

   private void resetRelativeNumber() {
      this.lastNumber = 0;
   }

   private void writeString(String str) throws IOException {
      byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
      this.writeUnsignedNumber(bytes.length);
      this.output.write(bytes);
   }

   private void writeNullableString(String str) throws IOException {
      if (str == null) {
         this.writeUnsignedNumber(0);
      } else {
         byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
         this.writeUnsignedNumber(bytes.length + 1);
         this.output.write(bytes);
      }
   }
}
