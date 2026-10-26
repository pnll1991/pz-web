package port.compat.java.nio.file;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.teavm.jso.*;
public final class WatchService implements Closeable {
    private final Map<Path,WatchKey> keys=new HashMap<>();private final ArrayDeque<WatchKey> queue=new ArrayDeque<>();
    private boolean closed;private JSObject detach;
    public WatchService(){this(true);}
    public WatchService(boolean browser){if(browser)detach=port.shim.BrowserWatch.attach(this::change);}
    private void check(){if(closed)throw new ClosedWatchServiceException();}
    public synchronized WatchKey register(Path path,WatchEvent.Kind<?>[] events,WatchEvent.Modifier[] modifiers)throws IOException{
        check();Objects.requireNonNull(events);Objects.requireNonNull(modifiers);if(modifiers.length!=0)throw new UnsupportedOperationException("VFS WatchService modifiers");
        Set<WatchEvent.Kind<?>> kinds=new HashSet<>();for(var event:events){Objects.requireNonNull(event);if(event==StandardWatchEventKinds.OVERFLOW)continue;if(event!=StandardWatchEventKinds.ENTRY_CREATE&&event!=StandardWatchEventKinds.ENTRY_DELETE&&event!=StandardWatchEventKinds.ENTRY_MODIFY)throw new UnsupportedOperationException("Unknown WatchEvent.Kind: "+event.name());kinds.add(event);}
        if(kinds.isEmpty())throw new IllegalArgumentException("No watch events");path=path.toAbsolutePath().normalize();if(!Files.isDirectory(path))throw new NotDirectoryException(path.toString());
        WatchKey key=keys.get(path);if(key==null){key=new WatchKey(this,path,kinds);keys.put(path,key);}else key.kinds=kinds;return key;
    }
    synchronized void enqueue(WatchKey key){if(!key.signalled){key.signalled=true;queue.add(key);notifyAll();}}
    synchronized void unregister(WatchKey key){keys.remove(key.directory,key);}
    public synchronized void change(String filename,String operation){if(closed)return;Path path=Path.of(filename);WatchKey key=keys.get(path.getParent());var kind=switch(operation){case "create"->StandardWatchEventKinds.ENTRY_CREATE;case "delete"->StandardWatchEventKinds.ENTRY_DELETE;case "modify"->StandardWatchEventKinds.ENTRY_MODIFY;default->throw new IllegalArgumentException(operation);};if(key!=null)key.change(kind,path.getFileName());if(operation.equals("delete")){var removed=keys.get(path);if(removed!=null){removed.cancel();enqueue(removed);}}}
    public synchronized WatchKey poll(){check();return queue.poll();}
    public synchronized WatchKey take()throws InterruptedException{check();while(queue.isEmpty()){wait();check();}return queue.remove();}
    public synchronized WatchKey poll(long time,TimeUnit unit)throws InterruptedException{check();long nanos=unit.toNanos(time),start=System.nanoTime();while(queue.isEmpty()){long left=nanos-(System.nanoTime()-start);if(left<=0)return null;TimeUnit.NANOSECONDS.timedWait(this,left);check();}return queue.remove();}
    public synchronized void close(){if(closed)return;closed=true;for(var key:new ArrayList<>(keys.values()))key.cancel();queue.clear();notifyAll();if(detach!=null)port.shim.BrowserWatch.detach(detach);}
}
