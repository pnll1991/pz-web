package port.compat.javax.xml.bind.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)@Target({ElementType.TYPE})
public @interface XmlType {String name() default "$default$";String[] propOrder() default {};String namespace() default "##default";Class factoryClass() default Object.class;String factoryMethod() default "$default$";}
