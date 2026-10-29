package org.teavm.debugging.javascript;

import java.util.Map;
import org.teavm.common.Promise;

public interface JavaScriptValue {
   String getSimpleRepresentation();

   Promise<String> getRepresentation();

   Promise<String> getClassName();

   Promise<Map<String, ? extends JavaScriptVariable>> getProperties();

   boolean hasInnerStructure();

   String getInstanceId();
}
