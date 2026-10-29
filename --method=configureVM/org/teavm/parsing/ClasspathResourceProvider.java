package org.teavm.parsing;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import org.teavm.parsing.resource.Resource;
import org.teavm.parsing.resource.ResourceProvider;

public class ClasspathResourceProvider implements ResourceProvider {
   private ClassLoader classLoader;

   public ClasspathResourceProvider(ClassLoader classLoader) {
      this.classLoader = classLoader;
   }

   @Override
   public Iterator<Resource> getResources(final String name) {
      try {
         final Enumeration<URL> enumeration = this.classLoader.getResources(name);
         return enumeration == null ? Collections.emptyIterator() : new Iterator<Resource>() {
            @Override
            public boolean hasNext() {
               return enumeration.hasMoreElements();
            }

            public Resource next() {
               final URL elem = enumeration.nextElement();
               return new Resource() {
                  @Override
                  public InputStream open() {
                     try {
                        return elem.openStream();
                     } catch (IOException e) {
                        throw new UncheckedIOException(e);
                     }
                  }

                  @Override
                  public Date getModificationDate() {
                     if (ClasspathResourceProvider.this.classLoader == null) {
                        return null;
                     }

                     URL url = ClasspathResourceProvider.this.classLoader.getResource(name);
                     if (url == null) {
                        return null;
                     }

                     if (url.getProtocol().equals("file")) {
                        try {
                           File file = new File(url.toURI());
                           return file.exists() ? new Date(file.lastModified()) : null;
                        } catch (URISyntaxException e) {
                           return null;
                        }
                     } else if (url.getProtocol().equals("jar") && url.getPath().startsWith("file:")) {
                        int exclIndex = url.getPath().indexOf(33);
                        String jarFileName = exclIndex >= 0 ? url.getPath().substring(0, exclIndex) : url.getPath();
                        File file = new File(jarFileName.substring("file:".length()));
                        return file.exists() ? new Date(file.lastModified()) : null;
                     } else {
                        return null;
                     }
                  }
               };
            }
         };
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   @Override
   public void close() {
   }
}
