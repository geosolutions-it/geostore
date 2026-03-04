/* ====================================================================
 *
 * Copyright (C) 2024-2025 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.enancher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.OpenIdConnectConfiguration;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.token.DefaultAccessTokenRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class PKCERequestEnhancerTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testEnhanceAddsCodeVerifierFromRequest() {
        OpenIdConnectConfiguration config = new OpenIdConnectConfiguration();
        config.setUsePKCE(true);
        config.setSendClientSecret(false);

        PKCERequestEnhancer enhancer = new PKCERequestEnhancer(config);

        DefaultAccessTokenRequest request = new DefaultAccessTokenRequest();
        request.put(
                PkceParameterNames.CODE_VERIFIER, Collections.singletonList("test-verifier-42"));

        OAuth2ProtectedResourceDetails resource = mock(OAuth2ProtectedResourceDetails.class);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();

        enhancer.enhance(request, resource, form, headers);

        assertTrue(form.containsKey(PkceParameterNames.CODE_VERIFIER));
        assertEquals("test-verifier-42", form.getFirst(PkceParameterNames.CODE_VERIFIER));
        assertFalse(form.containsKey("client_secret"), "client_secret should not be added");
    }

    @Test
    public void testEnhanceAddsCodeVerifierFromSession() {
        OpenIdConnectConfiguration config = new OpenIdConnectConfiguration();
        config.setUsePKCE(true);
        config.setSendClientSecret(false);

        PKCERequestEnhancer enhancer = new PKCERequestEnhancer(config);

        // Set up mock HTTP session with stored verifier
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest
                .getSession(true)
                .setAttribute(
                        OpenIdConnectConfiguration.PKCE_CODE_VERIFIER_SESSION_ATTR,
                        "session-verifier-99");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

        DefaultAccessTokenRequest request = new DefaultAccessTokenRequest();
        OAuth2ProtectedResourceDetails resource = mock(OAuth2ProtectedResourceDetails.class);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();

        enhancer.enhance(request, resource, form, headers);

        assertTrue(form.containsKey(PkceParameterNames.CODE_VERIFIER));
        assertEquals("session-verifier-99", form.getFirst(PkceParameterNames.CODE_VERIFIER));

        // Verify session attribute was removed after retrieval
        assertNull(
                httpRequest
                        .getSession()
                        .getAttribute(OpenIdConnectConfiguration.PKCE_CODE_VERIFIER_SESSION_ATTR),
                "Session verifier should be consumed (removed) after use");
    }

    @Test
    public void testEnhanceAddsClientSecretWhenEnabled() {
        OpenIdConnectConfiguration config = new OpenIdConnectConfiguration();
        config.setUsePKCE(true);
        config.setSendClientSecret(true);

        PKCERequestEnhancer enhancer = new PKCERequestEnhancer(config);

        DefaultAccessTokenRequest request = new DefaultAccessTokenRequest();
        request.put(PkceParameterNames.CODE_VERIFIER, Collections.singletonList("verifier-abc"));

        OAuth2ProtectedResourceDetails resource = mock(OAuth2ProtectedResourceDetails.class);
        when(resource.getClientSecret()).thenReturn("dual-secret");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();

        enhancer.enhance(request, resource, form, headers);

        assertTrue(form.containsKey("client_secret"), "client_secret should be added");
        assertEquals("dual-secret", form.getFirst("client_secret"));
        assertTrue(form.containsKey(PkceParameterNames.CODE_VERIFIER));
        assertEquals("verifier-abc", form.getFirst(PkceParameterNames.CODE_VERIFIER));
    }

    @Test
    public void testEnhanceSkipsPkceWhenDisabled() {
        OpenIdConnectConfiguration config = new OpenIdConnectConfiguration();
        config.setUsePKCE(false);
        config.setSendClientSecret(false);

        PKCERequestEnhancer enhancer = new PKCERequestEnhancer(config);

        DefaultAccessTokenRequest request = new DefaultAccessTokenRequest();
        OAuth2ProtectedResourceDetails resource = mock(OAuth2ProtectedResourceDetails.class);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();

        enhancer.enhance(request, resource, form, headers);

        assertFalse(form.containsKey(PkceParameterNames.CODE_VERIFIER));
        assertFalse(form.containsKey("client_secret"));
    }
}
