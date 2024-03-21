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

import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DiscoveryClient to perform a discovery request and set the value to the OAuth2Configuration instance.
 */
public class DiscoveryClient {
    private static final String PROVIDER_END_PATH = "/.well-known/openid-configuration";
    private static final String AUTHORIZATION_ENDPOINT_ATTR_NAME = "authorization_endpoint";
    private static final String TOKEN_ENDPOINT_ATTR_NAME = "token_endpoint";
    private static final String USERINFO_ENDPOINT_ATTR_NAME = "userinfo_endpoint";
    private static final String END_SESSION_ENDPOINT = "end_session_endpoint";
    private static final String JWK_SET_URI_ATTR_NAME = "jwks_uri";
    private static final String SCOPES_SUPPORTED = "scopes_supported";
    private static final String REVOCATION_ENDPOINT = "revocation_endpoint";

    private final RestTemplate restTemplate;
    private String location;

    public DiscoveryClient(String location) {
        setLocation(location);
        this.restTemplate = new RestTemplate();
    }

    public DiscoveryClient(String location, RestTemplate restTemplate) {
        setLocation(location);
        this.restTemplate = restTemplate;
    }

    private static String appendPath(String... pathComponents) {
        StringBuilder result = new StringBuilder(pathComponents[0]);
        for (int i = 1; i < pathComponents.length; i++) {
            String component = pathComponents[i];
            boolean endsWithSlash = result.charAt(result.length() - 1) == '/';
            boolean startsWithSlash = component.startsWith("/");
            if (endsWithSlash && startsWithSlash) {
                result.setLength(result.length() - 1);
            } else if (!endsWithSlash && !startsWithSlash) {
                result.append("/");
            }
            result.append(component);
        }

        return result.toString();
    }

    private void setLocation(String location) {
        if (!location.endsWith(PROVIDER_END_PATH)) {
            location = appendPath(location, PROVIDER_END_PATH);
        }
        this.location = location;
    }

    /**
     * Fill the OAuth2Configuration instance with the values found in the discovery response.
     *
     * @param conf the OAuth2Configuration.
     */
    public void autofill(OAuth2Configuration conf) {
        if (location != null) {
            Map response = restTemplate.getForObject(this.location, Map.class);
            Optional.ofNullable(response.get(getAuthorizationEndpointAttrName()))
                    .ifPresent(uri -> conf.setAuthorizationUri((String) uri));
            Optional.ofNullable(response.get(getTokenEndpointAttrName()))
                    .ifPresent(uri -> conf.setAccessTokenUri((String) uri));
            Optional.ofNullable(response.get(getUserinfoEndpointAttrName()))
                    .ifPresent(uri -> conf.setCheckTokenEndpointUrl((String) uri));
            Optional.ofNullable(response.get(getJwkSetUriAttrName()))
                    .ifPresent(uri -> conf.setIdTokenUri((String) uri));
            Optional.ofNullable(response.get(getEndSessionEndpoint()))
                    .ifPresent(uri -> conf.setLogoutUri((String) uri));
            Optional.ofNullable(response.get(getScopesSupported()))
                    .ifPresent(
                            s -> {
                                @SuppressWarnings("unchecked")
                                List<String> scopes = (List<String>) s;
                                conf.setScopes(collectScopes(scopes));
                            });
            Optional.ofNullable(response.get(getRevocationEndpoint()))
                    .ifPresent(
                            s -> conf.setRevokeEndpoint((String) s));
        }
    }

    private String collectScopes(List<String> scopes) {
        return scopes.stream().collect(Collectors.joining(","));
    }

    protected String getUserinfoEndpointAttrName() {
        return USERINFO_ENDPOINT_ATTR_NAME;
    }

    protected String getEndSessionEndpoint() {
        return END_SESSION_ENDPOINT;
    }

    protected String getJwkSetUriAttrName() {
        return JWK_SET_URI_ATTR_NAME;
    }

    protected String getProviderEndPath() {
        return PROVIDER_END_PATH;
    }

    protected String getAuthorizationEndpointAttrName() {
        return AUTHORIZATION_ENDPOINT_ATTR_NAME;
    }

    protected String getTokenEndpointAttrName() {
        return TOKEN_ENDPOINT_ATTR_NAME;
    }

    protected String getScopesSupported() {
        return SCOPES_SUPPORTED;
    }

    protected String getRevocationEndpoint() {
        return REVOCATION_ENDPOINT;
    }
}
