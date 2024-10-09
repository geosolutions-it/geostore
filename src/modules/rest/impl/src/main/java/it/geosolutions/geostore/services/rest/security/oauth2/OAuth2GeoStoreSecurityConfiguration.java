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
package it.geosolutions.geostore.services.rest.security.oauth2;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.oauth2.client.DefaultOAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.token.AccessTokenProvider;
import org.springframework.security.oauth2.client.token.AccessTokenProviderChain;
import org.springframework.security.oauth2.client.token.AccessTokenRequest;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeResourceDetails;
import org.springframework.security.oauth2.client.token.grant.implicit.ImplicitAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.password.ResourceOwnerPasswordAccessTokenProvider;
import org.springframework.security.oauth2.common.AuthenticationScheme;

/**
 * Base abstract class for @Configuration classes providing the necessary beans from the Spring
 * OAuth2 mechanism.
 */
@Configuration
public abstract class OAuth2GeoStoreSecurityConfiguration implements ApplicationContextAware {

    static final String DETAILS_ID = "oauth2-client";

    protected ApplicationContext context;

    @Resource
    @Qualifier("accessTokenRequest")
    private AccessTokenRequest accessTokenRequest;

    /**
     * Returns the resource bean containing the Access Token Request info.
     *
     * @return the accessTokenRequest
     */
    public AccessTokenRequest getAccessTokenRequest() {
        return accessTokenRequest;
    }

    protected OAuth2ProtectedResourceDetails resourceDetails() {
        AuthorizationCodeResourceDetails details = new AuthorizationCodeResourceDetails();
        details.setId(getDetailsId());

        details.setGrantType("authorization_code");
        details.setAuthenticationScheme(AuthenticationScheme.header);
        details.setClientAuthenticationScheme(AuthenticationScheme.form);

        return details;
    }

    protected String getDetailsId() {
        return DETAILS_ID;
    }

    protected GeoStoreOAuthRestTemplate restTemplate() {
        return new GeoStoreOAuthRestTemplate(
                resourceDetails(),
                new DefaultOAuth2ClientContext(getAccessTokenRequest()),
                configuration());
    }

    public GeoStoreOAuthRestTemplate oauth2RestTemplate() {
        GeoStoreOAuthRestTemplate oAuth2RestTemplate = restTemplate();
        setJacksonConverter(oAuth2RestTemplate);
        AuthorizationCodeAccessTokenProvider authorizationCodeAccessTokenProvider =
                new AuthorizationCodeAccessTokenProvider();
        authorizationCodeAccessTokenProvider.setStateMandatory(false);

        AccessTokenProvider accessTokenProviderChain =
                new AccessTokenProviderChain(
                        Arrays.<AccessTokenProvider>asList(
                                authorizationCodeAccessTokenProvider,
                                new ImplicitAccessTokenProvider(),
                                new ResourceOwnerPasswordAccessTokenProvider(),
                                new ClientCredentialsAccessTokenProvider()));

        oAuth2RestTemplate.setAccessTokenProvider(accessTokenProviderChain);
        return oAuth2RestTemplate;
    }

    protected void setJacksonConverter(OAuth2RestTemplate oAuth2RestTemplate) {
        List<HttpMessageConverter<?>> converterList = oAuth2RestTemplate.getMessageConverters();
        MappingJackson2HttpMessageConverter jacksonConverter = null;
        for (HttpMessageConverter<?> converter : converterList) {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                jacksonConverter = (MappingJackson2HttpMessageConverter) converter;
                break;
            }
        }
        if (jacksonConverter == null) {
            jacksonConverter = new MappingJackson2HttpMessageConverter();
            oAuth2RestTemplate.getMessageConverters().add(jacksonConverter);
        }
        jacksonConverter.setSupportedMediaTypes(
                Collections.singletonList(
                        new MediaType("application", "json", StandardCharsets.UTF_8)));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    public abstract OAuth2Configuration configuration();
}
