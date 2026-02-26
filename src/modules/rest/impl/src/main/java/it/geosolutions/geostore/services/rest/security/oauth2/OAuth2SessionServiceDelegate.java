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

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.*;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.jwt.Jwt;
import org.springframework.security.jwt.JwtHelper;
import org.springframework.security.jwt.crypto.sign.InvalidSignatureException;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.UserRedirectRequiredException;
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

    private static final long CLOCK_SKEW_ALLOWANCE_MILLIS = 5 * 60 * 1000; // 5 minutes

    protected UserService userService;
    protected final String delegateName;

    /**
     * @param restSessionService the session service to which register this delegate?
     * @param delegateName this delegate name eg. google or GitHub etc...
     */
    public OAuth2SessionServiceDelegate(
            RESTSessionService restSessionService, String delegateName, UserService userService) {
        restSessionService.registerDelegate(delegateName, this);
        this.delegateName = delegateName;
        this.userService = userService;
    }

    public OAuth2SessionServiceDelegate(
            RestTemplate restTemplate, OAuth2Configuration configuration) {
        this.delegateName = null;
    }

    @Override
    public SessionToken refresh(String refreshToken, String accessToken) {
        String errorMessage = "";
        String warningMessage = "";
        HttpServletRequest request = getRequest();

        // Ensure accessToken is available
        if (accessToken == null || accessToken.isEmpty()) {
            accessToken = OAuth2Utils.tokenFromParamsOrBearer(ACCESS_TOKEN_PARAM, request);
        }
        if (accessToken == null || accessToken.isEmpty()) {
            throw new NotFoundWebEx("Either the accessToken or the refresh token are missing");
        }

        OAuth2AccessToken currentToken = retrieveAccessToken(accessToken, null);

        // Determine refreshTokenToUse
        String refreshTokenToUse =
                Optional.ofNullable(currentToken.getRefreshToken())
                        .map(OAuth2RefreshToken::getValue)
                        .filter(value -> !value.isEmpty())
                        .orElse(refreshToken);

        if (refreshTokenToUse == null || refreshTokenToUse.isEmpty()) {
            refreshTokenToUse = getParameterValue(REFRESH_TOKEN_PARAM, request);
        }

        if (refreshTokenToUse == null || refreshTokenToUse.isEmpty()) {
            refreshTokenToUse = getRefreshTokenFromCookie(request);
        }

        SessionToken sessionToken = null;
        OAuth2Configuration configuration = configuration();

        if (refreshTokenToUse == null || refreshTokenToUse.isEmpty()) {
            // No refresh token available (e.g. bearer-token auth without auth code flow,
            // or IdP did not issue a refresh token because offline_access was not requested).
            // Skip the refresh attempt and return the current token if still valid.
            LOGGER.info(
                    "No refresh token available; skipping token refresh and returning current token.");
            warningMessage = "No refresh token available; using existing access token.";
        } else if (configuration != null && configuration.isEnabled()) {
            LOGGER.info("Attempting to refresh the token.");
            try {
                sessionToken = doRefresh(refreshTokenToUse, accessToken, configuration);
                if (sessionToken != null) {
                    currentToken =
                            retrieveAccessToken(
                                    sessionToken.getAccessToken(), sessionToken.getExpires());
                }
            } catch (UserRedirectRequiredException e) {
                warningMessage = "A redirect is required to get the user's approval.";
                LOGGER.warn(warningMessage, e);
            } catch (Exception e) {
                errorMessage = "An error occurred during token refresh: " + e.getMessage();
                LOGGER.error(errorMessage, e);
            }
        } else {
            LOGGER.warn("Configuration is null or disabled; skipping token refresh.");
        }

        if (sessionToken == null) {
            if (isTokenExpired(currentToken) /* || !isTokenValid(currentToken) */) {
                errorMessage = "Token is invalid or expired, and refresh failed.";
                LOGGER.error(errorMessage);
                handleRefreshFailure(accessToken, refreshTokenToUse, configuration);
                return null;
            } else {
                if (warningMessage.isEmpty()) warningMessage = "Using existing access token.";
                sessionToken =
                        sessionToken(accessToken, refreshTokenToUse, currentToken.getExpiration());
            }
        }

        if (!warningMessage.isEmpty()) {
            sessionToken.setWarning(warningMessage);
        }
        if (!errorMessage.isEmpty()) {
            sessionToken.setError(errorMessage);
        }

        request.setAttribute(
                OAuth2AuthenticationDetails.ACCESS_TOKEN_VALUE, sessionToken.getAccessToken());
        request.setAttribute(
                OAuth2AuthenticationDetails.ACCESS_TOKEN_TYPE, sessionToken.getTokenType());

        // Set updated refresh token as HttpOnly cookie and remove from JSON response
        HttpServletResponse response = getResponse();
        if (response != null && sessionToken.getRefreshToken() != null) {
            setRefreshTokenCookie(response, sessionToken.getRefreshToken(), request.isSecure());
            sessionToken.setRefreshToken(null);
        }

        return sessionToken;
    }

    private boolean isTokenExpired(OAuth2AccessToken token) {
        if (token == null || token.getValue().isEmpty()) {
            return true;
        }

        Date expiration = token.getExpiration();

        if (expiration == null) {
            expiration = getExpirationDateFromToken(token.getValue());
            if (expiration == null) {
                return true;
            }
        }

        long now = System.currentTimeMillis();
        long adjustedExpirationTime = expiration.getTime() + CLOCK_SKEW_ALLOWANCE_MILLIS;

        return adjustedExpirationTime <= now;
    }

    private Date getExpirationDateFromToken(String token) {
        try {
            Jwt decodedToken = JwtHelper.decode(token);
            String claimsJson = decodedToken.getClaims();

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> claims = mapper.readValue(claimsJson, Map.class);

            Object exp = claims.get("exp");
            if (exp != null) {
                long expLong;
                if (exp instanceof Integer) {
                    expLong = ((Integer) exp).longValue();
                } else if (exp instanceof Long) {
                    expLong = (Long) exp;
                } else if (exp instanceof String) {
                    expLong = Long.parseLong((String) exp);
                } else {
                    throw new IllegalArgumentException("Cannot parse 'exp' claim from token");
                }

                // The 'exp' claim has usually been in seconds since the epoch
                return new Date(expLong * 1000);
            } else {
                return null;
            }
        } catch (InvalidSignatureException e) {
            LOGGER.error("Invalid JWT signature: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.error("Failed to parse JWT token: {}", e.getMessage());
            return null;
        }
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
        int attempt = 0;
        String errorMessage = "";
        String warningMessage = "";

        // Use a plain RestTemplate to avoid OAuth2RestTemplate interceptors that may
        // trigger a UserRedirectRequiredException when the current access token is expired.
        RestTemplate plainRestTemplate = createRefreshRestTemplate();
        HttpHeaders headers = getHttpHeaders(accessToken, configuration);
        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("grant_type", "refresh_token");
        requestBody.add("refresh_token", refreshToken);
        requestBody.add("client_secret", configuration.getClientSecret());
        requestBody.add("client_id", configuration.getClientId());
        if (configuration.getScopes() != null && !configuration.getScopes().isEmpty()) {
            requestBody.add("scope", configuration.getScopes().replace(",", " "));
        }
        HttpEntity<MultiValueMap<String, String>> requestEntity =
                new HttpEntity<>(requestBody, headers);

        long backoffDelay = configuration.getInitialBackoffDelay();
        int maxRetries = configuration.getMaxRetries() > 0 ? configuration.getMaxRetries() : 1;

        while (attempt < maxRetries) {
            attempt++;
            LOGGER.info("Attempting to refresh token, attempt {} of {}", attempt, maxRetries);

            try {
                ResponseEntity<OAuth2AccessToken> response =
                        plainRestTemplate.exchange(
                                configuration.getAccessTokenUri(),
                                HttpMethod.POST,
                                requestEntity,
                                OAuth2AccessToken.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    OAuth2AccessToken newToken = response.getBody();
                    if (newToken != null
                            && !isTokenExpired(newToken) /* && isTokenValid(newToken) */) {
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
                        attempt = maxRetries; // Exit retry loop on redirect exception
                        break;
                    } else {
                        LOGGER.warn("Received invalid or expired token on attempt {}", attempt);
                    }
                } else if (response.getStatusCode().is4xxClientError()) {
                    LOGGER.error(
                            "Client error occurred: {}. Stopping further attempts.",
                            response.getStatusCode());
                    break;
                } else {
                    LOGGER.warn("Server error occurred: {}. Retrying...", response.getStatusCode());
                }
            } catch (UserRedirectRequiredException e) {
                // Handle redirect exception, set warning, and prevent further attempts
                warningMessage = "A redirect is required to get the user's approval.";
                LOGGER.warn(warningMessage);
                sessionToken = sessionToken(accessToken, refreshToken, null); // Keep current token
                sessionToken.setWarning(warningMessage);
                attempt = maxRetries; // Exit retry loop on redirect exception
                break;
            } catch (RestClientException ex) {
                LOGGER.error("Attempt {}: Error refreshing token: {}", attempt, ex.getMessage());
                if (attempt == maxRetries) {
                    errorMessage = "Max retries reached. Unable to refresh token.";
                    LOGGER.error(errorMessage);
                    break;
                }
            }

            // Apply backoff delay before the next retry, unless it's the last attempt
            if (attempt < maxRetries) {
                try {
                    LOGGER.info("Waiting for {} ms before next retry.", backoffDelay);
                    Thread.sleep(backoffDelay);
                } catch (InterruptedException e) {
                    LOGGER.warn("Backoff delay interrupted", e);
                    Thread.currentThread().interrupt(); // Preserve interrupt status
                }
                backoffDelay *=
                        configuration.getBackoffMultiplier(); // Increase delay for next attempt
            }
        }

        // Only call handleRefreshFailure if sessionToken is null after all attempts and errors
        if (sessionToken == null) {
            try {
                handleRefreshFailure(accessToken, refreshToken, configuration);
            } catch (Exception e) {
                errorMessage =
                        "Could not successfully perform the 'doLogout' procedure due to an internal error.";
                LOGGER.error(errorMessage, e);
            }
        }
        return sessionToken;
    }

    /**
     * Handles the refresh failure by clearing the session, logging out remotely, and redirecting to
     * log in.
     *
     * @param accessToken the current access token
     * @param refreshToken the current refresh token
     * @param configuration the OAuth2Configuration with endpoint details
     */
    public void handleRefreshFailure(
            String accessToken, String refreshToken, OAuth2Configuration configuration) {
        LOGGER.info(
                "Unable to refresh token after max retries. Clearing session and redirecting to login.");
        HttpServletResponse response = getResponse();
        if (response != null) {
            clearRefreshTokenCookie(response);
        }
        doLogout(null);

        try {
            if (configuration != null && configuration.getProvider() != null) {
                String redirectUrl =
                        "../../openid/" + configuration.getProvider().toLowerCase() + "/login";
                getResponse().sendRedirect(redirectUrl);
            }
        } catch (IOException e) {
            LOGGER.error("Error while sending redirect to login service: ", e);
            throw new RuntimeException("Failed to redirect to login", e);
        }
    }

    private static HttpHeaders getHttpHeaders(
            String accessToken, OAuth2Configuration configuration) {
        HttpHeaders headers = new HttpHeaders();
        if (configuration != null
                && configuration.getClientId() != null
                && configuration.getClientSecret() != null)
            headers.setBasicAuth(
                    configuration.getClientId(),
                    configuration.getClientSecret()); // Set client ID and client secret for
        // authentication
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
    protected void updateAuthToken(
            String oldToken,
            OAuth2AccessToken newToken,
            OAuth2RefreshToken refreshToken,
            OAuth2Configuration conf) {
        Authentication authentication = cache().get(oldToken);
        if (authentication == null)
            authentication = SecurityContextHolder.getContext().getAuthentication();

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
            LOGGER.info("Creating new details. AccessToken: {} IdToken: {}", accessToken, idToken);
            updated.setDetails(new TokenDetails(accessToken, idToken, conf.getBeanName()));
            cache().putCacheEntry(newToken.getValue(), updated);
            SecurityContextHolder.getContext().setAuthentication(updated);
        }
    }

    protected TokenDetails getTokenDetails(Authentication authentication) {
        return OAuth2Utils.getTokenDetails(authentication);
    }

    protected OAuth2AccessToken retrieveAccessToken(String accessToken, Long expires) {
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
        if (result == null) {
            result = new DefaultOAuth2AccessToken(accessToken);
            if (expires != null && expires > 0) {
                ((DefaultOAuth2AccessToken) result).setExpiration(new Date(expires));
            }
        }
        return result;
    }

    protected HttpServletRequest getRequest() {
        return OAuth2Utils.getRequest();
    }

    protected HttpServletResponse getResponse() {
        return OAuth2Utils.getResponse();
    }

    @Override
    public void doLogout(String sessionId) {
        HttpServletRequest request = getRequest();
        HttpServletResponse response = getResponse();
        OAuth2RestTemplate restTemplate = restTemplate();
        OAuth2Configuration configuration = configuration();

        // Check if request, response, or configuration are null
        if (request == null || response == null || configuration == null) {
            LOGGER.warn(
                    "Request, response, or configuration is null, unable to proceed with logout.");
            return;
        }

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
            if (restTemplate != null
                    && restTemplate.getOAuth2ClientContext() != null
                    && restTemplate.getOAuth2ClientContext().getAccessToken() != null
                    && restTemplate.getOAuth2ClientContext().getAccessToken().getRefreshToken()
                            != null) {
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
            if (token == null && RequestContextHolder.getRequestAttributes() != null) {
                token =
                        (String)
                                RequestContextHolder.getRequestAttributes()
                                        .getAttribute(REFRESH_TOKEN_PARAM, 0);
            }
        }

        if (accessToken == null) {
            if (restTemplate != null
                    && restTemplate.getOAuth2ClientContext() != null
                    && restTemplate.getOAuth2ClientContext().getAccessToken() != null) {
                accessToken = restTemplate.getOAuth2ClientContext().getAccessToken().getValue();
            }
            if (accessToken == null) {
                accessToken = OAuth2Utils.getParameterValue(ACCESS_TOKEN_PARAM, request);
            }
            if (accessToken == null && RequestContextHolder.getRequestAttributes() != null) {
                accessToken =
                        (String)
                                RequestContextHolder.getRequestAttributes()
                                        .getAttribute(ACCESS_TOKEN_PARAM, 0);
            }
        }

        if (configuration.isEnabled()) {
            if (token != null
                    && accessToken != null
                    && !token.isEmpty()
                    && !accessToken.isEmpty()) {
                if (configuration.isGlobalLogoutEnabled())
                    doLogoutInternal(token, configuration, accessToken);
                if (configuration.getRevokeEndpoint() != null) clearSession(restTemplate, request);
            } else {
                LOGGER.debug("Unable to retrieve access token. Remote logout was not executed.");
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
        if (tokenValue == null) return;

        // Revoke the token if a revocation endpoint is available
        if (configuration.getRevokeEndpoint() != null) {
            LOGGER.debug("Revoking token at revocation endpoint");
            callRevokeEndpoint(tokenValue, accessToken);
        }

        // Call the remote logout endpoint (end_session_endpoint) independently
        LOGGER.debug("Performing remote logout");
        callRemoteLogout(tokenValue, accessToken);
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
        Cookie[] allCookies = request.getCookies();
        if (allCookies != null)
            for (Cookie toDelete : allCookies) {
                if (deleteCookie(toDelete)) {
                    toDelete.setMaxAge(-1);
                    toDelete.setPath("/");
                    toDelete.setComment("EXPIRING COOKIE at " + System.currentTimeMillis());
                    if (toDelete.getName().equalsIgnoreCase(REFRESH_TOKEN_PARAM)) {
                        toDelete.setHttpOnly(true);
                        toDelete.setSecure(request.isSecure());
                    }
                    response.addCookie(toDelete);
                }
            }
    }

    protected boolean deleteCookie(Cookie c) {
        return c.getName().equalsIgnoreCase("JSESSIONID")
                || c.getName().equalsIgnoreCase(ACCESS_TOKEN_PARAM)
                || c.getName().equalsIgnoreCase(REFRESH_TOKEN_PARAM);
    }

    protected String getRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (REFRESH_TOKEN_PARAM.equalsIgnoreCase(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    protected void setRefreshTokenCookie(
            HttpServletResponse response, String refreshToken, boolean secure) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_PARAM, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setMaxAge(604800); // 7 days
        response.addCookie(cookie);
    }

    protected void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_PARAM, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    protected TokenAuthenticationCache cache() {
        return GeoStoreContext.bean("oAuth2Cache", TokenAuthenticationCache.class);
    }

    /**
     * Get the OAuth2Configuration. Prefers the provider-specific bean ({delegateName}OAuth2Config)
     * and falls back to iterating all enabled configurations.
     *
     * @return the OAuth2Configuration.
     */
    protected OAuth2Configuration configuration() {
        // Try provider-specific bean first
        if (delegateName != null) {
            OAuth2Configuration specific =
                    GeoStoreContext.bean(
                            delegateName + OAuth2Configuration.CONFIG_NAME_SUFFIX,
                            OAuth2Configuration.class);
            if (specific != null && specific.isEnabled()) {
                return specific;
            }
        }
        // Fallback: iterate all configurations
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
        } else {
            LOGGER.error("OAuth2Configuration is not initialized properly.");
            throw new IllegalStateException("Configuration is required but not initialized.");
        }
        return null;
    }

    protected HttpMessageConverterExtractor<OAuth2AccessToken> tokenExtractor() {
        return new HttpMessageConverterExtractor<>(
                OAuth2AccessToken.class, restTemplate().getMessageConverters());
    }

    protected abstract OAuth2RestTemplate restTemplate();

    /**
     * Creates a plain RestTemplate for token refresh requests. Using a plain RestTemplate avoids
     * OAuth2RestTemplate interceptors that can trigger UserRedirectRequiredException when the
     * current access token is expired.
     */
    protected RestTemplate createRefreshRestTemplate() {
        return new RestTemplate();
    }

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
