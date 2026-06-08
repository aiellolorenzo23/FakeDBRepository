package io.github.aiellolorenzo23.fakedb.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FakeDBReference {

    String schema() default "";

    String table();

    String localField();

    String targetField() default "id";

    boolean multiple() default false;
}
