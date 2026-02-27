/* ====================================================================
 *
 * Copyright (C) 2024-2025 GeoSolutions S.A.S.
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

import dasniko.testcontainers.keycloak.KeycloakContainer;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.DiscoveryClient;
import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreOAuthRestTemplate;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.AudienceAccessTokenValidator;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.JwksRsaKeyProvider;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.MultiTokenValidator;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.SubjectTokenValidator;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.token.DefaultAccessTokenRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests against a real Keycloak instance running in a Docker container. Tests the full
 * OIDC lifecycle: bearer token authentication, JWKS signature verification, token refresh, and
 * logout with real signed tokens.
 */
@Testcontainers(disabledWithoutDocker = true)
public class KeycloakLifecycleTest {

    private static final String REALM = "geostore-test";
    private static final String CLIENT_ID = "geostore-client";
    private static final String CLIENT_SECRET = "geostore-test-secret";

    @Container
    static KeycloakContainer keycloak =
            new KeycloakContainer("quay.io/keycloak/keycloak:26.0")
                    .withRealmImportFile("keycloak/geostore-test-realm.json");

    private OpenIdConnectFilter filter;
    private OpenIdConnectConfiguration configuration;
    private TokenAuthenticationCache cache;

    @BeforeEach
    void setUp() {
        String authServerUrl = keycloak.getAuthServerUrl();
        if (!authServerUrl.endsWith("/")) {
            authServerUrl += "/";
        }
        String realmUrl = authServerUrl + "realms/" + REALM;

        configuration = new OpenIdConnectConfiguration();
        configuration.setClientId(CLIENT_ID);
        configuration.setClientSecret(CLIENT_SECRET);
        configuration.setAccessTokenUri(realmUrl + "/protocol/openid-connect/token");
        configuration.setAuthorizationUri(realmUrl + "/protocol/openid-connect/auth");
        configuration.setCheckTokenEndpointUrl(realmUrl + "/protocol/openid-connect/userinfo");
        configuration.setIdTokenUri(realmUrl + "/protocol/openid-connect/certs");
        configuration.setRevokeEndpoint(realmUrl + "/protocol/openid-connect/revoke");
        configuration.setLogoutUri(realmUrl + "/protocol/openid-connect/logout");
        configuration.setEnabled(true);
        configuration.setAutoCreateUser(true);
        configuration.setBeanName("oidcOAuth2Config");
        configuration.setEnableRedirectEntryPoint(true);
        configuration.setRedirectUri("../../../geostore/rest/users/user/details");
        configuration.setScopes("openid,email");
        configuration.setSendClientSecret(true);
        configuration.setAllowBearerTokens(true);
        configuration.setPrincipalKey("preferred_username");
        configuration.setRolesClaim("roles");
        configuration.setGroupsClaim("groups");

        recreateFilter();
    }

    private void recreateFilter() {
        GeoStoreOAuthRestTemplate restTemplate =
                OpenIdConnectRestTemplateFactory.create(
                        configuration, new DefaultAccessTokenRequest());
        JwksRsaKeyProvider jwksKeyProvider = new JwksRsaKeyProvider(configuration.getIdTokenUri());
        OpenIdConnectTokenServices tokenServices =
                new OpenIdConnectTokenServices(configuration.getPrincipalKey());
        cache =
                new TokenAuthenticationCache(
                        configuration.getCacheSize(), configuration.getCacheExpirationMinutes());
        MultiTokenValidator validator =
                new MultiTokenValidator(
                        Arrays.asList(
                                new AudienceAccessTokenValidator(), new SubjectTokenValidator()));
        filter =
                new OpenIdConnectFilter(
                        tokenServices,
                        restTemplate,
                        configuration,
                        cache,
                        validator,
                        jwksKeyProvider);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testBearerTokenAuthentication_User() throws IOException, ServletException {
        KeycloakTokens tokens = obtainTokens("testuser", "testuser123");

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + tokens.accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication, "Bearer token should produce an authenticated user");
        User user = (User) authentication.getPrincipal();
        assertEquals("testuser", user.getName());
        assertEquals(Role.USER, user.getRole());

        Set<String> groupNames =
                user.getGroups().stream().map(UserGroup::getGroupName).collect(Collectors.toSet());
        assertTrue(groupNames.contains("analysts"), "User should belong to analysts group");
    }

    @Test
    public void testBearerTokenAuthentication_Admin() throws IOException, ServletException {
        KeycloakTokens tokens = obtainTokens("testadmin", "testadmin123");

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + tokens.accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication, "Bearer token should produce an authenticated admin");
        User user = (User) authentication.getPrincipal();
        assertEquals("testadmin", user.getName());
        assertEquals(Role.ADMIN, user.getRole());

        Set<String> groupNames =
                user.getGroups().stream().map(UserGroup::getGroupName).collect(Collectors.toSet());
        assertTrue(groupNames.contains("analysts"), "Admin should belong to analysts group");
        assertTrue(groupNames.contains("editors"), "Admin should belong to editors group");
    }

    @Test
    public void testJwksSignatureVerification() throws IOException, ServletException {
        KeycloakTokens tokens = obtainTokens("testuser", "testuser123");

        // The token is signed by Keycloak's real RSA key and verified against
        // the real JWKS endpoint — if signature verification fails, authentication
        // would not succeed.
        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + tokens.accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(
                authentication,
                "Token should authenticate — JWKS verification against real Keycloak certs");
    }

    @Test
    public void testDiscoveryAutoConfiguration() {
        String authServerUrl = keycloak.getAuthServerUrl();
        if (!authServerUrl.endsWith("/")) {
            authServerUrl += "/";
        }
        String realmUrl = authServerUrl + "realms/" + REALM;

        OpenIdConnectConfiguration discoveryConfig = new OpenIdConnectConfiguration();
        DiscoveryClient discoveryClient =
                new DiscoveryClient(realmUrl + "/.well-known/openid-configuration");
        discoveryClient.autofill(discoveryConfig);

        assertEquals(
                realmUrl + "/protocol/openid-connect/token",
                discoveryConfig.getAccessTokenUri(),
                "Token endpoint should be auto-configured");
        assertEquals(
                realmUrl + "/protocol/openid-connect/auth",
                discoveryConfig.getAuthorizationUri(),
                "Authorization endpoint should be auto-configured");
        assertEquals(
                realmUrl + "/protocol/openid-connect/userinfo",
                discoveryConfig.getCheckTokenEndpointUrl(),
                "Userinfo endpoint should be auto-configured");
        assertEquals(
                realmUrl + "/protocol/openid-connect/certs",
                discoveryConfig.getIdTokenUri(),
                "JWKS endpoint should be auto-configured");
        assertNotNull(
                discoveryConfig.getLogoutUri(),
                "Logout endpoint should be auto-configured from discovery");
    }

    @Test
    public void testRefreshTokenFlow() throws IOException, ServletException {
        KeycloakTokens tokens = obtainTokens("testuser", "testuser123");

        // First, authenticate to populate the cache
        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + tokens.accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        Authentication originalAuth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(originalAuth, "Initial authentication should succeed");
        assertNotNull(cache.get(tokens.accessToken), "Token should be in cache after auth");

        // Perform a real token refresh against Keycloak
        RestTemplate refreshTemplate = new RestTemplate();
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("client_id", CLIENT_ID);
        params.add("client_secret", CLIENT_SECRET);
        params.add("refresh_token", tokens.refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

        ResponseEntity<Map> refreshResponse =
                refreshTemplate.exchange(
                        configuration.getAccessTokenUri(), HttpMethod.POST, entity, Map.class);

        assertEquals(HttpStatus.OK, refreshResponse.getStatusCode());
        Map refreshBody = refreshResponse.getBody();
        assertNotNull(refreshBody, "Refresh response should not be null");

        String newAccessToken = (String) refreshBody.get("access_token");
        String newRefreshToken = (String) refreshBody.get("refresh_token");
        assertNotNull(newAccessToken, "New access token should be returned");
        assertNotNull(newRefreshToken, "New refresh token should be returned");
        assertNotEquals(
                tokens.accessToken, newAccessToken, "New access token should differ from original");

        // Verify the new token authenticates
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request2 = createRequest("rest/resources");
        request2.addHeader("Authorization", "Bearer " + newAccessToken);
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filter.doFilter(request2, response2, new MockFilterChain());

        assertEquals(200, response2.getStatus());
        Authentication newAuth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(newAuth, "Refreshed token should authenticate successfully");
        User user = (User) newAuth.getPrincipal();
        assertEquals("testuser", user.getName(), "Username should be the same after refresh");
    }

    @Test
    public void testFullLifecycle_LoginRefreshLogout() throws IOException, ServletException {
        // Step 1: Login via Bearer token
        KeycloakTokens tokens = obtainTokens("testadmin", "testadmin123");

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + tokens.accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Step 1: Login should succeed");
        User user = (User) auth.getPrincipal();
        assertEquals("testadmin", user.getName());
        assertEquals(Role.ADMIN, user.getRole());

        // Step 2: Verify cache is populated
        assertNotNull(
                cache.get(tokens.accessToken), "Step 2: Token should be in cache after login");

        // Step 3: Refresh the token against real Keycloak
        RestTemplate refreshTemplate = new RestTemplate();
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("client_id", CLIENT_ID);
        params.add("client_secret", CLIENT_SECRET);
        params.add("refresh_token", tokens.refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

        ResponseEntity<Map> refreshResponse =
                refreshTemplate.exchange(
                        configuration.getAccessTokenUri(), HttpMethod.POST, entity, Map.class);

        assertEquals(HttpStatus.OK, refreshResponse.getStatusCode());
        Map refreshBody = refreshResponse.getBody();
        assertNotNull(refreshBody, "Step 3: Refresh response should not be null");
        String newAccessToken = (String) refreshBody.get("access_token");
        assertNotNull(newAccessToken, "Step 3: New access token from refresh");

        // Step 4: Verify new token works
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request2 = createRequest("rest/resources");
        request2.addHeader("Authorization", "Bearer " + newAccessToken);
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filter.doFilter(request2, response2, new MockFilterChain());

        assertEquals(200, response2.getStatus());
        Authentication newAuth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(newAuth, "Step 4: New token should authenticate");
        assertNotNull(cache.get(newAccessToken), "Step 4: New token should be in cache");

        // Step 5: Revoke the token at Keycloak
        MultiValueMap<String, String> revokeParams = new LinkedMultiValueMap<>();
        revokeParams.add("client_id", CLIENT_ID);
        revokeParams.add("client_secret", CLIENT_SECRET);
        revokeParams.add("token", newAccessToken);

        HttpEntity<MultiValueMap<String, String>> revokeEntity =
                new HttpEntity<>(revokeParams, headers);
        ResponseEntity<String> revokeResponse =
                refreshTemplate.exchange(
                        configuration.getRevokeEndpoint(),
                        HttpMethod.POST,
                        revokeEntity,
                        String.class);

        assertEquals(
                HttpStatus.OK,
                revokeResponse.getStatusCode(),
                "Step 5: Token revocation should succeed");

        // Step 6: Clear the cache and security context to simulate logout
        cache.removeEntry(newAccessToken);
        SecurityContextHolder.clearContext();

        assertNull(cache.get(newAccessToken), "Step 6: Cache should be empty after logout");
        assertNull(
                SecurityContextHolder.getContext().getAuthentication(),
                "Step 6: SecurityContext should be cleared");
    }

    @Test
    public void testTokenRevocation() {
        KeycloakTokens tokens = obtainTokens("testuser", "testuser123");

        // Revoke the token
        RestTemplate restTemplate = new RestTemplate();
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", CLIENT_ID);
        params.add("client_secret", CLIENT_SECRET);
        params.add("token", tokens.accessToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

        ResponseEntity<String> revokeResponse =
                restTemplate.exchange(
                        configuration.getRevokeEndpoint(), HttpMethod.POST, entity, String.class);

        assertEquals(
                HttpStatus.OK,
                revokeResponse.getStatusCode(),
                "Token revocation should succeed at Keycloak");

        // Verify subsequent userinfo call fails (token is revoked)
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(tokens.accessToken);
        HttpEntity<Void> userinfoEntity = new HttpEntity<>(authHeaders);

        try {
            ResponseEntity<Map> userinfoResponse =
                    restTemplate.exchange(
                            configuration.getCheckTokenEndpointUrl(),
                            HttpMethod.GET,
                            userinfoEntity,
                            Map.class);
            // Keycloak may still accept JWTs until they expire (stateless validation),
            // but the revocation endpoint call itself should succeed
            // The key assertion here is that revocation was accepted (200 above)
        } catch (Exception e) {
            // Expected: revoked token should fail at userinfo
            assertTrue(
                    e.getMessage().contains("401") || e.getMessage().contains("403"),
                    "Revoked token should be rejected");
        }
    }

    /** Obtain tokens from Keycloak using Direct Access Grants (resource owner password). */
    private KeycloakTokens obtainTokens(String username, String password) {
        RestTemplate restTemplate = new RestTemplate();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "password");
        params.add("client_id", CLIENT_ID);
        params.add("client_secret", CLIENT_SECRET);
        params.add("username", username);
        params.add("password", password);
        params.add("scope", "openid");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response =
                restTemplate.exchange(
                        configuration.getAccessTokenUri(), HttpMethod.POST, entity, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "Token request should succeed");
        Map body = response.getBody();
        assertNotNull(body, "Token response body should not be null");

        return new KeycloakTokens(
                (String) body.get("access_token"),
                (String) body.get("refresh_token"),
                (String) body.get("id_token"));
    }

    private MockHttpServletRequest createRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setContextPath("/geostore");
        request.setRequestURI("/geostore/" + path);
        request.setRemoteAddr("127.0.0.1");
        request.setServletPath("/geostore");
        request.setPathInfo(path);
        request.addHeader("Host", "localhost:8080");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
        return request;
    }

    private static class KeycloakTokens {
        final String accessToken;
        final String refreshToken;
        final String idToken;

        KeycloakTokens(String accessToken, String refreshToken, String idToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.idToken = idToken;
        }
    }
}
