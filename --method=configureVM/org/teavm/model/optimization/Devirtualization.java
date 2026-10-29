package org.teavm.model.optimization;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.teavm.common.OptionalPredicate;
import org.teavm.dependency.DependencyInfo;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.dependency.ValueDependencyInfo;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReader;
import org.teavm.model.Instruction;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;

public class Devirtualization {
   static final boolean shouldLog = System.getProperty("org.teavm.logDevirtualization", "false").equals("true");
   private DependencyInfo dependency;
   private ClassHierarchy hierarchy;
   private Set<MethodReference> virtualMethods = new HashSet<>();
   private Set<? extends MethodReference> readonlyVirtualMethods = Collections.unmodifiableSet(this.virtualMethods);
   private Map<ValueDependencyInfo, Map<MethodReference, Set<MethodReference>>> implementationCache = new HashMap<>();
   private Map<ValueDependencyInfo, Map<ValueType, Optional<ValueType>>> castCache = new HashMap<>();
   private int virtualCallSites;
   private int directCallSites;
   private int remainingCasts;
   private int eliminatedCasts;

   public Devirtualization(DependencyInfo dependency, ClassHierarchy hierarchy) {
      this.dependency = dependency;
      this.hierarchy = hierarchy;
   }

   public int getVirtualCallSites() {
      return this.virtualCallSites;
   }

   public int getDirectCallSites() {
      return this.directCallSites;
   }

   public int getRemainingCasts() {
      return this.remainingCasts;
   }

   public int getEliminatedCasts() {
      return this.eliminatedCasts;
   }

   public void apply(MethodHolder method) {
      MethodDependencyInfo methodDep = this.dependency.getMethod(method.getReference());
      if (methodDep != null) {
         Program program = method.getProgram();
         if (shouldLog) {
            System.out.println("DEVIRTUALIZATION running at " + method.getReference());
         }

         for (int i = 0; i < program.basicBlockCount(); i++) {
            for (Instruction insn : program.basicBlockAt(i)) {
               if (insn instanceof InvokeInstruction) {
                  this.applyToInvoke(methodDep, program, (InvokeInstruction)insn);
               } else if (insn instanceof CastInstruction) {
                  this.applyToCast(methodDep, (CastInstruction)insn);
               }
            }
         }

         if (shouldLog) {
            System.out.println("DEVIRTUALIZATION complete for " + method.getReference());
         }
      }
   }

   private void applyToInvoke(MethodDependencyInfo methodDep, Program program, InvokeInstruction invoke) {
      if (invoke.getType() == InvocationType.VIRTUAL) {
         ValueDependencyInfo var = methodDep.getVariable(invoke.getInstance().getIndex());
         Set<MethodReference> implementations = this.getImplementations(var, invoke.getMethod());
         if (implementations.size() == 1) {
            MethodReference resolvedImplementation = implementations.iterator().next();
            if (shouldLog) {
               System.out.print("DIRECT CALL " + invoke.getMethod() + " resolved to " + resolvedImplementation.getClassName());
               if (invoke.getLocation() != null) {
                  System.out.print(" at " + invoke.getLocation().getFileName() + ":" + invoke.getLocation().getLine());
               }

               System.out.println();
            }

            if (!resolvedImplementation.getClassName().equals(invoke.getMethod().getClassName())) {
               CastInstruction cast = new CastInstruction();
               cast.setValue(invoke.getInstance());
               cast.setTargetType(ValueType.object(resolvedImplementation.getClassName()));
               cast.setWeak(true);
               cast.setReceiver(program.createVariable());
               cast.setLocation(invoke.getLocation());
               invoke.insertPrevious(cast);
               invoke.setInstance(cast.getReceiver());
            }

            invoke.setType(InvocationType.SPECIAL);
            invoke.setMethod(resolvedImplementation);
            this.directCallSites++;
         } else {
            this.virtualMethods.addAll(implementations);
            if (shouldLog) {
               System.out.print("VIRTUAL CALL " + invoke.getMethod() + " resolved to [");
               boolean first = true;

               for (MethodReference impl : implementations) {
                  if (!first) {
                     System.out.print(", ");
                  }

                  first = false;
                  System.out.print(impl.getClassName());
               }

               System.out.print("]");
               if (invoke.getLocation() != null) {
                  System.out.print(" at " + invoke.getLocation().getFileName() + ":" + invoke.getLocation().getLine());
               }

               System.out.println();
            }

            this.virtualCallSites++;
         }
      }
   }

   private void applyToCast(MethodDependencyInfo methodDep, CastInstruction cast) {
      ValueDependencyInfo var = methodDep.getVariable(cast.getValue().getIndex());
      if (var != null) {
         ValueType failType = this.getCastFailType(var, cast.getTargetType());
         if (failType != null) {
            if (shouldLog) {
               System.out.print("REMAINING CAST to " + cast.getTargetType() + " (example is " + failType + ")");
               if (cast.getLocation() != null) {
                  System.out.print(" at " + cast.getLocation().getFileName() + ":" + cast.getLocation().getLine());
               }

               System.out.println();
            }

            this.remainingCasts++;
         } else {
            if (shouldLog) {
               System.out.print("ELIMINATED CAST to " + cast.getTargetType());
               if (cast.getLocation() != null) {
                  System.out.print(" at " + cast.getLocation().getFileName() + ":" + cast.getLocation().getLine());
               }

               System.out.println();
            }

            cast.setWeak(true);
            this.eliminatedCasts++;
         }
      }
   }

   private ValueType getCastFailType(ValueDependencyInfo node, ValueType targetType) {
      if (this.dependency.isPrecise()) {
         return this.computeCastFailType(node, targetType);
      }

      Map<ValueType, Optional<ValueType>> byType = this.castCache.computeIfAbsent(node, n -> new HashMap<>());
      return byType.computeIfAbsent(targetType, t -> Optional.ofNullable(this.computeCastFailType(node, t))).orElse(null);
   }

   private ValueType computeCastFailType(ValueDependencyInfo node, ValueType targetType) {
      for (ValueType type : node.getTypes()) {
         if (this.castCanFail(type, targetType)) {
            return type;
         }
      }

      return null;
   }

   private boolean castCanFail(ValueType type, ValueType target) {
      return !this.hierarchy.isSuperType(target, type, false);
   }

   private Set<MethodReference> getImplementations(ValueDependencyInfo value, MethodReference ref) {
      if (this.dependency.isPrecise()) {
         return implementations(this.hierarchy, this.dependency, value.getTypes(), ref);
      }

      Map<MethodReference, Set<MethodReference>> map = this.implementationCache.computeIfAbsent(value, v -> new HashMap<>());
      return map.computeIfAbsent(ref, m -> implementations(this.hierarchy, this.dependency, value.getTypes(), m));
   }

   public static Set<MethodReference> implementations(ClassHierarchy hierarchy, DependencyInfo dependency, ValueType[] types, MethodReference ref) {
      OptionalPredicate<ValueType> isSuperclass = hierarchy.getSuperclassPredicate(ref.getClassName());
      Set<MethodReference> methods = new LinkedHashSet<>();
      boolean arrayEncountered = false;

      for (ValueType type : types) {
         String className;
         if (type instanceof ValueType.Array) {
            if (arrayEncountered) {
               continue;
            }

            arrayEncountered = true;
            className = "java.lang.Object";
         } else {
            if (!(type instanceof ValueType.Object)) {
               continue;
            }

            className = ((ValueType.Object)type).getClassName();
         }

         if (isSuperclass.test(ValueType.object(className), false)) {
            ClassReader cls = hierarchy.getClassSource().get(className);
            if (cls != null) {
               MethodDependencyInfo methodDep = dependency.getMethodImplementation(new MethodReference(className, ref.getDescriptor()));
               if (methodDep != null) {
                  methods.add(methodDep.getReference());
               }
            }
         }
      }

      return methods;
   }

   public Set<? extends MethodReference> getVirtualMethods() {
      return this.readonlyVirtualMethods;
   }
}
