package org.teavm.backend.c.generate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.teavm.backend.c.generators.GeneratorContext;
import org.teavm.backend.lowlevel.generate.NameProvider;
import org.teavm.dependency.DependencyInfo;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.lowlevel.CallSiteDescriptor;
import org.teavm.model.lowlevel.CallSiteLocation;
import org.teavm.model.lowlevel.ExceptionHandlerDescriptor;

class GeneratorContextImpl implements GeneratorContext {
   private GenerationContext context;
   private ClassGenerationContext classContext;
   private CodeWriter bodyWriter;
   private CodeWriter writerBefore;
   private CodeWriter writerAfter;
   private IncludeManager includes;
   private List<GeneratorContextImpl.FileGeneratorImpl> fileGenerators = new ArrayList<>();
   private List<CallSiteDescriptor> callSites;

   public GeneratorContextImpl(
      ClassGenerationContext classContext,
      CodeWriter bodyWriter,
      CodeWriter writerBefore,
      CodeWriter writerAfter,
      IncludeManager includes,
      List<CallSiteDescriptor> callSites
   ) {
      this.context = classContext.getContext();
      this.classContext = classContext;
      this.bodyWriter = bodyWriter;
      this.writerBefore = writerBefore;
      this.writerAfter = writerAfter;
      this.includes = includes;
      this.callSites = callSites;
   }

   @Override
   public GenerationContext mainContext() {
      return this.classContext.getContext();
   }

   @Override
   public NameProvider names() {
      return this.context.getNames();
   }

   @Override
   public Diagnostics diagnotics() {
      return this.context.getDiagnostics();
   }

   @Override
   public ClassReaderSource classSource() {
      return this.context.getClassSource();
   }

   @Override
   public ClassReaderSource initialClassSource() {
      return this.context.getInitialClassSource();
   }

   @Override
   public ClassHierarchy hierarchy() {
      return this.context.getHierarchy();
   }

   @Override
   public DependencyInfo dependencies() {
      return this.context.getDependencies();
   }

   @Override
   public String parameterName(int index) {
      return index == 0 ? "teavm_this_" : "teavm_local_" + index;
   }

   @Override
   public CodeWriter writer() {
      return this.bodyWriter;
   }

   @Override
   public StringPool stringPool() {
      return this.context.getStringPool();
   }

   @Override
   public CodeWriter writerBefore() {
      return this.writerBefore;
   }

   @Override
   public CodeWriter writerAfter() {
      return this.writerAfter;
   }

   @Override
   public IncludeManager includes() {
      return this.includes;
   }

   @Override
   public FileGenerator createSourceFile(String path) {
      return this.createFile(new BufferedCodeWriter(false), path);
   }

   @Override
   public void importMethod(MethodReference method, boolean isStatic) {
      this.classContext.importMethod(method, isStatic);
   }

   @Override
   public FileGenerator createHeaderFile(String path) {
      BufferedCodeWriter writer = new BufferedCodeWriter(false);
      writer.println("#pragma once");
      return this.createFile(writer, path);
   }

   private FileGenerator createFile(BufferedCodeWriter writer, String path) {
      IncludeManager includes = new SimpleIncludeManager(this.context.getFileNames(), writer);
      includes.init(path);
      GeneratorContextImpl.FileGeneratorImpl generator = new GeneratorContextImpl.FileGeneratorImpl(path, writer, includes);
      this.fileGenerators.add(generator);
      return generator;
   }

   @Override
   public String escapeFileName(String name) {
      return this.context.getFileNames().escapeName(name);
   }

   @Override
   public CallSiteDescriptor createCallSite(CallSiteLocation[] locations, ExceptionHandlerDescriptor[] exceptionHandlers) {
      CallSiteDescriptor callSite = new CallSiteDescriptor(this.callSites.size(), locations);
      callSite.getHandlers().addAll(Arrays.asList(exceptionHandlers));
      this.callSites.add(callSite);
      return callSite;
   }

   void flush() throws IOException {
      for (GeneratorContextImpl.FileGeneratorImpl generator : this.fileGenerators) {
         OutputFileUtil.write(generator.writer, generator.path, this.context.getBuildTarget());
      }

      this.fileGenerators.clear();
   }

   static class FileGeneratorImpl implements FileGenerator {
      String path;
      BufferedCodeWriter writer;
      private IncludeManager includes;

      FileGeneratorImpl(String path, BufferedCodeWriter writer, IncludeManager includes) {
         this.path = path;
         this.writer = writer;
         this.includes = includes;
      }

      @Override
      public CodeWriter writer() {
         return this.writer;
      }

      @Override
      public IncludeManager includes() {
         return this.includes;
      }
   }
}
