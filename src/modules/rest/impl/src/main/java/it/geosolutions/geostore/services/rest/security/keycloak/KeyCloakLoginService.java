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
package it.geosolutions.geostore.services.rest.security.keycloak;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.getAccessToken;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.getRefreshAccessToken;

import it.geosolutions.geostore.services.rest.IdPLoginRest;
import it.geosolutions.geostore.services.rest.security.oauth2.Oauth2LoginService;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Keycloak implementation for a LoginService. Since keycloak redirects to the url from which the
 * call to the authorization page was issued no internal redirect is really performed here.
 */
public class KeyCloakLoginService extends Oauth2LoginService {

    private static final Logger LOGGER = LogManager.getLogger(KeyCloakLoginService.class);

    static String KEYCLOAK_REDIRECT = "KEYCLOAK_REDIRECT";

    public KeyCloakLoginService(IdPLoginRest loginRest) {
        loginRest.registerService("keycloak", this);
    }

    @Override
    public void doLogin(HttpServletRequest request, HttpServletResponse response, String provider) {
        AuthenticationEntryPoint challenge =
                (AuthenticationEntryPoint)
                        RequestContextHolder.getRequestAttributes()
                                .getAttribute(KEYCLOAK_REDIRECT, 0);
        if (challenge != null) {
            try {
                challenge.commence(request, response, null);
            } catch (Exception e) {
                LOGGER.error("Error while redirecting to Keycloak authorization.", e);
                throw new RuntimeException(e);
            }
        } else {
            try {
                response.sendRedirect(configuration(provider).getInternalRedirectUri());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Response doInternalRedirect(
            HttpServletRequest request, HttpServletResponse response, String provider) {
        String token;
        String refreshToken;
        KeycloakTokenDetails details = getDetails();
        if (details != null) {
            token = details.getAccessToken();
            refreshToken = details.getRefreshToken();
        } else {
            token = getAccessToken();
            refreshToken = getRefreshAccessToken();
        }
        return buildCallbackResponse(token, refreshToken, provider);
    }

    private KeycloakTokenDetails getDetails() {
        KeycloakTokenDetails result = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof KeycloakTokenDetails)
            result = (KeycloakTokenDetails) auth.getDetails();
        return result;
    }
}
