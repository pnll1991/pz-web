package port.compat.java.nio.file;
import java.nio.file.Path;
public final class StandardWatchEventKinds {
    public static final WatchEvent.Kind<Object> OVERFLOW=new Kind<>("OVERFLOW",Object.class);
    public static final WatchEvent.Kind<Path> ENTRY_CREATE=new Kind<>("ENTRY_CREATE",Path.class),ENTRY_DELETE=new Kind<>("ENTRY_DELETE",Path.class),ENTRY_MODIFY=new Kind<>("ENTRY_MODIFY",Path.class);
    private record Kind<T>(String name,Class<T> type)implements WatchEvent.Kind<T>{}
}
