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

import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import org.apache.log4j.Logger;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.AdapterTokenStore;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.RequestAuthenticator;
import org.keycloak.adapters.spi.HttpFacade;
import org.keycloak.adapters.springsecurity.facade.SimpleHttpFacade;
import org.keycloak.adapters.springsecurity.token.SpringSecurityAdapterTokenStoreFactory;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.util.Http;
import org.keycloak.enums.TokenStore;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * This class provides some utility methods to deal with Keycloak Authentication.
 */
public class KeyCloakHelper {

    private final static Logger LOGGER = Logger.getLogger(KeycloakSessionServiceDelegate.class);


    protected final SpringSecurityAdapterTokenStoreFactory adapterTokenStoreFactory;
    protected AdapterDeploymentContext keycloakContext;

    public KeyCloakHelper(AdapterDeploymentContext keycloakContext) {
        this.adapterTokenStoreFactory = new SpringSecurityAdapterTokenStoreFactory();
        this.keycloakContext = keycloakContext;
    }

    /**
     * @param request  request.
     * @param response response.
     * @return return a KeycloakDeployment instance.
     */
    public KeycloakDeployment getDeployment(HttpServletRequest request, HttpServletResponse response) {
        HttpFacade exchange = new SimpleHttpFacade(request, response);
        return getDeployment(exchange);
    }

    /**
     * @param exchange the httpFacade.
     * @return return a KeycloakDeployment instance.
     */
    public KeycloakDeployment getDeployment(HttpFacade exchange) {
        KeycloakDeployment deployment = keycloakContext.resolveDeployment(exchange);
        deployment.setTokenStore(TokenStore.COOKIE);
        deployment.setDelegateBearerErrorResponseSending(true);
        return deployment;
    }

    /**
     * Return the request authenticator that will be used by the filter to perform the various authentication steps.
     *
     * @param request    the request.
     * @param response   the response.
     * @param deployment the deployment instance.
     * @return the request authenticator.
     */
    public RequestAuthenticator getAuthenticator(HttpServletRequest request, HttpServletResponse response, KeycloakDeployment deployment) {
        request =
                new KeyCloakRequestWrapper(request);
        AdapterTokenStore tokenStore =
                adapterTokenStoreFactory.createAdapterTokenStore(deployment, request, response);
        SimpleHttpFacade simpleHttpFacade = new SimpleHttpFacade(request, response);
        return
                new GeoStoreKeycloakAuthenticator(
                        simpleHttpFacade, request, deployment, tokenStore, -1);
    }

    /**
     * Issue a refresh token operation.
     *
     * @return the new Authentication instance with new tokens if they were expired.
     */
    public AccessTokenResponse refreshToken(AdapterConfig adapter, String refreshToken) {
        Configuration clientConf = getClientConfiguration(adapter);
        String url = adapter.getAuthServerUrl() + "/realms/" + adapter.getRealm() + "/protocol/openid-connect/token";
        String clientId = adapter.getResource();
        String secret = (String) adapter.getCredentials().get("secret");
        Http http = new Http(clientConf, (params, headers) -> {
        });

        return http.<AccessTokenResponse>post(url)
                .authentication()
                .client()
                .form()
                .param("grant_type", "refresh_token")
                .param("refresh_token", refreshToken)
                .param("client_id", clientId)
                .param("client_secret", secret)
                .response()
                .json(AccessTokenResponse.class)
                .execute();
    }

    /**
     * Build a Configuration instance out of a specific AdapterConfig.
     *
     * @param config the AdapterConfig.
     * @return the Configuration instance.
     */
    public Configuration getClientConfiguration(AdapterConfig config) {
        String serverUrl = config.getAuthServerUrl();
        String realm = config.getRealm();
        String resource = config.getResource();
        Map<String, Object> credentials = config.getCredentials();
        return new Configuration(serverUrl, realm, resource, credentials, null);
    }

    //

    /**
     * Builds an authentication instance out of the passed values.
     * Sets it to the cache and to the SecurityContext to be sure the new token is updates.
     *
     * @param cache     the auth cache.
     * @param oldToken  the old token.
     * @param newToken  the new token.
     * @param expiresIn the expiration of the new token.
     * @return the new Authentication object.
     */
    public Authentication updateAuthentication(TokenAuthenticationCache cache, String oldToken, String newToken, String refreshToken, long expiresIn) {
        Authentication authentication = cache.get(oldToken);
        if (authentication == null)
            authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof PreAuthenticatedAuthenticationToken) {
            if (LOGGER.isDebugEnabled())
                LOGGER.info("Updating the cache and the SecurityContext with new Auth details");
            cache.removeEntry(oldToken);

            PreAuthenticatedAuthenticationToken updated = new PreAuthenticatedAuthenticationToken(authentication.getPrincipal(), authentication.getCredentials(), authentication.getAuthorities());
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Updating keycloak details.");
            KeycloakTokenDetails details = new KeycloakTokenDetails(newToken, refreshToken, expiresIn);
            updated.setDetails(details);

            cache.putCacheEntry(newToken, updated);
            SecurityContextHolder.getContext().setAuthentication(updated);
            authentication = updated;
        }
        return authentication;
    }
}
