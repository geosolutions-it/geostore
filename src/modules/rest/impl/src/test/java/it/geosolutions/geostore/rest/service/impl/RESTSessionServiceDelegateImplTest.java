package it.geosolutions.geostore.rest.service.impl;

import it.geosolutions.geostore.services.InMemoryUserSessionServiceImpl;
import it.geosolutions.geostore.services.ServiceTestBase;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.RESTSessionService;
import it.geosolutions.geostore.services.rest.exception.ForbiddenErrorWebEx;
import it.geosolutions.geostore.services.rest.impl.RESTResourceServiceImpl;
import it.geosolutions.geostore.services.rest.impl.RESTSessionServiceImpl;
import it.geosolutions.geostore.services.rest.impl.SessionServiceDelegateImpl;
import it.geosolutions.geostore.services.rest.model.SessionToken;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.ws.rs.core.Response;
import java.text.ParseException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RESTSessionServiceDelegateImplTest {

    @Test
    public void testForbiddenOnRefreshTokenInvalidOrExpired() throws ParseException {
        MockHttpServletRequest request=new MockHttpServletRequest();
        MockHttpServletResponse response=new MockHttpServletResponse();
        SessionServiceDelegateImpl sessionServiceDelegate=new SessionServiceDelegateImpl();
        sessionServiceDelegate.setUserSessionService(new InMemoryUserSessionServiceImpl());
        ServletRequestAttributes attributes=new ServletRequestAttributes(request,response);
        RequestContextHolder.setRequestAttributes(attributes);
        String access_token="a_random_access_token";
        String refresh_token="a_random_refresh_token";
        SessionToken sessionToken=new SessionToken();
        sessionToken.setAccessToken(access_token);
        sessionToken.setRefreshToken(refresh_token);
        SessionToken result=null;
        int status=0;
        try{
            result=sessionServiceDelegate.refresh(sessionToken.getRefreshToken(),sessionToken.getAccessToken());
        } catch (ForbiddenErrorWebEx e){
            status=e.getResponse().getStatus();
        }
        assertNull(result);
        assertEquals(status, Response.Status.FORBIDDEN.getStatusCode());

    }
}
