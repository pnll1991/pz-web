package org.teavm.cache;

import org.teavm.ast.ControlFlowEntry;
import org.teavm.ast.RegularMethodNode;

public class AstCacheEntry {
   public RegularMethodNode method;
   public ControlFlowEntry[] cfg;

   public AstCacheEntry(RegularMethodNode method, ControlFlowEntry[] cfg) {
      this.method = method;
      this.cfg = cfg;
   }
}
