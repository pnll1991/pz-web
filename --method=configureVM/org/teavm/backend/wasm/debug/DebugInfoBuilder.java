package org.teavm.backend.wasm.debug;

import java.util.ArrayList;
import java.util.List;
import org.teavm.backend.wasm.model.WasmCustomSection;

public class DebugInfoBuilder {
   private DebugStringsBuilder strings = new DebugStringsBuilder();
   private DebugFilesBuilder files = new DebugFilesBuilder(this.strings);
   private DebugPackagesBuilder packages = new DebugPackagesBuilder(this.strings);
   private DebugClassesBuilder classes = new DebugClassesBuilder(this.packages, this.strings);
   private DebugMethodsBuilder methods = new DebugMethodsBuilder(this.classes, this.strings);
   private DebugVariablesBuilder variables = new DebugVariablesBuilder(this.strings);
   private DebugLinesBuilder lines = new DebugLinesBuilder(this.files, this.methods);
   private DebugClassLayoutBuilder classLayout = new DebugClassLayoutBuilder(this.classes, this.strings);

   public DebugStrings strings() {
      return this.strings;
   }

   public DebugFiles files() {
      return this.files;
   }

   public DebugPackages packages() {
      return this.packages;
   }

   public DebugClasses classes() {
      return this.classes;
   }

   public DebugMethods methods() {
      return this.methods;
   }

   public DebugVariables variables() {
      return this.variables;
   }

   public DebugLines lines() {
      return this.lines;
   }

   public DebugClassLayout classLayout() {
      return this.classLayout;
   }

   public List<WasmCustomSection> build() {
      ArrayList<WasmCustomSection> result = new ArrayList<>();
      this.addSection(result, this.strings);
      this.addSection(result, this.files);
      this.addSection(result, this.packages);
      this.addSection(result, this.classes);
      this.addSection(result, this.methods);
      this.addSection(result, this.variables);
      this.addSection(result, this.lines);
      this.addSection(result, this.classLayout);
      return result;
   }

   private void addSection(List<WasmCustomSection> sections, DebugSectionBuilder builder) {
      if (!builder.isEmpty()) {
         sections.add(new WasmCustomSection(builder.name(), builder.build()));
      }
   }
}
