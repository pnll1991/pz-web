package port.shim;
import java.io.*;
import java.net.*;
import java.util.*;
import org.teavm.interop.*;
import org.teavm.jso.*;
import org.teavm.jso.typedarrays.Uint8Array;

/** Read-through classpath URLs retain classpath order and suspend only for local IO. */
public final class BrowserResources {
    @Async private static native String resolve(String name)throws IOException;
    private static void resolve(String name,AsyncCallback<String> callback){lookup(name,callback::complete,message->callback.error(new IOException(message)));}
    @JSFunctor private interface Text extends JSObject{void accept(String text);}
    @JSFunctor private interface Bytes extends JSObject{void accept(Uint8Array bytes);}
    @JSBody(params={"name","success","failure"},script="fetch('/classpath/lookup?name='+encodeURIComponent(name)).then(r=>{if(!r.ok)throw Error('Classpath lookup HTTP '+r.status);return r.json();}).then(urls=>success(urls.join('\\n')),e=>failure(String(e)));")
    private static native void lookup(String name,Text success,Text failure);
    @Async private static native byte[] fetch(String url)throws IOException;
    private static void fetch(String url,AsyncCallback<byte[]> callback){read(url,data->{byte[] bytes=new byte[data.getLength()];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)data.get(i);callback.complete(bytes);},message->callback.error(new IOException(message)));}
    @JSBody(params={"url","success","failure"},script="if(new URL(url,location.href).origin!==location.origin){failure('Classpath URL outside local origin');return;}fetch(url).then(r=>{if(!r.ok)throw Error('Resource HTTP '+r.status);return r.arrayBuffer();}).then(b=>success(new Uint8Array(b)),e=>failure(String(e)));")
    private static native void read(String url,Bytes success,Text failure);
    public static Enumeration<URL> resources(String name)throws IOException{
        Objects.requireNonNull(name);String found=resolve(name);List<URL> result=new ArrayList<>();
        if(!found.isEmpty())for(String value:found.split("\n"))result.add(new URL(null,value,new Handler()));return Collections.enumeration(result);
    }
    public static URL resource(String name){try{var found=resources(name);return found.hasMoreElements()?found.nextElement():null;}catch(IOException error){throw new java.io.UncheckedIOException(error);}}
    public static InputStream stream(String name){URL url=resource(name);if(url==null)return null;try{return url.openStream();}catch(IOException error){return null;}}
    private static final class Handler extends URLStreamHandler {
        @Override protected URLConnection openConnection(URL url){return new URLConnection(url){
            private byte[] bytes;
            public void connect()throws IOException{if(!connected){bytes=fetch(url.toExternalForm());connected=true;}}
            public InputStream getInputStream()throws IOException{connect();return new ByteArrayInputStream(bytes);}
        };}
    }
}
