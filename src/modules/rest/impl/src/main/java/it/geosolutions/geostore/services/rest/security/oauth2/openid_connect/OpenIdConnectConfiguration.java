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

import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import javax.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

public class OpenIdConnectConfiguration extends OAuth2Configuration {

    private static final Logger LOGGER = LogManager.getLogger(OpenIdConnectConfiguration.class);

    public static final String PKCE_CODE_VERIFIER_SESSION_ATTR = "oidc.pkce.code_verifier";

    String jwkURI;
    String postLogoutRedirectUri;
    boolean sendClientSecret = false;
    boolean allowBearerTokens = true;
    boolean usePKCE = false;
    int maxTokenAgeSecs = 0;

    public String getJwkURI() {
        return jwkURI;
    }

    public void setJwkURI(String jwkURI) {
        this.jwkURI = jwkURI;
    }

    public String getPostLogoutRedirectUri() {
        return postLogoutRedirectUri;
    }

    public void setPostLogoutRedirectUri(String postLogoutRedirectUri) {
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }

    /**
     * If true, the client secret will be sent to the token endpoint. This is useful for clients
     * that are not capable of keeping the client secret confidential. ref.:
     * https://tools.ietf.org/html/rfc6749#section-2.3.1
     *
     * @return boolean
     */
    public boolean isSendClientSecret() {
        return sendClientSecret;
    }

    public void setSendClientSecret(boolean sendClientSecret) {
        this.sendClientSecret = sendClientSecret;
    }

    public boolean isAllowBearerTokens() {
        return allowBearerTokens;
    }

    public void setAllowBearerTokens(boolean allowBearerTokens) {
        this.allowBearerTokens = allowBearerTokens;
    }

    /**
     * Enables the use of Authorization Code Flow with Proof Key for Code Exchange (PKCE) for the
     * authorization endpoint. ref.:
     * https://auth0.com/docs/get-started/authentication-and-authorization-flow/authorization-code-flow-with-pkce
     *
     * @return the authorization endpoint.
     * @return boolean
     */
    public boolean isUsePKCE() {
        return usePKCE;
    }

    public void setUsePKCE(boolean usePKCE) {
        this.usePKCE = usePKCE;
    }

    public int getMaxTokenAgeSecs() {
        return maxTokenAgeSecs;
    }

    public void setMaxTokenAgeSecs(int maxTokenAgeSecs) {
        this.maxTokenAgeSecs = maxTokenAgeSecs;
    }

    @Override
    public AuthenticationEntryPoint getAuthenticationEntryPoint() {
        if (!usePKCE) {
            return super.getAuthenticationEntryPoint();
        }
        return (request, response, authException) -> {
            try {
                // Generate code_verifier: 32 random bytes, base64url-encoded
                byte[] randomBytes = new byte[32];
                new SecureRandom().nextBytes(randomBytes);
                String codeVerifier =
                        Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

                // Compute code_challenge = BASE64URL(SHA-256(code_verifier))
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
                String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

                // Store verifier in HTTP session
                HttpSession session = request.getSession(true);
                session.setAttribute(PKCE_CODE_VERIFIER_SESSION_ATTR, codeVerifier);

                // Build login URI with code_challenge parameters
                String loginUri =
                        buildLoginUri()
                                + "&code_challenge="
                                + codeChallenge
                                + "&code_challenge_method=S256";
                response.sendRedirect(loginUri);
            } catch (Exception e) {
                LOGGER.error("Failed to generate PKCE parameters", e);
                response.sendRedirect(buildLoginUri());
            }
        };
    }

    /**
     * Build the logout endpoint.
     *
     * @param token the current access_token.
     * @return the logout endpoint.
     */
    @Override
    public Endpoint buildLogoutEndpoint(
            String token, String accessToken, OAuth2Configuration configuration) {
        Endpoint result = null;
        String uri = getLogoutUri();
        String idToken = OAuth2Utils.getIdToken() != null ? OAuth2Utils.getIdToken() : accessToken;
        if (uri != null) {
            HttpHeaders headers = new HttpHeaders();

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            if (idToken != null) {
                params.put("token_type_hint", Collections.singletonList("id_token"));
                headers.set("Authorization", "Bearer " + idToken);
            }
            if (StringUtils.hasText(getPostLogoutRedirectUri()))
                params.put(
                        "post_logout_redirect_uri",
                        Collections.singletonList(getPostLogoutRedirectUri()));
            getLogoutRequestParams(token, getClientId(), params);

            HttpEntity<MultiValueMap<String, String>> requestEntity =
                    new HttpEntity<>(null, headers);

            result = new Endpoint(HttpMethod.GET, appendParameters(params, uri), requestEntity);
        }
        return result;
    }
}
