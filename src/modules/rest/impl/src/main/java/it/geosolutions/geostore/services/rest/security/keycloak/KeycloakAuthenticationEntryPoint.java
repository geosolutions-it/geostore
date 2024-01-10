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

import org.keycloak.adapters.spi.AuthChallenge;
import org.keycloak.adapters.springsecurity.facade.SimpleHttpFacade;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * AuthenticationEntryPoint that execute the Keycloak challenge and redirect to the Keycloak login page.
 */
class KeycloakAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthChallenge challenge;

    KeycloakAuthenticationEntryPoint(AuthChallenge challenge) {
        this.challenge = challenge;
    }


    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        if (challenge == null)
            throw new RuntimeException("Keycloak config is bearer only. No redirect to authorization page can be performed.");
        challenge.challenge(new SimpleHttpFacade(request, response));
        response.sendRedirect(response.getHeader("Location"));
    }
}