/* ====================================================================
 *
 * Copyright (C) 2022-2025 GeoSolutions S.A.S.
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

import static com.google.common.collect.Lists.newArrayList;
import static it.geosolutions.geostore.core.security.password.SecurityUtils.getUsername;
import static it.geosolutions.geostore.services.rest.SessionServiceDelegate.PROVIDER_KEY;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.*;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.UserGroupAttribute;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.UserGroupService;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.security.RestAuthenticationEntryPoint;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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

/**
 * Base filter class for an OAuth2 authentication filter.
 *
 * <p>This class handles OAuth2 authentication including user auto-creation, token caching, and
 * robust principal resolution (security principal -> introspection map -> JWT claims). It can
 * augment user role/groups from token claims.
 */
public abstract class OAuth2GeoStoreAuthenticationFilter
        extends OAuth2ClientAuthenticationProcessingFilter {

    private static final Logger LOGGER =
            LogManager.getLogger(OAuth2GeoStoreAuthenticationFilter.class);

    private static final String SOURCE_SERVICE_USER_GROUP_ATTRIBUTE_NAME = "sourceService";

    public static final String OAUTH2_AUTHENTICATION_KEY = "oauth2.authentication";
    public static final String OAUTH2_AUTHENTICATION_TYPE_KEY = "oauth2.authenticationType";
    public static final String OAUTH2_ACCESS_TOKEN_CHECK_KEY = "oauth2.AccessTokenCheckResponse";

    private final AuthenticationEntryPoint authEntryPoint;
    private final TokenAuthenticationCache cache;

    @Autowired protected UserService userService;
    @Autowired protected UserGroupService userGroupService;
    protected RemoteTokenServices tokenServices;
    protected OAuth2Configuration configuration;

    public OAuth2GeoStoreAuthenticationFilter(
            RemoteTokenServices tokenServices,
            GeoStoreOAuthRestTemplate oAuth2RestTemplate,
            OAuth2Configuration configuration,
            TokenAuthenticationCache tokenAuthenticationCache) {
        super("/**");
        super.setTokenServices(tokenServices);
        this.tokenServices = tokenServices;
        super.restTemplate = oAuth2RestTemplate;
        this.configuration = configuration;
        this.authEntryPoint = configuration.getAuthenticationEntryPoint();
        this.cache = tokenAuthenticationCache;
    }

    public UserService getUserService() {
        return userService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public UserGroupService getUserGroupService() {
        return userGroupService;
    }

    public void setUserGroupService(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    public RemoteTokenServices getTokenServices() {
        return tokenServices;
    }

    public void setTokenServices(RemoteTokenServices tokenServices) {
        this.tokenServices = tokenServices;
    }

    public OAuth2Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(OAuth2Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (configuration.isEnabled() && !configuration.isInvalid() && authentication == null) {
            super.doFilter(req, res, chain);
        } else if (req instanceof HttpServletRequest) {
            addRequestAttributes((HttpServletRequest) req, authentication);
        }
        if (configuration.isEnabled() && configuration.isInvalid()) {
            LOGGER.info(
                    "Skipping configured OAuth2 authentication. One or more mandatory properties are missing (clientId, clientSecret, authorizationUri, tokenUri).");
        }
        chain.doFilter(req, res);
    }

    @Override
    public Authentication attemptAuthentication(
            HttpServletRequest request, HttpServletResponse response) {

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
                authentication =
                        authenticateAndUpdateCache(
                                request, response, token, new DefaultOAuth2AccessToken(token));
            } else {
                TokenDetails details = tokenDetails(authentication);
                if (details != null) {
                    OAuth2AccessToken accessToken = details.getAccessToken();
                    if (accessToken.isExpired()) {
                        authentication =
                                authenticateAndUpdateCache(request, response, token, accessToken);
                    }
                }
            }
        } else {
            clearState();
            authentication = authenticateAndUpdateCache(request, response, null, null);
            token =
                    (String)
                            Objects.requireNonNull(RequestContextHolder.getRequestAttributes())
                                    .getAttribute(ACCESS_TOKEN_PARAM, 0);
            if (token != null) {
                request.setAttribute(ACCESS_TOKEN_PARAM, token);
                request.setAttribute(
                        OAUTH2_AUTHENTICATION_TYPE_KEY, OAuth2AuthenticationType.BEARER);
                request.setAttribute(
                        ID_TOKEN_PARAM,
                        RequestContextHolder.getRequestAttributes()
                                .getAttribute(ID_TOKEN_PARAM, 0));
                request.setAttribute(
                        REFRESH_TOKEN_PARAM,
                        RequestContextHolder.getRequestAttributes()
                                .getAttribute(REFRESH_TOKEN_PARAM, 0));
            }
        }

        return authentication;
    }

    private TokenDetails tokenDetails(Authentication authentication) {
        Object details = authentication != null ? authentication.getDetails() : null;
        return (details instanceof TokenDetails) ? (TokenDetails) details : null;
    }

    private Authentication authenticateAndUpdateCache(
            HttpServletRequest request,
            HttpServletResponse response,
            String token,
            OAuth2AccessToken accessToken) {
        Authentication authentication = performOAuthAuthentication(request, response, accessToken);
        if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            TokenDetails tokenDetails = tokenDetails(authentication);
            if (tokenDetails != null) {
                OAuth2AccessToken accessTokenDetails = tokenDetails.getAccessToken();
                if (accessTokenDetails != null) {
                    token = accessTokenDetails.getValue();
                    Objects.requireNonNull(RequestContextHolder.getRequestAttributes())
                            .setAttribute(ACCESS_TOKEN_PARAM, accessTokenDetails.getValue(), 0);
                    if (accessTokenDetails.getRefreshToken() != null
                            && accessTokenDetails.getRefreshToken().getValue() != null) {
                        RequestContextHolder.getRequestAttributes()
                                .setAttribute(
                                        REFRESH_TOKEN_PARAM,
                                        accessTokenDetails.getRefreshToken().getValue(),
                                        0);
                    }
                }
                if (tokenDetails.getIdToken() != null) {
                    Objects.requireNonNull(RequestContextHolder.getRequestAttributes())
                            .setAttribute(ID_TOKEN_PARAM, tokenDetails.getIdToken(), 0);
                }
            }
            if (token != null) {
                cache.putCacheEntry(token, authentication);
            } else {
                LOGGER.info("Skipping cache insert: no access token available yet.");
            }
        }
        Objects.requireNonNull(RequestContextHolder.getRequestAttributes())
                .setAttribute(PROVIDER_KEY, configuration.getProvider(), 0);
        return authentication;
    }

    private void clearState() {
        OAuth2ClientContext clientContext = restTemplate.getOAuth2ClientContext();
        final AccessTokenRequest accessTokenRequest = clientContext.getAccessTokenRequest();
        if (accessTokenRequest != null && accessTokenRequest.getStateKey() != null) {
            clientContext.removePreservedState(accessTokenRequest.getStateKey());
        }
        if (accessTokenRequest != null) {
            try {
                accessTokenRequest.remove(ACCESS_TOKEN_PARAM);
            } finally {
                SecurityContextHolder.clearContext();
                HttpServletRequest request =
                        ((ServletRequestAttributes)
                                        Objects.requireNonNull(
                                                RequestContextHolder.getRequestAttributes()))
                                .getRequest();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                LOGGER.info("Cleaned out Session Access Token Request!");
            }
        }
    }

    protected Authentication performOAuthAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            OAuth2AccessToken accessToken) {
        LOGGER.info("About to perform remote authentication.");
        LOGGER.info("Access Token: {}", accessToken);
        String principal = null;
        PreAuthenticatedAuthenticationToken result = null;
        try {
            LOGGER.info("Trying to get the pre-authenticated principal.");
            principal = getPreAuthenticatedPrincipal(request, response, accessToken);
        } catch (IOException | ServletException e1) {
            LOGGER.error("Error obtaining pre-authenticated principal: {}", e1.getMessage(), e1);
        }

        LOGGER.info("Pre-authenticated principal = {}, trying to authenticate", principal);

        if (StringUtils.isNotBlank(principal)) {
            result = createPreAuthentication(principal, request, response);
        }
        return result;
    }

    /**
     * Resolve principal with fallbacks: 1) Spring security principal 2) Introspection/extension map
     * 3) JWT claims (ID Token, else Access Token, else bearer param)
     */
    protected String getPreAuthenticatedPrincipal(
            HttpServletRequest req, HttpServletResponse resp, OAuth2AccessToken accessToken)
            throws IOException, ServletException {

        // 0) Configure and attach tokens to the rest template
        LOGGER.info("Configuring the REST Resource Template");
        configureRestTemplate();

        if (accessToken != null && StringUtils.isNotEmpty(accessToken.getValue())) {
            LOGGER.info("Setting the access token on the OAuth2ClientContext");
            restTemplate.getOAuth2ClientContext().setAccessToken(accessToken);
        }

        // 1) Setup services
        LOGGER.info("Setting up OAuth2 Filter services and resource template");
        setRestTemplate(restTemplate);
        setTokenServices(tokenServices);

        // 2) Attempt authentication (introspection / user-info)
        Authentication authentication = null;
        try {
            authentication = super.attemptAuthentication(req, resp);
            req.setAttribute(OAUTH2_AUTHENTICATION_KEY, authentication);

            if (authentication instanceof OAuth2Authentication) {
                OAuth2Authentication oa = (OAuth2Authentication) authentication;
                LOGGER.info(
                        "isClientOnly={}, userAuth={}",
                        oa.isClientOnly(),
                        oa.getUserAuthentication());
            }

            // Stash Access Token Check Response (introspection result) if present
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

            if (authentication != null) {
                LOGGER.info(
                        "Authenticated OAuth request for principal {}",
                        authentication.getPrincipal());
            }
        } catch (Exception e) {
            handleOAuthException(e, req, resp);
        }

        // 3) Primary: Spring's principal
        String username =
                (authentication != null ? getUsername(authentication.getPrincipal()) : null);
        if (StringUtils.isNotBlank(username)) {
            LOGGER.info("Authenticated OAuth request with user (security principal) {}", username);
            return username;
        }

        // 4) Fallback #1: introspection/extension map
        String fromExt = null;
        Object extObj = req.getAttribute(OAUTH2_ACCESS_TOKEN_CHECK_KEY);
        if (extObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ext = (Map<String, Object>) extObj;

            // Prefer configured keys
            fromExt =
                    coalesce(
                            findFirstIgnoreCase(ext, configuration.getUniqueUsername()),
                            findFirstIgnoreCase(ext, configuration.getPrincipalKey()));

            // Common keys
            if (StringUtils.isBlank(fromExt)) {
                fromExt =
                        coalesce(
                                findFirstIgnoreCase(ext, "upn"),
                                findFirstIgnoreCase(ext, "preferred_username"),
                                findFirstIgnoreCase(ext, "unique_name"),
                                findFirstIgnoreCase(ext, "user_name"),
                                findFirstIgnoreCase(ext, "username"),
                                findFirstIgnoreCase(ext, "email"),
                                findFirstIgnoreCase(ext, "sub"),
                                findFirstIgnoreCase(ext, "oid"));
            }
        }
        if (StringUtils.isNotBlank(fromExt)) {
            LOGGER.info("Authenticated OAuth request with user (introspection) {}", fromExt);
            return fromExt;
        }

        // 5) Fallback #2: JWT claims (ID Token preferred, else Access Token, else bearer/param)
        String idToken = OAuth2Utils.getIdToken();
        String jwtForClaims = idToken;
        if (jwtForClaims == null && accessToken != null) jwtForClaims = accessToken.getValue();
        if (jwtForClaims == null)
            jwtForClaims = OAuth2Utils.tokenFromParamsOrBearer(ACCESS_TOKEN_PARAM, req);

        if (StringUtils.isNotBlank(jwtForClaims)) {
            JWTHelper helper = decodeAndValidateJwt(jwtForClaims);
            if (helper != null) {
                String fromJwt =
                        coalesce(
                                getClaim(helper, configuration.getUniqueUsername()),
                                getClaim(helper, configuration.getPrincipalKey()));

                if (StringUtils.isBlank(fromJwt)) {
                    fromJwt =
                            coalesce(
                                    getClaim(helper, "upn"),
                                    getClaim(helper, "preferred_username"),
                                    getClaim(helper, "unique_name"),
                                    getClaim(helper, "email"),
                                    getClaim(helper, "sub"),
                                    getClaim(helper, "oid"));
                }

                if (StringUtils.isNotBlank(fromJwt)) {
                    LOGGER.info("Authenticated OAuth request with user (JWT claims) {}", fromJwt);
                    return fromJwt;
                }
            }
        }

        // 6) Nothing worked
        LOGGER.warn(
                "Principal could not be resolved from security principal, introspection, or JWT claims.");
        return null;
    }

    private String coalesce(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) return v;
        }
        return null;
    }

    private String getClaim(JWTHelper helper, String key) {
        if (helper == null || StringUtils.isBlank(key)) return null;
        try {
            return helper.getClaim(key, String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Case-insensitive, searches also one nested level if the map contains sub-maps. */
    @SuppressWarnings("unchecked")
    private String findFirstIgnoreCase(Map<String, Object> map, String key) {
        if (map == null || StringUtils.isBlank(key)) return null;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (key.equalsIgnoreCase(String.valueOf(e.getKey()))) {
                return e.getValue() != null ? String.valueOf(e.getValue()) : null;
            }
        }
        for (Object v : map.values()) {
            if (v instanceof Map) {
                String found = findFirstIgnoreCase((Map<String, Object>) v, key);
                if (StringUtils.isNotBlank(found)) return found;
            }
        }
        return null;
    }

    private void handleOAuthException(Exception e, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {
        if (e instanceof UserRedirectRequiredException
                && configuration.isEnableRedirectEntryPoint()) {
            handleUserRedirection(req, resp);
            return;
        }

        String errorDetail;
        if (e instanceof BadCredentialsException) {
            if (e.getCause() instanceof OAuth2AccessDeniedException) {
                errorDetail = "OAuth2 access denied: " + e.getCause().getMessage();
                LOGGER.warn("OAuth2 access denied by provider: {}", e.getCause().getMessage());
            } else {
                errorDetail = "Bad credentials: " + e.getMessage();
                LOGGER.warn("OAuth2 bad credentials: {}", e.getMessage());
            }
        } else if (e instanceof ResourceAccessException) {
            errorDetail = "OAuth2 provider unreachable: " + e.getMessage();
            LOGGER.error("Could not reach OAuth2 provider: {}", e.getMessage(), e);
        } else {
            errorDetail = "Authentication error: " + e.getMessage();
            LOGGER.warn("OAuth2 authentication error: {}", e.getMessage(), e);
        }

        req.setAttribute(RestAuthenticationEntryPoint.OAUTH2_AUTH_ERROR_KEY, errorDetail);
    }

    private void handleUserRedirection(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {
        if (req.getRequestURI().contains(configuration.getProvider() + "/login")) {
            authEntryPoint.commence(req, resp, null);
        } else {
            if (resp.getStatus() != 302) {
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

    /** Configures the REST template with values from the OAuth2 configuration. */
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

        final String scopesJoined =
                (configuration.getScopes() == null)
                        ? ""
                        : Stream.of(configuration.getScopes()).collect(Collectors.joining(","));
        details.setScope(parseScopes(scopesJoined));
    }

    protected List<String> parseScopes(String commaSeparatedScopes) {
        List<String> scopes = newArrayList();
        if (StringUtils.isBlank(commaSeparatedScopes)) return scopes;
        Collections.addAll(scopes, commaSeparatedScopes.split(","));
        return scopes;
    }

    /**
     * Create PreAuthenticatedAuthenticationToken from resolved username. Uses token claims to
     * optionally augment roles/groups.
     */
    public PreAuthenticatedAuthenticationToken createPreAuthentication(
            String username, HttpServletRequest request, HttpServletResponse response) {

        if (StringUtils.isBlank(username)) {
            LOGGER.error("Cannot create authentication: empty username.");
            return null;
        }

        LOGGER.info("Retrieving user with authorities for username: {}", username);
        User user = retrieveUserWithAuthorities(username, request, response);
        if (user == null) {
            LOGGER.error("User retrieval failed for username: {}", username);
            return null;
        }

        SimpleGrantedAuthority authority =
                new SimpleGrantedAuthority("ROLE_" + user.getRole().toString());
        PreAuthenticatedAuthenticationToken authenticationToken =
                new PreAuthenticatedAuthenticationToken(
                        user, null, Collections.singletonList(authority));

        // Prefer ID token for claim enrichment, else access token
        String idToken = OAuth2Utils.getIdToken();
        OAuth2AccessToken accessToken = null;
        try {
            accessToken = restTemplate.getOAuth2ClientContext().getAccessToken();
        } catch (Exception e) {
            LOGGER.debug("No access token in context yet.", e);
        }

        String tokenForClaims =
                (idToken != null) ? idToken : (accessToken != null ? accessToken.getValue() : null);
        if (StringUtils.isNotBlank(tokenForClaims)
                && (StringUtils.isNotBlank(configuration.getGroupsClaim())
                        || StringUtils.isNotBlank(configuration.getRolesClaim()))) {
            // Read userinfo/introspection response stored by getPreAuthenticatedPrincipal()
            @SuppressWarnings("unchecked")
            Map<String, Object> userinfoMap =
                    (Map<String, Object>) request.getAttribute(OAUTH2_ACCESS_TOKEN_CHECK_KEY);
            addAuthoritiesFromToken(user, tokenForClaims, userinfoMap);
        }

        authenticationToken.setDetails(
                new TokenDetails(accessToken, idToken, configuration.getBeanName()));
        return authenticationToken;
    }

    /**
     * Decode and validate a JWT (ID token or access token). Returns null if the token is null or
     * cannot be decoded. (Kept protected for potential subclass use.)
     */
    protected JWTHelper decodeAndValidateJwt(String jwt) {
        if (jwt == null) {
            LOGGER.warn("No JWT provided for decoding.");
            return null;
        }
        try {
            return new JWTHelper(jwt);
        } catch (Exception e) {
            LOGGER.error("Failed to decode or validate JWT", e);
            return null;
        }
    }

    /**
     * Add authorities from token claims: - recompute Role if roles claim is present (supports
     * demotion), - reconcile remote groups for THIS provider against IdP groups. Delegates to the
     * overloaded version with a null userinfo map.
     */
    protected void addAuthoritiesFromToken(User user, String tokenString) {
        addAuthoritiesFromToken(user, tokenString, null);
    }

    /**
     * Add authorities from token claims with optional userinfo/introspection fallback. Tries JWT
     * extraction first; if the claim is not found in the JWT, falls back to the userinfo map.
     */
    @SuppressWarnings("unchecked")
    protected void addAuthoritiesFromToken(
            User user, String tokenString, Map<String, Object> userinfoMap) {
        LOGGER.info("Syncing authorities from token claims.");

        JWTHelper helper = null;
        try {
            helper = new JWTHelper(tokenString);
        } catch (Exception e) {
            LOGGER.warn("Token is not a valid JWT; will try userinfo map for claims.", e);
        }

        // ----- Roles -----
        Role currentRole = user.getRole();
        String rolesClaimName = configuration.getRolesClaim();
        Object rawRoles = null;
        if (StringUtils.isNotBlank(rolesClaimName)) {
            if (helper != null) {
                rawRoles = helper.getClaim(rolesClaimName, Object.class);
            }
            if (rawRoles == null && userinfoMap != null) {
                rawRoles = ClaimPathResolver.resolveIgnoreCase(userinfoMap, rolesClaimName);
            }
        }

        if (rawRoles != null) {
            List<String> oidcRoles;
            if (helper != null && helper.getClaim(rolesClaimName, Object.class) != null) {
                oidcRoles = helper.getClaimAsList(rolesClaimName, String.class);
            } else {
                oidcRoles = ClaimPathResolver.toStringList(rawRoles);
            }
            if (oidcRoles == null) oidcRoles = Collections.emptyList();
            Role defaultRole =
                    configuration.getAuthenticatedDefaultRole() != null
                            ? configuration.getAuthenticatedDefaultRole()
                            : Role.USER;
            Role newRole = computeRole(oidcRoles, defaultRole);
            user.setRole(newRole);
            LOGGER.info("User role set from token. {} -> {}", currentRole, newRole);
        } else {
            LOGGER.info(
                    "Roles claim '{}' missing in token and userinfo -> preserving current role: {}",
                    rolesClaimName,
                    currentRole);
        }

        // ----- Groups -----
        List<String> oidcGroups = Collections.emptyList();
        if (configuration.getGroupsClaim() != null) {
            List<String> fromJwt = null;
            if (helper != null) {
                fromJwt = helper.getClaimAsList(configuration.getGroupsClaim(), String.class);
            }
            if (fromJwt != null && !fromJwt.isEmpty()) {
                oidcGroups = fromJwt;
            } else if (userinfoMap != null) {
                List<String> fromUserinfo =
                        ClaimPathResolver.resolveAsListIgnoreCase(
                                userinfoMap, configuration.getGroupsClaim());
                if (fromUserinfo != null) {
                    oidcGroups = fromUserinfo;
                }
            }
            LOGGER.info("Groups from token/userinfo: {}", oidcGroups);
        }

        Map<String, String> groupMappings = configuration.getGroupMappings();
        boolean dropUnmapped = configuration.isDropUnmapped();
        if (groupMappings != null && !oidcGroups.isEmpty()) {
            List<String> mapped = new java.util.ArrayList<>();
            for (String g : oidcGroups) {
                if (g == null) continue;
                String m = groupMappings.get(g.toUpperCase(Locale.ROOT));
                if (m != null) {
                    mapped.add(m);
                } else if (!dropUnmapped) {
                    mapped.add(g);
                }
            }
            oidcGroups = mapped;
        }

        reconcileRemoteGroups(user, new LinkedHashSet<>(oidcGroups));

        // ----- Persist user after role & group sync -----
        try {
            if (userService != null) userService.update(user);
        } catch (BadRequestServiceEx | NotFoundServiceEx e) {
            LOGGER.error(
                    "Updating user with synchronized groups found in claims failed: {}",
                    e.getMessage(),
                    e);
        } catch (DataIntegrityViolationException e) {
            LOGGER.error(
                    "Updating user with synchronized groups data integrity violation: {}",
                    e.getMessage(),
                    e);
        } finally {
            LOGGER.info("User updated with the following groups: {}", user.getGroups());
        }
    }

    // ---------------------------------------------------------------------
    // Helpers for deterministic role and provider-scoped group reconcile
    // ---------------------------------------------------------------------

    private Role computeRole(List<String> rolesFromToken, Role defaultRole) {
        if (rolesFromToken == null || rolesFromToken.isEmpty()) {
            return (defaultRole != null) ? defaultRole : Role.USER;
        }
        Map<String, String> roleMappings = configuration.getRoleMappings();
        boolean dropUnmapped = configuration.isDropUnmapped();
        Role resolved = (defaultRole != null) ? defaultRole : Role.USER;
        for (String r : rolesFromToken) {
            if (r == null) continue;
            String rr = r.trim();
            if (roleMappings != null) {
                String mapped = roleMappings.get(rr.toUpperCase(Locale.ROOT));
                if (mapped != null) {
                    rr = mapped;
                } else if (dropUnmapped) {
                    continue;
                }
            }
            if (rr.equalsIgnoreCase(Role.ADMIN.name())) return Role.ADMIN;
            if (rr.equalsIgnoreCase(Role.GUEST.name())) resolved = Role.GUEST;
        }
        return resolved;
    }

    private String normalizeGroupName(String name) {
        if (name == null) return null;
        return configuration.isGroupNamesUppercase() ? name.toUpperCase(Locale.ROOT) : name;
    }

    private Set<Long> remoteGroupIdsForCurrentProvider() {
        if (userGroupService == null || configuration == null) return Collections.emptySet();
        final String provider = configuration.getProvider();
        Set<Long> ids = new LinkedHashSet<>();
        try {
            Collection<UserGroup> groups =
                    userGroupService.findByAttribute(
                            SOURCE_SERVICE_USER_GROUP_ATTRIBUTE_NAME,
                            Collections.singletonList(provider),
                            true);
            if (groups != null) {
                for (UserGroup g : groups) {
                    if (g != null && g.getId() != null) ids.add(g.getId());
                }
            }
        } catch (Exception e) {
            LOGGER.warn(
                    "Unable to resolve remote groups for provider '{}': {}",
                    provider,
                    e.getMessage());
        }
        return ids;
    }

    private void reconcileRemoteGroups(User user, Set<String> newGroupNamesRaw) {
        final String provider = configuration.getProvider();
        if (StringUtils.isBlank(provider)) {
            LOGGER.warn("Provider name is empty; skipping remote group reconciliation.");
            return;
        }

        if (user.getGroups() == null) {
            user.setGroups(new LinkedHashSet<>());
        }

        Set<String> newGroupNames =
                newGroupNamesRaw.stream()
                        .filter(Objects::nonNull)
                        .map(this::normalizeGroupName)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> providerRemoteIds = remoteGroupIdsForCurrentProvider();

        // 1) remove old remote groups for THIS provider that are not in the new set
        Set<UserGroup> toRemove =
                user.getGroups().stream()
                        .filter(
                                g ->
                                        g != null
                                                && g.getId() != null
                                                && providerRemoteIds.contains(g.getId()))
                        .filter(g -> !newGroupNames.contains(normalizeGroupName(g.getGroupName())))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        for (UserGroup g : toRemove) {
            try {
                userGroupService.deassignUserGroup(user.getId(), g.getId());
                LOGGER.info(
                        "Removed remote group '{}' for provider '{}' from user {}",
                        g.getGroupName(),
                        provider,
                        user.getId());
            } catch (NotFoundServiceEx e) {
                LOGGER.warn(
                        "Deassign failed for group '{}' from user {}: {}",
                        g.getGroupName(),
                        user.getId(),
                        e.getMessage());
            }
        }
        user.getGroups().removeAll(toRemove);

        // 2) ensure each new token group exists, is marked for this provider, and is assigned
        for (String groupName : newGroupNames) {
            UserGroup group = searchGroup(groupName);
            if (group == null) {
                group = createUserGroup(groupName); // sets sourceService=provider
                LOGGER.info("Created remote group '{}' (provider={})", groupName, provider);
            } else {
                updateGroupSourceServiceAttributes(group); // backfill provider tag
            }

            UserGroup finalGroup = group;
            boolean alreadyAssigned =
                    user.getGroups().stream()
                            .anyMatch(
                                    g -> {
                                        if (g == null) return false;
                                        if (finalGroup.getId() != null && g.getId() != null) {
                                            return Objects.equals(g.getId(), finalGroup.getId());
                                        }
                                        return Objects.equals(
                                                normalizeGroupName(g.getGroupName()),
                                                normalizeGroupName(finalGroup.getGroupName()));
                                    });
            if (!alreadyAssigned) {
                if (userGroupService != null && user.getId() != null && group.getId() != null) {
                    try {
                        userGroupService.assignUserGroup(user.getId(), group.getId());
                        LOGGER.info("Assigned user {} to group '{}'", user.getId(), groupName);
                    } catch (NotFoundServiceEx e) {
                        LOGGER.error(
                                "Assignment of user {} to group '{}' failed: {}",
                                user.getId(),
                                groupName,
                                e.getMessage());
                    }
                }
                user.getGroups().add(group);
            }
        }

        Map<String, UserGroup> byName = new LinkedHashMap<>();
        for (UserGroup g : user.getGroups()) {
            byName.put(normalizeGroupName(g.getGroupName()), g);
        }
        user.setGroups(new LinkedHashSet<>(byName.values()));
    }

    private UserGroup searchGroup(String groupName) {
        if (userGroupService == null) return null;
        if (configuration.isGroupNamesUppercase()) {
            UserGroup ug = userGroupService.get(groupName.toUpperCase());
            if (ug != null) return ug;
        }
        return userGroupService.get(groupName);
    }

    private void updateGroupSourceServiceAttributes(UserGroup group) {
        if (group == null || group.getId() == null) return;
        try {
            userGroupService.upsertAttribute(
                    group.getId(),
                    SOURCE_SERVICE_USER_GROUP_ATTRIBUTE_NAME,
                    configuration.getProvider());
        } catch (NotFoundServiceEx | BadRequestServiceEx e) {
            LOGGER.warn(
                    "Could not upsert sourceService for group '{}': {}",
                    group.getGroupName(),
                    e.getMessage());
        }
    }

    private UserGroup createUserGroup(String groupName) {
        UserGroup group = new UserGroup();
        group.setGroupName(
                configuration.isGroupNamesUppercase() ? groupName.toUpperCase() : groupName);
        group.setAttributes(
                List.of(createUserGroupSourceServiceAttribute(configuration.getProvider())));
        if (userGroupService != null) {
            try {
                long groupId = userGroupService.insert(group);
                group = userGroupService.get(groupId);
                LOGGER.info("inserted group id: {}", group.getGroupName());
            } catch (BadRequestServiceEx e) {
                LOGGER.error("Saving new group found in claims failed");
            }
        }
        return group;
    }

    private UserGroupAttribute createUserGroupSourceServiceAttribute(String remoteService) {
        UserGroupAttribute userGroupAttribute = new UserGroupAttribute();
        userGroupAttribute.setName(SOURCE_SERVICE_USER_GROUP_ATTRIBUTE_NAME);
        userGroupAttribute.setValue(remoteService);
        return userGroupAttribute;
    }

    protected User retrieveUserWithAuthorities(
            String username, HttpServletRequest request, HttpServletResponse response) {
        User user = null;
        if (username != null && userService != null) {
            try {
                user = userService.get(username);
            } catch (NotFoundServiceEx notFoundServiceEx) {
                LOGGER.info("User with username {} not found.", username);
            }
        }
        if (user == null) {
            try {
                user = createUser(username, null, "");
            } catch (BadRequestServiceEx | NotFoundServiceEx e) {
                LOGGER.error("Error while auto-creating the user: {}", username, e);
            }
        }
        return user;
    }

    protected User createUser(String userName, String credentials, Object rawUser)
            throws BadRequestServiceEx, NotFoundServiceEx {
        User user = new User();
        user.setName(userName);
        user.setNewPassword(credentials);
        user.setEnabled(true);
        UserAttribute userAttribute = new UserAttribute();
        userAttribute.setName(OAuth2Configuration.CONFIGURATION_NAME);
        userAttribute.setValue(configuration.getBeanName());
        user.setAttribute(Collections.singletonList(userAttribute));
        Set<UserGroup> groups = new HashSet<>();
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
        // Do nothing: avoid filter instantiation failing due to RestTemplate bean having session
        // scope.
    }

    @Override
    protected void successfulAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain,
            Authentication authResult)
            throws IOException, ServletException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "Authentication success. Updating SecurityContextHolder to contain: {}",
                    authResult);
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
                if (tokenDetails.getIdToken() != null)
                    request.setAttribute(ID_TOKEN_PARAM, tokenDetails.getIdToken());
                if (accessToken.getRefreshToken() != null)
                    request.setAttribute(
                            REFRESH_TOKEN_PARAM, accessToken.getRefreshToken().getValue());
                request.setAttribute(PROVIDER_KEY, configuration.getProvider());
            }
        }
    }

    @Override
    protected void unsuccessfulAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException failed)
            throws IOException, ServletException {
        if (failed instanceof AccessTokenRequiredException) {
            SecurityContextHolder.clearContext();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Authentication request failed:", failed);
                LOGGER.debug("Cleared SecurityContextHolder.");
            }
        }
    }

    /** Enum representing the type of OAuth2 authentication. */
    public enum OAuth2AuthenticationType {
        BEARER, // Bearer token authentication (existing access token in request headers)
        USER // Interactive OAuth2 login authentication
    }
}
