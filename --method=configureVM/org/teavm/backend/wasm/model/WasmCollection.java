package org.teavm.backend.wasm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class WasmCollection<T extends WasmEntity> implements Iterable<T> {
   private List<T> items = new ArrayList<>();
   private List<T> readonlyItems = Collections.unmodifiableList(this.items);
   private boolean indexesInvalid;

   WasmCollection() {
   }

   public T get(int index) {
      return this.items.get(index);
   }

   public int size() {
      return this.items.size();
   }

   public boolean isEmpty() {
      return this.items.isEmpty();
   }

   public void add(T entity) {
      if (entity.collection != null) {
         throw new IllegalArgumentException("Entity already belongs some collection");
      }

      this.invalidateIndexes();
      entity.collection = this;
      this.items.add(entity);
   }

   public void removeIf(Predicate<T> predicate) {
      for (T item : this.items) {
         if (predicate.test(item)) {
            item.collection = null;
         }
      }

      if (this.items.removeIf(predicate)) {
         this.invalidateIndexes();
      }
   }

   public void clear() {
      for (T item : this.items) {
         item.collection = null;
      }

      this.items.clear();
   }

   final void invalidateIndexes() {
      this.indexesInvalid = true;
   }

   public int indexInCollection(T entity) {
      if (entity.collection != this) {
         throw new IllegalArgumentException("Given entity does not belong to this module");
      }

      this.ensureIndexes();
      return entity.indexInCollection;
   }

   public int indexOf(T entity) {
      if (entity.collection != this) {
         throw new IllegalArgumentException("Given entity does not belong to this module");
      }

      this.ensureIndexes();
      return entity.index;
   }

   private void ensureIndexes() {
      if (this.indexesInvalid) {
         this.indexesInvalid = false;
         int index = 0;

         for (T item : this.items) {
            if (item.isImported()) {
               item.index = index++;
            }
         }

         int indexInCollection = 0;

         for (T item : this.items) {
            item.indexInCollection = indexInCollection++;
            if (!item.isImported()) {
               item.index = index++;
            }
         }
      }
   }

   @Override
   public Iterator<T> iterator() {
      return this.readonlyItems.iterator();
   }

   public Stream<T> stream() {
      return this.readonlyItems.stream();
   }

   public void sort(Comparator<T> comparator) {
      this.items.sort(comparator);
      this.indexesInvalid = true;
   }
}
