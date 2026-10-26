package port.compat.java.sql;
import java.util.*;
public class SQLException extends Exception implements Iterable<Throwable> {
    private final String state;private final int code;private SQLException next;
    public SQLException(){this(null,null,0,null);}public SQLException(String reason){this(reason,null,0,null);}public SQLException(String reason,String state){this(reason,state,0,null);}public SQLException(String reason,String state,int code){this(reason,state,code,null);}
    public SQLException(Throwable cause){this(cause==null?null:cause.toString(),null,0,cause);}public SQLException(String reason,Throwable cause){this(reason,null,0,cause);}public SQLException(String reason,String state,Throwable cause){this(reason,state,0,cause);}public SQLException(String reason,String state,int code,Throwable cause){super(reason,cause);this.state=state;this.code=code;}
    public String getSQLState(){return state;}public int getErrorCode(){return code;}public SQLException getNextException(){return next;}
    public synchronized void setNextException(SQLException value){SQLException last=this;while(last.next!=null)last=last.next;last.next=value;}
    public Iterator<Throwable> iterator(){List<Throwable> errors=new ArrayList<>();for(SQLException e=this;e!=null;e=e.next){errors.add(e);for(Throwable c=e.getCause();c!=null;c=c.getCause())errors.add(c);}return errors.iterator();}
}
