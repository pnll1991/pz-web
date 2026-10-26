package port.shim;

import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** A Java blocking call suspends its coroutine until the durable transaction ends. */
public final class BrowserPersistence {
    @Async public static native void flush();
    private static void flush(AsyncCallback<Void> callback) {
        flushAsync(() -> callback.complete(null), message -> callback.error(new java.io.IOException(message)));
    }
    @JSFunctor private interface Success extends JSObject { void complete(); }
    @JSFunctor private interface Failure extends JSObject { void error(String message); }
    @JSBody(params={"success", "failure"}, script="pzRuntime.vfs.flush().then(success,e=>failure(String(e)));")
    private static native void flushAsync(Success success, Failure failure);
}
