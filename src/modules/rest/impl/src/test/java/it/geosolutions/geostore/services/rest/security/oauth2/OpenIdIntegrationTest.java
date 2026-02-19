/*
 *  Copyright (C) 2022-2025 GeoSolutions S.A.S.
 *  http://www.geo-solutions.it
 *
 *  GPLv3 + Classpath exception
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.
 *
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by developers
 *  of GeoSolutions.  For more information on GeoSolutions, please see
 *  <http://www.geo-solutions.it/>.
 *
 */
package it.geosolutions.geostore.services.rest.security.oauth2;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.UserGroupAttribute;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.UserGroupService;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.OpenIdConnectConfiguration;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.OpenIdConnectFilter;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.OpenIdConnectSecurityConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import org.apache.commons.codec.binary.Base64;
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

/**
 * Integration-style tests against a WireMock-ed OpenID Provider.
 *
 * <p>Enhanced to verify: - Redirect flow - Basic authentication via auth code - Groups from token
 * (hd or groups claim) - Roles resolution (present ADMIN, empty list) - Provider-scoped group
 * reconciliation (keep local & other provider, replace own)
 */
public class OpenIdIntegrationTest {

    private static final String CLIENT_ID = "kbyuFDidLLm280LIwVFiazOqjO3ty8KH";
    private static final String CLIENT_SECRET =
            "60Op4HFM0I8ajz0WdiStAbziZ-VFQttXuxixHHs2R7r7-CW8GR79l-mmLqMhc-Sa";

    private static final String CODE = "R-2CqM7H1agwc7Cx";
    private static final String CODE_GROUPS_HD = "CODE_GROUPS_HD";
    private static final String CODE_ROLES_ADMIN = "CODE_ROLES_ADMIN";
    private static final String CODE_ROLES_EMPTY = "CODE_ROLES_EMPTY";
    private static final String CODE_GROUPS_RECON = "CODE_GROUPS_RECON";
    private static final String CODE_ROLES_GUEST = "CODE_ROLES_GUEST";
    private static final String CODE_USERINFO_ROLES = "CODE_USERINFO_ROLES";
    private static final String CODE_USERINFO_GROUPS = "CODE_USERINFO_GROUPS";

    private static WireMockServer openIdService;
    private String authService;
    private OpenIdConnectFilter filter;
    private TestOAuth2Configuration configuration;

    @BeforeAll
    static void beforeClass() {
        openIdService =
                new WireMockServer(
                        wireMockConfig()
                                .dynamicPort()
                                // uncomment for verbose logging
                                .notifier(new ConsoleNotifier(true)));
        openIdService.start();

        openIdService.stubFor(
                WireMock.get(urlEqualTo("/certs"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody("{\"keys\":[]}")));

        // generic userinfo stub
        openIdService.stubFor(
                any(urlPathEqualTo("/userinfo"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody("{}")));
    }

    @BeforeEach
    void before() {
        // base URL
        authService = "http://localhost:" + openIdService.port();

        configuration = new TestOAuth2Configuration();
        configuration.setClientId(CLIENT_ID);
        configuration.setClientSecret(CLIENT_SECRET);
        configuration.setRevokeEndpoint(authService + "/revoke");
        configuration.setAccessTokenUri(authService + "/token");
        configuration.setAuthorizationUri(authService + "/authorize");
        configuration.setCheckTokenEndpointUrl(authService + "/userinfo");
        configuration.setEnabled(true);
        configuration.setAutoCreateUser(true);
        configuration.setIdTokenUri(authService + "/certs");
        configuration.setBeanName("oidcOAuth2Config"); // provider will mirror this via override
        configuration.setEnableRedirectEntryPoint(true);
        configuration.setRedirectUri("../../../geostore/rest/users/user/details");
        configuration.setScopes("openId,email");

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
        // Disable JWKS verification — test uses unsigned JWTs (alg: none)
        restTemplate.setTokenStore(null);
        filter =
                new OpenIdConnectFilter(
                        securityConfiguration.oidcTokenServices(),
                        restTemplate,
                        configuration,
                        securityConfiguration.oAuth2Cache(),
                        null,
                        null);

        // attach a simple in-memory UserGroupService so group reconciliation works
        filter.setUserGroupService(new DummyUserGroupService());

        // default stub for original CODE -> returns email only (no groups/roles)
        stubTokenForCode(CODE, jsonPayload().put("email", "ritter@erdukunde.de").build());

        // specific stubs for additional scenarios
        stubTokenForCode(
                CODE_GROUPS_HD,
                jsonPayload().put("email", "u@ex.com").put("hd", "geosolutionsgroup.com").build());
        stubTokenForCode(
                CODE_ROLES_ADMIN,
                jsonPayload().put("email", "admin@ex.com").putArray("roles", arr("ADMIN")).build());
        stubTokenForCode(
                CODE_ROLES_EMPTY,
                jsonPayload().put("email", "demote@ex.com").putArray("roles", "[]").build());
        // reconcile: provide groups ["A","B"]
        stubTokenForCode(
                CODE_GROUPS_RECON,
                jsonPayload()
                        .put("email", "recon@ex.com")
                        .putArray("groups", arr("A", "B"))
                        .build());
        // demotion/promotion check for existing user override
        stubTokenForCode(
                CODE_ROLES_GUEST,
                jsonPayload().put("email", "guest@ex.com").putArray("roles", arr("GUEST")).build());

        // Stubs for userinfo fallback tests: JWT has NO roles/groups claim
        stubTokenForCode(CODE_USERINFO_ROLES, jsonPayload().put("email", "uiroles@ex.com").build());
        stubTokenForCode(
                CODE_USERINFO_GROUPS, jsonPayload().put("email", "uigroups@ex.com").build());

        // Userinfo stubs returning roles/groups for specific access tokens
        openIdService.stubFor(
                WireMock.get(urlPathEqualTo("/userinfo"))
                        .withHeader("Authorization", equalTo("Bearer at-" + CODE_USERINFO_ROLES))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(
                                                "{\"email\":\"uiroles@ex.com\","
                                                        + "\"roles\":[\"ADMIN\"]}")));
        openIdService.stubFor(
                WireMock.get(urlPathEqualTo("/userinfo"))
                        .withHeader("Authorization", equalTo("Bearer at-" + CODE_USERINFO_GROUPS))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(
                                                "{\"email\":\"uigroups@ex.com\","
                                                        + "\"groups\":[\"TEAM_X\",\"TEAM_Y\"]}")));
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
        assertEquals(configuration.buildLoginUri(), response.getRedirectedUrl());
    }

    @Test
    public void testAuthentication_basic() throws IOException, ServletException {
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
        assertNotNull(authentication, "authentication should not be null");
        User user = (User) authentication.getPrincipal();
        assertEquals("ritter@erdukunde.de", user.getName());
        // roles claim missing -> preserve default USER
        assertEquals(Role.USER, user.getRole());
    }

    @Test
    public void testGroupsFromToken_hdDomain() throws Exception {
        configuration.setGroupsClaim("hd"); // Google hosted domain string claim
        MockHttpServletRequest request = createRequest("oidc/login");
        request.setParameter("authorization_code", CODE_GROUPS_HD);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.restTemplate
                .getOAuth2ClientContext()
                .getAccessTokenRequest()
                .setAuthorizationCode(CODE_GROUPS_HD);
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();
        UserGroup group = user.getGroups().stream().findAny().orElseThrow();
        assertEquals("geosolutionsgroup.com", group.getGroupName());
    }

    @Test
    public void testRoleFromToken_adminPromotion() throws Exception {
        configuration.setRolesClaim("roles");
        MockHttpServletRequest request = createRequest("oidc/login");
        request.setParameter("authorization_code", CODE_ROLES_ADMIN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.restTemplate
                .getOAuth2ClientContext()
                .getAccessTokenRequest()
                .setAuthorizationCode(CODE_ROLES_ADMIN);
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();
        assertEquals(Role.ADMIN, user.getRole());
    }

    @Test
    public void testRoleFromToken_emptyListDemotesToDefault() throws Exception {
        configuration.setRolesClaim("roles");
        MockHttpServletRequest request = createRequest("oidc/login");
        request.setParameter("authorization_code", CODE_ROLES_EMPTY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.restTemplate
                .getOAuth2ClientContext()
                .getAccessTokenRequest()
                .setAuthorizationCode(CODE_ROLES_EMPTY);
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();
        // empty roles -> computeRole defaults to USER
        assertEquals(Role.USER, user.getRole());
    }

    @Test
    public void testProviderScopedGroupReconciliation() throws Exception {
        configuration.setGroupsClaim("groups");

        // Pre-seed the DummyUserGroupService with a local & other-provider remote group bound to
        // the same user
        DummyUserGroupService svc = (DummyUserGroupService) filter.getUserGroupService();
        User seeded = new User();
        seeded.setId(777L);
        seeded.setName("recon@ex.com");
        seeded.setRole(Role.USER);
        seeded.setEnabled(true);
        seeded.setGroups(new HashSet<>());

        UserGroup local = new UserGroup();
        local.setGroupName("LOCAL_GROUP");
        local.setAttributes(new ArrayList<>());
        svc.insert(local);

        UserGroup otherProv = new UserGroup();
        otherProv.setGroupName("OTHER_GROUP");
        otherProv.setAttributes(new ArrayList<>(List.of(attr("sourceService", "other"))));
        svc.insert(otherProv);

        // old remote from THIS provider (should be removed)
        UserGroup oldRemote = new UserGroup();
        oldRemote.setGroupName("OLD_REMOTE");
        oldRemote.setAttributes(
                new ArrayList<>(List.of(attr("sourceService", configuration.getProvider()))));
        svc.insert(oldRemote);

        // attach seeded groups to user
        seeded.getGroups().add(local);
        seeded.getGroups().add(otherProv);
        seeded.getGroups().add(oldRemote);

        // Wrap the filter with a retrieveUser override that returns our seeded user
        OpenIdConnectFilter seededFilter = getOpenIdFilter(seeded, svc);

        MockHttpServletRequest request = createRequest("oidc/login");
        request.setParameter("authorization_code", CODE_GROUPS_RECON);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        seededFilter
                .restTemplate
                .getOAuth2ClientContext()
                .getAccessTokenRequest()
                .setAuthorizationCode(CODE_GROUPS_RECON);
        seededFilter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Set<String> names =
                seeded.getGroups().stream()
                        .map(UserGroup::getGroupName)
                        .collect(Collectors.toSet());
        // kept
        assertTrue(names.contains("LOCAL_GROUP"));
        assertTrue(names.contains("OTHER_GROUP"));
        // new from token
        assertTrue(names.contains("A"));
        assertTrue(names.contains("B"));
        // removed old provider-remote not in token
        assertFalse(names.contains("OLD_REMOTE"));

        // ensure new groups are tagged with sourceService = provider
        for (String g : List.of("A", "B")) {
            UserGroup ug = svc.get(g);
            assertNotNull(ug);
            String src =
                    ug.getAttributes().stream()
                            .filter(a -> "sourceService".equals(a.getName()))
                            .findFirst()
                            .orElseThrow()
                            .getValue();
            assertEquals(configuration.getProvider(), src);
        }
    }

    // ---------------------------------------------------------------------
    // New tests: creation, override role, full provider-remote reset & idempotency
    // ---------------------------------------------------------------------

    @Test
    public void testNewUserCreatedWithRoleFromOidc() throws Exception {
        configuration.setRolesClaim("roles");

        MockHttpServletRequest request = createRequest("oidc/login");
        request.setParameter("authorization_code", CODE_ROLES_ADMIN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.restTemplate
                .getOAuth2ClientContext()
                .getAccessTokenRequest()
                .setAuthorizationCode(CODE_ROLES_ADMIN);

        // No pre-existing user; filter will create it based on token.
        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        assertEquals("admin@ex.com", user.getName());
        assertEquals(Role.ADMIN, user.getRole());

        // Created-by-provider marker attribute
        assertTrue(
                user.getAttribute() != null
                        && user.getAttribute().stream()
                                .anyMatch(
                                        a ->
                                                OAuth2Configuration.CONFIGURATION_NAME.equals(
                                                                a.getName())
                                                        && configuration
                                                                .getBeanName()
                                                                .equals(a.getValue())));
    }

    @Test
    public void testExistingUserRoleOverriddenByOidc() throws Exception {
        configuration.setRolesClaim("roles");

        // Seed an existing user with ADMIN; token will demote to GUEST
        User existing = new User();
        existing.setId(901L);
        existing.setName("guest@ex.com");
        existing.setRole(Role.ADMIN);
        existing.setEnabled(true);
        existing.setGroups(new HashSet<>());
        existing.setAttribute(new ArrayList<>());

        OpenIdConnectFilter seededFilter =
                getOpenIdFilter(existing, (DummyUserGroupService) filter.getUserGroupService());

        MockHttpServletRequest request = createRequest("oidc/login");
        request.setParameter("authorization_code", CODE_ROLES_GUEST);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        seededFilter
                .restTemplate
                .getOAuth2ClientContext()
                .getAccessTokenRequest()
                .setAuthorizationCode(CODE_ROLES_GUEST);

        seededFilter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        assertEquals("guest@ex.com", user.getName());
        assertEquals(Role.GUEST, user.getRole());
    }

    @Test
    public void testProviderRemoteGroupsResetAlignedAndIdempotent() throws Exception {
        configuration.setGroupsClaim("groups");

        DummyUserGroupService svc = (DummyUserGroupService) filter.getUserGroupService();

        // Seed user with: local + other provider remote + old provider remote
        User seeded = new User();
        seeded.setId(1001L);
        seeded.setName("recon@ex.com");
        seeded.setRole(Role.USER);
        seeded.setEnabled(true);
        seeded.setGroups(new HashSet<>());

        UserGroup local = new UserGroup();
        local.setGroupName("LOCAL_GROUP");
        local.setAttributes(new ArrayList<>()); // no sourceService -> local
        svc.insert(local);

        UserGroup otherProv = new UserGroup();
        otherProv.setGroupName("OTHER_GROUP");
        otherProv.setAttributes(new ArrayList<>(List.of(attr("sourceService", "other"))));
        svc.insert(otherProv);

        UserGroup oldRemote = new UserGroup();
        oldRemote.setGroupName("OLD_REMOTE");
        oldRemote.setAttributes(
                new ArrayList<>(List.of(attr("sourceService", configuration.getProvider()))));
        svc.insert(oldRemote);

        seeded.getGroups().add(local);
        seeded.getGroups().add(otherProv);
        seeded.getGroups().add(oldRemote);

        OpenIdConnectFilter seededFilter = getOpenIdFilter(seeded, svc);

        // First pass: token groups ["A","B"]
        MockHttpServletRequest request = createRequest("oidc/login");
        request.setParameter("authorization_code", CODE_GROUPS_RECON);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        seededFilter
                .restTemplate
                .getOAuth2ClientContext()
                .getAccessTokenRequest()
                .setAuthorizationCode(CODE_GROUPS_RECON);
        seededFilter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());

        Set<String> names =
                seeded.getGroups().stream()
                        .map(UserGroup::getGroupName)
                        .collect(Collectors.toSet());
        assertTrue(names.contains("LOCAL_GROUP"));
        assertTrue(names.contains("OTHER_GROUP"));
        assertFalse(names.contains("OLD_REMOTE"));
        assertTrue(names.contains("A"));
        assertTrue(names.contains("B"));

        // Only A,B are provider-remote
        Set<String> providerRemotes =
                seeded.getGroups().stream()
                        .filter(g -> g.getAttributes() != null)
                        .filter(
                                g ->
                                        g.getAttributes().stream()
                                                .anyMatch(
                                                        a ->
                                                                "sourceService".equals(a.getName())
                                                                        && configuration
                                                                                .getProvider()
                                                                                .equals(
                                                                                        a
                                                                                                .getValue())))
                        .map(UserGroup::getGroupName)
                        .collect(Collectors.toSet());
        assertEquals(Set.of("A", "B"), providerRemotes);

        // Idempotency: second pass with same token makes no changes
        int beforeCount = seeded.getGroups().size();
        MockHttpServletRequest request2 = createRequest("oidc/login");
        request2.setParameter("authorization_code", CODE_GROUPS_RECON);
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        seededFilter
                .restTemplate
                .getOAuth2ClientContext()
                .getAccessTokenRequest()
                .setAuthorizationCode(CODE_GROUPS_RECON);
        seededFilter.doFilter(request2, response2, new MockFilterChain());
        assertEquals(200, response2.getStatus());
        int afterCount = seeded.getGroups().size();
        assertEquals(beforeCount, afterCount);

        Set<String> providerRemotesAfter =
                seeded.getGroups().stream()
                        .filter(g -> g.getAttributes() != null)
                        .filter(
                                g ->
                                        g.getAttributes().stream()
                                                .anyMatch(
                                                        a ->
                                                                "sourceService".equals(a.getName())
                                                                        && configuration
                                                                                .getProvider()
                                                                                .equals(
                                                                                        a
                                                                                                .getValue())))
                        .map(UserGroup::getGroupName)
                        .collect(Collectors.toSet());
        assertEquals(Set.of("A", "B"), providerRemotesAfter);
    }

    @Test
    public void testAuthentication_rolesFromUserinfo() throws Exception {
        // rolesClaim is configured, but the JWT does NOT contain the claim.
        // The userinfo endpoint response (WireMock stub) returns roles=[ADMIN].
        configuration.setRolesClaim("roles");

        MockHttpServletRequest request = createRequest("oidc/login");
        request.setParameter("authorization_code", CODE_USERINFO_ROLES);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.restTemplate
                .getOAuth2ClientContext()
                .getAccessTokenRequest()
                .setAuthorizationCode(CODE_USERINFO_ROLES);
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication, "authentication should not be null");
        User user = (User) authentication.getPrincipal();
        assertEquals("uiroles@ex.com", user.getName());
        // Role should come from userinfo fallback
        assertEquals(Role.ADMIN, user.getRole());
    }

    @Test
    public void testAuthentication_groupsFromUserinfo() throws Exception {
        // groupsClaim is configured, but the JWT does NOT contain the claim.
        // The userinfo endpoint response (WireMock stub) returns groups=[TEAM_X, TEAM_Y].
        configuration.setGroupsClaim("groups");

        MockHttpServletRequest request = createRequest("oidc/login");
        request.setParameter("authorization_code", CODE_USERINFO_GROUPS);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.restTemplate
                .getOAuth2ClientContext()
                .getAccessTokenRequest()
                .setAuthorizationCode(CODE_USERINFO_GROUPS);
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication, "authentication should not be null");
        User user = (User) authentication.getPrincipal();
        assertEquals("uigroups@ex.com", user.getName());
        // Groups should come from userinfo fallback
        Set<String> groupNames =
                user.getGroups().stream().map(UserGroup::getGroupName).collect(Collectors.toSet());
        assertTrue(groupNames.contains("TEAM_X"), "Should contain TEAM_X from userinfo");
        assertTrue(groupNames.contains("TEAM_Y"), "Should contain TEAM_Y from userinfo");
    }

    private OpenIdConnectFilter getOpenIdFilter(User seeded, DummyUserGroupService svc) {
        OpenIdConnectFilter seededFilter =
                new OpenIdConnectFilter(
                        new OpenIdConnectSecurityConfiguration() {
                            @Override
                            protected GeoStoreOAuthRestTemplate restTemplate() {
                                return (GeoStoreOAuthRestTemplate) filter.restTemplate;
                            }

                            @Override
                            public OAuth2Configuration configuration() {
                                return configuration;
                            }
                        }.oidcTokenServices(),
                        (GeoStoreOAuthRestTemplate) filter.restTemplate,
                        configuration,
                        new TokenAuthenticationCache(),
                        null,
                        null) {
                    @Override
                    protected User retrieveUserWithAuthorities(
                            String username,
                            javax.servlet.http.HttpServletRequest request,
                            javax.servlet.http.HttpServletResponse response) {
                        return seeded;
                    }
                };
        seededFilter.setUserGroupService(svc);
        return seededFilter;
    }

    // ---------------------------------------------------------------------
    // Helpers & test support
    // ---------------------------------------------------------------------

    private static class TestOAuth2Configuration extends OpenIdConnectConfiguration {
        // Uses default getProvider() from OAuth2Configuration which strips
        // CONFIG_NAME_SUFFIX ("OAuth2Config") from beanName, e.g.
        // "oidcOAuth2Config" -> "oidc"
    }

    private static class DummyUserGroupService implements UserGroupService {
        private final Map<String, UserGroup> byName = new HashMap<>();
        private final Map<Long, UserGroup> byId = new HashMap<>();
        private long nextId = 1;

        @Override
        public long insert(UserGroup g) {
            if (g.getId() == null) g.setId(nextId++);
            if (g.getAttributes() == null) g.setAttributes(new ArrayList<>());
            byId.put(g.getId(), g);
            byName.put(g.getGroupName(), g);
            return g.getId();
        }

        @Override
        public void assignUserGroup(long userId, long groupId) {
            /* no-op for integration scope */
        }

        @Override
        public void deassignUserGroup(long userId, long groupId) {
            /* no-op */
        }

        @Override
        public UserGroup get(String name) {
            return byName.get(name);
        }

        @Override
        public UserGroup get(long id) {
            return byId.get(id);
        }

        @Override
        public void updateAttributes(long id, List<UserGroupAttribute> attributes)
                throws NotFoundServiceEx {
            UserGroup g = byId.get(id);
            if (g == null) throw new NotFoundServiceEx("not found");
            g.setAttributes(new ArrayList<>(attributes));
            byName.put(g.getGroupName(), g);
        }

        @Override
        public long update(UserGroup group) {
            if (group.getId() == null) group.setId(nextId++);
            if (group.getAttributes() == null) group.setAttributes(new ArrayList<>());
            byId.put(group.getId(), group);
            byName.put(group.getGroupName(), group);
            return group.getId();
        }

        @Override
        public long getCount(String nameLike, boolean all) {
            return byName.size();
        }

        @Override
        public long getCount(User authUser, String nameLike, boolean all) {
            return byName.size();
        }

        @Override
        public boolean delete(long id) {
            return byId.remove(id) != null;
        }

        @Override
        public Collection<UserGroup> findByAttribute(
                String name, List<String> values, boolean ignoreCase) {
            String n = ignoreCase ? name.toLowerCase(Locale.ROOT) : name;
            Set<String> vals = new HashSet<>();
            for (String v : values) vals.add(ignoreCase ? v.toLowerCase(Locale.ROOT) : v);

            List<UserGroup> out = new ArrayList<>();
            for (UserGroup g : byName.values()) {
                List<UserGroupAttribute> attrs = g.getAttributes();
                if (attrs == null) continue;
                for (UserGroupAttribute a : attrs) {
                    if (a.getName() == null) continue;
                    String an = ignoreCase ? a.getName().toLowerCase(Locale.ROOT) : a.getName();
                    if (!an.equals(n)) continue;
                    String av =
                            a.getValue() == null
                                    ? ""
                                    : (ignoreCase
                                            ? a.getValue().toLowerCase(Locale.ROOT)
                                            : a.getValue());
                    if (vals.contains(av)) {
                        out.add(g);
                        break;
                    }
                }
            }
            return out;
        }

        @Override
        public UserGroup getWithAttributes(long id) throws NotFoundServiceEx, BadRequestServiceEx {
            UserGroup g = byId.get(id);
            if (g == null) throw new NotFoundServiceEx("UserGroup not found " + id);
            return g;
        }

        @Override
        public void upsertAttribute(long groupId, String name, String value)
                throws NotFoundServiceEx, BadRequestServiceEx {
            UserGroup g = byId.get(groupId);
            if (g == null) throw new NotFoundServiceEx("UserGroup not found " + groupId);
            if (g.getAttributes() == null) g.setAttributes(new ArrayList<>());
            for (UserGroupAttribute a : g.getAttributes()) {
                if (a.getName() != null && a.getName().equalsIgnoreCase(name)) {
                    a.setValue(value);
                    return;
                }
            }
            UserGroupAttribute a = new UserGroupAttribute();
            a.setName(name);
            a.setValue(value);
            g.getAttributes().add(a);
        }

        @Override
        public java.util.List<UserGroup> getAllAllowed(
                User user, Integer page, Integer entries, String nameLike, boolean all) {
            return new ArrayList<>(byName.values());
        }

        @Override
        public java.util.List<UserGroup> getAll(Integer page, Integer entries) {
            return new ArrayList<>(byName.values());
        }

        @Override
        public java.util.List<UserGroup> getAll(
                Integer page, Integer entries, String nameLike, boolean all) {
            return new ArrayList<>(byName.values());
        }

        @Override
        public java.util.List<ShortResource> updateSecurityRules(
                Long groupId,
                java.util.List<Long> resourcesToSet,
                boolean canRead,
                boolean canWrite) {
            return List.of();
        }

        /**
         * Persist the special UserGroups, those that implies special behavior
         *
         * <p>For obvious reasons, this Method MUST NOT be exposed through the rest interface.
         *
         * @return true if the persist operation finish with success, false otherwise
         */
        @Override
        public boolean insertSpecialUsersGroups() {
            return false;
        }

        /**
         * Remove the special UserGroups, those that implies special behavior
         *
         * <p>For obvious reasons this Method MUST NOT be exposed through the rest interface.
         *
         * @return true if the removal operation finish with success, false otherwise
         */
        @Override
        public boolean removeSpecialUsersGroups() {
            return false;
        }
    }

    private static UserGroupAttribute attr(String n, String v) {
        UserGroupAttribute a = new UserGroupAttribute();
        a.setName(n);
        a.setValue(v);
        return a;
    }

    private void stubTokenForCode(String code, String idTokenPayloadJson) {
        String idToken = unsignedJwtJson(idTokenPayloadJson);
        String body =
                "{"
                        + "\"access_token\":\"at-"
                        + code
                        + "\","
                        + "\"token_type\":\"Bearer\","
                        + "\"scope\":\"openid email\","
                        + "\"expires_in\":3600,"
                        + "\"id_token\":\""
                        + idToken
                        + "\"}";
        openIdService.stubFor(
                WireMock.post(urlPathEqualTo("/token"))
                        .withRequestBody(containing("grant_type=authorization_code"))
                        .withRequestBody(containing("client_id=" + CLIENT_ID))
                        .withRequestBody(containing("code=" + code))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBody(body)));
    }

    private static String unsignedJwtJson(String payloadJson) {
        String header = "{\"alg\":\"none\"}";
        return b64Url(header) + "." + b64Url(payloadJson) + ".";
    }

    private static String b64Url(String s) {
        return new String(
                Base64.encodeBase64URLSafe(s.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
    }

    private static JsonBuilder jsonPayload() {
        return new JsonBuilder();
    }

    private static String arr(String... items) {
        return Arrays.stream(items)
                .map(x -> "\"" + x + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static class JsonBuilder {
        private final StringBuilder sb = new StringBuilder("{");
        private boolean first = true;

        JsonBuilder put(String k, String v) {
            return rawPair(k, "\"" + v + "\"");
        }

        JsonBuilder put(String k, int v) {
            return rawPair(k, String.valueOf(v));
        }

        JsonBuilder putRaw(String k, String rawJson) {
            return rawPair(k, rawJson);
        }

        JsonBuilder putArray(String k, String arrayJson) {
            return rawPair(k, arrayJson);
        }

        JsonBuilder rawPair(String k, String raw) {
            if (!first) sb.append(',');
            sb.append("\"" + k + "\":").append(raw);
            first = false;
            return this;
        }

        String build() {
            return sb.append('}').toString();
        }
        // convenience for roles empty [] case

        String raw() {
            return build();
        }
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
