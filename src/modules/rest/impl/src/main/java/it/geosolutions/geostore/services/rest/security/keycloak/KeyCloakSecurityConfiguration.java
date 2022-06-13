package it.geosolutions.geostore.services.rest.security.keycloak;

import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration.CONFIG_NAME_SUFFIX;


@Configuration("keycloakConfig")
public class KeyCloakSecurityConfiguration {

    static final String CONF_BEAN_NAME = "keycloak" + CONFIG_NAME_SUFFIX;

    public static final String CACHE_BEAN_NAME ="keycloakCache";

    @Bean(value=CONF_BEAN_NAME)
    public KeyCloakConfiguration keycloakConfiguration(){
        return new KeyCloakConfiguration();
    }

    @Bean
    public KeyCloakFilter keycloakFilter(){
        return new KeyCloakFilter(keyCloakHelper(),keycloakCache(), keycloakConfiguration(),keycloakAuthenticationProvider());
    }

    @Bean
    public GeoStoreKeycloakAuthProvider keycloakAuthenticationProvider(){
        return new GeoStoreKeycloakAuthProvider(keycloakConfiguration());
    }

    @Bean(value= CACHE_BEAN_NAME)
    public TokenAuthenticationCache keycloakCache(){
        return new TokenAuthenticationCache();
    }

    @Bean
    public AdapterDeploymentContext keycloackContext(){
        KeycloakDeployment deployment =
                KeycloakDeploymentBuilder.build(keycloakConfiguration().readAdapterConfig());
        return new AdapterDeploymentContext(deployment);
    }

    @Bean
    public KeyCloakHelper keyCloakHelper(){
        return new KeyCloakHelper(keycloackContext());
    }
}
