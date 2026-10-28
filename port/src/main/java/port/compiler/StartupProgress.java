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
        "java.lang.Thread");
    // Round 29 close: the exact park is now bounded. The last game boundary is
    // PZXmlUtil#createNewDocument()Lorg/w3c/dom/Document;:returned inside PZXmlUtil#write(), and
    // nothing after it is reached. Everything between that point and the file write is OUR compat
    // code (javax.xml/org.w3c.dom shims plus BrowserDom), so instrumenting it is not game code.
    // Only the XML write path is instrumented so the bounded ring keeps the park point instead of
    // filling with unrelated shim traffic. port.shim.BrowserProgress is deliberately excluded: it
    // is the recorder itself and would recurse.
    private static final Set<String> OWN_PREFIXES = Set.of(
        "port.compat.javax.xml.",
        "port.shim.BrowserDom");
    private static boolean own(String name) {
        for (String prefix : OWN_PREFIXES) if (name.startsWith(prefix)) return true;
        return false;
    }
    private static final Set<String> METHODS = Set.of(
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
            if(!METHODS.contains(key)&&!CLASSES.contains(cls.getName())&&!own(cls.getName()))continue;
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
