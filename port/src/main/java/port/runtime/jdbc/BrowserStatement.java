package port.runtime.jdbc;
import java.sql.SQLException;
import port.compat.java.sql.*;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
public class BrowserStatement extends AbstractStatement {
    protected final BrowserConnection connection;private boolean closed;private BrowserResultSet result;private int updates=-1;private String generated="0";
    BrowserStatement(BrowserConnection connection){this.connection=connection;}
    protected void check()throws SQLException{connection.check();if(closed)throw new SQLException("Statement is closed");}
    protected boolean executeBound(String sql,JSArray<JSObject> bindings)throws SQLException{
        check();if(result!=null){result.close();result=null;}
        JSObject data=Sqlite.checked(Sqlite.run(connection.handle,sql,bindings));generated=Sqlite.string(data,"rowid");
        boolean query=Sqlite.columnCount(data)>0;updates=query?-1:Sqlite.integer(data,"updates");if(query)result=new BrowserResultSet(this,data);
        if(Sqlite.bool(data,"autocommit")&&!query)connection.persist();return query;
    }
    public boolean execute(String sql)throws SQLException{return executeBound(sql,JSArray.create());}
    public ResultSet executeQuery(String sql)throws SQLException{if(!execute(sql))throw new SQLException("Statement produced no result set");return result;}
    public int executeUpdate(String sql)throws SQLException{if(execute(sql))throw new SQLException("Statement produced a result set");return updates;}
    public ResultSet getResultSet()throws SQLException{check();return result;}
    public int getUpdateCount()throws SQLException{check();return updates;}
    public ResultSet getGeneratedKeys()throws SQLException{check();return new BrowserResultSet(this,Sqlite.generated(generated));}
    public Connection getConnection()throws SQLException{check();return connection;}
    public boolean isClosed(){return closed;}
    public void close()throws SQLException{if(closed)return;closed=true;if(result!=null)result.close();connection.untrack(this);}
}
