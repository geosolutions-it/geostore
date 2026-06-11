package it.geosolutions.geostore.services.rest.security.oauth2;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration.CONFIG_NAME_SUFFIX;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.*;
import static org.springframework.security.oauth2.common.OAuth2AccessToken.BEARER_TYPE;

import it.geosolutions.geostore.services.rest.IdPLoginService;
import it.geosolutions.geostore.services.rest.model.SessionToken;
import it.geosolutions.geostore.services.rest.security.IdPConfiguration;
import it.geosolutions.geostore.services.rest.security.RestAuthenticationEntryPoint;
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
import org.apache.commons.lang3.time.DateUtils;
import org.apache.cxf.jaxrs.impl.ResponseBuilderImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public abstract class Oauth2LoginService implements IdPLoginService {

    private static final Logger LOGGER = LogManager.getLogger(Oauth2LoginService.class);

    @Override
    public void doLogin(HttpServletRequest request, HttpServletResponse response, String provider) {
        LOGGER.info("doLogin called for provider '{}'", provider);
        HttpServletResponse resp = OAuth2Utils.getResponse();
        OAuth2Configuration configuration = oauth2Configuration(provider);
        if (configuration == null) {
            LOGGER.error(
                    "No OAuth2Configuration bean found for provider '{}' (expected bean name: '{}')",
                    provider,
                    provider + CONFIG_NAME_SUFFIX);
            throw new RuntimeException("No OAuth2Configuration found for provider: " + provider);
        }
        debugSensitive(
                configuration,
                "Provider '{}' config: enabled={}, clientId={}, authorizationUri={}, "
                        + "accessTokenUri={}, discoveryUrl={}, redirectUri={}, scopes={}",
                provider,
                configuration.isEnabled(),
                configuration.getClientId(),
                configuration.getAuthorizationUri(),
                configuration.getAccessTokenUri(),
                configuration.getDiscoveryUrl(),
                configuration.getRedirectUri(),
                configuration.getScopes());
        if (configuration.isInvalid()) {
            LOGGER.error(
                    "Provider '{}' configuration is INVALID (missing clientId, clientSecret, "
                            + "authorizationUri, or accessTokenUri). Discovery may have failed.",
                    provider);
        }
        String login = configuration.buildLoginUri();
        try {
            LOGGER.info("Redirecting to login URI for provider '{}'", provider);
            debugSensitive(configuration, "Login URI: {}", login);
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
        if (configuration == null) {
            LOGGER.error(
                    "No IdPConfiguration bean found for provider '{}' (expected bean name: '{}'). "
                            + "The bean may not have been registered in GeoStoreContext's "
                            + "ApplicationContext.",
                    provider,
                    provider + CONFIG_NAME_SUFFIX);
            throw new RuntimeException(
                    "No IdPConfiguration found for callback provider: " + provider);
        }
        debugSensitive(configuration, "Token: {}", token);
        LOGGER.info("Redirect uri: {}", configuration.getRedirectUri());
        LOGGER.info("Internal redirect uri: {}", configuration.getInternalRedirectUri());
        if (token != null) {
            LOGGER.info("AccessToken found");
            SessionToken sessionToken = new SessionToken();
            try {
                result =
                        result.status(302)
                                .location(new URI(configuration.getInternalRedirectUri()));
                debugSensitive(configuration, "AccessToken: {}", token);
                sessionToken.setAccessToken(token);
                sessionToken.setTokenType(BEARER_TYPE);
                if (refreshToken != null) {
                    debugSensitive(configuration, "RefreshToken: {}", refreshToken);
                    result.header("Set-Cookie", refreshTokenCookie(refreshToken).toString());
                }
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
            String errorDetail = authenticationErrorDetail();
            String message =
                    "Authentication with provider '"
                            + provider
                            + "' failed: "
                            + (errorDetail != null
                                    ? errorDetail
                                    : "no access token was returned by the identity provider.");
            LOGGER.error("{} (callback response status: {})", message, response.getStatus());
            result = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(message);
        }
        return result;
    }

    /**
     * Returns the authentication failure reason recorded by the OAuth2 filter on the current
     * request (see {@link RestAuthenticationEntryPoint#OAUTH2_AUTH_ERROR_KEY}), or null if the
     * filter did not record one.
     */
    protected String authenticationErrorDetail() {
        HttpServletRequest request = OAuth2Utils.getRequest();
        if (request == null) return null;
        Object detail = request.getAttribute(RestAuthenticationEntryPoint.OAUTH2_AUTH_ERROR_KEY);
        return detail instanceof String ? (String) detail : null;
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

    /**
     * Logs potentially sensitive details (token values, provider configuration) only when the
     * provider explicitly enables {@code logSensitiveInfo}. Raising the logger level alone is not
     * enough to print them.
     */
    private static void debugSensitive(
            IdPConfiguration configuration, String message, Object... args) {
        if (configuration instanceof OAuth2Configuration
                && ((OAuth2Configuration) configuration).isLogSensitiveInfo()) {
            LOGGER.debug(message, args);
        }
    }

    protected Response buildCallbackResponse(
            HttpServletResponse response, String token, String refreshToken, String provider) {
        Response.ResponseBuilder result =
                getCallbackResponseBuilder(response, token, refreshToken, provider);
        return result.build();
    }

    protected OAuth2Configuration oauth2Configuration(String provider) {
        String beanName = provider + CONFIG_NAME_SUFFIX;
        OAuth2Configuration config = GeoStoreContext.bean(beanName, OAuth2Configuration.class);
        if (config == null) {
            LOGGER.error(
                    "GeoStoreContext.bean('{}', OAuth2Configuration.class) returned null. "
                            + "Trying raw lookup...",
                    beanName);
            Object raw = GeoStoreContext.bean(beanName);
            LOGGER.error(
                    "Raw bean '{}': {} (type: {})",
                    beanName,
                    raw,
                    raw != null ? raw.getClass().getName() : "null");
        }
        return config;
    }

    protected IdPConfiguration configuration(String provider) {
        String beanName = provider + CONFIG_NAME_SUFFIX;
        IdPConfiguration config = GeoStoreContext.bean(beanName, IdPConfiguration.class);
        if (config == null) {
            LOGGER.error(
                    "GeoStoreContext.bean('{}', IdPConfiguration.class) returned null. "
                            + "Trying raw lookup...",
                    beanName);
            Object raw = GeoStoreContext.bean(beanName);
            LOGGER.error(
                    "Raw bean '{}': {} (type: {})",
                    beanName,
                    raw,
                    raw != null ? raw.getClass().getName() : "null");
        }
        return config;
    }

    protected NewCookie refreshTokenCookie(String value) {
        boolean secure = isRequestSecure();
        Cookie cookie = new Cookie(REFRESH_TOKEN_PARAM, value, "/", null);
        return new AccessCookie(cookie, "", 604800, null, secure, true, "lax");
    }

    private boolean isRequestSecure() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null && attrs.getRequest().isSecure();
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
