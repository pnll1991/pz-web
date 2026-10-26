package port.compat.java.sql;
public class SQLWarning extends SQLException {
    public SQLWarning(){super();}
    public SQLWarning(String reason){super(reason);}
    public SQLWarning(String reason,String state){super(reason,state);}
    public SQLWarning(String reason,String state,int code){super(reason,state,code);}
    public SQLWarning(Throwable cause){super(cause);}
    public SQLWarning(String reason,Throwable cause){super(reason,cause);}
    public SQLWarning(String reason,String state,Throwable cause){super(reason,state,cause);}
    public SQLWarning(String reason,String state,int code,Throwable cause){super(reason,state,code,cause);}
}
