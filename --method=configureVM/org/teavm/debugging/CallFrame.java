package org.teavm.debugging;

import java.util.Collections;
import java.util.Map;
import org.teavm.backend.wasm.debug.info.DebugInfo;
import org.teavm.common.Promise;
import org.teavm.debugging.information.DebugInformation;
import org.teavm.debugging.information.SourceLocation;
import org.teavm.debugging.javascript.JavaScriptCallFrame;
import org.teavm.debugging.javascript.JavaScriptLocation;
import org.teavm.model.MethodReference;

public class CallFrame {
   private Debugger debugger;
   private JavaScriptCallFrame originalCallFrame;
   private SourceLocation location;
   private MethodReference method;
   private Promise<Map<String, Variable>> variables;
   private DebugInformation debugInformation;
   private DebugInfo wasmDebugInfo;

   CallFrame(
      Debugger debugger,
      JavaScriptCallFrame originalFrame,
      SourceLocation location,
      MethodReference method,
      DebugInformation debugInformation,
      DebugInfo wasmDebugInfo
   ) {
      this.debugger = debugger;
      this.originalCallFrame = originalFrame;
      this.location = location;
      this.method = method;
      this.debugInformation = debugInformation;
      this.wasmDebugInfo = wasmDebugInfo;
   }

   public Debugger getDebugger() {
      return this.debugger;
   }

   public JavaScriptLocation getOriginalLocation() {
      return this.originalCallFrame.getLocation();
   }

   public JavaScriptCallFrame getOriginalCallFrame() {
      return this.originalCallFrame;
   }

   public SourceLocation getLocation() {
      return this.location;
   }

   public MethodReference getMethod() {
      return this.method;
   }

   public Promise<Map<String, Variable>> getVariables() {
      if (this.variables == null) {
         if (this.debugInformation != null) {
            this.variables = this.debugger.createVariables(this.originalCallFrame, this.debugInformation);
         } else if (this.wasmDebugInfo != null) {
            this.variables = this.debugger.createVariables(this.originalCallFrame, this.wasmDebugInfo);
         } else {
            this.variables = Promise.of(Collections.emptyMap());
         }
      }

      return this.variables;
   }
}
