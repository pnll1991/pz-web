package org.teavm.backend.wasm.generate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.teavm.backend.wasm.debug.info.VariableType;
import org.teavm.backend.wasm.dwarf.DwarfAbbreviation;
import org.teavm.backend.wasm.dwarf.DwarfInfoWriter;
import org.teavm.backend.wasm.dwarf.DwarfPlaceholder;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.common.binary.Blob;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.PrimitiveType;
import org.teavm.model.ValueType;

public class DwarfClassGenerator {
   private static final ValueType objectType = ValueType.object("java.lang.Object");
   final DwarfClassGenerator.Namespace root = new DwarfClassGenerator.Namespace(null);
   final Map<String, DwarfClassGenerator.Subprogram> subprogramsByFunctionName = new HashMap<>();
   final List<DwarfClassGenerator.Subprogram> rootSubprograms = new ArrayList<>();
   private final DwarfInfoWriter writer;
   private final DwarfStrings strings;
   private DwarfAbbreviation nsAbbrev;
   private DwarfAbbreviation classTypeAbbrev;
   private DwarfAbbreviation inheritanceAbbrev;
   private DwarfAbbreviation immutableMemberAbbrev;
   private DwarfAbbreviation memberAbbrev;
   private DwarfPlaceholder[] primitiveTypes = new DwarfPlaceholder[PrimitiveType.values().length];
   private DwarfPlaceholder unspecifiedType;
   private DwarfAbbreviation baseTypeAbbrev;
   private DwarfAbbreviation pointerAbbrev;
   private DwarfAbbreviation variableAbbrev;
   private List<Runnable> postponedWrites = new ArrayList<>();
   private DwarfClassGenerator.ClassType classClass;
   private DwarfFunctionGenerator functionGen;
   private DwarfPlaceholder fakeClassPtrStruct;
   private Queue<DwarfClassGenerator.Subprogram> subprogramsToPrepare = new ArrayDeque<>();

   public DwarfClassGenerator(DwarfInfoWriter writer, DwarfStrings strings) {
      this.writer = writer;
      this.strings = strings;
      this.functionGen = new DwarfFunctionGenerator(this, writer, strings);
      this.fakeClassPtrStruct = writer.placeholder(4);
   }

   private void createFakeClassPtrStruct() {
      DwarfAbbreviation abbrev = this.writer.abbreviation(19, true, data -> data.writeLEB(80).writeLEB(24));
      this.writer.mark(this.fakeClassPtrStruct).tag(abbrev);
      Blob ops = new Blob();
      ops.writeByte(151).writeByte(6).writeByte(51).writeByte(36);
      this.writer.writeLEB(ops.size());
      ops.newReader(this.writer::write).readRemaining();
      this.writer.tag(this.getImmutableMemberAbbrev());
      this.writer.writeInt(this.strings.stringRef("value"));
      this.writer.ref(this.classClass.getPointerPtr(), Blob::writeInt);
      this.writer.writeShort(0);
      this.writer.emptyTag();
   }

   public void flushTypes() {
      for (Runnable postponedWrite : this.postponedWrites) {
         postponedWrite.run();
      }

      this.postponedWrites.clear();
   }

   public DwarfClassGenerator.ClassType getClass(String fullName) {
      int index = 0;
      DwarfClassGenerator.Namespace ns = this.root;

      while (true) {
         int next = fullName.indexOf(46, index);
         if (next < 0) {
            DwarfClassGenerator.ClassType cls = ns.getClass(fullName.substring(index));
            if (fullName.equals("java.lang.Object")) {
               cls.isRoot = true;
            }

            return cls;
         }

         ns = ns.getNamespace(fullName.substring(index, next));
         index = next + 1;
      }
   }

   public void registerSubprogram(String functionName, DwarfClassGenerator.Subprogram subprogram) {
      this.subprogramsByFunctionName.put(functionName, subprogram);
      this.subprogramsToPrepare.add(subprogram);
   }

   public DwarfClassGenerator.Subprogram getSubprogram(String functionName) {
      return this.subprogramsByFunctionName.get(functionName);
   }

   public void write() {
      this.classClass = this.getClass("java.lang.Class");
      this.createFakeClassPtrStruct();

      for (DwarfClassGenerator.Subprogram subprogram : this.rootSubprograms) {
         subprogram.prepare();
      }

      while (!this.subprogramsToPrepare.isEmpty()) {
         this.subprogramsToPrepare.remove().prepare();
      }

      this.root.writeChildren();

      for (DwarfClassGenerator.Subprogram subprogram : this.rootSubprograms) {
         subprogram.write();
      }

      this.flushTypes();
   }

   private DwarfAbbreviation getNsAbbrev() {
      if (this.nsAbbrev == null) {
         this.nsAbbrev = this.writer.abbreviation(57, true, data -> data.writeLEB(3).writeLEB(14));
      }

      return this.nsAbbrev;
   }

   private DwarfAbbreviation getClassTypeAbbrev() {
      if (this.classTypeAbbrev == null) {
         this.classTypeAbbrev = this.writer.abbreviation(2, true, data -> {
            data.writeLEB(3).writeLEB(14);
            data.writeLEB(11).writeLEB(5);
         });
      }

      return this.classTypeAbbrev;
   }

   private DwarfAbbreviation getInheritanceAbbrev() {
      if (this.inheritanceAbbrev == null) {
         this.inheritanceAbbrev = this.writer.abbreviation(28, false, data -> data.writeLEB(73).writeLEB(19));
      }

      return this.inheritanceAbbrev;
   }

   private DwarfAbbreviation getImmutableMemberAbbrev() {
      if (this.immutableMemberAbbrev == null) {
         this.immutableMemberAbbrev = this.writer.abbreviation(13, false, data -> {
            data.writeLEB(3).writeLEB(14);
            data.writeLEB(73).writeLEB(19);
            data.writeLEB(56).writeLEB(5);
         });
      }

      return this.immutableMemberAbbrev;
   }

   private DwarfAbbreviation getMemberAbbrev() {
      if (this.memberAbbrev == null) {
         this.memberAbbrev = this.writer.abbreviation(13, false, data -> {
            data.writeLEB(3).writeLEB(14);
            data.writeLEB(73).writeLEB(19);
            data.writeLEB(56).writeLEB(5);
         });
      }

      return this.memberAbbrev;
   }

   public DwarfPlaceholder getTypePtr(VariableType type) {
      switch (type) {
         case INT:
            return this.getPrimitivePtr(ValueType.Primitive.INTEGER);
         case LONG:
            return this.getPrimitivePtr(ValueType.Primitive.LONG);
         case FLOAT:
            return this.getPrimitivePtr(ValueType.Primitive.FLOAT);
         case DOUBLE:
            return this.getPrimitivePtr(ValueType.Primitive.DOUBLE);
         default:
            return this.getTypePtr(objectType);
      }
   }

   public DwarfPlaceholder getTypePtr(ValueType type) {
      if (type instanceof ValueType.Primitive) {
         return this.getPrimitivePtr((ValueType.Primitive)type);
      } else {
         return type instanceof ValueType.Object ? this.getClassType(((ValueType.Object)type).getClassName()) : this.getClassType("java.lang.Object");
      }
   }

   private DwarfPlaceholder getUnspecifiedType() {
      if (this.unspecifiedType == null) {
         this.unspecifiedType = this.writer.placeholder(4);
         DwarfAbbreviation abbrev = this.writer.abbreviation(59, false, blob -> blob.writeInt(3).writeInt(14));
         this.writer.mark(this.unspecifiedType).tag(abbrev).writeInt(this.strings.stringRef("<unspecified>"));
      }

      return this.unspecifiedType;
   }

   private DwarfPlaceholder getClassType(String name) {
      return this.getClass(name).getPointerPtr();
   }

   private DwarfPlaceholder getPrimitivePtr(ValueType.Primitive type) {
      DwarfPlaceholder result = this.primitiveTypes[type.getKind().ordinal()];
      if (result == null) {
         String name;
         int byteSize;
         int encoding;
         switch (type.getKind()) {
            case BOOLEAN:
               name = "boolean";
               byteSize = 1;
               encoding = 2;
               break;
            case BYTE:
               name = "byte";
               byteSize = 1;
               encoding = 5;
               break;
            case SHORT:
               name = "short";
               byteSize = 2;
               encoding = 5;
               break;
            case CHARACTER:
               name = "char";
               byteSize = 2;
               encoding = 16;
               break;
            case INTEGER:
               name = "int";
               byteSize = 4;
               encoding = 5;
               break;
            case LONG:
               name = "long";
               byteSize = 8;
               encoding = 5;
               break;
            case FLOAT:
               name = "float";
               encoding = 4;
               byteSize = 4;
               break;
            case DOUBLE:
               name = "double";
               encoding = 4;
               byteSize = 8;
               break;
            default:
               throw new IllegalArgumentException();
         }

         DwarfPlaceholder ptr = this.writer.placeholder(4);
         this.postponedWrites.add(() -> {
            this.writer.mark(ptr).tag(this.getBaseTypeAbbrev());
            this.writer.writeInt(this.strings.stringRef(name));
            this.writer.writeByte(byteSize);
            this.writer.writeByte(encoding);
         });
         result = ptr;
         this.primitiveTypes[type.getKind().ordinal()] = ptr;
      }

      return result;
   }

   private DwarfAbbreviation getBaseTypeAbbrev() {
      if (this.baseTypeAbbrev == null) {
         this.baseTypeAbbrev = this.writer.abbreviation(36, false, blob -> {
            blob.writeLEB(3).writeLEB(14);
            blob.writeLEB(11).writeLEB(11);
            blob.writeLEB(62).writeLEB(11);
         });
      }

      return this.baseTypeAbbrev;
   }

   private DwarfAbbreviation getPointerAbbrev() {
      if (this.pointerAbbrev == null) {
         this.pointerAbbrev = this.writer.abbreviation(15, false, blob -> blob.writeLEB(73).writeLEB(19));
      }

      return this.pointerAbbrev;
   }

   private DwarfAbbreviation getVariableAbbrev() {
      if (this.variableAbbrev == null) {
         this.variableAbbrev = this.writer.abbreviation(52, false, blob -> {
            blob.writeLEB(3).writeLEB(14);
            blob.writeLEB(73).writeLEB(19);
            blob.writeLEB(2).writeLEB(24);
         });
      }

      return this.variableAbbrev;
   }

   public class ClassType {
      public final String name;
      boolean isRoot;
      final DwarfPlaceholder ptr;
      private DwarfPlaceholder pointerPtr;
      final Map<MethodDescriptor, DwarfClassGenerator.Subprogram> subprograms = new LinkedHashMap<>();
      List<DwarfClassGenerator.Field> fields;
      private DwarfClassGenerator.ClassType superclass;
      private int size;
      private int pointer = -1;

      private ClassType(String name) {
         this.ptr = DwarfClassGenerator.this.writer.placeholder(4);
         this.name = name;
      }

      public void setSuperclass(DwarfClassGenerator.ClassType superclass) {
         this.superclass = superclass;
      }

      public DwarfClassGenerator.Subprogram getSubprogram(MethodDescriptor desc) {
         return this.subprograms.computeIfAbsent(desc, d -> DwarfClassGenerator.this.new Subprogram(d.getName(), desc));
      }

      public void setSize(int size) {
         this.size = size;
      }

      public void setPointer(int pointer) {
         this.pointer = pointer;
      }

      public void registerField(String name, ValueType type, int offset) {
         this.addField(DwarfClassGenerator.this.new InstanceField(name, type, offset));
      }

      public void registerStaticField(String name, ValueType type, int address) {
         this.addField(DwarfClassGenerator.this.new StaticField(name, type, address));
      }

      private void addField(DwarfClassGenerator.Field field) {
         if (this.fields == null) {
            this.fields = new ArrayList<>();
         }

         this.fields.add(field);
      }

      public DwarfPlaceholder getPointerPtr() {
         if (this.pointerPtr == null) {
            this.pointerPtr = DwarfClassGenerator.this.writer.placeholder(4);
         }

         return this.pointerPtr;
      }

      private void write() {
         DwarfClassGenerator.this.writer.mark(this.ptr).tag(DwarfClassGenerator.this.getClassTypeAbbrev());
         DwarfClassGenerator.this.writer.writeInt(DwarfClassGenerator.this.strings.stringRef(this.name));
         DwarfClassGenerator.this.writer.writeShort(this.size);
         if (this.superclass != null) {
            DwarfClassGenerator.this.writer.tag(DwarfClassGenerator.this.getInheritanceAbbrev()).ref(this.superclass.ptr, Blob::writeInt);
         }

         for (DwarfClassGenerator.Subprogram child : this.subprograms.values()) {
            child.write();
         }

         if (this.pointerPtr != null) {
            DwarfClassGenerator.this.writer.mark(this.pointerPtr).tag(DwarfClassGenerator.this.getPointerAbbrev());
            DwarfClassGenerator.this.writer.ref(this.ptr, Blob::writeInt);
         }

         if (this.pointer >= 0) {
            DwarfClassGenerator.this.writer.tag(DwarfClassGenerator.this.getVariableAbbrev());
            DwarfClassGenerator.this.writer.writeInt(DwarfClassGenerator.this.strings.stringRef("__classData__"));
            DwarfClassGenerator.this.writer.ref(DwarfClassGenerator.this.classClass.getPointerPtr(), Blob::writeInt);
            Blob ops = new Blob();
            ops.writeByte(3).writeInt(this.pointer).writeByte(159);
            DwarfClassGenerator.this.writer.writeLEB(ops.size());
            ops.newReader(DwarfClassGenerator.this.writer::write).readRemaining();
         }

         if (this.isRoot) {
            this.writeClassPointerField();
         }

         this.writeFields();
         DwarfClassGenerator.this.writer.emptyTag();
      }

      private void writeClassPointerField() {
         DwarfClassGenerator.this.writer.tag(DwarfClassGenerator.this.getImmutableMemberAbbrev());
         DwarfClassGenerator.this.writer.writeInt(DwarfClassGenerator.this.strings.stringRef("__class__"));
         DwarfClassGenerator.this.writer.ref(DwarfClassGenerator.this.fakeClassPtrStruct, Blob::writeInt);
         DwarfClassGenerator.this.writer.writeShort(0);
      }

      private void writeFields() {
         if (this.fields != null) {
            for (DwarfClassGenerator.Field field : this.fields) {
               field.write();
            }
         }
      }
   }

   abstract static class Field {
      String name;
      ValueType type;
      int offset;

      Field(String name, ValueType type, int offset) {
         this.name = name;
         this.type = type;
         this.offset = offset;
      }

      abstract void write();
   }

   class InstanceField extends DwarfClassGenerator.Field {
      InstanceField(String name, ValueType type, int offset) {
         super(name, type, offset);
      }

      @Override
      void write() {
         DwarfClassGenerator.this.writer.tag(DwarfClassGenerator.this.getMemberAbbrev());
         DwarfClassGenerator.this.writer.writeInt(DwarfClassGenerator.this.strings.stringRef(this.name));
         DwarfClassGenerator.this.writer.ref(DwarfClassGenerator.this.getTypePtr(this.type), Blob::writeInt);
         DwarfClassGenerator.this.writer.writeShort(this.offset);
      }
   }

   public class Namespace {
      public final String name;
      final Map<String, DwarfClassGenerator.Namespace> namespaces = new LinkedHashMap<>();
      final Map<String, DwarfClassGenerator.ClassType> classes = new LinkedHashMap<>();

      private Namespace(String name) {
         this.name = name;
      }

      private DwarfClassGenerator.Namespace getNamespace(String name) {
         return this.namespaces.computeIfAbsent(name, x$0 -> DwarfClassGenerator.this.new Namespace(x$0));
      }

      private void write() {
         DwarfClassGenerator.this.writer.tag(DwarfClassGenerator.this.getNsAbbrev());
         DwarfClassGenerator.this.writer.writeInt(DwarfClassGenerator.this.strings.stringRef(this.name));
         this.writeChildren();
         DwarfClassGenerator.this.writer.emptyTag();
      }

      private void writeChildren() {
         for (DwarfClassGenerator.Namespace child : this.namespaces.values()) {
            child.write();
         }

         for (DwarfClassGenerator.ClassType child : this.classes.values()) {
            child.write();
         }
      }

      DwarfClassGenerator.ClassType getClass(String name) {
         return this.classes.computeIfAbsent(name, x$0 -> DwarfClassGenerator.this.new ClassType(x$0));
      }
   }

   class StaticField extends DwarfClassGenerator.Field {
      StaticField(String name, ValueType type, int offset) {
         super(name, type, offset);
      }

      @Override
      void write() {
         DwarfClassGenerator.this.writer.tag(DwarfClassGenerator.this.getVariableAbbrev());
         DwarfClassGenerator.this.writer.writeInt(DwarfClassGenerator.this.strings.stringRef(this.name));
         DwarfClassGenerator.this.writer.ref(DwarfClassGenerator.this.getTypePtr(this.type), Blob::writeInt);
         Blob ops = new Blob();
         ops.writeByte(3).writeInt(this.offset);
         DwarfClassGenerator.this.writer.writeLEB(ops.size());
         ops.newReader(DwarfClassGenerator.this.writer::write).readRemaining();
      }
   }

   public class Subprogram {
      public final String name;
      public boolean isStatic;
      public final MethodDescriptor descriptor;
      public int startOffset;
      public int endOffset;
      public WasmFunction function;

      private Subprogram(String name, MethodDescriptor descriptor) {
         this.name = name;
         this.descriptor = descriptor;
      }

      private void prepare() {
         if (DwarfClassGenerator.this.functionGen != null) {
            DwarfClassGenerator.this.functionGen.prepareContent(this);
         }
      }

      private void write() {
         if (this.function != null) {
            DwarfClassGenerator.this.functionGen.writeContent(this);
         }
      }
   }
}
