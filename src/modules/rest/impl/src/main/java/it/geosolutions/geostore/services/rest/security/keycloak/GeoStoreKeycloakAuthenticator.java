package it.geosolutions.geostore.services.rest.security.keycloak;

import org.keycloak.adapters.AdapterTokenStore;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.OAuthRequestAuthenticator;
import org.keycloak.adapters.spi.HttpFacade;
import org.keycloak.adapters.springsecurity.authentication.SpringSecurityRequestAuthenticator;

import javax.servlet.http.HttpServletRequest;

/**
 * Custom {@link SpringSecurityRequestAuthenticator}. Takes care of performing the various authentication step against Keycloak.
 */
public class GeoStoreKeycloakAuthenticator extends SpringSecurityRequestAuthenticator {
    /**
     * Creates a new Spring Security request authenticator.
     *
     * @param facade          the current <code>HttpFacade</code> (required)
     * @param request         the current <code>HttpServletRequest</code> (required)
     * @param deployment      the <code>KeycloakDeployment</code> (required)
     * @param tokenStore      the <cdoe>AdapterTokenStore</cdoe> (required)
     * @param sslRedirectPort the SSL redirect port (required)
     */
    public GeoStoreKeycloakAuthenticator(HttpFacade facade, HttpServletRequest request, KeycloakDeployment deployment, AdapterTokenStore tokenStore, int sslRedirectPort) {
        super(facade, request, deployment, tokenStore, sslRedirectPort);
    }

    @Override
    protected OAuthRequestAuthenticator createOAuthAuthenticator() {
        return new GeoStoreOAuthAuthenticator(this, facade, deployment, sslRedirectPort, tokenStore);
    }
}
