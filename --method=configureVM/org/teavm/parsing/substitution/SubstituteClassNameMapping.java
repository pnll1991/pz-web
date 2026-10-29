package org.teavm.parsing.substitution;

import org.teavm.model.ClassReaderSource;

public interface SubstituteClassNameMapping {
   String originalToSubstitute(ClassReaderSource var1, String var2);

   String substituteToOriginal(String var1);
}
