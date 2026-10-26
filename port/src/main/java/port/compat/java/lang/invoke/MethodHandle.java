package port.compat.java.lang.invoke;
import java.lang.reflect.*;
import java.util.*;
public class MethodHandle {
    private final Method method;
    MethodHandle(Method method){this.method=method;}
    public Object invokeWithArguments(Object... args)throws Throwable{
        boolean statik=Modifier.isStatic(method.getModifiers());
        Object receiver=statik?null:args[0];Object[] parameters=statik?args:Arrays.copyOfRange(args,1,args.length);
        try{return method.invoke(receiver,parameters);}catch(InvocationTargetException e){throw e.getCause();}
    }
    public Object exact(Object[] args,Class<?>[] parameters,Class<?> result)throws Throwable{
        Class<?>[] target=method.getParameterTypes();boolean statik=Modifier.isStatic(method.getModifiers());
        if(result!=method.getReturnType()||parameters.length!=target.length+(statik?0:1))throw new WrongMethodTypeException();
        int first=statik?0:1;if(!statik&&parameters[0]!=method.getDeclaringClass())throw new WrongMethodTypeException();
        for(int i=0;i<target.length;i++)if(target[i]!=parameters[i+first])throw new WrongMethodTypeException();
        return invokeWithArguments(args);
    }
}
