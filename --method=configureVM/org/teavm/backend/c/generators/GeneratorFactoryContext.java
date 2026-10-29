package org.teavm.backend.c.generators;

import java.util.Properties;
import org.teavm.common.ServiceRepository;
import org.teavm.model.ClassReaderSource;
import org.teavm.parsing.resource.ResourceProvider;

public interface GeneratorFactoryContext {
   ClassReaderSource getClassSource();

   ResourceProvider getResourceProvider();

   ClassLoader getClassLoader();

   ServiceRepository getServices();

   Properties getProperties();
}
