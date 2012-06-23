package com.github.jsr330.spi.config.builder;

import java.lang.reflect.Constructor;

public class Constructors {
    
    @SuppressWarnings("unchecked")
    public static <T> Constructor<T> defaultConstructor(Class<T> type) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length == 0) {
                return (Constructor<T>) constructor;
            }
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public static <T> Constructor<T> constructor(Class<T> type, Class<?>... arguments) {
        Class<?>[] parameters;
        boolean equals;
        
        if (arguments == null || arguments.length == 0) {
            return defaultConstructor(type);
        }
        
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if ((parameters = constructor.getParameterTypes()).length == arguments.length) {
                equals = true;
                for (int i = 0; i < arguments.length; i++) {
                    if (!arguments[i].equals(parameters[i])) {
                        equals = false;
                    }
                }
                if (equals) {
                    return (Constructor<T>) constructor;
                }
            }
        }
        return null;
    }
    
}
