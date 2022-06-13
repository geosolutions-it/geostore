package it.geosolutions.geostore.services.rest.security.keycloak;

import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import org.apache.log4j.Logger;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.AdapterTokenStore;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.RequestAuthenticator;
import org.keycloak.adapters.spi.HttpFacade;
import org.keycloak.adapters.springsecurity.facade.SimpleHttpFacade;
import org.keycloak.adapters.springsecurity.token.SpringSecurityAdapterTokenStoreFactory;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.util.Http;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * This class provides some utility methods to deal with Keycloak Authentication.
 */
public class KeyCloakHelper {

    private final static Logger LOGGER = Logger.getLogger(KeycloakSessionServiceDelegate.class);


    protected final SpringSecurityAdapterTokenStoreFactory adapterTokenStoreFactory;
    protected AdapterDeploymentContext keycloakContext;

    public KeyCloakHelper(AdapterDeploymentContext keycloakContext){
        this.adapterTokenStoreFactory=new SpringSecurityAdapterTokenStoreFactory();
        this.keycloakContext=keycloakContext;
    }

    /**
     * @param request request.
     * @param response response.
     * @return return a KeycloakDeployment instance.
     */
    public KeycloakDeployment getDeployment(HttpServletRequest request,HttpServletResponse response){
        HttpFacade exchange = new SimpleHttpFacade(request, response);
        KeycloakDeployment deployment = keycloakContext.resolveDeployment(exchange);
        deployment.setDelegateBearerErrorResponseSending(true);
        return deployment;
    }

    /**
     * Return the request authenticator that will be used by the filter to perform the various authentication steps.
     * @param request the request.
     * @param response the response.
     * @param deployment the deployment instance.
     * @return the request authenticator.
     */
    public RequestAuthenticator getAuthenticator(HttpServletRequest request, HttpServletResponse response, KeycloakDeployment deployment){
        request =
                new KeyCloakRequestWrapper(request);
        AdapterTokenStore tokenStore =
                adapterTokenStoreFactory.createAdapterTokenStore(deployment, request,response);
        SimpleHttpFacade simpleHttpFacade=new SimpleHttpFacade(request,response);
        return
                new GeoStoreKeycloakAuthenticator(
                        simpleHttpFacade, request, deployment, tokenStore, -1);
    }

    /**
     * Issue a refresh token http call.
     * @param adapter the adapter config.
     * @param refreshToken the refresh token.
     * @return the AccessTokenResponse with the new tokens.
     */
    public AccessTokenResponse refreshToken(AdapterConfig adapter,String refreshToken){
        Configuration clientConf = getClientConfiguration(adapter);
        String url = adapter.getAuthServerUrl() + "/realms/" + adapter.getRealm() + "/protocol/openid-connect/token";
        String clientId = adapter.getResource();
        String secret = (String) adapter.getCredentials().get("secret");
        Http http = new Http(clientConf, (params, headers) -> {
        });

        return http.<AccessTokenResponse>post(url)
                .authentication()
                .client()
                .form()
                .param("grant_type", "refresh_token")
                .param("refresh_token", refreshToken)
                .param("client_id", clientId)
                .param("client_secret", secret)
                .response()
                .json(AccessTokenResponse.class)
                .execute();
    }

    /**
     * Build a Configuration instance out of a specific AdapterConfig.
     * @param config the AdapterConfig.
     * @return the Configuration instance.
     */
    public Configuration getClientConfiguration(AdapterConfig config){
        String serverUrl=config.getAuthServerUrl();
        String realm=config.getRealm();
        String resource=config.getResource();
        Map<String,Object> credentials=config.getCredentials();
        return new Configuration(serverUrl,realm,resource,credentials,null);
    }

    //

    /**
     * Builds an authentication instance out of the passed values.
     * Sets it to the cache and to the SecurityContext to be sure the new token is updates.
     * @param cache the auth cache.
     * @param oldToken the old token.
     * @param newToken the new token.
     * @param refreshToken the refresh token.
     * @param expiresIn expires in value.
     * @return the new Authentication object.
     */
    public Authentication updateAuthentication(TokenAuthenticationCache cache, String oldToken, String newToken, String refreshToken, long expiresIn) {
        Authentication authentication = cache.get(oldToken);
        if (authentication == null)
            authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof PreAuthenticatedAuthenticationToken) {
            if (LOGGER.isDebugEnabled())
                LOGGER.info("Updating the cache and the SecurityContext with new Auth details");
            cache.removeEntry(oldToken);
            PreAuthenticatedAuthenticationToken updated = new PreAuthenticatedAuthenticationToken(authentication.getPrincipal(), authentication.getCredentials(), authentication.getAuthorities());
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Updating keycloak details.");
            KeycloakTokenDetails details=new KeycloakTokenDetails(newToken,refreshToken,expiresIn);
            updated.setDetails(details);
            cache.putCacheEntry(newToken, updated);
            SecurityContextHolder.getContext().setAuthentication(updated);
            authentication = updated;
        }
        return authentication;
    }
}
