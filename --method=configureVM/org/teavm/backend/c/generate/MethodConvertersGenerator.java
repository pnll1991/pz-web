package org.teavm.backend.c.generate;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.teavm.model.ValueType;
import org.teavm.vm.BuildTarget;

public class MethodConvertersGenerator {
   private boolean incremental;
   private Map<MethodConvertersGenerator.Signature, String> signatureCache = new HashMap<>();
   private int lastSignatureId;
   private BufferedCodeWriter singleWriter;
   private BufferedCodeWriter headerWriter;
   private CodeWriter writer;
   private IncludeManager includes;
   private IncludeManager classLocalIncludes;

   public MethodConvertersGenerator(boolean incremental, FileNameProvider fileNames) {
      this.incremental = incremental;
      this.singleWriter = new BufferedCodeWriter(false);
      if (!incremental) {
         this.headerWriter = new BufferedCodeWriter(false);
         this.headerWriter.println("#pragma once");
         this.writer = this.singleWriter;
         this.includes = new SimpleIncludeManager(fileNames, this.writer);
         this.includes.init("reflection_signatures.c");
      }
   }

   public void startForClass(CodeWriter writer, IncludeManager includes) {
      if (this.incremental) {
         this.writer = writer.fragment();
         this.includes = includes;
      }

      this.classLocalIncludes = includes;
   }

   public String getSignatureFunction(boolean hasReceiver, ValueType returnType, ValueType[] parameterTypes) {
      if (!this.incremental) {
         this.classLocalIncludes.includePath("reflection_signatures.h");
      }

      return this.signatureCache
         .computeIfAbsent(new MethodConvertersGenerator.Signature(hasReceiver, returnType, parameterTypes), this::createSignatureFunction);
   }

   private String createSignatureFunction(MethodConvertersGenerator.Signature signature) {
      String name = "teavm_reflect_methodConverter_" + this.lastSignatureId++;
      if (this.incremental) {
         this.writer.print("static ");
      }

      this.writer.println("void* " + name + "(void* fn, TeaVM_Object* receiver, TeaVM_Object** args) {").indent();
      this.writeFunctionType(signature, "actualFn");
      this.writer.print(" = (");
      this.writeFunctionType(signature, "");
      this.writer.println(") fn;");
      if (signature.returnType != ValueType.VOID) {
         this.writer.printType(signature.returnType).print(" result = ");
      }

      this.writer.print("(*actualFn)(");
      boolean commaNeeded = false;
      if (signature.hasReceiver()) {
         this.writer.print("receiver");
         commaNeeded = true;
      }

      for (int i = 0; i < signature.parameterTypes.length; i++) {
         if (commaNeeded) {
            this.writer.print(", ");
         }

         commaNeeded = true;
         this.convertArg("args[" + i + "]", signature.parameterTypes[i]);
      }

      this.writer.println(");");
      if (signature.returnType != ValueType.VOID) {
         this.writer.print("return ");
         this.convertReturn(signature.returnType);
         this.writer.println(";");
      } else {
         this.writer.println("return NULL;");
      }

      this.writer.outdent().println("}");
      if (!this.incremental) {
         this.headerWriter.println("extern void* " + name + "(void*, TeaVM_Object*, TeaVM_Object**);");
      }

      return name;
   }

   private void writeFunctionType(MethodConvertersGenerator.Signature signature, String name) {
      this.writer.printType(signature.returnType).print(" (*" + name + ")(");
      boolean commaNeeded = false;
      if (signature.hasReceiver()) {
         this.writer.print("void*");
         commaNeeded = true;
      }

      for (ValueType paramType : signature.parameterTypes) {
         if (commaNeeded) {
            this.writer.print(", ");
         }

         commaNeeded = true;
         this.writer.printType(paramType);
      }

      this.writer.print(")");
   }

   private void convertArg(String arg, ValueType type) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_UNBOX_BOOLEAN(" + arg + ")");
               return;
            case BYTE:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_UNBOX_BYTE(" + arg + ")");
               return;
            case SHORT:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_UNBOX_SHORT(" + arg + ")");
               return;
            case CHARACTER:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_UNBOX_CHAR(" + arg + ")");
               return;
            case INTEGER:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_UNBOX_INT(" + arg + ")");
               return;
            case LONG:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_UNBOX_LONG(" + arg + ")");
               return;
            case FLOAT:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_UNBOX_FLOAT(" + arg + ")");
               return;
            case DOUBLE:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_UNBOX_DOUBLE(" + arg + ")");
               return;
         }
      }

      this.writer.print(arg);
   }

   private void convertReturn(ValueType type) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_BOX_BOOLEAN(result)");
               return;
            case BYTE:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_BOX_BYTE(result)");
               return;
            case SHORT:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_BOX_SHORT(result)");
               return;
            case CHARACTER:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_BOX_CHAR(result)");
               return;
            case INTEGER:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_BOX_INT(result)");
               return;
            case LONG:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_BOX_LONG(result)");
               return;
            case FLOAT:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_BOX_FLOAT(result)");
               return;
            case DOUBLE:
               this.includes.includePath("reflection_gen.h");
               this.writer.print("TEAVM_REFLECTION_BOX_DOUBLE(result)");
               return;
         }
      }

      this.writer.print("result");
   }

   public void endForClass() {
      if (this.incremental) {
         this.writer.flush();
         this.includes = null;
         this.writer = null;
      }

      this.classLocalIncludes = null;
   }

   public void endForBuild(BuildTarget buildTarget) throws IOException {
      this.singleWriter.flush();
      OutputFileUtil.write(this.singleWriter, "reflection_signatures.c", buildTarget);
      if (this.headerWriter != null) {
         OutputFileUtil.write(this.headerWriter, "reflection_signatures.h", buildTarget);
      }
   }

   private record Signature(boolean hasReceiver, ValueType returnType, ValueType[] parameterTypes) {
   }
}
