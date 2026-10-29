package org.teavm.backend.javascript.spi;

import org.teavm.model.ClassReaderSource;

public interface MethodContributorContext {
   ClassReaderSource getClassSource();
}
