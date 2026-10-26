package port.compat.java.lang.invoke;
import java.lang.reflect.*;
import java.util.Objects;
public final class MethodHandles {
    public static Lookup lookup(){throw new UnsupportedOperationException("MethodHandles.lookup caller was not linked by the compiler");}
    public static Lookup lookup(Class<?> caller){return new Lookup(Objects.requireNonNull(caller));}
    public static Lookup publicLookup(){return new Lookup(null);}
    public static final class Lookup {
        private final Class<?> caller;
        Lookup(Class<?> caller){this.caller=caller;}
        public Class<?> lookupClass(){return caller==null?Object.class:caller;}
        private void check(Member member)throws IllegalAccessException{
            if(caller!=member.getDeclaringClass()&&!(Modifier.isPublic(member.getModifiers())&&Modifier.isPublic(member.getDeclaringClass().getModifiers())))throw new IllegalAccessException(member.toString());
        }
        public VarHandle findVarHandle(Class<?> owner,String name,Class<?> type)throws NoSuchFieldException,IllegalAccessException{
            Field field=owner.getDeclaredField(name);check(field);if(Modifier.isStatic(field.getModifiers())||field.getType()!=type)throw new NoSuchFieldException(name);field.setAccessible(true);return new VarHandle(field);
        }
        public MethodHandle unreflect(Method method)throws IllegalAccessException{
            if(!method.isAccessible())check(method);method.setAccessible(true);return new MethodHandle(method);
        }
    }
}
