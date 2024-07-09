package it.geosolutions.geostore.services.rest.security.oauth2;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration.CONFIG_NAME_SUFFIX;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.*;
import static org.springframework.security.oauth2.common.OAuth2AccessToken.BEARER_TYPE;

import it.geosolutions.geostore.services.rest.IdPLoginService;
import it.geosolutions.geostore.services.rest.model.SessionToken;
import it.geosolutions.geostore.services.rest.security.IdPConfiguration;
import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import org.apache.commons.lang.time.DateUtils;
import org.apache.cxf.jaxrs.impl.ResponseBuilderImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class Oauth2LoginService implements IdPLoginService {

    private static final Logger LOGGER = LogManager.getLogger(Oauth2LoginService.class);

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
    public Response doInternalRedirect(
            HttpServletRequest request, HttpServletResponse response, String provider) {
        String token = getAccessToken();
        String refreshToken = getRefreshAccessToken();
        return buildCallbackResponse(response, token, refreshToken, provider);
    }

    protected Response.ResponseBuilder getCallbackResponseBuilder(
            HttpServletResponse response, String token, String refreshToken, String provider) {
        Response.ResponseBuilder result = new ResponseBuilderImpl();
        IdPConfiguration configuration = configuration(provider);
        LOGGER.info("Callback Provider: {}", provider);
        LOGGER.debug("Token: {}", token);
        LOGGER.debug("Redirect uri: {}", configuration.getRedirectUri());
        LOGGER.debug("Internal redirect uri: {}", configuration.getInternalRedirectUri());
        if (token != null) {
            LOGGER.info("AccessToken found");
            SessionToken sessionToken = new SessionToken();
            try {
                result =
                        result.status(302)
                                .location(new URI(configuration.getInternalRedirectUri()));
                LOGGER.debug("AccessToken: {}", token);
                sessionToken.setAccessToken(token);
                if (refreshToken != null) {
                    LOGGER.debug("RefreshToken: {}", refreshToken);
                    sessionToken.setRefreshToken(refreshToken);
                }
                sessionToken.setTokenType(BEARER_TYPE);
                TokenStorage tokenStorage = tokenStorage();
                Object key = tokenStorage.buildTokenKey();
                tokenStorage.saveToken(key, sessionToken);
                Cookie cookie = cookie(TOKENS_KEY, key.toString());
                result.header("Set-Cookie", cookie.toString());
                cookie = cookie(AUTH_PROVIDER, provider);
                result.header("Set-Cookie", cookie.toString());
            } catch (URISyntaxException e) {
                LOGGER.error(e);
                result =
                        result.status(Response.Status.INTERNAL_SERVER_ERROR)
                                .entity(
                                        "Exception while parsing the internal redirect url: "
                                                + e.getMessage());
            }
        } else {
            LOGGER.error("No access token found on callback request: {}", response.getStatus());
            result =
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("No access token found.");
        }
        return result;
    }

    @Override
    public SessionToken getTokenByIdentifier(String provider, String tokenIdentifier) {
        TokenStorage storage = tokenStorage();
        SessionToken sessionToken = storage.getTokenByIdentifier(tokenIdentifier);
        if (sessionToken != null) storage.removeTokenByIdentifier(tokenIdentifier);
        return sessionToken;
    }

    protected TokenStorage tokenStorage() {
        return GeoStoreContext.bean(TokenStorage.class);
    }

    protected Response buildCallbackResponse(
            HttpServletResponse response, String token, String refreshToken, String provider) {
        Response.ResponseBuilder result =
                getCallbackResponseBuilder(response, token, refreshToken, provider);
        return result.build();
    }

    protected OAuth2Configuration oauth2Configuration(String provider) {
        return GeoStoreContext.bean(provider + CONFIG_NAME_SUFFIX, OAuth2Configuration.class);
    }

    protected IdPConfiguration configuration(String provider) {
        return GeoStoreContext.bean(provider + CONFIG_NAME_SUFFIX, IdPConfiguration.class);
    }

    protected NewCookie cookie(String name, String value) {
        return cookie(name, value, DateUtils.addMinutes(new Date(), 2));
    }

    protected NewCookie cookie(String name, String value, Date expires) {
        Cookie cookie = new Cookie(name, value, "/", null);
        return new AccessCookie(
                cookie, "", 120, DateUtils.addMinutes(new Date(), 2), false, false, "lax");
    }
}
