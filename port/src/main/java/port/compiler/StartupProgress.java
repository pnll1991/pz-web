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
    // Round 61: the ring's last boundary is ModelManager#create()V:entered and create() never
    // returns. Its first statements are new Texture(1024,1024,16), new Texture(1024,1024,512) and
    // new TextureFBO(tex, depth, false) before initAnimationMeshes (see
    // /tmp/r60/out4/ModelManager.java:149-167), and the console's last line of that run is
    // "WebGL: INVALID_VALUE: clear: invalid mask", i.e. our FBO path. These are method-only keys
    // (descriptors from `javap -s` on the pinned jar) so the 400-event ring keeps the park point
    // instead of filling with per-frame bind()/render() traffic.
    private static final Set<String> FBO_CLASSES_METHODS = Set.of(
        "zombie.core.textures.Texture#<init>(III)V",
        "zombie.core.textures.Texture#<init>(Lzombie/core/textures/TextureID;Ljava/lang/String;)V",
        "zombie.core.textures.TextureID#<init>(III)V",
        "zombie.core.textures.TextureID#createTexture()V",
        "zombie.core.textures.TextureID#createTexture(Z)V",
        "zombie.core.textures.TextureID#generateHwId(Z)V",
        "zombie.core.textures.TextureFBO#<init>(Lzombie/interfaces/ITexture;Lzombie/interfaces/ITexture;Z)V",
        "zombie.core.textures.TextureFBO#init(Lzombie/interfaces/ITexture;Lzombie/interfaces/ITexture;Z)V",
        "zombie.core.textures.TextureFBO#initInternal(Lzombie/interfaces/ITexture;Lzombie/interfaces/ITexture;Z)V",
        "zombie.core.textures.TextureFBO#checkFBOSupport()Z",
        "zombie.core.textures.TextureFBO#getFuncs()Lzombie/core/textures/IGLFramebufferObject;",
        "zombie.core.textures.TextureFBO#attach(Lzombie/interfaces/ITexture;I)V",
        "zombie.core.textures.TextureFBO#startDrawing()V",
        "zombie.core.textures.TextureFBO#endDrawing()V",
        "zombie.core.textures.TextureFBODepth#<init>(Lzombie/interfaces/ITexture;)V",
        "zombie.core.textures.TextureFBODepth#initInternal(Lzombie/interfaces/ITexture;)V",
        "zombie.core.textures.TextureFBODepth#checkFBOSupport()Z",
        // The shim entrypoints the FBO path actually calls (org.lwjgl.opengl.GL11/GL30 natives are
        // patched to these wrappers, so the shim names are what exists post-rename). The clear mask
        // and the FBO attachment are what the WebGL console complained about.
        "port.shim.GL11#glClear(I)V",
        "port.shim.GL11#glClearColor(FFFF)V",
        "port.shim.GL30#glBindFramebuffer(II)V",
        "port.shim.GL30#glCheckFramebufferStatus(I)I",
        "port.shim.GL30#glFramebufferTexture2D(IIIII)V",
        "port.shim.GL30#nglGenFramebuffers(IJ)V",
        "port.shim.GL11#nglGenTextures(IJ)V",
        "port.shim.GL11#glTexImage2D(IIIIIIILjava/nio/ByteBuffer;)V",
        "port.shim.GL11#glTexParameteri(III)V");
    // Round 64: the boot now reaches UI_Loading_OnGameBoot (see /tmp/diag-models3.log), so the
    // next question is the state machine: does GameStateMachine#update() ever instantiate
    // MainScreenState, and does LuaManager#init() complete? Method-only keys (descriptors from
    // `javap -p -s` on the pinned jar: MainScreenState#enter()V, #render()V,
    // #update()Lzombie/gameStates/GameStateMachine$StateAction;, GameStateMachine#update()V,
    // #render()V, zombie.Lua.LuaManager#init()V and #LoadDirBase(Ljava/lang/String;)V; note the
    // class is zombie.Lua.LuaManager, NOT zombie.scripting.lua.LuaManager) so the counts show the
    // per-frame state traffic without pulling whole classes into the ring.
    private static final Set<String> MENU_CLASSES_METHODS = Set.of(
        "zombie.gameStates.GameStateMachine#update()V",
        "zombie.gameStates.GameStateMachine#render()V",
        // Ronda 64b: el state machine YA gira (GameStateMachine#update 18/18 en
        // /tmp/diag-menu65.log) pero `current` es el primer estado de la lista, TISLogoState, y
        // MainScreenState#enter nunca entra. Sin instrumentar los estados previos la sonda no puede
        // decir en qué estado está el arranque ni por qué no avanza (TISLogoState#update necesita
        // que alpha llegue a 1 con GameTime#getMultiplier y sostiene el logo 20 s).
        "zombie.gameStates.TISLogoState#enter()V",
        "zombie.gameStates.TISLogoState#update()Lzombie/gameStates/GameStateMachine$StateAction;",
        "zombie.gameStates.TISLogoState#render()V",
        "zombie.gameStates.TermsOfServiceState#enter()V",
        "zombie.gameStates.TermsOfServiceState#update()Lzombie/gameStates/GameStateMachine$StateAction;",
        "zombie.gameStates.TermsOfServiceState#render()V",
        "zombie.gameStates.MainScreenState#<init>()V",
        "zombie.gameStates.MainScreenState#enter()V",
        "zombie.gameStates.MainScreenState#render()V",
        "zombie.gameStates.MainScreenState#renderBackground()V",
        "zombie.gameStates.MainScreenState#update()Lzombie/gameStates/GameStateMachine$StateAction;",
        "zombie.gameStates.MainScreenState#preloadBackgroundTextures()V",
        "zombie.Lua.LuaManager#init()V",
        "zombie.Lua.LuaManager#LoadDirBase(Ljava/lang/String;)V",
        "zombie.Lua.LuaManager#initChecksum()V",
        // Ronda 74: ACCEPT del TOS no dispara aunque Java ve el boton 0 abajo. Estos contadores
        // dicen en que eslabon se corta Mouse#update -> UIManager#updateMouseButtons ->
        // UIElement#onConsumeMouseButtonDown/Up -> onMouseDown/Up (Lua) -> fromLua0("exit").
        "zombie.input.Mouse#update()V",
        "zombie.ui.UIManager#updateMouseButtons(II)I",
        "zombie.ui.UIElement#onConsumeMouseButtonDown(IDD)Z",
        "zombie.ui.UIElement#onConsumeMouseButtonUp(IDD)Z",
        "zombie.ui.UIElement#onMouseDown(DD)Ljava/lang/Boolean;",
        "zombie.ui.UIElement#onMouseUp(DD)Ljava/lang/Boolean;",
        "zombie.gameStates.TermsOfServiceState#fromLua0(Ljava/lang/String;)Ljava/lang/Object;",
        // Ronda 76: MainScreenState#enter entra y nunca vuelve, y wasm queda idle (fibra aparcada).
        "zombie.SoundManager#setMusicState(Ljava/lang/String;)V",
        "zombie.Lua.LuaEventManager#triggerEvent(Ljava/lang/String;)V",
        "zombie.core.textures.Texture#getSharedTexture(Ljava/lang/String;)Lzombie/core/textures/Texture;",
        "zombie.core.textures.Texture#getSharedTexture(Ljava/lang/String;I)Lzombie/core/textures/Texture;",
        "zombie.core.opengl.RenderThread#invokeOnRenderContext(Ljava/lang/Runnable;)V",
        "zombie.core.opengl.RenderThread#invokeOnRenderContext(Lzombie/core/opengl/RenderContextQueueItem;)V",
        "java.lang.management.ManagementFactory#getOperatingSystemMXBean()Ljava/lang/management/OperatingSystemMXBean;");
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
    // Ronda 64: el arranque se detiene dentro de la lectura del Guid table. El anillo termina en
    // javax.xml.bind.JAXBContext#resolve y PortXmlBeans/PortUnmarshaller NO estaban instrumentados
    // (own() no cubre port.runtime.*), así que la sonda no podía nombrar la llamada que no retorna.
    // Son código propio del puerto, no del juego, y no participan del mecanismo suspend/resume de la
    // fibra (a diferencia de port.shim.BrowserYield, que sigue excluido a propósito).
    // PortMarshaller se agrega por simetría: usa las mismas primitivas (fields/instantiate/assign).
    private static final Set<String> XML_BEAN_PREFIXES = Set.of(
        "port.runtime.PortXmlBeans",
        "port.runtime.PortUnmarshaller",
        "port.runtime.PortMarshaller");
    private static boolean own(String name) {
        for (String prefix : OWN_PREFIXES) if (name.startsWith(prefix)) return true;
        for (String prefix : XML_BEAN_PREFIXES) if (name.startsWith(prefix)) return true;
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
                &&!FBO_CLASSES_METHODS.contains(key)
                &&!MENU_CLASSES_METHODS.contains(key)
                &&!own(cls.getName()))continue;
            if(method.getProgram()==null)continue;
            Program program=method.getProgram();
            Instruction first=program.basicBlockAt(0).getFirstInstruction();
            // Ronda 75: argumentos del despacho del click (coordenadas que ve el hit-test).
            if(key.equals("zombie.ui.UIManager#updateMouseButtons(II)I")||key.equals("zombie.ui.UIElement#onMouseDown(DD)Ljava/lang/Boolean;")) {
                boolean ints=key.startsWith("zombie.ui.UIManager");
                var call=new InvokeInstruction();call.setType(InvocationType.SPECIAL);
                ValueType t=ints?ValueType.INTEGER:ValueType.DOUBLE;
                call.setMethod(new MethodReference("port.shim.BrowserProgress",ints?"traceInts":"traceDoubles",t,t,ValueType.VOID));
                call.setArguments(program.variableAt(1),program.variableAt(2));
                first.insertPrevious(call);
            }
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
