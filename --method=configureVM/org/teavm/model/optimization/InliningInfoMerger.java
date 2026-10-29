package org.teavm.model.optimization;

import java.util.HashMap;
import java.util.Map;
import org.teavm.model.InliningInfo;

class InliningInfoMerger {
   private InliningInfo parent;
   Map<InliningInfo, InliningInfo> inliningInfoCache = new HashMap<>();

   InliningInfoMerger(InliningInfo parent) {
      this.parent = parent;
   }

   InliningInfo merge(InliningInfo inliningInfo) {
      if (inliningInfo == null) {
         return this.parent;
      }

      InliningInfo result = this.inliningInfoCache.get(inliningInfo);
      if (result == null) {
         result = new InliningInfo(inliningInfo.getMethod(), inliningInfo.getFileName(), inliningInfo.getLine(), this.merge(inliningInfo.getParent()));
         this.inliningInfoCache.put(inliningInfo, result);
      }

      return result;
   }
}
