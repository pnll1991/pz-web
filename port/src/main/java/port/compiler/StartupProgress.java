package port.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.teavm.model.*;
import org.teavm.model.instructions.*;

/** Observe exact entry/normal return boundaries without replacing game logic. */
public final class StartupProgress implements ClassHolderTransformer {
    // Round 32: a boundary is emitted as an INT id, never as a String. A resumed fiber on WasmGC
    // re-enters the instrumented body mid-way and the String constant injected in that frame is
    // still unmaterialized, so the JS conversion crashed the module (diag56: record <-
    // org.teavm.runtime.Fiber#suspend). The id -> label table is written by BuildCompiler to
    // target/web/game/pulse-map.json and game.mjs loads it as globalThis.pzPulseLabels, so the
    // browser-side diagnostics keep their label shape while the wasm call needs no String.
    private static final List<String> LABELS = new ArrayList<>();
    static int idOf(String label) {
        int index = LABELS.indexOf(label);
        if (index < 0) { index = LABELS.size(); LABELS.add(label); }
        return index;
    }
    /** JSON array of labels, indexed by the int passed to port.shim.BrowserProgress.pulse(int). */
    public static String pulseMapJson() {
        var json = new StringBuilder("[");
        for (int i = 0; i < LABELS.size(); i++) {
            if (i > 0) json.append(',');
            json.append('"').append(LABELS.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return json.append(']').append('\n').toString();
    }
    // Whole classes are instrumented during the round that hunts a parked fiber: the entry/return
    // labels are deduplicated in JS, so this reveals which call of a suspicious method never
    // returns without decompiling game code.
    private static final Set<String> CLASSES = Set.of(
        "zombie.GameWindow",
        "zombie.gameStates.GameStateMachine",
        "zombie.Lua.LuaManager",
        // Everything GameWindow.mainThreadInit() calls that can block or start another fiber.
        "zombie.ZomboidFileSystem",
        "zombie.DebugFileWatcher",
        "zombie.GameProfiler",
        "zombie.debug.DebugOptions",
        "zombie.core.raknet.VoiceManager",
        "zombie.SoundManager",
        "zombie.DummySoundManager",
        "zombie.AmbientStreamManager",
        "zombie.DummyAmbientStreamManager",
        "zombie.audio.DummySoundBank",
        // Round 29 close: the ring stops right after ZomboidFileSystem#getMessagingDirSub()
        // returned, inside DebugOptions#initMessaging(). These are the calls that live between
        // that return and DebugFileWatcher#add(): the watcher, the XML classes, and java.io via
        // our own VFS. java.lang.Thread is instrumented so a sleep/yield/wait/jield park is
        // visible instead of inferred.
        "zombie.PredicatedFileWatcher",
        "zombie.debug.DebugOptionsXml",
        "zombie.util.PZXmlUtil",
        "zombie.XMLDocument",
        "java.io.File",
        "java.io.FileOutputStream",
        "java.io.BufferedWriter",
        "java.io.OutputStreamWriter",
        "java.io.FileWriter",
        // Round 32: java.lang.Thread is deliberately NOT instrumented. TeaVM's Thread#sleep/yield
        // suspend the fiber, and the transformer injects code into the frame that TeaVM re-enters
        // after a resume; that corrupted the WasmGC fiber stack (round 31: null String; round 32:
        // RuntimeError "array element access out of bounds" in Fiber::popInt via Thread::currentThread).
        // Suspend/resume counts now come from the JS side (pzYield/pzTimers/pzRuntime).
        // Round 30: PZXmlUtil#write() reaches createNewDocument():returned and then nothing. Its
        // next calls are the JAXB marshaller allocator and the JAXBContext lookup, so those are
        // now observed explicitly (entry-only classes, no name guessing).
        "zombie.util.PZXmlUtil$MarshallerAllocator",
        "zombie.util.PZXmlUtil$MarshallerAllocator$1",
        "zombie.util.PZXmlUtil$UnmarshallerAllocator");
    // Round 42: GameWindow#initFonts()V:entered has no return and the DebugLog is silent. With the
    // inflate fix in place the font .fnt files parse and no texture fails, so the question is now
    // whether the async font/image loads progress or the main fiber blocks waiting for them.
    // These are the classes on that path only (no suspend/resume machinery, per the note above).
    private static final Set<String> FONT_CLASSES = Set.of(
        "zombie.ui.TextManager",
        "zombie.core.fonts.AngelCodeFont",
        "zombie.core.textures.ImageData",
        "zombie.core.textures.PNGDecoder",
        "zombie.asset.FileTask_LoadImageData",
        "zombie.fileSystem.FileSystemImpl");
    // Round 31: the two fibers are parked and nothing advances, but the deduplicated label list
    // cannot say whether a boundary repeats. The render fiber's last boundary is
    // Thread#sleep(J)V:entered without a return, so the calls that could reach a sleep inside
    // renderLoop's next iteration are now observed explicitly: the platform display cycle
    // (LWJGL/ GLFW is port territory), the render-queue handoff, and TeaVM's own monitor wait/
    // notify (java.lang.Object is TeaVM's classlib, so observing it is not touching the game).
    private static final Set<String> EXTRA_CLASSES = Set.of(
        "org.lwjglx.opengl.Sync");
    // Round 60: GameWindow#enter() never returns and the DebugLog stops at UI_Loading_ModelsAnimations
    // with the page alive (4427 swaps). ModelManager#initAnimationMeshes() loops while
    // isLoadingAnimationMeshes(), and ModelMesh#isReady() also needs the VBO, so the question is which
    // of those two predicates never clears. FONT_CLASSES above is DEAD code (the gate below only reads
    // METHODS/EXTRA_METHODS/CLASSES/EXTRA_CLASSES/own), so these go to EXTRA_CLASSES where the gate
    // actually sees them. Only the animation-loading classes: no per-frame renderer classes, or the
    // 400-event ring would lose the park point.
    private static final Set<String> ANIM_CLASSES = Set.of(
        "zombie.core.skinnedmodel.ModelManager",
        "zombie.core.skinnedmodel.model.ModelMesh",
        "zombie.core.skinnedmodel.model.ModelLoader",
        "zombie.core.skinnedmodel.model.FileTask_AbstractLoadModel",
        "zombie.core.skinnedmodel.model.FileTask_LoadAnimation");
    // Round 32: java.lang.Thread$SleepHandler and org.teavm.runtime.Fiber$AsyncCallbackImpl are
    // deliberately NOT instrumented: both run inside the suspend/resume machinery, and injected
    // code there corrupts the WasmGC fiber stack (see the removal of java.lang.Thread above).
    // Round 31: port.shim.BrowserYield is deliberately NOT instrumented. It is the method that
    // suspends the fiber, and on resume TeaVM re-enters that frame with the String constants the
    // transformer injected still unmaterialized, so record() crashed with
    // "dereferencing a null pointer" (see diag53/diag54 stack). Its counters now come from the
    // JS side (globalThis.pzYield.pauses/resumes in the @JSBody), which cannot crash.
    // Round 29 close: the exact park is now bounded. The last game boundary is
    // PZXmlUtil#createNewDocument()Lorg/w3c/dom/Document;:returned inside PZXmlUtil#write(), and
    // nothing after it is reached. Everything between that point and the file write is OUR compat
    // code (javax.xml/org.w3c.dom shims plus BrowserDom), so instrumenting it is not game code.
    // Only the XML write path is instrumented so the bounded ring keeps the park point instead of
    // filling with unrelated shim traffic. port.shim.BrowserProgress is deliberately excluded: it
    // is the recorder itself and would recurse.
    // Round 30: the prefix above produced ZERO boundaries, because the port.compat.* rename
    // happens BEFORE this transformer runs, so the names it sees are already the standard
    // javax.xml/org.w3c.dom/org.xml.sax ones. Instrument those names post-rename instead: the
    // ones we ship are our own code, and the rest (the JAXB shipped by the pinned jar) is
    // observed only, never replaced here.
    private static final Set<String> OWN_PREFIXES = Set.of(
        "javax.xml.",
        "org.w3c.dom.",
        "org.xml.sax.",
        "port.shim.BrowserDom");
    private static boolean own(String name) {
        for (String prefix : OWN_PREFIXES) if (name.startsWith(prefix)) return true;
        return false;
    }
    private static final Set<String> EXTRA_METHODS = Set.of(
        // Round 31: platform cycle of the render loop. renderLoop() calls Display.processMessages
        // only when rendering is disabled, and Display.sync()/update() are the paths that can
        // Thread.sleep/yield; isCloseRequested/swapBuffers prove whether the loop iterates.
        "org.lwjglx.opengl.Display#processMessages()V",
        "org.lwjglx.opengl.Display#update()V",
        "org.lwjglx.opengl.Display#update(Z)V",
        "org.lwjglx.opengl.Display#sync(I)V",
        "org.lwjglx.opengl.Display#swapBuffers()V",
        "org.lwjglx.opengl.Display#isCloseRequested()Z",
        "org.lwjglx.opengl.Display#getWidth()I",
        "org.lwjglx.opengl.Display#getHeight()I",
        // Round 31: the handoff that is actually stuck. The game fiber waits on
        // RenderContextQueueItem.waitLock; invoke() is the only thing that sets isFinished and
        // notifyAll, so its count decides whether the render fiber ever ran the pending item.
        "zombie.core.opengl.RenderContextQueueItem#invoke()V",
        "zombie.core.opengl.RenderContextQueueItem#notifyWaitingListeners()V",
        "zombie.core.SpriteRenderer#notifyRenderStateQueue()V",
        "zombie.core.SpriteRenderer#renderQueued()V",
        "zombie.core.SpriteRenderer#NewFrame()V",
        "zombie.core.SpriteRenderer#isWaitingForRenderState()Z",
        "zombie.core.SpriteRenderer#waitForReadyState(Ljava/util/function/BooleanSupplier;)Z",
        // Round 32 A/B: the frames of the suspending render fiber are NOT instrumented at all
        // (MainScreenState, RenderThread, JdkSupport#yield). Only their non-suspending callees are
        // observed below, so the injected pulses cannot be the corruptor of the fiber int stack.
        "zombie.core.opengl.RenderThread#flushInvokeQueue()V",
        "zombie.core.opengl.RenderThread#renderStep()V",
        "zombie.core.opengl.RenderThread#queueInvokeOnRenderContext(Lzombie/core/opengl/RenderContextQueueItem;)V",
        "zombie.core.opengl.RenderThread#invokeOnRenderContext(Ljava/lang/Runnable;)V",
        "zombie.core.opengl.RenderThread#lambda$invokeOnRenderContext$0()Z",
        "zombie.core.opengl.RenderThread#acquireContextReentrant()V",
        "zombie.core.opengl.RenderThread#acquireContextReentrantInternal()V",
        "zombie.core.opengl.RenderThread#releaseContextReentrant()V",
        "zombie.core.opengl.RenderThread#notifyRenderStateQueue()V",
        // Round 32: alloc/resetInternal/setWaiting/isFinished stand in for the whole class and for
        // waitUntilFinished (that one calls Object#wait, which suspends the fiber).
        "zombie.core.opengl.RenderContextQueueItem#alloc(Ljava/lang/Runnable;)Lzombie/core/opengl/RenderContextQueueItem;",
        "zombie.core.opengl.RenderContextQueueItem#resetInternal()V",
        "zombie.core.opengl.RenderContextQueueItem#setWaiting()V",
        "zombie.core.opengl.RenderContextQueueItem#isFinished()Z");
    // Round 32: removed from this set, all suspend/resume machinery where injected code corrupts the
    // WasmGC fiber stack on resume: java.lang.Object#wait/notify/notifyAll,
    // org.teavm.runtime.Fiber#suspend/start/resume, org.teavm.runtime.EventQueue#offer/processSingle/
    // stop, java.lang.Thread#sleep(J,AsyncCallback), org.teavm.platform.Platform#schedule/postpone.
    // Waits, notifies and suspends are still observable from the JS side (pzYield/pzTimers/pzRuntime).
    private static final Set<String> METHODS = Set.of(
        // Round 30: the JAXB path (JAXBContext.newInstance -> ContextFinder.find) uses reflection
        // and ServiceLoader, both of which are async in TeaVM and therefore able to park a fiber.
        // These are entry-only observations of TeaVM's own classlib, not game code.
        // ThreadLocal.get() is used by PZXmlUtil (documentBuilders and MarshallerAllocator are
        // ThreadLocal.withInitial) and is async in TeaVM outside the main thread. Method-only so
        // the bounded ring is not flooded by every ThreadLocal call in the boot.
        "zombie.core.logger.ExceptionLogger#logException(Ljava/lang/Throwable;Ljava/lang/String;)V",
        "zombie.Lua.LuaManager#init()V",
        "zombie.core.logger.LoggerManager#init()V",
        "zombie.core.physics.Bullet#init()V",
        "zombie.GameWindow#initShared()V",
        "zombie.GameWindow#InitGameThread()V",
        "zombie.GameWindow#init()V",
        "zombie.gameStates.MainScreenState#enter()V",
        "zombie.gameStates.MainScreenState#render()V",
        "org.lwjglx.opengl.Display#init()V",
        "org.lwjglx.opengl.Display#create()V",
        // Round 29: the main fiber parks after GameWindow#InitGameThread()V:returned with no
        // pending setTimeout (measured: 120 s, frozen GL/swap/window counters, JS still free), so
        // the boundary set now covers the thread handoff and the first frame of the main loop to
        // find which call never returns.
        "zombie.GameWindow#mainThreadInit()V",
        "zombie.GameWindow#mainThreadStart()V",
        "zombie.GameWindow#mainThreadStep()V",
        "zombie.GameWindow#mainThreadExit()V",
        "zombie.GameWindow#enter()V",
        "zombie.GameWindow#frameStep()V",
        "zombie.GameWindow#initFonts()V",
        "zombie.GameWindow#InitDisplay()V",
        "zombie.GameWindow#initState()V",
        "zombie.core.Core#init()V",
        "zombie.core.opengl.RenderThread#init()V",
        "zombie.core.opengl.RenderThread#Ready()V",
        "zombie.gameStates.MainScreenState#update()Lzombie/gameStates/GameStateMachine$StateAction;",
        "zombie.gameStates.MainScreenState#preloadBackgroundTextures()V",
        "zombie.gameStates.MainScreenState#loadIcons()Lorg/lwjgl/glfw/GLFWImage$Buffer;",
        "zombie.gameStates.GameStateMachine#update()V",
        "zombie.gameStates.GameStateMachine#enterState()V",
        "zombie.gameStates.GameStateMachine#render()V");
    // Round 32: the port's cooperative yield must not suspend the render fiber while a
    // render-context item is in flight, because the game fiber is parked in
    // RenderContextQueueItem#waitUntilFinished -> Object#wait at that moment and TeaVM 0.16.0 keeps
    // a single static Fiber.lastPendingCall (verified from teavm-core 0.16.0). These injections only
    // set a flag used by port.runtime.JdkSupport.yield(); they change no game rule and no game state.
    private static final java.util.Map<String,String> PENDING_FLAG = java.util.Map.of(
        "zombie.core.opengl.RenderContextQueueItem#setWaiting()V","true",
        "zombie.core.opengl.RenderContextQueueItem#invoke()V","false");
    @Override public void transformClass(ClassHolder cls,ClassHolderTransformerContext context) {
        // Round 32 A/B: PZ_PULSE_OFF=1 disables the boundary pulses entirely, so the Chrome
        // diagnostics can prove whether the "array element access out of bounds" in
        // Fiber::popInt <- Thread::currentThread <- Fiber::suspend comes from our injected
        // pulses or from TeaVM's own fiber replay. Nothing else changes.
        if(System.getenv("PZ_PULSE_OFF")!=null)return;
        for(var method:cls.getMethods()) {
            String key=cls.getName()+"#"+method.getDescriptor();
            if(!METHODS.contains(key)&&!EXTRA_METHODS.contains(key)&&!CLASSES.contains(cls.getName())
                &&!EXTRA_CLASSES.contains(cls.getName())&&!ANIM_CLASSES.contains(cls.getName())
                &&!own(cls.getName()))continue;
            if(method.getProgram()==null)continue;
            Program program=method.getProgram();
            Instruction first=program.basicBlockAt(0).getFirstInstruction();
            if(first==null)throw new IllegalStateException("Empty observed entry: "+key);
            record(program,first,key+":entered");
            String flag=PENDING_FLAG.get(key);
            if(flag!=null){
                for(var block:program.getBasicBlocks()) {
                    for(Instruction i=block.getFirstInstruction();i!=null;i=i.getNext()) {
                        if(i instanceof ExitInstruction)sideEffect(program,i,flag.equals("true"));
                    }
                }
            }
            for(var block:program.getBasicBlocks()) {
                for(Instruction i=block.getFirstInstruction();i!=null;i=i.getNext()) {
                    if(i instanceof ExitInstruction)record(program,i,key+":returned");
                }
            }
        }
    }
    /**
     * Inject `port.shim.BrowserYield.setRenderPending(value)` before an exit of an observed method.
     * Only the observer calls this, and the target is a plain Java method that forwards to a @JSBody,
     * which is the pattern this repo requires for injected JSO calls.
     */
    private static void sideEffect(Program program,Instruction before,boolean value) {
        var flag=program.createVariable();
        var constant=new IntegerConstantInstruction();constant.setConstant(value?1:0);constant.setReceiver(flag);
        var call=new InvokeInstruction();call.setType(InvocationType.SPECIAL);
        call.setMethod(new MethodReference("port.shim.BrowserYield","setRenderPending",ValueType.INTEGER,ValueType.VOID));
        call.setArguments(flag);
        before.insertPrevious(constant);before.insertPrevious(call);
    }
    private static void record(Program program,Instruction before,String label) {
        // One int-valued constant + one call replaces the previous two String injections
        // (record + trace). The JS side derives counts, the deduplicated progress list and the
        // bounded ring from that single pulse, and an int constant cannot be null in a replayed
        // frame. See the class comment for why Strings were unsafe here.
        var value=program.createVariable();
        var constant=new IntegerConstantInstruction();constant.setConstant(idOf(label));constant.setReceiver(value);
        var call=new InvokeInstruction();call.setType(InvocationType.SPECIAL);
        call.setMethod(new MethodReference("port.shim.BrowserProgress","pulse",ValueType.INTEGER,ValueType.VOID));
        call.setArguments(value);
        before.insertPrevious(constant);before.insertPrevious(call);
    }
}
