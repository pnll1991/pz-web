package org.teavm.dependency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.teavm.cache.IncrementalDependencyRegistration;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.BasicBlock;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderSource;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.Instruction;
import org.teavm.model.InvokeDynamicInstruction;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.optimization.UnreachableBasicBlockEliminator;
import org.teavm.model.transformation.ClassInitInsertion;
import org.teavm.model.util.BasicBlockSplitter;
import org.teavm.model.util.ModelUtils;

class DependencyClassSource implements ClassHolderSource {
   private DependencyAgent agent;
   private ClassReaderSource innerSource;
   ClassHierarchy innerHierarchy;
   private Diagnostics diagnostics;
   private IncrementalDependencyRegistration dependencyRegistration;
   private Map<String, ClassHolder> generatedClasses = new LinkedHashMap<>();
   private List<ClassHolderTransformer> transformers = new ArrayList<>();
   boolean obfuscated;
   boolean strict;
   Map<String, Optional<ClassHolder>> cache = new LinkedHashMap<>(1000, 0.5F);
   private ReferenceResolver referenceResolver;
   private ClassInitInsertion classInitInsertion;
   private String entryPoint;
   Map<MethodReference, BootstrapMethodSubstitutor> bootstrapMethodSubstitutors = new HashMap<>();
   private boolean disposed;
   private Set<MethodReference> usedMethods = new HashSet<>();
   private Map<MethodReference, List<Runnable>> pendingErrors = new HashMap<>();
   final ClassHolderTransformerContext transformContext = new ClassHolderTransformerContext() {
      @Override
      public ClassHierarchy getHierarchy() {
         return DependencyClassSource.this.innerHierarchy;
      }

      @Override
      public Diagnostics getDiagnostics() {
         return DependencyClassSource.this.diagnostics;
      }

      @Override
      public IncrementalDependencyRegistration getIncrementalCache() {
         return DependencyClassSource.this.dependencyRegistration;
      }

      @Override
      public boolean isObfuscated() {
         return DependencyClassSource.this.obfuscated;
      }

      @Override
      public boolean isStrict() {
         return DependencyClassSource.this.strict;
      }

      @Override
      public void submit(ClassHolder cls) {
         DependencyClassSource.this.submit(cls);
      }

      @Override
      public String getEntryPoint() {
         return DependencyClassSource.this.entryPoint;
      }
   };

   DependencyClassSource(
      DependencyAgent agent,
      ClassReaderSource innerSource,
      Diagnostics diagnostics,
      IncrementalDependencyRegistration dependencyRegistration,
      String[] platformTags
   ) {
      this.agent = agent;
      this.innerSource = innerSource;
      this.diagnostics = diagnostics;
      this.innerHierarchy = new ClassHierarchy(innerSource);
      this.dependencyRegistration = dependencyRegistration;
      this.referenceResolver = new ReferenceResolver(this, platformTags, diagnostics);
      this.classInitInsertion = new ClassInitInsertion(this);
   }

   public ReferenceResolver getReferenceResolver() {
      return this.referenceResolver;
   }

   @Override
   public ClassHolder get(String name) {
      Optional<ClassHolder> result = this.cache.get(name);
      if (result == null) {
         ClassHolder cls = this.findClass(name);
         result = Optional.ofNullable(cls);
         this.cache.put(name, result);
         if (cls != null) {
            this.transformClass(cls);
         }
      }

      return result.orElse(null);
   }

   public void submit(ClassHolder cls) {
      if (this.innerSource.get(cls.getName()) == null && !this.generatedClasses.containsKey(cls.getName())) {
         if (!this.transformers.isEmpty()) {
            cls = ModelUtils.copyClass(cls);
         }

         this.generatedClasses.put(cls.getName(), cls);

         for (MethodHolder method : cls.getMethods()) {
            if (method.getProgram() != null && method.getProgram().basicBlockCount() > 0) {
               new UnreachableBasicBlockEliminator().optimize(method.getProgram());
            }
         }

         this.cache.remove(cls.getName());
      } else {
         throw new IllegalArgumentException("Class " + cls.getName() + " is already defined");
      }
   }

   private void transformClass(ClassHolder cls) {
      if (!this.disposed) {
         for (MethodHolder method : cls.getMethods()) {
            this.processInvokeDynamic(method);
         }
      }

      if (!this.transformers.isEmpty()) {
         for (ClassHolderTransformer transformer : this.transformers) {
            transformer.transformClass(cls, this.transformContext);
         }
      }

      for (MethodHolder method : cls.getMethods()) {
         if (method.getProgram() != null) {
            Program program = method.getProgram();
            method.setProgramSupplier(m -> {
               if (this.disposed) {
                  return null;
               }

               this.referenceResolver.resolve(m, program);
               this.classInitInsertion.apply(m, program);
               return program;
            });
         }
      }
   }

   private ClassHolder findClass(String name) {
      ClassReader cls = this.innerSource.get(name);
      return cls != null ? ModelUtils.copyClass(cls) : this.generatedClasses.get(name);
   }

   Collection<String> getGeneratedClassNames() {
      return this.generatedClasses.keySet();
   }

   public boolean isGeneratedClass(String className) {
      return this.generatedClasses.containsKey(className);
   }

   public void addTransformer(ClassHolderTransformer transformer) {
      this.transformers.add(transformer);
   }

   void setEntryPoint(String entryPoint) {
      this.entryPoint = entryPoint;
   }

   public void dispose() {
      this.transformers.clear();
      this.bootstrapMethodSubstitutors.clear();
      this.disposed = true;
   }

   private void processInvokeDynamic(MethodHolder method) {
      Program program = method.getProgram();
      if (program != null) {
         ProgramEmitter pe = ProgramEmitter.create(program, this.innerHierarchy);
         BasicBlockSplitter splitter = new BasicBlockSplitter(program);

         for (int i = 0; i < program.basicBlockCount(); i++) {
            for (Instruction insn : program.basicBlockAt(i)) {
               if (insn instanceof InvokeDynamicInstruction) {
                  BasicBlock var17 = insn.getBasicBlock();
                  InvokeDynamicInstruction indy = (InvokeDynamicInstruction)insn;
                  MethodReference bootstrapMethod = new MethodReference(
                     indy.getBootstrapMethod().getClassName(), indy.getBootstrapMethod().getName(), indy.getBootstrapMethod().signature()
                  );
                  BootstrapMethodSubstitutor substitutor = this.bootstrapMethodSubstitutors.get(bootstrapMethod);
                  if (substitutor == null) {
                     NullConstantInstruction nullInsn = new NullConstantInstruction();
                     nullInsn.setReceiver(indy.getReceiver());
                     nullInsn.setLocation(indy.getLocation());
                     insn.replace(nullInsn);
                     CallLocation location = new CallLocation(method.getReference(), insn.getLocation());
                     this.reportError(location, "Bootstrap method {{m0}} was not found", bootstrapMethod);
                  } else {
                     BasicBlock splitBlock = splitter.split(var17, insn);
                     pe.enter(var17);
                     pe.setCurrentLocation(indy.getLocation());
                     insn.delete();
                     List<ValueEmitter> arguments = new ArrayList<>();

                     for (int k = 0; k < indy.getArguments().size(); k++) {
                        arguments.add(pe.var(indy.getArguments().get(k), indy.getMethod().parameterType(k)));
                     }

                     DynamicCallSite callSite = new DynamicCallSite(
                        method.getReference(),
                        indy.getMethod(),
                        indy.getInstance() != null ? pe.var(indy.getInstance(), ValueType.object(method.getOwnerName())) : null,
                        arguments,
                        indy.getBootstrapMethod(),
                        indy.getBootstrapArguments(),
                        this.agent,
                        insn.getLocation()
                     );
                     ValueEmitter result = substitutor.substitute(callSite, pe);
                     if (result.getVariable() != null && result.getVariable() != indy.getReceiver() && indy.getReceiver() != null) {
                        AssignInstruction assign = new AssignInstruction();
                        assign.setAssignee(result.getVariable());
                        assign.setReceiver(indy.getReceiver());
                        pe.addInstruction(assign);
                     }

                     pe.jump(splitBlock);
                  }
               }
            }
         }

         splitter.fixProgram();
      }
   }

   private void reportError(CallLocation location, String message, Object... args) {
      if (this.usedMethods.contains(location.getMethod())) {
         this.diagnostics.error(location, message, args);
      } else {
         this.pendingErrors.computeIfAbsent(location.getMethod(), m -> new ArrayList<>()).add(() -> this.diagnostics.error(location, message, args));
      }
   }

   public void use(MethodReference method) {
      this.usedMethods.add(method);
      List<Runnable> errors = this.pendingErrors.remove(method);
      if (errors != null) {
         for (Runnable error : errors) {
            error.run();
         }
      }

      this.referenceResolver.use(method);
   }
}
