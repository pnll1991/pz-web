package org.teavm.backend.wasm.transformation;

import java.lang.ref.Reference;

class ReferenceQueueEntry<T> {
   final Reference<T> reference;
   ReferenceQueueEntry<T> next;

   ReferenceQueueEntry(Reference<T> reference) {
      this.reference = reference;
   }
}
