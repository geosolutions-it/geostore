/* ====================================================================
 *
 * Copyright (C) 2024 GeoSolutions S.A.S.
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

import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.OpenIdConnectConfiguration;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.token.AccessTokenRequest;
import org.springframework.security.oauth2.client.token.RequestEnhancer;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Used to enhance Token Requests with previously generated code_verifier. */
public class PKCERequestEnhancer implements RequestEnhancer {

    private static final Logger LOGGER = LogManager.getLogger(PKCERequestEnhancer.class);

    private final OpenIdConnectConfiguration config;

    public PKCERequestEnhancer(OpenIdConnectConfiguration oidcConfig) {
        this.config = oidcConfig;
    }

    @Override
    public void enhance(
            AccessTokenRequest request,
            OAuth2ProtectedResourceDetails resource,
            MultiValueMap<String, String> form,
            HttpHeaders headers) {

        if (config.isSendClientSecret()) {
            form.put(
                    ClientSecretRequestEnhancer.CLIENT_SECRET,
                    Collections.singletonList(resource.getClientSecret()));
        }
        if (config.isUsePKCE()) {
            java.util.List<String> codeVerifier = request.get(PkceParameterNames.CODE_VERIFIER);
            if (codeVerifier == null || codeVerifier.isEmpty()) {
                // Retrieve from HTTP session (stored by the PKCE entry point)
                codeVerifier = retrieveVerifierFromSession();
            }
            if (codeVerifier != null && !codeVerifier.isEmpty()) {
                form.put(PkceParameterNames.CODE_VERIFIER, codeVerifier);
            }
        }
    }

    private java.util.List<String> retrieveVerifierFromSession() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest httpRequest = attrs.getRequest();
            HttpSession session = httpRequest.getSession(false);
            if (session == null) return null;
            String verifier =
                    (String)
                            session.getAttribute(
                                    OpenIdConnectConfiguration.PKCE_CODE_VERIFIER_SESSION_ATTR);
            if (verifier != null) {
                session.removeAttribute(OpenIdConnectConfiguration.PKCE_CODE_VERIFIER_SESSION_ATTR);
                return Collections.singletonList(verifier);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not retrieve PKCE code_verifier from session", e);
        }
        return null;
    }
}
