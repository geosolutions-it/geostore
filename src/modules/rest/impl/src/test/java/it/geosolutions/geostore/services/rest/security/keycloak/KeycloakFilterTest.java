package it.geosolutions.geostore.services.rest.security.keycloak;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.adapters.*;
import org.keycloak.adapters.rotation.AdapterTokenVerifier;
import org.keycloak.adapters.spi.AdapterSessionStore;
import org.keycloak.adapters.spi.HttpFacade;
import org.keycloak.adapters.springsecurity.authentication.SpringSecurityRequestAuthenticator;
import org.keycloak.adapters.springsecurity.facade.SimpleHttpFacade;
import org.keycloak.common.VerificationException;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class KeycloakFilterTest extends KeycloakTestSupport {

    private final MockUserGroupService groupService = new MockUserGroupService();
    private final MockUserService userService = new MockUserService();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    private static org.keycloak.representations.AccessToken verifyToken() {
        try {
            JWSInput jwsInput = new JWSInput(JWT_2018_2037);
            return jwsInput.readJsonContent(org.keycloak.representations.AccessToken.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void before() {
        setUpAdapter(AUTH_URL);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setRequestURI(APP_URL);
        request.setScheme("http");
        request.setServerPort(8080);
        request.setServerName("localhost");
        this.chain = new MockFilterChain();
        RequestAttributes requestAttributes = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(requestAttributes);
    }

    @Test
    public void testKeyCloakFilterRedirect() throws IOException, ServletException {
        KeyCloakConfiguration configuration = createConfiguration();
        KeyCloakFilter filter = createFilter(configuration);
        filter.doFilter(request, response, chain);
        Object redirect = request.getAttribute("KEYCLOAK_REDIRECT");
        assertTrue(redirect instanceof AuthenticationEntryPoint);
    }

    @Test
    public void testAuthentication() throws IOException, ServletException, VerificationException {
        KeyCloakConfiguration configuration = createConfiguration();
        AdapterConfig config = configuration.readAdapterConfig();
        config.setRealmKey(PUBLIC_KEY);
        ObjectMapper om = new ObjectMapper();
        String stringConfig = om.writeValueAsString(config);
        configuration.setJsonConfig(stringConfig);
        try (MockedStatic<AdapterTokenVerifier> utilities =
                Mockito.mockStatic(AdapterTokenVerifier.class)) {
            utilities
                    .when(
                            () ->
                                    AdapterTokenVerifier.verifyToken(
                                            eq(JWT_2018_2037), any(KeycloakDeployment.class)))
                    .thenReturn(verifyToken());
            TokenAuthenticationCache cache = new TokenAuthenticationCache();
            KeyCloakFilter filter = createFilter(configuration, cache);
            String auth_header = "bearer " + JWT_2018_2037;
            request.addHeader("AUTHORIZATION", auth_header);
            filter.doFilter(request, response, chain);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertTrue(authentication.getPrincipal() instanceof User);
            User user = (User) authentication.getPrincipal();
            Set<UserGroup> groupSet = user.getGroups();
            groupSet =
                    groupSet.stream()
                            .filter(g -> !g.getGroupName().equals("everyone"))
                            .collect(Collectors.toSet());
            for (UserGroup group : groupSet) {
                assertNotNull(groupService.get(group.getGroupName()));
            }
            assertTrue(authentication.getDetails() instanceof KeycloakTokenDetails);
            KeycloakTokenDetails tokenDetails = (KeycloakTokenDetails) authentication.getDetails();
            assertNotNull(cache.get(tokenDetails.getAccessToken()));
        }
    }

    @Test
    public void testAuthenticationFailure() throws IOException, ServletException {
        KeyCloakConfiguration configuration = createConfiguration();
        AdapterConfig config = configuration.readAdapterConfig();
        config.setRealmKey(PUBLIC_KEY);
        ObjectMapper om = new ObjectMapper();
        String stringConfig = om.writeValueAsString(config);
        configuration.setJsonConfig(stringConfig);
        TokenAuthenticationCache cache = new TokenAuthenticationCache();
        KeyCloakFilter filter = createFilter(configuration, cache);
        String auth_header = "bearer " + "wrong_token";
        request.addHeader("AUTHORIZATION", auth_header);
        filter.doFilter(request, response, chain);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
    }

    private KeyCloakFilter createFilter(KeyCloakConfiguration configuration) {
        return createFilter(configuration, new TokenAuthenticationCache());
    }

    private KeyCloakFilter createFilter(
            KeyCloakConfiguration configuration, TokenAuthenticationCache cache) {
        KeycloakDeployment deployment =
                KeycloakDeploymentBuilder.build(configuration.readAdapterConfig());
        AdapterDeploymentContext context = new AdapterDeploymentContext(deployment);
        KeyCloakHelper helper =
                new KeyCloakHelper(context) {
                    @Override
                    public RequestAuthenticator getAuthenticator(
                            HttpServletRequest request,
                            HttpServletResponse response,
                            KeycloakDeployment deployment) {
                        request = new KeyCloakRequestWrapper(request);
                        AdapterTokenStore tokenStore =
                                adapterTokenStoreFactory.createAdapterTokenStore(
                                        deployment, request, response);
                        SimpleHttpFacade simpleHttpFacade = new SimpleHttpFacade(request, response);
                        return new TestAuthenticator(
                                simpleHttpFacade, request, deployment, tokenStore, -1);
                    }
                };
        GeoStoreKeycloakAuthProvider authProvider = new GeoStoreKeycloakAuthProvider(configuration);
        authProvider.setUserService(userService);
        authProvider.setGroupService(groupService);
        return new KeyCloakFilter(helper, cache, configuration, authProvider);
    }

    @After
    public void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testDropUnMappedGroups() throws IOException, ServletException {
        KeyCloakConfiguration configuration = createConfiguration();
        configuration.setDropUnmapped(true);
        configuration.setGroupMappings("uma_authorization:TEST_GROUP");
        AdapterConfig config = configuration.readAdapterConfig();
        config.setRealmKey(PUBLIC_KEY);
        ObjectMapper om = new ObjectMapper();
        String stringConfig = om.writeValueAsString(config);
        configuration.setJsonConfig(stringConfig);
        try (MockedStatic<AdapterTokenVerifier> utilities =
                Mockito.mockStatic(AdapterTokenVerifier.class)) {
            utilities
                    .when(
                            () ->
                                    AdapterTokenVerifier.verifyToken(
                                            eq(JWT_2018_2037), any(KeycloakDeployment.class)))
                    .thenReturn(verifyToken());
            TokenAuthenticationCache cache = new TokenAuthenticationCache();
            KeyCloakFilter filter = createFilter(configuration, cache);
            String auth_header = "bearer " + JWT_2018_2037;
            request.addHeader("AUTHORIZATION", auth_header);
            filter.doFilter(request, response, chain);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertTrue(authentication.getPrincipal() instanceof User);
            User user = (User) authentication.getPrincipal();
            Set<UserGroup> groupSet = user.getGroups();
            assertEquals(2, groupSet.size());
            groupSet.stream().anyMatch(g -> g.getGroupName().equals("TEST_GROUP"));
            groupSet.stream().anyMatch(g -> g.getGroupName().equals("everyone"));
            assertTrue(authentication.getDetails() instanceof KeycloakTokenDetails);
            KeycloakTokenDetails tokenDetails = (KeycloakTokenDetails) authentication.getDetails();
            assertNotNull(cache.get(tokenDetails.getAccessToken()));
        }
    }

    private class TestOAuthAuthenticator extends GeoStoreOAuthAuthenticator {

        public TestOAuthAuthenticator(
                RequestAuthenticator requestAuthenticator,
                HttpFacade facade,
                KeycloakDeployment deployment,
                int sslRedirectPort,
                AdapterSessionStore tokenStore) {
            super(requestAuthenticator, facade, deployment, sslRedirectPort, tokenStore);
        }

        @Override
        protected String getRedirectUri(String state) {
            return AUTH_URL;
        }
    }

    private class TestAuthenticator extends SpringSecurityRequestAuthenticator {

        public TestAuthenticator(
                HttpFacade facade,
                HttpServletRequest request,
                KeycloakDeployment deployment,
                AdapterTokenStore tokenStore,
                int sslRedirectPort) {
            super(facade, request, deployment, tokenStore, sslRedirectPort);
        }

        @Override
        protected OAuthRequestAuthenticator createOAuthAuthenticator() {
            return new TestOAuthAuthenticator(
                    this, facade, deployment, sslRedirectPort, tokenStore);
        }
    }
}
