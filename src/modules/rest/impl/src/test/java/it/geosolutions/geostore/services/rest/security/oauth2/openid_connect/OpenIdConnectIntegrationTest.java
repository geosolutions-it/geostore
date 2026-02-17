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
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreOAuthRestTemplate;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.JwksRsaKeyProvider;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.DefaultOAuth2ClientContext;
import org.springframework.security.oauth2.client.token.DefaultAccessTokenRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class OpenIdConnectIntegrationTest {

    private static final String CLIENT_ID = "kbyuFDidLLm280LIwVFiazOqjO3ty8KH";
    private static final String CLIENT_SECRET =
            "60Op4HFM0I8ajz0WdiStAbziZ-VFQttXuxixHHs2R7r7-CW8GR79l-mmLqMhc-Sa";
    private static final String CODE = "R-2CqM7H1agwc7Cx";
    private static final String TEST_KID = "test-key-1";

    private static WireMockServer openIdConnectService;
    private static RSAPublicKey rsaPublicKey;
    private static RSAPrivateKey rsaPrivateKey;
    private static Algorithm rsaAlgorithm;

    private String authService;
    private OpenIdConnectFilter filter;
    private OpenIdConnectConfiguration configuration;

    @BeforeAll
    static void beforeClass() throws Exception {
        // Generate RSA key pair for signing test JWTs
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        rsaPublicKey = (RSAPublicKey) keyPair.getPublic();
        rsaPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
        rsaAlgorithm = Algorithm.RSA256(rsaPublicKey, rsaPrivateKey);

        // Build JWKS JSON from the generated public key
        String jwksJson = buildJwksJson(rsaPublicKey, TEST_KID);

        // Build a properly signed id_token matching the userinfo.json test data.
        // GeoStoreOAuthRestTemplate.validate() verifies the id_token signature against
        // the JWKS endpoint, so this must be signed with our test key.
        long now = System.currentTimeMillis() / 1000;
        String idToken =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("100301874944276879963462152")
                        .withClaim("email", "ritter@erdukunde.de")
                        .withClaim("email_verified", true)
                        .withClaim("name", "Karl Ritter")
                        .withClaim("hd", "geosolutionsgroup.com")
                        .withIssuedAt(new Date(now * 1000))
                        .withExpiresAt(new Date((now + 86400) * 1000))
                        .sign(rsaAlgorithm);

        String tokenResponseJson =
                "{\"access_token\":\"CPURR33RUz-gGhjwODTd9zXo5JkQx4wS\","
                        + "\"id_token\":\""
                        + idToken
                        + "\","
                        + "\"scope\":\"openid profile email phone address\","
                        + "\"expires_in\":86400,"
                        + "\"token_type\":\"Bearer\"}";

        openIdConnectService =
                new WireMockServer(
                        wireMockConfig()
                                .dynamicPort()
                                // uncomment the following to get wiremock logging
                                .notifier(new ConsoleNotifier(true)));
        openIdConnectService.start();

        openIdConnectService.stubFor(
                WireMock.get(urlEqualTo("/certs"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(jwksJson)));

        openIdConnectService.stubFor(
                WireMock.post(urlPathEqualTo("/token"))
                        .withRequestBody(containing("grant_type=authorization_code"))
                        .withRequestBody(containing("client_id=" + CLIENT_ID))
                        .withRequestBody(containing("code=" + CODE))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(tokenResponseJson)));
        openIdConnectService.stubFor(
                any(urlPathEqualTo("/userinfo"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBodyFile(
                                                "userinfo.json"))); // disallow query parameters
    }

    @BeforeEach
    void before() {
        // prepare mock server base path
        authService = "http://localhost:" + openIdConnectService.port();
        OpenIdConnectConfiguration configuration = new OpenIdConnectConfiguration();
        configuration.setClientId(CLIENT_ID);
        configuration.setClientSecret(CLIENT_SECRET);
        configuration.setRevokeEndpoint(authService + "/revoke");
        configuration.setAccessTokenUri(authService + "/token");
        configuration.setAuthorizationUri(authService + "/authorize");
        configuration.setCheckTokenEndpointUrl(authService + "/userinfo");
        configuration.setEnabled(true);
        configuration.setAutoCreateUser(true);
        configuration.setIdTokenUri(authService + "/certs");
        configuration.setBeanName("oidcOAuth2Config");
        configuration.setEnableRedirectEntryPoint(true);
        configuration.setRedirectUri("../../../geostore/rest/users/user/details");
        configuration.setScopes("openId,email");
        configuration.setSendClientSecret(true);
        this.configuration = configuration;
        OpenIdConnectSecurityConfiguration securityConfiguration =
                new OpenIdConnectSecurityConfiguration() {

                    @Override
                    protected GeoStoreOAuthRestTemplate restTemplate() {
                        return new GeoStoreOAuthRestTemplate(
                                resourceDetails(),
                                new DefaultOAuth2ClientContext(new DefaultAccessTokenRequest()),
                                configuration());
                    }

                    @Override
                    public OAuth2Configuration configuration() {
                        return configuration;
                    }
                };
        GeoStoreOAuthRestTemplate restTemplate = securityConfiguration.oauth2RestTemplate();
        JwksRsaKeyProvider jwksKeyProvider = new JwksRsaKeyProvider(authService + "/certs");
        this.filter =
                new OpenIdConnectFilter(
                        securityConfiguration.oidcTokenServices(),
                        restTemplate,
                        configuration,
                        securityConfiguration.oAuth2Cache(),
                        securityConfiguration.openIdConnectBearerTokenValidator(),
                        jwksKeyProvider);
    }

    @AfterEach
    void afterTest() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testRedirect() throws IOException, ServletException {
        MockHttpServletRequest request = createRequest("oidc/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(302, response.getStatus());
        assertEquals(response.getRedirectedUrl(), configuration.buildLoginUri());
    }

    @Test
    public void testAuthentication() throws IOException, ServletException {
        MockHttpServletRequest request = createRequest("oidc/login");
        request.setParameter("authorization_code", CODE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.restTemplate
                .getOAuth2ClientContext()
                .getAccessTokenRequest()
                .setAuthorizationCode(CODE);
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();
        assertEquals("ritter@erdukunde.de", user.getName());
        assertEquals(Role.USER, user.getRole());
    }

    @Test
    public void testGroupsAndRolesFromToken() throws IOException, ServletException {
        configuration.setGroupsClaim("hd");
        MockHttpServletRequest request = createRequest("oidc/login");
        request.setParameter("authorization_code", CODE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.restTemplate
                .getOAuth2ClientContext()
                .getAccessTokenRequest()
                .setAuthorizationCode(CODE);
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();
        assertEquals("ritter@erdukunde.de", user.getName());
        assertEquals(Role.USER, user.getRole());
        UserGroup group = user.getGroups().stream().findAny().get();
        assertEquals("geosolutionsgroup.com", group.getGroupName());
    }

    @Test
    public void testBearerTokenAuthentication() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);

        // Build a properly signed JWT bearer token
        String jwt = createSignedJwt("ritter@erdukunde.de", "test-sub-123", CLIENT_ID);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication, "Bearer token should produce an authenticated user");
        User user = (User) authentication.getPrincipal();
        assertEquals("ritter@erdukunde.de", user.getName());
        assertEquals(Role.USER, user.getRole());
    }

    @Test
    public void testBearerTokenDisallowed() throws IOException, ServletException {
        configuration.setAllowBearerTokens(false);

        String jwt = createSignedJwt("ritter@erdukunde.de", "test-sub-123", CLIENT_ID);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(
                authentication,
                "Bearer token should not authenticate when bearer tokens are disallowed");
    }

    @Test
    public void testBearerTokenWithPrincipalKey() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        configuration.setPrincipalKey("preferred_username");

        long now = System.currentTimeMillis() / 1000;
        String jwt =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("test-sub-456")
                        .withClaim("preferred_username", "karl.ritter")
                        .withClaim("email", "ritter@erdukunde.de")
                        .withIssuedAt(new Date(now * 1000))
                        .withExpiresAt(new Date((now + 3600) * 1000))
                        .sign(rsaAlgorithm);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        User user = (User) authentication.getPrincipal();
        // principalKey is "preferred_username", so it should use that claim
        assertEquals("karl.ritter", user.getName());
    }

    @Test
    public void testBearerTokenRolesAndGroups() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        configuration.setRolesClaim("roles");
        configuration.setGroupsClaim("groups");

        long now = System.currentTimeMillis() / 1000;
        String jwt =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("test-sub-789")
                        .withClaim("email", "admin@example.com")
                        .withArrayClaim("roles", new String[] {"ADMIN"})
                        .withArrayClaim("groups", new String[] {"analysts", "editors"})
                        .withIssuedAt(new Date(now * 1000))
                        .withExpiresAt(new Date((now + 3600) * 1000))
                        .sign(rsaAlgorithm);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication, "Bearer token with roles/groups should authenticate");
        User user = (User) authentication.getPrincipal();
        assertEquals("admin@example.com", user.getName());
        assertEquals(Role.ADMIN, user.getRole());

        // Verify groups were extracted from the bearer token claims
        assertNotNull(user.getGroups(), "Groups should be populated from bearer token");
        Set<String> groupNames =
                user.getGroups().stream().map(UserGroup::getGroupName).collect(Collectors.toSet());
        assertTrue(groupNames.contains("analysts"), "Should have 'analysts' group");
        assertTrue(groupNames.contains("editors"), "Should have 'editors' group");
    }

    @Test
    public void testBearerTokenExpired() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);

        long now = System.currentTimeMillis() / 1000;
        String jwt =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("test-sub-expired")
                        .withClaim("email", "expired@example.com")
                        .withIssuedAt(new Date((now - 7200) * 1000))
                        .withExpiresAt(new Date((now - 3600) * 1000))
                        .sign(rsaAlgorithm);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication, "Expired bearer token should not authenticate");
    }

    @Test
    public void testBearerTokenWrongAudience() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);

        // JWT signed with correct key but wrong audience
        String jwt = createSignedJwt("ritter@erdukunde.de", "test-sub-aud", "wrong-client-id");

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication, "Bearer token with wrong audience should not authenticate");
    }

    @Test
    public void testBearerTokenMalformed() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer not-a-valid-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication, "Malformed bearer token should not authenticate");
    }

    @Test
    public void testBearerTokenBadSignature() throws Exception {
        configuration.setAllowBearerTokens(true);

        // Generate a different RSA key pair and sign with it
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair wrongKeyPair = keyGen.generateKeyPair();
        Algorithm wrongAlgorithm =
                Algorithm.RSA256(
                        (RSAPublicKey) wrongKeyPair.getPublic(),
                        (RSAPrivateKey) wrongKeyPair.getPrivate());

        long now = System.currentTimeMillis() / 1000;
        String jwt =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("test-sub-bad-sig")
                        .withClaim("email", "ritter@erdukunde.de")
                        .withIssuedAt(new Date(now * 1000))
                        .withExpiresAt(new Date((now + 3600) * 1000))
                        .sign(wrongAlgorithm);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication, "Bearer token signed with wrong key should not authenticate");
    }

    @Test
    public void testRoleMappings() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        configuration.setRolesClaim("roles");
        configuration.setRoleMappings("idp_admin:ADMIN,idp_viewer:GUEST");

        long now = System.currentTimeMillis() / 1000;
        String jwt =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("test-sub-role-map")
                        .withClaim("email", "mapped@example.com")
                        .withArrayClaim("roles", new String[] {"idp_admin"})
                        .withIssuedAt(new Date(now * 1000))
                        .withExpiresAt(new Date((now + 3600) * 1000))
                        .sign(rsaAlgorithm);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication, "Role-mapped bearer token should authenticate");
        User user = (User) authentication.getPrincipal();
        assertEquals(Role.ADMIN, user.getRole(), "idp_admin should map to ADMIN");
    }

    @Test
    public void testGroupMappings() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        configuration.setGroupsClaim("groups");
        configuration.setGroupMappings("devs:developers,ops:operations");

        long now = System.currentTimeMillis() / 1000;
        String jwt =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("test-sub-group-map")
                        .withClaim("email", "groupmap@example.com")
                        .withArrayClaim("groups", new String[] {"devs", "ops"})
                        .withIssuedAt(new Date(now * 1000))
                        .withExpiresAt(new Date((now + 3600) * 1000))
                        .sign(rsaAlgorithm);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        User user = (User) authentication.getPrincipal();
        Set<String> groupNames =
                user.getGroups().stream().map(UserGroup::getGroupName).collect(Collectors.toSet());
        assertTrue(groupNames.contains("developers"), "devs should map to developers");
        assertTrue(groupNames.contains("operations"), "ops should map to operations");
    }

    @Test
    public void testDropUnmapped() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        configuration.setGroupsClaim("groups");
        configuration.setGroupMappings("devs:developers");
        configuration.setDropUnmapped(true);

        long now = System.currentTimeMillis() / 1000;
        String jwt =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("test-sub-drop")
                        .withClaim("email", "drop@example.com")
                        .withArrayClaim("groups", new String[] {"devs", "unmapped_group"})
                        .withIssuedAt(new Date(now * 1000))
                        .withExpiresAt(new Date((now + 3600) * 1000))
                        .sign(rsaAlgorithm);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        User user = (User) authentication.getPrincipal();
        Set<String> groupNames =
                user.getGroups().stream().map(UserGroup::getGroupName).collect(Collectors.toSet());
        assertTrue(groupNames.contains("developers"), "devs should map to developers");
        assertFalse(
                groupNames.contains("unmapped_group"),
                "unmapped_group should be dropped when dropUnmapped=true");
    }

    @Test
    public void testPkceAuthorizationUrl() {
        configuration.setUsePKCE(true);

        MockHttpServletRequest request = createRequest("oidc/login");
        request.getSession(true); // ensure session exists
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertDoesNotThrow(
                () ->
                        configuration
                                .getAuthenticationEntryPoint()
                                .commence(request, response, null));

        assertEquals(302, response.getStatus());
        String redirectUrl = response.getRedirectedUrl();
        assertNotNull(redirectUrl, "Redirect URL should not be null");
        assertTrue(
                redirectUrl.contains("code_challenge="),
                "PKCE redirect URL should contain code_challenge");
        assertTrue(
                redirectUrl.contains("code_challenge_method=S256"),
                "PKCE redirect URL should contain code_challenge_method=S256");

        // Verify code_verifier was stored in session
        String verifier =
                (String)
                        request.getSession()
                                .getAttribute(
                                        OpenIdConnectConfiguration.PKCE_CODE_VERIFIER_SESSION_ATTR);
        assertNotNull(verifier, "PKCE code_verifier should be stored in session");
        assertFalse(verifier.isEmpty(), "PKCE code_verifier should not be empty");
    }

    @Test
    public void testArrayPrincipalClaim() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        configuration.setPrincipalKey("emails");

        long now = System.currentTimeMillis() / 1000;
        String jwt =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("test-sub-array")
                        .withArrayClaim(
                                "emails", new String[] {"first@example.com", "second@example.com"})
                        .withIssuedAt(new Date(now * 1000))
                        .withExpiresAt(new Date((now + 3600) * 1000))
                        .sign(rsaAlgorithm);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication, "Array claim bearer token should authenticate");
        User user = (User) authentication.getPrincipal();
        assertEquals(
                "first@example.com",
                user.getName(),
                "Should use first element of array-type principal claim");
    }

    @Test
    public void testIatValidation() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        configuration.setMaxTokenAgeSecs(60); // max 60 seconds old

        long now = System.currentTimeMillis() / 1000;
        // Token issued 2 hours ago
        String jwt =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("test-sub-iat")
                        .withClaim("email", "old@example.com")
                        .withIssuedAt(new Date((now - 7200) * 1000))
                        .withExpiresAt(new Date((now + 3600) * 1000))
                        .sign(rsaAlgorithm);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(
                authentication,
                "Token with old iat should be rejected when maxTokenAgeSecs is set");
    }

    @Test
    public void testCacheConfiguration() {
        // Sanity check: parameterized constructor works and doesn't throw
        TokenAuthenticationCache cache = new TokenAuthenticationCache(500, 120);
        assertNotNull(cache);
        assertNull(cache.get("non-existent-token"));

        // Default constructor
        TokenAuthenticationCache defaultCache = new TokenAuthenticationCache();
        assertNotNull(defaultCache);
    }

    /** Creates a properly RSA-signed JWT with email, sub, and aud claims. */
    private String createSignedJwt(String email, String sub, String aud) {
        long now = System.currentTimeMillis() / 1000;
        return JWT.create()
                .withKeyId(TEST_KID)
                .withIssuer("https://test.issuer/")
                .withAudience(aud)
                .withSubject(sub)
                .withClaim("email", email)
                .withIssuedAt(new Date(now * 1000))
                .withExpiresAt(new Date((now + 3600) * 1000))
                .sign(rsaAlgorithm);
    }

    /** Builds a JWKS JSON string from the given RSA public key with the specified kid. */
    private static String buildJwksJson(RSAPublicKey publicKey, String kid) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
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
}
