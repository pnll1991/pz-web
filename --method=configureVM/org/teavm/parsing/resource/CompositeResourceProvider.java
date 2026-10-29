package org.teavm.parsing.resource;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CompositeResourceProvider implements ResourceProvider {
   private ResourceProvider[] providers;

   public CompositeResourceProvider(ResourceProvider... providers) {
      this.providers = providers;
   }

   @Override
   public Iterator<Resource> getResources(String name) {
      final List<Iterator<Resource>> iterators = Stream.of(this.providers).map(provider -> provider.getResources(name)).collect(Collectors.toList());
      return new Iterator<Resource>() {
         Iterator<Resource> current;
         int currentIndex;

         @Override
         public boolean hasNext() {
            return this.current == null ? this.following() : this.current.hasNext();
         }

         public Resource next() {
            if ((this.current == null || !this.current.hasNext()) && !this.following()) {
               throw new NoSuchElementException();
            }

            Resource result = this.current.next();
            if (!this.current.hasNext()) {
               this.current = null;
            }

            return result;
         }

         private boolean following() {
            while (this.currentIndex < iterators.size()) {
               this.current = iterators.get(this.currentIndex++);
               if (this.current.hasNext()) {
                  return true;
               }
            }

            return false;
         }
      };
   }

   @Override
   public void close() {
      for (ResourceProvider provider : this.providers) {
         provider.close();
      }
   }
}
