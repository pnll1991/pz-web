package org.teavm.backend.c.intrinsic;

import java.util.Properties;
import org.teavm.common.ServiceRepository;
import org.teavm.model.ClassReaderSource;

public interface IntrinsicFactoryContext {
   ClassReaderSource getClassSource();

   ClassLoader getClassLoader();

   ServiceRepository getServices();

   Properties getProperties();
}
