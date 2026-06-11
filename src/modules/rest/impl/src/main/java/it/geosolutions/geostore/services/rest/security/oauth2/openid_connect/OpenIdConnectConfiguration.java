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

import it.geosolutions.geostore.services.rest.security.oauth2.JWTHelper;
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
import org.springframework.web.context.request.RequestContextHolder;

public class OpenIdConnectConfiguration extends OAuth2Configuration {

    private static final Logger LOGGER = LogManager.getLogger(OpenIdConnectConfiguration.class);

    public static final String PKCE_CODE_VERIFIER_SESSION_ATTR = "oidc.pkce.code_verifier";

    String jwkURI;
    String postLogoutRedirectUri;
    boolean sendClientSecret = false;
    boolean allowBearerTokens = true;
    boolean usePKCE = false;
    int maxTokenAgeSecs = 0;
    String bearerTokenStrategy = "jwt";
    String accessType;

    // JWE (encrypted token) support
    String jweKeyStoreFile;
    String jweKeyStorePassword;
    String jweKeyStoreType = "PKCS12";
    String jweKeyAlias;
    String jweKeyPassword;

    // Microsoft Graph API integration
    boolean msGraphEnabled = false;
    String msGraphEndpoint = "https://graph.microsoft.com/v1.0";
    boolean msGraphGroupsEnabled = true;
    boolean msGraphRolesEnabled = false;
    String msGraphAppId;

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

    /**
     * Strategy for validating bearer tokens: "jwt" (default, decode and verify JWT),
     * "introspection" (call the token introspection endpoint), or "auto" (try JWT first, fallback
     * to introspection).
     */
    public String getBearerTokenStrategy() {
        return bearerTokenStrategy;
    }

    public void setBearerTokenStrategy(String bearerTokenStrategy) {
        this.bearerTokenStrategy = bearerTokenStrategy;
    }

    /**
     * Access type for the authorization request. Set to "offline" to request a refresh token (e.g.
     * for Google). When null, no access_type parameter is appended.
     */
    public String getAccessType() {
        return accessType;
    }

    public void setAccessType(String accessType) {
        this.accessType = accessType;
    }

    public String getJweKeyStoreFile() {
        return jweKeyStoreFile;
    }

    public void setJweKeyStoreFile(String jweKeyStoreFile) {
        this.jweKeyStoreFile = jweKeyStoreFile;
    }

    public String getJweKeyStorePassword() {
        return jweKeyStorePassword;
    }

    public void setJweKeyStorePassword(String jweKeyStorePassword) {
        this.jweKeyStorePassword = jweKeyStorePassword;
    }

    public String getJweKeyStoreType() {
        return jweKeyStoreType;
    }

    public void setJweKeyStoreType(String jweKeyStoreType) {
        this.jweKeyStoreType = jweKeyStoreType;
    }

    public String getJweKeyAlias() {
        return jweKeyAlias;
    }

    public void setJweKeyAlias(String jweKeyAlias) {
        this.jweKeyAlias = jweKeyAlias;
    }

    public String getJweKeyPassword() {
        return jweKeyPassword;
    }

    public void setJweKeyPassword(String jweKeyPassword) {
        this.jweKeyPassword = jweKeyPassword;
    }

    public boolean isMsGraphEnabled() {
        return msGraphEnabled;
    }

    public void setMsGraphEnabled(boolean msGraphEnabled) {
        this.msGraphEnabled = msGraphEnabled;
    }

    public String getMsGraphEndpoint() {
        return msGraphEndpoint;
    }

    public void setMsGraphEndpoint(String msGraphEndpoint) {
        this.msGraphEndpoint = msGraphEndpoint;
    }

    public boolean isMsGraphGroupsEnabled() {
        return msGraphGroupsEnabled;
    }

    public void setMsGraphGroupsEnabled(boolean msGraphGroupsEnabled) {
        this.msGraphGroupsEnabled = msGraphGroupsEnabled;
    }

    public boolean isMsGraphRolesEnabled() {
        return msGraphRolesEnabled;
    }

    public void setMsGraphRolesEnabled(boolean msGraphRolesEnabled) {
        this.msGraphRolesEnabled = msGraphRolesEnabled;
    }

    public String getMsGraphAppId() {
        return msGraphAppId;
    }

    public void setMsGraphAppId(String msGraphAppId) {
        this.msGraphAppId = msGraphAppId;
    }

    @Override
    public String buildLoginUri() {
        return super.buildLoginUri(accessType);
    }

    @Override
    public String buildRefreshTokenURI() {
        return super.buildRefreshTokenURI(accessType);
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
     * Build the OIDC RP-Initiated Logout endpoint. Uses standard parameters: id_token_hint,
     * post_logout_redirect_uri, and client_id.
     *
     * @param token the token handed down by the logout flow: the cached ID token when the logout is
     *     session-based, the refresh token otherwise.
     * @param accessToken the access token (never used as id_token_hint).
     * @param configuration the OAuth2Configuration.
     * @return the logout endpoint, or null if no logoutUri is configured.
     */
    @Override
    public Endpoint buildLogoutEndpoint(
            String token, String accessToken, OAuth2Configuration configuration) {
        String uri = getLogoutUri();
        if (uri == null) return null;

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        String idToken = resolveIdTokenHint(token);
        if (idToken != null) {
            params.put("id_token_hint", Collections.singletonList(idToken));
        } else {
            LOGGER.warn(
                    "No ID token available for RP-initiated logout; calling '{}' without "
                            + "id_token_hint. The provider may require interactive confirmation "
                            + "and keep the SSO session alive.",
                    uri);
        }

        // client_id: some providers require it alongside id_token_hint
        if (StringUtils.hasText(getClientId())) {
            params.put("client_id", Collections.singletonList(getClientId()));
        }

        // post_logout_redirect_uri: where to redirect after logout
        if (StringUtils.hasText(getPostLogoutRedirectUri())) {
            params.put(
                    "post_logout_redirect_uri",
                    Collections.singletonList(getPostLogoutRedirectUri()));
        }

        HttpEntity<MultiValueMap<String, String>> requestEntity =
                new HttpEntity<>(null, new HttpHeaders());
        return new Endpoint(HttpMethod.GET, appendParameters(params, uri), requestEntity);
    }

    /**
     * Picks the ID token for the {@code id_token_hint} parameter. Tries, in order: the token passed
     * down by the logout flow (the ID token cached at login, when the logout is session-based), the
     * thread-local stashed during the token exchange, and the {@code id_token} request attribute
     * set for authenticated requests. Never falls back to the access token: an invalid hint is
     * rejected outright by the provider (leaving the SSO session alive), while a missing one may
     * still be accepted.
     */
    private String resolveIdTokenHint(String token) {
        if (isIdToken(token)) return token;
        if (RequestContextHolder.getRequestAttributes() == null) return null;
        String idToken = OAuth2Utils.getIdToken();
        if (idToken == null) {
            idToken = OAuth2Utils.getRequestAttribute(OAuth2Utils.ID_TOKEN_PARAM);
        }
        return idToken;
    }

    /**
     * Detects whether a token is an OIDC ID token: a JWT whose payload {@code typ} claim is {@code
     * ID} (Keycloak convention) or, lacking a {@code typ} claim, one carrying a {@code nonce}
     * claim. Access tokens ({@code typ=Bearer}), refresh tokens ({@code typ=Refresh}) and opaque
     * strings are rejected.
     */
    static boolean isIdToken(String token) {
        if (token == null || token.chars().filter(c -> c == '.').count() != 2) return false;
        try {
            JWTHelper helper = new JWTHelper(token);
            String typ = helper.getClaim("typ", String.class);
            if (typ != null) return "ID".equalsIgnoreCase(typ);
            return helper.getClaim("nonce", String.class) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
