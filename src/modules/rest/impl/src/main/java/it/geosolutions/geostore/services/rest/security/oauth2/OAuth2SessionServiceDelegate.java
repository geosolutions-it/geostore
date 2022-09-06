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

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.security.password.SecurityUtils;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.RESTSessionService;
import it.geosolutions.geostore.services.rest.SessionServiceDelegate;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.SessionToken;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.keycloak.KeycloakTokenDetails;
import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;
import org.apache.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.token.AccessTokenRequest;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.DefaultOAuth2RefreshToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpMessageConverterExtractor;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.RestTemplate;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.REFRESH_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.fiveMinutesFromNow;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.getParameterValue;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.getRequest;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.getResponse;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.getTokenDetails;

/**
 * Abstract implementation of an OAuth2 SessionServiceDelegate.
 */
public abstract class OAuth2SessionServiceDelegate implements SessionServiceDelegate {

    private final static Logger LOGGER = Logger.getLogger(OAuth2SessionServiceDelegate.class);

    protected UserService userService;

    /**
     * @param restSessionService the session service to which register this delegate.
     * @param delegateName       this delegate name eg. google or github etc...
     */
    public OAuth2SessionServiceDelegate(RESTSessionService restSessionService, String delegateName,UserService userService) {
        restSessionService.registerDelegate(delegateName, this);
        this.userService=userService;
    }


    @Override
    public SessionToken refresh(String refreshToken, String accessToken) {
        HttpServletRequest request = getRequest();
        if (accessToken == null) accessToken = OAuth2Utils.tokenFromParamsOrBearer(ACCESS_TOKEN_PARAM, request);
        if (accessToken == null) throw new NotFoundWebEx("Either the accessToken or the refresh token are missing");

        OAuth2AccessToken currentToken = retrieveAccessToken(accessToken);
        Date expiresIn = currentToken.getExpiration();
        if (refreshToken == null) refreshToken = getParameterValue(REFRESH_TOKEN_PARAM, request);
        Date fiveMinutesFromNow = fiveMinutesFromNow();
        SessionToken sessionToken = null;
        OAuth2Configuration configuration = configuration();
        if ((expiresIn == null || fiveMinutesFromNow.after(expiresIn)) && refreshToken != null) {
            if (LOGGER.isDebugEnabled())
                LOGGER.info("Going to refresh the token.");
            doRefresh(refreshToken, accessToken, configuration);
        }
        if (sessionToken == null)
            sessionToken = sessionToken(accessToken, refreshToken, currentToken.getExpiration());
        return sessionToken;
    }


    /**
     * Invokes the refresh endpoint and return a session token holding the updated tokens details.
     *
     * @param refreshToken  the refresh token.
     * @param accessToken   the access token.
     * @param configuration the OAuth2Configuration.
     * @return the SessionToken.
     */
    protected SessionToken doRefresh(String refreshToken, String accessToken, OAuth2Configuration configuration) {
        SessionToken sessionToken = null;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_secret", configuration.getClientSecret());
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        OAuth2AccessToken newToken = restTemplate.execute(configuration.buildRefreshTokenURI(), HttpMethod.POST, new RefreshTokenRequestCallback(form, headers), tokenExtractor());
        if (newToken != null && newToken.getValue() != null) {
            String refreshed = newToken.getValue();
            // update the Authentication
            updateAuthToken(accessToken, newToken, refreshToken, configuration);
            sessionToken = sessionToken(refreshed, refreshToken, newToken.getExpiration());
        } else {
            // the refresh token was invalid. lets clear the session and send a remote logout.
            // then redirects to the login entry point.
            LOGGER.info("Unable to refresh the token. The following request was performed: " + configuration.buildRefreshTokenURI("offline") + ". Redirecting to login.");
            doLogout(null);
            try {
                getResponse().sendRedirect("../../openid/" + configuration.getProvider().toLowerCase() + "/login");
            } catch (IOException e) {
                LOGGER.error("Error while sending redirect to login service. ", e);
                throw new RuntimeException(e);
            }
        }
        return sessionToken;
    }

    private SessionToken sessionToken(String accessToken, String refreshToken, Date expires) {
        SessionToken sessionToken = new SessionToken();
        sessionToken.setExpires(Long.valueOf(expires.getTime()));
        sessionToken.setAccessToken(accessToken);
        sessionToken.setRefreshToken(refreshToken);
        sessionToken.setTokenType("bearer");
        return sessionToken;
    }

    // Builds an authentication instance out of the passed values.
    // Sets it to the cache and to the SecurityContext to be sure the new token is updates.
    private Authentication updateAuthToken(String oldToken, OAuth2AccessToken newToken, String refreshToken, OAuth2Configuration conf) {
        Authentication authentication = cache().get(oldToken);
        if (authentication == null)
            authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof PreAuthenticatedAuthenticationToken) {
            if (LOGGER.isDebugEnabled())
                LOGGER.info("Updating the cache and the SecurityContext with new Auth details");
            String idToken = null;
            TokenDetails details = getTokenDetails(authentication);
            idToken = details.getIdToken();
            cache().removeEntry(oldToken);
            PreAuthenticatedAuthenticationToken updated = new PreAuthenticatedAuthenticationToken(authentication.getPrincipal(), authentication.getCredentials(), authentication.getAuthorities());
            DefaultOAuth2AccessToken accessToken = new DefaultOAuth2AccessToken(newToken);
            if (refreshToken != null) {
                accessToken.setRefreshToken(new DefaultOAuth2RefreshToken(refreshToken));
            }
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Creating new details. AccessToken: " + accessToken + " IdToken: " + idToken);
            updated.setDetails(new TokenDetails(accessToken, idToken, conf.getBeanName()));
            cache().putCacheEntry(newToken.getValue(), updated);
            SecurityContextHolder.getContext().setAuthentication(updated);
            authentication = updated;
        }
        return authentication;
    }

    private OAuth2AccessToken retrieveAccessToken(String accessToken) {
        Authentication authentication = cache().get(accessToken);
        OAuth2AccessToken result = null;
        if (authentication != null) {
            TokenDetails details = OAuth2Utils.getTokenDetails(authentication);
            result = details.getAccessToken();
        }
        if (result == null) {
            OAuth2ClientContext context = GeoStoreContext.bean(OAuth2RestTemplate.class).getOAuth2ClientContext();
            if (context != null) result = context.getAccessToken();
        }
        if (result == null)
            result = new DefaultOAuth2AccessToken(accessToken);
        return result;
    }

    @Override
    public void doLogout(String accessToken) {
        HttpServletRequest request = getRequest();
        HttpServletResponse response = getResponse();
        OAuth2RestTemplate restTemplate = restTemplate();
        if (accessToken == null)
            accessToken = OAuth2Utils.getParameterValue(ACCESS_TOKEN_PARAM, request);
        TokenAuthenticationCache cache = cache();
        Authentication authentication = cache.get(accessToken);
        OAuth2AccessToken token = null;
        TokenDetails tokenDetails = getTokenDetails(authentication);
        if (tokenDetails != null) token = tokenDetails.getAccessToken();
        cache.removeEntry(accessToken);
        if (token == null)
            token = restTemplate.getOAuth2ClientContext().getAccessToken();
        if (token != null) {
            OAuth2Configuration configuration = configuration();
            doLogoutInternal(token, configuration);
            clearSession(restTemplate,request);
        } else {
            if (LOGGER.isDebugEnabled())
                LOGGER.info("Unable to retrieve access token. Remote logout was not executed.");
        }
        if (request != null && response != null)
            clearCookies(request, response);
    }

    // clears any state Spring OAuth2 object might preserve.
    private void clearSession(OAuth2RestTemplate restTemplate,HttpServletRequest request) {
        final AccessTokenRequest accessTokenRequest =
                restTemplate.getOAuth2ClientContext().getAccessTokenRequest();
        if (accessTokenRequest != null && accessTokenRequest.getStateKey() != null) {
            restTemplate
                    .getOAuth2ClientContext()
                    .removePreservedState(accessTokenRequest.getStateKey());
        }
        try {
            accessTokenRequest.remove("access_token");
            accessTokenRequest.remove("refresh_token");
            request.logout();
        } catch (ServletException e){
            LOGGER.error("Error happened while doing request logout: ",e);
        }
        finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Call the revoke/logout endpoint of the OAuth2 provider.
     *
     * @param token         the access token.
     * @param configuration the OAuth2Configuration
     */
    protected void doLogoutInternal(OAuth2AccessToken token, OAuth2Configuration configuration) {
        String tokenValue = token.getRefreshToken() != null ? token.getRefreshToken().getValue() : token.getValue();
        if (configuration.getRevokeEndpoint() != null && tokenValue != null) {
            if (LOGGER.isDebugEnabled())
                LOGGER.info("Performing remote logout");
            callRevokeEndpoint(tokenValue, configuration.getRevokeEndpoint());
            callRemoteLogout(token.getValue(),configuration.getLogoutUri());
        }
    }

    protected void callRevokeEndpoint(String token, String url) {
        OAuth2Configuration configuration = configuration();
        OAuth2Configuration.Endpoint revokeEndpoint = configuration.buildRevokeEndpoint(token);
        if (revokeEndpoint!=null) {
            RestTemplate template = new RestTemplate();
            ResponseEntity<String> responseEntity = template.exchange(revokeEndpoint.getUrl(), revokeEndpoint.getMethod(), null, String.class);
            if (responseEntity.getStatusCode().value() != 200) {
                LOGGER.error("Error while revoking authorization. Error is: " + responseEntity.getBody());
            }
        }
    }

    protected void callRemoteLogout(String token, String url) {
        OAuth2Configuration configuration = configuration();
        OAuth2Configuration.Endpoint logoutEndpoint = configuration.buildLogoutEndpoint(token);
        if (logoutEndpoint!=null) {
            RestTemplate template = new RestTemplate();
            ResponseEntity<String> responseEntity = template.exchange(logoutEndpoint.getUrl(), logoutEndpoint.getMethod(), null, String.class);
            if (responseEntity.getStatusCode().value() != 200) {
                LOGGER.error("Error while revoking authorization. Error is: " + responseEntity.getBody());
            }
        }
    }

    protected void clearCookies(HttpServletRequest request, HttpServletResponse response) {
        javax.servlet.http.Cookie[] allCookies = request.getCookies();
        if (allCookies != null && allCookies.length > 0)
            for (int i = 0; i < allCookies.length; i++) {
                javax.servlet.http.Cookie toDelete = allCookies[i];
                if (deleteCookie(toDelete)) {
                    toDelete.setMaxAge(-1);
                    toDelete.setPath("/");
                    toDelete.setComment("EXPIRING COOKIE at " + System.currentTimeMillis());
                    response.addCookie(toDelete);
                }
            }
    }


    protected boolean deleteCookie(javax.servlet.http.Cookie c) {
        return c.getName().equalsIgnoreCase("JSESSIONID") || c.getName().equalsIgnoreCase(ACCESS_TOKEN_PARAM) || c.getName().equalsIgnoreCase(REFRESH_TOKEN_PARAM);
    }

    private TokenAuthenticationCache cache() {
        return GeoStoreContext.bean("oAuth2Cache",TokenAuthenticationCache.class);
    }

    /**
     * Get the OAuth2Configuration.
     *
     * @return the OAuth2Configuration.
     */
    protected abstract OAuth2Configuration configuration();

    /**
     * Get an OAuth2Configuration by bean name.
     *
     * @param configBeanName the bean name.
     * @return the config bean.
     */
    protected OAuth2Configuration configuration(String configBeanName) {
        return GeoStoreContext.bean(configBeanName,OAuth2Configuration.class);
    }

    /**
     * A RequestCallback used when performing a refresh token request.
     */
    protected class RefreshTokenRequestCallback implements RequestCallback {

        private final MultiValueMap<String, String> form;

        private final HttpHeaders headers;

        private RefreshTokenRequestCallback(MultiValueMap<String, String> form, HttpHeaders headers) {
            this.form = form;
            this.headers = headers;
        }

        public void doWithRequest(ClientHttpRequest request) throws IOException {
            request.getHeaders().putAll(this.headers);
            request.getHeaders().setAccept(
                    Arrays.asList(MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.TEXT_PLAIN, MediaType.APPLICATION_FORM_URLENCODED));
            new FormHttpMessageConverter().write(this.form, MediaType.APPLICATION_FORM_URLENCODED, request);
        }
    }

    protected HttpMessageConverterExtractor<OAuth2AccessToken> tokenExtractor() {
        return new HttpMessageConverterExtractor<>(OAuth2AccessToken.class, restTemplate().getMessageConverters());
    }


    protected abstract OAuth2RestTemplate restTemplate();

    @Override
    public User getUser(String sessionId, boolean refresh, boolean autorefresh) {
        String username=getUserName(sessionId,refresh,autorefresh);
        if (username!=null) {
            User user;
            try {
                user=userService.get(username);
            } catch (Exception e){
                LOGGER.warn("Issue while retrieving user. Will return just the username.", e);
                user=new User();
                user.setName(username);
            }
            return user;
        }
        return null;
    }

    @Override
    public String getUserName(String sessionId, boolean refresh, boolean autorefresh) {
        TokenAuthenticationCache cache=cache();
        Authentication authentication=cache.get(sessionId);
        if (refresh) LOGGER.warn("Refresh was set to true but this delegate is " +
                "not supporting refreshing token when retrieving the user...");
        if (authentication!=null){
            Object o=authentication.getPrincipal();
            if (o !=null) return SecurityUtils.getUsername(o);
        }
        return null;
    }

}
