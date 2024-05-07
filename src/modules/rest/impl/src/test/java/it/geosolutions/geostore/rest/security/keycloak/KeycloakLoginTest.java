package it.geosolutions.geostore.rest.security.keycloak;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.geosolutions.geostore.services.rest.IdPLoginRest;
import it.geosolutions.geostore.services.rest.model.SessionToken;
import it.geosolutions.geostore.services.rest.security.IdPConfiguration;
import it.geosolutions.geostore.services.rest.security.keycloak.KeyCloakConfiguration;
import it.geosolutions.geostore.services.rest.security.keycloak.KeyCloakLoginService;
import it.geosolutions.geostore.services.rest.security.keycloak.KeycloakTokenDetails;
import it.geosolutions.geostore.services.rest.security.oauth2.IdPLoginRestImpl;
import it.geosolutions.geostore.services.rest.security.oauth2.InMemoryTokenStorage;
import it.geosolutions.geostore.services.rest.security.oauth2.TokenStorage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class KeycloakLoginTest extends KeycloakTestSupport {

    private IdPLoginRest loginRest;

    private Object key;

    @Before
    public void setUp() throws JsonProcessingException {
        setUpAdapter(AUTH_URL);
        KeycloakDeployment deployment = KeycloakDeploymentBuilder.build(adapterConfig);
        KeyCloakConfiguration configuration = createConfiguration();
        loginRest = new IdPLoginRestImpl();
        // autoregister to the loginRest object
        TokenStorage storage = new InMemoryTokenStorage();
        key = storage.buildTokenKey();
        SessionToken token = new SessionToken();
        token.setAccessToken(ACCESS_TOKEN_PARAM);
        token.setRefreshToken(REFRESH_TOKEN_PARAM);
        storage.saveToken(key, token);
        new KeyCloakLoginService(loginRest) {
            @Override
            protected TokenStorage tokenStorage() {
                return storage;
            }

            @Override
            protected IdPConfiguration configuration(String provider) {
                return configuration;
            }
        };
    }

    @Test
    public void testLoginEndpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request, response);
        AuthenticationEntryPoint entryPoint =
                new AuthenticationEntryPoint() {
                    @Override
                    public void commence(
                            HttpServletRequest request,
                            HttpServletResponse response,
                            AuthenticationException authException)
                            throws IOException, ServletException {
                        response.sendRedirect("/");
                    }
                };
        attributes.setAttribute("KEYCLOAK_REDIRECT", entryPoint, 0);
        RequestContextHolder.setRequestAttributes(attributes);
        loginRest.login("keycloak");
        assertEquals(302, response.getStatus());
        assertNotNull(response.getRedirectedUrl());
    }

    @Test
    public void testLoginEndpointInternalRedirect() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request, httpResponse);
        RequestContextHolder.setRequestAttributes(attributes);
        PreAuthenticatedAuthenticationToken authentication =
                new PreAuthenticatedAuthenticationToken("username", "", new ArrayList<>());
        KeycloakTokenDetails details =
                new KeycloakTokenDetails("accessToken", "refreshToken", 10202L);
        authentication.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        Response response = loginRest.callback("keycloak");
        assertEquals(302, response.getStatus());
        assertEquals("../../../", response.getHeaderString("Location"));
        MultivaluedMap<String, Object> meta = response.getMetadata();
        List<Object> cookies = meta.get("Set-Cookie");
        List<Object> tokenCookies =
                cookies.stream()
                        .filter(
                                c ->
                                        ((String) c).contains(AUTH_PROVIDER)
                                                || ((String) c).contains(TOKENS_KEY))
                        .collect(Collectors.toList());
        assertEquals(2, tokenCookies.size());
    }

    @Test
    public void testGetTokenByIdentifier() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request, httpResponse);
        RequestContextHolder.setRequestAttributes(attributes);
        PreAuthenticatedAuthenticationToken authentication =
                new PreAuthenticatedAuthenticationToken("username", "", new ArrayList<>());
        KeycloakTokenDetails details =
                new KeycloakTokenDetails("accessToken", "refreshToken", 10202L);
        authentication.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        SessionToken sessionToken =
                loginRest.getTokensByTokenIdentifier("keycloak", key.toString());
        assertEquals(ACCESS_TOKEN_PARAM, sessionToken.getAccessToken());
        assertEquals(REFRESH_TOKEN_PARAM, sessionToken.getRefreshToken());
    }

    @After
    public void afterTest() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }
}
