package port.shim;
import org.teavm.jso.*;
public final class BrowserWatch {
    @JSFunctor public interface Listener extends JSObject{void change(String path,String operation);}
    public static JSObject attach(Listener listener){return listen(listener);}
    public static void detach(JSObject detach){unlisten(detach);}
    @JSBody(params="listener",script="return pzRuntime.vfs.onChange(listener);")private static native JSObject listen(Listener listener);
    @JSBody(params="detach",script="detach();")private static native void unlisten(JSObject detach);
}
