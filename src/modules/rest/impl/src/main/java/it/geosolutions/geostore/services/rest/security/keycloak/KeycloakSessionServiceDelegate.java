package it.geosolutions.geostore.services.rest.security.keycloak;

import it.geosolutions.geostore.services.rest.RESTSessionService;
import it.geosolutions.geostore.services.rest.SessionServiceDelegate;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.SessionToken;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils;
import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;
import org.apache.log4j.Logger;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.util.Http;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Date;

import static it.geosolutions.geostore.services.rest.security.keycloak.KeyCloakSecurityConfiguration.CACHE_BEAN_NAME;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.REFRESH_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.getParameterValue;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.getRequest;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.getResponse;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.tokenFromParamsOrBearer;

/**
 * Keycloak implementation of SessionService delegate to provide method of refreshing the token and logging out.
 */
public class KeycloakSessionServiceDelegate implements SessionServiceDelegate {

    private final static Logger LOGGER = Logger.getLogger(KeycloakSessionServiceDelegate.class);

    public KeycloakSessionServiceDelegate(RESTSessionService restSessionService){
        restSessionService.registerDelegate("keycloak",this);
    }

    @Override
    public SessionToken refresh(String refreshToken, String accessToken) {
        HttpServletRequest request = getRequest();
        if (accessToken == null) accessToken = tokenFromParamsOrBearer(ACCESS_TOKEN_PARAM, request);
        if (accessToken == null) throw new NotFoundWebEx("The accessToken is missing");
        if (refreshToken == null) refreshToken = getParameterValue(REFRESH_TOKEN_PARAM, request);
        TokenAuthenticationCache cache=GeoStoreContext.bean(CACHE_BEAN_NAME,TokenAuthenticationCache.class);
        Date tokenExpiration=tokenExpirationTime(accessToken,cache);
        Date fiveMinutesFromNow=OAuth2Utils.fiveMinutesFromNow();
        SessionToken sessionToken;
        if (refreshToken!=null && (tokenExpiration==null || fiveMinutesFromNow.after(tokenExpiration)))
            sessionToken=doRefresh(accessToken,refreshToken,cache);
        else sessionToken=sessionToken(accessToken,refreshToken);
        return sessionToken;
    }

    private SessionToken doRefresh(String accessToken,String refreshToken,TokenAuthenticationCache cache){
        KeyCloakConfiguration configuration = GeoStoreContext.bean(KeyCloakConfiguration.class);
        AdapterConfig adapter = configuration.readAdapterConfig();
        KeyCloakHelper helper=GeoStoreContext.bean(KeyCloakHelper.class);
        AccessTokenResponse response=helper.refreshToken(adapter,refreshToken);
        String newAccessToken = response.getToken();
        long exp = response.getExpiresIn();
        String newRefreshToken = response.getRefreshToken();
        helper.updateAuthentication(cache, accessToken, newAccessToken, newRefreshToken, exp);
        return sessionToken(newAccessToken,newRefreshToken);
    }

    private Date tokenExpirationTime(String accessToken, TokenAuthenticationCache cache){
        Date result=null;
        Authentication authentication=cache.get(accessToken);
        if(authentication!=null && authentication.getDetails() instanceof KeycloakTokenDetails){
            KeycloakTokenDetails details=(KeycloakTokenDetails) authentication.getDetails();
            result=details.getExpiration();
        }
        return result;
    }

    private SessionToken sessionToken(String accessToken, String refreshToken) {
        SessionToken sessionToken = new SessionToken();
        sessionToken.setAccessToken(accessToken);
        sessionToken.setRefreshToken(refreshToken);
        sessionToken.setTokenType("bearer");
        return sessionToken;
    }

    @Override
    public void doLogout(String accessToken) {
        HttpServletRequest request= OAuth2Utils.getRequest();
        HttpServletResponse response=OAuth2Utils.getResponse();
        KeyCloakHelper helper=GeoStoreContext.bean(KeyCloakHelper.class);
        KeycloakDeployment deployment=helper.getDeployment(request,response);
        Authentication authentication=SecurityContextHolder.getContext().getAuthentication();
        String refreshToken=null;
        if (authentication.getDetails() instanceof KeycloakTokenDetails){
            refreshToken=((KeycloakTokenDetails) authentication.getDetails()).getRefreshToken();
        }
        String logoutUrl=deployment
                .getLogoutUrl()
                .build()
                .toString();
        AdapterConfig adapterConfig=GeoStoreContext.bean(KeyCloakConfiguration.class).readAdapterConfig();
        Configuration clientConfiguration=helper.getClientConfiguration(adapterConfig);
        Http http = new Http(clientConfiguration, (params, headers) -> {});
        String clientId = adapterConfig.getResource();
        String secret = (String) adapterConfig.getCredentials().get("secret");
        try{
            http.post(logoutUrl)
                    .form()
                    .param("client_id", clientId)
                    .param("client_secret", secret)
                    .param("refresh_token",refreshToken)
                    .execute();
        } catch (Exception e){
            LOGGER.error("Error while performing global logout.",e);
        }
        internalLogout(accessToken,request);
    }

    private void internalLogout(String accessToken, HttpServletRequest request){
        TokenAuthenticationCache cache=GeoStoreContext.bean(CACHE_BEAN_NAME,TokenAuthenticationCache.class);
        if (cache.get(accessToken)!=null) cache.removeEntry(accessToken);
        SecurityContextHolder.clearContext();
        try {
            HttpSession session=request.getSession(false);
            if (session!=null) session.invalidate();
            request.logout();
        } catch (ServletException e) {
            if (LOGGER.isDebugEnabled()){
                LOGGER.warn("Error while logging out from servlet request.",e);
            }
        }
    }
}
