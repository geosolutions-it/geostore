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
import it.geosolutions.geostore.services.rest.security.oauth2.DiscoveryClient;
import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreOAuthRestTemplate;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2GeoStoreAuthenticationFilter;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.enancher.ClientSecretRequestEnhancer;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.enancher.PKCERequestEnhancer;
import org.springframework.security.oauth2.client.token.DefaultRequestEnhancer;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeAccessTokenProvider;
import org.springframework.security.oauth2.provider.token.RemoteTokenServices;
import org.springframework.security.oauth2.provider.token.store.jwk.JwkTokenStore;

/**
 * OpenId Connect filter implementation.
 */
public class OpenIdConnectFilter extends OAuth2GeoStoreAuthenticationFilter {

    /**
     * @param tokenServices            a RemoteTokenServices instance.
     * @param oAuth2RestTemplate       the rest template to use for OAuth2 requests.
     * @param provider                 the authorization code token provider.
     * @param configuration            the OAuth2 configuration.
     * @param tokenAuthenticationCache the cache.
     */
    public OpenIdConnectFilter(RemoteTokenServices tokenServices, GeoStoreOAuthRestTemplate oAuth2RestTemplate, AuthorizationCodeAccessTokenProvider authorizationAccessTokenProvider, OAuth2Configuration configuration, TokenAuthenticationCache tokenAuthenticationCache) {
        super(tokenServices, oAuth2RestTemplate, configuration, tokenAuthenticationCache);
        if (configuration.getDiscoveryUrl() != null && !"".equals(configuration.getDiscoveryUrl()))
            new DiscoveryClient(configuration.getDiscoveryUrl()).autofill(configuration);

        OpenIdConnectConfiguration idConfig = (OpenIdConnectConfiguration) configuration;
        if (idConfig.isUsePKCE())
            authorizationAccessTokenProvider.setTokenRequestEnhancer(new PKCERequestEnhancer(idConfig));
        else if (idConfig.isSendClientSecret())
            authorizationAccessTokenProvider.setTokenRequestEnhancer(new ClientSecretRequestEnhancer());
        else authorizationAccessTokenProvider.setTokenRequestEnhancer(new DefaultRequestEnhancer());

        if (idConfig.getJwkURI() != null && !"".equals(idConfig.getJwkURI())) {
            oAuth2RestTemplate.setTokenStore(new JwkTokenStore(idConfig.getJwkURI()));
        }
    }
}
