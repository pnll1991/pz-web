package org.teavm.backend.javascript;

import org.teavm.common.ServiceRepository;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReference;

public interface ProviderContext extends ServiceRepository {
   MethodReference getMethod();

   ClassReaderSource getClassSource();
}
