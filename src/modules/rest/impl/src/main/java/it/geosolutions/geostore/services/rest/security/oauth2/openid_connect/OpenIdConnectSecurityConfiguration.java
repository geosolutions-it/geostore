/* ====================================================================
 *
 * Copyright (C) 2024 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.security.oauth2.openid_connect;

import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreOAuthRestTemplate;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2GeoStoreSecurityConfiguration;
import it.geosolutions.geostore.services.rest.security.oauth2.OpenIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeResourceDetails;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration.CONFIG_NAME_SUFFIX;

/**
 * Configuration class for OpenID Connect client.
 */
@Configuration("oidcSecConfig")
@EnableOAuth2Client
public class OpenIdConnectSecurityConfiguration extends OAuth2GeoStoreSecurityConfiguration {

    static final String CONF_BEAN_NAME = "oidc" + CONFIG_NAME_SUFFIX;

    @Override
    public OAuth2ProtectedResourceDetails resourceDetails() {
        AuthorizationCodeResourceDetails details =
                (AuthorizationCodeResourceDetails) super.resourceDetails();
        details.setTokenName("authorization_code");
        return details;
    }

    @Override
    @Bean(value = CONF_BEAN_NAME)
    public OAuth2Configuration configuration() {
        return new OpenIdConnectConfiguration();
    }

    /**
     * Must have "session" scope
     */
    @Override
    @Bean(value = "oidcOpenIdRestTemplate")
    @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public GeoStoreOAuthRestTemplate oauth2RestTemplate() {
        return super.oauth2RestTemplate();
    }

    @Bean
    public OpenIdFilter oidcOpenIdFilter() {
        return new OpenIdFilter(oidcTokenServices(), oauth2RestTemplate(), configuration(), oidcCache());
    }

    @Bean
    public OpenIdConnectTokenServices oidcTokenServices() {
        return new OpenIdConnectTokenServices(configuration().getPrincipalKey());
    }

    @Bean
    public TokenAuthenticationCache oidcCache() {
        return new TokenAuthenticationCache();
    }
}
