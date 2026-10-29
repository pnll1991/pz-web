package org.teavm.debugging;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Base64.Decoder;
import java.util.Map.Entry;
import java.util.function.Predicate;
import org.teavm.backend.wasm.debug.info.DebugInfo;
import org.teavm.backend.wasm.debug.info.FileInfo;
import org.teavm.backend.wasm.debug.info.InliningLocation;
import org.teavm.backend.wasm.debug.info.InstructionLocation;
import org.teavm.backend.wasm.debug.info.LineInfo;
import org.teavm.backend.wasm.debug.info.LineInfoCommand;
import org.teavm.backend.wasm.debug.info.LineInfoFileCommand;
import org.teavm.backend.wasm.debug.info.LineInfoSequence;
import org.teavm.backend.wasm.debug.info.Location;
import org.teavm.backend.wasm.debug.info.MethodInfo;
import org.teavm.backend.wasm.debug.info.StepLocationsFinder;
import org.teavm.backend.wasm.debug.info.VariableRangeInfo;
import org.teavm.backend.wasm.debug.info.VariablesInfo;
import org.teavm.backend.wasm.debug.parser.DebugInfoParser;
import org.teavm.common.ByteArrayAsyncInputStream;
import org.teavm.common.Promise;
import org.teavm.debugging.information.DebugInformation;
import org.teavm.debugging.information.DebugInformationProvider;
import org.teavm.debugging.information.DebuggerCallSite;
import org.teavm.debugging.information.DebuggerCallSiteVisitor;
import org.teavm.debugging.information.DebuggerStaticCallSite;
import org.teavm.debugging.information.DebuggerVirtualCallSite;
import org.teavm.debugging.information.GeneratedLocation;
import org.teavm.debugging.information.SourceLocation;
import org.teavm.debugging.javascript.JavaScriptBreakpoint;
import org.teavm.debugging.javascript.JavaScriptCallFrame;
import org.teavm.debugging.javascript.JavaScriptDebugger;
import org.teavm.debugging.javascript.JavaScriptDebuggerListener;
import org.teavm.debugging.javascript.JavaScriptLocation;
import org.teavm.debugging.javascript.JavaScriptScript;
import org.teavm.debugging.javascript.JavaScriptValue;
import org.teavm.debugging.javascript.JavaScriptVariable;
import org.teavm.hppc.IntHashSet;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class Debugger {
   private Set<DebuggerListener> listeners = new LinkedHashSet<>();
   private JavaScriptDebugger javaScriptDebugger;
   private DebugInformationProvider debugInformationProvider;
   private List<JavaScriptBreakpoint> temporaryBreakpoints = new ArrayList<>();
   private Predicate<JavaScriptBreakpoint> temporaryBreakpointHandler;
   private Map<JavaScriptScript, DebugInformation> debugInformationMap = new HashMap<>();
   private Map<String, Set<DebugInformation>> debugInformationFileMap = new HashMap<>();
   private Map<JavaScriptScript, DebugInfo> wasmDebugInfoMap = new HashMap<>();
   private Map<DebugInfo, JavaScriptScript> wasmScriptMap = new HashMap<>();
   private Map<String, Set<DebugInfo>> wasmInfoFileMap = new HashMap<>();
   private Map<DebugInformation, JavaScriptScript> scriptMap = new HashMap<>();
   private Map<JavaScriptBreakpoint, Breakpoint> breakpointMap = new HashMap<>();
   private Set<Breakpoint> breakpoints = new LinkedHashSet<>();
   private Set<? extends Breakpoint> readonlyBreakpoints = Collections.unmodifiableSet(this.breakpoints);
   private CallFrame[] callStack;
   private Set<String> scriptNames = new LinkedHashSet<>();
   private Set<String> allSourceFiles = new LinkedHashSet<>();
   private StepLocationsFinder wasmStepLocationsFinder;
   private JavaScriptDebuggerListener javaScriptListener = new JavaScriptDebuggerListener() {
      @Override
      public void resumed() {
         Debugger.this.fireResumed();
      }

      @Override
      public void paused(JavaScriptBreakpoint breakpoint) {
         Debugger.this.firePaused(breakpoint);
      }

      @Override
      public void scriptAdded(JavaScriptScript script) {
         Debugger.this.addScript(script);
      }

      @Override
      public void attached() {
         Debugger.this.fireAttached();
      }

      @Override
      public void detached() {
         Debugger.this.fireDetached();
      }

      @Override
      public void breakpointChanged(JavaScriptBreakpoint jsBreakpoint) {
         Debugger.this.fireBreakpointChanged(jsBreakpoint);
      }
   };

   public Debugger(JavaScriptDebugger javaScriptDebugger, DebugInformationProvider debugInformationProvider) {
      this.javaScriptDebugger = javaScriptDebugger;
      this.debugInformationProvider = debugInformationProvider;
      javaScriptDebugger.addListener(this.javaScriptListener);
   }

   public JavaScriptDebugger getJavaScriptDebugger() {
      return this.javaScriptDebugger;
   }

   public void addListener(DebuggerListener listener) {
      this.listeners.add(listener);
   }

   public void removeListener(DebuggerListener listener) {
      this.listeners.remove(listener);
   }

   public Promise<Void> suspend() {
      return this.javaScriptDebugger.suspend();
   }

   public Promise<Void> resume() {
      return this.javaScriptDebugger.resume();
   }

   public Promise<Void> stepInto() {
      return this.step(true);
   }

   public Promise<Void> stepOut() {
      return this.javaScriptDebugger.stepOut();
   }

   public Promise<Void> stepOver() {
      return this.step(false);
   }

   private Promise<Void> jsStep(boolean enterMethod) {
      return enterMethod ? this.javaScriptDebugger.stepInto() : this.javaScriptDebugger.stepOver();
   }

   private Promise<Void> step(boolean enterMethod) {
      CallFrame[] callStack = this.getCallStack();
      if (callStack != null && callStack.length != 0) {
         CallFrame frame = callStack[0];
         if (frame.getLocation() != null && frame.getLocation().getFileName() != null && frame.getLocation().getLine() >= 0) {
            HashSet<JavaScriptLocation> successors = new HashSet<>();
            JavaScriptScript script = frame.getOriginalLocation().getScript();
            boolean hasSuccessors = false;
            if (frame.getLocation() != null && frame.getLocation().getFileName() != null && frame.getLocation().getLine() >= 0) {
               switch (script.getLanguage()) {
                  case JS:
                     hasSuccessors = this.addJsBreakpoints(frame, script, enterMethod, successors);
                     break;
                  case WASM:
                     Promise<Void> promise = this.stepWasm(frame, enterMethod);
                     if (promise != null) {
                        return promise;
                     }
               }
            }

            return hasSuccessors
               ? this.jsStep(enterMethod)
               : this.createTemporaryBreakpoints(successors, null).thenAsync(v -> this.javaScriptDebugger.stepOut());
         } else {
            return this.jsStep(enterMethod);
         }
      } else {
         return this.jsStep(enterMethod);
      }
   }

   private Promise<Void> createTemporaryBreakpoints(Collection<JavaScriptLocation> locations, Predicate<JavaScriptBreakpoint> handler) {
      ArrayList<Promise<Void>> jsBreakpointPromises = new ArrayList<>();

      for (JavaScriptLocation location : locations) {
         jsBreakpointPromises.add(this.javaScriptDebugger.createBreakpoint(location).thenVoid(this.temporaryBreakpoints::add));
      }

      this.temporaryBreakpointHandler = handler;
      return Promise.allVoid(jsBreakpointPromises);
   }

   private Promise<Void> destroyTemporaryBreakpoints() {
      ArrayList<JavaScriptBreakpoint> temporaryBreakpoints = new ArrayList<>(this.temporaryBreakpoints);
      this.temporaryBreakpoints.clear();
      ArrayList<Promise<Void>> promises = new ArrayList<>();

      for (JavaScriptBreakpoint jsBreakpoint : temporaryBreakpoints) {
         promises.add(jsBreakpoint.destroy());
      }

      this.callStack = null;
      return Promise.allVoid(promises);
   }

   private boolean addJsBreakpoints(CallFrame frame, JavaScriptScript script, boolean enterMethod, Set<JavaScriptLocation> successors) {
      DebugInformation debugInfo = this.debugInformationMap.get(script);
      if (debugInfo == null) {
         return false;
      }

      this.addFollowing(debugInfo, frame.getLocation(), script, new HashSet<>(), successors);
      if (enterMethod) {
         Debugger.CallSiteSuccessorFinder successorFinder = new Debugger.CallSiteSuccessorFinder(debugInfo, script, successors);
         DebuggerCallSite[] callSites = debugInfo.getCallSites(frame.getLocation());

         for (DebuggerCallSite callSite : callSites) {
            callSite.acceptVisitor(successorFinder);
         }
      }

      return true;
   }

   private Promise<Void> stepWasm(CallFrame frame, boolean enterMethod) {
      DebugInfo debugInfo = this.wasmDebugInfoMap.get(frame.getOriginalLocation().getScript());
      if (debugInfo != null && debugInfo.controlFlow() != null && debugInfo.lines() != null) {
         if (this.wasmStepLocationsFinder == null || this.wasmStepLocationsFinder.debugInfo != debugInfo) {
            this.wasmStepLocationsFinder = new StepLocationsFinder(debugInfo);
         }

         this.wasmStepLocationsFinder
            .step(frame.getLocation().getFileName(), frame.getLocation().getLine(), frame.getOriginalLocation().getColumn(), enterMethod);
         ArrayList<JavaScriptLocation> locations = new ArrayList<>();

         for (int breakpointAddress : this.wasmStepLocationsFinder.getBreakpointAddresses()) {
            locations.add(new JavaScriptLocation(frame.getOriginalLocation().getScript(), 0, breakpointAddress));
         }

         IntHashSet callAddresses = IntHashSet.from(this.wasmStepLocationsFinder.getCallAddresses());
         Promise<Void> result = this.createTemporaryBreakpoints(locations, br -> {
            if (br != null && br.isValid() && callAddresses.contains(br.getLocation().getColumn())) {
               this.destroyTemporaryBreakpoints().thenVoid(x -> this.javaScriptDebugger.stepInto());
               return true;
            } else {
               return false;
            }
         });
         return result.thenVoid(x -> this.javaScriptDebugger.stepOut());
      } else {
         return null;
      }
   }

   private boolean addFollowing(
      DebugInformation debugInfo, SourceLocation location, JavaScriptScript script, Set<SourceLocation> visited, Set<JavaScriptLocation> successors
   ) {
      if (!visited.add(location)) {
         return false;
      }

      SourceLocation[] following = debugInfo.getFollowingLines(location);
      boolean exits = false;
      if (following != null) {
         for (SourceLocation successor : following) {
            if (successor == null) {
               exits = true;
            } else {
               Collection<GeneratedLocation> genLocations = debugInfo.getGeneratedLocations(successor);
               if (!genLocations.isEmpty()) {
                  for (GeneratedLocation loc : genLocations) {
                     loc = debugInfo.getStatementLocation(loc);
                     successors.add(new JavaScriptLocation(script, loc.getLine(), loc.getColumn()));
                  }
               } else {
                  exits |= this.addFollowing(debugInfo, successor, script, visited, successors);
               }
            }
         }
      }

      return exits;
   }

   private List<DebugInformation> debugInformationBySource(String sourceFile) {
      Set<DebugInformation> list = this.debugInformationFileMap.get(sourceFile);
      return list != null ? new ArrayList<>(list) : Collections.emptyList();
   }

   private List<DebugInfo> wasmLineInfoBySource(String sourceFile) {
      Set<DebugInfo> list = this.wasmInfoFileMap.get(sourceFile);
      return list != null ? new ArrayList<>(list) : Collections.emptyList();
   }

   public Promise<Void> continueToLocation(SourceLocation location) {
      return this.continueToLocation(location.getFileName(), location.getLine());
   }

   public Promise<Void> continueToLocation(String fileName, int line) {
      if (!this.javaScriptDebugger.isSuspended()) {
         return Promise.VOID;
      }

      List<Promise<Void>> promises = new ArrayList<>();

      for (DebugInformation debugInformation : this.debugInformationBySource(fileName)) {
         for (GeneratedLocation location : debugInformation.getGeneratedLocations(fileName, line)) {
            JavaScriptLocation jsLocation = new JavaScriptLocation(this.scriptMap.get(debugInformation), location.getLine(), location.getColumn());
            promises.add(this.javaScriptDebugger.createBreakpoint(jsLocation).thenVoid(this.temporaryBreakpoints::add));
         }
      }

      return Promise.allVoid(promises).thenAsync(v -> this.javaScriptDebugger.resume());
   }

   public boolean isSuspended() {
      return this.javaScriptDebugger.isSuspended();
   }

   public Promise<Breakpoint> createBreakpoint(String file, int line) {
      return this.createBreakpoint(new SourceLocation(file, line));
   }

   public Collection<? extends String> getSourceFiles() {
      return this.allSourceFiles;
   }

   public Promise<Breakpoint> createBreakpoint(SourceLocation location) {
      Breakpoint breakpoint = new Breakpoint(this, location);
      this.breakpoints.add(breakpoint);
      return this.updateInternalBreakpoints(breakpoint).then(v -> {
         this.updateBreakpointStatus(breakpoint, false);
         return breakpoint;
      });
   }

   public Set<? extends Breakpoint> getBreakpoints() {
      return this.readonlyBreakpoints;
   }

   private Promise<Void> updateInternalBreakpoints(Breakpoint breakpoint) {
      if (breakpoint.isDestroyed()) {
         return Promise.VOID;
      }

      ArrayList<Promise<Void>> promises = new ArrayList<>();

      for (JavaScriptBreakpoint jsBreakpoint : breakpoint.jsBreakpoints) {
         this.breakpointMap.remove(jsBreakpoint);
         promises.add(jsBreakpoint.destroy());
      }

      ArrayList<JavaScriptBreakpoint> jsBreakpoints = new ArrayList<>();
      SourceLocation location = breakpoint.getLocation();

      for (DebugInformation debugInformation : this.debugInformationBySource(location.getFileName())) {
         for (GeneratedLocation genLocation : debugInformation.getGeneratedLocations(location)) {
            JavaScriptLocation jsLocation = new JavaScriptLocation(this.scriptMap.get(debugInformation), genLocation.getLine(), genLocation.getColumn());
            promises.add(this.javaScriptDebugger.createBreakpoint(jsLocation).thenVoid(jsBreakpoint -> {
               jsBreakpoints.add(jsBreakpoint);
               this.breakpointMap.put(jsBreakpoint, breakpoint);
            }));
         }
      }

      for (DebugInfo wasmDebugInfo : this.wasmLineInfoBySource(location.getFileName())) {
         if (wasmDebugInfo.lines() != null) {
            for (LineInfoSequence sequence : wasmDebugInfo.lines().sequences()) {
               for (InstructionLocation loc : sequence.unpack().locations()) {
                  if (loc.location() != null && loc.location().line() == location.getLine() && loc.location().file().fullName().equals(location.getFileName())) {
                     JavaScriptLocation jsLocation = new JavaScriptLocation(this.wasmScriptMap.get(wasmDebugInfo), 0, loc.address() + wasmDebugInfo.offset());
                     promises.add(this.javaScriptDebugger.createBreakpoint(jsLocation).thenVoid(jsBreakpoint -> {
                        jsBreakpoints.add(jsBreakpoint);
                        this.breakpointMap.put(jsBreakpoint, breakpoint);
                     }));
                  }
               }
            }
         }
      }

      breakpoint.jsBreakpoints = jsBreakpoints;
      return Promise.allVoid(promises);
   }

   private DebuggerListener[] getListeners() {
      return this.listeners.toArray(new DebuggerListener[0]);
   }

   private void updateBreakpointStatus(Breakpoint breakpoint, boolean fireEvent) {
      boolean valid = false;

      for (JavaScriptBreakpoint jsBreakpoint : breakpoint.jsBreakpoints) {
         if (jsBreakpoint.isValid()) {
            valid = true;
         }
      }

      if (breakpoint.valid != valid) {
         breakpoint.valid = valid;
         if (fireEvent) {
            for (DebuggerListener listener : this.getListeners()) {
               listener.breakpointStatusChanged(breakpoint);
            }
         }
      }
   }

   public CallFrame[] getCallStack() {
      if (!this.isSuspended()) {
         return null;
      }

      if (this.callStack == null) {
         ArrayList<CallFrame> frames = new ArrayList<>();
         boolean wasEmpty = false;

         for (JavaScriptCallFrame jsFrame : this.javaScriptDebugger.getCallStack()) {
            DebugInformation debugInformation = null;
            DebugInfo wasmDebugInfo = null;
            List<Debugger.SourceLocationWithMethod> locations;
            switch (jsFrame.getLocation().getScript().getLanguage()) {
               case JS:
                  debugInformation = this.debugInformationMap.get(jsFrame.getLocation().getScript());
                  locations = this.mapJsFrames(jsFrame, debugInformation);
                  break;
               case WASM:
                  locations = this.mapWasmFrames(jsFrame);
                  if (!locations.isEmpty()) {
                     wasmDebugInfo = this.wasmDebugInfoMap.get(jsFrame.getLocation().getScript());
                  }
                  break;
               default:
                  locations = Collections.emptyList();
            }

            for (Debugger.SourceLocationWithMethod locWithMethod : locations) {
               SourceLocation loc = locWithMethod.loc;
               MethodReference method = locWithMethod.method;
               if (!locWithMethod.empty || !wasEmpty) {
                  frames.add(new CallFrame(this, jsFrame, loc, method, debugInformation, wasmDebugInfo));
               }

               wasEmpty = locWithMethod.empty;
            }
         }

         this.callStack = frames.toArray(new CallFrame[0]);
      }

      return (CallFrame[])this.callStack.clone();
   }

   private List<Debugger.SourceLocationWithMethod> mapJsFrames(JavaScriptCallFrame frame, DebugInformation debugInformation) {
      SourceLocation loc;
      if (debugInformation != null) {
         loc = debugInformation.getSourceLocation(frame.getLocation().getLine(), frame.getLocation().getColumn());
      } else {
         loc = null;
      }

      boolean empty = loc == null || loc.getFileName() == null && loc.getLine() < 0;
      MethodReference method = !empty && debugInformation != null
         ? debugInformation.getMethodAt(frame.getLocation().getLine(), frame.getLocation().getColumn())
         : null;
      return Collections.singletonList(new Debugger.SourceLocationWithMethod(empty, loc, method));
   }

   private List<Debugger.SourceLocationWithMethod> mapWasmFrames(JavaScriptCallFrame frame) {
      DebugInfo debugInfo = this.wasmDebugInfoMap.get(frame.getLocation().getScript());
      if (debugInfo == null) {
         return Collections.emptyList();
      }

      LineInfo lineInfo = debugInfo.lines();
      if (lineInfo == null) {
         return Collections.emptyList();
      }

      int address = frame.getLocation().getColumn() - debugInfo.offset();
      LineInfoSequence sequence = lineInfo.find(address);
      if (sequence == null) {
         return Collections.emptyList();
      }

      InstructionLocation instructionLocation = sequence.unpack().find(address);
      if (instructionLocation == null) {
         return Collections.emptyList();
      }

      Location location = instructionLocation.location();
      ArrayList<Debugger.SourceLocationWithMethod> result = new ArrayList<>();

      while (true) {
         SourceLocation loc = new SourceLocation(location.file().fullName(), location.line());
         InliningLocation inlining = location.inlining();
         MethodInfo method = inlining != null ? inlining.method() : sequence.method();
         result.add(new Debugger.SourceLocationWithMethod(false, loc, this.getMethodReference(method)));
         if (inlining == null) {
            return result;
         }

         location = inlining.location();
      }
   }

   private MethodReference getMethodReference(MethodInfo methodInfo) {
      return new MethodReference(methodInfo.cls().fullName(), methodInfo.name(), ValueType.VOID);
   }

   Promise<Map<String, Variable>> createVariables(JavaScriptCallFrame jsFrame, DebugInformation debugInformation) {
      return jsFrame.getVariables().then(jsVariables -> {
         Map<String, Variable> vars = new HashMap<>();

         for (Entry<String, ? extends JavaScriptVariable> entry : jsVariables.entrySet()) {
            JavaScriptVariable jsVar = entry.getValue();
            String[] names = this.mapVariable(entry.getKey(), jsFrame.getLocation());
            Value value = new JsValueImpl(this, debugInformation, jsVar.getValue());

            for (String name : names) {
               if (name == null) {
                  name = "js:" + jsVar.getName();
               }

               vars.put(name, new Variable(name, value));
            }
         }

         return Collections.unmodifiableMap(vars);
      });
   }

   Promise<Map<String, Variable>> createVariables(JavaScriptCallFrame jsFrame, DebugInfo debugInfo) {
      return jsFrame.getVariables().thenAsync(jsVariables -> {
         HashMap<String, Variable> vars = new HashMap<>();
         VariablesInfo variables = debugInfo.variables();
         ArrayList<Promise<Void>> promises = new ArrayList<>();
         if (variables != null) {
            int address = jsFrame.getLocation().getColumn();
            address -= debugInfo.offset();

            for (VariableRangeInfo range : variables.find(address)) {
               Promise<Map<String, ? extends JavaScriptVariable>> propertiesPromise = jsVariables.get("$var" + range.index()).getValue().getProperties();
               promises.add(propertiesPromise.<JavaScriptValue>then(prop -> {
                  JavaScriptVariable variable = prop.get("value");
                  return variable != null ? variable.getValue() : null;
               }).thenAsync(value -> {
                  if (value != null) {
                     String repr = value.getSimpleRepresentation();
                     if (repr.endsWith("n")) {
                        repr = repr.substring(0, repr.length() - 1);
                     }

                     long longValue = Long.parseLong(repr);
                     WasmValueImpl varValue = new WasmValueImpl(this, debugInfo, range.variable().type().asFieldType(), jsFrame, longValue);
                     Variable variable = new Variable(range.variable().name(), varValue);
                     vars.put(variable.getName(), variable);
                  }

                  return Promise.VOID;
               }));
            }
         }

         return Promise.allVoid(promises).then(x -> vars);
      });
   }

   private void addScript(JavaScriptScript script) {
      (switch (script.getLanguage()) {
         case JS -> this.addJavaScriptScript(script);
         case WASM -> this.addWasmScript(script);
         default -> Promise.VOID;
      }).thenVoid(v -> this.updateBreakpoints());
   }

   private Promise<Void> addJavaScriptScript(JavaScriptScript script) {
      DebugInformation debugInfo = this.debugInformationProvider.getDebugInformation(script.getUrl());
      if (debugInfo == null) {
         return Promise.VOID;
      }

      this.debugInformationMap.put(script, debugInfo);

      for (String sourceFile : debugInfo.getFilesNames()) {
         Set<DebugInformation> list = this.debugInformationFileMap.get(sourceFile);
         if (list == null) {
            list = new HashSet<>();
            this.debugInformationFileMap.put(sourceFile, list);
            this.allSourceFiles.add(sourceFile);
         }

         list.add(debugInfo);
      }

      this.scriptMap.put(debugInfo, script);
      return Promise.VOID;
   }

   private Promise<Void> addWasmScript(JavaScriptScript script) {
      return script.getSource().thenVoid(source -> {
         if (source != null) {
            Decoder decoder = Base64.getDecoder();
            ByteArrayAsyncInputStream reader = new ByteArrayAsyncInputStream(decoder.decode(source));
            DebugInfoParser parser = new DebugInfoParser(reader);

            try {
               reader.readFully(parser::parse);
            } catch (Throwable e) {
               e.printStackTrace();
            }

            DebugInfo debugInfo = parser.getDebugInfo();
            if (debugInfo != null) {
               this.wasmDebugInfoMap.put(script, debugInfo);
               this.wasmScriptMap.put(debugInfo, script);
               if (debugInfo.lines() != null) {
                  for (LineInfoSequence sequence : debugInfo.lines().sequences()) {
                     for (LineInfoCommand command : sequence.commands()) {
                        if (command instanceof LineInfoFileCommand) {
                           FileInfo file = ((LineInfoFileCommand)command).file();
                           if (file != null) {
                              this.addWasmInfoFile(file.fullName(), debugInfo);
                           }
                        }
                     }
                  }
               }
            }
         }
      });
   }

   private void addWasmInfoFile(String sourceFile, DebugInfo debugInfo) {
      Set<DebugInfo> list = this.wasmInfoFileMap.get(sourceFile);
      if (list == null) {
         list = new HashSet<>();
         this.wasmInfoFileMap.put(sourceFile, list);
      }

      list.add(debugInfo);
      this.allSourceFiles.add(sourceFile);
   }

   public Set<? extends String> getScriptNames() {
      return this.scriptNames;
   }

   private void updateBreakpoints() {
      for (Breakpoint breakpoint : this.breakpoints) {
         this.updateInternalBreakpoints(breakpoint).thenVoid(v -> this.updateBreakpointStatus(breakpoint, true));
      }
   }

   public boolean isAttached() {
      return this.javaScriptDebugger.isAttached();
   }

   public void detach() {
      this.javaScriptDebugger.detach();
   }

   void destroyBreakpoint(Breakpoint breakpoint) {
      for (JavaScriptBreakpoint jsBreakpoint : breakpoint.jsBreakpoints) {
         jsBreakpoint.destroy();
         this.breakpointMap.remove(jsBreakpoint);
      }

      breakpoint.jsBreakpoints = new ArrayList<>();
      this.breakpoints.remove(breakpoint);
   }

   private void fireResumed() {
      for (DebuggerListener listener : this.getListeners()) {
         listener.resumed();
      }
   }

   private void firePaused(JavaScriptBreakpoint breakpoint) {
      Predicate<JavaScriptBreakpoint> handler = this.temporaryBreakpointHandler;
      this.temporaryBreakpointHandler = null;
      this.callStack = null;
      Breakpoint javaBreakpoint = null;
      JavaScriptBreakpoint tmpBreakpoint = null;
      if (breakpoint != null) {
         if (this.temporaryBreakpoints.contains(breakpoint)) {
            tmpBreakpoint = breakpoint;
         } else {
            javaBreakpoint = this.breakpointMap.get(breakpoint);
         }
      }

      if (handler == null || !handler.test(tmpBreakpoint)) {
         Breakpoint pausedAtBreakpoint = javaBreakpoint;
         this.destroyTemporaryBreakpoints().thenVoid(v -> {
            for (DebuggerListener listener : this.getListeners()) {
               listener.paused(pausedAtBreakpoint);
            }
         });
      }
   }

   private void fireAttached() {
      for (Breakpoint breakpoint : this.breakpoints) {
         this.updateInternalBreakpoints(breakpoint).thenVoid(v -> this.updateBreakpointStatus(breakpoint, false));
      }

      for (DebuggerListener listener : this.getListeners()) {
         listener.attached();
      }
   }

   private void fireDetached() {
      for (Breakpoint breakpoint : this.breakpoints) {
         this.updateBreakpointStatus(breakpoint, false);
      }

      for (DebuggerListener listener : this.getListeners()) {
         listener.detached();
      }
   }

   private void fireBreakpointChanged(JavaScriptBreakpoint jsBreakpoint) {
      Breakpoint breakpoint = this.breakpointMap.get(jsBreakpoint);
      if (breakpoint != null) {
         this.updateBreakpointStatus(breakpoint, true);
      }
   }

   String[] mapVariable(String variable, JavaScriptLocation location) {
      DebugInformation debugInfo = this.debugInformationMap.get(location.getScript());
      return debugInfo == null ? new String[0] : debugInfo.getVariableMeaningAt(location.getLine(), location.getColumn(), variable);
   }

   String mapField(String className, String jsField) {
      for (DebugInformation debugInfo : this.debugInformationMap.values()) {
         String meaning = debugInfo.getFieldMeaning(className, jsField);
         if (meaning != null) {
            return meaning;
         }
      }

      return null;
   }

   static class CallSiteSuccessorFinder implements DebuggerCallSiteVisitor {
      private DebugInformation debugInfo;
      private JavaScriptScript script;
      Set<JavaScriptLocation> locations;

      CallSiteSuccessorFinder(DebugInformation debugInfo, JavaScriptScript script, Set<JavaScriptLocation> locations) {
         this.debugInfo = debugInfo;
         this.script = script;
         this.locations = locations;
      }

      @Override
      public void visit(DebuggerVirtualCallSite callSite) {
         for (MethodReference potentialMethod : this.debugInfo.getOverridingMethods(callSite.getMethod())) {
            for (GeneratedLocation loc : this.debugInfo.getMethodEntrances(potentialMethod)) {
               loc = this.debugInfo.getStatementLocation(loc);
               this.locations.add(new JavaScriptLocation(this.script, loc.getLine(), loc.getColumn()));
            }
         }
      }

      @Override
      public void visit(DebuggerStaticCallSite callSite) {
         for (GeneratedLocation loc : this.debugInfo.getMethodEntrances(callSite.getMethod())) {
            loc = this.debugInfo.getStatementLocation(loc);
            this.locations.add(new JavaScriptLocation(this.script, loc.getLine(), loc.getColumn()));
         }
      }
   }

   private static class SourceLocationWithMethod {
      private final boolean empty;
      private final SourceLocation loc;
      private final MethodReference method;

      SourceLocationWithMethod(boolean empty, SourceLocation loc, MethodReference method) {
         this.empty = empty;
         this.loc = loc;
         this.method = method;
      }
   }
}
