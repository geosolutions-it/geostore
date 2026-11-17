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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import static org.junit.jupiter.api.Assertions.*;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.AudienceAccessTokenValidator;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.JwksRsaKeyProvider;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.MultiTokenValidator;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.SubjectTokenValidator;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

/**
 * Integration test for the multi-provider OIDC support. Verifies that:
 *
 * <ul>
 *   <li>Bearer tokens from provider A authenticate via A's configuration
 *   <li>Bearer tokens from provider B authenticate via B's configuration
 *   <li>Cross-provider isolation: A's token is rejected by B (wrong audience/key)
 *   <li>The {@link CompositeOpenIdConnectFilter} correctly routes bearer tokens to the right
 *       provider
 * </ul>
 *
 * <p>Spring Security 7 port: each per-provider filter is now built from an {@link
 * OpenIdConnectAuthenticationService} (with {@code null} user/group services, so the principal is
 * materialized from token claims) + a thin {@link OpenIdConnectFilter} + an {@link
 * OpenIdConnectRestClient}, replacing the legacy 6-arg constructor / {@code
 * GeoStoreOAuthRestTemplate}.
 */
public class MultiProviderIntegrationTest {

    // Provider A constants
    private static final String PROVIDER_A_NAME = "oidc";
    private static final String PROVIDER_A_CLIENT_ID = "client-id-provider-a";
    private static final String PROVIDER_A_CLIENT_SECRET = "secret-a";
    private static final String PROVIDER_A_KID = "key-provider-a";

    // Provider B constants
    private static final String PROVIDER_B_NAME = "google";
    private static final String PROVIDER_B_CLIENT_ID = "client-id-provider-b";
    private static final String PROVIDER_B_CLIENT_SECRET = "secret-b";
    private static final String PROVIDER_B_KID = "key-provider-b";

    // RSA keys per provider
    private static RSAPublicKey rsaPublicKeyA;
    private static RSAPrivateKey rsaPrivateKeyA;
    private static Algorithm rsaAlgorithmA;

    private static RSAPublicKey rsaPublicKeyB;
    private static RSAPrivateKey rsaPrivateKeyB;
    private static Algorithm rsaAlgorithmB;

    // WireMock servers (one per provider to simulate separate IdPs)
    private static WireMockServer wireMockA;
    private static WireMockServer wireMockB;

    private OpenIdConnectFilter filterA;
    private OpenIdConnectFilter filterB;
    private OpenIdConnectConfiguration configA;
    private OpenIdConnectConfiguration configB;

    @BeforeAll
    static void beforeAll() throws Exception {
        // Generate separate RSA key pairs for each provider
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);

        KeyPair keyPairA = keyGen.generateKeyPair();
        rsaPublicKeyA = (RSAPublicKey) keyPairA.getPublic();
        rsaPrivateKeyA = (RSAPrivateKey) keyPairA.getPrivate();
        rsaAlgorithmA = Algorithm.RSA256(rsaPublicKeyA, rsaPrivateKeyA);

        KeyPair keyPairB = keyGen.generateKeyPair();
        rsaPublicKeyB = (RSAPublicKey) keyPairB.getPublic();
        rsaPrivateKeyB = (RSAPrivateKey) keyPairB.getPrivate();
        rsaAlgorithmB = Algorithm.RSA256(rsaPublicKeyB, rsaPrivateKeyB);

        // Start WireMock for provider A
        wireMockA = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockA.start();

        wireMockA.stubFor(
                WireMock.get(urlEqualTo("/certs"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(buildJwksJson(rsaPublicKeyA, PROVIDER_A_KID))));
        wireMockA.stubFor(
                any(urlPathEqualTo("/userinfo"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(
                                                "{\"email\":\"alice@provider-a.com\","
                                                        + "\"sub\":\"alice-sub-a\"}")));
        // Discovery document of provider A, for the tests that configure it by discoveryUrl only
        String baseA = "http://localhost:" + wireMockA.port();
        wireMockA.stubFor(
                WireMock.get(urlEqualTo("/.well-known/openid-configuration"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(
                                                "{\"issuer\":\""
                                                        + baseA
                                                        + "\",\"authorization_endpoint\":\""
                                                        + baseA
                                                        + "/authorize\",\"token_endpoint\":\""
                                                        + baseA
                                                        + "/token\",\"userinfo_endpoint\":\""
                                                        + baseA
                                                        + "/userinfo\",\"jwks_uri\":\""
                                                        + baseA
                                                        + "/certs\"}")));
        wireMockA.stubFor(
                WireMock.post(urlPathEqualTo("/introspect"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(
                                                "{\"active\":true,"
                                                        + "\"email\":\"alice@provider-a.com\","
                                                        + "\"sub\":\"alice-sub-a\"}")));
        wireMockA.stubFor(
                WireMock.post(urlPathEqualTo("/introspect-inactive"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody("{\"active\":false}")));

        // Start WireMock for provider B
        wireMockB = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockB.start();

        wireMockB.stubFor(
                WireMock.get(urlEqualTo("/certs"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(buildJwksJson(rsaPublicKeyB, PROVIDER_B_KID))));
        wireMockB.stubFor(
                any(urlPathEqualTo("/userinfo"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(
                                                "{\"email\":\"bob@provider-b.com\","
                                                        + "\"sub\":\"bob-sub-b\"}")));
    }

    @AfterAll
    static void afterAll() {
        if (wireMockA != null) wireMockA.stop();
        if (wireMockB != null) wireMockB.stop();
    }

    @BeforeEach
    void setUp() {
        String baseA = "http://localhost:" + wireMockA.port();
        String baseB = "http://localhost:" + wireMockB.port();

        // Configure provider A
        configA = new OpenIdConnectConfiguration();
        configA.setClientId(PROVIDER_A_CLIENT_ID);
        configA.setClientSecret(PROVIDER_A_CLIENT_SECRET);
        configA.setAccessTokenUri(baseA + "/token");
        configA.setAuthorizationUri(baseA + "/authorize");
        configA.setCheckTokenEndpointUrl(baseA + "/userinfo");
        configA.setIdTokenUri(baseA + "/certs");
        configA.setEnabled(true);
        configA.setAutoCreateUser(true);
        configA.setAllowBearerTokens(true);
        configA.setBeanName(PROVIDER_A_NAME + "OAuth2Config");
        configA.setScopes("openId,email");
        configA.setRedirectUri("../../../geostore/rest/users/user/details");

        // Configure provider B
        configB = new OpenIdConnectConfiguration();
        configB.setClientId(PROVIDER_B_CLIENT_ID);
        configB.setClientSecret(PROVIDER_B_CLIENT_SECRET);
        configB.setAccessTokenUri(baseB + "/token");
        configB.setAuthorizationUri(baseB + "/authorize");
        configB.setCheckTokenEndpointUrl(baseB + "/userinfo");
        configB.setIdTokenUri(baseB + "/certs");
        configB.setEnabled(true);
        configB.setAutoCreateUser(true);
        configB.setAllowBearerTokens(true);
        configB.setBeanName(PROVIDER_B_NAME + "OAuth2Config");
        configB.setScopes("openId,email");
        configB.setRedirectUri("../../../geostore/rest/users/user/details");

        // Build filters
        filterA = buildFilter(configA, baseA + "/certs");
        filterB = buildFilter(configB, baseB + "/certs");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testProviderAAcceptsOwnBearerToken() throws IOException, ServletException {
        String jwt =
                createSignedJwt(
                        "alice@provider-a.com",
                        "alice-sub",
                        PROVIDER_A_CLIENT_ID,
                        PROVIDER_A_KID,
                        rsaAlgorithmA);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterA.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Provider A should accept its own bearer token");
        User user = (User) auth.getPrincipal();
        assertEquals("alice@provider-a.com", user.getName());
    }

    @Test
    public void testProviderBAcceptsOwnBearerToken() throws IOException, ServletException {
        String jwt =
                createSignedJwt(
                        "bob@provider-b.com",
                        "bob-sub",
                        PROVIDER_B_CLIENT_ID,
                        PROVIDER_B_KID,
                        rsaAlgorithmB);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterB.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Provider B should accept its own bearer token");
        User user = (User) auth.getPrincipal();
        assertEquals("bob@provider-b.com", user.getName());
    }

    @Test
    public void testProviderARejectsProviderBToken() throws IOException, ServletException {
        // Token signed with B's key and B's audience — should be rejected by A
        String jwtForB =
                createSignedJwt(
                        "bob@provider-b.com",
                        "bob-sub",
                        PROVIDER_B_CLIENT_ID,
                        PROVIDER_B_KID,
                        rsaAlgorithmB);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwtForB);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Filter A should reject: wrong key (kid not in A's JWKS) and wrong audience
        try {
            filterA.doFilter(request, response, new MockFilterChain());
        } catch (Exception e) {
            // Expected: signature verification or audience validation failure
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "Provider A should reject a token signed for provider B");
    }

    @Test
    public void testProviderBRejectsProviderAToken() throws IOException, ServletException {
        // Token signed with A's key and A's audience — should be rejected by B
        String jwtForA =
                createSignedJwt(
                        "alice@provider-a.com",
                        "alice-sub",
                        PROVIDER_A_CLIENT_ID,
                        PROVIDER_A_KID,
                        rsaAlgorithmA);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwtForA);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filterB.doFilter(request, response, new MockFilterChain());
        } catch (Exception e) {
            // Expected: signature verification or audience validation failure
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "Provider B should reject a token signed for provider A");
    }

    @Test
    public void testCrossProviderAudienceIsolation() throws IOException, ServletException {
        // Token signed with A's key but with B's audience
        String jwt =
                createSignedJwt(
                        "alice@provider-a.com",
                        "alice-sub",
                        PROVIDER_B_CLIENT_ID,
                        PROVIDER_A_KID,
                        rsaAlgorithmA);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filterA.doFilter(request, response, new MockFilterChain());
        } catch (Exception e) {
            // Expected: audience validation failure
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "Provider A should reject a token with provider B's audience");
    }

    @Test
    public void testCompositeFilterRoutesBearerToCorrectProvider() throws Exception {
        // Set up a CompositeOpenIdConnectFilter with both providers
        CompositeOpenIdConnectFilter composite = new CompositeOpenIdConnectFilter();
        composite.setApplicationContext(
                createMockApplicationContext(
                        Map.of(
                                PROVIDER_A_NAME + "OAuth2Config", configA,
                                PROVIDER_B_NAME + "OAuth2Config", configB)));
        composite.afterPropertiesSet();

        // Verify both providers were initialized
        assertEquals(2, composite.getProviderFilters().size());
        assertTrue(composite.getProviderFilters().containsKey(PROVIDER_A_NAME));
        assertTrue(composite.getProviderFilters().containsKey(PROVIDER_B_NAME));

        // Send a bearer token valid for provider A
        String jwtA =
                createSignedJwt(
                        "alice@provider-a.com",
                        "alice-sub",
                        PROVIDER_A_CLIENT_ID,
                        PROVIDER_A_KID,
                        rsaAlgorithmA);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwtA);
        MockHttpServletResponse response = new MockHttpServletResponse();

        composite.doFilter(request, response, new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Composite filter should authenticate via provider A");
        User user = (User) auth.getPrincipal();
        assertEquals("alice@provider-a.com", user.getName());
    }

    @Test
    public void testCompositeFilterRoutesBearerToSecondProvider() throws Exception {
        CompositeOpenIdConnectFilter composite = new CompositeOpenIdConnectFilter();
        composite.setApplicationContext(
                createMockApplicationContext(
                        Map.of(
                                PROVIDER_A_NAME + "OAuth2Config", configA,
                                PROVIDER_B_NAME + "OAuth2Config", configB)));
        composite.afterPropertiesSet();

        // Send a bearer token valid only for provider B
        String jwtB =
                createSignedJwt(
                        "bob@provider-b.com",
                        "bob-sub",
                        PROVIDER_B_CLIENT_ID,
                        PROVIDER_B_KID,
                        rsaAlgorithmB);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwtB);
        MockHttpServletResponse response = new MockHttpServletResponse();

        composite.doFilter(request, response, new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Composite filter should authenticate via provider B");
        User user = (User) auth.getPrincipal();
        assertEquals("bob@provider-b.com", user.getName());
    }

    @Test
    public void testCompositeFilterSharesTokenCacheAcrossProviders() throws Exception {
        ApplicationContext ctx =
                createMockApplicationContext(
                        Map.of(
                                PROVIDER_A_NAME + "OAuth2Config", configA,
                                PROVIDER_B_NAME + "OAuth2Config", configB));
        CompositeOpenIdConnectFilter composite = new CompositeOpenIdConnectFilter();
        composite.setApplicationContext(ctx);
        composite.afterPropertiesSet();

        TokenAuthenticationCache sharedCache =
                ctx.getBean("oAuth2Cache", TokenAuthenticationCache.class);

        String jwtA =
                createSignedJwt(
                        "alice@provider-a.com",
                        "alice-sub",
                        PROVIDER_A_CLIENT_ID,
                        PROVIDER_A_KID,
                        rsaAlgorithmA);
        MockHttpServletRequest requestA = createRequest("rest/resources");
        requestA.addHeader("Authorization", "Bearer " + jwtA);
        composite.doFilter(requestA, new MockHttpServletResponse(), new MockFilterChain());

        SecurityContextHolder.clearContext();

        String jwtB =
                createSignedJwt(
                        "bob@provider-b.com",
                        "bob-sub",
                        PROVIDER_B_CLIENT_ID,
                        PROVIDER_B_KID,
                        rsaAlgorithmB);
        MockHttpServletRequest requestB = createRequest("rest/resources");
        requestB.addHeader("Authorization", "Bearer " + jwtB);
        composite.doFilter(requestB, new MockHttpServletResponse(), new MockFilterChain());

        Set<String> cachedUsernames = new HashSet<>();
        for (Authentication cached : sharedCache.getCache().asMap().values()) {
            cachedUsernames.add(((User) cached.getPrincipal()).getName());
        }
        assertTrue(
                cachedUsernames.contains("alice@provider-a.com"),
                "Provider A's authentication should be cached in the shared oAuth2Cache bean");
        assertTrue(
                cachedUsernames.contains("bob@provider-b.com"),
                "Provider B's authentication should be cached in the shared oAuth2Cache bean");
    }

    @Test
    public void testCompositeFilterRejectsInvalidBearerToken() throws Exception {
        CompositeOpenIdConnectFilter composite = new CompositeOpenIdConnectFilter();
        composite.setApplicationContext(
                createMockApplicationContext(
                        Map.of(
                                PROVIDER_A_NAME + "OAuth2Config", configA,
                                PROVIDER_B_NAME + "OAuth2Config", configB)));
        composite.afterPropertiesSet();

        // Send a token with unknown audience — neither provider should accept
        String badJwt =
                createSignedJwt(
                        "nobody@nowhere.com",
                        "bad-sub",
                        "unknown-client-id",
                        PROVIDER_A_KID,
                        rsaAlgorithmA);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + badJwt);
        MockHttpServletResponse response = new MockHttpServletResponse();

        composite.doFilter(request, response, new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "Composite filter should reject token not valid for any provider");
    }

    @Test
    public void testCompositeFilterVerifiesBearerWithJwksFromDiscovery() throws Exception {
        // Provider A configured the way a discovery-only deployment is: no idTokenUri, no jwkURI
        OpenIdConnectConfiguration discovered = discoveryOnlyConfig();
        CompositeOpenIdConnectFilter composite = new CompositeOpenIdConnectFilter();
        composite.setApplicationContext(
                createMockApplicationContext(Map.of(PROVIDER_A_NAME + "OAuth2Config", discovered)));
        composite.afterPropertiesSet();
        assertEquals(
                "http://localhost:" + wireMockA.port() + "/certs",
                discovered.getIdTokenUri(),
                "Discovery should have filled in the JWKS address");

        // Right audience and key id, but signed with a key that provider A does not publish
        String wrongKey =
                createSignedJwt(
                        "alice@provider-a.com",
                        "alice-sub",
                        PROVIDER_A_CLIENT_ID,
                        PROVIDER_A_KID,
                        rsaAlgorithmB);
        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + wrongKey);
        composite.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(
                SecurityContextHolder.getContext().getAuthentication(),
                "A token not signed by the provider's key must be refused");

        // The same token signed with provider A's key is accepted
        SecurityContextHolder.clearContext();
        String rightKey =
                createSignedJwt(
                        "alice@provider-a.com",
                        "alice-sub",
                        PROVIDER_A_CLIENT_ID,
                        PROVIDER_A_KID,
                        rsaAlgorithmA);
        request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + rightKey);
        composite.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "A token signed by the provider's key must be accepted");
        assertEquals("alice@provider-a.com", ((User) auth.getPrincipal()).getName());
    }

    @Test
    public void testCompositeFilterRefusesBearerWhenNoJwksAddressIsKnown() throws Exception {
        // Neither discovery nor an explicit JWKS address: signatures cannot be verified
        configA.setIdTokenUri(null);
        CompositeOpenIdConnectFilter composite = new CompositeOpenIdConnectFilter();
        composite.setApplicationContext(
                createMockApplicationContext(Map.of(PROVIDER_A_NAME + "OAuth2Config", configA)));
        composite.afterPropertiesSet();

        String jwt =
                createSignedJwt(
                        "alice@provider-a.com",
                        "alice-sub",
                        PROVIDER_A_CLIENT_ID,
                        PROVIDER_A_KID,
                        rsaAlgorithmA);
        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        composite.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(
                SecurityContextHolder.getContext().getAuthentication(),
                "A JWT bearer token must be refused when its signature cannot be verified");
    }

    @Test
    public void testCompositeFilterUsesJwkUriWhenIdTokenUriIsNotSet() throws Exception {
        configA.setIdTokenUri(null);
        configA.setJwkURI("http://localhost:" + wireMockA.port() + "/certs");
        CompositeOpenIdConnectFilter composite = new CompositeOpenIdConnectFilter();
        composite.setApplicationContext(
                createMockApplicationContext(Map.of(PROVIDER_A_NAME + "OAuth2Config", configA)));
        composite.afterPropertiesSet();

        String wrongKey =
                createSignedJwt(
                        "alice@provider-a.com",
                        "alice-sub",
                        PROVIDER_A_CLIENT_ID,
                        PROVIDER_A_KID,
                        rsaAlgorithmB);
        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + wrongKey);
        composite.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        SecurityContextHolder.clearContext();
        String rightKey =
                createSignedJwt(
                        "alice@provider-a.com",
                        "alice-sub",
                        PROVIDER_A_CLIENT_ID,
                        PROVIDER_A_KID,
                        rsaAlgorithmA);
        request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + rightKey);
        composite.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    public void testAutoStrategyFallsBackToIntrospectionWithoutJwksAddress() throws Exception {
        // With bearerTokenStrategy=auto the identity provider decides when no JWKS address is known
        configA.setIdTokenUri(null);
        configA.setBearerTokenStrategy("auto");
        configA.setIntrospectionEndpoint("http://localhost:" + wireMockA.port() + "/introspect");
        CompositeOpenIdConnectFilter composite = new CompositeOpenIdConnectFilter();
        composite.setApplicationContext(
                createMockApplicationContext(Map.of(PROVIDER_A_NAME + "OAuth2Config", configA)));
        composite.afterPropertiesSet();

        String jwt =
                createSignedJwt(
                        "alice@provider-a.com",
                        "alice-sub",
                        PROVIDER_A_CLIENT_ID,
                        PROVIDER_A_KID,
                        rsaAlgorithmA);
        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        composite.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "The token should be accepted through introspection");
        assertEquals("alice@provider-a.com", ((User) auth.getPrincipal()).getName());
        wireMockA.verify(postRequestedFor(urlPathEqualTo("/introspect")));
    }

    @Test
    public void testAutoStrategyRefusesTokenThatIntrospectionReportsInactive() throws Exception {
        configA.setIdTokenUri(null);
        configA.setBearerTokenStrategy("auto");
        configA.setIntrospectionEndpoint(
                "http://localhost:" + wireMockA.port() + "/introspect-inactive");
        CompositeOpenIdConnectFilter composite = new CompositeOpenIdConnectFilter();
        composite.setApplicationContext(
                createMockApplicationContext(Map.of(PROVIDER_A_NAME + "OAuth2Config", configA)));
        composite.afterPropertiesSet();

        String jwt =
                createSignedJwt(
                        "alice@provider-a.com",
                        "alice-sub",
                        PROVIDER_A_CLIENT_ID,
                        PROVIDER_A_KID,
                        rsaAlgorithmA);
        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        composite.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(
                SecurityContextHolder.getContext().getAuthentication(),
                "A token that introspection reports as inactive must be refused");
        wireMockA.verify(postRequestedFor(urlPathEqualTo("/introspect-inactive")));
    }

    @Test
    public void testExplicitIdTokenUriTakesPrecedenceOverDiscovery() throws Exception {
        // discoveryUrl advertises provider A's JWKS, while idTokenUri points to provider B's
        OpenIdConnectConfiguration config = discoveryOnlyConfig();
        config.setIdTokenUri("http://localhost:" + wireMockB.port() + "/certs");
        assertExplicitJwksAddressIsUsed(config);
    }

    @Test
    public void testExplicitJwkUriTakesPrecedenceOverDiscovery() throws Exception {
        // the same with jwkURI, which discovery does not fill in
        OpenIdConnectConfiguration config = discoveryOnlyConfig();
        config.setJwkURI("http://localhost:" + wireMockB.port() + "/certs");
        assertExplicitJwksAddressIsUsed(config);
    }

    /**
     * The given configuration has a discoveryUrl that advertises provider A's JWKS and an explicit
     * address for provider B's: tokens must be verified with the explicit one.
     */
    private void assertExplicitJwksAddressIsUsed(OpenIdConnectConfiguration config)
            throws Exception {
        CompositeOpenIdConnectFilter composite = new CompositeOpenIdConnectFilter();
        composite.setApplicationContext(
                createMockApplicationContext(Map.of(PROVIDER_A_NAME + "OAuth2Config", config)));
        composite.afterPropertiesSet();

        String signedWithExplicitKey =
                createSignedJwt(
                        "alice@provider-a.com",
                        "alice-sub",
                        PROVIDER_A_CLIENT_ID,
                        PROVIDER_B_KID,
                        rsaAlgorithmB);
        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + signedWithExplicitKey);
        composite.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNotNull(
                SecurityContextHolder.getContext().getAuthentication(),
                "A token signed with a key of the explicit JWKS address must be accepted");

        SecurityContextHolder.clearContext();
        String signedWithDiscoveredKey =
                createSignedJwt(
                        "alice@provider-a.com",
                        "alice-sub",
                        PROVIDER_A_CLIENT_ID,
                        PROVIDER_A_KID,
                        rsaAlgorithmA);
        request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + signedWithDiscoveredKey);
        composite.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(
                SecurityContextHolder.getContext().getAuthentication(),
                "A token signed with a key that only the discovered JWKS address has must be"
                        + " refused");
    }

    @Test
    public void testCompositeFilterSkipsDisabledProvider() throws Exception {
        // Disable provider B
        configB.setEnabled(false);

        CompositeOpenIdConnectFilter composite = new CompositeOpenIdConnectFilter();
        composite.setApplicationContext(
                createMockApplicationContext(
                        Map.of(
                                PROVIDER_A_NAME + "OAuth2Config", configA,
                                PROVIDER_B_NAME + "OAuth2Config", configB)));
        composite.afterPropertiesSet();

        // Only provider A should be initialized
        assertEquals(1, composite.getProviderFilters().size());
        assertTrue(composite.getProviderFilters().containsKey(PROVIDER_A_NAME));
        assertFalse(composite.getProviderFilters().containsKey(PROVIDER_B_NAME));

        // Re-enable for other tests
        configB.setEnabled(true);
    }

    @Test
    public void testCompositeFilterPassesThroughWithoutBearer() throws Exception {
        CompositeOpenIdConnectFilter composite = new CompositeOpenIdConnectFilter();
        composite.setApplicationContext(
                createMockApplicationContext(Map.of(PROVIDER_A_NAME + "OAuth2Config", configA)));
        composite.afterPropertiesSet();

        // Request with no bearer token and not a login/callback URL
        MockHttpServletRequest request = createRequest("rest/resources");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        composite.doFilter(request, response, chain);

        // Should have passed through to the chain (via the first provider's filter)
        // No authentication should be set since there was no token
        // The important thing is no exception was thrown
    }

    @Test
    public void testProviderSpecificRolesAndGroups() throws IOException, ServletException {
        configA.setRolesClaim("roles");
        configA.setGroupsClaim("groups");

        long now = System.currentTimeMillis() / 1000;
        String jwt =
                JWT.create()
                        .withKeyId(PROVIDER_A_KID)
                        .withIssuer("https://provider-a.example.com/")
                        .withAudience(PROVIDER_A_CLIENT_ID)
                        .withSubject("admin-sub")
                        .withClaim("email", "admin@provider-a.com")
                        .withArrayClaim("roles", new String[] {"ADMIN"})
                        .withArrayClaim("groups", new String[] {"team-alpha", "team-beta"})
                        .withIssuedAt(new Date(now * 1000))
                        .withExpiresAt(new Date((now + 3600) * 1000))
                        .sign(rsaAlgorithmA);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterA.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Should authenticate with roles and groups");
        User user = (User) auth.getPrincipal();
        assertEquals("admin@provider-a.com", user.getName());
        assertEquals(Role.ADMIN, user.getRole());
        assertNotNull(user.getGroups());
        Set<String> groupNames = new HashSet<>();
        user.getGroups().forEach(g -> groupNames.add(g.getGroupName()));
        assertTrue(groupNames.contains("team-alpha"));
        assertTrue(groupNames.contains("team-beta"));
    }

    // ---- Helper methods ----

    /**
     * Builds a per-provider OIDC filter on the Spring Security 7 service-based architecture:
     * OpenIdConnectRestClient (token exchange/refresh) + OpenIdConnectAuthenticationService (built
     * with null user/group services so the principal is materialized from token claims) + a thin
     * OpenIdConnectFilter. Replaces the legacy 6-arg constructor / GeoStoreOAuthRestTemplate.
     */
    private OpenIdConnectFilter buildFilter(OpenIdConnectConfiguration config, String jwksUri) {
        OpenIdConnectRestClient restClient = new OpenIdConnectRestClient(config);
        JwksRsaKeyProvider jwksKeyProvider = new JwksRsaKeyProvider(jwksUri, config);
        TokenAuthenticationCache cache =
                new TokenAuthenticationCache(
                        config.getCacheSize(), config.getCacheExpirationMinutes());
        MultiTokenValidator validator =
                new MultiTokenValidator(
                        Arrays.asList(
                                new AudienceAccessTokenValidator(), new SubjectTokenValidator()));
        OpenIdConnectAuthenticationService service =
                new OpenIdConnectAuthenticationService(
                        cache, null, null, config, validator, jwksKeyProvider);
        return new OpenIdConnectFilter(config, service, restClient);
    }

    /** Provider A configured by discoveryUrl only, as in a discovery-only deployment. */
    private OpenIdConnectConfiguration discoveryOnlyConfig() {
        OpenIdConnectConfiguration config = new OpenIdConnectConfiguration();
        config.setClientId(PROVIDER_A_CLIENT_ID);
        config.setClientSecret(PROVIDER_A_CLIENT_SECRET);
        config.setDiscoveryUrl("http://localhost:" + wireMockA.port());
        config.setEnabled(true);
        config.setAutoCreateUser(true);
        config.setAllowBearerTokens(true);
        config.setBeanName(PROVIDER_A_NAME + "OAuth2Config");
        config.setScopes("openId,email");
        config.setRedirectUri("../../../geostore/rest/users/user/details");
        return config;
    }

    private String createSignedJwt(
            String email, String sub, String aud, String kid, Algorithm algorithm) {
        long now = System.currentTimeMillis() / 1000;
        return JWT.create()
                .withKeyId(kid)
                .withIssuer("https://test.issuer/")
                .withAudience(aud)
                .withSubject(sub)
                .withClaim("email", email)
                .withIssuedAt(new Date(now * 1000))
                .withExpiresAt(new Date((now + 3600) * 1000))
                .sign(algorithm);
    }

    private static String buildJwksJson(RSAPublicKey publicKey, String kid) {
        java.util.Base64.Encoder encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        String n = encoder.encodeToString(publicKey.getModulus().toByteArray());
        String e = encoder.encodeToString(publicKey.getPublicExponent().toByteArray());
        return "{\"keys\":[{"
                + "\"kty\":\"RSA\","
                + "\"use\":\"sig\","
                + "\"alg\":\"RS256\","
                + "\"kid\":\""
                + kid
                + "\","
                + "\"n\":\""
                + n
                + "\","
                + "\"e\":\""
                + e
                + "\""
                + "}]}";
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

    /**
     * Creates a StaticApplicationContext pre-loaded with the given OpenIdConnectConfiguration
     * beans.
     */
    private ApplicationContext createMockApplicationContext(
            Map<String, OpenIdConnectConfiguration> configs) {
        StaticApplicationContext ctx = new StaticApplicationContext();
        for (Map.Entry<String, OpenIdConnectConfiguration> e : configs.entrySet()) {
            ctx.getBeanFactory().registerSingleton(e.getKey(), e.getValue());
        }
        ctx.refresh();
        return ctx;
    }
}
