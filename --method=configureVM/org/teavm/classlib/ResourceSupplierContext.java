package org.teavm.classlib;

import java.util.Properties;
import org.teavm.model.ListableClassReaderSource;

@Deprecated
public interface ResourceSupplierContext {
   ClassLoader getClassLoader();

   ListableClassReaderSource getClassSource();

   Properties getProperties();
}
