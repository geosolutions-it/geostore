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

import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.spi.HttpFacade;
import org.keycloak.common.util.KeycloakUriBuilder;
import org.keycloak.constants.AdapterConstants;


/**
 * Utility class that provides method to update the Keycloak Adapter cookie.
 */
class KeycloakCookieUtils {

    private static final String SEPARATOR = "___";

    static void setTokenCookie(KeycloakDeployment deployment, HttpFacade facade, KeycloakTokenDetails tokenDetails) {
        String accessToken = tokenDetails.getAccessToken();
        String idToken = tokenDetails.getIdToken();
        String refreshToken = tokenDetails.getRefreshToken();
        String cookie = accessToken + SEPARATOR +
                idToken + SEPARATOR +
                refreshToken;

        String cookiePath = getCookiePath(deployment, facade);
        // forces the expiration of the old keycloak cookie after refresh token. Keycloak doesn't do it for us.
        facade.getResponse().setCookie(AdapterConstants.KEYCLOAK_ADAPTER_STATE_COOKIE, cookie, cookiePath, null, 0, deployment.getSslRequired().isRequired(facade.getRequest().getRemoteAddr()), true);
    }

    static String getCookiePath(KeycloakDeployment deployment, HttpFacade facade) {
        String path = deployment.getAdapterStateCookiePath() == null ? "" : deployment.getAdapterStateCookiePath().trim();
        if (path.startsWith("/")) {
            return path;
        }
        String contextPath = getContextPath(facade);
        StringBuilder cookiePath = new StringBuilder(contextPath);
        if (!contextPath.endsWith("/") && !path.isEmpty()) {
            cookiePath.append("/");
        }
        return cookiePath.append(path).toString();
    }

    static String getContextPath(HttpFacade facade) {
        String uri = facade.getRequest().getURI();
        String path = KeycloakUriBuilder.fromUri(uri).getPath();
        if (path == null || path.isEmpty()) {
            return "/";
        }
        int index = path.indexOf("/", 1);
        return index == -1 ? path : path.substring(0, index);
    }
}
