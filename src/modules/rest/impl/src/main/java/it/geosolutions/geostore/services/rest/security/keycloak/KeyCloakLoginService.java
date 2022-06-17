package it.geosolutions.geostore.services.rest.security.keycloak;

import it.geosolutions.geostore.services.rest.IdPLoginRest;
import it.geosolutions.geostore.services.rest.security.oauth2.Oauth2LoginService;
import org.apache.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.context.request.RequestContextHolder;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Date;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.REFRESH_TOKEN_PARAM;

/**
 * Keycloak implementation for a LoginService.
 * Since keycloak redirects to the url from which the call to the authorization page was issued
 * no internal redirect is really performed here.
 */
public class KeyCloakLoginService extends Oauth2LoginService {

    private final static Logger LOGGER = Logger.getLogger(KeyCloakLoginService.class);

    static String KEYCLOAK_REDIRECT="KEYCLOAK_REDIRECT";


    public KeyCloakLoginService(IdPLoginRest loginRest){
        loginRest.registerService("keycloak",this);
    }

    @Override
    public void doLogin(HttpServletRequest request, HttpServletResponse response, String provider) {
        AuthenticationEntryPoint challenge= (AuthenticationEntryPoint) RequestContextHolder.getRequestAttributes().getAttribute(KEYCLOAK_REDIRECT,0);
        if (challenge!=null) {
            try {
                challenge.commence(request,response,null);
            } catch (Exception e) {
                LOGGER.error("Error while redirecting to Keycloak authorization.",e);
                throw new RuntimeException(e);
            }
        } else {
            KeycloakTokenDetails details=getDetails();
            if (details!=null){
                String accessToken=details.getAccessToken();
                String refreshToken=details.getRefreshToken();
                Date expiresIn=details.getExpiration();
                if (accessToken!=null) {
                    Cookie access = new Cookie(ACCESS_TOKEN_PARAM, accessToken);
                    access.setSecure(false);
                    access.setMaxAge(Long.valueOf(expiresIn.getTime()).intValue());
                    response.addCookie(access);
                } else if (LOGGER.isDebugEnabled()){
                    LOGGER.warn("No access token found in auth object...");
                }
                if (refreshToken!=null) {
                    Cookie refresh = new Cookie(REFRESH_TOKEN_PARAM, refreshToken);
                    refresh.setSecure(false);
                    refresh.setMaxAge(Long.valueOf(expiresIn.getTime()).intValue());
                    response.addCookie(refresh);
                } else if (LOGGER.isDebugEnabled()){
                    LOGGER.warn("No refresh token found in auth object...");
                }
            }
            try {
                response.sendRedirect(configuration(provider).getInternalRedirectUri());
            }catch (IOException e){
                LOGGER.error("Error while redirecting to internal url...",e);
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Response doInternalRedirect(HttpServletRequest request, HttpServletResponse response, String provider) {
        String token =null;
        String refreshToken=null;
        KeycloakTokenDetails details=getDetails();
        if (details!=null){
            token=details.getAccessToken();
            refreshToken=details.getRefreshToken();
        }
        return buildCallbackResponse(token,refreshToken,provider);
    }

    private KeycloakTokenDetails getDetails(){
        KeycloakTokenDetails result=null;
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth!=null && auth.getDetails() instanceof KeycloakTokenDetails)
            result=(KeycloakTokenDetails) auth.getDetails();
        return result;
    }
}
