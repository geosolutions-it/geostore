package it.geosolutions.geostore.rest.security.oauth2;

import it.geosolutions.geostore.services.rest.security.oauth2.AccessCookie;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import org.junit.After;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.REFRESH_TOKEN_PARAM;
import static org.junit.Assert.assertEquals;

public class OAuthLoginRestTest {

    private SetConfOAuthLoginRest SetConfOAuthLoginRest =new SetConfOAuthLoginRest();
    @Test
    public void testLogin(){
        MockHttpServletRequest request=new MockHttpServletRequest();
        MockHttpServletResponse response=new MockHttpServletResponse();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request,response);
        RequestContextHolder.setRequestAttributes(attributes);
        OAuth2Configuration configuration=new OAuth2Configuration();
        configuration.setScopes("openid");
        configuration.setClientId("mockClientId");
        configuration.setAuthorizationUri("http://localhost:8080/authorization");
        SetConfOAuthLoginRest.setConfiguration(configuration);
        SetConfOAuthLoginRest.login("mock");
        assertEquals(302,response.getStatus());
        assertEquals("http://localhost:8080/authorization?response_type=code&client_id=mockClientId&scope=openid&redirect_uri=null",response.getRedirectedUrl());
    }

    @Test
    public void testCallback(){

        ServletRequestAttributes attributes = new ServletRequestAttributes(new MockHttpServletRequest(),new MockHttpServletResponse());
        attributes.setAttribute(REFRESH_TOKEN_PARAM,"mockRefreshToken",0);
        attributes.setAttribute(ACCESS_TOKEN_PARAM,"mockAccessToken",0);
        RequestContextHolder.setRequestAttributes(attributes);
        OAuth2Configuration configuration=new OAuth2Configuration();
        configuration.setInternalRedirectUri("http://localhost:8080/geostore/redirect");
        SetConfOAuthLoginRest.setConfiguration(configuration);
        Response response= SetConfOAuthLoginRest.callback("mock");
        assertEquals(302,response.getStatus());
        List<Object> cookies=response.getMetadata().get("Set-Cookie");
        cookies=cookies.stream().filter(o->((AccessCookie)o).getName().equals(ACCESS_TOKEN_PARAM)|| ((AccessCookie)o).getName().equals(REFRESH_TOKEN_PARAM)).collect(Collectors.toList());
        assertEquals(2,cookies.size());
        assertEquals("http://localhost:8080/geostore/redirect",response.getHeaderString("Location"));
    }

    @After
    public void afterTest(){
        RequestContextHolder.resetRequestAttributes();
    }
}
