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
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.enancher.ClientSecretRequestEnhancer;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.enancher.PKCERequestEnhancer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.token.AccessTokenProvider;
import org.springframework.security.oauth2.client.token.AccessTokenProviderChain;
import org.springframework.security.oauth2.client.token.DefaultRequestEnhancer;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeResourceDetails;
import org.springframework.security.oauth2.client.token.grant.implicit.ImplicitAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.password.ResourceOwnerPasswordAccessTokenProvider;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;
import org.springframework.security.oauth2.provider.token.store.jwk.JwkTokenStore;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration.CONFIG_NAME_SUFFIX;

/**
 * Configuration class for OpenID Connect client.
 */
@Configuration("oidcSecConfig")
@EnableOAuth2Client
public class OpenIdConnectSecurityConfiguration extends OAuth2GeoStoreSecurityConfiguration {

    static final String CONF_BEAN_NAME = "oidc" + CONFIG_NAME_SUFFIX;
    private final static Logger LOGGER = LogManager.getLogger(OpenIdConnectSecurityConfiguration.class);

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
     * Must have "request" scope
     */
    @Override
    @Bean(value = "oidcOpenIdRestTemplate")
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    public GeoStoreOAuthRestTemplate oauth2RestTemplate() {
        GeoStoreOAuthRestTemplate oAuth2RestTemplate = restTemplate();
        setJacksonConverter(oAuth2RestTemplate);

        AuthorizationCodeAccessTokenProvider authorizationAccessTokenProvider = authorizationAccessTokenProvider();

        OpenIdConnectConfiguration idConfig = (OpenIdConnectConfiguration) configuration();
        if (idConfig.isUsePKCE()) {
            LOGGER.info("Using PKCE for OpenID Connect");
            authorizationAccessTokenProvider.setTokenRequestEnhancer(new PKCERequestEnhancer(idConfig));
        } else if (idConfig.isSendClientSecret()) {
            LOGGER.info("Using client secret for OpenID Connect");
            authorizationAccessTokenProvider.setTokenRequestEnhancer(new ClientSecretRequestEnhancer());
        } else {
            LOGGER.info("Using default request enhancer for OpenID Connect");
            authorizationAccessTokenProvider.setTokenRequestEnhancer(new DefaultRequestEnhancer());
        }

        AccessTokenProvider accessTokenProviderChain =
                new AccessTokenProviderChain(
                        Arrays.<AccessTokenProvider>asList(
                                authorizationAccessTokenProvider,
                                new ImplicitAccessTokenProvider(),
                                new ResourceOwnerPasswordAccessTokenProvider(),
                                new ClientCredentialsAccessTokenProvider()));

        oAuth2RestTemplate.setAccessTokenProvider(accessTokenProviderChain);

        if (idConfig.getJwkURI() != null && !"".equals(idConfig.getJwkURI())) {
            LOGGER.info("Using JWK for OpenID Connect");
            oAuth2RestTemplate.setTokenStore(new JwkTokenStore(idConfig.getJwkURI()));
        }

        return oAuth2RestTemplate;
    }

    @Bean(name = "authorizationAccessTokenProvider")
    @Scope(value = "prototype")
    public AuthorizationCodeAccessTokenProvider authorizationAccessTokenProvider() {
        AuthorizationCodeAccessTokenProvider authorizationCodeAccessTokenProvider =
                new AuthorizationCodeAccessTokenProvider();
        authorizationCodeAccessTokenProvider.setStateMandatory(false);
        authorizationCodeAccessTokenProvider.setTokenRequestEnhancer(new DefaultRequestEnhancer());
        return authorizationCodeAccessTokenProvider;
    }

    @Bean
    public OpenIdConnectFilter oidcOpenIdFilter() {
        return new OpenIdConnectFilter(oidcTokenServices(), oauth2RestTemplate(), configuration(), oidcCache());
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
