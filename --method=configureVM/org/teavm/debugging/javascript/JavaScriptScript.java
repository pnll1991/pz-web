package org.teavm.debugging.javascript;

import org.teavm.common.Promise;

public interface JavaScriptScript {
   String getId();

   JavaScriptLanguage getLanguage();

   String getUrl();

   Promise<String> getSource();
}
