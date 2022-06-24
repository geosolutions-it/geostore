package it.geosolutions.geostore.services.rest.security.oauth2;

import it.geosolutions.geostore.services.rest.IdPLoginService;
import it.geosolutions.geostore.services.rest.security.IdPConfiguration;
import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;
import org.apache.commons.lang.time.DateUtils;
import org.apache.cxf.jaxrs.impl.ResponseBuilderImpl;
import org.apache.log4j.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration.CONFIG_NAME_SUFFIX;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.REFRESH_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.getAccessToken;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.getRefreshAccessToken;

public abstract class Oauth2LoginService implements IdPLoginService {

    private final static Logger LOGGER = Logger.getLogger(Oauth2LoginService.class);


    @Override
    public void doLogin(HttpServletRequest request, HttpServletResponse response, String provider) {
        HttpServletResponse resp = OAuth2Utils.getResponse();
        OAuth2Configuration configuration = oauth2Configuration(provider);
        String login = configuration.buildLoginUri();
        try {
            resp.sendRedirect(login);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Response doInternalRedirect(HttpServletRequest request, HttpServletResponse response, String provider) {
        String token = getAccessToken();
        String refreshToken = getRefreshAccessToken();
       return buildCallbackResponse(token,refreshToken,provider);
    }

    protected Response.ResponseBuilder getCallbackResponseBuilder(String token, String refreshToken, String provider){
        Response.ResponseBuilder result = new ResponseBuilderImpl();
        IdPConfiguration configuration = configuration(provider);
        if (token != null) {
            try {
                result = result.status(302)
                        .location(new URI(configuration.getInternalRedirectUri()));
                if (token != null) {
                    if(LOGGER.isDebugEnabled())
                        LOGGER.info("AccessToken found");
                    result = result.cookie(cookie(ACCESS_TOKEN_PARAM, token));
                }
                if (refreshToken != null){
                    if(LOGGER.isDebugEnabled())
                        LOGGER.info("RefreshToken found");
                    result = result.cookie(cookie(REFRESH_TOKEN_PARAM, refreshToken));
                }
            } catch (URISyntaxException e) {
                LOGGER.error(e);
                result = result
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Exception while parsing the internal redirect url: " + e.getMessage());
            }
        } else {
            result = Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("No access token found.");
        }
        return result;
    }


    protected Response buildCallbackResponse(String token, String refreshToken, String provider){
        Response.ResponseBuilder result =getCallbackResponseBuilder(token,refreshToken,provider);
        return result.build();
    }

    protected OAuth2Configuration oauth2Configuration(String provider){
        return GeoStoreContext.bean(provider+CONFIG_NAME_SUFFIX,OAuth2Configuration.class);
    }
    protected IdPConfiguration configuration(String provider) {
        return GeoStoreContext.bean(provider + CONFIG_NAME_SUFFIX,IdPConfiguration.class);
    }

    private NewCookie cookie(String name, String value) {
        return cookie(name, value, DateUtils.addMinutes(new Date(), 2));
    }

    protected NewCookie cookie(String name, String value, Date expires) {
        Cookie cookie = new Cookie(name, value, "/", null);
        return new AccessCookie(cookie, "", 120, DateUtils.addMinutes(new Date(), 2), false, false, "lax");
    }
}
