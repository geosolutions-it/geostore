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
package it.geosolutions.geostore.services.rest.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils;
import it.geosolutions.geostore.services.rest.security.oauth2.TokenDetails;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.index.qual.NonNegative;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.ExpiringOAuth2RefreshToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.web.client.RestTemplate;

/**
 * A cache for OAuth2 Authentication object. Authentication instances are identified by the
 * corresponding accessToken. Uses per-entry expiration based on the token's actual expiry time.
 */
public class TokenAuthenticationCache implements ApplicationContextAware {

    private static final Logger LOGGER = LogManager.getLogger(TokenAuthenticationCache.class);
    private final Cache<String, Authentication> cache;
    private final long defaultExpirationNanos;
    private ApplicationContext context;

    public TokenAuthenticationCache() {
        this(1000, 480);
    }

    public TokenAuthenticationCache(int cacheSize, int cacheExpirationMinutes) {
        this.defaultExpirationNanos = TimeUnit.MINUTES.toNanos(cacheExpirationMinutes);
        this.cache =
                Caffeine.newBuilder()
                        .maximumSize(cacheSize)
                        .expireAfter(
                                new Expiry<String, Authentication>() {
                                    @Override
                                    public long expireAfterCreate(
                                            String key, Authentication value, long currentTime) {
                                        return computeExpirationNanos(value);
                                    }

                                    @Override
                                    public long expireAfterUpdate(
                                            String key,
                                            Authentication value,
                                            long currentTime,
                                            @NonNegative long currentDuration) {
                                        return computeExpirationNanos(value);
                                    }

                                    @Override
                                    public long expireAfterRead(
                                            String key,
                                            Authentication value,
                                            long currentTime,
                                            @NonNegative long currentDuration) {
                                        return currentDuration;
                                    }
                                })
                        .evictionListener(
                                (key, authentication, cause) -> {
                                    if (cause == RemovalCause.EXPIRED && authentication != null) {
                                        revokeAuthIfRefreshExpired(authentication);
                                    }
                                })
                        .recordStats()
                        .build();
    }

    private long computeExpirationNanos(Authentication authentication) {
        TokenDetails details = OAuth2Utils.getTokenDetails(authentication);
        if (details != null && details.getAccessToken() != null) {
            Date exp = details.getAccessToken().getExpiration();
            if (exp != null) {
                long remainingMs = exp.getTime() - System.currentTimeMillis();
                if (remainingMs > 0) {
                    return TimeUnit.MILLISECONDS.toNanos(remainingMs);
                }
            }
        }
        return defaultExpirationNanos;
    }

    /**
     * Perform a revoke authorization when the cache entry expires.
     *
     * @param authentication the authentication object.
     */
    protected void revokeAuthIfRefreshExpired(Authentication authentication) {
        TokenDetails tokenDetails = OAuth2Utils.getTokenDetails(authentication);
        if (tokenDetails != null && tokenDetails.getAccessToken() != null) {
            OAuth2AccessToken accessToken = tokenDetails.getAccessToken();
            OAuth2RefreshToken refreshToken = accessToken.getRefreshToken();
            if (refreshToken instanceof ExpiringOAuth2RefreshToken) {
                ExpiringOAuth2RefreshToken expiring = (ExpiringOAuth2RefreshToken) refreshToken;
                OAuth2Configuration configuration =
                        (OAuth2Configuration) context.getBean(tokenDetails.getProvider());
                if (configuration != null && configuration.isEnabled()) {
                    if (expiring.getExpiration().after(new Date())) {
                        OAuth2Configuration.Endpoint revokeEndpoint =
                                configuration.buildRevokeEndpoint(
                                        expiring.getValue(), accessToken.getValue(), configuration);
                        if (revokeEndpoint != null) {
                            RestTemplate template = new RestTemplate();
                            ResponseEntity<String> responseEntity =
                                    template.exchange(
                                            revokeEndpoint.getUrl(),
                                            revokeEndpoint.getMethod(),
                                            null,
                                            String.class);
                            if (responseEntity.getStatusCode().value() != 200) {
                                LOGGER.error(
                                        "Error while revoking authorization. Error is: {}",
                                        responseEntity.getBody());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Retrieve the authentication by its accessToken value.
     *
     * @param accessToken the accessToken.
     * @return the Authentication identified by the token if present. Null otherwise.
     */
    public Authentication get(String accessToken) {
        return cache.asMap().get(accessToken);
    }

    /**
     * Put an Authentication instance identified by an accessToken value. If the passed
     * Authentication instance does not have a refresh token, and we have an old one that has, the
     * refresh Token is set to the new instance.
     *
     * @param accessToken the access token identifying the instance to update
     * @param authentication the Authentication to cache.
     * @return the Authentication cached.
     */
    public Authentication putCacheEntry(String accessToken, Authentication authentication) {
        Authentication old = get(accessToken);
        TokenDetails oldDetails = OAuth2Utils.getTokenDetails(old);
        if (oldDetails != null) {
            TokenDetails newDetails = OAuth2Utils.getTokenDetails(authentication);
            OAuth2AccessToken newToken = newDetails.getAccessToken();
            OAuth2AccessToken oldToken = oldDetails.getAccessToken();
            if (newToken.getRefreshToken() == null && oldToken != null) {
                DefaultOAuth2AccessToken defaultOAuth2AccessToken =
                        new DefaultOAuth2AccessToken(newToken.getValue());
                defaultOAuth2AccessToken.setRefreshToken(oldToken.getRefreshToken());
                newDetails.setAccessToken(defaultOAuth2AccessToken);
            }
        }

        this.cache.put(accessToken, authentication);
        return authentication;
    }

    /**
     * Remove an authentication from the cache.
     *
     * @param accessToken the accessToken identifying the authentication to remove.
     */
    public void removeEntry(String accessToken) {
        this.cache.invalidate(accessToken);
    }

    public Cache<String, Authentication> getCache() {
        return cache;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }
}
