package org.teavm.model;

import java.util.Set;

public interface ListableClassReaderSource extends ClassReaderSource {
   Set<String> getClassNames();
}
