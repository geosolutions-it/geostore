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

import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.OAuthRequestAuthenticator;
import org.keycloak.adapters.OIDCAuthenticationError;
import org.keycloak.adapters.RequestAuthenticator;
import org.keycloak.adapters.spi.AdapterSessionStore;
import org.keycloak.adapters.spi.AuthChallenge;
import org.keycloak.adapters.spi.HttpFacade;


/**
 * Custom OAuthRequestAuthenticator. Used to force the redirect URI to the configured one.
 */
public class GeoStoreOAuthAuthenticator extends OAuthRequestAuthenticator {

    public GeoStoreOAuthAuthenticator(RequestAuthenticator requestAuthenticator, HttpFacade facade, KeycloakDeployment deployment, int sslRedirectPort, AdapterSessionStore tokenStore) {
        super(requestAuthenticator, facade, deployment, sslRedirectPort, tokenStore);
    }

    @Override
    protected AuthChallenge loginRedirect() {
        final String state = getStateCode();
        final String redirect = getRedirectUri(state);
        if (redirect == null) {
            return challenge(403, OIDCAuthenticationError.Reason.NO_REDIRECT_URI, null);
        }
        return new AuthChallenge() {

            @Override
            public int getResponseCode() {
                return 0;
            }

            @Override
            public boolean challenge(HttpFacade exchange) {
                tokenStore.saveRequest();
                exchange.getResponse().setStatus(302);
                // the default keycloak authenticator set the path to /
                // but this causes a bug for which the state cookie is overrided all the times by the keycloak
                // server. Here we set it to null.
                exchange.getResponse().setCookie(deployment.getStateCookieName(), state, null, null, -1, deployment.getSslRequired().isRequired(facade.getRequest().getRemoteAddr()), true);
                exchange.getResponse().setHeader("Location", redirect);
                return true;
            }
        };
    }

    @Override
    protected String stripOauthParametersFromRedirect() {
        String redirectUrl = super.stripOauthParametersFromRedirect();
        KeyCloakConfiguration configuration = GeoStoreContext.bean(KeyCloakConfiguration.class);
        Boolean forceRedirectURI = configuration.getForceConfiguredRedirectURI();
        if (forceRedirectURI)
            return configuration.getRedirectUri();
        return redirectUrl;
    }

    @Override
    protected String getRequestUrl() {
        KeyCloakConfiguration configuration = GeoStoreContext.bean(KeyCloakConfiguration.class);
        String redirectUri = configuration.getRedirectUri();
        if (redirectUri != null && !"".equals(redirectUri)) return redirectUri;
        return super.getRequestUrl();
    }
}
