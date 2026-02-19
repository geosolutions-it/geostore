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

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;

import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.DiscoveryClient;
import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreOAuthRestTemplate;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2GeoStoreAuthenticationFilter;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.JwksRsaKeyProvider;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.OpenIdTokenValidator;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.sf.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.jwt.Jwt;
import org.springframework.security.jwt.JwtHelper;
import org.springframework.security.jwt.crypto.sign.RsaVerifier;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.security.oauth2.provider.token.RemoteTokenServices;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/** OpenId Connect filter implementation. */
public class OpenIdConnectFilter extends OAuth2GeoStoreAuthenticationFilter {

    private static final Logger LOGGER = LogManager.getLogger(OpenIdConnectFilter.class);

    private final OpenIdTokenValidator bearerTokenValidator;
    private final JwksRsaKeyProvider jwksKeyProvider;

    /**
     * @param tokenServices a RemoteTokenServices instance.
     * @param oAuth2RestTemplate the rest template to use for OAuth2 requests.
     * @param configuration the OAuth2 configuration.
     * @param tokenAuthenticationCache the cache.
     * @param bearerTokenValidator validator for attached Bearer tokens (may be null to disable)
     * @param jwksKeyProvider provider for JWKS RSA keys for signature verification (may be null)
     */
    public OpenIdConnectFilter(
            RemoteTokenServices tokenServices,
            GeoStoreOAuthRestTemplate oAuth2RestTemplate,
            OAuth2Configuration configuration,
            TokenAuthenticationCache tokenAuthenticationCache,
            OpenIdTokenValidator bearerTokenValidator,
            JwksRsaKeyProvider jwksKeyProvider) {
        super(tokenServices, oAuth2RestTemplate, configuration, tokenAuthenticationCache);
        if (configuration.getDiscoveryUrl() != null
                && !"".equals(configuration.getDiscoveryUrl())) {
            new DiscoveryClient(configuration.getDiscoveryUrl()).autofill(configuration);
        }
        this.bearerTokenValidator = bearerTokenValidator;
        this.jwksKeyProvider = jwksKeyProvider;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String getPreAuthenticatedPrincipal(
            HttpServletRequest req, HttpServletResponse resp, OAuth2AccessToken accessToken)
            throws IOException, ServletException {

        OAuth2AuthenticationType type =
                (OAuth2AuthenticationType) req.getAttribute(OAUTH2_AUTHENTICATION_TYPE_KEY);

        // For BEARER tokens with a validator configured, validate directly
        // and extract the principal from claims — skip Spring's introspection-based
        // super.attemptAuthentication() which can fail with OIDC-only providers.
        if (type != null
                && type.equals(OAuth2AuthenticationType.BEARER)
                && bearerTokenValidator != null) {
            if (!((OpenIdConnectConfiguration) configuration).isAllowBearerTokens()) {
                LOGGER.warn(
                        "OIDC: received an attached Bearer token, but Bearer tokens aren't allowed!");
                throw new IOException(
                        "OIDC: received an attached Bearer token, but Bearer tokens aren't allowed!");
            }

            // Resolve the token value
            String token = resolveTokenValue(req, accessToken);
            if (token == null || token.isEmpty()) {
                LOGGER.error(
                        "OIDC: Bearer token validation requested but no token was found in request context");
                throw new IOException("Attached Bearer Token is missing");
            }

            // Resolve claims based on configured bearer token strategy
            OpenIdConnectConfiguration oidcConfig = (OpenIdConnectConfiguration) configuration;
            String strategy =
                    oidcConfig.getBearerTokenStrategy() != null
                            ? oidcConfig.getBearerTokenStrategy()
                            : "jwt";
            Map<String, Object> bearerClaims = resolveBearerClaims(token, strategy);

            if (bearerClaims == null) {
                LOGGER.warn(
                        "OIDC: Bearer token could not be validated with strategy '{}'", strategy);
                throw new IOException("Attached Bearer Token is invalid");
            }

            // Extract principal from claims using configured keys and common fallbacks
            String principal =
                    coalesceClaimValue(
                            bearerClaims,
                            configuration.getUniqueUsername(),
                            configuration.getPrincipalKey());
            if (principal == null) {
                principal =
                        coalesceClaimValue(
                                bearerClaims,
                                "upn",
                                "preferred_username",
                                "unique_name",
                                "user_name",
                                "username",
                                "email",
                                "sub",
                                "oid");
            }

            if (principal != null && !principal.isEmpty()) {
                LOGGER.info(
                        "Authenticated OIDC Bearer token for user ({}): {}", strategy, principal);
                // Place the access token on the rest template context so that
                // createPreAuthentication() -> addAuthoritiesFromToken() can read
                // rolesClaim / groupsClaim from the JWT.
                if (accessToken != null) {
                    restTemplate.getOAuth2ClientContext().setAccessToken(accessToken);
                }
                return principal;
            }

            LOGGER.warn(
                    "OIDC: Bearer token validated but no principal could be resolved from claims");
            return null;
        }

        // For USER auth type or BEARER without validator, use the standard flow
        return super.getPreAuthenticatedPrincipal(req, resp, accessToken);
    }

    /**
     * Resolves the bearer token value from the request, checking OAuth2AccessToken, Spring
     * attribute, and our own attribute.
     */
    private String resolveTokenValue(HttpServletRequest req, OAuth2AccessToken accessToken) {
        String token = null;
        if (accessToken != null
                && !accessToken.isExpired()
                && accessToken.getValue() != null
                && !accessToken.getValue().isEmpty()) {
            token = accessToken.getValue();
        }
        if (token == null) {
            Object fromSpring = req.getAttribute(OAuth2AuthenticationDetails.ACCESS_TOKEN_VALUE);
            if (fromSpring instanceof String) {
                token = (String) fromSpring;
            }
        }
        if (token == null) {
            Object fromOurFlow = req.getAttribute(ACCESS_TOKEN_PARAM);
            if (fromOurFlow instanceof String) {
                token = (String) fromOurFlow;
            }
        }
        return token;
    }

    /**
     * Resolves bearer token claims based on the configured strategy.
     *
     * @param token the bearer token value.
     * @param strategy "jwt" (default), "introspection", or "auto" (JWT with introspection
     *     fallback).
     * @return the claims map, or null if validation fails.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveBearerClaims(String token, String strategy)
            throws IOException {
        if ("introspection".equalsIgnoreCase(strategy)) {
            return introspectToken(token);
        } else if ("auto".equalsIgnoreCase(strategy)) {
            try {
                return validateBearerJwt(token);
            } catch (Exception e) {
                LOGGER.info(
                        "OIDC: JWT validation failed, falling back to introspection: {}",
                        e.getMessage());
                return introspectToken(token);
            }
        } else {
            // Default: "jwt"
            return validateBearerJwt(token);
        }
    }

    /**
     * Validates a bearer token as a JWT: decode, verify signature via JWKS, check exp/iat, and run
     * the configured token validator.
     *
     * @return the claims map from the JWT.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> validateBearerJwt(String token) throws IOException {
        Map<String, Object> accessTokenClaims;
        try {
            Jwt decodedAccessToken;
            if (jwksKeyProvider != null) {
                String kid = extractKidFromHeader(token);
                RSAPublicKey key = jwksKeyProvider.getKey(kid);
                if (key == null) {
                    throw new IOException("No JWK key found for kid: " + kid);
                }
                decodedAccessToken = JwtHelper.decodeAndVerify(token, new RsaVerifier(key));
            } else {
                LOGGER.warn(
                        "OIDC: No JWKS key provider configured — decoding bearer token"
                                + " WITHOUT signature verification");
                decodedAccessToken = JwtHelper.decode(token);
            }
            String claimsJson = decodedAccessToken.getClaims();
            accessTokenClaims = (Map<String, Object>) JSONObject.fromObject(claimsJson);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("OIDC: Could not decode/verify bearer token", e);
            throw new IOException("Attached Bearer Token is invalid (decoding failed)", e);
        }

        // Check token expiry from claims
        Object expObj = accessTokenClaims.get("exp");
        if (expObj != null) {
            long expSeconds;
            if (expObj instanceof Number) {
                expSeconds = ((Number) expObj).longValue();
            } else {
                try {
                    expSeconds = Long.parseLong(String.valueOf(expObj));
                } catch (NumberFormatException nfe) {
                    throw new IOException("Bearer token 'exp' claim is not a valid number");
                }
            }
            if (System.currentTimeMillis() / 1000 > expSeconds) {
                LOGGER.warn("OIDC: Bearer token has expired (exp={})", expSeconds);
                throw new IOException("Attached Bearer Token has expired");
            }
        }

        // Check iat (issued-at) if maxTokenAgeSecs is configured
        int maxTokenAgeSecs = ((OpenIdConnectConfiguration) configuration).getMaxTokenAgeSecs();
        if (maxTokenAgeSecs > 0) {
            Object iatObj = accessTokenClaims.get("iat");
            if (iatObj != null) {
                long iatSeconds;
                if (iatObj instanceof Number) {
                    iatSeconds = ((Number) iatObj).longValue();
                } else {
                    try {
                        iatSeconds = Long.parseLong(String.valueOf(iatObj));
                    } catch (NumberFormatException nfe) {
                        throw new IOException("Bearer token 'iat' claim is not a valid number");
                    }
                }
                long age = System.currentTimeMillis() / 1000 - iatSeconds;
                if (age > maxTokenAgeSecs) {
                    LOGGER.warn(
                            "OIDC: Bearer token is too old (iat={}, age={}s, max={}s)",
                            iatSeconds,
                            age,
                            maxTokenAgeSecs);
                    throw new IOException("Attached Bearer Token is too old");
                }
            }
        }

        try {
            bearerTokenValidator.verifyToken(
                    (OpenIdConnectConfiguration) configuration, accessTokenClaims, null);
        } catch (Exception e) {
            throw new IOException("Attached Bearer Token is invalid", e);
        }

        return accessTokenClaims;
    }

    /**
     * Validates a bearer token via RFC 7662 Token Introspection. POSTs the token to the
     * introspection endpoint with client credentials using Basic auth.
     *
     * @return the claims map from the introspection response, or null if token is inactive.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> introspectToken(String token) throws IOException {
        String introspectionUrl = configuration.getIntrospectionEndpoint();
        if (introspectionUrl == null || introspectionUrl.isEmpty()) {
            throw new IOException(
                    "Bearer token introspection requested but no introspection endpoint"
                            + " is configured");
        }

        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("token", token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // Use Basic auth with client credentials (RFC 7662 Section 2.1)
        String clientId = configuration.getClientId();
        String clientSecret = configuration.getClientSecret();
        if (clientId != null
                && !clientId.isEmpty()
                && clientSecret != null
                && !clientSecret.isEmpty()) {
            String credentials = clientId + ":" + clientSecret;
            String encoded =
                    Base64.getEncoder()
                            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encoded);
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formParams, headers);

        try {
            RestTemplate rt = new RestTemplate();
            Map<String, Object> response = rt.postForObject(introspectionUrl, request, Map.class);

            if (response == null) {
                LOGGER.warn("OIDC: Token introspection returned null response");
                return null;
            }

            Object active = response.get("active");
            boolean isActive =
                    Boolean.TRUE.equals(active)
                            || (active instanceof String
                                    && "true".equalsIgnoreCase((String) active));
            if (!isActive) {
                LOGGER.warn("OIDC: Token introspection returned active=false");
                return null;
            }

            LOGGER.info("OIDC: Token introspection successful (active=true)");
            return response;
        } catch (Exception e) {
            LOGGER.error("OIDC: Token introspection failed", e);
            throw new IOException("Token introspection failed", e);
        }
    }

    /**
     * Extracts the "kid" (key ID) from a JWT's header segment. Returns empty string if not present.
     */
    private String extractKidFromHeader(String token) {
        try {
            int firstDot = token.indexOf('.');
            if (firstDot < 0) return "";
            String headerSegment = token.substring(0, firstDot);
            String headerJson =
                    new String(
                            Base64.getUrlDecoder().decode(headerSegment), StandardCharsets.UTF_8);
            JSONObject header = JSONObject.fromObject(headerJson);
            return header.optString("kid", "");
        } catch (Exception e) {
            LOGGER.debug("Could not extract kid from JWT header", e);
            return "";
        }
    }

    /**
     * Look up claim values from the map by the given keys (case-insensitive), returning the first
     * non-blank match.
     */
    private String coalesceClaimValue(Map<String, Object> claims, String... keys) {
        if (claims == null) return null;
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            // Direct lookup first
            Object value = claims.get(key);
            if (value == null) {
                // Case-insensitive fallback
                for (Map.Entry<String, Object> e : claims.entrySet()) {
                    if (key.equalsIgnoreCase(e.getKey())) {
                        value = e.getValue();
                        break;
                    }
                }
            }
            if (value != null) {
                // Handle array-type claims (e.g., ["a@b.com", "c@d.com"])
                if (value instanceof Collection) {
                    Collection<?> coll = (Collection<?>) value;
                    if (!coll.isEmpty()) {
                        value = coll.iterator().next();
                    }
                } else if (value.getClass().isArray() && Array.getLength(value) > 0) {
                    value = Array.get(value, 0);
                }
                String str = String.valueOf(value);
                if (!str.isEmpty()) return str;
            }
        }
        return null;
    }
}
