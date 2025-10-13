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

import static it.geosolutions.geostore.services.rest.SessionServiceDelegate.PROVIDER_KEY;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import it.geosolutions.geostore.services.rest.RESTSessionService;
import it.geosolutions.geostore.services.rest.impl.RESTSessionServiceImpl;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.google.GoogleOAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.google.GoogleSessionServiceDelegate;
import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.After;
import org.junit.Before;
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

public class OAuth2SessionServiceTest {

    private static final String ID_TOKEN = "test.id.token";
    private static final String ACCESS_TOKEN = "access_token";

    @Before
    public void setup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @After
    public void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testLogout_withParamBearer_revokesAndClearsSessionAndCache() {
        GoogleOAuth2Configuration configuration = new GoogleOAuth2Configuration();
        configuration.setEnabled(true);
        configuration.setIdTokenUri("https://www.googleapis.com/oauth2/v3/certs");
        configuration.setRevokeEndpoint("http://google.foo/revoke");

        // principal + TokenDetails
        PreAuthenticatedAuthenticationToken authenticationToken =
                new PreAuthenticatedAuthenticationToken("user", "", new ArrayList<>());
        OAuth2AccessToken accessToken = new DefaultOAuth2AccessToken(ACCESS_TOKEN);
        TokenDetails details = Mockito.mock(TokenDetails.class);
        when(details.getProvider()).thenReturn("google");
        when(details.getAccessToken()).thenReturn(accessToken);
        when(details.getIdToken()).thenReturn(ID_TOKEN);
        authenticationToken.setDetails(details);

        // cache pre-populated with the token
        TokenAuthenticationCache cache = new TokenAuthenticationCache();
        cache.putCacheEntry(ACCESS_TOKEN, authenticationToken);

        // use a mocked restTemplate so we don't hit the network
        OAuth2RestTemplate restTemplate = Mockito.mock(OAuth2RestTemplate.class);
        when(restTemplate.getOAuth2ClientContext()).thenReturn(new DefaultOAuth2ClientContext());
        // if your revoke flow performs an HTTP call, you can uncomment and tailor:
        // when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
        //         .thenReturn(ResponseEntity.ok().build());

        // prepare GeoStoreContext static lookups
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        HashMap<Object, Object> configurations = new HashMap<>();
        configurations.put("googleOAuth2Config", configuration);

        try (MockedStatic<GeoStoreContext> ctx = Mockito.mockStatic(GeoStoreContext.class)) {
            ctx.when(() -> GeoStoreContext.beans(OAuth2Configuration.class))
                    .thenReturn(configurations);
            ctx.when(() -> GeoStoreContext.bean("googleOAuth2Config", OAuth2Configuration.class))
                    .thenReturn(configuration);
            ctx.when(() -> GeoStoreContext.bean("oAuth2Cache", TokenAuthenticationCache.class))
                    .thenReturn(cache);
            ctx.when(
                            () ->
                                    GeoStoreContext.bean(
                                            "googleOpenIdRestTemplate", OAuth2RestTemplate.class))
                    .thenReturn(restTemplate);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter(ACCESS_TOKEN_PARAM, ACCESS_TOKEN);
            request.setUserPrincipal(authenticationToken);
            MockHttpServletResponse response = new MockHttpServletResponse();

            ServletRequestAttributes attributes = new ServletRequestAttributes(request, response);
            attributes.setAttribute(PROVIDER_KEY, "google", 0);
            RequestContextHolder.setRequestAttributes(attributes);

            RESTSessionService sessionService = new RESTSessionServiceImpl();
            new GoogleSessionServiceDelegate(sessionService, null);

            // act
            sessionService.removeSession();

            // assert
            assertEquals(HttpStatus.OK_200, response.getStatus());
            assertNull(
                    "SecurityContext should be cleared",
                    SecurityContextHolder.getContext().getAuthentication());
            assertNull("request principal should be cleared", request.getUserPrincipal());
            assertNull("token should be evicted from cache", cache.get(ACCESS_TOKEN));
        }
    }

    @Test
    public void testLogout_withAuthorizationHeader_picksTokenAndClears() {
        GoogleOAuth2Configuration configuration = new GoogleOAuth2Configuration();
        configuration.setEnabled(true);
        configuration.setRevokeEndpoint("http://google.foo/revoke");

        PreAuthenticatedAuthenticationToken authenticationToken =
                new PreAuthenticatedAuthenticationToken("user", "", new ArrayList<>());
        OAuth2AccessToken accessToken = new DefaultOAuth2AccessToken(ACCESS_TOKEN);
        TokenDetails details = Mockito.mock(TokenDetails.class);
        when(details.getProvider()).thenReturn("google");
        when(details.getAccessToken()).thenReturn(accessToken);
        when(details.getIdToken()).thenReturn(ID_TOKEN);
        authenticationToken.setDetails(details);

        TokenAuthenticationCache cache = new TokenAuthenticationCache();
        cache.putCacheEntry(ACCESS_TOKEN, authenticationToken);

        OAuth2RestTemplate restTemplate = Mockito.mock(OAuth2RestTemplate.class);
        when(restTemplate.getOAuth2ClientContext()).thenReturn(new DefaultOAuth2ClientContext());

        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        HashMap<Object, Object> configurations = new HashMap<>();
        configurations.put("googleOAuth2Config", configuration);

        try (MockedStatic<GeoStoreContext> ctx = Mockito.mockStatic(GeoStoreContext.class)) {
            ctx.when(() -> GeoStoreContext.beans(OAuth2Configuration.class))
                    .thenReturn(configurations);
            ctx.when(() -> GeoStoreContext.bean("googleOAuth2Config", OAuth2Configuration.class))
                    .thenReturn(configuration);
            ctx.when(() -> GeoStoreContext.bean("oAuth2Cache", TokenAuthenticationCache.class))
                    .thenReturn(cache);
            ctx.when(
                            () ->
                                    GeoStoreContext.bean(
                                            "googleOpenIdRestTemplate", OAuth2RestTemplate.class))
                    .thenReturn(restTemplate);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + ACCESS_TOKEN);
            request.setUserPrincipal(authenticationToken);
            MockHttpServletResponse response = new MockHttpServletResponse();

            ServletRequestAttributes attributes = new ServletRequestAttributes(request, response);
            attributes.setAttribute(PROVIDER_KEY, "google", 0);
            RequestContextHolder.setRequestAttributes(attributes);

            RESTSessionService sessionService = new RESTSessionServiceImpl();
            new GoogleSessionServiceDelegate(sessionService, null);

            sessionService.removeSession();

            assertEquals(HttpStatus.OK_200, response.getStatus());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            assertNull(request.getUserPrincipal());
            assertNull(cache.get(ACCESS_TOKEN));
        }
    }

    @Test
    public void testLogout_noRevokeEndpoint_stillClearsLocally() {
        GoogleOAuth2Configuration configuration = new GoogleOAuth2Configuration();
        configuration.setEnabled(true);
        configuration.setRevokeEndpoint(null); // ensure missing remote revoke

        PreAuthenticatedAuthenticationToken authenticationToken =
                new PreAuthenticatedAuthenticationToken("user", "", new ArrayList<>());
        OAuth2AccessToken accessToken = new DefaultOAuth2AccessToken(ACCESS_TOKEN);
        TokenDetails details = Mockito.mock(TokenDetails.class);
        when(details.getProvider()).thenReturn("google");
        when(details.getAccessToken()).thenReturn(accessToken);
        when(details.getIdToken()).thenReturn(ID_TOKEN);
        authenticationToken.setDetails(details);

        TokenAuthenticationCache cache = new TokenAuthenticationCache();
        cache.putCacheEntry(ACCESS_TOKEN, authenticationToken);

        OAuth2RestTemplate restTemplate = Mockito.mock(OAuth2RestTemplate.class);
        when(restTemplate.getOAuth2ClientContext()).thenReturn(new DefaultOAuth2ClientContext());

        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        HashMap<Object, Object> configurations = new HashMap<>();
        configurations.put("googleOAuth2Config", configuration);

        try (MockedStatic<GeoStoreContext> ctx = Mockito.mockStatic(GeoStoreContext.class)) {
            ctx.when(() -> GeoStoreContext.beans(OAuth2Configuration.class))
                    .thenReturn(configurations);
            ctx.when(() -> GeoStoreContext.bean("googleOAuth2Config", OAuth2Configuration.class))
                    .thenReturn(configuration);
            ctx.when(() -> GeoStoreContext.bean("oAuth2Cache", TokenAuthenticationCache.class))
                    .thenReturn(cache);
            ctx.when(
                            () ->
                                    GeoStoreContext.bean(
                                            "googleOpenIdRestTemplate", OAuth2RestTemplate.class))
                    .thenReturn(restTemplate);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter(ACCESS_TOKEN_PARAM, ACCESS_TOKEN);
            request.setUserPrincipal(authenticationToken);
            MockHttpServletResponse response = new MockHttpServletResponse();

            ServletRequestAttributes attributes = new ServletRequestAttributes(request, response);
            attributes.setAttribute(PROVIDER_KEY, "google", 0);
            RequestContextHolder.setRequestAttributes(attributes);

            RESTSessionService sessionService = new RESTSessionServiceImpl();
            new GoogleSessionServiceDelegate(sessionService, null);

            sessionService.removeSession();

            assertEquals(HttpStatus.OK_200, response.getStatus());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            assertNull(request.getUserPrincipal());
            assertNull("cache entry should still be removed locally", cache.get(ACCESS_TOKEN));
        }
    }

    @Test
    public void testLogout_providerMismatch_keepsOtherProvidersCache() {
        GoogleOAuth2Configuration configuration = new GoogleOAuth2Configuration();
        configuration.setEnabled(true);
        configuration.setRevokeEndpoint("http://google.foo/revoke");

        // token A (google) and token B (azure)
        PreAuthenticatedAuthenticationToken googleAuth =
                new PreAuthenticatedAuthenticationToken("user", "", new ArrayList<>());
        TokenDetails googleDetails = Mockito.mock(TokenDetails.class);
        when(googleDetails.getProvider()).thenReturn("google");
        when(googleDetails.getAccessToken()).thenReturn(new DefaultOAuth2AccessToken("tokenA"));
        when(googleDetails.getIdToken()).thenReturn(ID_TOKEN);
        googleAuth.setDetails(googleDetails);

        PreAuthenticatedAuthenticationToken azureAuth =
                new PreAuthenticatedAuthenticationToken("user2", "", new ArrayList<>());
        TokenDetails azureDetails = Mockito.mock(TokenDetails.class);
        when(azureDetails.getProvider()).thenReturn("azure");
        when(azureDetails.getAccessToken()).thenReturn(new DefaultOAuth2AccessToken("tokenB"));
        when(azureDetails.getIdToken()).thenReturn(ID_TOKEN);
        azureAuth.setDetails(azureDetails);

        TokenAuthenticationCache cache = new TokenAuthenticationCache();
        cache.putCacheEntry("tokenA", googleAuth);
        cache.putCacheEntry("tokenB", azureAuth);

        OAuth2RestTemplate restTemplate = Mockito.mock(OAuth2RestTemplate.class);
        when(restTemplate.getOAuth2ClientContext()).thenReturn(new DefaultOAuth2ClientContext());

        SecurityContextHolder.getContext().setAuthentication(googleAuth);
        HashMap<Object, Object> configurations = new HashMap<>();
        configurations.put("googleOAuth2Config", configuration);

        try (MockedStatic<GeoStoreContext> ctx = Mockito.mockStatic(GeoStoreContext.class)) {
            ctx.when(() -> GeoStoreContext.beans(OAuth2Configuration.class))
                    .thenReturn(configurations);
            ctx.when(() -> GeoStoreContext.bean("googleOAuth2Config", OAuth2Configuration.class))
                    .thenReturn(configuration);
            ctx.when(() -> GeoStoreContext.bean("oAuth2Cache", TokenAuthenticationCache.class))
                    .thenReturn(cache);
            ctx.when(
                            () ->
                                    GeoStoreContext.bean(
                                            "googleOpenIdRestTemplate", OAuth2RestTemplate.class))
                    .thenReturn(restTemplate);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter(ACCESS_TOKEN_PARAM, "tokenA");
            request.setUserPrincipal(googleAuth);
            MockHttpServletResponse response = new MockHttpServletResponse();

            ServletRequestAttributes attributes = new ServletRequestAttributes(request, response);
            attributes.setAttribute(PROVIDER_KEY, "google", 0);
            RequestContextHolder.setRequestAttributes(attributes);

            RESTSessionService sessionService = new RESTSessionServiceImpl();
            new GoogleSessionServiceDelegate(sessionService, null);

            sessionService.removeSession();

            assertEquals(HttpStatus.OK_200, response.getStatus());
            assertNull("google entry evicted", cache.get("tokenA"));
            assertNotNull("azure entry untouched", cache.get("tokenB"));
        }
    }
}
