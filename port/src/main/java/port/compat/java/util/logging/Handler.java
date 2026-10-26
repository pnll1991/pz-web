package port.compat.java.util.logging;
import java.util.Objects;
import java.util.logging.*;
public abstract class Handler {
    private Level level=Level.ALL;private Filter filter;private Formatter formatter;
    protected Handler(){}
    public abstract void publish(LogRecord record);public abstract void flush();public abstract void close();
    public void setLevel(Level value){level=Objects.requireNonNull(value);}public Level getLevel(){return level;}
    public void setFilter(Filter value){filter=value;}public Filter getFilter(){return filter;}
    public void setFormatter(Formatter value){formatter=Objects.requireNonNull(value);}public Formatter getFormatter(){return formatter;}
    public boolean isLoggable(LogRecord record){return record!=null&&level!=Level.OFF&&record.getLevel().intValue()>=level.intValue()&&(filter==null||filter.isLoggable(record));}
}
