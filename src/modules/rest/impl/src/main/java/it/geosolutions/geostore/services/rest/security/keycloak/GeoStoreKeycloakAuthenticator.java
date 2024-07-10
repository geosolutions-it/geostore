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

import javax.servlet.http.HttpServletRequest;
import org.keycloak.adapters.AdapterTokenStore;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.OAuthRequestAuthenticator;
import org.keycloak.adapters.spi.HttpFacade;
import org.keycloak.adapters.springsecurity.authentication.SpringSecurityRequestAuthenticator;

/**
 * Custom {@link SpringSecurityRequestAuthenticator}. Takes care of performing the various
 * authentication step against Keycloak.
 */
public class GeoStoreKeycloakAuthenticator extends SpringSecurityRequestAuthenticator {
    /**
     * Creates a new Spring Security request authenticator.
     *
     * @param facade the current <code>HttpFacade</code> (required)
     * @param request the current <code>HttpServletRequest</code> (required)
     * @param deployment the <code>KeycloakDeployment</code> (required)
     * @param tokenStore the <cdoe>AdapterTokenStore</cdoe> (required)
     * @param sslRedirectPort the SSL redirect port (required)
     */
    public GeoStoreKeycloakAuthenticator(
            HttpFacade facade,
            HttpServletRequest request,
            KeycloakDeployment deployment,
            AdapterTokenStore tokenStore,
            int sslRedirectPort) {
        super(facade, request, deployment, tokenStore, sslRedirectPort);
    }

    @Override
    protected OAuthRequestAuthenticator createOAuthAuthenticator() {
        return new GeoStoreOAuthAuthenticator(
                this, facade, deployment, sslRedirectPort, tokenStore);
    }
}
