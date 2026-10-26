package port.compat.java.lang.invoke;
import java.lang.reflect.*;
public class VarHandle {
    private final Field field;
    public VarHandle(Field field){this.field=field;}
    public Object access(String mode,Object[] arguments)throws IllegalAccessException{
        Object target=arguments[0];if(target==null)throw new NullPointerException();
        synchronized(target){return switch(mode){
            case "get","getVolatile","getAcquire","getOpaque" -> field.get(target);
            case "set","setVolatile","setRelease","setOpaque" -> {if(Modifier.isFinal(field.getModifiers()))throw new UnsupportedOperationException("VarHandle write to final field");field.set(target,arguments[1]);yield null;}
            case "compareAndExchange","compareAndSet" -> {if(Modifier.isFinal(field.getModifiers()))throw new UnsupportedOperationException("VarHandle write to final field");Object old=field.get(target);boolean equal=field.getType().isPrimitive()?old.equals(arguments[1]):old==arguments[1];if(equal)field.set(target,arguments[2]);yield mode.equals("compareAndSet")?equal:old;}
            default -> throw new UnsupportedOperationException("VarHandle."+mode);
        };}
    }
}
