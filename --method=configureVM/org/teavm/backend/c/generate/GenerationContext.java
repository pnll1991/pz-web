package org.teavm.backend.c.generate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.teavm.backend.c.generators.Generator;
import org.teavm.backend.c.intrinsic.Intrinsic;
import org.teavm.backend.lowlevel.generate.NameProvider;
import org.teavm.dependency.DependencyInfo;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.model.analysis.ClassMetadataRequirements;
import org.teavm.model.classes.VirtualTableProvider;
import org.teavm.model.lowlevel.Characteristics;
import org.teavm.vm.BuildTarget;

public class GenerationContext {
   private VirtualTableProvider virtualTableProvider;
   private Characteristics characteristics;
   private DependencyInfo dependencies;
   private StringPool stringPool;
   private NameProvider names;
   private FileNameProvider fileNames;
   private Diagnostics diagnostics;
   private ClassReaderSource classSource;
   private ClassReaderSource initialClassSource;
   private ClassHierarchy hierarchy;
   private List<Intrinsic> intrinsics;
   private List<Generator> generators;
   private Map<MethodReference, Intrinsic> intrinsicCache = new HashMap<>();
   private Predicate<MethodReference> asyncMethods;
   private BuildTarget buildTarget;
   private ClassInitializerInfo classInitializerInfo;
   private boolean incremental;
   private boolean vmAssertions;
   private boolean heapDump;
   private boolean obfuscated;
   private Set<ValueType> types;
   private Set<ValueType> typeLiterals;
   private ClassMetadataRequirements metadataRequirements;

   public GenerationContext(
      VirtualTableProvider virtualTableProvider,
      Characteristics characteristics,
      DependencyInfo dependencies,
      StringPool stringPool,
      NameProvider names,
      FileNameProvider fileNames,
      Diagnostics diagnostics,
      ClassReaderSource classSource,
      ClassReaderSource initialClassSource,
      ClassHierarchy hierarchy,
      List<Intrinsic> intrinsics,
      List<Generator> generators,
      Predicate<MethodReference> asyncMethods,
      BuildTarget buildTarget,
      ClassInitializerInfo classInitializerInfo,
      boolean incremental,
      boolean vmAssertions,
      boolean heapDump,
      boolean obfuscated,
      Set<ValueType> types,
      Set<ValueType> typeLiterals,
      ClassMetadataRequirements metadataRequirements
   ) {
      this.virtualTableProvider = virtualTableProvider;
      this.characteristics = characteristics;
      this.dependencies = dependencies;
      this.stringPool = stringPool;
      this.names = names;
      this.fileNames = fileNames;
      this.diagnostics = diagnostics;
      this.classSource = classSource;
      this.initialClassSource = initialClassSource;
      this.hierarchy = hierarchy;
      this.intrinsics = new ArrayList<>(intrinsics);
      this.generators = new ArrayList<>(generators);
      this.asyncMethods = asyncMethods;
      this.buildTarget = buildTarget;
      this.classInitializerInfo = classInitializerInfo;
      this.incremental = incremental;
      this.vmAssertions = vmAssertions;
      this.heapDump = heapDump;
      this.obfuscated = obfuscated;
      this.types = types;
      this.typeLiterals = typeLiterals;
      this.metadataRequirements = metadataRequirements;
   }

   public void addIntrinsic(Intrinsic intrinsic) {
      this.intrinsics.add(intrinsic);
   }

   public void addGenerator(Generator generator) {
      this.generators.add(generator);
   }

   public VirtualTableProvider getVirtualTableProvider() {
      return this.virtualTableProvider;
   }

   public Characteristics getCharacteristics() {
      return this.characteristics;
   }

   public DependencyInfo getDependencies() {
      return this.dependencies;
   }

   public StringPool getStringPool() {
      return this.stringPool;
   }

   public NameProvider getNames() {
      return this.names;
   }

   public FileNameProvider getFileNames() {
      return this.fileNames;
   }

   public Diagnostics getDiagnostics() {
      return this.diagnostics;
   }

   public ClassReaderSource getClassSource() {
      return this.classSource;
   }

   public ClassReaderSource getInitialClassSource() {
      return this.initialClassSource;
   }

   public ClassHierarchy getHierarchy() {
      return this.hierarchy;
   }

   public Intrinsic getIntrinsic(MethodReference method) {
      return this.intrinsicCache.computeIfAbsent(method, m -> this.intrinsics.stream().filter(i -> i.canHandle(m)).findFirst().orElse(null));
   }

   public Generator getGenerator(MethodReference method) {
      for (Generator generator : this.generators) {
         if (generator.canHandle(method)) {
            return generator;
         }
      }

      return null;
   }

   public boolean isAsync(MethodReference method) {
      return this.asyncMethods.test(method);
   }

   public BuildTarget getBuildTarget() {
      return this.buildTarget;
   }

   public ClassInitializerInfo getClassInitializerInfo() {
      return this.classInitializerInfo;
   }

   public boolean isIncremental() {
      return this.incremental;
   }

   public boolean isHeapDump() {
      return this.heapDump;
   }

   public boolean isVmAssertions() {
      return this.vmAssertions;
   }

   public boolean isObfuscated() {
      return this.obfuscated;
   }

   public void addTypeLiteral(ValueType type) {
      this.typeLiterals.add(type);
   }

   public void addType(ValueType type) {
      if (!(type instanceof ValueType.Object)) {
         if (type instanceof ValueType.Array) {
            ValueType item = ((ValueType.Array)type).getItemType();
            this.addType(item);
            if (!(item instanceof ValueType.Primitive)) {
               return;
            }
         }

         if (this.types.add(type)) {
            if (type instanceof ValueType.Array) {
               this.addType(((ValueType.Array)type).getItemType());
            }
         }
      }
   }

   public ClassMetadataRequirements getMetadataRequirements() {
      return this.metadataRequirements;
   }
}
