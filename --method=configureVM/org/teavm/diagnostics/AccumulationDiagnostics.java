package org.teavm.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.teavm.model.CallLocation;

public class AccumulationDiagnostics implements Diagnostics, ProblemProvider {
   private List<Problem> problems = new ArrayList<>();
   private List<Problem> readonlyProblems = Collections.unmodifiableList(this.problems);
   private List<Problem> severeProblems = new ArrayList<>();
   private List<Problem> readonlySevereProblems = Collections.unmodifiableList(this.severeProblems);

   @Override
   public void error(CallLocation location, String error, Object... params) {
      Problem problem = new Problem(ProblemSeverity.ERROR, location, error, params);
      this.problems.add(problem);
      this.severeProblems.add(problem);
   }

   @Override
   public void warning(CallLocation location, String error, Object... params) {
      Problem problem = new Problem(ProblemSeverity.WARNING, location, error, params);
      this.problems.add(problem);
   }

   @Override
   public List<Problem> getProblems() {
      return this.readonlyProblems;
   }

   @Override
   public List<Problem> getSevereProblems() {
      return this.readonlySevereProblems;
   }
}
