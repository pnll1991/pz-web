package port.compiler;

import org.teavm.model.*;
import org.teavm.model.emit.*;

/** Replace platform initialization, preserving the game's renderer and state machine. */
public final class WindowCompatibility implements ClassHolderTransformer {
    private static final String BRIDGE = "port.shim.BrowserWindow";
    private final java.util.Map<String,String> nativeNames=new java.util.HashMap<>();
    public WindowCompatibility() {
        try {
            for(String line:java.nio.file.Files.readAllLines(java.nio.file.Path.of(System.getProperty("pz.nativeIndex")))) {
                if(line.isBlank()||line.startsWith("#"))continue;
                String[] fields=line.split("\t",-1);
                if(fields.length!=4)throw new IllegalArgumentException("Invalid native index");
                nativeNames.put(fields[0]+"#"+fields[1]+fields[2],fields[3]);
            }
        } catch(java.io.IOException error) {throw new IllegalStateException("Missing native index",error);}
    }
    @Override public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (cls.getName().equals("org.lwjglx.opengl.Display")) {
            var method = cls.getMethod(new MethodDescriptor("init",ValueType.VOID));
            if (method == null) throw new IllegalStateException("Inventoried Display.init is missing");
            var e = ProgramEmitter.create(method,context.getHierarchy());
            e.when(e.getField(cls.getName(),"isInitialized",ValueType.BOOLEAN).isTrue()).thenDo(e::exit);
            e.invoke(new MethodReference(BRIDGE,"init",ValueType.VOID));
            var width = e.invoke(new MethodReference(BRIDGE,"desktopWidth",ValueType.INTEGER));
            var height = e.invoke(new MethodReference(BRIDGE,"desktopHeight",ValueType.INTEGER));
            var bits = e.invoke(new MethodReference(BRIDGE,"colorBits",ValueType.INTEGER));
            // The browser exposes no monitor refresh rate; preserve an unknown frequency.
            e.setField(cls.getName(),"desktopDisplayMode",e.construct("org.lwjglx.opengl.DisplayMode",width,height,bits,e.constant(0)));
            // Display.monitor holds a monitor handle, not the window handle: this backend's
            // single display is BrowserWindow.MONITOR_HANDLE. The window handle stays 0 until created.
            e.setField(cls.getName(),"monitor",e.constant(2L));
            e.invoke(new MethodReference("org.lwjglx.input.Keyboard","create",ValueType.VOID));
            e.setField(cls.getName(),"isInitialized",e.constant(1).cast(ValueType.BOOLEAN));
            e.exit();
        }
        if (!cls.getName().equals("org.lwjgl.glfw.GLFW")) return;
        var bridge = context.getHierarchy().getClassSource().get(BRIDGE);
        for (var method : cls.getMethods()) {
            // The monitor structs are built in IR: the bridge cannot import org.lwjgl types,
            // so a real GLFWVidMode/Buffer is constructed over one arena address holding the
            // single mode the page can present (the canvas drawing buffer).
            if (method.getDescriptor().equals(new MethodDescriptor("glfwGetVideoModes",
                    ValueType.object("org.lwjgl.glfw.GLFWVidMode$Buffer"), ValueType.LONG))) {
                var e = ProgramEmitter.create(method,context.getHierarchy());
                e.invoke(new MethodReference(BRIDGE,"checkMonitor",ValueType.VOID,ValueType.LONG),e.var(1,ValueType.LONG));
                var address = e.invoke(new MethodReference(BRIDGE,"videoModeAddress",ValueType.LONG));
                e.construct("org.lwjgl.glfw.GLFWVidMode$Buffer",address,e.constant(1)).returnValue();
                continue;
            }
            if (method.getDescriptor().equals(new MethodDescriptor("glfwGetVideoMode",
                    ValueType.object("org.lwjgl.glfw.GLFWVidMode"), ValueType.LONG))) {
                var e = ProgramEmitter.create(method,context.getHierarchy());
                e.invoke(new MethodReference(BRIDGE,"checkMonitor",ValueType.VOID,ValueType.LONG),e.var(1,ValueType.LONG));
                var address = e.invoke(new MethodReference(BRIDGE,"videoModeAddress",ValueType.LONG));
                e.construct("org.lwjgl.glfw.GLFWVidMode",address,
                    e.constantNull(java.nio.ByteBuffer.class)).returnValue();
                continue;
            }
            if (method.getName().equals("<clinit>")) {
                // The browser owns the backend; there is no host dynamic library.
                var e = ProgramEmitter.create(method,context.getHierarchy());
                e.invoke(new MethodReference(BRIDGE,"init",ValueType.VOID));e.exit();
            } else if (bridge.getMethod(method.getDescriptor()) != null && method.hasModifier(ElementModifier.STATIC)) {
                method.getModifiers().remove(ElementModifier.NATIVE);
                var e = ProgramEmitter.create(method,context.getHierarchy());
                var arguments = new ValueEmitter[method.parameterCount()];
                for(int i=0;i<arguments.length;i++) arguments[i]=e.var(i+1,method.parameterType(i));
                var result=e.invoke(new MethodReference(BRIDGE,method.getDescriptor()),arguments);
                if(method.getResultType()==ValueType.VOID)e.exit();else result.returnValue();
            } else if (!method.getName().equals("<init>")) {
                String key=cls.getName()+"#"+method.getDescriptor();
                String failure=nativeNames.getOrDefault(key,"Browser GLFW operation unavailable: "+key);
                method.getModifiers().remove(ElementModifier.NATIVE);
                var e=ProgramEmitter.create(method,context.getHierarchy());
                e.construct(UnsupportedOperationException.class,e.constant(failure)).raise();
            }
        }
    }
}
