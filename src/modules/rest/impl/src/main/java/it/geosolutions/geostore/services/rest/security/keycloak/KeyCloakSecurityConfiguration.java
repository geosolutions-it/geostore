/* ====================================================================
 *
 * Copyright (C) 2022 GeoSolutions S.A.S.
 * http://www.geo-solutions.it
 *
 * GPLv3 + Classpath exception
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 *
 * ====================================================================
 *
 * This software consists of voluntary contributions made by developers
 * of GeoSolutions.  For more information on GeoSolutions, please see
 * <http://www.geo-solutions.it/>.
 *
 */
package it.geosolutions.geostore.services.rest.security.keycloak;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration.CONFIG_NAME_SUFFIX;

import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration("keycloakConfig")
public class KeyCloakSecurityConfiguration {

    public static final String CACHE_BEAN_NAME = "keycloakCache";
    static final String CONF_BEAN_NAME = "keycloak" + CONFIG_NAME_SUFFIX;

    @Bean(value = CONF_BEAN_NAME)
    public KeyCloakConfiguration keycloakConfiguration() {
        return new KeyCloakConfiguration();
    }

    @Bean
    public KeycloakAdminClientConfiguration keycloakRESTClient() {
        return new KeycloakAdminClientConfiguration();
    }

    @Bean
    public KeyCloakFilter keycloakFilter() {
        return new KeyCloakFilter(
                keyCloakHelper(),
                keycloakCache(),
                keycloakConfiguration(),
                keycloakAuthenticationProvider());
    }

    @Bean
    public GeoStoreKeycloakAuthProvider keycloakAuthenticationProvider() {
        return new GeoStoreKeycloakAuthProvider(keycloakConfiguration());
    }

    @Bean(value = CACHE_BEAN_NAME)
    public TokenAuthenticationCache keycloakCache() {
        return new TokenAuthenticationCache();
    }

    @Bean
    public AdapterDeploymentContext keycloackContext() {
        AdapterConfig config = keycloakConfiguration().readAdapterConfig();
        AdapterDeploymentContext context;
        if (config != null) {
            KeycloakDeployment deployment = KeycloakDeploymentBuilder.build(config);
            context = new AdapterDeploymentContext(deployment);
        } else {
            context = new AdapterDeploymentContext();
        }
        return context;
    }

    @Bean
    public KeyCloakHelper keyCloakHelper() {
        return new KeyCloakHelper(keycloackContext());
    }
}
