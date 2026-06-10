/* ====================================================================
 *
 * Copyright (C) 2025 GeoSolutions S.A.S.
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

import static org.junit.jupiter.api.Assertions.*;

import it.geosolutions.geostore.services.rest.security.IdPConfiguration;
import it.geosolutions.geostore.services.rest.security.RestAuthenticationEntryPoint;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.OpenIdConnectConfiguration;
import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Tests that the OIDC callback surfaces the real authentication failure cause (recorded by the
 * OAuth2 filter in the {@link RestAuthenticationEntryPoint#OAUTH2_AUTH_ERROR_KEY} request
 * attribute) instead of a generic "no access token found" message.
 */
public class Oauth2LoginServiceCallbackTest {

    private Oauth2LoginService loginService;
    private OpenIdConnectConfiguration configuration;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        loginService = new Oauth2LoginService() {};
        configuration = new OpenIdConnectConfiguration();
        configuration.setEnabled(true);
        configuration.setInternalRedirectUri("http://localhost:8081/");
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private Response callbackWithoutToken() {
        try (MockedStatic<GeoStoreContext> ctx = Mockito.mockStatic(GeoStoreContext.class)) {
            ctx.when(() -> GeoStoreContext.bean("oidcOAuth2Config", IdPConfiguration.class))
                    .thenReturn(configuration);
            return loginService.buildCallbackResponse(response, null, null, "oidc");
        }
    }

    @Test
    void surfacesRecordedFailureCause() {
        request.setAttribute(
                RestAuthenticationEntryPoint.OAUTH2_AUTH_ERROR_KEY,
                "The identity provider rejected the token validation request with status 401");

        Response result = callbackWithoutToken();

        assertEquals(500, result.getStatus());
        String body = String.valueOf(result.getEntity());
        assertTrue(body.contains("provider 'oidc'"), "body should name the provider: " + body);
        assertTrue(
                body.contains("rejected the token validation request with status 401"),
                "body should contain the recorded cause: " + body);
    }

    @Test
    void fallsBackToGenericMessageWhenNoCauseRecorded() {
        Response result = callbackWithoutToken();

        assertEquals(500, result.getStatus());
        String body = String.valueOf(result.getEntity());
        assertTrue(
                body.contains("no access token was returned by the identity provider"),
                "body should contain the generic message: " + body);
    }

    @Test
    void successfulCallbackStillRedirects() {
        try (MockedStatic<GeoStoreContext> ctx = Mockito.mockStatic(GeoStoreContext.class)) {
            ctx.when(() -> GeoStoreContext.bean("oidcOAuth2Config", IdPConfiguration.class))
                    .thenReturn(configuration);
            ctx.when(() -> GeoStoreContext.bean(TokenStorage.class))
                    .thenReturn(new InMemoryTokenStorage());

            Response result =
                    loginService.buildCallbackResponse(response, "the-access-token", null, "oidc");

            assertEquals(302, result.getStatus());
        }
    }
}
