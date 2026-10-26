package port.compat.java.sql;
public class SQLTransientException extends SQLException {
    public SQLTransientException(){super();}
    public SQLTransientException(String reason){super(reason);}
    public SQLTransientException(String reason,String state){super(reason,state);}
    public SQLTransientException(String reason,String state,int code){super(reason,state,code);}
    public SQLTransientException(Throwable cause){super(cause);}
    public SQLTransientException(String reason,Throwable cause){super(reason,cause);}
    public SQLTransientException(String reason,String state,Throwable cause){super(reason,state,cause);}
    public SQLTransientException(String reason,String state,int code,Throwable cause){super(reason,state,code,cause);}
}
