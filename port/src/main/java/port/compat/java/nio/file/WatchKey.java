package port.compat.java.nio.file;
import java.nio.file.Path;
import java.util.*;
public final class WatchKey {
    final WatchService service;final Path directory;
    Set<WatchEvent.Kind<?>> kinds;boolean valid=true,signalled;
    private final List<WatchEvent<?>> pending=new ArrayList<>();
    WatchKey(WatchService service,Path directory,Set<WatchEvent.Kind<?>> kinds){this.service=service;this.directory=directory;this.kinds=kinds;}
    public boolean isValid(){synchronized(service){return valid;}}
    public java.nio.file.Watchable watchable(){return directory;}
    public List<WatchEvent<?>> pollEvents(){synchronized(service){var result=new ArrayList<>(pending);pending.clear();return result;}}
    public boolean reset(){synchronized(service){if(!valid)return false;signalled=false;if(!pending.isEmpty())service.enqueue(this);return true;}}
    public void cancel(){synchronized(service){valid=false;service.unregister(this);}}
    void change(WatchEvent.Kind<Path> kind,Path relative){
        if(!valid||!kinds.contains(kind))return;
        if(pending.size()>=512){pending.clear();pending.add(new Event<>(StandardWatchEventKinds.OVERFLOW,null));}
        else{Event<?> previous=pending.isEmpty()?null:(Event<?>)pending.get(pending.size()-1);if(previous!=null&&previous.kind==kind&&Objects.equals(previous.context,relative))previous.count++;else pending.add(new Event<>(kind,relative));}
        service.enqueue(this);
    }
    private static final class Event<T> implements WatchEvent<T>{final Kind<T> kind;final T context;int count=1;Event(Kind<T> kind,T context){this.kind=kind;this.context=context;}public Kind<T> kind(){return kind;}public int count(){return count;}public T context(){return context;}}
}
