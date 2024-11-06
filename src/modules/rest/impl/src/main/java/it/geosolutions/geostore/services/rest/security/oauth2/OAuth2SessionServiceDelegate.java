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
package it.geosolutions.geostore.services.rest.security.oauth2;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.*;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.security.password.SecurityUtils;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.rest.RESTSessionService;
import it.geosolutions.geostore.services.rest.SessionServiceDelegate;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.SessionToken;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.*;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.token.AccessTokenRequest;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.DefaultOAuth2RefreshToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpMessageConverterExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;

/** Abstract implementation of an OAuth2 SessionServiceDelegate. */
public abstract class OAuth2SessionServiceDelegate implements SessionServiceDelegate {

    private static final Logger LOGGER = LogManager.getLogger(OAuth2SessionServiceDelegate.class);

    protected UserService userService;

    /**
     * @param restSessionService the session service to which register this delegate.
     * @param delegateName this delegate name eg. google or GitHub etc...
     */
    public OAuth2SessionServiceDelegate(
            RESTSessionService restSessionService, String delegateName, UserService userService) {
        restSessionService.registerDelegate(delegateName, this);
        this.userService = userService;
    }

    public OAuth2SessionServiceDelegate(
            RestTemplate restTemplate, OAuth2Configuration configuration) {}

    @Override
    public SessionToken refresh(String refreshToken, String accessToken) {
        HttpServletRequest request = getRequest();
        if (accessToken == null)
            accessToken = OAuth2Utils.tokenFromParamsOrBearer(ACCESS_TOKEN_PARAM, request);
        if (accessToken == null)
            throw new NotFoundWebEx("Either the accessToken or the refresh token are missing");

        OAuth2AccessToken currentToken = retrieveAccessToken(accessToken);
        String refreshTokenToUse =
                currentToken.getRefreshToken() != null
                                && currentToken.getRefreshToken().getValue() != null
                                && !currentToken.getRefreshToken().getValue().isEmpty()
                        ? currentToken.getRefreshToken().getValue()
                        : refreshToken;
        if (refreshTokenToUse == null || refreshTokenToUse.isEmpty())
            refreshTokenToUse = getParameterValue(REFRESH_TOKEN_PARAM, request);
        SessionToken sessionToken = null;
        OAuth2Configuration configuration = configuration();
        if (configuration != null && configuration.isEnabled()) {
            if (LOGGER.isDebugEnabled()) LOGGER.info("Going to refresh the token.");
            try {
                sessionToken = doRefresh(refreshTokenToUse, accessToken, configuration);
            } catch (NullPointerException npe) {
                LOGGER.error("Current configuration wasn't correctly initialized.");
            }
        }
        if (sessionToken == null)
            sessionToken =
                    sessionToken(accessToken, refreshTokenToUse, currentToken.getExpiration());

        request.setAttribute(
                OAuth2AuthenticationDetails.ACCESS_TOKEN_VALUE, sessionToken.getAccessToken());
        request.setAttribute(
                OAuth2AuthenticationDetails.ACCESS_TOKEN_TYPE, sessionToken.getTokenType());

        return sessionToken;
    }

    /**
     * Invokes the refresh endpoint to get a new session token with updated token details.
     *
     * <p>This method attempts to refresh the session by exchanging the provided refresh token for a
     * new access token. If the refresh token is invalid or the request fails after several retries,
     * the session is cleared, and the user is redirected to the login page.
     *
     * @param refreshToken the refresh token to use for obtaining new access and refresh tokens
     * @param accessToken the current access token
     * @param configuration the OAuth2Configuration containing client credentials and endpoint URI
     * @return a SessionToken containing the new token details, or null if the refresh process
     *     failed
     */
    protected SessionToken doRefresh(
            String refreshToken, String accessToken, OAuth2Configuration configuration) {
        SessionToken sessionToken = null;
        int maxRetries = 3;
        int attempt = 0;
        boolean success = false;

        // Setup HTTP headers and body for the request
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = getHttpHeaders(accessToken, configuration);
        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("grant_type", "refresh_token");
        requestBody.add("refresh_token", refreshToken);
        requestBody.add("client_secret", configuration.getClientSecret());
        requestBody.add("client_id", configuration.getClientId());
        HttpEntity<MultiValueMap<String, String>> requestEntity =
                new HttpEntity<>(requestBody, headers);

        while (attempt < maxRetries && !success) {
            attempt++;
            LOGGER.info("Attempting to refresh token, attempt {} of {}", attempt, maxRetries);

            try {
                ResponseEntity<OAuth2AccessToken> response =
                        restTemplate.exchange(
                                configuration.buildRefreshTokenURI(),
                                HttpMethod.POST,
                                requestEntity,
                                OAuth2AccessToken.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    OAuth2AccessToken newToken = response.getBody();
                    if (newToken != null
                            && newToken.getValue() != null
                            && !newToken.getValue().isEmpty()) {
                        // Process and update the new token details
                        OAuth2RefreshToken newRefreshToken = newToken.getRefreshToken();
                        OAuth2RefreshToken refreshTokenToUse =
                                (newRefreshToken != null && newRefreshToken.getValue() != null)
                                        ? newRefreshToken
                                        : new DefaultOAuth2RefreshToken(refreshToken);

                        updateAuthToken(accessToken, newToken, refreshTokenToUse, configuration);
                        sessionToken =
                                sessionToken(
                                        newToken.getValue(),
                                        refreshTokenToUse.getValue(),
                                        newToken.getExpiration());

                        LOGGER.info("Token refreshed successfully on attempt {}", attempt);
                        success = true;
                    } else {
                        LOGGER.warn("Received empty or null token on attempt {}", attempt);
                    }
                } else if (response.getStatusCode().is4xxClientError()) {
                    // For client errors (e.g., 400, 401, 403), do not retry.
                    LOGGER.error(
                            "Client error occurred: {}. Stopping further attempts.",
                            response.getStatusCode());
                    break;
                } else {
                    // For server errors (5xx), continue retrying
                    LOGGER.warn("Server error occurred: {}. Retrying...", response.getStatusCode());
                }
            } catch (RestClientException ex) {
                LOGGER.error("Attempt {}: Error refreshing token: {}", attempt, ex.getMessage());
                if (attempt == maxRetries) {
                    LOGGER.error("Max retries reached. Unable to refresh token.");
                }
            }
        }

        // Handle unsuccessful refresh
        if (!success) {
            handleRefreshFailure(accessToken, refreshToken, configuration);
        }
        return sessionToken;
    }

    /**
     * Handles the refresh failure by clearing the session, logging out remotely, and redirecting to
     * login.
     *
     * @param accessToken the current access token
     * @param refreshToken the current refresh token
     * @param configuration the OAuth2Configuration with endpoint details
     */
    private void handleRefreshFailure(
            String accessToken, String refreshToken, OAuth2Configuration configuration) {
        LOGGER.info(
                "Unable to refresh token after max retries. Clearing session and redirecting to login.");
        doLogout(null);

        try {
            String redirectUrl =
                    "../../openid/" + configuration.getProvider().toLowerCase() + "/login";
            getResponse().sendRedirect(redirectUrl);
        } catch (IOException e) {
            LOGGER.error("Error while sending redirect to login service: ", e);
            throw new RuntimeException("Failed to redirect to login", e);
        }
    }

    private static HttpHeaders getHttpHeaders(
            String accessToken, OAuth2Configuration configuration) {
        HttpHeaders headers = new HttpHeaders();
        if (configuration != null
                && configuration.clientId != null
                && configuration.clientSecret != null)
            headers.setBasicAuth(
                    configuration.clientId,
                    configuration
                            .clientSecret); // Set client ID and client secret for authentication
        else if (accessToken != null && !accessToken.isEmpty()) {
            headers.set("Authorization", "Bearer " + accessToken);
        }
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED); // Set content type
        return headers;
    }

    private SessionToken sessionToken(String accessToken, String refreshToken, Date expires) {
        SessionToken sessionToken = new SessionToken();
        if (expires != null) sessionToken.setExpires(expires.getTime());
        sessionToken.setAccessToken(accessToken);
        sessionToken.setRefreshToken(refreshToken);
        sessionToken.setTokenType("bearer");
        return sessionToken;
    }

    // Builds an authentication instance out of the passed values.
    // Sets it to the cache and to the SecurityContext to be sure the new token is updates.
    private void updateAuthToken(
            String oldToken,
            OAuth2AccessToken newToken,
            OAuth2RefreshToken refreshToken,
            OAuth2Configuration conf) {
        Authentication authentication = cache().get(oldToken);
        if (authentication == null)
            authentication = SecurityContextHolder.getContext().getAuthentication();

        if (LOGGER.isDebugEnabled())
            LOGGER.info("Updating the cache and the SecurityContext with new Auth details");
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
            TokenDetails details = getTokenDetails(authentication);
            String idToken = details.getIdToken();
            cache().removeEntry(oldToken);
            PreAuthenticatedAuthenticationToken updated =
                    new PreAuthenticatedAuthenticationToken(
                            authentication.getPrincipal(),
                            authentication.getCredentials(),
                            authentication.getAuthorities());
            DefaultOAuth2AccessToken accessToken = new DefaultOAuth2AccessToken(newToken);
            if (refreshToken != null) {
                accessToken.setRefreshToken(refreshToken);
            }
            if (LOGGER.isDebugEnabled())
                LOGGER.debug(
                        "Creating new details. AccessToken: {} IdToken: {}", accessToken, idToken);
            updated.setDetails(new TokenDetails(accessToken, idToken, conf.getBeanName()));
            cache().putCacheEntry(newToken.getValue(), updated);
            SecurityContextHolder.getContext().setAuthentication(updated);
        }
    }

    private OAuth2AccessToken retrieveAccessToken(String accessToken) {
        Authentication authentication = cache() != null ? cache().get(accessToken) : null;
        OAuth2AccessToken result = null;
        if (authentication != null) {
            TokenDetails details = OAuth2Utils.getTokenDetails(authentication);
            result = details.getAccessToken();
        }
        if (result == null) {
            OAuth2RestTemplate oAuth2RestTemplate = restTemplate();
            if (oAuth2RestTemplate != null) {
                OAuth2ClientContext context = oAuth2RestTemplate.getOAuth2ClientContext();
                if (context != null) result = context.getAccessToken();
            }
        }
        if (result == null) result = new DefaultOAuth2AccessToken(accessToken);
        return result;
    }

    @Override
    public void doLogout(String sessionId) {
        HttpServletRequest request = getRequest();
        HttpServletResponse response = getResponse();
        OAuth2RestTemplate restTemplate = restTemplate();

        String token = null;
        String accessToken = null;
        if (sessionId != null) {
            TokenAuthenticationCache cache = cache();
            Authentication authentication = cache.get(sessionId);
            TokenDetails tokenDetails = getTokenDetails(authentication);
            if (tokenDetails != null) {
                token = tokenDetails.getIdToken();
                accessToken = tokenDetails.getAccessToken().getValue();
            }
            cache.removeEntry(sessionId);
        }

        if (token == null) {
            if (restTemplate.getOAuth2ClientContext().getAccessToken() != null) {
                token =
                        restTemplate
                                .getOAuth2ClientContext()
                                .getAccessToken()
                                .getRefreshToken()
                                .getValue();
            }
            if (token == null) {
                token = OAuth2Utils.getParameterValue(REFRESH_TOKEN_PARAM, request);
            }
            if (token == null) {
                token =
                        (String)
                                Objects.requireNonNull(RequestContextHolder.getRequestAttributes())
                                        .getAttribute(REFRESH_TOKEN_PARAM, 0);
            }
        }

        if (accessToken == null) {
            if (restTemplate.getOAuth2ClientContext().getAccessToken() != null) {
                accessToken = restTemplate.getOAuth2ClientContext().getAccessToken().getValue();
            }
            if (accessToken == null) {
                accessToken = OAuth2Utils.getParameterValue(ACCESS_TOKEN_PARAM, request);
            }
            if (accessToken == null) {
                accessToken =
                        (String)
                                Objects.requireNonNull(RequestContextHolder.getRequestAttributes())
                                        .getAttribute(ACCESS_TOKEN_PARAM, 0);
            }
        }

        OAuth2Configuration configuration = configuration();
        if (configuration != null && configuration.isEnabled()) {
            if (token != null
                    && accessToken != null
                    && !token.isEmpty()
                    && !accessToken.isEmpty()) {
                if (configuration.isGlobalLogoutEnabled())
                    doLogoutInternal(token, configuration, accessToken);
                if (configuration.getRevokeEndpoint() != null) clearSession(restTemplate, request);
            } else {
                if (LOGGER.isDebugEnabled())
                    LOGGER.info("Unable to retrieve access token. Remote logout was not executed.");
            }
            if (response != null) clearCookies(request, response);
        }
    }

    // clears any state a Spring OAuth2 object might preserve.
    private void clearSession(OAuth2RestTemplate restTemplate, HttpServletRequest request) {
        final AccessTokenRequest accessTokenRequest =
                restTemplate.getOAuth2ClientContext().getAccessTokenRequest();
        if (accessTokenRequest != null && accessTokenRequest.getStateKey() != null) {
            restTemplate
                    .getOAuth2ClientContext()
                    .removePreservedState(accessTokenRequest.getStateKey());
        }
        try {
            if (accessTokenRequest != null) {
                accessTokenRequest.remove("access_token");
                accessTokenRequest.remove("refresh_token");
            }
            request.logout();
        } catch (ServletException e) {
            LOGGER.error("Error happened while doing request logout: ", e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Call the revoke/logout endpoint of the OAuth2 provider.
     *
     * @param token the access token.
     * @param configuration the OAuth2Configuration
     */
    protected void doLogoutInternal(
            Object token, OAuth2Configuration configuration, String accessToken) {
        String tokenValue = null;
        if (token instanceof OAuth2AccessToken) {
            tokenValue =
                    ((OAuth2AccessToken) token).getRefreshToken() != null
                            ? ((OAuth2AccessToken) token).getRefreshToken().getValue()
                            : ((OAuth2AccessToken) token).getValue();
        } else if (token instanceof String) {
            tokenValue = (String) token;
        }
        if (configuration.getRevokeEndpoint() != null && tokenValue != null) {
            if (LOGGER.isDebugEnabled()) LOGGER.info("Performing remote logout");
            callRevokeEndpoint(tokenValue, accessToken);
            callRemoteLogout(tokenValue, accessToken);
        }
    }

    protected void callRevokeEndpoint(String token, String accessToken) {
        OAuth2Configuration configuration = configuration();
        if (configuration != null && configuration.isEnabled()) {
            OAuth2Configuration.Endpoint revokeEndpoint =
                    configuration.buildRevokeEndpoint(token, accessToken, configuration);
            if (revokeEndpoint != null) {
                RestTemplate template = new RestTemplate();
                try {
                    ResponseEntity<String> responseEntity =
                            template.exchange(
                                    revokeEndpoint.getUrl(),
                                    revokeEndpoint.getMethod(),
                                    revokeEndpoint.getRequestEntity(),
                                    String.class);
                    if (responseEntity.getStatusCode().value() != 200) {
                        logRevokeErrors(responseEntity.getBody());
                    }
                } catch (Exception e) {
                    logRevokeErrors(e);
                }
            }
        }
    }

    protected void callRemoteLogout(String token, String accessToken) {
        OAuth2Configuration configuration = configuration();
        if (configuration != null && configuration.isEnabled()) {
            OAuth2Configuration.Endpoint logoutEndpoint =
                    configuration.buildLogoutEndpoint(token, accessToken, configuration);
            if (logoutEndpoint != null) {
                RestTemplate template = new RestTemplate();
                ResponseEntity<String> responseEntity =
                        template.exchange(
                                logoutEndpoint.getUrl(),
                                logoutEndpoint.getMethod(),
                                logoutEndpoint.getRequestEntity(),
                                String.class);
                if (responseEntity.getStatusCode().value() != 200) {
                    logRevokeErrors(responseEntity.getBody());
                }
            }
        }
    }

    protected void clearCookies(HttpServletRequest request, HttpServletResponse response) {
        javax.servlet.http.Cookie[] allCookies = request.getCookies();
        if (allCookies != null)
            for (javax.servlet.http.Cookie toDelete : allCookies) {
                if (deleteCookie(toDelete)) {
                    toDelete.setMaxAge(-1);
                    toDelete.setPath("/");
                    toDelete.setComment("EXPIRING COOKIE at " + System.currentTimeMillis());
                    response.addCookie(toDelete);
                }
            }
    }

    protected boolean deleteCookie(javax.servlet.http.Cookie c) {
        return c.getName().equalsIgnoreCase("JSESSIONID")
                || c.getName().equalsIgnoreCase(ACCESS_TOKEN_PARAM)
                || c.getName().equalsIgnoreCase(REFRESH_TOKEN_PARAM);
    }

    private TokenAuthenticationCache cache() {
        return GeoStoreContext.bean("oAuth2Cache", TokenAuthenticationCache.class);
    }

    /**
     * Get the OAuth2Configuration.
     *
     * @return the OAuth2Configuration.
     */
    protected OAuth2Configuration configuration() {
        Map<String, OAuth2Configuration> configurations =
                GeoStoreContext.beans(OAuth2Configuration.class);
        if (configurations != null) {
            Optional<OAuth2Configuration> enabledConfig =
                    configurations.values().stream()
                            .filter(OAuth2Configuration::isEnabled)
                            .findFirst();

            if (enabledConfig.isPresent()) {
                return enabledConfig.get();
            }
        }
        return null;
    }

    protected HttpMessageConverterExtractor<OAuth2AccessToken> tokenExtractor() {
        return new HttpMessageConverterExtractor<>(
                OAuth2AccessToken.class, restTemplate().getMessageConverters());
    }

    protected abstract OAuth2RestTemplate restTemplate();

    @Override
    public User getUser(String sessionId, boolean refresh, boolean autorefresh) {
        String username = getUserName(sessionId, refresh, autorefresh);
        if (username != null) {
            User user;
            try {
                user = userService.get(username);
            } catch (Exception e) {
                LOGGER.warn("Issue while retrieving user. Will return just the username.", e);
                user = new User();
                user.setName(username);
            }
            return user;
        }
        return null;
    }

    @Override
    public String getUserName(String sessionId, boolean refresh, boolean autorefresh) {
        TokenAuthenticationCache cache = cache();
        Authentication authentication = cache.get(sessionId);
        if (refresh)
            LOGGER.warn(
                    "Refresh was set to true but this delegate is "
                            + "not supporting refreshing token when retrieving the user...");
        if (authentication != null) {
            Object o = authentication.getPrincipal();
            if (o != null) return SecurityUtils.getUsername(o);
        }
        return null;
    }

    private static void logRevokeErrors(Object cause) {
        LOGGER.error("Error while revoking authorization. Error is: {}", cause);
    }
}
