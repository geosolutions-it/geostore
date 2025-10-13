/*
 *  Copyright (C) 2015-2025 GeoSolutions S.A.S.
 *  http://www.geo-solutions.it
 *
 *  GPLv3 + Classpath exception
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.geosolutions.geostore.services.rest.security;

import static org.junit.Assert.*;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.UserGroupAttribute;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.core.security.MapExpressionUserMapper;
import it.geosolutions.geostore.services.UserGroupService;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreOAuthRestTemplate;
import it.geosolutions.geostore.services.rest.security.oauth2.JWTHelper;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2GeoStoreAuthenticationFilter;
import it.geosolutions.geostore.services.rest.utils.MockedUserService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.binary.Base64; // for base64-url without padding helper
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Enhanced tests for GeoStore authentication filters covering: - username remapping -
 * provider-scoped reconciliation of remote groups - uppercase group handling & tagging with
 * sourceService - role recomputation (present/empty/missing roles claims)
 */
public class GeoStoreAuthenticationFilterTest {

    private static final String USERNAME_HEADER = "username";
    private static final String SAMPLE_USER = "myuser";

    private MockedUserService userService;
    private GeoStoreRequestHeadersAuthenticationFilter headerFilter;
    private HttpServletRequest req;
    private HttpServletResponse resp;

    @Before
    public void setUp() {
        // Setup for the header-based filter tests.
        userService = new MockedUserService();
        headerFilter = new GeoStoreRequestHeadersAuthenticationFilter();
        headerFilter.setUserNameHeader(USERNAME_HEADER);
        headerFilter.setUserService(userService);
        headerFilter.setAutoCreateUser(true);

        req = Mockito.mock(HttpServletRequest.class);
        resp = Mockito.mock(HttpServletResponse.class);

        Mockito.when(req.getHeader(USERNAME_HEADER)).thenReturn(SAMPLE_USER);
        Mockito.when(req.getHeader("header1")).thenReturn("value1");
        Mockito.when(req.getHeaderNames())
                .thenReturn(new Vector<>(Arrays.asList(USERNAME_HEADER, "header1")).elements());
    }

    @After
    public void tearDown() {
        SecurityContextHolder.getContext().setAuthentication(null);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testAutoCreate() throws IOException, ServletException, NotFoundServiceEx {
        headerFilter.doFilter(
                req,
                resp,
                new FilterChain() {
                    @Override
                    public void doFilter(ServletRequest arg0, ServletResponse arg1)
                            throws IOException, ServletException {
                        // No-op chain for testing.
                    }
                });

        User user = userService.get(SAMPLE_USER);
        checkUser(user);
        assertTrue("User should be enabled", user.isEnabled());
    }

    @Test
    public void testAutoCreateDisabled() throws IOException, ServletException, NotFoundServiceEx {
        headerFilter.setEnableAutoCreatedUsers(false);
        headerFilter.doFilter(
                req,
                resp,
                new FilterChain() {
                    @Override
                    public void doFilter(ServletRequest arg0, ServletResponse arg1)
                            throws IOException, ServletException {
                        // No-op chain for testing.
                    }
                });

        User user = userService.get(SAMPLE_USER);
        checkUser(user);
        assertFalse("User should be disabled", user.isEnabled());
    }

    @Test
    public void testAutoCreateAttributesMapping()
            throws IOException, ServletException, NotFoundServiceEx {

        // Map header "header1" to user attribute "attr1".
        headerFilter.setUserMapper(
                new MapExpressionUserMapper(Collections.singletonMap("attr1", "header1")));

        headerFilter.doFilter(
                req,
                resp,
                new FilterChain() {
                    @Override
                    public void doFilter(ServletRequest arg0, ServletResponse arg1)
                            throws IOException, ServletException {
                        // No-op chain for testing.
                    }
                });

        User user = userService.get(SAMPLE_USER);
        checkUser(user);
        assertNotNull("Attributes should not be null", user.getAttribute());
        assertEquals("Should have one attribute", 1, user.getAttribute().size());
        UserAttribute attr = user.getAttribute().get(0);
        assertEquals("Attribute name should be 'attr1'", "attr1", attr.getName());
        assertEquals("Attribute value should be 'value1'", "value1", attr.getValue());
    }

    // ---------------------------------------------------------------------
    // OAuth2/OIDC related tests
    // ---------------------------------------------------------------------

    /** Username remapping via principal/unique claims. */
    @Test
    public void testUsernameRemapping() throws Exception {
        final String ORIGINAL_USERNAME = "myuser";
        final String REMAPPED_USERNAME = "remappedUser";

        TestOAuth2Configuration config = new TestOAuth2Configuration();
        config.setPrincipalKey("principal");
        config.setUniqueUsername("unique");
        config.setBeanName("testBean");
        config.setAutoCreateUser(true);

        // minimal restTemplate mock to avoid NPE in createPreAuthentication
        GeoStoreOAuthRestTemplate rt = Mockito.mock(GeoStoreOAuthRestTemplate.class);
        OAuth2ClientContext ctx = Mockito.mock(OAuth2ClientContext.class);
        Mockito.when(ctx.getAccessToken()).thenReturn(new DefaultOAuth2AccessToken("t"));
        Mockito.when(rt.getOAuth2ClientContext()).thenReturn(ctx);

        OAuth2GeoStoreAuthenticationFilter oauth2Filter =
                new OAuth2GeoStoreAuthenticationFilter(
                        /* tokenServices */ null, /* restTemplate */ rt, config, /* cache */ null) {

                    @Override
                    protected JWTHelper decodeAndValidateIdToken(String idToken) {
                        // Provide a helper that returns both claims to trigger remapping
                        return new JWTHelper(idToken) {
                            @Override
                            public <T> T getClaim(String claimName, Class<T> clazz) {
                                if ("principal".equals(claimName)) return (T) ORIGINAL_USERNAME;
                                if ("unique".equals(claimName)) return (T) REMAPPED_USERNAME;
                                return null;
                            }
                        };
                    }

                    @Override
                    protected User retrieveUserWithAuthorities(
                            String username,
                            HttpServletRequest request,
                            HttpServletResponse response) {
                        User user = new User();
                        user.setName(username);
                        user.setRole(Role.USER);
                        user.setEnabled(true);
                        user.setAttribute(Collections.emptyList());
                        user.setGroups(new HashSet<>());
                        return user;
                    }

                    @Override
                    protected void configureRestTemplate() {
                        /* no-op */
                    }
                };

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        PreAuthenticatedAuthenticationToken authToken =
                oauth2Filter.createPreAuthentication(ORIGINAL_USERNAME, req, resp);
        assertNotNull("Authentication token should not be null", authToken);

        User user = (User) authToken.getPrincipal();
        assertNotNull("User should not be null", user);
        assertEquals(
                "Username should be remapped to the unique claim value",
                REMAPPED_USERNAME,
                user.getName());
    }

    /** Uppercase group handling and assignment; uses a real JWT payload for groups. */
    @Test
    public void testGroupNamesUppercaseAndUserGroupAssignment() throws Exception {
        final String GROUP_FROM_TOKEN = "groupA";
        final String EXPECTED_GROUP = "GROUPA";

        TestOAuth2Configuration config = new TestOAuth2Configuration();
        config.setGroupsClaim("groups");
        config.setBeanName("testBean");
        config.setAutoCreateUser(true);
        config.setGroupNamesUppercase(true);

        DummyUserGroupService dummyGroupService = new DummyUserGroupService();

        GeoStoreOAuthRestTemplate rt = Mockito.mock(GeoStoreOAuthRestTemplate.class);
        OAuth2ClientContext ctx = Mockito.mock(OAuth2ClientContext.class);
        Mockito.when(ctx.getAccessToken()).thenReturn(new DefaultOAuth2AccessToken("t"));
        Mockito.when(rt.getOAuth2ClientContext()).thenReturn(ctx);

        OAuth2GeoStoreAuthenticationFilter oauth2Filter =
                new OAuth2GeoStoreAuthenticationFilter(
                        /* tokenServices */ null, /* restTemplate */ rt, config, /* cache */ null) {

                    @Override
                    protected User retrieveUserWithAuthorities(
                            String username,
                            HttpServletRequest request,
                            HttpServletResponse response) {
                        User user = new User();
                        user.setId(1L);
                        user.setName(username);
                        user.setRole(Role.USER);
                        user.setEnabled(true);
                        user.setAttribute(Collections.emptyList());
                        user.setGroups(new HashSet<>());
                        return user;
                    }

                    @Override
                    protected void configureRestTemplate() {
                        /* no-op */
                    }
                };
        oauth2Filter.setUserGroupService(dummyGroupService);

        // put an id_token with groups claim into the request context
        String jwt = unsignedJwtJson("{\"groups\":[\"" + GROUP_FROM_TOKEN + "\"]}");
        MockHttpServletRequest req = new MockHttpServletRequest();
        ServletRequestAttributes attrs = new ServletRequestAttributes(req);
        attrs.setAttribute(GeoStoreOAuthRestTemplate.ID_TOKEN_VALUE, jwt, 0);
        RequestContextHolder.setRequestAttributes(attrs);

        PreAuthenticatedAuthenticationToken authToken =
                oauth2Filter.createPreAuthentication("anyuser", req, new MockHttpServletResponse());
        assertNotNull(authToken);

        User user = (User) authToken.getPrincipal();
        assertNotNull(user);

        boolean found =
                user.getGroups().stream().anyMatch(g -> EXPECTED_GROUP.equals(g.getGroupName()));
        assertTrue("User should be assigned to group " + EXPECTED_GROUP, found);

        UserGroup groupFromService = dummyGroupService.get(EXPECTED_GROUP);
        assertNotNull(
                "Dummy group service should contain group " + EXPECTED_GROUP, groupFromService);
        // sourceService attribute set to provider (beanName in our TestOAuth2Configuration)
        assertEquals(
                "testBean",
                groupFromService.getAttributes().stream()
                        .filter(a -> "sourceService".equals(a.getName()))
                        .findFirst()
                        .orElseThrow()
                        .getValue());
    }

    /**
     * Provider-scoped remote groups reconcile: keep local & other providers, add new from token.
     */
    @Test
    public void testRemoteGroupsUpdateProviderScoped() throws Exception {
        GeoStoreOAuthRestTemplate rt = Mockito.mock(GeoStoreOAuthRestTemplate.class);
        OAuth2ClientContext ctx = Mockito.mock(OAuth2ClientContext.class);
        Mockito.when(ctx.getAccessToken())
                .thenReturn(new DefaultOAuth2AccessToken("oauth2-test-token"));
        Mockito.when(rt.getOAuth2ClientContext()).thenReturn(ctx);

        TestOAuth2Configuration config = new TestOAuth2Configuration();
        config.setGroupsClaim("groups");
        config.setBeanName("testBean"); // provider == beanName by override
        config.setAutoCreateUser(true);
        config.setGroupNamesUppercase(true);

        DummyUserGroupService userGroupService = new DummyUserGroupService();

        User user = new User();
        user.setId(1000L);
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setAttribute(Collections.emptyList());

        // local group (no sourceService)
        UserGroup local = new UserGroup();
        local.setGroupName("local");
        local.setUsers(new ArrayList<>(Collections.singletonList(user)));
        local.setAttributes(new ArrayList<>(Collections.singletonList(attr("any", "v"))));
        userGroupService.insert(local);

        // remote group from another provider -> should be KEPT
        UserGroup otherRemote = new UserGroup();
        otherRemote.setGroupName("remote");
        otherRemote.setUsers(new ArrayList<>(Collections.singletonList(user)));
        otherRemote.setAttributes(
                new ArrayList<>(Collections.singletonList(attr("sourceService", "other"))));
        userGroupService.insert(otherRemote);

        user.setGroups(new HashSet<>(Arrays.asList(local, otherRemote)));

        OAuth2GeoStoreAuthenticationFilter filter =
                new OAuth2GeoStoreAuthenticationFilter(null, rt, config, null) {
                    @Override
                    protected User retrieveUserWithAuthorities(
                            String username,
                            HttpServletRequest request,
                            HttpServletResponse response) {
                        user.setName(username);
                        return user;
                    }

                    @Override
                    protected void configureRestTemplate() {
                        /* no-op */
                    }
                };
        filter.setUserGroupService(userGroupService);

        // token contains groups: ["admin","developer"]
        String jwt = unsignedJwtJson("{\"groups\":[\"admin\",\"developer\"]}");
        MockHttpServletRequest req = new MockHttpServletRequest();
        ServletRequestAttributes attrs = new ServletRequestAttributes(req);
        attrs.setAttribute(GeoStoreOAuthRestTemplate.ID_TOKEN_VALUE, jwt, 0);
        RequestContextHolder.setRequestAttributes(attrs);

        PreAuthenticatedAuthenticationToken authToken =
                filter.createPreAuthentication("test-user", req, new MockHttpServletResponse());
        assertNotNull(authToken);

        User authenticatedUser = (User) authToken.getPrincipal();
        assertNotNull(authenticatedUser);

        Set<UserGroup> groups = authenticatedUser.getGroups();
        // local + otherRemote + ADMIN + DEVELOPER = 4 groups (provider-scoped behavior)
        assertEquals(4, groups.size());

        Map<String, UserGroup> byName =
                groups.stream()
                        .collect(Collectors.toMap(UserGroup::getGroupName, Function.identity()));
        assertTrue(byName.containsKey("local"));
        assertTrue(byName.containsKey("remote")); // kept (other provider)
        assertTrue(byName.containsKey("ADMIN"));
        assertTrue(byName.containsKey("DEVELOPER"));

        // new groups tagged with sourceService = provider ("testBean")
        assertEquals(
                "testBean",
                byName.get("ADMIN").getAttributes().stream()
                        .filter(a -> "sourceService".equals(a.getName()))
                        .findFirst()
                        .orElseThrow()
                        .getValue());
        assertEquals(
                "testBean",
                byName.get("DEVELOPER").getAttributes().stream()
                        .filter(a -> "sourceService".equals(a.getName()))
                        .findFirst()
                        .orElseThrow()
                        .getValue());
    }

    /**
     * When token has empty groups [], remove provider's remote groups (but keep local/other
     * providers).
     */
    @Test
    public void testRemoteGroupsRemovalWhenGroupsEmpty() throws Exception {
        GeoStoreOAuthRestTemplate rt = Mockito.mock(GeoStoreOAuthRestTemplate.class);
        OAuth2ClientContext ctx = Mockito.mock(OAuth2ClientContext.class);
        Mockito.when(ctx.getAccessToken())
                .thenReturn(new DefaultOAuth2AccessToken("oauth2-test-token"));
        Mockito.when(rt.getOAuth2ClientContext()).thenReturn(ctx);

        TestOAuth2Configuration config = new TestOAuth2Configuration();
        config.setGroupsClaim("groups");
        config.setBeanName("testBean");
        config.setAutoCreateUser(true);
        config.setGroupNamesUppercase(true);

        DummyUserGroupService userGroupService = new DummyUserGroupService();

        User user = new User();
        user.setId(2000L);
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setAttribute(Collections.emptyList());

        // provider remote group (to be removed)
        UserGroup msAdmin = new UserGroup();
        msAdmin.setGroupName("MS_ADMIN_GROUP");
        msAdmin.setAttributes(
                new ArrayList<>(Collections.singletonList(attr("sourceService", "testBean"))));
        msAdmin.setUsers(new ArrayList<>(Collections.singletonList(user)));
        userGroupService.insert(msAdmin);

        // other provider remote group (kept)
        UserGroup other = new UserGroup();
        other.setGroupName("OTHER_GROUP");
        other.setAttributes(
                new ArrayList<>(Collections.singletonList(attr("sourceService", "other"))));
        other.setUsers(new ArrayList<>(Collections.singletonList(user)));
        userGroupService.insert(other);

        // local (kept)
        UserGroup local = new UserGroup();
        local.setGroupName("LOCAL_GROUP");
        local.setAttributes(new ArrayList<>());
        local.setUsers(new ArrayList<>(Collections.singletonList(user)));
        userGroupService.insert(local);

        user.setGroups(new HashSet<>(Arrays.asList(msAdmin, other, local)));

        OAuth2GeoStoreAuthenticationFilter filter =
                new OAuth2GeoStoreAuthenticationFilter(null, rt, config, null) {
                    @Override
                    protected User retrieveUserWithAuthorities(
                            String username,
                            HttpServletRequest request,
                            HttpServletResponse response) {
                        user.setName(username);
                        return user;
                    }

                    @Override
                    protected void configureRestTemplate() {
                        /* no-op */
                    }
                };
        filter.setUserGroupService(userGroupService);

        // groups claim present but empty []
        String jwt = unsignedJwtJson("{\"groups\":[]}");
        MockHttpServletRequest req = new MockHttpServletRequest();
        ServletRequestAttributes attrs = new ServletRequestAttributes(req);
        attrs.setAttribute(GeoStoreOAuthRestTemplate.ID_TOKEN_VALUE, jwt, 0);
        RequestContextHolder.setRequestAttributes(attrs);

        PreAuthenticatedAuthenticationToken token =
                filter.createPreAuthentication("test", req, new MockHttpServletResponse());
        assertNotNull(token);
        User u = (User) token.getPrincipal();
        Set<String> groups =
                u.getGroups().stream().map(UserGroup::getGroupName).collect(Collectors.toSet());
        // provider remote removed, others kept
        assertFalse(groups.contains("MS_ADMIN_GROUP"));
        assertTrue(groups.contains("OTHER_GROUP"));
        assertTrue(groups.contains("LOCAL_GROUP"));
    }

    /** Roles: present claim -> recompute; empty -> demote; missing -> preserve. */
    @Test
    public void testRoleResolutionPresentEmptyMissing() throws Exception {
        GeoStoreOAuthRestTemplate rt = Mockito.mock(GeoStoreOAuthRestTemplate.class);
        OAuth2ClientContext ctx = Mockito.mock(OAuth2ClientContext.class);
        Mockito.when(ctx.getAccessToken()).thenReturn(new DefaultOAuth2AccessToken("t"));
        Mockito.when(rt.getOAuth2ClientContext()).thenReturn(ctx);

        TestOAuth2Configuration config = new TestOAuth2Configuration();
        config.setRolesClaim("roles");
        config.setBeanName("testBean");
        config.setAutoCreateUser(true);

        DummyUserGroupService ugs = new DummyUserGroupService();

        User user = new User();
        user.setId(3000L);
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setAttribute(Collections.emptyList());
        user.setGroups(new HashSet<>());

        OAuth2GeoStoreAuthenticationFilter filter =
                new OAuth2GeoStoreAuthenticationFilter(null, rt, config, null) {
                    @Override
                    protected User retrieveUserWithAuthorities(
                            String username,
                            HttpServletRequest request,
                            HttpServletResponse response) {
                        user.setName(username);
                        return user;
                    }

                    @Override
                    protected void configureRestTemplate() {
                        /* no-op */
                    }
                };
        filter.setUserGroupService(ugs);

        // 1) roles present with ADMIN -> set to ADMIN
        setIdToken("{\"roles\":[\"ADMIN\"]}");
        PreAuthenticatedAuthenticationToken t1 =
                filter.createPreAuthentication(
                        "u", new MockHttpServletRequest(), new MockHttpServletResponse());
        assertEquals(Role.ADMIN, ((User) t1.getPrincipal()).getRole());

        // 2) roles present but empty -> demote to default (USER)
        user.setRole(Role.ADMIN);
        setIdToken("{\"roles\":[]}");
        PreAuthenticatedAuthenticationToken t2 =
                filter.createPreAuthentication(
                        "u", new MockHttpServletRequest(), new MockHttpServletResponse());
        assertEquals(Role.USER, ((User) t2.getPrincipal()).getRole());

        // 3) roles MISSING entirely -> preserve current role
        user.setRole(Role.GUEST);
        setIdToken("{\"some\":\"thing\"}");
        PreAuthenticatedAuthenticationToken t3 =
                filter.createPreAuthentication(
                        "u", new MockHttpServletRequest(), new MockHttpServletResponse());
        assertEquals(Role.GUEST, ((User) t3.getPrincipal()).getRole());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static void checkUser(User user) {
        assertNotNull("User should not be null", user);
        assertEquals("User role should be USER", Role.USER, user.getRole());
        assertTrue("User groups should be empty", user.getGroups().isEmpty());
    }

    private static UserGroupAttribute attr(String n, String v) {
        UserGroupAttribute a = new UserGroupAttribute();
        a.setName(n);
        a.setValue(v);
        return a;
    }

    /**
     * Minimal config that treats beanName as provider (so tests don't need to call setProvider).
     */
    private static class TestOAuth2Configuration extends OAuth2Configuration {
        @Override
        public String getProvider() {
            return getBeanName();
        }
    }

    private static String unsignedJwtJson(String payloadJson) {
        String header = "{\"alg\":\"none\"}";
        String encHeader = base64Url(header);
        String encPayload = base64Url(payloadJson);
        return encHeader + "." + encPayload + "."; // no signature required by JWTHelper for tests
    }

    private static String base64Url(String s) {
        byte[] enc = Base64.encodeBase64URLSafe(s.getBytes(StandardCharsets.UTF_8));
        return new String(enc, StandardCharsets.UTF_8);
    }

    private static void setIdToken(String payloadJson) {
        String jwt = unsignedJwtJson(payloadJson);
        MockHttpServletRequest req = new MockHttpServletRequest();
        ServletRequestAttributes attrs = new ServletRequestAttributes(req);
        attrs.setAttribute(GeoStoreOAuthRestTemplate.ID_TOKEN_VALUE, jwt, 0);
        RequestContextHolder.setRequestAttributes(attrs);
    }

    /** Dummy implementation of UserGroupService for testing purposes. */
    private static class DummyUserGroupService implements UserGroupService {
        private final Map<String, UserGroup> groupsByName = new HashMap<>();
        private final Map<Long, UserGroup> groupsById = new HashMap<>();
        private long nextId = 1;

        @Override
        public UserGroup get(String groupName) {
            return groupsByName.get(groupName);
        }

        @Override
        public long getCount(String nameLike, boolean all) {
            return groupsByName.size();
        }

        @Override
        public long getCount(User authUser, String nameLike, boolean all) {
            return groupsByName.size();
        }

        @Override
        public void updateAttributes(long id, List<UserGroupAttribute> attributes)
                throws NotFoundServiceEx {
            UserGroup g = groupsById.get(id);
            if (g == null) throw new NotFoundServiceEx("group not found");
            g.setAttributes(new ArrayList<>(attributes));
            groupsByName.put(g.getGroupName(), g);
        }

        @Override
        public long update(UserGroup group) { // simple upsert
            if (group.getId() == null) {
                group.setId(nextId++);
            }
            groupsById.put(group.getId(), group);
            groupsByName.put(group.getGroupName(), group);
            return group.getId();
        }

        @Override
        public Collection<UserGroup> findByAttribute(
                String name, List<String> values, boolean ignoreCase) {
            return List.of();
        }

        @Override
        public UserGroup get(long id) {
            return groupsById.get(id);
        }

        @Override
        public List<ShortResource> updateSecurityRules(
                Long groupId, List<Long> resourcesToSet, boolean canRead, boolean canWrite) {
            return List.of();
        }

        @Override
        public boolean insertSpecialUsersGroups() {
            return false;
        }

        @Override
        public boolean removeSpecialUsersGroups() {
            return false;
        }

        @Override
        public long insert(UserGroup group) {
            if (group.getId() == null) group.setId(nextId++);
            groupsById.put(group.getId(), group);
            groupsByName.put(group.getGroupName(), group);
            return group.getId();
        }

        @Override
        public boolean delete(long id) {
            return groupsById.remove(id) != null;
        }

        @Override
        public void assignUserGroup(long userId, long groupId) throws NotFoundServiceEx {
            UserGroup g = groupsById.get(groupId);
            if (g == null) throw new NotFoundServiceEx("group not found");
            if (g.getUsers() == null) g.setUsers(new ArrayList<>());
            // naive lookup: ensure a user placeholder is present
            User u =
                    g.getUsers().stream().filter(x -> x.getId() == userId).findFirst().orElse(null);
            if (u == null) {
                u = new User();
                u.setId(userId);
                u.setGroups(new HashSet<>());
                u.setEnabled(true);
                u.setName("u" + userId);
                g.getUsers().add(u);
            }
            u.getGroups().add(g);
        }

        @Override
        public void deassignUserGroup(long userId, long groupId) throws NotFoundServiceEx {
            UserGroup g = groupsById.get(groupId);
            if (g == null) throw new NotFoundServiceEx("group not found");
            if (g.getUsers() != null) {
                g.getUsers()
                        .removeIf(
                                u -> {
                                    if (u.getId() == userId) {
                                        if (u.getGroups() != null) u.getGroups().remove(g);
                                        return true;
                                    }
                                    return false;
                                });
            }
        }

        @Override
        public List<UserGroup> getAllAllowed(
                User user, Integer page, Integer entries, String nameLike, boolean all) {
            return new ArrayList<>(groupsByName.values());
        }

        @Override
        public List<UserGroup> getAll(Integer page, Integer entries) {
            return new ArrayList<>(groupsByName.values());
        }

        @Override
        public List<UserGroup> getAll(Integer page, Integer entries, String nameLike, boolean all) {
            return new ArrayList<>(groupsByName.values());
        }
    }
}
