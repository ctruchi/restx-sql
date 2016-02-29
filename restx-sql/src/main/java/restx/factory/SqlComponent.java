package restx.factory;

public @interface SqlComponent {
    int priority() default 0;

    /**
     * @return the class to use for the name of this component,
     * if not defined, the annotated class will be used
     */
    Class<?> asClass() default void.class; // trick to mark default value, as null is not permitted
}
