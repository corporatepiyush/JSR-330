package com.github.jsr330.instance;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultClassInstancer implements ClassInstancer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClassInstancer.class);
    
    protected Map<String, Provider<?>> providers = new TreeMap<String, Provider<?>>();
    protected Map<String, Object> singletons = new TreeMap<String, Object>();
    protected TypeDeterminator typeDeterminator = new DefaultTypeDeterminator();
    
    @Override
    public <T> T instance(Class<T> type, Map<String, Class<?>[]> inheritanceTree, ClassLoader classLoader) {
        return instance(type, inheritanceTree, classLoader, null, null);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T instance(Class<?> type, Map<String, Class<?>[]> inheritanceTree, ClassLoader classLoader, Class<?>[] generics, Annotation qualifier) {
        Constructor<?> ctor = null;
        T inst = null;
        Provider<?> provider;
        Class<?>[] candidates;
        TypeContainer typeContainer;
        
        LOGGER.debug("instance - gets instance for {} with generics {}", type, generics);
        
        if (type.isAssignableFrom(Provider.class)) {
            provider = new SimpleProvider(generics[0], this, inheritanceTree, qualifier, classLoader);
            LOGGER.debug("instance - return provider {} for {}", provider, generics[0]);
            return (T) provider;
        }
        
        candidates = inheritanceTree.get(type.getName());
        type = typeDeterminator.determineClass(type, candidates, qualifier, classLoader);
        LOGGER.debug("instance - got type {} due to annotations", type);
        
        if (singletons.containsKey(type.getName())) {
            LOGGER.debug("instance - deliver singleton {}", singletons.get(type.getName()));
            return (T) singletons.get(type.getName());
        } else {
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                candidates = inheritanceTree.get(type.getName());
                if (candidates != null && candidates.length > 0) {
                    if (candidates.length == 1) {
                        inst = instance(candidates[0], inheritanceTree, classLoader, null, qualifier);
                    }
                }
                
                if (inst == null) {
                    inst = instance(type, inheritanceTree, classLoader, null, qualifier);
                }
            } else {
                ctor = getInjectableConstructor(type);
                if (ctor == null) {
                    ctor = getDefaultConstructor(type);
                }
                
                if (ctor != null) {
                    try {
                        inst = (T) ctor.newInstance(getArguments(ctor, inheritanceTree, classLoader));
                    } catch (Exception exception) {
                        LOGGER.debug("error while instantiating class with constructor {}", ctor, exception);
                    }
                    
                    typeContainer = new TypeContainer(type, ctor);
                    typeContainer.fillTypeContainer();
                    injectTypeContainer(typeContainer, inst, inheritanceTree, classLoader);
                    
                    if (type.isAnnotationPresent(Singleton.class) && !singletons.containsKey(type.getName())) {
                        singletons.put(type.getName(), inst);
                    }
                }
            }
        }
        
        return inst;
    }
    
    protected void injectTypeContainer(TypeContainer typeContainer, Object inst, Map<String, Class<?>[]> inheritanceTree, ClassLoader classLoader) {
        for (InjectionSet set : typeContainer.injectionSets) {
            injectFields(set, inst, inheritanceTree, classLoader);
            injectMethods(set, inst, inheritanceTree, classLoader);
        }
    }
    
    protected void injectFields(InjectionSet set, Object inst, Map<String, Class<?>[]> inheritanceTree, ClassLoader classLoader) {
        ParameterizedType parameterizedType;
        Class<?> objectType;
        Class<?>[] generics;
        Class<?>[] candidates;
        Annotation qualifier;
        
        for (Field field : set.fields) {
            try {
                LOGGER.debug("injectFields - field injected {} of {}", field, set.type);
                if (field.getGenericType() instanceof ParameterizedType) {
                    parameterizedType = (ParameterizedType) field.getGenericType();
                    generics = getGenericTypes(parameterizedType);
                    objectType = null;
                    if (parameterizedType.getRawType() instanceof Class) {
                        objectType = (Class<?>) parameterizedType.getRawType();
                    }
                    
                    if (objectType != null) {
                        candidates = inheritanceTree.get(objectType.getName());
                        qualifier = getQualifier(field.getAnnotations());
                        objectType = typeDeterminator.determineClass(objectType, candidates, qualifier, classLoader);
                        field.set(inst, instance(objectType, inheritanceTree, classLoader, generics, qualifier));
                    }
                } else if (field.getGenericType() instanceof Class) {
                    qualifier = getQualifier(field.getAnnotations());
                    field.set(inst, instance((Class<?>) field.getGenericType(), inheritanceTree, classLoader, null, qualifier));
                }
            } catch (Exception exception) {
                LOGGER.debug("Error while injecting field", exception);
            }
        }
    }
    
    protected void injectMethods(InjectionSet set, Object inst, Map<String, Class<?>[]> inheritanceTree, ClassLoader classLoader) {
        Object[] arguments;
        
        for (Method method : set.methods) {
            try {
                LOGGER.debug("injectMethods - method injected {} of {}", method, set.type);
                arguments = getArguments(method, inheritanceTree, classLoader);
                if (arguments != null) {
                    method.invoke(inst, arguments);
                } else {
                    method.invoke(inst);
                }
            } catch (Exception exception) {
                LOGGER.debug("error while invoking method", exception);
            }
        }
    }
    
    protected Object[] getArguments(Method method, Map<String, Class<?>[]> inheritanceTree, ClassLoader classLoader) {
        return getArguments(inheritanceTree, method.getParameterTypes(), method.getGenericParameterTypes(), method.getParameterAnnotations(), classLoader);
    }
    
    protected Object[] getArguments(Constructor<?> ctor, Map<String, Class<?>[]> inheritanceTree, ClassLoader classLoader) {
        return getArguments(inheritanceTree, ctor.getParameterTypes(), ctor.getGenericParameterTypes(), ctor.getParameterAnnotations(), classLoader);
    }
    
    protected Object[] getArguments(Map<String, Class<?>[]> inheritanceTree, Class<?>[] parameters, Type[] generics, Annotation[][] annotations,
            ClassLoader classLoader) {
        Object[] arguments = null;
        Class<?>[] genericTypes;
        Class<?>[] candidates;
        int index = 0;
        Class<?> cls;
        Annotation qualifier;
        
        if (parameters.length > 0) {
            arguments = new Object[parameters.length];
            for (Class<?> parameterType : parameters) {
                genericTypes = null;
                if (ParameterizedType.class.isAssignableFrom(generics[index].getClass())) {
                    genericTypes = getGenericTypes((ParameterizedType) generics[index]);
                }
                
                candidates = inheritanceTree.get(parameterType.getName());
                qualifier = getQualifier(annotations[index]);
                cls = typeDeterminator.determineClass(parameterType, candidates, qualifier, classLoader);
                arguments[index] = instance(cls, inheritanceTree, classLoader, genericTypes, qualifier);
                index++;
            }
        }
        
        return arguments;
    }
    
    protected Annotation getQualifier(Annotation[] annotations) {
        if (annotations != null && annotations.length > 0) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType().isAnnotationPresent(Qualifier.class)) {
                    return annotation;
                }
            }
        }
        
        return null;
    }
    
    protected Constructor<?> getInjectableConstructor(Class<?> type) {
        Constructor<?>[] ctors = type.getDeclaredConstructors();
        
        for (Constructor<?> ctor : ctors) {
            if (ctor.isAnnotationPresent(Inject.class)) {
                LOGGER.debug("getInjectableConstructor - constructor found for {} {}", type, ctor);
                if (!ctor.isAccessible()) {
                    ctor.setAccessible(true);
                }
                return ctor;
            }
        }
        
        return null;
    }
    
    protected Constructor<?> getDefaultConstructor(Class<?> type) {
        Constructor<?>[] ctors = type.getDeclaredConstructors();
        
        for (Constructor<?> ctor : ctors) {
            if (ctor.getParameterTypes().length == 0) {
                if (!ctor.isAccessible()) {
                    ctor.setAccessible(true);
                }
                LOGGER.debug("getDefaultConstructor - got constructor for {}", type);
                return ctor;
            }
        }
        
        return null;
    }
    
    protected Class<?>[] getGenericTypes(ParameterizedType type) {
        List<Class<?>> list = new ArrayList<Class<?>>();
        
        for (Type tmp : type.getActualTypeArguments()) {
            if (tmp instanceof Class) {
                list.add((Class<?>) tmp);
            }
        }
        
        return list.toArray(new Class<?>[] {});
    }
    
}