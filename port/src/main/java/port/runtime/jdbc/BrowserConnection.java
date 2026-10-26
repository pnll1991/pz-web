package port.runtime.jdbc;
import java.sql.SQLException;
import port.compat.java.sql.*;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
import java.util.*;
public final class BrowserConnection extends AbstractConnection {
    final JSObject handle;private boolean closed,autoCommit=true;
    private final Set<BrowserStatement> statements=new HashSet<>();
    public BrowserConnection(String path)throws SQLException{handle=Sqlite.checked(Sqlite.open(path));}
    void check()throws SQLException{if(closed)throw new SQLException("Connection is closed");}
    void track(BrowserStatement statement)throws SQLException{check();statements.add(statement);}void untrack(BrowserStatement statement){statements.remove(statement);}
    private void control(String sql)throws SQLException{Sqlite.checked(Sqlite.run(handle,sql,JSArray.create()));}
    void persist()throws SQLException{if(Sqlite.bool(Sqlite.checked(Sqlite.export(handle))))try{port.shim.BrowserPersistence.flush();}catch(Throwable error){throw new SQLException("SQLite VFS persistence failed",error);}}
    public Statement createStatement()throws SQLException{check();var statement=new BrowserStatement(this);track(statement);return statement;}
    public PreparedStatement prepareStatement(String sql)throws SQLException{return prepareStatement(sql,Statement.NO_GENERATED_KEYS);}
    public PreparedStatement prepareStatement(String sql,int keys)throws SQLException{check();if(keys!=Statement.NO_GENERATED_KEYS&&keys!=Statement.RETURN_GENERATED_KEYS)throw new SQLException("Invalid generated keys flag");var statement=new BrowserPreparedStatement(this,sql);track(statement);return statement;}
    public boolean getAutoCommit()throws SQLException{check();return autoCommit;}
    public void setAutoCommit(boolean value)throws SQLException{check();if(value==autoCommit)return;if(value){control("COMMIT");autoCommit=true;persist();}else{control("BEGIN");autoCommit=false;}}
    public void commit()throws SQLException{check();if(autoCommit)throw new SQLException("Connection is in auto-commit mode");control("COMMIT");try{persist();}finally{control("BEGIN");}}
    public void rollback()throws SQLException{check();if(autoCommit)throw new SQLException("Connection is in auto-commit mode");control("ROLLBACK");control("BEGIN");}
    public boolean isClosed(){return closed;}
    public boolean isValid(int timeout)throws SQLException{if(timeout<0)throw new SQLException("Negative validation timeout");return !closed;}
    public DatabaseMetaData getMetaData()throws SQLException{check();return new BrowserDatabaseMetaData(this);}
    public void close()throws SQLException{if(closed)return;try{for(var statement:new ArrayList<>(statements))statement.close();if(!autoCommit)control("ROLLBACK");persist();}finally{closed=true;Sqlite.checked(Sqlite.close(handle));}}
    public boolean isReadOnly()throws SQLException{check();return false;}
    public int getTransactionIsolation()throws SQLException{check();return Connection.TRANSACTION_SERIALIZABLE;}
    public void setTransactionIsolation(int level)throws SQLException{check();if(level!=Connection.TRANSACTION_SERIALIZABLE)throw new java.sql.SQLFeatureNotSupportedException("SQLite bridge supports SERIALIZABLE isolation");}
}
