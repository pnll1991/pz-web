package port.compat.java.sql;
public class SQLTimeoutException extends SQLTransientException {
    public SQLTimeoutException(){super();}
    public SQLTimeoutException(String reason){super(reason);}
    public SQLTimeoutException(String reason,String state){super(reason,state);}
    public SQLTimeoutException(String reason,String state,int code){super(reason,state,code);}
    public SQLTimeoutException(Throwable cause){super(cause);}
    public SQLTimeoutException(String reason,Throwable cause){super(reason,cause);}
    public SQLTimeoutException(String reason,String state,Throwable cause){super(reason,state,cause);}
    public SQLTimeoutException(String reason,String state,int code,Throwable cause){super(reason,state,code,cause);}
}
