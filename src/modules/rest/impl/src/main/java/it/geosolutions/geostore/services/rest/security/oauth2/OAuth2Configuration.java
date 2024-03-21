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

import it.geosolutions.geostore.services.rest.security.IdPConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;

/**
 * This class represent the geostore configuration for an OAuth2/OpenId provider.
 * An OAuth2Configuration bean should be provided for each OAuth2 provider. The bean id has to be
 * {providerName}OAuth2Config.
 */
public class OAuth2Configuration extends IdPConfiguration {

    public static final String CONFIG_NAME_SUFFIX = "OAuth2Config";
    public static final String CONFIGURATION_NAME = "CONFIGURATION_NAME";
    private final static Logger LOGGER = LogManager.getLogger(OAuth2GeoStoreAuthenticationFilter.class);
    protected String clientId;
    protected String clientSecret;
    protected String accessTokenUri;
    protected String authorizationUri;
    protected String checkTokenEndpointUrl;
    protected String logoutUri;
    protected String scopes;
    protected String idTokenUri;
    protected String discoveryUrl;
    protected String revokeEndpoint;
    protected boolean enableRedirectEntryPoint = false;
    protected String principalKey;
    protected String rolesClaim;
    protected String groupsClaim;

    /**
     * Get an authentication entry point instance meant to handle redirect to the authorization page.
     *
     * @return the authentication entry point.
     */
    public AuthenticationEntryPoint getAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            String loginUri = buildLoginUri();
            response.sendRedirect(loginUri);
        };
    }

    /**
     * Build the authorization uri to the OAuth2 provider.
     *
     * @return the authorization uri completed with the various query strings.
     */
    public String buildLoginUri() {
        return buildLoginUri(null, new String[]{});
    }

    /**
     * Build the authorization uri to the OAuth2 provider.
     *
     * @param accessType the access type request param value. Can be null.
     * @return the authorization uri completed with the various query strings.
     */
    public String buildLoginUri(String accessType) {
        return buildLoginUri(accessType, new String[]{});
    }

    /**
     * @param accessType       the access type request param value. Can be null.
     * @param additionalScopes additional scopes not set at from geostore-ovr.properties. Can be null.
     * @return the
     */
    public String buildLoginUri(String accessType, String... additionalScopes) {
        final StringBuilder loginUri = new StringBuilder(getAuthorizationUri());
        loginUri.append("?")
                .append("response_type=code")
                .append("&")
                .append("client_id=")
                .append(getClientId())
                .append("&")
                .append("scope=")
                .append(getScopes().replace(",", "%20"));
        for (String s : additionalScopes) {
            loginUri.append("%20").append(s);
        }
        loginUri.append("&")
                .append("redirect_uri=")
                .append(getRedirectUri());
        if (accessType != null) loginUri.append("&").append("access_type=").append(accessType);
        String finalUrl = loginUri.toString();
        if (LOGGER.isDebugEnabled()) LOGGER.info("Going to request authorization to this endpoint " + finalUrl);
        return finalUrl;
    }

    /**
     * Builds the refresh token URI.
     *
     * @return the complete refresh token uri.
     */
    public String buildRefreshTokenURI() {
        return buildRefreshTokenURI(null);
    }

    /**
     * Builds the refresh token URI.
     *
     * @param accessType the access type request param.
     * @return the complete refresh token uri.
     */
    public String buildRefreshTokenURI(String accessType) {
        final StringBuilder refreshUri = new StringBuilder(getAccessTokenUri());
        refreshUri.append("?")
                .append("&")
                .append("client_id=")
                .append(getClientId())
                .append("&")
                .append("scope=")
                .append(getScopes().replace(",", "%20"));
        if (accessType != null)
            refreshUri.append("&").append("access_type=").append(accessType);
        return refreshUri.toString();
    }

    /**
     * @return the clientId.
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Set the client id.
     *
     * @param cliendId the client id.
     */
    public void setClientId(String cliendId) {
        this.clientId = cliendId;
    }

    /**
     * @return the client secret.
     */
    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Set the client secret.
     *
     * @param clientSecret the client secret.
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    /**
     * @return the access token uri
     */
    public String getAccessTokenUri() {
        return accessTokenUri;
    }

    /**
     * Set the access token uri
     *
     * @param accessTokenUri the access token uri.
     */
    public void setAccessTokenUri(String accessTokenUri) {
        this.accessTokenUri = accessTokenUri;
    }

    /**
     * @return the authorization URI.
     */
    public String getAuthorizationUri() {
        return authorizationUri;
    }

    /**
     * Set the authorization URI.
     *
     * @param authorizationUri the authorization URI.
     */
    public void setAuthorizationUri(String authorizationUri) {
        this.authorizationUri = authorizationUri;
    }

    /**
     * @return the check token endpoint URL.
     */
    public String getCheckTokenEndpointUrl() {
        return checkTokenEndpointUrl;
    }

    /**
     * Set the check token endpoint URL.
     *
     * @param checkTokenEndpointUrl the check token endpoint URL.
     */
    public void setCheckTokenEndpointUrl(String checkTokenEndpointUrl) {
        this.checkTokenEndpointUrl = checkTokenEndpointUrl;
    }

    /**
     * @return the logout URI.
     */
    public String getLogoutUri() {
        return logoutUri;
    }

    /**
     * Set the logout URI.
     *
     * @param logoutUri the logout URI.
     */
    public void setLogoutUri(String logoutUri) {
        this.logoutUri = logoutUri;
    }

    /**
     * Get the configured scopes as a String.
     *
     * @return the scopes.
     */
    public String getScopes() {
        return scopes;
    }

    /**
     * Set the scopes.
     *
     * @param scopes the scopes.
     */
    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    /**
     * @return the id Token URI.
     */
    public String getIdTokenUri() {
        return idTokenUri;
    }

    /**
     * Set the id token URI.
     *
     * @param idTokenUri the id Token URI.
     */
    public void setIdTokenUri(String idTokenUri) {
        this.idTokenUri = idTokenUri;
    }

    /**
     * @return the Discovery URL.
     */
    public String getDiscoveryUrl() {
        return discoveryUrl;
    }

    /**
     * Set the discovery URL.
     *
     * @param discoveryUrl the discovery URL.
     */
    public void setDiscoveryUrl(String discoveryUrl) {
        this.discoveryUrl = discoveryUrl;
    }

    /**
     * Check if the configuration is valid or not. Is considered invalid if either one of client id, secret,
     * authorization, accessToke Uri was not provided.
     *
     * @return
     */
    public boolean isInvalid() {
        return clientId == null || clientSecret == null || authorizationUri == null || accessTokenUri == null;
    }

    /**
     * @return the revoke endpoint.
     */
    public String getRevokeEndpoint() {
        return revokeEndpoint;
    }

    /**
     * Set the revoke endpoint.
     *
     * @param revokeEndpoint the revoke endpoint.
     */
    public void setRevokeEndpoint(String revokeEndpoint) {
        this.revokeEndpoint = revokeEndpoint;
    }

    /**
     * Get the string identifier the provider associated with the configuration.
     *
     * @return the provider name.
     */
    public String getProvider() {
        return getBeanName().replaceAll(CONFIG_NAME_SUFFIX, "");
    }

    /**
     * Append the request params to the URL.
     *
     * @param params the request params.
     * @param url    the url.
     * @return the complete url.
     */
    protected String appendParameters(MultiValueMap<String, String> params, String url) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        builder.queryParams(params);
        return builder.build().toUriString();
    }

    /**
     * Build the revoke endpoint.
     *
     * @param token the access_token to revoke.
     * @return the revoke endpoint.
     */
    public Endpoint buildRevokeEndpoint(String token) {
        Endpoint result = null;
        if (revokeEndpoint != null) {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.put("token", Collections.singletonList(token));
            result = new Endpoint(HttpMethod.POST, appendParameters(params, revokeEndpoint));
        }
        return result;
    }

    /**
     * Build the logout endpoint.
     *
     * @param token the current access_token.
     * @return the logout endpoint.
     */
    public Endpoint buildLogoutEndpoint(String token) {
        Endpoint result = null;
        if (logoutUri != null) {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.put("token", Collections.singletonList(token));
            result = new Endpoint(HttpMethod.GET, appendParameters(params, logoutUri));
        }
        return result;
    }

    /**
     * @return true if redirect to authorization is active always. False otherwise.
     */
    public boolean isEnableRedirectEntryPoint() {
        return enableRedirectEntryPoint;
    }

    /**
     * Set the enableRedirectEntryPoint flag.
     *
     * @param enableRedirectEntryPoint true to enable always redirection, false otherwise.
     */
    public void setEnableRedirectEntryPoint(boolean enableRedirectEntryPoint) {
        this.enableRedirectEntryPoint = enableRedirectEntryPoint;
    }

    /**
     * Get the principal key. Default is email.
     *
     * @return the principal key.
     */
    public String getPrincipalKey() {
        if (principalKey == null || "".equals(principalKey)) return "email";
        return principalKey;
    }

    /**
     * Set the principal key.
     *
     * @param principalKey the principal key.
     */
    public void setPrincipalKey(String principalKey) {
        this.principalKey = principalKey;
    }

    /**
     * The roles claim name.
     *
     * @return the roles claim name.
     */
    public String getRolesClaim() {
        return rolesClaim;
    }

    /**
     * Set the roles claim name.
     *
     * @param rolesClaim the roles claim name.
     */
    public void setRolesClaim(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    /**
     * @return the groups claim name.
     */
    public String getGroupsClaim() {
        return groupsClaim;
    }

    /**
     * Set the groups claim name.
     *
     * @param groupsClaim the groups claim name.
     */
    public void setGroupsClaim(String groupsClaim) {
        this.groupsClaim = groupsClaim;
    }

    /**
     * Class the represent and endpoint with a HTTP method.
     */
    public static class Endpoint {

        private String url;

        private HttpMethod method;

        public Endpoint(HttpMethod method, String url) {
            this.method = method;
            this.url = url;
        }

        /**
         * @return the url.
         */
        public String getUrl() {
            return url;
        }

        /**
         * Set the url.
         *
         * @param url the url.
         */
        public void setUrl(String url) {
            this.url = url;
        }

        /**
         * @return the HttpMethod.
         */
        public HttpMethod getMethod() {
            return method;
        }

        /**
         * Set the HttpMethod.
         *
         * @param method the HttpMethod.
         */
        public void setMethod(HttpMethod method) {
            this.method = method;
        }
    }
}
