package port.runtime;
import java.util.*;
import java.util.concurrent.*;
import java.lang.invoke.*;
import java.lang.foreign.*;
import java.nio.file.*;
import org.teavm.jso.JSBody;

/** Standard Java API calls compiled to Wasm; no game code or resources in these fixtures. */
public final class Task4Checks {
    public static void run()throws Throwable{
        phase("memory");memory();phase("handles");handles();phase("concurrency");concurrency();phase("resources");resources();phase("xml");xml();phase("watchers");watchers();phase("jdbc");jdbc();phase("digest-normalize");digest();exceptionContracts();
    }
    @JSBody(params="name",script="globalThis.pzTask4={status:'running',detail:name};")private static native void phase(String name);
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static Throwable exceptionAt(int index){return switch(index){
        case 0->new java.lang.reflect.InaccessibleObjectException("access");
        case 1->new javax.management.MBeanException(new Exception("bean"));
        case 2->new javax.management.ReflectionException(new Exception("reflection"));
        case 3->new javax.management.InstanceNotFoundException("instance");
        case 4->new java.util.MissingFormatArgumentException("%s");
        default->new UnsupportedClassVersionError("version");
    };}
    private static void exceptionContracts()throws Throwable{
        for(int i=0;i<6;i++){
            try{throw exceptionAt(i);}
            catch(java.lang.reflect.InaccessibleObjectException e){require(i==0&&e instanceof RuntimeException,"access exception hierarchy");}
            catch(javax.management.MBeanException e){require(i==1&&e.getTargetException()==e.getCause()&&e.getCause().getMessage().equals("bean"),"MBean cause");}
            catch(javax.management.ReflectionException e){require(i==2&&e.getTargetException()==e.getCause(),"reflection cause");}
            catch(javax.management.InstanceNotFoundException e){require(i==3&&e instanceof javax.management.OperationsException,"management hierarchy");}
            catch(java.util.MissingFormatArgumentException e){require(i==4&&e.getFormatSpecifier().equals("%s")&&e.getMessage().equals("Format specifier '%s'"),"format exception");}
            catch(UnsupportedClassVersionError e){require(i==5&&e instanceof ClassFormatError,"class version hierarchy");}
        }
        try{new java.io.ObjectOutputStream(new java.io.ByteArrayOutputStream());throw new AssertionError("serialization was accepted");}catch(java.io.IOException expected){require(expected.getMessage().contains("not implemented"),"serialization explicit failure");}
    }
    private static void memory(){
        var buffer=port.demo.MemoryMappingProbe.memAlloc(16);long address=port.demo.MemoryMappingProbe.memAddress(buffer);
        port.demo.MemoryMappingProbe.memPutLong(address,0x8877665544332211L);require(buffer.get(7)==(byte)0x88,"arena buffer alias");
        port.demo.MemoryMappingProbe.memFree(buffer);try{port.demo.MemoryMappingProbe.memGetLong(address);throw new AssertionError("freed address accepted");}catch(IllegalStateException expected){}
        MemorySegment segment;try(var arena=Arena.ofConfined()){segment=arena.allocate(ValueLayout.JAVA_LONG,2);segment.set(ValueLayout.JAVA_LONG,0,Long.MIN_VALUE);require(segment.get(ValueLayout.JAVA_LONG,0)==Long.MIN_VALUE,"foreign long");require(arena.allocateFrom("ñ😀").getString(0).equals("ñ😀"),"foreign UTF-8");}
        try{segment.get(ValueLayout.JAVA_LONG,0);throw new AssertionError("closed arena accepted");}catch(IllegalStateException expected){}
    }
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)
    public @interface Marker {}
    public static final class HandleProbe {
        @Marker public int annotatedRaw=7;
        public java.util.List<String> genericField;

        private volatile Object value="before";
        public static VarHandle valueHandle()throws Exception{return MethodHandles.lookup().findVarHandle(HandleProbe.class,"value",Object.class);}
        public static long plusOne(long n){return n+1;}
    }
    private static void handles()throws Throwable{
        var raw=HandleProbe.class.getField("annotatedRaw");require(raw.getAnnotation(Marker.class)!=null&&raw.getGenericType()==int.class,"annotated raw field metadata");
        require(HandleProbe.class.getField("genericField").getGenericType() instanceof java.lang.reflect.ParameterizedType,"generic field metadata retained");
        var probe=new HandleProbe();var handle=HandleProbe.valueHandle();Object before=handle.getVolatile(probe);require(before.equals("before"),"VarHandle read");
        require(handle.compareAndExchange(probe,before,"after")==before,"VarHandle exchange");require(((Object)handle.getVolatile(probe)).equals("after"),"VarHandle write");
        var method=MethodHandles.lookup().unreflect(HandleProbe.class.getMethod("plusOne",long.class));require((long)method.invokeExact(9L)==10,"MethodHandle exact");
        try{int invalid=(int)method.invokeExact(9L);throw new AssertionError("invokeExact accepted wrong result "+invalid);}catch(WrongMethodTypeException expected){}
    }
    private static void concurrency()throws Exception{
        Object monitor=new Object();boolean[] waiting={false},restored={false};
        Thread waiter=new Thread(()->{synchronized(monitor){synchronized(monitor){waiting[0]=true;monitor.notifyAll();try{monitor.wait();throw new AssertionError("interrupt did not throw");}catch(InterruptedException expected){restored[0]=Thread.holdsLock(monitor)&&!Thread.currentThread().isInterrupted();}}}});
        synchronized(monitor){waiter.start();while(!waiting[0])monitor.wait();waiter.interrupt();}
        waiter.join();require(restored[0],"interrupted wait reacquires recursive monitor and clears flag");
        synchronized(monitor){Thread.currentThread().interrupt();try{monitor.wait();throw new AssertionError("pre-interrupted wait accepted");}catch(InterruptedException expected){require(Thread.holdsLock(monitor)&&!Thread.currentThread().isInterrupted(),"pre-interrupted wait state");}}
        synchronized(monitor){TimeUnit.NANOSECONDS.timedWait(monitor,500_000);require(Thread.holdsLock(monitor),"submillisecond wait returns with monitor");}
        var queue=new LinkedBlockingQueue<Integer>(1);queue.put(7);Thread producer=new Thread(()->{try{queue.put(9);}catch(InterruptedException e){throw new AssertionError(e);}});producer.start();require(queue.take()==7&&queue.take()==9,"blocking FIFO");producer.join();
        var rendezvous=new SynchronousQueue<Integer>(true);require(!rendezvous.offer(1)&&rendezvous.size()==0,"synchronous queue has no storage");Thread sender=new Thread(()->{try{rendezvous.put(13);}catch(InterruptedException e){throw new AssertionError(e);}});sender.start();require(rendezvous.take()==13,"synchronous handoff");sender.join();require(rendezvous.poll(500_000,TimeUnit.NANOSECONDS)==null,"synchronous timeout");
        var future=CompletableFuture.supplyAsync(()->20).thenApply(n->n+1).thenCompose(n->CompletableFuture.completedFuture(n*2));require(future.get(2,TimeUnit.SECONDS)==42,"completion chain");
        var pool=new ThreadPoolExecutor(1,2,10,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>(1),Executors.defaultThreadFactory());
        try{require(pool.submit(()->23).get(2,TimeUnit.SECONDS)==23,"configurable pool result");}finally{pool.shutdown();}require(pool.awaitTermination(2,TimeUnit.SECONDS),"configurable pool termination");
        var executor=Executors.newSingleThreadScheduledExecutor();try{require(executor.schedule(()->17,5,TimeUnit.MILLISECONDS).get(2,TimeUnit.SECONDS)==17,"scheduled result");}finally{executor.shutdownNow();}require(executor.awaitTermination(2,TimeUnit.SECONDS),"scheduled termination");
        var lock=new java.util.concurrent.locks.ReentrantReadWriteLock(true);lock.readLock().lock();require(!lock.writeLock().tryLock(),"read-write upgrade");lock.readLock().unlock();lock.writeLock().lock();lock.readLock().lock();lock.writeLock().unlock();require(lock.getReadHoldCount()==1,"lock downgrade");lock.readLock().unlock();
        var map=new ConcurrentHashMap<String,Integer>();map.put("a",1);var keys=map.keySet();require(keys.remove("a")&&map.isEmpty(),"live key view");try{keys.add("x");throw new AssertionError("key view added without value");}catch(UnsupportedOperationException expected){}
    }
    private static void resources()throws Exception{
        var url=Task4Checks.class.getResource("fixture-resource.txt");require(url!=null,"classpath URL");try(var input=url.openStream()){require(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("propio — ñ"),"classpath bytes");}
        var resources=ClassLoader.getSystemResources("port/runtime/fixture-resource.txt");int count=0;while(resources.hasMoreElements()){resources.nextElement();count++;}require(count==2,"classpath order and duplicates");require(Task4Checks.class.getResource("absent-fixture.txt")==null,"missing resource");
    }
    private static void xml()throws Exception{
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var builder=factory.newDocumentBuilder();var doc=builder.parse(new java.io.ByteArrayInputStream("<root><item id='7'>ñ&amp;😀</item></root>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var item=(org.w3c.dom.Element)doc.getElementsByTagName("item").item(0);require(item.getAttribute("id").equals("7")&&item.getTextContent().equals("ñ&😀"),"XML content");
        item.setAttribute("id","8");require(item.getParentNode()==doc.getDocumentElement(),"DOM node identity");
        var fresh=builder.newDocument();var root=fresh.createElement("copy");fresh.appendChild(root);root.appendChild(fresh.importNode(item,true));require(fresh.getDocumentElement().getTextContent().equals("ñ&😀"),"DOM import");
        var transformer=javax.xml.transform.TransformerFactory.newInstance().newTransformer();transformer.setOutputProperty("indent","yes");transformer.setOutputProperty("omit-xml-declaration","yes");var output=new java.io.StringWriter();transformer.transform(new javax.xml.transform.dom.DOMSource(doc),new javax.xml.transform.stream.StreamResult(output));
        require(output.toString().contains("\n  <item")&&item.getParentNode()==doc.getDocumentElement(),"XML indent preserves original DOM");
        var copy=new javax.xml.transform.dom.DOMResult();transformer.transform(new javax.xml.transform.stream.StreamSource(new java.io.StringReader(output.toString())),copy);require(((org.w3c.dom.Document)copy.getNode()).getElementsByTagName("item").item(0).getTextContent().equals("ñ&😀"),"XML identity round trip");
        try{builder.parse(new java.io.ByteArrayInputStream("<broken>".getBytes()));throw new AssertionError("malformed XML accepted");}catch(org.xml.sax.SAXException expected){}
    }
    private static void watchers()throws Exception{
        Path directory=Path.of("/Zomboid/watch-fixture");Files.createDirectories(directory);Path path=directory.resolve("data.txt");Files.deleteIfExists(path);
        try(var watcher=FileSystems.getDefault().newWatchService()){
            var key=directory.register(watcher,StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_MODIFY,StandardWatchEventKinds.ENTRY_DELETE);
            Files.writeString(path,"value");require(watcher.poll(1,TimeUnit.SECONDS)==key,"watch create signal");require(key.pollEvents().stream().anyMatch(e->e.kind()==StandardWatchEventKinds.ENTRY_CREATE&&e.context().toString().equals("data.txt")),"watch create event");require(key.reset(),"watch reset");
            Files.delete(path);require(watcher.take()==key,"watch delete signal");require(key.pollEvents().stream().anyMatch(e->e.kind()==StandardWatchEventKinds.ENTRY_DELETE),"watch delete event");key.cancel();require(!key.isValid(),"watch cancel");
        }
    }
    private static void jdbc()throws Exception{
        try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:/Zomboid/task4.db")){
            try(var statement=connection.createStatement()){statement.executeUpdate("DROP TABLE IF EXISTS fixture");statement.executeUpdate("CREATE TABLE fixture(id INTEGER PRIMARY KEY, name TEXT UNIQUE, payload BLOB, big INTEGER)");}
            connection.setAutoCommit(false);
            try(var insert=connection.prepareStatement("INSERT INTO fixture(name,payload,big) VALUES(?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS)){
                insert.setString(1,"ñ😀");insert.setBytes(2,new byte[]{0,(byte)128,(byte)255});insert.setLong(3,Long.MAX_VALUE);require(insert.executeUpdate()==1,"SQL insert count");
                try(var keys=insert.getGeneratedKeys()){require(keys.next()&&keys.getLong(1)==1,"SQL generated key");}
            }
            connection.commit();
            try(var statement=connection.createStatement()){statement.executeUpdate("INSERT INTO fixture(name) VALUES('rollback')");}connection.rollback();
            try(var query=connection.prepareStatement("SELECT name,payload,big FROM fixture WHERE id=?")){query.setInt(1,1);try(var result=query.executeQuery()){require(result.next(),"SQL row");require(result.getString(1).equals("ñ😀"),"SQL UTF-8");require(Arrays.equals(result.getBytes(2),new byte[]{0,(byte)128,(byte)255}),"SQL blob");require(result.getLong(3)==Long.MAX_VALUE,"SQL int64");require(!result.next(),"SQL cursor end");}}
            try(var tables=connection.getMetaData().getTables(null,null,"fixture",new String[]{"TABLE"})){require(tables.next()&&tables.getString("TABLE_NAME").equals("fixture"),"SQL metadata");}
            connection.setAutoCommit(true);
        }
    }
    public static String readSavedSql()throws Exception{
        try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:/Zomboid/task4.db");var query=connection.createStatement();var result=query.executeQuery("SELECT name,big FROM fixture ORDER BY id")){
            require(result.next(),"persisted SQL row");String value=result.getString(1)+":"+result.getLong(2);require(!result.next(),"rollback persisted");return value;
        }
    }
    private static void digest()throws Exception{
        require(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest("abc".getBytes())).equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),"SHA-256");
        require(java.text.Normalizer.normalize("ñ",java.text.Normalizer.Form.NFD).equals("n\u0303"),"normalization");
        var record=new java.util.logging.LogRecord(java.util.logging.Level.INFO,"value {0}");record.setParameters(new Object[]{7});require(new java.util.logging.ConsoleHandler().getFormatter().format(record).contains("value 7"),"console formatter parameters");
        var logger=java.util.logging.Logger.getLogger("own.task4");logger.setUseParentHandlers(false);logger.setLevel(java.util.logging.Level.ALL);int[] published={0};var handler=new java.util.logging.Handler(){public void publish(java.util.logging.LogRecord record){if(isLoggable(record))published[0]++;}public void flush(){}public void close(){}};handler.setLevel(java.util.logging.Level.WARNING);logger.addHandler(handler);logger.info("filtered");logger.warning("accepted");logger.removeHandler(handler);require(published[0]==1,"logger handler filtering");
        require(java.text.Collator.getInstance(new Locale("es")).compare("n","ñ")<0,"collation");
    }
}
