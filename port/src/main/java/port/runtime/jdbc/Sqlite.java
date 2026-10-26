package port.runtime.jdbc;
import java.sql.SQLException;
import org.teavm.jso.*;
import org.teavm.jso.core.*;
import org.teavm.jso.typedarrays.Uint8Array;
final class Sqlite {
    static JSObject checked(JSObject response)throws SQLException{String message=error(response);if(message!=null)throw new SQLException(message);return value(response);}
    @JSBody(params="response",script="return response.error;")private static native String error(JSObject response);
    @JSBody(params="response",script="return response.value;")private static native JSObject value(JSObject response);
    @JSBody(params="name",script="if(!pzRuntime.sqlite)return {error:'SQLite Wasm has not been initialized'};return pzRuntime.sqlite.open(name);")static native JSObject open(String name);
    @JSBody(params={"connection","sql","bindings"},script="return pzRuntime.sqlite.run(connection,sql,bindings);")static native JSObject run(JSObject connection,String sql,JSArray<JSObject> bindings);
    @JSBody(params={"connection","sql"},script="return pzRuntime.sqlite.parameterCount(connection,sql);")static native JSObject parameters(JSObject connection,String sql);
    @JSBody(params="connection",script="return pzRuntime.sqlite.export(connection);")static native JSObject export(JSObject connection);
    @JSBody(params="connection",script="return pzRuntime.sqlite.close(connection);")static native JSObject close(JSObject connection);
    @JSBody(params="value",script="return value;")static native int integer(JSObject value);
    @JSBody(params="value",script="return !!value;")static native boolean bool(JSObject value);
    @JSBody(params={"value","property"},script="return value[property];")static native int integer(JSObject value,String property);
    @JSBody(params={"value","property"},script="return !!value[property];")static native boolean bool(JSObject value,String property);
    @JSBody(params={"value","property"},script="return String(value[property]);")static native String string(JSObject value,String property);
    @JSBody(params="value",script="return BigInt(value);")static native JSObject longValue(String value);
    @JSBody(params="value",script="return value;")static native JSObject booleanValue(boolean value);
    @JSBody(params={"result","row","column"},script="const value=result.rows[row][column];return value===null?'null':value instanceof Uint8Array?'blob':typeof value==='bigint'?'integer':typeof value==='number'?(Number.isInteger(value)?'integer':'real'):'text';")static native String cellType(JSObject result,int row,int column);
    @JSBody(params={"result","row","column"},script="const value=result.rows[row][column];return value===null?null:String(value);")static native String text(JSObject result,int row,int column);
    @JSBody(params={"result","row","column"},script="return result.rows[row][column];")static native Uint8Array blob(JSObject result,int row,int column);
    @JSBody(params="result",script="return result.rows.length;")static native int rowCount(JSObject result);
    @JSBody(params="result",script="return result.columns.length;")static native int columnCount(JSObject result);
    @JSBody(params={"result","name"},script="return result.columns.findIndex(n=>n.toLowerCase()===name.toLowerCase());")static native int column(JSObject result,String name);
    @JSBody(params="key",script="return {columns:['GENERATED_KEY'],rows:[[BigInt(key)]]};")static native JSObject generated(String key);
    @JSBody(script="return pzRuntime.sqlite.version;")static native String version();
    static JSObject binding(Object value)throws SQLException{
        if(value==null)return null;if(value instanceof String s)return JSString.valueOf(s);if(value instanceof Boolean b)return booleanValue(b);
        if(value instanceof Long l)return longValue(Long.toString(l));if(value instanceof Number n)return JSNumber.valueOf(n.doubleValue());
        if(value instanceof byte[] bytes){var array=Uint8Array.create(bytes.length);for(int i=0;i<bytes.length;i++)array.set(i,(short)(bytes[i]&255));return array;}
        throw new SQLException("Unsupported JDBC parameter type: "+value.getClass().getName());
    }
}
