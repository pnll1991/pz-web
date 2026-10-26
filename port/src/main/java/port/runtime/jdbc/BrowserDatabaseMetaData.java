package port.runtime.jdbc;
import java.sql.SQLException;
import port.compat.java.sql.*;
public final class BrowserDatabaseMetaData extends AbstractDatabaseMetaData {
    private final BrowserConnection connection;BrowserDatabaseMetaData(BrowserConnection connection){this.connection=connection;}
    public Connection getConnection(){return connection;}
    public String getDatabaseProductName(){return "SQLite";}public String getDatabaseProductVersion(){return Sqlite.version();}
    public String getDriverName(){return "pz-web SQLite Wasm bridge";}public String getDriverVersion(){return "1";}
    public ResultSet getTables(String catalog,String schema,String table,String[] types)throws SQLException{
        if(catalog!=null&&!catalog.isEmpty()||schema!=null&&!schema.isEmpty())throw new java.sql.SQLFeatureNotSupportedException("SQLite catalogs/schemas");
        String filter="";if(types!=null){boolean tables=false,views=false;for(String type:types){tables|="TABLE".equals(type);views|="VIEW".equals(type);}filter=tables&&views?"":tables?" AND type='table'":views?" AND type='view'":" AND 0";}
        PreparedStatement query=connection.prepareStatement("SELECT NULL AS TABLE_CAT,NULL AS TABLE_SCHEM,name AS TABLE_NAME,upper(type) AS TABLE_TYPE,'' AS REMARKS FROM sqlite_schema WHERE type IN ('table','view') AND name LIKE ?"+filter+" ORDER BY TABLE_TYPE,TABLE_NAME");query.setString(1,table==null?"%":table);return query.executeQuery();
    }
    public ResultSet getColumns(String catalog,String schema,String table,String column)throws SQLException{
        if(catalog!=null&&!catalog.isEmpty()||schema!=null&&!schema.isEmpty())throw new java.sql.SQLFeatureNotSupportedException("SQLite catalogs/schemas");
        PreparedStatement query=connection.prepareStatement("SELECT NULL AS TABLE_CAT,NULL AS TABLE_SCHEM,s.name AS TABLE_NAME,p.name AS COLUMN_NAME,CASE WHEN upper(p.type) LIKE '%INT%' THEN -5 WHEN upper(p.type) LIKE '%CHAR%' OR upper(p.type) LIKE '%TEXT%' OR upper(p.type) LIKE '%CLOB%' THEN 12 WHEN upper(p.type) LIKE '%BLOB%' OR p.type='' THEN -3 WHEN upper(p.type) LIKE '%REAL%' OR upper(p.type) LIKE '%FLOA%' OR upper(p.type) LIKE '%DOUB%' THEN 8 ELSE 2 END AS DATA_TYPE,p.type AS TYPE_NAME,NULL AS COLUMN_SIZE,NULL AS BUFFER_LENGTH,NULL AS DECIMAL_DIGITS,10 AS NUM_PREC_RADIX,CASE p.\"notnull\" WHEN 1 THEN 0 ELSE 1 END AS NULLABLE,'' AS REMARKS,p.dflt_value AS COLUMN_DEF,NULL AS SQL_DATA_TYPE,NULL AS SQL_DATETIME_SUB,NULL AS CHAR_OCTET_LENGTH,p.cid+1 AS ORDINAL_POSITION,CASE p.\"notnull\" WHEN 1 THEN 'NO' ELSE 'YES' END AS IS_NULLABLE,NULL AS SCOPE_CATALOG,NULL AS SCOPE_SCHEMA,NULL AS SCOPE_TABLE,NULL AS SOURCE_DATA_TYPE,'' AS IS_AUTOINCREMENT,'' AS IS_GENERATEDCOLUMN FROM sqlite_schema s,pragma_table_info(s.name) p WHERE s.type IN ('table','view') AND s.name LIKE ? AND p.name LIKE ? ORDER BY s.name,p.cid");query.setString(1,table==null?"%":table);query.setString(2,column==null?"%":column);return query.executeQuery();
    }
}
