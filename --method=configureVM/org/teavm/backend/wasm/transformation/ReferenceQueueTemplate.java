package org.teavm.backend.wasm.transformation;

import java.lang.ref.Reference;

class ReferenceQueueTemplate<T> {
   private ReferenceQueueEntry<T> start;
   private ReferenceQueueEntry<T> end;

   public Reference<T> poll() {
      ReferenceQueueEntry<T> result = this.start;
      if (result == null) {
         return null;
      }

      this.start = result.next;
      if (this.start == null) {
         this.end = null;
      }

      return result.reference;
   }

   public void supply(Reference<T> reference) {
      ReferenceQueueEntry<T> entry = new ReferenceQueueEntry<>(reference);
      if (this.start == null) {
         this.start = entry;
      } else {
         this.end.next = entry;
      }

      this.end = entry;
   }
}
