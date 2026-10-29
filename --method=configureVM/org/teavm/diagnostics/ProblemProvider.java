package org.teavm.diagnostics;

import java.util.List;

public interface ProblemProvider {
   List<Problem> getProblems();

   List<Problem> getSevereProblems();
}
