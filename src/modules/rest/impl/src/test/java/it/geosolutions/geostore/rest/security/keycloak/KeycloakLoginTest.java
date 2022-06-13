package it.geosolutions.geostore.rest.security.keycloak;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.geosolutions.geostore.services.rest.IdPLoginRest;
import it.geosolutions.geostore.services.rest.security.IdPConfiguration;
import it.geosolutions.geostore.services.rest.security.keycloak.KeyCloakConfiguration;
import it.geosolutions.geostore.services.rest.security.keycloak.KeyCloakHelper;
import it.geosolutions.geostore.services.rest.security.keycloak.KeyCloakLoginService;
import it.geosolutions.geostore.services.rest.security.keycloak.KeycloakTokenDetails;
import it.geosolutions.geostore.services.rest.security.oauth2.AccessCookie;
import it.geosolutions.geostore.services.rest.security.oauth2.IdPLoginRestImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.REFRESH_TOKEN_PARAM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class KeycloakLoginTest extends KeycloakTestSupport {

    private IdPLoginRest loginRest;

    @Before
    public void setUp() throws JsonProcessingException {
        setUpAdapter(AUTH_URL);
        KeycloakDeployment deployment =
                KeycloakDeploymentBuilder.build(adapterConfig);
        AdapterDeploymentContext context= new AdapterDeploymentContext(deployment);
        KeyCloakConfiguration configuration=createConfiguration();
        loginRest=new IdPLoginRestImpl();
        // autoregister to the loginRest object
        new KeyCloakLoginService(loginRest){
            @Override
            protected IdPConfiguration configuration(String provider) {
                return configuration;
            }
        };
    }

    @Test
    public void testLoginEndpoint(){
        MockHttpServletRequest request=new MockHttpServletRequest();
        MockHttpServletResponse response=new MockHttpServletResponse();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request,response);
        AuthenticationEntryPoint entryPoint=new AuthenticationEntryPoint() {
            @Override
            public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
                response.sendRedirect("/");
            }
        };
        attributes.setAttribute("KEYCLOAK_REDIRECT",entryPoint,0);
        RequestContextHolder.setRequestAttributes(attributes);
        loginRest.login("keycloak");
        assertEquals(302,response.getStatus());
        assertNotNull(response.getRedirectedUrl());
    }

    @Test
    public void testLoginEndpointInternalRedirect(){
        MockHttpServletRequest request=new MockHttpServletRequest();
        MockHttpServletResponse response=new MockHttpServletResponse();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request,response);
        RequestContextHolder.setRequestAttributes(attributes);
        PreAuthenticatedAuthenticationToken authentication=new PreAuthenticatedAuthenticationToken("username","", new ArrayList<>());
        KeycloakTokenDetails details=new KeycloakTokenDetails("accessToken","refreshToken",10202l);
        authentication.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        loginRest.login("keycloak");
        assertEquals(302,response.getStatus());
        assertEquals("../../../",response.getRedirectedUrl());
        List<Cookie> cookies= Stream.of(response.getCookies())
                .filter(c->c.getName().equals(ACCESS_TOKEN_PARAM) || c.getName().equals(REFRESH_TOKEN_PARAM))
                .collect(Collectors.toList());
        assertEquals(2,cookies.size());
    }

    @After
    public void afterTest(){
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }
}
