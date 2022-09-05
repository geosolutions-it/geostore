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
package it.geosolutions.geostore.services.rest.security.oauth2.google;

import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2GeoStoreSecurityConfiguration;
import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreOAuthRestTemplate;
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
 * Configuration class for OAuth2 Google client.
 */
@Configuration("googleSecConfig")
@EnableOAuth2Client
public class OAuthGoogleSecurityConfiguration extends OAuth2GeoStoreSecurityConfiguration {

    static final String CONF_BEAN_NAME = "google" + CONFIG_NAME_SUFFIX;

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
        return new GoogleOAuth2Configuration();
    }

    /**
     * Must have "session" scope
     */
    @Override
    @Bean(value = "googleOpenIdRestTemplate")
    @Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public GeoStoreOAuthRestTemplate oauth2RestTemplate() {
        return super.oauth2RestTemplate();
    }

    @Bean
    public OpenIdFilter googleOpenIdFilter(){
        return new OpenIdFilter(googleTokenServices(), oauth2RestTemplate(),configuration(),oAuth2Cache());
    }

    @Bean
    public GoogleTokenServices googleTokenServices(){
        return new GoogleTokenServices(configuration().getPrincipalKey());
    }

    @Bean
    public TokenAuthenticationCache oAuth2Cache(){
        return new TokenAuthenticationCache();
    }

}
