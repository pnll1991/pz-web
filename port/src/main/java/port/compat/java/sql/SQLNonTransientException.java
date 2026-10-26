package port.compat.java.sql;
public class SQLNonTransientException extends SQLException {
    public SQLNonTransientException(){super();}
    public SQLNonTransientException(String reason){super(reason);}
    public SQLNonTransientException(String reason,String state){super(reason,state);}
    public SQLNonTransientException(String reason,String state,int code){super(reason,state,code);}
    public SQLNonTransientException(Throwable cause){super(cause);}
    public SQLNonTransientException(String reason,Throwable cause){super(reason,cause);}
    public SQLNonTransientException(String reason,String state,Throwable cause){super(reason,state,cause);}
    public SQLNonTransientException(String reason,String state,int code,Throwable cause){super(reason,state,code,cause);}
}
