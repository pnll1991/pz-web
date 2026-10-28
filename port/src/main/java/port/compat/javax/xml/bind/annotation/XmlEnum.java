package port.compat.javax.xml.bind.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)@Target({ElementType.TYPE})
public @interface XmlEnum {Class<?> value() default void.class;}
