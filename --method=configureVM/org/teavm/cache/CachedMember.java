package org.teavm.cache;

import org.teavm.model.MemberReader;

class CachedMember extends CachedElement implements MemberReader {
   String ownerName;

   @Override
   public String getOwnerName() {
      return this.ownerName;
   }
}
