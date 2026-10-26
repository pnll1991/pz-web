package port.compat.java.sql;
public class SQLFeatureNotSupportedException extends SQLNonTransientException {
    public SQLFeatureNotSupportedException(){super();}
    public SQLFeatureNotSupportedException(String reason){super(reason);}
    public SQLFeatureNotSupportedException(String reason,String state){super(reason,state);}
    public SQLFeatureNotSupportedException(String reason,String state,int code){super(reason,state,code);}
    public SQLFeatureNotSupportedException(Throwable cause){super(cause);}
    public SQLFeatureNotSupportedException(String reason,Throwable cause){super(reason,cause);}
    public SQLFeatureNotSupportedException(String reason,String state,Throwable cause){super(reason,state,cause);}
    public SQLFeatureNotSupportedException(String reason,String state,int code,Throwable cause){super(reason,state,code,cause);}
}
