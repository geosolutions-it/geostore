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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.resource.UserRedirectRequiredException;
import org.springframework.security.oauth2.client.token.AccessTokenRequest;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.store.jwk.JwkTokenStore;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.*;

/**
 * Custom OAuth2RestTemplate. Allows the extraction of the id token from the response.
 */
public class GeoStoreOAuthRestTemplate extends OAuth2RestTemplate {

    public static final String ID_TOKEN_VALUE = "OpenIdConnect-IdTokenValue";
    private final static Logger LOGGER = LogManager.getLogger(GeoStoreOAuthRestTemplate.class);
    private final String idTokenParam;
    private JwkTokenStore store;

    public GeoStoreOAuthRestTemplate(
            OAuth2ProtectedResourceDetails resource, OAuth2ClientContext context, OAuth2Configuration configuration) {
        this(resource, context, configuration, ID_TOKEN_PARAM);
    }

    public GeoStoreOAuthRestTemplate(
            OAuth2ProtectedResourceDetails resource, OAuth2ClientContext context, OAuth2Configuration configuration, String idTokenParam) {
        super(resource, context);
        if (configuration.getIdTokenUri() != null)
            this.store = new JwkTokenStore(configuration.getIdTokenUri());
        this.idTokenParam = idTokenParam;
    }

    @Override
    protected OAuth2AccessToken acquireAccessToken(OAuth2ClientContext oauth2Context)
            throws UserRedirectRequiredException {

        OAuth2AccessToken result = null;
        try {
            result = super.acquireAccessToken(oauth2Context);
            return result;
        } finally {
            // CODE shouldn't typically be displayed since it can be "handed in" for an access/id
            // token So, we don't log the CODE until AFTER it has been handed in.
            // CODE is one-time-use.
            if ((oauth2Context != null) && (oauth2Context.getAccessTokenRequest() != null)) {
                AccessTokenRequest accessTokenRequest = oauth2Context.getAccessTokenRequest();
                if ((accessTokenRequest.getAuthorizationCode() != null)
                        && (!accessTokenRequest.getAuthorizationCode().isEmpty())) {
                    LOGGER.debug(
                            "OIDC: received a CODE from Identity Provider - handing it in for ID/Access Token");
                    LOGGER.debug("OIDC: CODE=" + accessTokenRequest.getAuthorizationCode());
                    if (result != null) {
                        LOGGER.debug(
                                "OIDC: Identity Provider returned Token, type="
                                        + result.getTokenType());
                        LOGGER.debug("OIDC: SCOPES=" + String.join(" ", result.getScope()));
                        final String accessToken = saferJWT(result.getValue());
                        LOGGER.debug("OIDC: ACCESS TOKEN:" + accessToken);
                        RequestContextHolder.getRequestAttributes().setAttribute(
                                ACCESS_TOKEN_PARAM, accessToken, 0);
                        if (result.getAdditionalInformation().containsKey("refresh_token")) {
                            final String refreshToken =
                                    saferJWT(
                                            (String)
                                                    result.getAdditionalInformation()
                                                            .get("refresh_token"));
                            LOGGER.debug("OIDC: REFRESH TOKEN:" + refreshToken);
                            RequestContextHolder.getRequestAttributes().setAttribute(
                                    REFRESH_TOKEN_PARAM, accessToken, 0);
                        }
                        if (result.getAdditionalInformation().containsKey("id_token")) {
                            final String idToken =
                                    saferJWT(
                                            (String)
                                                    result.getAdditionalInformation()
                                                            .get("id_token"));
                            LOGGER.debug("OIDC: ID TOKEN:" + idToken);
                            RequestContextHolder.getRequestAttributes().setAttribute(
                                    ID_TOKEN_PARAM, accessToken, 0);
                        }
                    }
                }
            }
        }
    }

    /**
     * Logs the string value of a token if it's a JWT token - it should be in 3 parts, separated by a
     * "." These 3 sections are: header, claims, signature We only log the 2nd (claims) part. This
     * is safer because without the signature, the token will not validate.
     *
     * <p>We don't log the token directly because it can be used to access protected resources.
     *
     * @param jwt
     * @return
     */
    String saferJWT(String jwt) {
        String[] JWTParts = jwt.split("\\.");
        if (JWTParts.length > 1) return JWTParts[1]; // this is the claims part
        return "NOT A JWT"; // not a JWT
    }

    @Override
    public OAuth2AccessToken getAccessToken() throws UserRedirectRequiredException {
        OAuth2AccessToken token = super.getAccessToken();
        if (token != null) validate(token);
        return token;
    }

    private void validate(OAuth2AccessToken token) {
        Object maybeIdToken = token.getAdditionalInformation().get("id_token");
        if (maybeIdToken instanceof String) {
            String idToken = (String) maybeIdToken;
            setAsRequestAttribute(GeoStoreOAuthRestTemplate.ID_TOKEN_VALUE, idToken);
            // among other things, this verifies the token
            if (store != null) store.readAuthentication(idToken);
            // TODO: the authentication just read could contain roles, could be treated as
            // another role source... but needs to be made available to role computation
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

    public void setTokenStore(JwkTokenStore jwkTokenStore) {
        this.store = jwkTokenStore;
    }
}
