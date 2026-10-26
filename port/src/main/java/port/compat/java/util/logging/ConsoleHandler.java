package port.compat.java.util.logging;
import java.util.logging.*;
public class ConsoleHandler extends Handler {
    public ConsoleHandler(){setLevel(Level.INFO);setFormatter(new Formatter(){public String format(LogRecord record){return record.getLevel().getName()+": "+formatMessage(record);}});}
    public void publish(LogRecord record){if(isLoggable(record)){port.runtime.JdkTask4Support.console(record.getLevel().intValue(),getFormatter().format(record));if(record.getThrown()!=null)record.getThrown().printStackTrace();}}
    public void flush(){} // Browser console publishes synchronously; there is no buffered stream.
    public void close(){flush();} // Closing a ConsoleHandler does not close the shared console.
}
