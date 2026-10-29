package org.teavm.common;

public interface ServiceRepository {
   <T> T getService(Class<T> var1);
}
