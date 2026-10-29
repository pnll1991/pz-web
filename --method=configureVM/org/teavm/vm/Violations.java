package org.teavm.vm;

import java.util.List;
import java.util.Set;
import org.teavm.dependency.ClassDependencyInfo;
import org.teavm.dependency.FieldDependencyInfo;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.diagnostics.Problem;

public interface Violations {
   Set<MethodDependencyInfo> getMissingMethods();

   Set<ClassDependencyInfo> getMissingClasses();

   Set<FieldDependencyInfo> getMissingFields();

   List<Problem> getDiagnosticsProblems();

   List<Problem> getSevereDiagnosticsProblems();

   boolean hasSevereViolations();

   void checkForViolations();
}
