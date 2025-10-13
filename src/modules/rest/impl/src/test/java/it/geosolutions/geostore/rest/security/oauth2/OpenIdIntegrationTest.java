/* ====================================================================
 *
 * Copyright (C) 2022-2025 GeoSolutions S.A.S.
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.UserGroupAttribute;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.UserGroupService;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.security.oauth2.google.OAuthGoogleSecurityConfiguration;
import it.geosolutions.geostore.services.rest.security.oauth2.google.OpenIdFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import org.apache.commons.codec.binary.Base64;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
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

    private static WireMockServer openIdService;
    private String authService;
    private OpenIdFilter filter;
    private TestOAuth2Configuration configuration;

    @BeforeClass
    public static void beforeClass() {
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

    @Before
    public void before() {
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
        configuration.setBeanName("googleOAuth2Config"); // provider will mirror this via override
        configuration.setEnableRedirectEntryPoint(true);
        configuration.setRedirectUri("../../../geostore/rest/users/user/details");
        configuration.setScopes("openId,email");

        OAuthGoogleSecurityConfiguration securityConfiguration =
                new OAuthGoogleSecurityConfiguration() {
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
        filter =
                new OpenIdFilter(
                        securityConfiguration.googleTokenServices(),
                        restTemplate,
                        configuration,
                        securityConfiguration.oAuth2Cache());

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
                jsonPayload().put("email", "admin@ex.com").put("roles", arr("ADMIN")).build());
        stubTokenForCode(
                CODE_ROLES_EMPTY,
                jsonPayload().put("email", "demote@ex.com").put("roles", "[]").raw());
        // reconcile: provide groups ["A","B"]
        stubTokenForCode(
                CODE_GROUPS_RECON,
                jsonPayload().put("email", "recon@ex.com").put("groups", arr("A", "B")).build());
    }

    @After
    public void afterTest() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testRedirect() throws IOException, ServletException {
        MockHttpServletRequest request = createRequest("google/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(302, response.getStatus());
        assertEquals(configuration.buildLoginUri(), response.getRedirectedUrl());
    }

    @Test
    public void testAuthentication_basic() throws IOException, ServletException {
        MockHttpServletRequest request = createRequest("google/login");
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
        // roles claim missing -> preserve default USER
        assertEquals(Role.USER, user.getRole());
    }

    @Test
    public void testGroupsFromToken_hdDomain() throws Exception {
        configuration.setGroupsClaim("hd"); // Google hosted domain string claim
        MockHttpServletRequest request = createRequest("google/login");
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
        MockHttpServletRequest request = createRequest("google/login");
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
        MockHttpServletRequest request = createRequest("google/login");
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
        OpenIdFilter seededFilter = getOpenIdFilter(seeded, svc);

        MockHttpServletRequest request = createRequest("google/login");
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

    private OpenIdFilter getOpenIdFilter(User seeded, DummyUserGroupService svc) {
        OpenIdFilter seededFilter =
                new OpenIdFilter(
                        (GeoStoreRemoteTokenServices)
                                ((OAuthGoogleSecurityConfiguration)
                                                new OAuthGoogleSecurityConfiguration() {
                                                    @Override
                                                    protected GeoStoreOAuthRestTemplate
                                                            restTemplate() {
                                                        return (GeoStoreOAuthRestTemplate)
                                                                filter.restTemplate;
                                                    }

                                                    @Override
                                                    public OAuth2Configuration configuration() {
                                                        return configuration;
                                                    }
                                                })
                                        .googleTokenServices(),
                        (GeoStoreOAuthRestTemplate) filter.restTemplate,
                        configuration,
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

    private static class TestOAuth2Configuration extends OAuth2Configuration {
        @Override
        public String getProvider() {
            return getBeanName();
        }
    }

    private static class DummyUserGroupService implements UserGroupService {
        private final Map<String, UserGroup> byName = new HashMap<>();
        private final Map<Long, UserGroup> byId = new HashMap<>();
        private long nextId = 1;

        @Override
        public long insert(UserGroup g) {
            if (g.getId() == null) g.setId(nextId++);
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
            return List.of();
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
