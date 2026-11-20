/* ====================================================================
 *
 * Copyright (C) 2026 GeoSolutions S.A.S.
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

import static org.junit.jupiter.api.Assertions.*;

import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Tests for the RP-Initiated Logout endpoint built by {@link OpenIdConnectConfiguration}: the
 * id_token_hint must carry the ID token (never the access token), otherwise providers like Keycloak
 * reject the request and keep the SSO session alive.
 */
public class OpenIdConnectLogoutEndpointTest {

    private static final String LOGOUT_URI =
            "https://idp.example.com/realms/test/protocol/openid-connect/logout";

    private OpenIdConnectConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new OpenIdConnectConfiguration();
        configuration.setLogoutUri(LOGOUT_URI);
        configuration.setClientId("test-client");
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    static String jwt(String payloadJson) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header =
                enc.encodeToString(
                        "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signature = enc.encodeToString("sig".getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + signature;
    }

    @Test
    void usesPassedIdTokenAsHint() {
        String idToken = jwt("{\"typ\":\"ID\",\"sub\":\"user\",\"sid\":\"abc\"}");

        OAuth2Configuration.Endpoint endpoint =
                configuration.buildLogoutEndpoint(idToken, "opaque-access-token", configuration);

        assertNotNull(endpoint);
        assertEquals(HttpMethod.GET, endpoint.getMethod());
        assertTrue(endpoint.getUrl().startsWith(LOGOUT_URI));
        assertTrue(
                endpoint.getUrl().contains("id_token_hint=" + idToken),
                "the ID token passed by the logout flow must be used as id_token_hint");
        assertTrue(endpoint.getUrl().contains("client_id=test-client"));
    }

    @Test
    void usesAnIdTokenWithoutTypAndNonceAsHint() {
        // Entra ID, Google, Okta and Auth0 put no typ claim in the ID token, and no nonce either
        // unless the authorization request carried one.
        String idToken =
                jwt(
                        "{\"iss\":\"https://login.example.com/tenant/v2.0\",\"sub\":\"user\","
                                + "\"aud\":\"test-client\",\"exp\":1893456000,\"iat\":1893452400}");

        OAuth2Configuration.Endpoint endpoint =
                configuration.buildLogoutEndpoint(idToken, "opaque-access-token", configuration);

        assertNotNull(endpoint);
        assertTrue(
                endpoint.getUrl().contains("id_token_hint=" + idToken),
                "an ID token without typ and nonce must be used as id_token_hint");
    }

    @Test
    void neverUsesAccessOrRefreshTokenAsHint() {
        String refreshToken = jwt("{\"typ\":\"Refresh\",\"sub\":\"user\"}");
        String accessToken = jwt("{\"typ\":\"Bearer\",\"sub\":\"user\"}");

        OAuth2Configuration.Endpoint endpoint =
                configuration.buildLogoutEndpoint(refreshToken, accessToken, configuration);

        assertNotNull(endpoint);
        assertFalse(
                endpoint.getUrl().contains("id_token_hint"),
                "a refresh or access token must not be sent as id_token_hint");
        assertTrue(endpoint.getUrl().contains("client_id=test-client"));
    }

    @Test
    void noHintWithoutAnyToken() {
        OAuth2Configuration.Endpoint endpoint =
                configuration.buildLogoutEndpoint(null, "opaque-access-token", configuration);

        assertNotNull(endpoint);
        assertFalse(endpoint.getUrl().contains("id_token_hint"));
    }

    @Test
    void fallsBackToIdTokenFromCodeExchange() {
        String idToken = jwt("{\"typ\":\"ID\",\"sub\":\"user\"}");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(OAuth2Utils.ID_TOKEN_VALUE, idToken);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        OAuth2Configuration.Endpoint endpoint =
                configuration.buildLogoutEndpoint(
                        "opaque-refresh-token", "opaque-access-token", configuration);

        assertNotNull(endpoint);
        assertTrue(endpoint.getUrl().contains("id_token_hint=" + idToken));
    }

    @Test
    void fallsBackToIdTokenRequestAttribute() {
        String idToken = jwt("{\"typ\":\"ID\",\"sub\":\"user\"}");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(OAuth2Utils.ID_TOKEN_PARAM, idToken);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        OAuth2Configuration.Endpoint endpoint =
                configuration.buildLogoutEndpoint(
                        "opaque-refresh-token", "opaque-access-token", configuration);

        assertNotNull(endpoint);
        assertTrue(endpoint.getUrl().contains("id_token_hint=" + idToken));
    }

    @Test
    void includesPostLogoutRedirectUri() {
        configuration.setPostLogoutRedirectUri("https://app.example.com/");
        String idToken = jwt("{\"typ\":\"ID\",\"sub\":\"user\"}");

        OAuth2Configuration.Endpoint endpoint =
                configuration.buildLogoutEndpoint(idToken, null, configuration);

        assertNotNull(endpoint);
        assertTrue(endpoint.getUrl().contains("post_logout_redirect_uri="));
    }

    @Test
    void returnsNullWithoutLogoutUri() {
        configuration.setLogoutUri(null);
        assertNull(configuration.buildLogoutEndpoint("token", "access", configuration));
    }

    @Test
    void isIdTokenDetection() {
        assertTrue(
                OpenIdConnectConfiguration.isIdToken(jwt("{\"typ\":\"ID\",\"sub\":\"u\"}")),
                "Keycloak-style ID token (typ=ID)");
        assertTrue(
                OpenIdConnectConfiguration.isIdToken(jwt("{\"sub\":\"u\",\"nonce\":\"n\"}")),
                "ID token without typ, carrying a nonce");
        assertTrue(
                OpenIdConnectConfiguration.isIdToken(jwt("{\"sub\":\"u\",\"aud\":\"c\"}")),
                "ID token without typ and nonce (Entra ID, Google, Okta, Auth0)");
        assertFalse(
                OpenIdConnectConfiguration.isIdToken(jwt("{\"typ\":\"Bearer\",\"sub\":\"u\"}")),
                "access token (typ=Bearer)");
        assertFalse(
                OpenIdConnectConfiguration.isIdToken(jwt("{\"typ\":\"Refresh\",\"sub\":\"u\"}")),
                "refresh token (typ=Refresh)");
        assertFalse(
                OpenIdConnectConfiguration.isIdToken(jwt("{\"typ\":\"Offline\",\"sub\":\"u\"}")),
                "offline token (typ=Offline)");
        assertFalse(OpenIdConnectConfiguration.isIdToken("opaque-token"), "opaque string");
        assertFalse(OpenIdConnectConfiguration.isIdToken("a.b.c"), "three parts, not a JWT");
        assertFalse(OpenIdConnectConfiguration.isIdToken(null), "null");
    }
}
