package sdk.anno;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
/**
 * 网络请求时需要用到，对应的value是参数名称
 */
public @interface Param {
    String value() default "";
}
