package port;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;
import port.compat.java.util.concurrent.*;
class Task4ExtraTest {
    @Test void exceptionMessagesCausesAndParentsMatchJdk(){
        var cause=new Exception("cause");var bean=new port.compat.javax.management.MBeanException(cause,"bean");assertSame(cause,bean.getCause());assertSame(cause,bean.getTargetException());assertEquals("bean",bean.getMessage());
        var reflection=new port.compat.javax.management.ReflectionException(cause);assertSame(cause,reflection.getCause());assertSame(cause,reflection.getTargetException());assertNull(reflection.getMessage());
        for(String spec:new String[]{"%s","%2$d",""})assertEquals(new java.util.MissingFormatArgumentException(spec).getMessage(),new port.compat.java.util.MissingFormatArgumentException(spec).getMessage());
        assertThrows(NullPointerException.class,()->new port.compat.java.util.MissingFormatArgumentException(null));
        assertInstanceOf(LinkageError.class,new port.compat.java.lang.UnsupportedClassVersionError());
        assertThrows(java.io.IOException.class,()->new port.compat.java.io.ObjectOutputStream(new java.io.ByteArrayOutputStream()));
    }
    @Test void loggerFormattingKeepsLiteralApostrophes(){
        var record=new java.util.logging.LogRecord(java.util.logging.Level.INFO,"it's literal");record.setParameters(new Object[]{7});assertEquals("it's literal",port.runtime.JdkTask4Support.formatMessage(null,record));record.setMessage("value {0}");assertEquals("value 7",port.runtime.JdkTask4Support.formatMessage(null,record));
    }

    @Test void boundedPoolExpandsRejectsAndDrainsGracefully()throws Exception{
        var gate=new java.util.concurrent.CountDownLatch(1);var started=new java.util.concurrent.CountDownLatch(2);var queue=new LinkedBlockingQueue<Runnable>(1);
        var pool=new ThreadPoolExecutor(1,2,20,TimeUnit.MILLISECONDS,queue,Executors.defaultThreadFactory());
        try{java.util.concurrent.Callable<Integer> work=()->{started.countDown();gate.await();return 7;};var first=pool.submit(work);var queued=pool.submit(()->9);var second=pool.submit(work);assertTrue(started.await(2,TimeUnit.SECONDS));assertEquals(1,queue.size());assertThrows(RejectedExecutionException.class,()->pool.execute(()->{}));pool.shutdown();gate.countDown();assertEquals(7,first.get(2,TimeUnit.SECONDS));assertEquals(9,queued.get(2,TimeUnit.SECONDS));assertEquals(7,second.get(2,TimeUnit.SECONDS));assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));assertThrows(RejectedExecutionException.class,()->pool.execute(()->{}));}finally{gate.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}
    }
    @Test void managementNamesMatchJdkCanonicalization()throws Exception{
        for(String text:new String[]{"java.lang:type=Memory","own:name=abc,type=Cache","own:name=\"a,b\",type=*","*:type=X,*","own:*,type=X",""}){
            var expected=new javax.management.ObjectName(text);var actual=new port.compat.javax.management.ObjectName(text);assertEquals(expected.getCanonicalName(),actual.getCanonicalName());assertEquals(expected.getKeyPropertyList(),actual.getKeyPropertyList());assertEquals(expected.isPattern(),actual.isPattern());
        }
        for(String invalid:new String[]{"no-colon","own:","own:a=x,a=y","own:a=bad:value","own:a=\"unclosed"})assertThrows(javax.management.MalformedObjectNameException.class,()->new port.compat.javax.management.ObjectName(invalid));
    }
}
