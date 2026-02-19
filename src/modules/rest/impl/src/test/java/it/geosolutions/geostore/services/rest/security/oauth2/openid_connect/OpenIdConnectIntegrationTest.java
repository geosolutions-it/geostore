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
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreOAuthRestTemplate;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.AudienceAccessTokenValidator;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.JwksRsaKeyProvider;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.MultiTokenValidator;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.SubjectTokenValidator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
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
        GeoStoreOAuthRestTemplate restTemplate =
                OpenIdConnectRestTemplateFactory.create(
                        configuration, new DefaultAccessTokenRequest());
        JwksRsaKeyProvider jwksKeyProvider = new JwksRsaKeyProvider(authService + "/certs");
        OpenIdConnectTokenServices tokenServices =
                new OpenIdConnectTokenServices(configuration.getPrincipalKey());
        TokenAuthenticationCache cache =
                new TokenAuthenticationCache(
                        configuration.getCacheSize(), configuration.getCacheExpirationMinutes());
        MultiTokenValidator validator =
                new MultiTokenValidator(
                        java.util.Arrays.asList(
                                new AudienceAccessTokenValidator(), new SubjectTokenValidator()));
        this.filter =
                new OpenIdConnectFilter(
                        tokenServices,
                        restTemplate,
                        configuration,
                        cache,
                        validator,
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

    @Test
    public void testBearerTokenIntrospection() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        configuration.setBearerTokenStrategy("introspection");
        configuration.setIntrospectionEndpoint(authService + "/introspect");

        // Stub the introspection endpoint to return active=true with claims
        openIdConnectService.stubFor(
                WireMock.post(urlPathEqualTo("/introspect"))
                        .withRequestBody(containing("token=opaque-test-token-12345"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(
                                                "{\"active\":true,\"sub\":\"test-sub-opaque\","
                                                        + "\"email\":\"opaque@example.com\","
                                                        + "\"preferred_username\":\"opaque_user\"}")));

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer opaque-test-token-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication, "Opaque bearer token should authenticate via introspection");
        User user = (User) authentication.getPrincipal();
        assertEquals("opaque@example.com", user.getName());
    }

    @Test
    public void testBearerTokenIntrospectionInactive() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        configuration.setBearerTokenStrategy("introspection");
        configuration.setIntrospectionEndpoint(authService + "/introspect");

        // Stub the introspection endpoint to return active=false
        openIdConnectService.stubFor(
                WireMock.post(urlPathEqualTo("/introspect"))
                        .withRequestBody(containing("token=revoked-token-99999"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody("{\"active\":false}")));

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer revoked-token-99999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication, "Inactive token should not authenticate via introspection");
    }

    @Test
    public void testBearerTokenAutoStrategyJwtSuccess() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        configuration.setBearerTokenStrategy("auto");
        configuration.setIntrospectionEndpoint(authService + "/introspect");

        // Build a valid signed JWT — auto strategy should succeed with JWT, no introspection needed
        String jwt = createSignedJwt("auto-jwt@example.com", "test-sub-auto", CLIENT_ID);

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication, "Auto strategy should authenticate valid JWT");
        User user = (User) authentication.getPrincipal();
        assertEquals("auto-jwt@example.com", user.getName());
    }

    @Test
    public void testBearerTokenAutoStrategyFallbackToIntrospection()
            throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        configuration.setBearerTokenStrategy("auto");
        configuration.setIntrospectionEndpoint(authService + "/introspect");

        // Stub the introspection endpoint for the opaque token fallback
        openIdConnectService.stubFor(
                WireMock.post(urlPathEqualTo("/introspect"))
                        .withRequestBody(containing("token=not-a-jwt-opaque-token"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(
                                                "{\"active\":true,\"sub\":\"test-sub-fallback\","
                                                        + "\"email\":\"fallback@example.com\"}")));

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer not-a-jwt-opaque-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(
                authentication,
                "Auto strategy should fall back to introspection for non-JWT tokens");
        User user = (User) authentication.getPrincipal();
        assertEquals("fallback@example.com", user.getName());
    }

    @Test
    public void testNestedClaimRoles() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        // Keycloak-style nested claim path
        configuration.setRolesClaim("realm_access.roles");

        long now = System.currentTimeMillis() / 1000;
        // Build JWT with nested realm_access.roles claim (Keycloak format)
        String jwt =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("test-sub-nested")
                        .withClaim("email", "nested@example.com")
                        .withClaim(
                                "realm_access",
                                java.util.Collections.singletonMap(
                                        "roles", java.util.Arrays.asList("ADMIN", "user")))
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
        assertNotNull(authentication, "Nested claim path should resolve roles");
        User user = (User) authentication.getPrincipal();
        assertEquals("nested@example.com", user.getName());
        assertEquals(
                Role.ADMIN,
                user.getRole(),
                "realm_access.roles containing ADMIN should resolve to ADMIN role");
    }

    @Test
    public void testNestedClaimGroups() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        configuration.setGroupsClaim("resource_access.geostore.groups");

        long now = System.currentTimeMillis() / 1000;
        // Build JWT with deeply nested groups claim
        String jwt =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("test-sub-nested-groups")
                        .withClaim("email", "nested-groups@example.com")
                        .withClaim(
                                "resource_access",
                                java.util.Collections.singletonMap(
                                        "geostore",
                                        java.util.Collections.singletonMap(
                                                "groups",
                                                java.util.Arrays.asList("analysts", "editors"))))
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
        assertNotNull(authentication, "Nested claim path should resolve groups");
        User user = (User) authentication.getPrincipal();
        assertEquals("nested-groups@example.com", user.getName());
        Set<String> groupNames =
                user.getGroups().stream().map(UserGroup::getGroupName).collect(Collectors.toSet());
        assertTrue(groupNames.contains("analysts"), "Should have 'analysts' from nested path");
        assertTrue(groupNames.contains("editors"), "Should have 'editors' from nested path");
    }

    @Test
    public void testJsonPathRoles() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        // Explicit JsonPath expression for roles
        configuration.setRolesClaim("$.realm_access.roles");

        long now = System.currentTimeMillis() / 1000;
        String jwt =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("test-sub-jsonpath")
                        .withClaim("email", "jsonpath@example.com")
                        .withClaim(
                                "realm_access",
                                java.util.Collections.singletonMap(
                                        "roles", java.util.Arrays.asList("ADMIN", "user")))
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
        assertNotNull(authentication, "JsonPath roles expression should resolve");
        User user = (User) authentication.getPrincipal();
        assertEquals("jsonpath@example.com", user.getName());
        assertEquals(
                Role.ADMIN,
                user.getRole(),
                "$.realm_access.roles containing ADMIN should resolve to ADMIN role");
    }

    @Test
    public void testJsonPathGroupsWithWildcard() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        // Wildcard JsonPath for groups across all resource_access entries
        configuration.setGroupsClaim("$.resource_access.*.groups");

        long now = System.currentTimeMillis() / 1000;
        java.util.Map<String, Object> resourceAccess = new java.util.LinkedHashMap<>();
        resourceAccess.put(
                "app1",
                java.util.Collections.singletonMap("groups", java.util.Arrays.asList("analysts")));
        resourceAccess.put(
                "app2",
                java.util.Collections.singletonMap("groups", java.util.Arrays.asList("editors")));

        String jwt =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("test-sub-wildcard")
                        .withClaim("email", "wildcard@example.com")
                        .withClaim("resource_access", resourceAccess)
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
        assertNotNull(authentication, "Wildcard JsonPath should resolve groups");
        User user = (User) authentication.getPrincipal();
        Set<String> groupNames =
                user.getGroups().stream().map(UserGroup::getGroupName).collect(Collectors.toSet());
        assertTrue(groupNames.contains("analysts"), "Should have 'analysts' from wildcard path");
        assertTrue(groupNames.contains("editors"), "Should have 'editors' from wildcard path");
    }

    @Test
    public void testJsonPathArrayIndex() throws IOException, ServletException {
        configuration.setAllowBearerTokens(true);
        // Array index JsonPath to get the first role
        configuration.setRolesClaim("$.roles[0]");

        long now = System.currentTimeMillis() / 1000;
        String jwt =
                JWT.create()
                        .withKeyId(TEST_KID)
                        .withIssuer("https://test.issuer/")
                        .withAudience(CLIENT_ID)
                        .withSubject("test-sub-index")
                        .withClaim("email", "index@example.com")
                        .withArrayClaim("roles", new String[] {"ADMIN", "USER"})
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
        assertNotNull(authentication, "Array index JsonPath should resolve");
        User user = (User) authentication.getPrincipal();
        assertEquals("index@example.com", user.getName());
        assertEquals(
                Role.ADMIN, user.getRole(), "$.roles[0] should extract ADMIN as first element");
    }

    @Test
    public void testBearerTokenJweRsaOaep() throws Exception {
        configuration.setAllowBearerTokens(true);

        // Create a PKCS12 keystore with the RSA key pair for JWE decryption
        File keystoreFile = createTestKeystore();
        try {
            configuration.setJweKeyStoreFile(keystoreFile.getAbsolutePath());
            configuration.setJweKeyStorePassword("changeit");
            configuration.setJweKeyStoreType("PKCS12");
            configuration.setJweKeyAlias("jwekey");
            configuration.setJweKeyPassword("changeit");

            // Recreate filter so it picks up the JWE config
            recreateFilter();

            // Create a signed JWT, then encrypt it inside a JWE
            String jweToken =
                    createSignedJweToken("jwe-user@example.com", "test-sub-jwe", CLIENT_ID);

            MockHttpServletRequest request = createRequest("rest/resources");
            request.addHeader("Authorization", "Bearer " + jweToken);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus());
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(authentication, "JWE bearer token should authenticate successfully");
            User user = (User) authentication.getPrincipal();
            assertEquals("jwe-user@example.com", user.getName());
        } finally {
            keystoreFile.delete();
        }
    }

    @Test
    public void testBearerTokenJweWithRolesAndGroups() throws Exception {
        configuration.setAllowBearerTokens(true);
        configuration.setRolesClaim("roles");
        configuration.setGroupsClaim("groups");

        File keystoreFile = createTestKeystore();
        try {
            configuration.setJweKeyStoreFile(keystoreFile.getAbsolutePath());
            configuration.setJweKeyStorePassword("changeit");
            configuration.setJweKeyStoreType("PKCS12");
            configuration.setJweKeyAlias("jwekey");
            configuration.setJweKeyPassword("changeit");

            recreateFilter();

            // Create JWE with roles and groups in the inner JWT
            long now = System.currentTimeMillis() / 1000;
            JWTClaimsSet innerClaims =
                    new JWTClaimsSet.Builder()
                            .subject("test-sub-jwe-roles")
                            .issuer("https://test.issuer/")
                            .audience(CLIENT_ID)
                            .claim("email", "jwe-admin@example.com")
                            .claim("roles", java.util.Arrays.asList("ADMIN"))
                            .claim("groups", java.util.Arrays.asList("analysts", "editors"))
                            .issueTime(new Date(now * 1000))
                            .expirationTime(new Date((now + 3600) * 1000))
                            .build();

            SignedJWT signedJWT =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TEST_KID).build(),
                            innerClaims);
            signedJWT.sign(new RSASSASigner(rsaPrivateKey));

            JWEHeader jweHeader =
                    new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM)
                            .contentType("JWT")
                            .build();
            JWEObject jweObject = new JWEObject(jweHeader, new Payload(signedJWT));
            jweObject.encrypt(new RSAEncrypter(rsaPublicKey));
            String jweToken = jweObject.serialize();

            MockHttpServletRequest request = createRequest("rest/resources");
            request.addHeader("Authorization", "Bearer " + jweToken);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus());
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(authentication, "JWE token with roles/groups should authenticate");
            User user = (User) authentication.getPrincipal();
            assertEquals("jwe-admin@example.com", user.getName());
            assertEquals(Role.ADMIN, user.getRole());

            Set<String> groupNames =
                    user.getGroups().stream()
                            .map(UserGroup::getGroupName)
                            .collect(Collectors.toSet());
            assertTrue(groupNames.contains("analysts"), "Should have 'analysts' group from JWE");
            assertTrue(groupNames.contains("editors"), "Should have 'editors' group from JWE");
        } finally {
            keystoreFile.delete();
        }
    }

    @Test
    public void testBearerTokenJweFallsBackToJws() throws Exception {
        configuration.setAllowBearerTokens(true);

        // Configure JWE keystore — but send a plain JWS token
        File keystoreFile = createTestKeystore();
        try {
            configuration.setJweKeyStoreFile(keystoreFile.getAbsolutePath());
            configuration.setJweKeyStorePassword("changeit");
            configuration.setJweKeyStoreType("PKCS12");
            configuration.setJweKeyAlias("jwekey");
            configuration.setJweKeyPassword("changeit");

            recreateFilter();

            // Send a plain JWS token (3 parts) — should bypass JWE decryption and work normally
            String jwt = createSignedJwt("jws-fallback@example.com", "test-sub-jws", CLIENT_ID);

            MockHttpServletRequest request = createRequest("rest/resources");
            request.addHeader("Authorization", "Bearer " + jwt);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus());
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(
                    authentication, "Plain JWS token should still work when JWE is configured");
            User user = (User) authentication.getPrincipal();
            assertEquals("jws-fallback@example.com", user.getName());
        } finally {
            keystoreFile.delete();
        }
    }

    @Test
    public void testBearerTokenJweNotConfigured() throws Exception {
        configuration.setAllowBearerTokens(true);
        // No JWE keystore configured — JWE tokens should fail validation
        // (they look like malformed JWTs to the JWS pipeline)

        // Create a JWE token
        long now = System.currentTimeMillis() / 1000;
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .subject("test-sub-jwe-noconfig")
                        .issuer("https://test.issuer/")
                        .audience(CLIENT_ID)
                        .claim("email", "jwe-noconfig@example.com")
                        .issueTime(new Date(now * 1000))
                        .expirationTime(new Date((now + 3600) * 1000))
                        .build();

        JWEHeader header =
                new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM).build();
        EncryptedJWT encryptedJWT = new EncryptedJWT(header, claims);
        encryptedJWT.encrypt(new RSAEncrypter(rsaPublicKey));
        String jweToken = encryptedJWT.serialize();

        MockHttpServletRequest request = createRequest("rest/resources");
        request.addHeader("Authorization", "Bearer " + jweToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(
                authentication,
                "JWE token should not authenticate when no JWE keystore is configured");
    }

    /**
     * Creates a PKCS12 keystore file containing the test RSA key pair for JWE decryption testing.
     */
    private File createTestKeystore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, "changeit".toCharArray());

        // Create a self-signed certificate for the key pair
        X509Certificate cert = generateSelfSignedCert(rsaPublicKey, rsaPrivateKey);
        keyStore.setKeyEntry(
                "jwekey",
                rsaPrivateKey,
                "changeit".toCharArray(),
                new java.security.cert.Certificate[] {cert});

        File tempFile = File.createTempFile("geostore-test-jwe-", ".p12");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            keyStore.store(fos, "changeit".toCharArray());
        }
        return tempFile;
    }

    /**
     * Generates a minimal self-signed X.509 certificate for test purposes using DER encoding
     * directly (no sun.security.x509 dependency).
     */
    private static X509Certificate generateSelfSignedCert(
            RSAPublicKey pubKey, RSAPrivateKey privKey) throws Exception {
        // Build a self-signed cert by constructing the DER TBSCertificate manually,
        // signing it, and parsing it back via CertificateFactory.
        byte[] subjectBytes =
                derSequence(
                        derSet(
                                derSequence(
                                        derOid(new int[] {2, 5, 4, 3}), // OID for CN
                                        derUtf8String("GeoStore Test JWE"))));

        long now = System.currentTimeMillis();
        byte[] notBefore = derUtcTime(new Date(now));
        byte[] notAfter = derUtcTime(new Date(now + 365L * 24 * 60 * 60 * 1000));
        byte[] validity = derSequence(notBefore, notAfter);

        // SHA256withRSA OID: 1.2.840.113549.1.1.11
        byte[] sha256WithRsa =
                derSequence(derOid(new int[] {1, 2, 840, 113549, 1, 1, 11}), derNull());

        byte[] serialNumber =
                derInteger(new java.math.BigInteger(64, new java.security.SecureRandom()));
        byte[] version = derExplicit(0, derInteger(java.math.BigInteger.valueOf(2))); // v3

        byte[] subjectPublicKeyInfo = pubKey.getEncoded();

        byte[] tbsCertificate =
                derSequence(
                        version,
                        serialNumber,
                        sha256WithRsa,
                        subjectBytes,
                        validity,
                        subjectBytes,
                        subjectPublicKeyInfo);

        java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
        sig.initSign(privKey);
        sig.update(tbsCertificate);
        byte[] signature = sig.sign();

        byte[] signatureBits = derBitString(signature);
        byte[] certificate = derSequence(tbsCertificate, sha256WithRsa, signatureBits);

        java.security.cert.CertificateFactory cf =
                java.security.cert.CertificateFactory.getInstance("X.509");
        return (X509Certificate)
                cf.generateCertificate(new java.io.ByteArrayInputStream(certificate));
    }

    // --- Minimal DER encoding helpers for self-signed cert generation ---

    private static byte[] derSequence(byte[]... items) {
        return derTagged(0x30, concat(items));
    }

    private static byte[] derSet(byte[]... items) {
        return derTagged(0x31, concat(items));
    }

    private static byte[] derOid(int[] components) {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        buf.write(40 * components[0] + components[1]);
        for (int i = 2; i < components.length; i++) {
            encodeOidComponent(buf, components[i]);
        }
        byte[] content = buf.toByteArray();
        return derTagged(0x06, content);
    }

    private static void encodeOidComponent(java.io.ByteArrayOutputStream buf, int value) {
        if (value < 128) {
            buf.write(value);
        } else {
            // Multi-byte encoding
            byte[] bytes = new byte[5];
            int pos = 4;
            bytes[pos] = (byte) (value & 0x7F);
            value >>= 7;
            while (value > 0) {
                pos--;
                bytes[pos] = (byte) ((value & 0x7F) | 0x80);
                value >>= 7;
            }
            buf.write(bytes, pos, 5 - pos);
        }
    }

    private static byte[] derUtf8String(String s) {
        return derTagged(0x0C, s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] derNull() {
        return new byte[] {0x05, 0x00};
    }

    private static byte[] derInteger(java.math.BigInteger value) {
        byte[] content = value.toByteArray();
        return derTagged(0x02, content);
    }

    private static byte[] derBitString(byte[] data) {
        byte[] content = new byte[data.length + 1];
        content[0] = 0; // no unused bits
        System.arraycopy(data, 0, content, 1, data.length);
        return derTagged(0x03, content);
    }

    private static byte[] derUtcTime(Date date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyMMddHHmmss'Z'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        byte[] content = sdf.format(date).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        return derTagged(0x17, content);
    }

    private static byte[] derExplicit(int tag, byte[] content) {
        return derTagged(0xA0 | tag, content);
    }

    private static byte[] derTagged(int tag, byte[] content) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(tag);
        writeLength(out, content.length);
        out.write(content, 0, content.length);
        return out.toByteArray();
    }

    private static void writeLength(java.io.ByteArrayOutputStream out, int length) {
        if (length < 128) {
            out.write(length);
        } else if (length < 256) {
            out.write(0x81);
            out.write(length);
        } else {
            out.write(0x82);
            out.write(length >> 8);
            out.write(length & 0xFF);
        }
    }

    private static byte[] concat(byte[][]... arrays) {
        int total = 0;
        for (byte[][] arr : arrays) {
            for (byte[] b : arr) {
                total += b.length;
            }
        }
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[][] arr : arrays) {
            for (byte[] b : arr) {
                System.arraycopy(b, 0, result, pos, b.length);
                pos += b.length;
            }
        }
        return result;
    }

    /** Creates a JWE token wrapping a signed JWT (nested JWS in JWE). */
    private String createSignedJweToken(String email, String sub, String aud) throws Exception {
        long now = System.currentTimeMillis() / 1000;

        JWTClaimsSet innerClaims =
                new JWTClaimsSet.Builder()
                        .subject(sub)
                        .issuer("https://test.issuer/")
                        .audience(aud)
                        .claim("email", email)
                        .issueTime(new Date(now * 1000))
                        .expirationTime(new Date((now + 3600) * 1000))
                        .build();

        SignedJWT signedJWT =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TEST_KID).build(),
                        innerClaims);
        signedJWT.sign(new RSASSASigner(rsaPrivateKey));

        JWEHeader jweHeader =
                new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM)
                        .contentType("JWT")
                        .build();
        JWEObject jweObject = new JWEObject(jweHeader, new Payload(signedJWT));
        jweObject.encrypt(new RSAEncrypter(rsaPublicKey));
        return jweObject.serialize();
    }

    /** Recreates the filter with current configuration (picks up JWE config changes). */
    private void recreateFilter() {
        GeoStoreOAuthRestTemplate restTemplate =
                OpenIdConnectRestTemplateFactory.create(
                        configuration, new DefaultAccessTokenRequest());
        JwksRsaKeyProvider jwksKeyProvider = new JwksRsaKeyProvider(authService + "/certs");
        OpenIdConnectTokenServices tokenServices =
                new OpenIdConnectTokenServices(configuration.getPrincipalKey());
        TokenAuthenticationCache cache =
                new TokenAuthenticationCache(
                        configuration.getCacheSize(), configuration.getCacheExpirationMinutes());
        MultiTokenValidator validator =
                new MultiTokenValidator(
                        java.util.Arrays.asList(
                                new AudienceAccessTokenValidator(), new SubjectTokenValidator()));
        this.filter =
                new OpenIdConnectFilter(
                        tokenServices,
                        restTemplate,
                        configuration,
                        cache,
                        validator,
                        jwksKeyProvider);
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
