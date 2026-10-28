package port.compiler;

import java.util.Set;
import org.teavm.model.*;
import org.teavm.model.instructions.*;

/** Observe exact entry/normal return boundaries without replacing game logic. */
public final class StartupProgress implements ClassHolderTransformer {
    // Whole classes are instrumented during the round that hunts a parked fiber: the entry/return
    // labels are deduplicated in JS, so this reveals which call of a suspicious method never
    // returns without decompiling game code.
    private static final Set<String> CLASSES = Set.of(
        "zombie.GameWindow",
        "zombie.gameStates.MainScreenState",
        "zombie.gameStates.GameStateMachine",
        "zombie.Lua.LuaManager",
        "zombie.core.opengl.RenderThread",
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
        "java.lang.Thread",
        // Round 30: PZXmlUtil#write() reaches createNewDocument():returned and then nothing. Its
        // next calls are the JAXB marshaller allocator and the JAXBContext lookup, so those are
        // now observed explicitly (entry-only classes, no name guessing).
        "zombie.util.PZXmlUtil$MarshallerAllocator",
        "zombie.util.PZXmlUtil$MarshallerAllocator$1",
        "zombie.util.PZXmlUtil$UnmarshallerAllocator");
    // Round 31: the two fibers are parked and nothing advances, but the deduplicated label list
    // cannot say whether a boundary repeats. The render fiber's last boundary is
    // Thread#sleep(J)V:entered without a return, so the calls that could reach a sleep inside
    // renderLoop's next iteration are now observed explicitly: the platform display cycle
    // (LWJGL/ GLFW is port territory), the render-queue handoff, and TeaVM's own monitor wait/
    // notify (java.lang.Object is TeaVM's classlib, so observing it is not touching the game).
    private static final Set<String> EXTRA_CLASSES = Set.of(
        "org.lwjglx.opengl.Sync",
        "zombie.core.opengl.RenderContextQueueItem",
        // Round 31: the page created exactly ONE setTimeout (the renderThread start) and zero
        // Fiber#resume, yet Thread#sleep(J)V:entered never returned. Instrument TeaVM's own sleep
        // implementation and its completion path (whole classes, so no descriptor guessing) to see
        // which scheduling branch runs and whether the pending callback is ever completed.
        "java.lang.Thread$SleepHandler",
        "org.teavm.runtime.Fiber$AsyncCallbackImpl");
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
        "zombie.core.opengl.RenderContextQueueItem#waitUntilFinished(Ljava/util/function/BooleanSupplier;)V",
        "zombie.core.opengl.RenderContextQueueItem#notifyWaitingListeners()V",
        "zombie.core.SpriteRenderer#notifyRenderStateQueue()V",
        "zombie.core.SpriteRenderer#renderQueued()V",
        "zombie.core.SpriteRenderer#NewFrame()V",
        "zombie.core.SpriteRenderer#isWaitingForRenderState()Z",
        "zombie.core.SpriteRenderer#waitForReadyState(Ljava/util/function/BooleanSupplier;)Z",
        "zombie.core.SpriteRenderer#waitForReadySlotToOpen()V",
        // TeaVM's own monitor primitives, named after the @Rename the classlib applies, so these
        // keys match what the transformer sees. Counting them shows who waits and who notifies.
        "java.lang.Object#wait()V",
        "java.lang.Object#wait(J)V",
        "java.lang.Object#wait(JI)V",
        "java.lang.Object#notify()V",
        "java.lang.Object#notifyAll()V",
        // Our own cooperative yield: if its count grows, the render fiber is iterating.
        "port.runtime.JdkSupport#yield()V",
        "port.runtime.JdkSupport#onSpinWait()V",
        // Round 31: the render fiber's last boundary is Thread#sleep(J)V:entered and no timer ever
        // resumes it, so observe TeaVM's own scheduler (its classes, not the game's) to see whether
        // an event is offered and whether the fiber is ever resumed.
        "org.teavm.runtime.Fiber#suspend(Lorg/teavm/runtime/Fiber$AsyncCall;)Ljava/lang/Object;",
        "org.teavm.runtime.Fiber#start()V",
        "org.teavm.runtime.Fiber#resume()V",
        "org.teavm.runtime.EventQueue#offer(Lorg/teavm/runtime/EventQueue$Event;)I",
        "org.teavm.runtime.EventQueue#offer(Lorg/teavm/runtime/EventQueue$Event;J)I",
        "org.teavm.runtime.EventQueue#processSingle()J",
        "org.teavm.runtime.EventQueue#stop()V",
        // Round 31: the JS side proved there is exactly ONE setTimeout in the whole page (the
        // renderThread start) and zero resumes, so Thread#sleep(J)V:entered suspends a fiber whose
        // async implementation never registers a timer. Instrument that implementation and the
        // completion path to see which of the two branches (EventQueue.offer vs Platform.schedule)
        // runs, and whether the pending callback is ever completed.
        "java.lang.Thread#sleep(JLorg/teavm/interop/AsyncCallback;)V",
        "org.teavm.platform.Platform#schedule(Lorg/teavm/platform/PlatformRunnable;I)I",
        "org.teavm.platform.Platform#postpone(Lorg/teavm/platform/PlatformRunnable;)V");
    private static final Set<String> METHODS = Set.of(
        // Round 30: the JAXB path (JAXBContext.newInstance -> ContextFinder.find) uses reflection
        // and ServiceLoader, both of which are async in TeaVM and therefore able to park a fiber.
        // These are entry-only observations of TeaVM's own classlib, not game code.
        "java.util.ServiceLoader#load(Ljava/lang/Class;)Ljava/util/ServiceLoader;",
        "java.util.ServiceLoader#loadIterator()Ljava/util/Iterator;",
        "java.lang.Class#forName(Ljava/lang/String;)Ljava/lang/Class;",
        "java.lang.Class#getMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;",
        "java.lang.reflect.Method#invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
        "java.lang.ClassLoader#getResource(Ljava/lang/String;)Ljava/net/URL;",
        // ThreadLocal.get() is used by PZXmlUtil (documentBuilders and MarshallerAllocator are
        // ThreadLocal.withInitial) and is async in TeaVM outside the main thread. Method-only so
        // the bounded ring is not flooded by every ThreadLocal call in the boot.
        "java.lang.ThreadLocal#get()Ljava/lang/Object;",
        "java.lang.ThreadLocal#initialValue()Ljava/lang/Object;",
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
        "zombie.core.opengl.RenderThread#renderLoop()V",
        "zombie.core.opengl.RenderThread#Ready()V",
        "zombie.gameStates.MainScreenState#update()Lzombie/gameStates/GameStateMachine$StateAction;",
        "zombie.gameStates.MainScreenState#preloadBackgroundTextures()V",
        "zombie.gameStates.MainScreenState#loadIcons()Lorg/lwjgl/glfw/GLFWImage$Buffer;",
        "zombie.gameStates.GameStateMachine#update()V",
        "zombie.gameStates.GameStateMachine#enterState()V",
        "zombie.gameStates.GameStateMachine#render()V");
    @Override public void transformClass(ClassHolder cls,ClassHolderTransformerContext context) {
        for(var method:cls.getMethods()) {
            String key=cls.getName()+"#"+method.getDescriptor();
            if(!METHODS.contains(key)&&!EXTRA_METHODS.contains(key)&&!CLASSES.contains(cls.getName())
                &&!EXTRA_CLASSES.contains(cls.getName())&&!own(cls.getName()))continue;
            if(method.getProgram()==null)continue;
            Program program=method.getProgram();
            Instruction first=program.basicBlockAt(0).getFirstInstruction();
            if(first==null)throw new IllegalStateException("Empty observed entry: "+key);
            record(program,first,key+":entered");
            for(var block:program.getBasicBlocks()) {
                for(Instruction i=block.getFirstInstruction();i!=null;i=i.getNext()) {
                    if(i instanceof ExitInstruction)record(program,i,key+":returned");
                }
            }
        }
    }
    private static void record(Program program,Instruction before,String label) {
        var value=program.createVariable();
        var constant=new StringConstantInstruction();constant.setConstant(label);constant.setReceiver(value);
        var call=new InvokeInstruction();call.setType(InvocationType.SPECIAL);
        call.setMethod(new MethodReference("port.shim.BrowserProgress","record",ValueType.object("java.lang.String"),ValueType.VOID));
        call.setArguments(value);
        before.insertPrevious(constant);before.insertPrevious(call);
        var traceValue=program.createVariable();
        var traceConstant=new StringConstantInstruction();traceConstant.setConstant(label);traceConstant.setReceiver(traceValue);
        var traceCall=new InvokeInstruction();traceCall.setType(InvocationType.SPECIAL);
        traceCall.setMethod(new MethodReference("port.shim.BrowserProgress","trace",ValueType.object("java.lang.String"),ValueType.VOID));
        traceCall.setArguments(traceValue);
        before.insertPrevious(traceConstant);before.insertPrevious(traceCall);
    }
}
