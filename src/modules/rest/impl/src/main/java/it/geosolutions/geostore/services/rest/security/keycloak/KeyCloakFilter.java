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
package it.geosolutions.geostore.services.rest.security.keycloak;

import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils;
import org.apache.log4j.Logger;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.RequestAuthenticator;
import org.keycloak.adapters.spi.AuthOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;


import static it.geosolutions.geostore.services.rest.SessionServiceDelegate.PROVIDER_KEY;
import static it.geosolutions.geostore.services.rest.security.keycloak.KeyCloakLoginService.KEYCLOAK_REDIRECT;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.REFRESH_TOKEN_PARAM;

/**
 * Keycloak Authentication Filter. Manage the logic to authenticate a user against a keycloak server.
 */
public class KeyCloakFilter extends GenericFilterBean {


    // used to map keycloak roles to spring-security roles
    private final GeoStoreKeycloakAuthProvider authenticationProvider;
    // creates token stores capable of generating spring-security tokens from keycloak auth
    // the context of the keycloak environment (realm, URL, client-secrets etc.)
    private KeyCloakHelper helper;

    private KeyCloakConfiguration configuration;

    @Autowired
    protected UserService userService;

    private TokenAuthenticationCache cache;

    private final static Logger LOGGER = Logger.getLogger(KeyCloakFilter.class);


    /**
     * @param helper a {@link KeyCloakHelper} instance.
     * @param cache an instance of {@link TokenAuthenticationCache} to cache authentication objects.
     * @param configuration the {@link KeyCloakConfiguration} for this geostore instance.
     * @param authenticationProvider the authentication provider to map the Keycloak Authentication to the GeoStore one.
     */
    public KeyCloakFilter (KeyCloakHelper helper, TokenAuthenticationCache cache, KeyCloakConfiguration configuration, GeoStoreKeycloakAuthProvider authenticationProvider){
        this.helper=helper;
        this.authenticationProvider = authenticationProvider;
        this.cache=cache;
        this.configuration=configuration;
    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (enabledAndValid() && SecurityContextHolder.getContext().getAuthentication()==null) {
            Authentication authentication = authenticate((HttpServletRequest) request, (HttpServletResponse) response);
            if (authentication != null){
                SecurityContextHolder.getContext().setAuthentication(authentication);
                if (authentication.getDetails() instanceof KeycloakTokenDetails){
                    KeycloakTokenDetails details=(KeycloakTokenDetails) authentication.getDetails();
                    if (details.getAccessToken()!=null) RequestContextHolder.getRequestAttributes().setAttribute(ACCESS_TOKEN_PARAM,details.getAccessToken(),0);
                    if (details.getRefreshToken()!=null) RequestContextHolder.getRequestAttributes().setAttribute(REFRESH_TOKEN_PARAM,details.getRefreshToken(),0);
                }
            }
            RequestContextHolder.getRequestAttributes().setAttribute(PROVIDER_KEY,"keycloak",0);
        }
        chain.doFilter(request,response);
    }

    private boolean enabledAndValid(){
        return configuration.isEnabled() && configuration.getJsonConfig()!=null;
    }

    /**
     * Perform the authentication and updates the cache.
     * @param request the request.
     * @param response the response.
     * @return the authentication object. Can be null if the user is not authenticated.
     */
    protected Authentication authenticateAndUpdateCache(HttpServletRequest request, HttpServletResponse response) {
        // do some setup and create the authenticator
        KeycloakDeployment deployment=helper.getDeployment(request,response);
        RequestAuthenticator authenticator = helper.getAuthenticator(request,response,deployment);
        // perform the authentication operation
        AuthOutcome result = authenticator.authenticate();
        Authentication auth=null;
        if (result.equals(AuthOutcome.AUTHENTICATED)) {
                auth = SecurityContextHolder.getContext().getAuthentication();
                auth=authenticationProvider.authenticate(auth);
                updateCache(auth);
        } else if (result.equals(AuthOutcome.NOT_ATTEMPTED)){
            AuthenticationEntryPoint entryPoint;
            if (deployment.isBearerOnly()) {
                // if bearer-only, then missing auth means you are forbidden
                entryPoint=new KeycloakAuthenticationEntryPoint(null);
            } else {
                entryPoint = new KeycloakAuthenticationEntryPoint(authenticator.getChallenge());
            }
            RequestContextHolder.getRequestAttributes().setAttribute(KEYCLOAK_REDIRECT,entryPoint,0);
        } else {
            LOGGER.warn("Failed to authentication and to redirect the user.");
        }
        return auth;
    }

    /**
     * Updates the cache with the new Authentication entry.
     * @param authentication the new Authentication entry.
     */
    protected void updateCache(Authentication authentication){
        Object details=authentication.getDetails();
        if (details instanceof KeycloakTokenDetails){
            KeycloakTokenDetails keycloakDetails=(KeycloakTokenDetails) details;
            String accessToken=keycloakDetails.getAccessToken();
            if (accessToken!=null){
                cache.putCacheEntry(accessToken,authentication);
            }
        }
    }

    /**
     * Performs the authentication. The method will check the cache before calling keycloak.
     * If the token is expired, a new authentication is anyway issued and the cache updated.
     * @param request the request.
     * @param response the response.
     * @return the authentication.
     */
    protected Authentication authenticate(HttpServletRequest request,HttpServletResponse response){
        Authentication authentication=null;
        String token = OAuth2Utils.tokenFromParamsOrBearer(ACCESS_TOKEN_PARAM, request);
        if (token != null) {
            authentication = cache.get(token);
            if (authentication!=null && authentication.getDetails() instanceof KeycloakTokenDetails){
                KeycloakTokenDetails details=(KeycloakTokenDetails) authentication.getDetails();
                if (details.getExpiration().before(new Date())){
                    LOGGER.warn("Token has expired and the refresh token endpoint has not been called. The request will not be authorized by the keycloak filter");
                    cache.removeEntry(details.getAccessToken());
                    authentication=null;
                }
            }
            if (authentication == null) {
                authentication = authenticateAndUpdateCache(request, response);
            }
        } else {
            authentication=authenticateAndUpdateCache(request,response);
        }
        return authentication;
    }

}
