package org.teavm.backend.c.analyze;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.teavm.ast.AssignmentStatement;
import org.teavm.ast.RecursiveVisitor;
import org.teavm.ast.Statement;
import org.teavm.ast.TryCatchStatement;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntLookupContainer;
import org.teavm.hppc.IntSet;

public class VolatileDefinitionFinder {
   private static final int[] EMPTY_INT_ARRAY = new int[0];
   private Set<TryCatchStatement> outerTryCatches = new HashSet<>();
   private Set<AssignmentStatement> definitionsToBackup = new HashSet<>();
   private Map<TryCatchStatement, IntSet> usagesToRestoreByHandler = new HashMap<>();
   private Map<TryCatchStatement, IntSet> definitionsToBackupOnEntry = new HashMap<>();
   private Map<TryCatchStatement, IntHashSet> mutatedVars = new HashMap<>();
   private AstDefinitionUsageAnalysis defuse;
   private RecursiveVisitor handlerAnalyzer = new RecursiveVisitor() {
      @Override
      public void visit(TryCatchStatement statement) {
         VolatileDefinitionFinder.this.outerTryCatches.add(statement);
         this.visit(statement.getProtectedBody());
         VolatileDefinitionFinder.this.outerTryCatches.remove(statement);
         this.visit(statement.getHandler());
      }

      @Override
      public void visit(AssignmentStatement statement) {
         AstDefinitionUsageAnalysis.Definition definition = VolatileDefinitionFinder.this.defuse.getDefinition(statement);
         if (definition != null) {
            for (AstDefinitionUsageAnalysis.Usage usage : definition.getUsages()) {
               for (TryCatchStatement tryCatch : usage.getLiveInCatches()) {
                  if (VolatileDefinitionFinder.this.outerTryCatches.contains(tryCatch)) {
                     VolatileDefinitionFinder.this.definitionsToBackup.add(definition.getStatement());
                     VolatileDefinitionFinder.this.mutatedVars.computeIfAbsent(tryCatch, k -> new IntHashSet()).add(definition.getVariableIndex());

                     for (TryCatchStatement outerTryCatch : VolatileDefinitionFinder.this.outerTryCatches) {
                        VolatileDefinitionFinder.this.definitionsToBackupOnEntry
                           .computeIfAbsent(outerTryCatch, k -> new IntHashSet())
                           .add(definition.getVariableIndex());
                     }
                  } else {
                     VolatileDefinitionFinder.this.definitionsToBackupOnEntry
                        .computeIfAbsent(tryCatch, k -> new IntHashSet())
                        .add(definition.getVariableIndex());
                  }

                  VolatileDefinitionFinder.this.usagesToRestoreByHandler.computeIfAbsent(tryCatch, k -> new IntHashSet()).add(definition.getVariableIndex());
               }
            }
         }
      }
   };

   public void findVolatileDefinitions(Statement statement) {
      this.defuse = new AstDefinitionUsageAnalysis();
      this.defuse.analyze(statement);
      statement.acceptVisitor(this.handlerAnalyzer);
      this.defuse = null;
      this.usagesToRestoreByHandler.keySet().retainAll(this.mutatedVars.keySet());
      this.definitionsToBackupOnEntry.keySet().retainAll(this.mutatedVars.keySet());

      for (Entry<TryCatchStatement, IntHashSet> entry : this.mutatedVars.entrySet()) {
         IntSet set = this.usagesToRestoreByHandler.get(entry.getKey());
         if (set != null) {
            set.retainAll((IntLookupContainer)entry.getValue());
         }

         set = this.definitionsToBackupOnEntry.get(entry.getKey());
         if (set != null) {
            set.retainAll((IntLookupContainer)entry.getValue());
         }
      }
   }

   public boolean shouldBackup(AssignmentStatement statement) {
      return this.definitionsToBackup.contains(statement);
   }

   public int[] variablesToRestore(TryCatchStatement tryCatch) {
      IntSet result = this.usagesToRestoreByHandler.get(tryCatch);
      if (result == null) {
         return EMPTY_INT_ARRAY;
      }

      int[] array = result.toArray();
      Arrays.sort(array);
      return array;
   }

   public int[] variablesToBackup(TryCatchStatement tryCatch) {
      IntSet result = this.definitionsToBackupOnEntry.get(tryCatch);
      if (result == null) {
         return EMPTY_INT_ARRAY;
      }

      int[] array = result.toArray();
      Arrays.sort(array);
      return array;
   }
}
