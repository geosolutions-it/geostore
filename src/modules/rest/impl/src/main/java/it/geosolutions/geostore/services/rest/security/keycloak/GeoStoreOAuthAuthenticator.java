package it.geosolutions.geostore.services.rest.security.keycloak;

import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.OAuthRequestAuthenticator;
import org.keycloak.adapters.OIDCAuthenticationError;
import org.keycloak.adapters.RequestAuthenticator;
import org.keycloak.adapters.spi.AdapterSessionStore;
import org.keycloak.adapters.spi.AuthChallenge;
import org.keycloak.adapters.spi.HttpFacade;
import org.keycloak.adapters.springsecurity.KeycloakConfiguration;

/**
 * Custom OAuthAuthenticator.
 */
public class GeoStoreOAuthAuthenticator extends OAuthRequestAuthenticator {

    public GeoStoreOAuthAuthenticator(RequestAuthenticator requestAuthenticator, HttpFacade facade, KeycloakDeployment deployment, int sslRedirectPort, AdapterSessionStore tokenStore) {
        super(requestAuthenticator, facade, deployment, sslRedirectPort, tokenStore);
    }

    @Override
    protected AuthChallenge loginRedirect() {
        final String state = getStateCode();
        final String redirect =  getRedirectUri(state);
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
    protected String getRequestUrl() {
        KeyCloakConfiguration configuration=GeoStoreContext.bean(KeyCloakConfiguration.class);
        String redirectUri=configuration.getRedirectUri();
        if (redirectUri!=null) return redirectUri;
        return super.getRequestUrl();
    }
}
