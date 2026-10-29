package org.teavm.classlib;

import org.teavm.model.ClassReaderSource;

public interface ServiceLoaderFilterContext {
   ClassLoader getClassLoader();

   ClassReaderSource getClassSource();
}
