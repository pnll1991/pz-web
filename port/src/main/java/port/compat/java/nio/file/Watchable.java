package port.compat.java.nio.file;
import java.io.IOException;
public interface Watchable {
    WatchKey register(WatchService service,WatchEvent.Kind<?>[] events,WatchEvent.Modifier... modifiers)throws IOException;
    WatchKey register(WatchService service,WatchEvent.Kind<?>... events)throws IOException;
}
