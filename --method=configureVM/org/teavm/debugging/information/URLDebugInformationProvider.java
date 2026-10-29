package org.teavm.debugging.information;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

public class URLDebugInformationProvider implements DebugInformationProvider {
   private String baseURL;

   public URLDebugInformationProvider(String baseURL) {
      this.baseURL = baseURL;
   }

   @Override
   public DebugInformation getDebugInformation(String script) {
      try {
         URI uri = new URI(this.baseURL + script);
         uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath() + ".teavmdbg", uri.getQuery(), uri.getFragment());

         try (InputStream input = uri.toURL().openStream()) {
            return DebugInformation.read(input);
         }
      } catch (IOException | URISyntaxException e) {
         return null;
      }
   }
}
