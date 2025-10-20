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
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * username remapping. It decodes/validates the idToken once and, if configured, remaps the username
 * from a "principal" claim to a "unique username" claim.
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

    /**
     * Constructs a new OAuth2GeoStoreAuthenticationFilter.
     *
     * @param tokenServices a RemoteTokenServices instance.
     * @param oAuth2RestTemplate the REST template to use for OAuth2 requests.
     * @param configuration the OAuth2 configuration.
     * @param tokenAuthenticationCache the token authentication cache.
     */
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
        // Do we need to authenticate?
        if (configuration.isEnabled() && !configuration.isInvalid() && authentication == null) {
            super.doFilter(req, res, chain);
        } else if (req instanceof HttpServletRequest) {
            // No need to authenticate, but if the security context holds a Token authentication,
            // set the access token as a request attribute.
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
            // guard against null token
            if (token != null) {
                cache.putCacheEntry(token, authentication);
            } else {
                LOGGER.debug("Skipping cache insert: no access token available yet.");
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
                LOGGER.debug("Cleaned out Session Access Token Request!");
            }
        }
    }

    /**
     * Performs the OAuth2 authentication using the given access token.
     *
     * @param request the HTTP request.
     * @param response the HTTP response.
     * @param accessToken the OAuth2 access token.
     * @return an Authentication object if successful, null otherwise.
     */
    protected Authentication performOAuthAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            OAuth2AccessToken accessToken) {
        LOGGER.debug("About to perform remote authentication.");
        LOGGER.debug("Access Token: {}", accessToken);
        String principal = null;
        PreAuthenticatedAuthenticationToken result = null;
        try {
            LOGGER.debug("Trying to get the pre-authenticated principal.");
            principal = getPreAuthenticatedPrincipal(request, response, accessToken);
        } catch (IOException | ServletException e1) {
            LOGGER.error("Error obtaining pre-authenticated principal: {}", e1.getMessage(), e1);
        }

        LOGGER.debug("Pre-authenticated principal = {}, trying to authenticate", principal);

        if (principal != null && !principal.trim().isEmpty()) {
            result = createPreAuthentication(principal, request, response);
        }
        return result;
    }

    /**
     * Retrieves the pre-authenticated principal.
     *
     * @param req the HTTP request.
     * @param resp the HTTP response.
     * @param accessToken the OAuth2 access token.
     * @return the principal as a String.
     * @throws IOException if an I/O error occurs.
     * @throws ServletException if a servlet error occurs.
     */
    protected String getPreAuthenticatedPrincipal(
            HttpServletRequest req, HttpServletResponse resp, OAuth2AccessToken accessToken)
            throws IOException, ServletException {

        // Configure the REST Resource Template
        LOGGER.debug("Configuring the REST Resource Template");
        configureRestTemplate();

        if (accessToken != null && StringUtils.isNotEmpty(accessToken.getValue())) {
            LOGGER.debug("Setting the access token on the OAuth2ClientContext");
            restTemplate.getOAuth2ClientContext().setAccessToken(accessToken);
        }

        // Set up OAuth2 Filter services and resource template
        LOGGER.debug("Setting up OAuth2 Filter services and resource template");
        setRestTemplate(restTemplate);
        setTokenServices(tokenServices);

        // Validate the access token
        Authentication authentication = null;
        try {
            authentication = super.attemptAuthentication(req, resp);
            req.setAttribute(OAUTH2_AUTHENTICATION_KEY, authentication);

            // Extract extensions containing the Access Token Check Response
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

            if (authentication != null && LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Authenticated OAuth request for principal {}",
                        authentication.getPrincipal());
            }
        } catch (Exception e) {
            handleOAuthException(e, req, resp);
        }

        String username =
                (authentication != null ? getUsername(authentication.getPrincipal()) : null);
        if (username != null && username.trim().isEmpty()) {
            username = null;
        }
        return username;
    }

    private void handleOAuthException(Exception e, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {
        if (e instanceof UserRedirectRequiredException
                && configuration.isEnableRedirectEntryPoint()) {
            handleUserRedirection(req, resp);
        } else if (e instanceof BadCredentialsException || e instanceof ResourceAccessException) {
            if (e.getCause() instanceof OAuth2AccessDeniedException) {
                LOGGER.warn(
                        "Error while trying to authenticate to OAuth2 Provider. Cause: ",
                        e.getCause());
            } else if (e instanceof ResourceAccessException) {
                LOGGER.error("Could not authorize OAuth2 Resource due to exception: ", e);
            } else if (e.getCause() instanceof OAuth2AccessDeniedException) {
                LOGGER.warn(
                        "If you try to validate credentials against an SSH protected endpoint, you need your server exposed on a secure SSL channel or OAuth2 Provider Certificate to be trusted on your JVM.");
                LOGGER.info(
                        "Refer to the GeoServer OAuth2 Plugin Documentation for steps to import SSH certificates.");
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.error("Could not authorize OAuth2 Resource due to exception: ", e);
                }
            }
        }
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

        // safe scopes handling
        final String scopesJoined =
                (configuration.getScopes() == null)
                        ? ""
                        : Stream.of(configuration.getScopes()).collect(Collectors.joining(","));
        details.setScope(parseScopes(scopesJoined));
    }

    /**
     * Parses the scopes from a comma-separated string into a list.
     *
     * @param commaSeparatedScopes the scopes as a comma-separated string.
     * @return a list of scope strings.
     */
    protected List<String> parseScopes(String commaSeparatedScopes) {
        List<String> scopes = newArrayList();
        if (StringUtils.isBlank(commaSeparatedScopes)) return scopes;
        Collections.addAll(scopes, commaSeparatedScopes.split(","));
        return scopes;
    }

    /**
     * Creates a pre-authenticated token instance from the username.
     *
     * <p>This method decodes and validates the idToken once and, if the token contains the
     * appropriate claims, remaps the username from the configured "principal" claim to the "unique
     * username" claim.
     *
     * @param username the original username.
     * @param request the HTTP request.
     * @param response the HTTP response.
     * @return a {@link PreAuthenticatedAuthenticationToken} if the user is retrieved/created, or
     *     null otherwise.
     */
    public PreAuthenticatedAuthenticationToken createPreAuthentication(
            String username, HttpServletRequest request, HttpServletResponse response) {
        String idToken = OAuth2Utils.getIdToken();
        JWTHelper jwtHelper = decodeAndValidateIdToken(idToken);
        // Remap the username if the idToken is valid and the configuration is set
        username = remapUsername(username, jwtHelper);

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

        if (StringUtils.isNotBlank(configuration.getGroupsClaim())
                || StringUtils.isNotBlank(configuration.getRolesClaim())) {
            addAuthoritiesFromToken(user, idToken);
        }

        OAuth2AccessToken accessToken = restTemplate.getOAuth2ClientContext().getAccessToken();
        authenticationToken.setDetails(
                new TokenDetails(accessToken, idToken, configuration.getBeanName()));
        return authenticationToken;
    }

    /**
     * Decodes and validates the given idToken.
     *
     * <p>If the token is null or fails to decode, this method logs an appropriate message and
     * returns null, causing the authentication to fall back to using the original username.
     *
     * @param idToken the idToken to decode.
     * @return a {@link JWTHelper} instance if the token is valid, or null otherwise.
     */
    protected JWTHelper decodeAndValidateIdToken(String idToken) {
        if (idToken == null) {
            LOGGER.warn("No idToken provided for decoding. Skipping username remapping.");
            return null;
        }
        try {
            // Optionally add additional validation logic for the token here (e.g. signature,
            // expiration)
            return new JWTHelper(idToken);
        } catch (Exception e) {
            LOGGER.error("Failed to decode or validate idToken: {}", idToken, e);
            return null;
        }
    }

    /**
     * Remaps the provided username based on idToken claims if applicable.
     *
     * @param username the original username.
     * @param jwtHelper the {@link JWTHelper} instance for decoding idToken claims, may be null.
     * @return the remapped username if claims match; otherwise, the original username.
     */
    private String remapUsername(String username, JWTHelper jwtHelper) {
        if (jwtHelper != null
                && StringUtils.isNotBlank(configuration.getPrincipalKey())
                && StringUtils.isNotBlank(configuration.getUniqueUsername())) {
            String principalClaim =
                    jwtHelper.getClaim(configuration.getPrincipalKey(), String.class);
            if (StringUtils.isNotBlank(principalClaim)
                    && StringUtils.equals(username, principalClaim)) {
                String uniqueUsername =
                        jwtHelper.getClaim(configuration.getUniqueUsername(), String.class);
                if (StringUtils.isNotBlank(uniqueUsername)) {
                    LOGGER.info(
                            "Username remapped from {} to {} based on idToken claims.",
                            username,
                            uniqueUsername);
                    return uniqueUsername;
                }
            }
        }
        return username;
    }

    /**
     * Adds authorities to the user based on idToken claims: - recomputes Role if roles claim is
     * present (supports demotion), - otherwise keeps the current role, - reconciles remote groups
     * for THIS provider against IdP groups.
     */
    protected void addAuthoritiesFromToken(User user, String idToken) {
        LOGGER.debug(" --------- OIDC - ID_TOKEN: {}", idToken);
        JWTHelper helper = new JWTHelper(idToken);

        // ----- Roles -----
        Role currentRole = user.getRole();
        String rolesClaimName = configuration.getRolesClaim();
        Object rawRoles = null;
        if (StringUtils.isNotBlank(rolesClaimName)) {
            // Presence detection: if claim is absent -> rawRoles == null
            rawRoles = helper.getClaim(rolesClaimName, Object.class);
        }

        if (rawRoles != null) {
            List<String> oidcRoles = helper.getClaimAsList(rolesClaimName, String.class);
            Role defaultRole =
                    configuration.getAuthenticatedDefaultRole() != null
                            ? configuration.getAuthenticatedDefaultRole()
                            : Role.USER;
            Role newRole = computeRole(oidcRoles, defaultRole);
            user.setRole(newRole);
            LOGGER.info("User role set from token. {} -> {}", currentRole, newRole);
        } else {
            LOGGER.debug(
                    "Roles claim '{}' missing in token -> preserving current role: {}",
                    rolesClaimName,
                    currentRole);
        }

        // ----- Groups -----
        List<String> oidcGroups = Collections.emptyList();
        if (configuration.getGroupsClaim() != null) {
            oidcGroups = helper.getClaimAsList(configuration.getGroupsClaim(), String.class);
            LOGGER.debug(" --------- OIDC - GROUPS: {}", oidcGroups);
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
                    "Updating user with synchronized groups found in claims data integrity violation: {}",
                    e.getMessage(),
                    e);
        } finally {
            LOGGER.info("User updated with the following groups: {}", user.getGroups());
        }
    }

    // ---------------------------------------------------------------------
    // Helpers for deterministic role and provider-scoped group reconcile
    // ---------------------------------------------------------------------

    /** Compute the user's role from token roles with deterministic precedence. */
    private Role computeRole(List<String> rolesFromToken, Role defaultRole) {
        if (rolesFromToken == null || rolesFromToken.isEmpty()) {
            return (defaultRole != null) ? defaultRole : Role.USER;
        }
        Role resolved = (defaultRole != null) ? defaultRole : Role.USER;
        for (String r : rolesFromToken) {
            if (r == null) continue;
            String rr = r.trim();
            if (rr.equalsIgnoreCase(Role.ADMIN.name())) return Role.ADMIN;
            if (rr.equalsIgnoreCase(Role.GUEST.name())) resolved = Role.GUEST;
        }
        return resolved;
    }

    /** Normalize group name according to configuration (uppercase if enabled). */
    private String normalizeGroupName(String name) {
        if (name == null) return null;
        return configuration.isGroupNamesUppercase() ? name.toUpperCase(Locale.ROOT) : name;
    }

    /** Query the DB for group IDs tagged as remote for the current provider (no lazy access). */
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

    /**
     * Provider-scoped reconciliation of remote groups with the new IdP set: 1) remove remote groups
     * for THIS provider that are not in the token 2) ensure token groups exist, are marked with
     * sourceService=provider, and are assigned (No lazy attribute access is performed).
     */
    private void reconcileRemoteGroups(User user, Set<String> newGroupNamesRaw) {
        final String provider = configuration.getProvider();
        if (StringUtils.isBlank(provider)) {
            LOGGER.warn("Provider name is empty; skipping remote group reconciliation.");
            return;
        }

        if (user.getGroups() == null) {
            user.setGroups(new LinkedHashSet<>());
        }

        // Normalize new group names once
        Set<String> newGroupNames =
                newGroupNamesRaw.stream()
                        .filter(Objects::nonNull)
                        .map(this::normalizeGroupName)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        // Remote IDs from DB (no lazy attribute access)
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
                LOGGER.debug(
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
                LOGGER.debug("Created remote group '{}' (provider={})", groupName, provider);
            } else {
                updateGroupSourceServiceAttributes(group); // add/backfill provider tag
            }

            UserGroup finalGroup = group;
            boolean alreadyAssigned =
                    user.getGroups().stream()
                            .anyMatch(
                                    g ->
                                            g != null
                                                    && Objects.equals(
                                                            g.getId(), finalGroup.getId()));
            if (!alreadyAssigned) {
                try {
                    userGroupService.assignUserGroup(user.getId(), group.getId());
                    user.getGroups().add(group);
                    LOGGER.debug("Assigned user {} to group '{}'", user.getId(), groupName);
                } catch (NotFoundServiceEx e) {
                    LOGGER.error(
                            "Assignment of user {} to group '{}' failed: {}",
                            user.getId(),
                            groupName,
                            e.getMessage());
                }
            }
        }

        // de-dup by normalized name
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

    /**
     * Backfills/updates the {@code sourceService} attribute for an existing group using {@link
     * UserGroupService#updateAttributes(long, List)} to avoid lazy access to attributes.
     */
    private void updateGroupSourceServiceAttributes(UserGroup group) {
        if (group == null || group.getId() == null) return;

        List<UserGroupAttribute> attributes = group.getAttributes();
        if (attributes == null) attributes = new ArrayList<>();

        Optional<UserGroupAttribute> sourceServiceAttribute =
                attributes.stream()
                        .filter(
                                attribute ->
                                        SOURCE_SERVICE_USER_GROUP_ATTRIBUTE_NAME.equals(
                                                attribute.getName()))
                        .findFirst();

        if (sourceServiceAttribute.isPresent()) {
            sourceServiceAttribute.get().setValue(configuration.getProvider());
        } else {
            attributes.add(createUserGroupSourceServiceAttribute(configuration.getProvider()));
        }

        try {
            userGroupService.updateAttributes(group.getId(), attributes);
        } catch (NotFoundServiceEx e) {
            LOGGER.warn(
                    "Could not persist sourceService attribute for group '{}': {}",
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
                LOGGER.debug("inserted group id: {}", group.getGroupName());
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

    /**
     * Retrieves a user by username. If not found and auto-create is enabled, a new user is created.
     *
     * @param username the username.
     * @param request the HTTP request.
     * @param response the HTTP response.
     * @return a {@link User} instance if found or created, or null otherwise.
     */
    protected User retrieveUserWithAuthorities(
            String username, HttpServletRequest request, HttpServletResponse response) {
        User user = null;
        if (username != null && userService != null) {
            try {
                user = userService.get(username);
            } catch (NotFoundServiceEx notFoundServiceEx) {
                LOGGER.debug("User with username {} not found.", username);
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

    /**
     * Creates a new user.
     *
     * @param userName the username.
     * @param credentials the password.
     * @param rawUser a raw user object.
     * @return a newly created {@link User} instance.
     * @throws BadRequestServiceEx if the request is bad.
     * @throws NotFoundServiceEx if the user cannot be found.
     */
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
