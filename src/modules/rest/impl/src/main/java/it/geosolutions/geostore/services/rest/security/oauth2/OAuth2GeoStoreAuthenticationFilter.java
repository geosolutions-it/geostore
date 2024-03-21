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
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.UserGroupService;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.filter.OAuth2ClientAuthenticationProcessingFilter;
import org.springframework.security.oauth2.client.http.AccessTokenRequiredException;
import org.springframework.security.oauth2.client.resource.OAuth2AccessDeniedException;
import org.springframework.security.oauth2.client.resource.UserRedirectRequiredException;
import org.springframework.security.oauth2.client.token.AccessTokenRequest;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeResourceDetails;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.RemoteTokenServices;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static it.geosolutions.geostore.core.security.password.SecurityUtils.getUsername;
import static it.geosolutions.geostore.services.rest.SessionServiceDelegate.PROVIDER_KEY;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.REFRESH_TOKEN_PARAM;

/**
 * Base filter class for an OAuth2 authentication filter. Authentication instances are cached.
 */
public abstract class OAuth2GeoStoreAuthenticationFilter extends OAuth2ClientAuthenticationProcessingFilter {

    public static final String OAUTH2_AUTHENTICATION_KEY = "oauth2.authentication";
    public static final String OAUTH2_AUTHENTICATION_TYPE_KEY = "oauth2.authenticationType";
    public static final String OAUTH2_ACCESS_TOKEN_CHECK_KEY = "oauth2.AccessTokenCheckResponse";
    private final static Logger LOGGER = LogManager.getLogger(OAuth2GeoStoreAuthenticationFilter.class);
    private final AuthenticationEntryPoint authEntryPoint;
    private final TokenAuthenticationCache cache;
    @Autowired
    protected UserService userService;
    @Autowired
    protected UserGroupService userGroupService;
    protected RemoteTokenServices tokenServices;
    protected OAuth2Configuration configuration;

    /**
     * @param tokenServices            a RemoteTokenServices instance.
     * @param oAuth2RestTemplate       the rest template to use for OAuth2 requests.
     * @param configuration            the OAuth2 configuration.
     * @param tokenAuthenticationCache the cache.
     */
    public OAuth2GeoStoreAuthenticationFilter(RemoteTokenServices tokenServices, GeoStoreOAuthRestTemplate oAuth2RestTemplate, OAuth2Configuration configuration, TokenAuthenticationCache tokenAuthenticationCache) {
        super("/**");
        super.setTokenServices(tokenServices);
        this.tokenServices = tokenServices;
        super.restTemplate = oAuth2RestTemplate;
        this.configuration = configuration;
        this.authEntryPoint = configuration.getAuthenticationEntryPoint();
        this.cache = tokenAuthenticationCache;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // do we need to authenticate?
        if (configuration.isEnabled() && !configuration.isInvalid() && authentication == null)
            super.doFilter(req, res, chain);
        else if (req instanceof HttpServletRequest)
            // ok no need to authenticate, but in case the security context
            // holds a Token authentication, we set the access token to request's attributes.
            addRequestAttributes((HttpServletRequest) req, authentication);
        if (configuration.isEnabled() && configuration.isInvalid())
            if (LOGGER.isDebugEnabled())
                LOGGER.info("Skipping configured OAuth2 authentication. One or more mandatory properties are missing (clientId, clientSecret, authorizationUri, tokenUri");
        chain.doFilter(req, res);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {
        Authentication authentication;
        String token = OAuth2Utils.tokenFromParamsOrBearer(ACCESS_TOKEN_PARAM, request);

        if (token != null) {
            request.setAttribute(OAUTH2_AUTHENTICATION_TYPE_KEY, OAuth2AuthenticationType.BEARER);
        } else {
            request.setAttribute(OAUTH2_AUTHENTICATION_TYPE_KEY, OAuth2AuthenticationType.USER);
        }

        if (token != null) {
            authentication = cache.get(token);
            if (authentication == null) {
                authentication = authenticateAndUpdateCache(request, response, token, new DefaultOAuth2AccessToken(token));
            } else {
                TokenDetails details = tokenDetails(authentication);
                if (details != null) {
                    OAuth2AccessToken accessToken = details.getAccessToken();
                    if (accessToken.isExpired())
                        authentication = authenticateAndUpdateCache(request, response, token, accessToken);
                }
            }
        } else {
            clearState();
            authentication = authenticateAndUpdateCache(request, response, null, null);
        }
        return authentication;
    }

    private TokenDetails tokenDetails(Authentication authentication) {
        TokenDetails tokenDetails = null;
        Object details = authentication.getDetails();
        if (details instanceof TokenDetails) {
            tokenDetails = ((TokenDetails) details);
        }
        return tokenDetails;
    }

    private Authentication authenticateAndUpdateCache(HttpServletRequest request, HttpServletResponse response, String token, OAuth2AccessToken accessToken) {
        Authentication authentication = performOAuthAuthentication(request, response, accessToken);
        if (authentication != null) {
            TokenDetails tokenDetails = tokenDetails(authentication);
            if (tokenDetails != null) {
                token = tokenDetails.getAccessToken().getValue();
            }
            cache.putCacheEntry(token, authentication);
        }
        return authentication;
    }

    private void clearState() {
        OAuth2ClientContext clientContext = restTemplate.getOAuth2ClientContext();
        final AccessTokenRequest accessTokenRequest =
                clientContext.getAccessTokenRequest();
        if (accessTokenRequest != null && accessTokenRequest.getStateKey() != null) {
            clientContext
                    .removePreservedState(accessTokenRequest.getStateKey());
        }
        if (accessTokenRequest != null) {
            try {
                accessTokenRequest.remove(ACCESS_TOKEN_PARAM);
            } finally {
                SecurityContextHolder.clearContext();
                HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                        .getRequest();
                HttpSession session = request.getSession(false);
                if (session != null)
                    session.invalidate();
                LOGGER.debug("Cleaned out Session Access Token Request!");
            }
        }
    }

    /**
     * Perform the authentication.
     *
     * @param request     the httpServletRequest.
     * @param response    the httpServletResponse.
     * @param accessToken the accessToken.
     * @return the Authentication object. Null if not authenticated.
     */
    protected Authentication performOAuthAuthentication(HttpServletRequest request, HttpServletResponse response, OAuth2AccessToken accessToken) {
        LOGGER.debug("About to perform remote authentication.");
        LOGGER.debug("Access Token: " + accessToken);
        String principal = null;
        PreAuthenticatedAuthenticationToken result = null;
        try {
            LOGGER.debug("Trying to get the preauthenticated principal.");
            principal = getPreAuthenticatedPrincipal(request, response, accessToken);
        } catch (IOException e1) {
            LOGGER.error(e1.getMessage(), e1);
            principal = null;
        } catch (ServletException e1) {
            LOGGER.error(e1.getMessage(), e1);
            principal = null;
        }

        LOGGER.debug(
                "preAuthenticatedPrincipal = " + principal + ", trying to authenticate");

        if (principal != null && principal.trim().length() > 0)
            result = createPreAuthentication(principal, request, response);
        return result;

    }

    /**
     * Get the PreAuthenticatedPrincipal.
     *
     * @param req         the request.
     * @param resp        the response.
     * @param accessToken the access token.
     * @return the principal as a string.
     * @throws IOException
     * @throws ServletException
     */
    protected String getPreAuthenticatedPrincipal(HttpServletRequest req, HttpServletResponse resp, OAuth2AccessToken accessToken)
            throws IOException, ServletException {

        // Make sure the REST Resource Template has been correctly configured
        LOGGER.debug("About to configure the REST Resource Template");
        configureRestTemplate();

        if (accessToken != null) {
            LOGGER.debug("Setting the access token on the OAuth2ClientContext");
            restTemplate
                    .getOAuth2ClientContext()
                    .setAccessToken(accessToken);
        }

        // Setting up OAuth2 Filter services and resource template
        LOGGER.debug("Setting up OAuth2 Filter services and resource template");
        setRestTemplate(restTemplate);
        setTokenServices(tokenServices);

        // Validating the access_token
        Authentication authentication = null;
        try {
            authentication = super.attemptAuthentication(req, resp);
            LOGGER.debug("Authentication result: " + authentication);
            req.setAttribute(OAUTH2_AUTHENTICATION_KEY, authentication);

            // The authentication (in the extensions) should contain a Map which is the result of
            // the Access Token Check Request (which will be the json result from the oidc "userinfo"
            // endpoint).
            // We move it from inside the authentication to directly to a request attributes.
            // This will make it a "peer" with the Access Token (which spring puts on the request as
            // an attribute).
            if (authentication instanceof OAuth2Authentication) {
                OAuth2Authentication oAuth2Authentication = (OAuth2Authentication) authentication;
                Object map =
                        oAuth2Authentication
                                .getOAuth2Request()
                                .getExtensions()
                                .get(OAUTH2_ACCESS_TOKEN_CHECK_KEY);
                if (map instanceof Map) {
                    req.setAttribute(OAUTH2_ACCESS_TOKEN_CHECK_KEY, map);
                }
            }

            if (authentication != null && LOGGER.isDebugEnabled())
                LOGGER.debug(
                        "Authenticated OAuth request for principal " +
                                authentication.getPrincipal());
        } catch (Exception e) {
            handleOAuthException(e, req, resp);
        }

        String username =
                (authentication != null
                        ? getUsername(authentication.getPrincipal())
                        : null);
        if (username != null && username.trim().length() == 0) username = null;
        return username;
    }

    private void handleOAuthException(Exception e, HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        if (e instanceof UserRedirectRequiredException && configuration.isEnableRedirectEntryPoint()) {
            handleUserRedirection(req, resp);
        } else if (e instanceof BadCredentialsException
                || e instanceof ResourceAccessException) {
            if (e.getCause() instanceof OAuth2AccessDeniedException) {
                LOGGER.warn(
                        "Error while trying to authenticate to OAuth2 Provider with the following Exception cause:",
                        e.getCause());
            } else if (e instanceof ResourceAccessException) {
                LOGGER.error(
                        "Could not Authorize OAuth2 Resource due to the following exception:",
                        e);
            } else if (e instanceof ResourceAccessException
                    || e.getCause() instanceof OAuth2AccessDeniedException) {
                LOGGER.warn(
                        "It is worth notice that if you try to validate credentials against an SSH protected Endpoint, you need either your server exposed on a secure SSL channel or OAuth2 Provider Certificate to be trusted on your JVM!");
                LOGGER.info(
                        "Please refer to the GeoServer OAuth2 Plugin Documentation in order to find the steps for importing the SSH certificates.");
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.error(
                            "Could not Authorize OAuth2 Resource due to the following exception:",
                            e);
                }
            }
        }
    }

    private void handleUserRedirection(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        if (req.getRequestURI().contains(configuration.getProvider() + "/login")) {
            authEntryPoint.commence(req, resp, null);
        } else {
            if (resp.getStatus() != 302) {
                // AEP redirection failed
                final AccessTokenRequest accessTokenRequest =
                        restTemplate.getOAuth2ClientContext().getAccessTokenRequest();
                if (accessTokenRequest.getPreservedState() != null
                        && accessTokenRequest.getStateKey() != null) {
                    accessTokenRequest.remove("state");
                    accessTokenRequest.remove(accessTokenRequest.getStateKey());
                    accessTokenRequest.setPreservedState(null);
                }
            }
        }
    }

    protected void configureRestTemplate() {
        AuthorizationCodeResourceDetails details =
                (AuthorizationCodeResourceDetails) restTemplate.getResource();

        details.setClientId(configuration.getClientId());
        details.setClientSecret(configuration.getClientSecret());
        this.tokenServices.setClientId(configuration.getClientId());
        this.tokenServices.setClientSecret(configuration.getClientSecret());
        details.setAccessTokenUri(configuration.getAccessTokenUri());
        details.setUserAuthorizationUri(configuration.getAuthorizationUri());
        details.setPreEstablishedRedirectUri(configuration.getRedirectUri());
        this.tokenServices.setCheckTokenEndpointUrl(configuration.getCheckTokenEndpointUrl());
        details.setScope(parseScopes(Stream.of(configuration.getScopes()).collect(Collectors.joining(","))));
    }

    /**
     * Parse the scopes from a comma separated string to a list.
     *
     * @param commaSeparatedScopes the scopes as a string.
     * @return the scopes as a list.
     */
    protected List<String> parseScopes(String commaSeparatedScopes) {
        List<String> scopes = newArrayList();
        Collections.addAll(scopes, commaSeparatedScopes.split(","));
        return scopes;
    }

    /**
     * Create the preauthentication token instance from the User name.
     *
     * @param username the username.
     * @param request  the HttpServletRequest.
     * @param response the HttpServletResponse.
     * @return the PreAuthenticatedAuthenticationToken instance. Null if no user was found for the username.
     */
    protected PreAuthenticatedAuthenticationToken createPreAuthentication(String username, HttpServletRequest request, HttpServletResponse response) {
        User user = retrieveUserWithAuthorities(username, request, response);
        if (user == null) return null;
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().toString());
        PreAuthenticatedAuthenticationToken authenticationToken = new PreAuthenticatedAuthenticationToken(user, null, Collections.singletonList(authority));
        String idToken = OAuth2Utils.getIdToken();
        if (user != null && (StringUtils.isNotBlank(configuration.getGroupsClaim()) || StringUtils.isNotBlank(configuration.getRolesClaim()))) {
            addAuthoritiesFromToken(user, idToken);
        }
        OAuth2AccessToken accessToken = restTemplate.getOAuth2ClientContext().getAccessToken();
        authenticationToken.setDetails(new TokenDetails(accessToken, idToken, configuration.getBeanName()));
        return authenticationToken;
    }

    /**
     * Add authorities from the idToken claims if found.
     *
     * @param user    the user instance.
     * @param idToken the id token.
     */
    protected void addAuthoritiesFromToken(User user, String idToken) {
        JWTHelper helper = new JWTHelper(idToken);
        List<String> roles = null;
        List<String> groups = null;
        if (configuration.getRolesClaim() != null)
            roles = helper.getClaimAsList(configuration.getRolesClaim(), String.class);
        else roles = Collections.emptyList();

        if (configuration.getGroupsClaim() != null)
            groups = helper.getClaimAsList(configuration.getGroupsClaim(), String.class);
        if (groups == null) groups = Collections.emptyList();
        for (String r : roles) {
            if (r.equals(Role.ADMIN.name()))
                user.setRole(Role.ADMIN);
        }
        for (String g : groups) {
            UserGroup group = null;
            if (userGroupService != null)
                group = userGroupService.get(g);
            if (group == null) {
                group = new UserGroup();
                group.setGroupName(g);
            }
            user.getGroups().add(group);
        }

    }

    /**
     * Retrieves a user by username. Will create the user when not found, if the auto create flag was set to true.
     *
     * @param username the username.
     * @param request  the HttpServletRequest.
     * @param response the HttpServletResponse.
     * @return a {@link User} instance if the user was found/created. Null otherwise.
     */
    protected User retrieveUserWithAuthorities(String username, HttpServletRequest request, HttpServletResponse response) {
        User user = null;
        if (username != null && userService != null) {
            try {
                user = userService.get(username);
            } catch (NotFoundServiceEx notFoundServiceEx) {
                LOGGER.debug("User with username " + username + " not found.");
            }
        }
        if (user == null) {
            try {
                user = createUser(username, null, "");
            } catch (BadRequestServiceEx | NotFoundServiceEx e) {
                LOGGER.error("Error while autocreating the user: " + username, e);
            }
        }
        return user;
    }

    /**
     * Create a User instance.
     *
     * @param userName    the username.
     * @param credentials the password.
     * @param rawUser     user object.
     * @return a User instance.
     * @throws BadRequestServiceEx
     * @throws NotFoundServiceEx
     */
    protected User createUser(String userName, String credentials, Object rawUser) throws BadRequestServiceEx, NotFoundServiceEx {
        User user = new User();

        user.setName(userName);
        user.setNewPassword(credentials);
        user.setEnabled(true);
        UserAttribute userAttribute = new UserAttribute();
        userAttribute.setName(OAuth2Configuration.CONFIGURATION_NAME);
        userAttribute.setValue(configuration.getBeanName());
        user.setAttribute(Collections.singletonList(userAttribute));
        Set<UserGroup> groups = new HashSet<UserGroup>();
        user.setGroups(groups);
        user.setRole(Role.USER);
        if (userService != null && configuration.isAutoCreateUser()) {
            long id = userService.insert(user);
            user = new User(user);
            user.setId(id);
        }
        return user;
    }

    @Override
    public void afterPropertiesSet() {
        // do nothing: avoid filter instantiation failing due RestTemplate bean having creation scope=session
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Authentication success. Updating SecurityContextHolder to contain: "
                    + authResult);
        }

        SecurityContextHolder.getContext().setAuthentication(authResult);
        addRequestAttributes(request, authResult);
        request.setAttribute(PROVIDER_KEY, configuration.getProvider());
    }

    private void addRequestAttributes(HttpServletRequest request, Authentication authentication) {
        if (authentication != null) {
            TokenDetails tokenDetails = tokenDetails(authentication);
            if (tokenDetails != null && tokenDetails.getAccessToken() != null) {
                OAuth2AccessToken accessToken = tokenDetails.getAccessToken();
                request.setAttribute(ACCESS_TOKEN_PARAM, accessToken.getValue());
                if (accessToken.getRefreshToken() != null)
                    request.setAttribute(REFRESH_TOKEN_PARAM, accessToken.getRefreshToken().getValue());
                request.setAttribute(PROVIDER_KEY, configuration.getProvider());
            }
        }
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException, ServletException {
        if (failed instanceof AccessTokenRequiredException) {
            SecurityContextHolder.clearContext();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Authentication request failed: " + failed, failed);
                LOGGER.debug("Updated SecurityContextHolder to contain null Authentication");
            }
        }
    }

    public enum OAuth2AuthenticationType {
        BEARER, // this is a bearer token (meaning existing access token is in the request headers)
        USER // this is a "normal" oauth2 login (i.e. interactive user login)
    }
}
