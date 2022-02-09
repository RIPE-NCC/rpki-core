package net.ripe.rpki.services.impl.handlers;

import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Handler {
    /**
     * The order the handlers run in. Lower order runs before higher order.
     */
    int order() default 100;
}
