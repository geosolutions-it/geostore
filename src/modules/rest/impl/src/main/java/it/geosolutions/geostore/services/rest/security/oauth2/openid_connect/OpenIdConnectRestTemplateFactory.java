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

import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreOAuthRestTemplate;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.enancher.ClientSecretRequestEnhancer;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.enancher.PKCERequestEnhancer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.oauth2.client.DefaultOAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.token.AccessTokenProvider;
import org.springframework.security.oauth2.client.token.AccessTokenProviderChain;
import org.springframework.security.oauth2.client.token.AccessTokenRequest;
import org.springframework.security.oauth2.client.token.DefaultRequestEnhancer;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeResourceDetails;
import org.springframework.security.oauth2.client.token.grant.implicit.ImplicitAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.password.ResourceOwnerPasswordAccessTokenProvider;
import org.springframework.security.oauth2.common.AuthenticationScheme;
import org.springframework.security.oauth2.provider.token.store.jwk.JwkTokenStore;

/**
 * Factory that creates fully configured {@link GeoStoreOAuthRestTemplate} instances for OpenID
 * Connect providers. Extracted from {@link OpenIdConnectSecurityConfiguration} to allow
 * programmatic creation of per-provider rest templates.
 */
public final class OpenIdConnectRestTemplateFactory {

    private static final Logger LOGGER =
            LogManager.getLogger(OpenIdConnectRestTemplateFactory.class);

    private OpenIdConnectRestTemplateFactory() {}

    public static GeoStoreOAuthRestTemplate create(
            OpenIdConnectConfiguration config, AccessTokenRequest accessTokenRequest) {
        OAuth2ProtectedResourceDetails details = buildResourceDetails();
        GeoStoreOAuthRestTemplate restTemplate =
                new GeoStoreOAuthRestTemplate(
                        details, new DefaultOAuth2ClientContext(accessTokenRequest), config);
        setJacksonConverter(restTemplate);

        AuthorizationCodeAccessTokenProvider authProvider =
                new AuthorizationCodeAccessTokenProvider();
        authProvider.setStateMandatory(false);

        if (config.isUsePKCE()) {
            LOGGER.info("Using PKCE for provider {}", config.getProvider());
            authProvider.setTokenRequestEnhancer(new PKCERequestEnhancer(config));
        } else if (config.isSendClientSecret()) {
            LOGGER.info("Using client secret for provider {}", config.getProvider());
            authProvider.setTokenRequestEnhancer(new ClientSecretRequestEnhancer());
        } else {
            authProvider.setTokenRequestEnhancer(new DefaultRequestEnhancer());
        }

        AccessTokenProvider chain =
                new AccessTokenProviderChain(
                        Arrays.<AccessTokenProvider>asList(
                                authProvider,
                                new ImplicitAccessTokenProvider(),
                                new ResourceOwnerPasswordAccessTokenProvider(),
                                new ClientCredentialsAccessTokenProvider()));
        restTemplate.setAccessTokenProvider(chain);

        if (config.getJwkURI() != null && !config.getJwkURI().isEmpty()) {
            restTemplate.setTokenStore(new JwkTokenStore(config.getJwkURI()));
        }

        return restTemplate;
    }

    private static OAuth2ProtectedResourceDetails buildResourceDetails() {
        AuthorizationCodeResourceDetails details = new AuthorizationCodeResourceDetails();
        details.setId("oauth2-client");
        details.setGrantType("authorization_code");
        details.setTokenName("authorization_code");
        details.setAuthenticationScheme(AuthenticationScheme.header);
        details.setClientAuthenticationScheme(AuthenticationScheme.form);
        return details;
    }

    private static void setJacksonConverter(OAuth2RestTemplate template) {
        MappingJackson2HttpMessageConverter jacksonConverter = null;
        for (HttpMessageConverter<?> converter : template.getMessageConverters()) {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                jacksonConverter = (MappingJackson2HttpMessageConverter) converter;
                break;
            }
        }
        if (jacksonConverter == null) {
            jacksonConverter = new MappingJackson2HttpMessageConverter();
            template.getMessageConverters().add(jacksonConverter);
        }
        jacksonConverter.setSupportedMediaTypes(
                Collections.singletonList(
                        new MediaType("application", "json", StandardCharsets.UTF_8)));
    }
}
