package it.geosolutions.geostore.services.rest.utils;

import it.geosolutions.geostore.services.rest.security.keycloak.KeyCloakFilter;
import org.apache.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

public class GeoStoreContext implements ApplicationContextAware {

    /**
     * Static application context provided to {@link #setApplicationContext(ApplicationContext)}
     * during initalization.
     *
     * <p>This context is used by methods such as {@link #bean(String)}, {@link #bean(Class)}.
     */
    static ApplicationContext context;

    private final static Logger LOGGER = Logger.getLogger(GeoStoreContext.class);


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context=applicationContext;
    }

    public static <T> T bean(Class<T> clazz){
        T result=null;
        try {
            if (context!=null) result= context.getBean(clazz);
        } catch (Exception e){
            LOGGER.error("Error while retrieving the bean of type " + clazz.getSimpleName(),e);
        }
        return result;
    }

    public static Object bean(String name){
        Object result=null;
        try {
            if (context != null) result = context.getBean(name);
        } catch (BeansException e){
            LOGGER.error("Error while retrieving the bean with name " + name,e);
        }
        return result;
    }

    public static <T> T bean(String name, Class<T> clazz){
        return clazz.cast(bean(name));
    }
}
