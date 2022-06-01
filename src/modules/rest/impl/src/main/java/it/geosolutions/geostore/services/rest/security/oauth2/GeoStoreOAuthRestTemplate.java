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

import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.resource.UserRedirectRequiredException;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.store.jwk.JwkTokenStore;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Optional;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ID_TOKEN_PARAM;

/**
 * Custom OAuthRestTemplate. Allows the extraction of the id token from the response.
 */
public class GeoStoreOAuthRestTemplate extends OAuth2RestTemplate {

    private JwkTokenStore store;


    public static final String ID_TOKEN_VALUE = "OpenIdConnect-IdTokenValue";

    private String idTokenParam;


    public GeoStoreOAuthRestTemplate(
            OAuth2ProtectedResourceDetails resource, OAuth2ClientContext context, OAuth2Configuration configuration) {
        this(resource, context, configuration, ID_TOKEN_PARAM);
    }

    public GeoStoreOAuthRestTemplate(
            OAuth2ProtectedResourceDetails resource, OAuth2ClientContext context, OAuth2Configuration configuration, String idTokenParam) {
        super(resource, context);
        this.store = new JwkTokenStore(configuration.getIdTokenUri());
        this.idTokenParam = idTokenParam;
    }

    @Override
    public OAuth2AccessToken getAccessToken() throws UserRedirectRequiredException {
        OAuth2AccessToken token = super.getAccessToken();
        if (token != null) extractIDToken(token);
        return token;
    }

    /**
     * Extract the id token from the additional information store inside the {@link OAuth2AccessToken} instance.
     * @param token
     */
    protected void extractIDToken(OAuth2AccessToken token) {
        Object maybeIdToken = token.getAdditionalInformation().get(idTokenParam);
        if (maybeIdToken instanceof String) {
            String idToken = (String) maybeIdToken;
            setAsRequestAttribute(ID_TOKEN_VALUE, idToken);
            // among other things, this verifies the token
            if (store != null) store.readAuthentication(idToken);
        }
    }

    private void setAsRequestAttribute(String key, String value) {
        Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ra -> ra instanceof ServletRequestAttributes)
                .map(ra -> ((ServletRequestAttributes) ra))
                .map(ServletRequestAttributes::getRequest)
                .ifPresent(r -> r.setAttribute(key, value));
    }

    public OAuth2Authentication readAuthentication(String idToken) {
        return store.readAuthentication(idToken);
    }
}
