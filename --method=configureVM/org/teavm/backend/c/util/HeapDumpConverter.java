package org.teavm.backend.c.util;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.teavm.common.json.JsonAllErrorVisitor;
import org.teavm.common.json.JsonArrayVisitor;
import org.teavm.common.json.JsonErrorReporter;
import org.teavm.common.json.JsonObjectVisitor;
import org.teavm.common.json.JsonParser;
import org.teavm.common.json.JsonPropertyVisitor;
import org.teavm.common.json.JsonVisitingConsumer;
import org.teavm.common.json.JsonVisitor;
import org.teavm.hppc.LongArrayList;
import org.teavm.hppc.LongObjectHashMap;
import org.teavm.hppc.LongObjectMap;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;

public final class HeapDumpConverter {
   private static byte[] buffer = new byte[8];
   private static int idSize;
   private static JsonVisitor pointerSizeVisitor = new JsonAllErrorVisitor() {
      @Override
      public void intValue(JsonErrorReporter reporter, long value) {
         HeapDumpConverter.idSize = (int)value;
      }
   };

   private HeapDumpConverter() {
   }

   public static void main(String[] args) throws IOException {
      if (args.length != 2) {
         System.err.println("Converts TeaVM/C heap dump into HotSpot compatible format (hprof)");
         System.err.println("Two arguments expected: input file (JSON), output file (hprof)");
         System.exit(-1);
      }

      HeapDumpConverter.SymbolTable symbolTable;
      try (Reader reader = createReader(args[0])) {
         symbolTable = fillSymbolTable(reader);
      }

      try (
         Reader reader = createReader(args[0]);
         RandomAccessFile output = new RandomAccessFile(args[1], "rw");
      ) {
         generateHprofFile(reader, output, symbolTable);
      }
   }

   private static Reader createReader(String fileName) throws IOException {
      return new InputStreamReader(new BufferedInputStream(new FileInputStream(fileName)), StandardCharsets.UTF_8);
   }

   private static HeapDumpConverter.SymbolTable fillSymbolTable(Reader reader) throws IOException {
      HeapDumpConverter.SymbolTable symbolTable = new HeapDumpConverter.SymbolTable();
      JsonPropertyVisitor rootObjectVisitor = new JsonPropertyVisitor(true);
      rootObjectVisitor.addProperty("pointerSize", pointerSizeVisitor);
      rootObjectVisitor.addProperty("classes", new JsonArrayVisitor(new HeapDumpConverter.SymbolTableClassVisitor(symbolTable)));
      rootObjectVisitor.addProperty("stack", new JsonArrayVisitor(new HeapDumpConverter.SymbolTableStackVisitor(symbolTable)));
      JsonParser parser = new JsonParser(new JsonVisitingConsumer(new JsonObjectVisitor(rootObjectVisitor)));
      parser.parse(reader);
      if (symbolTable.classLoaderClassId == 0L) {
         addFakeClass(1L, symbolTable, "java.lang.ClassLoader", symbolTable.objectClassId);
      }

      if (symbolTable.referenceClassId == 0L) {
         addFakeClass(2L, symbolTable, "java.lang.ref.Reference", symbolTable.objectClassId);
         symbolTable.referenceClassId = 2L;
      }

      if (symbolTable.weakReferenceClassId == 0L) {
         addFakeClass(3L, symbolTable, "java.lang.ref.WeakReference", symbolTable.referenceClassId);
      }

      if (symbolTable.softReferenceClassId == 0L) {
         addFakeClass(4L, symbolTable, "java.lang.ref.SoftReference", symbolTable.referenceClassId);
      }

      if (symbolTable.phantomReferenceClassId == 0L) {
         addFakeClass(5L, symbolTable, "java.lang.ref.PhantomReference", symbolTable.referenceClassId);
      }

      if (symbolTable.finalReferenceClassId == 0L) {
         addFakeClass(6L, symbolTable, "java.lang.ref.FinalReference", symbolTable.referenceClassId);
      }

      fixClassNames(symbolTable);
      return symbolTable;
   }

   private static void addFakeClass(long id, HeapDumpConverter.SymbolTable symbolTable, String name, long parent) {
      HeapDumpConverter.ClassDescriptor cls = new HeapDumpConverter.ClassDescriptor();
      cls.id = id;
      cls.superClassId = parent;
      cls.name = name;
      symbolTable.classesById.put(cls.id, cls);
      symbolTable.classList.add(cls);
   }

   private static void fixClassNames(HeapDumpConverter.SymbolTable symbolTable) {
      int serialIdGen = 1;
      int anonymousIdGen = 0;

      for (HeapDumpConverter.ClassDescriptor descriptor : symbolTable.classList) {
         if (descriptor.primitiveType == null) {
            descriptor.serialId = serialIdGen++;
            if (descriptor.itemClassId == 0L) {
               if (descriptor.name == null) {
                  while (true) {
                     descriptor.name = "anonymous_" + anonymousIdGen++;
                     if (symbolTable.getClass(descriptor.name) == null) {
                        break;
                     }
                  }
               } else {
                  descriptor.name = descriptor.name.replace('.', '/');
               }
            }
         }
      }

      List<HeapDumpConverter.ClassDescriptor> arrayClasses = new ArrayList<>();

      for (HeapDumpConverter.ClassDescriptor descriptor : symbolTable.classList) {
         if (descriptor.itemClassId != 0L && descriptor.name == null) {
            while (descriptor.itemClassId != 0L) {
               arrayClasses.add(descriptor);
               descriptor = (HeapDumpConverter.ClassDescriptor)symbolTable.classesById.get(descriptor.itemClassId);
               if (descriptor.name != null) {
                  break;
               }
            }

            String name = descriptor.name;
            if (descriptor.primitiveType != null) {
               switch (descriptor.primitiveType) {
                  case OBJECT:
                  case ARRAY:
                     assert false;
                     break;
                  case BOOLEAN:
                     name = "Z";
                     break;
                  case BYTE:
                     name = "B";
                     break;
                  case CHAR:
                     name = "C";
                     break;
                  case SHORT:
                     name = "S";
                     break;
                  case INT:
                     name = "I";
                     break;
                  case LONG:
                     name = "J";
                     break;
                  case FLOAT:
                     name = "F";
                     break;
                  case DOUBLE:
                     name = "D";
               }
            } else if (descriptor.itemClassId == 0L) {
               name = "L" + name + ";";
            }

            for (int i = arrayClasses.size() - 1; i >= 0; i--) {
               name = "[" + name;
               arrayClasses.get(i).name = name;
            }

            arrayClasses.clear();
         }
      }

      for (HeapDumpConverter.ClassDescriptor descriptor : symbolTable.classList) {
         if (descriptor.name != null) {
            symbolTable.lookup(descriptor.name);
            symbolTable.classes.put(descriptor.name, descriptor);
         }
      }
   }

   private static void generateHprofFile(Reader reader, RandomAccessFile output, HeapDumpConverter.SymbolTable symbolTable) throws IOException {
      output.write("JAVA PROFILE 1.0.2\u0000".getBytes(StandardCharsets.UTF_8));
      output.writeInt(idSize);
      output.writeLong(System.currentTimeMillis());
      BufferedFile bufferedOutput = new BufferedFile(output);
      writeSymbols(bufferedOutput, symbolTable);
      writeStack(bufferedOutput, symbolTable);
      writeHeapDump(reader, bufferedOutput, symbolTable);
      bufferedOutput.flush();
      output.write(44);
      output.writeInt(0);
      output.writeInt(0);
      output.setLength(output.getFilePointer());
   }

   private static void writeSymbols(BufferedFile output, HeapDumpConverter.SymbolTable symbolTable) throws IOException {
      List<String> strings = symbolTable.getStrings();

      for (int i = 0; i < strings.size(); i++) {
         byte[] bytes = strings.get(i).getBytes(StandardCharsets.UTF_8);
         output.write(1);
         output.writeInt(0);
         output.writeInt(idSize + bytes.length);
         writeId(output, i + 1);
         output.write(bytes);
      }
   }

   private static void writeStack(BufferedFile output, HeapDumpConverter.SymbolTable symbolTable) throws IOException {
      for (int i = 0; i < symbolTable.stack.size(); i++) {
         HeapDumpConverter.Frame frame = symbolTable.stack.get(i);
         output.write(4);
         output.writeInt(0);
         output.writeInt(4 * idSize + 8);
         writeId(output, i + 1);
         writeId(output, frame.methodName != null ? symbolTable.lookup(frame.methodName) : 0L);
         writeId(output, 0L);
         writeId(output, frame.fileName != null ? symbolTable.lookup(frame.fileName) : 0L);
         int classSerialId = 0;
         if (frame.className != null) {
            HeapDumpConverter.ClassDescriptor cls = symbolTable.getClass(frame.className);
            if (cls != null) {
               classSerialId = cls.serialId;
            }
         }

         output.writeInt(classSerialId);
         output.writeInt(frame.lineNumber);
      }

      output.write(5);
      output.writeInt(0);
      output.writeInt(12 + idSize * symbolTable.stack.size());
      output.writeInt(1);
      output.writeInt(0);
      output.writeInt(symbolTable.stack.size());

      for (int i = 0; i < symbolTable.stack.size(); i++) {
         writeId(output, i + 1);
      }
   }

   private static void writeGcRoots(BufferedFile output, HeapDumpConverter.SymbolTable symbolTable) throws IOException {
      List<HeapDumpConverter.Frame> stack = symbolTable.stack;

      for (int i = 0; i < stack.size(); i++) {
         HeapDumpConverter.Frame frame = stack.get(i);
         if (frame.roots != null) {
            for (long rootId : frame.roots) {
               output.write(3);
               writeId(output, rootId);
               output.writeInt(0);
               output.writeInt(i);
            }
         }
      }

      for (HeapDumpConverter.ClassDescriptor classDescriptor : symbolTable.getClasses()) {
         if (classDescriptor.primitiveType == null) {
            output.write(5);
            writeId(output, classDescriptor.id);
         }
      }
   }

   private static void writeHeapDump(Reader reader, BufferedFile output, HeapDumpConverter.SymbolTable symbolTable) throws IOException {
      for (HeapDumpConverter.ClassDescriptor classDescriptor : symbolTable.getClasses()) {
         if (classDescriptor.primitiveType == null) {
            output.write(2);
            output.writeInt(0);
            output.writeInt(8 + 2 * idSize);
            output.writeInt(classDescriptor.serialId);
            writeId(output, classDescriptor.id);
            output.writeInt(1);
            writeId(output, symbolTable.lookup(classDescriptor.name));
         }
      }

      output.write(12);
      output.writeInt(0);
      output.writeInt(0);
      long mark = output.getFilePointer();
      writeGcRoots(output, symbolTable);
      writeClassObjects(output, symbolTable);
      JsonPropertyVisitor rootPropertyVisitor = new JsonPropertyVisitor(true);
      rootPropertyVisitor.addProperty("objects", new JsonArrayVisitor(new HeapDumpConverter.ObjectDumpVisitor(output, symbolTable)));
      JsonParser parser = new JsonParser(new JsonVisitingConsumer(new JsonObjectVisitor(rootPropertyVisitor)));
      parser.parse(reader);
      long pointerBackup = output.getFilePointer();
      int size = (int)(output.getFilePointer() - mark);
      output.seek(mark - 4L);
      output.writeInt(size);
      output.seek(pointerBackup);
   }

   private static void writeClassObjects(BufferedFile output, HeapDumpConverter.SymbolTable symbolTable) throws IOException {
      for (HeapDumpConverter.ClassDescriptor cls : symbolTable.getClasses()) {
         if (cls.primitiveType == null) {
            writeClassDump(output, symbolTable, cls);
         }
      }
   }

   private static void writeClassDump(BufferedFile output, HeapDumpConverter.SymbolTable symbolTable, HeapDumpConverter.ClassDescriptor cls) throws IOException {
      output.write(32);
      writeId(output, cls.id);
      output.writeInt(1);
      long superClassId = cls.superClassId;
      if (cls.itemClassId != 0L) {
         superClassId = symbolTable.objectClassId;
      }

      writeId(output, superClassId);
      writeId(output, 0L);
      writeId(output, 0L);
      writeId(output, 0L);
      writeId(output, 0L);
      writeId(output, 0L);
      output.writeInt(cls.size);
      output.writeShort(0);
      output.writeShort((short)cls.staticFields.size());
      int dataPtr = 0;

      for (HeapDumpConverter.FieldDescriptor field : cls.staticFields) {
         writeId(output, symbolTable.lookup(field.name));
         output.write(typeToInt(field.type));
         int sz = typeSize(field.type);
         output.write(cls.data, dataPtr, sz);
         dataPtr += sz;
      }

      output.writeShort((short)cls.fields.size());

      for (HeapDumpConverter.FieldDescriptor field : cls.fields) {
         writeId(output, symbolTable.lookup(field.name));
         output.write(typeToInt(field.type));
      }
   }

   private static HeapDumpConverter.Type parseType(JsonErrorReporter errorReporter, String type) {
      switch (type) {
         case "object":
            return HeapDumpConverter.Type.OBJECT;
         case "array":
            return HeapDumpConverter.Type.ARRAY;
         case "boolean":
            return HeapDumpConverter.Type.BOOLEAN;
         case "byte":
            return HeapDumpConverter.Type.BYTE;
         case "char":
            return HeapDumpConverter.Type.CHAR;
         case "short":
            return HeapDumpConverter.Type.SHORT;
         case "int":
            return HeapDumpConverter.Type.INT;
         case "long":
            return HeapDumpConverter.Type.LONG;
         case "float":
            return HeapDumpConverter.Type.FLOAT;
         case "double":
            return HeapDumpConverter.Type.DOUBLE;
         default:
            errorReporter.error("Unknown type: " + type);
            return HeapDumpConverter.Type.OBJECT;
      }
   }

   private static int typeToInt(HeapDumpConverter.Type type) {
      switch (type) {
         case OBJECT:
         case ARRAY:
            return 2;
         case BOOLEAN:
            return 4;
         case BYTE:
            return 8;
         case CHAR:
            return 5;
         case SHORT:
            return 9;
         case INT:
            return 10;
         case LONG:
            return 11;
         case FLOAT:
            return 6;
         case DOUBLE:
            return 7;
         default:
            return 0;
      }
   }

   private static int typeSize(HeapDumpConverter.Type type) {
      switch (type) {
         case OBJECT:
         case ARRAY:
            return idSize;
         case BOOLEAN:
         case BYTE:
            return 1;
         case CHAR:
         case SHORT:
            return 2;
         case INT:
         case FLOAT:
            return 4;
         case LONG:
         case DOUBLE:
            return 8;
         default:
            return 0;
      }
   }

   private static void writeId(BufferedFile out, long id) throws IOException {
      writeLongBytes(out, id, idSize);
   }

   private static void writeLongBytes(BufferedFile out, long v, int size) throws IOException {
      for (int i = size - 1; i >= 0; i--) {
         buffer[i] = (byte)(v & 255L);
         v >>>= 8;
      }

      out.write(buffer, 0, size);
   }

   static byte[] parseData(JsonErrorReporter errorReporter, String data) {
      if (data.length() % 2 != 0) {
         errorReporter.error("Invalid hex sequence");
         return new byte[0];
      }

      byte[] bytes = new byte[data.length() / 2];
      int j = 0;

      for (int i = 0; i < data.length(); i += 2) {
         int b = hexDigit(errorReporter, data.charAt(i)) << 4 | hexDigit(errorReporter, data.charAt(i + 1));
         bytes[j++] = (byte)b;
      }

      return bytes;
   }

   private static int hexDigit(JsonErrorReporter errorReporter, char c) {
      if (c >= '0' && c <= '9') {
         return c - 48;
      }

      if (c >= 'A' && c <= 'F') {
         return c - 65 + 10;
      }

      if (c >= 'a' && c <= 'f') {
         return c - 97 + 10;
      }

      errorReporter.error("Invalid hex sequence");
      return -1;
   }

   static class ClassDescriptor {
      long id;
      String name;
      int serialId;
      HeapDumpConverter.Type primitiveType;
      long itemClassId;
      long superClassId;
      int size;
      List<HeapDumpConverter.FieldDescriptor> fields = new ArrayList<>();
      List<HeapDumpConverter.FieldDescriptor> staticFields = new ArrayList<>();
      byte[] data;
   }

   static class FieldDescriptor {
      String name;
      HeapDumpConverter.Type type;
   }

   static class Frame {
      String className;
      String methodName;
      String fileName;
      int lineNumber = -1;
      long[] roots;
   }

   static class ObjectDumpVisitor extends JsonAllErrorVisitor {
      private BufferedFile output;
      private HeapDumpConverter.SymbolTable symbolTable;
      private JsonPropertyVisitor propertyVisitor = new JsonPropertyVisitor(true);
      private long id;
      private long classId;
      private byte[] data;
      private JsonVisitor idVisitor = new JsonAllErrorVisitor() {
         @Override
         public void intValue(JsonErrorReporter reporter, long value) {
            ObjectDumpVisitor.this.id = value;
         }
      };
      private JsonVisitor classVisitor = new JsonAllErrorVisitor() {
         @Override
         public void intValue(JsonErrorReporter reporter, long value) {
            ObjectDumpVisitor.this.classId = value;
         }
      };
      private JsonVisitor dataVisitor = new JsonAllErrorVisitor() {
         @Override
         public void stringValue(JsonErrorReporter reporter, String value) {
            ObjectDumpVisitor.this.data = HeapDumpConverter.parseData(reporter, value);
         }
      };

      ObjectDumpVisitor(BufferedFile output, HeapDumpConverter.SymbolTable symbolTable) {
         this.output = output;
         this.symbolTable = symbolTable;
         this.propertyVisitor.addProperty("id", this.idVisitor);
         this.propertyVisitor.addProperty("class", this.classVisitor);
         this.propertyVisitor.addProperty("data", this.dataVisitor);
      }

      @Override
      public JsonVisitor object(JsonErrorReporter reporter) {
         this.id = 0L;
         this.classId = 0L;
         this.data = null;
         return this.propertyVisitor;
      }

      @Override
      public void end(JsonErrorReporter reporter) {
         try {
            HeapDumpConverter.ClassDescriptor cls = this.symbolTable.getClassById(this.classId);
            if (cls == null) {
               reporter.error("Unknown class: " + this.classId);
            }

            if (cls.itemClassId == 0L) {
               this.output.write(33);
               HeapDumpConverter.writeId(this.output, this.id);
               this.output.writeInt(1);
               HeapDumpConverter.writeId(this.output, this.classId);
               this.output.writeInt(this.data.length);
               int dataPtr = this.data.length;

               while (cls != null) {
                  for (HeapDumpConverter.FieldDescriptor field : cls.fields) {
                     dataPtr -= HeapDumpConverter.typeSize(field.type);
                  }

                  int ptr = dataPtr;

                  for (HeapDumpConverter.FieldDescriptor field : cls.fields) {
                     int size = HeapDumpConverter.typeSize(field.type);
                     this.output.write(this.data, ptr, size);
                     ptr += size;
                  }

                  cls = cls.superClassId != 0L ? this.symbolTable.getClassById(cls.superClassId) : null;
               }
            } else {
               HeapDumpConverter.ClassDescriptor itemCls = this.symbolTable.getClassById(cls.itemClassId);
               this.output.write(itemCls.primitiveType == null ? 34 : 35);
               HeapDumpConverter.writeId(this.output, this.id);
               this.output.writeInt(1);
               int itemSize = itemCls.primitiveType != null ? HeapDumpConverter.typeSize(itemCls.primitiveType) : HeapDumpConverter.idSize;
               int size = this.data.length / itemSize;
               this.output.writeInt(size);
               if (itemCls.primitiveType == null) {
                  HeapDumpConverter.writeId(this.output, this.classId);
               } else {
                  this.output.write(HeapDumpConverter.typeToInt(itemCls.primitiveType));
               }

               for (int i = 0; i < size; i++) {
                  int ptr = i * itemSize;
                  this.output.write(this.data, ptr, itemSize);
               }
            }
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      }
   }

   static class SymbolTable {
      private List<String> strings = new ArrayList<>();
      private ObjectIntMap<String> stringIndexes = new ObjectIntHashMap();
      List<HeapDumpConverter.ClassDescriptor> classList = new ArrayList<>();
      Map<String, HeapDumpConverter.ClassDescriptor> classes = new LinkedHashMap<>();
      LongObjectMap<HeapDumpConverter.ClassDescriptor> classesById = new LongObjectHashMap();
      List<HeapDumpConverter.Frame> stack = new ArrayList<>();
      long objectClassId;
      long classLoaderClassId;
      long referenceClassId;
      long weakReferenceClassId;
      long softReferenceClassId;
      long finalReferenceClassId;
      long phantomReferenceClassId;

      List<String> getStrings() {
         return this.strings;
      }

      HeapDumpConverter.ClassDescriptor getClass(String name) {
         return this.classes.get(name);
      }

      HeapDumpConverter.ClassDescriptor getClassById(long id) {
         return (HeapDumpConverter.ClassDescriptor)this.classesById.get(id);
      }

      Collection<HeapDumpConverter.ClassDescriptor> getClasses() {
         return this.classList;
      }

      int lookup(String str) {
         int value = this.stringIndexes.getOrDefault(str, -1);
         if (value < 0) {
            value = this.strings.size() + 1;
            this.strings.add(str);
            this.stringIndexes.put(str, value);
         }

         return value;
      }
   }

   static class SymbolTableClassVisitor extends JsonAllErrorVisitor {
      HeapDumpConverter.SymbolTable symbolTable;
      HeapDumpConverter.ClassDescriptor descriptor;
      HeapDumpConverter.FieldDescriptor fieldDescriptor;
      private JsonPropertyVisitor propertyVisitor;
      private List<HeapDumpConverter.FieldDescriptor> fields;
      JsonVisitor idVisitor = new JsonAllErrorVisitor() {
         @Override
         public void intValue(JsonErrorReporter reporter, long value) {
            SymbolTableClassVisitor.this.descriptor.id = value;
         }
      };
      JsonVisitor nameVisitor = new JsonAllErrorVisitor() {
         @Override
         public void stringValue(JsonErrorReporter reporter, String value) {
            SymbolTableClassVisitor.this.descriptor.name = value;
         }
      };
      JsonVisitor superVisitor = new JsonAllErrorVisitor() {
         @Override
         public void intValue(JsonErrorReporter reporter, long value) {
            SymbolTableClassVisitor.this.descriptor.superClassId = value;
         }

         @Override
         public void nullValue(JsonErrorReporter reporter) {
         }
      };
      JsonVisitor sizeVisitor = new JsonAllErrorVisitor() {
         @Override
         public void intValue(JsonErrorReporter reporter, long value) {
            SymbolTableClassVisitor.this.descriptor.size = (int)value;
         }
      };
      JsonVisitor primitiveVisitor = new JsonAllErrorVisitor() {
         @Override
         public void stringValue(JsonErrorReporter reporter, String value) {
            SymbolTableClassVisitor.this.descriptor.primitiveType = HeapDumpConverter.parseType(reporter, value);
         }
      };
      JsonVisitor itemVisitor = new JsonAllErrorVisitor() {
         @Override
         public void intValue(JsonErrorReporter reporter, long value) {
            SymbolTableClassVisitor.this.descriptor.itemClassId = value;
         }
      };
      JsonVisitor fieldsVisitor = new JsonAllErrorVisitor() {
         @Override
         public JsonVisitor array(JsonErrorReporter reporter) {
            SymbolTableClassVisitor.this.fields = SymbolTableClassVisitor.this.descriptor.fields;
            return SymbolTableClassVisitor.this.fieldVisitor;
         }
      };
      JsonVisitor staticFieldsVisitor = new JsonAllErrorVisitor() {
         @Override
         public JsonVisitor array(JsonErrorReporter reporter) {
            SymbolTableClassVisitor.this.fields = SymbolTableClassVisitor.this.descriptor.staticFields;
            return SymbolTableClassVisitor.this.fieldVisitor;
         }
      };
      JsonVisitor fieldNameVisitor = new JsonAllErrorVisitor() {
         @Override
         public void stringValue(JsonErrorReporter reporter, String value) {
            SymbolTableClassVisitor.this.fieldDescriptor.name = value;
            SymbolTableClassVisitor.this.symbolTable.lookup(value);
         }
      };
      JsonVisitor fieldTypeVisitor = new JsonAllErrorVisitor() {
         @Override
         public void stringValue(JsonErrorReporter reporter, String value) {
            SymbolTableClassVisitor.this.fieldDescriptor.type = HeapDumpConverter.parseType(reporter, value);
         }
      };
      JsonVisitor fieldVisitor = new JsonAllErrorVisitor() {
         private JsonPropertyVisitor propertyVisitor = new JsonPropertyVisitor(true);

         {
            this.propertyVisitor.addProperty("name", SymbolTableClassVisitor.this.fieldNameVisitor);
            this.propertyVisitor.addProperty("type", SymbolTableClassVisitor.this.fieldTypeVisitor);
         }

         @Override
         public JsonVisitor object(JsonErrorReporter reporter) {
            SymbolTableClassVisitor.this.fieldDescriptor = new HeapDumpConverter.FieldDescriptor();
            return this.propertyVisitor;
         }

         @Override
         public void end(JsonErrorReporter reporter) {
            if (SymbolTableClassVisitor.this.fieldDescriptor.type == null) {
               reporter.error("Type for this field not specified");
            }

            SymbolTableClassVisitor.this.fields.add(SymbolTableClassVisitor.this.fieldDescriptor);
            SymbolTableClassVisitor.this.fieldDescriptor = null;
         }
      };
      JsonVisitor dataVisitor = new JsonAllErrorVisitor() {
         @Override
         public void stringValue(JsonErrorReporter reporter, String value) {
            SymbolTableClassVisitor.this.descriptor.data = HeapDumpConverter.parseData(reporter, value);
         }
      };

      SymbolTableClassVisitor(HeapDumpConverter.SymbolTable symbolTable) {
         this.symbolTable = symbolTable;
         this.propertyVisitor = new JsonPropertyVisitor(true);
         this.propertyVisitor.addProperty("id", this.idVisitor);
         this.propertyVisitor.addProperty("name", this.nameVisitor);
         this.propertyVisitor.addProperty("super", this.superVisitor);
         this.propertyVisitor.addProperty("size", this.sizeVisitor);
         this.propertyVisitor.addProperty("primitive", this.primitiveVisitor);
         this.propertyVisitor.addProperty("item", this.itemVisitor);
         this.propertyVisitor.addProperty("fields", this.fieldsVisitor);
         this.propertyVisitor.addProperty("staticFields", this.staticFieldsVisitor);
         this.propertyVisitor.addProperty("data", this.dataVisitor);
      }

      @Override
      public JsonVisitor object(JsonErrorReporter reporter) {
         this.descriptor = new HeapDumpConverter.ClassDescriptor();
         return this.propertyVisitor;
      }

      @Override
      public void end(JsonErrorReporter reporter) {
         if (this.descriptor.id == 0L) {
            reporter.error("Required property 'id' was not set");
         }

         this.symbolTable.classList.add(this.descriptor);
         if (this.symbolTable.classesById.put(this.descriptor.id, this.descriptor) != null) {
            reporter.error("Duplicate class id: " + this.descriptor.id);
         }

         if (this.descriptor.name != null) {
            switch (this.descriptor.name) {
               case "java.lang.Object":
                  this.symbolTable.objectClassId = this.descriptor.id;
                  break;
               case "java.lang.ClassLoader":
                  this.symbolTable.classLoaderClassId = this.descriptor.id;
                  break;
               case "java.lang.ref.Reference":
                  this.symbolTable.referenceClassId = this.descriptor.id;
                  break;
               case "java.lang.ref.WeakReference":
                  this.symbolTable.weakReferenceClassId = this.descriptor.id;
                  break;
               case "java.lang.ref.SoftReference":
                  this.symbolTable.softReferenceClassId = this.descriptor.id;
                  break;
               case "java.lang.ref.PhantomReference":
                  this.symbolTable.phantomReferenceClassId = this.descriptor.id;
                  break;
               case "java.lang.ref.FinalReference":
                  this.symbolTable.finalReferenceClassId = this.descriptor.id;
            }
         }
      }
   }

   static class SymbolTableStackVisitor extends JsonAllErrorVisitor {
      HeapDumpConverter.SymbolTable symbolTable;
      private JsonPropertyVisitor propertyVisitor = new JsonPropertyVisitor(true);
      private HeapDumpConverter.Frame frame;
      private LongArrayList roots = new LongArrayList();
      JsonVisitor fileVisitor = new JsonAllErrorVisitor() {
         @Override
         public void stringValue(JsonErrorReporter reporter, String value) {
            SymbolTableStackVisitor.this.symbolTable.lookup(value);
            SymbolTableStackVisitor.this.frame.fileName = value;
         }
      };
      JsonVisitor classVisitor = new JsonAllErrorVisitor() {
         @Override
         public void stringValue(JsonErrorReporter reporter, String value) {
            SymbolTableStackVisitor.this.symbolTable.lookup(value);
            SymbolTableStackVisitor.this.frame.className = value;
         }
      };
      JsonVisitor methodVisitor = new JsonAllErrorVisitor() {
         @Override
         public void stringValue(JsonErrorReporter reporter, String value) {
            SymbolTableStackVisitor.this.symbolTable.lookup(value);
            SymbolTableStackVisitor.this.frame.methodName = value;
         }
      };
      JsonVisitor lineVisitor = new JsonAllErrorVisitor() {
         @Override
         public void intValue(JsonErrorReporter reporter, long value) {
            SymbolTableStackVisitor.this.frame.lineNumber = (int)value;
         }
      };
      JsonVisitor rootsVisitor = new JsonAllErrorVisitor() {
         @Override
         public void intValue(JsonErrorReporter reporter, long value) {
            SymbolTableStackVisitor.this.roots.add(value);
         }
      };

      SymbolTableStackVisitor(HeapDumpConverter.SymbolTable symbolTable) {
         this.symbolTable = symbolTable;
         this.propertyVisitor.addProperty("file", this.fileVisitor);
         this.propertyVisitor.addProperty("class", this.classVisitor);
         this.propertyVisitor.addProperty("method", this.methodVisitor);
         this.propertyVisitor.addProperty("line", this.lineVisitor);
         this.propertyVisitor.addProperty("roots", new JsonArrayVisitor(this.rootsVisitor));
      }

      @Override
      public JsonVisitor object(JsonErrorReporter reporter) {
         this.frame = new HeapDumpConverter.Frame();
         return this.propertyVisitor;
      }

      @Override
      public void end(JsonErrorReporter reporter) {
         if (!this.roots.isEmpty()) {
            this.frame.roots = this.roots.toArray();
            this.roots.clear();
         }

         this.symbolTable.stack.add(this.frame);
      }
   }

   enum Type {
      OBJECT,
      ARRAY,
      BOOLEAN,
      BYTE,
      CHAR,
      SHORT,
      INT,
      LONG,
      FLOAT,
      DOUBLE;
   }
}
