package org.teavm.classlib;

import org.teavm.model.ClassReaderSource;

@Deprecated
public interface ReflectionContext {
   ClassLoader getClassLoader();

   ClassReaderSource getClassSource();
}
