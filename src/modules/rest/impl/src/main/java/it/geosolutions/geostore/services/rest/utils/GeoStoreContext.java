package it.geosolutions.geostore.services.rest.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class GeoStoreContext implements ApplicationContextAware {

    /**
     * Static application context provided to {@link #setApplicationContext(ApplicationContext)}
     * during initalization.
     *
     * <p>This context is used by methods such as {@link #bean(String)}, {@link #bean(Class)}.
     */
    static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context=applicationContext;
    }

    public static <T> T bean(Class<T> clazz){
        return context.getBean(clazz);
    }

    public static Object bean(String name){
        return context.getBean(name);
    }

    public static <T> T bean(String name, Class<T> clazz){
        return clazz.cast(bean(name));
    }
}
