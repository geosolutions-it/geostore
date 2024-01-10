/* ====================================================================
 *
 * Copyright (C) 2022 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.rest.security.oauth2;

import it.geosolutions.geostore.services.rest.RESTSessionService;
import it.geosolutions.geostore.services.rest.impl.RESTSessionServiceImpl;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreOAuthRestTemplate;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.TokenDetails;
import it.geosolutions.geostore.services.rest.security.oauth2.google.GoogleOAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.google.GoogleSessionServiceDelegate;
import it.geosolutions.geostore.services.rest.security.oauth2.google.OAuthGoogleSecurityConfiguration;
import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.DefaultOAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import wiremock.org.eclipse.jetty.http.HttpStatus;

import java.util.ArrayList;

import static it.geosolutions.geostore.services.rest.SessionServiceDelegate.PROVIDER_KEY;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

public class OAuth2SessionServiceTest {

    private static final String ID_TOKEN = "test.id.token";

    private static final String ACCESS_TOKEN = "access_token";

    @Test
    public void testLogout() {
        GoogleOAuth2Configuration configuration = new GoogleOAuth2Configuration();
        configuration.setIdTokenUri("https://www.googleapis.com/oauth2/v3/certs");
        PreAuthenticatedAuthenticationToken authenticationToken = new PreAuthenticatedAuthenticationToken("user", "", new ArrayList<>());
        OAuth2AccessToken accessToken = new DefaultOAuth2AccessToken(ACCESS_TOKEN);
        TokenDetails details = Mockito.mock(TokenDetails.class);
        when(details.getProvider()).thenReturn("google");
        when(details.getAccessToken()).thenReturn(accessToken);
        when(details.getIdToken()).thenReturn(ID_TOKEN);
        authenticationToken.setDetails(details);
        TokenAuthenticationCache cache = new TokenAuthenticationCache();
        cache.putCacheEntry(ACCESS_TOKEN, authenticationToken);
        OAuth2RestTemplate restTemplate = new GeoStoreOAuthRestTemplate(new OAuthGoogleSecurityConfiguration().resourceDetails(), new DefaultOAuth2ClientContext(), configuration);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        try (MockedStatic<GeoStoreContext> utilities = Mockito.mockStatic(GeoStoreContext.class)) {
            utilities.when(() -> GeoStoreContext.bean("googleOAuth2Config", OAuth2Configuration.class)).thenReturn(configuration);
            utilities.when(() -> GeoStoreContext.bean("oAuth2Cache", TokenAuthenticationCache.class)).thenReturn(cache);
            utilities.when(() -> GeoStoreContext.bean("googleOpenIdRestTemplate", OAuth2RestTemplate.class)).thenReturn(restTemplate);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter(ACCESS_TOKEN_PARAM, ACCESS_TOKEN);
            // test request.logout();
            request.setUserPrincipal(authenticationToken);
            MockHttpServletResponse response = new MockHttpServletResponse();
            ServletRequestAttributes attributes = new ServletRequestAttributes(request, response);
            attributes.setAttribute(PROVIDER_KEY, "google", 0);
            RequestContextHolder.setRequestAttributes(attributes);
            RESTSessionService sessionService = new RESTSessionServiceImpl();
            new GoogleSessionServiceDelegate(sessionService, null);

            // start the test
            sessionService.removeSession();
            assertEquals(response.getStatus(), HttpStatus.OK_200);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            assertNull(request.getUserPrincipal());
        }
    }
}
