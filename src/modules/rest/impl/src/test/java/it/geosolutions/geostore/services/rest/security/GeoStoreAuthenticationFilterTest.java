/*
 *  Copyright (C) 2015 GeoSolutions S.A.S.
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
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.security.oauth2.JWTHelper;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2GeoStoreAuthenticationFilter;
import it.geosolutions.geostore.services.rest.utils.MockedUserService;
import java.io.IOException;
import java.util.*;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Test class for GeoStore authentication filters.
 *
 * <p>This class contains tests for the header‐based authentication filter and tests for the OAuth2
 * filter, including verifying the new configuration option to remap the username based on idToken
 * claims and the new constraint enforcing uppercase group names with users being added to groups.
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

    /**
     * Test that verifies the new configuration option for username remapping.
     *
     * <p>This test creates a dummy OAuth2 filter that overrides the idToken decoding to return a
     * dummy JWTHelper. The dummy helper returns the original username for the "principal" claim and
     * a remapped username for the "unique" claim. The filter then should create a user with the
     * remapped name.
     */
    @Test
    public void testUsernameRemapping() throws Exception {
        final String ORIGINAL_USERNAME = "myuser";
        final String REMAPPED_USERNAME = "remappedUser";

        // Create a dummy OAuth2Configuration with the remapping properties.
        OAuth2Configuration config = new OAuth2Configuration();
        config.setPrincipalKey("principal");
        config.setUniqueUsername("unique");
        config.setBeanName("testBean");
        config.setAutoCreateUser(true);

        // Create an instance of the OAuth2 filter using an anonymous subclass.
        OAuth2GeoStoreAuthenticationFilter oauth2Filter =
                new OAuth2GeoStoreAuthenticationFilter(
                        /* tokenServices */ null,
                        /* restTemplate */ null,
                        config,
                        /* cache */ null) {

                    @Override
                    protected JWTHelper decodeAndValidateIdToken(String idToken) {
                        return new JWTHelper(idToken) {
                            @Override
                            public <T> T getClaim(String claimName, Class<T> clazz) {
                                if ("principal".equals(claimName)) {
                                    return (T) ORIGINAL_USERNAME;
                                }
                                if ("unique".equals(claimName)) {
                                    return (T) REMAPPED_USERNAME;
                                }
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
                        // No-op for testing.
                    }
                };

        HttpServletRequest req2 = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse resp2 = Mockito.mock(HttpServletResponse.class);

        PreAuthenticatedAuthenticationToken authToken =
                oauth2Filter.createPreAuthentication(ORIGINAL_USERNAME, req2, resp2);
        assertNotNull("Authentication token should not be null", authToken);

        User user = (User) authToken.getPrincipal();
        assertNotNull("User should not be null", user);
        assertEquals(
                "Username should be remapped to the unique claim value",
                REMAPPED_USERNAME,
                user.getName());
    }

    /**
     * Test that verifies group names are forced to uppercase and that the user is assigned to the
     * group.
     *
     * <p>This test creates a dummy OAuth2 filter that returns a dummy JWTHelper which, when asked
     * for the groups claim, returns a list with a single group name ("groupA"). With the
     * configuration set to force uppercase group names, the filter will use "GROUPA" for
     * lookup/insertion. We supply a dummy UserGroupService that holds groups in memory. The test
     * then verifies that the user gets assigned to the uppercase group.
     */
    @Test
    public void testGroupNamesUppercaseAndUserGroupAssignment() throws Exception {
        final String GROUP_FROM_TOKEN = "groupA";
        final String EXPECTED_GROUP = "GROUPA";

        // Create a dummy OAuth2Configuration with groupsClaim and groupNamesUppercase enabled.
        OAuth2Configuration config = new OAuth2Configuration();
        config.setGroupsClaim("groups");
        config.setBeanName("testBean");
        config.setAutoCreateUser(true);
        config.setGroupNamesUppercase(true);

        // Create a dummy UserGroupService that holds groups in memory.
        DummyUserGroupService dummyGroupService = new DummyUserGroupService();

        // Create an instance of the OAuth2 filter using an anonymous subclass.
        OAuth2GeoStoreAuthenticationFilter oauth2Filter =
                new OAuth2GeoStoreAuthenticationFilter(
                        /* tokenServices */ null,
                        /* restTemplate */ null,
                        config,
                        /* cache */ null) {

                    @Override
                    protected JWTHelper decodeAndValidateIdToken(String idToken) {
                        return new JWTHelper(idToken) {
                            @Override
                            public <T> List<T> getClaimAsList(String claimName, Class<T> clazz) {
                                if ("groups".equals(claimName)) {
                                    // Return a list containing the group name from the token.
                                    return (List<T>) Collections.singletonList(GROUP_FROM_TOKEN);
                                }
                                return Collections.emptyList();
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
                        // No-op for testing.
                    }
                };

        // Inject the dummy group service.
        oauth2Filter.setUserGroupService(dummyGroupService);

        // Create dummy HTTP request/response.
        HttpServletRequest req2 = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse resp2 = Mockito.mock(HttpServletResponse.class);

        // Call createPreAuthentication (which will invoke addAuthoritiesFromToken).
        PreAuthenticatedAuthenticationToken authToken =
                oauth2Filter.createPreAuthentication("anyuser", req2, resp2);
        assertNotNull("Authentication token should not be null", authToken);

        User user = (User) authToken.getPrincipal();
        assertNotNull("User should not be null", user);

        // Verify that the user has been assigned to the group with uppercase name.
        boolean found =
                user.getGroups().stream()
                        .anyMatch(group -> EXPECTED_GROUP.equals(group.getGroupName()));
        assertTrue("User should be assigned to group " + EXPECTED_GROUP, found);

        // Also verify that the dummy service holds the group under the uppercase key.
        UserGroup groupFromService = dummyGroupService.get(EXPECTED_GROUP);
        assertNotNull(
                "Dummy group service should contain group " + EXPECTED_GROUP, groupFromService);
    }

    private void checkUser(User user) {
        assertNotNull("User should not be null", user);
        assertEquals("User role should be USER", Role.USER, user.getRole());
        assertTrue("User groups should be empty", user.getGroups().isEmpty());
    }

    /** Dummy implementation of UserGroupService for testing purposes. */
    private static class DummyUserGroupService
            implements it.geosolutions.geostore.services.UserGroupService {
        private final Map<String, UserGroup> groupsByName = new HashMap<>();
        private final Map<Long, UserGroup> groupsById = new HashMap<>();
        private long nextId = 1;

        @Override
        public UserGroup get(String groupName) {
            return groupsByName.get(groupName);
        }

        @Override
        public long getCount(String nameLike, boolean all) throws BadRequestServiceEx {
            return 0;
        }

        @Override
        public long getCount(User authUser, String nameLike, boolean all)
                throws BadRequestServiceEx {
            return 0;
        }

        @Override
        public void updateAttributes(long id, List<UserGroupAttribute> attributes)
                throws NotFoundServiceEx {}

        @Override
        public long update(UserGroup group) throws NotFoundServiceEx, BadRequestServiceEx {
            return 0;
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
                Long groupId, List<Long> resourcesToSet, boolean canRead, boolean canWrite)
                throws NotFoundServiceEx, BadRequestServiceEx {
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
        public long insert(UserGroup group) throws BadRequestServiceEx {
            group.setId(nextId);
            groupsById.put(nextId, group);
            groupsByName.put(group.getGroupName(), group);
            return nextId++;
        }

        @Override
        public boolean delete(long id) throws NotFoundServiceEx, BadRequestServiceEx {
            return false;
        }

        @Override
        public void assignUserGroup(long userId, long groupId) throws NotFoundServiceEx {
            // For testing, we assume the assignment is always successful.
        }

        @Override
        public void deassignUserGroup(long userId, long groupId) throws NotFoundServiceEx {}

        @Override
        public List<UserGroup> getAllAllowed(
                User user, Integer page, Integer entries, String nameLike, boolean all)
                throws BadRequestServiceEx {
            return List.of();
        }

        @Override
        public List<UserGroup> getAll(Integer page, Integer entries) throws BadRequestServiceEx {
            return List.of();
        }

        @Override
        public List<UserGroup> getAll(Integer page, Integer entries, String nameLike, boolean all)
                throws BadRequestServiceEx {
            return List.of();
        }
    }
}
