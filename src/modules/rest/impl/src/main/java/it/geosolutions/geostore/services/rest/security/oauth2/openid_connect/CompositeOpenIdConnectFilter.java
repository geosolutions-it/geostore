/* ====================================================================
 *
 * Copyright (C) 2024 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.security.oauth2.openid_connect;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration.CONFIG_NAME_SUFFIX;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;

import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.rest.IdPLoginRest;
import it.geosolutions.geostore.services.rest.RESTSessionService;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreOAuthRestTemplate;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.AudienceAccessTokenValidator;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.JwksRsaKeyProvider;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.MultiTokenValidator;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.SubjectTokenValidator;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.token.DefaultAccessTokenRequest;
import org.springframework.web.filter.GenericFilterBean;

/**
 * Composite filter that routes requests to per-provider {@link OpenIdConnectFilter} instances.
 *
 * <p>On startup ({@link #afterPropertiesSet()}), discovers all {@link OpenIdConnectConfiguration}
 * beans and creates per-provider filters, validators, rest templates, and caches programmatically.
 *
 * <p>Request routing:
 *
 * <ul>
 *   <li><b>Already authenticated:</b> skip, chain through
 *   <li><b>Callback URL:</b> route to the matching provider's filter
 *   <li><b>Bearer token:</b> iterate all enabled providers; first success wins
 *   <li><b>No match:</b> chain through to other auth mechanisms
 * </ul>
 */
public class CompositeOpenIdConnectFilter extends GenericFilterBean
        implements ApplicationContextAware {

    private static final Logger LOGGER = LogManager.getLogger(CompositeOpenIdConnectFilter.class);

    private static final Pattern CALLBACK_PATTERN = Pattern.compile(".*/openid/([^/]+)/callback.*");
    private static final Pattern LOGIN_PATTERN = Pattern.compile(".*/openid/([^/]+)/login.*");

    private ApplicationContext applicationContext;
    private final Map<String, OpenIdConnectFilter> providerFilters = new LinkedHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws ServletException {
        super.afterPropertiesSet();
        if (applicationContext == null) {
            LOGGER.warn(
                    "ApplicationContext not available; no provider filters will be initialized");
            return;
        }

        Map<String, OpenIdConnectConfiguration> configs =
                applicationContext.getBeansOfType(OpenIdConnectConfiguration.class);
        for (Map.Entry<String, OpenIdConnectConfiguration> entry : configs.entrySet()) {
            OpenIdConnectConfiguration config = entry.getValue();
            String beanName = entry.getKey();

            // Derive provider name from bean name (e.g. "oidcOAuth2Config" -> "oidc")
            String providerName = beanName.replace(CONFIG_NAME_SUFFIX, "");

            if (!config.isEnabled()) {
                LOGGER.info(
                        "Provider '{}' is disabled (bean={}); skipping filter creation",
                        providerName,
                        beanName);
                continue;
            }

            LOGGER.info(
                    "Initializing OpenID Connect filter for provider '{}' (bean={})",
                    providerName,
                    beanName);

            // Create per-provider components
            OpenIdConnectTokenServices tokenServices =
                    new OpenIdConnectTokenServices(config.getPrincipalKey());
            GeoStoreOAuthRestTemplate restTemplate =
                    OpenIdConnectRestTemplateFactory.create(
                            config, new DefaultAccessTokenRequest());

            TokenAuthenticationCache cache =
                    new TokenAuthenticationCache(
                            config.getCacheSize(), config.getCacheExpirationMinutes());

            JwksRsaKeyProvider jwksKeyProvider = null;
            String jwksUri = config.getIdTokenUri();
            if (jwksUri == null || jwksUri.isEmpty()) {
                jwksUri = config.getJwkURI();
            }
            if (jwksUri != null && !jwksUri.isEmpty()) {
                jwksKeyProvider = new JwksRsaKeyProvider(jwksUri);
            }

            MultiTokenValidator validator =
                    new MultiTokenValidator(
                            Arrays.asList(
                                    new AudienceAccessTokenValidator(),
                                    new SubjectTokenValidator()));

            OpenIdConnectFilter filter =
                    new OpenIdConnectFilter(
                            tokenServices, restTemplate, config, cache, validator, jwksKeyProvider);

            providerFilters.put(providerName, filter);
            LOGGER.info("Provider '{}' filter initialized successfully", providerName);

            // Register the programmatically-created rest template and cache as Spring beans
            // so that session service delegates (which look up beans by name) can find them.
            registerSingletonBean(
                    providerName + "OpenIdRestTemplate", restTemplate, "OAuth2RestTemplate");
            registerSingletonBean("oAuth2Cache", cache, "TokenAuthenticationCache");

            // Register per-provider session delegate and login service for non-default providers.
            // The default "oidc" provider's session delegate and login service are already
            // defined in applicationContext.xml, so only create them for additional providers.
            if (!"oidc".equals(providerName)) {
                registerPerProviderServices(providerName);
            }
        }

        if (providerFilters.isEmpty()) {
            LOGGER.info("No enabled OpenID Connect providers found");
        } else {
            LOGGER.info(
                    "Composite filter initialized with {} provider(s): {}",
                    providerFilters.size(),
                    providerFilters.keySet());
        }
    }

    private void registerPerProviderServices(String providerName) {
        try {
            RESTSessionService restSessionService =
                    applicationContext.getBean("restSessionService", RESTSessionService.class);
            UserService userService = applicationContext.getBean("userService", UserService.class);
            new OpenIdConnectSessionServiceDelegate(restSessionService, userService, providerName);
            LOGGER.info("Registered session delegate for provider '{}'", providerName);
        } catch (Exception e) {
            LOGGER.warn(
                    "Could not register session delegate for provider '{}': {}",
                    providerName,
                    e.getMessage());
        }

        try {
            IdPLoginRest idpLoginRest =
                    applicationContext.getBean("idpLoginRest", IdPLoginRest.class);
            new OpenIdConnectLoginService(idpLoginRest, providerName);
            LOGGER.info("Registered login service for provider '{}'", providerName);
        } catch (Exception e) {
            LOGGER.warn(
                    "Could not register login service for provider '{}': {}",
                    providerName,
                    e.getMessage());
        }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;

        // Already authenticated -> skip
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth != null) {
            chain.doFilter(req, res);
            return;
        }

        // No providers configured -> skip
        if (providerFilters.isEmpty()) {
            chain.doFilter(req, res);
            return;
        }

        String uri = request.getRequestURI();

        // Check for callback URL: /openid/{provider}/callback
        Matcher callbackMatcher = CALLBACK_PATTERN.matcher(uri);
        if (callbackMatcher.matches()) {
            String provider = callbackMatcher.group(1);
            OpenIdConnectFilter filter = providerFilters.get(provider);
            if (filter != null) {
                LOGGER.debug("Routing callback to provider '{}'", provider);
                filter.doFilter(req, res, chain);
                return;
            }
            LOGGER.warn("Callback for unknown provider '{}'; passing through", provider);
            chain.doFilter(req, res);
            return;
        }

        // Check for login URL: /openid/{provider}/login
        Matcher loginMatcher = LOGIN_PATTERN.matcher(uri);
        if (loginMatcher.matches()) {
            String provider = loginMatcher.group(1);
            OpenIdConnectFilter filter = providerFilters.get(provider);
            if (filter != null) {
                LOGGER.debug("Routing login to provider '{}'", provider);
                filter.doFilter(req, res, chain);
                return;
            }
            LOGGER.warn("Login for unknown provider '{}'; passing through", provider);
            chain.doFilter(req, res);
            return;
        }

        // Check for bearer token: try each enabled provider.
        // Use a no-op chain for each attempt because each provider's filter always calls
        // chain.doFilter() internally. We call the real chain exactly once at the end.
        String bearerToken = OAuth2Utils.tokenFromParamsOrBearer(ACCESS_TOKEN_PARAM, request);
        if (bearerToken != null) {
            FilterChain noOpChain = (r, s) -> {};
            for (Map.Entry<String, OpenIdConnectFilter> entry : providerFilters.entrySet()) {
                String providerName = entry.getKey();
                OpenIdConnectFilter filter = entry.getValue();
                LOGGER.debug("Trying bearer token with provider '{}'", providerName);
                try {
                    filter.doFilter(req, res, noOpChain);
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null) {
                        LOGGER.debug("Bearer token authenticated by provider '{}'", providerName);
                        chain.doFilter(req, res);
                        return;
                    }
                } catch (Exception e) {
                    LOGGER.debug(
                            "Bearer token rejected by provider '{}': {}",
                            providerName,
                            e.getMessage());
                    // Clear any partial state and try next provider
                    SecurityContextHolder.clearContext();
                }
            }
            // No provider accepted the bearer token; chain through
            chain.doFilter(req, res);
            return;
        }

        // No bearer token and not a login/callback URL: delegate to the first enabled
        // provider filter for general request processing (e.g. redirect handling)
        if (!providerFilters.isEmpty()) {
            OpenIdConnectFilter firstFilter = providerFilters.values().iterator().next();
            firstFilter.doFilter(req, res, chain);
            return;
        }

        chain.doFilter(req, res);
    }

    private void registerSingletonBean(String name, Object bean, String label) {
        if (applicationContext instanceof ConfigurableApplicationContext) {
            ConfigurableListableBeanFactory factory =
                    ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
            if (!factory.containsSingleton(name)) {
                factory.registerSingleton(name, bean);
                LOGGER.info("Registered {} bean as '{}'", label, name);
            }
        }
    }

    public Map<String, OpenIdConnectFilter> getProviderFilters() {
        return providerFilters;
    }
}
