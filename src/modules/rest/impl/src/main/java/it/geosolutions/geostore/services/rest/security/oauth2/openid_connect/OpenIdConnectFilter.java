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

import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.DiscoveryClient;
import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreOAuthRestTemplate;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2GeoStoreAuthenticationFilter;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.bearer.OpenIdTokenValidator;
import java.io.IOException;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.sf.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.jwt.Jwt;
import org.springframework.security.jwt.JwtHelper;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.security.oauth2.provider.token.RemoteTokenServices;

/** OpenId Connect filter implementation. */
public class OpenIdConnectFilter extends OAuth2GeoStoreAuthenticationFilter {

    private static final Logger LOGGER = LogManager.getLogger(OpenIdConnectFilter.class);

    private final OpenIdTokenValidator bearerTokenValidator;

    /**
     * @param tokenServices a RemoteTokenServices instance.
     * @param oAuth2RestTemplate the rest template to use for OAuth2 requests.
     * @param configuration the OAuth2 configuration.
     * @param tokenAuthenticationCache the cache.
     */
    public OpenIdConnectFilter(
            RemoteTokenServices tokenServices,
            GeoStoreOAuthRestTemplate oAuth2RestTemplate,
            OAuth2Configuration configuration,
            TokenAuthenticationCache tokenAuthenticationCache,
            OpenIdTokenValidator bearerTokenValidator) {
        super(tokenServices, oAuth2RestTemplate, configuration, tokenAuthenticationCache);
        if (configuration.getDiscoveryUrl() != null && !"".equals(configuration.getDiscoveryUrl()))
            new DiscoveryClient(configuration.getDiscoveryUrl()).autofill(configuration);
        this.bearerTokenValidator = bearerTokenValidator;
    }

    @Override
    protected String getPreAuthenticatedPrincipal(
            HttpServletRequest req, HttpServletResponse resp, OAuth2AccessToken accessToken)
            throws IOException, ServletException {
        String result = super.getPreAuthenticatedPrincipal(req, resp, accessToken);

        OAuth2AuthenticationType type =
                (OAuth2AuthenticationType) req.getAttribute(OAUTH2_AUTHENTICATION_TYPE_KEY);
        if ((type != null)
                && (type.equals(OAuth2AuthenticationType.BEARER))
                && (bearerTokenValidator != null)) {
            if (!((OpenIdConnectConfiguration) configuration).isAllowBearerTokens()) {
                LOGGER.warn(
                        "OIDC: received an attached Bearer token, but Bearer tokens aren't allowed!");
                throw new IOException(
                        "OIDC: received an attached Bearer token, but Bearer tokens aren't allowed!");
            }
            // we must validate
            String token = null;
            if (accessToken != null
                    && !accessToken.isExpired()
                    && accessToken.getValue() != null
                    && !accessToken.getValue().isEmpty()) {
                token = accessToken.getValue();
            } else {
                token = (String) req.getAttribute(OAuth2AuthenticationDetails.ACCESS_TOKEN_VALUE);
            }
            Map userinfoMap = (Map) req.getAttribute(OAUTH2_ACCESS_TOKEN_CHECK_KEY);
            Jwt decodedAccessToken = JwtHelper.decode(token);
            Map accessTokenClaims = JSONObject.fromObject(decodedAccessToken.getClaims());
            try {
                bearerTokenValidator.verifyToken(
                        (OpenIdConnectConfiguration) configuration, accessTokenClaims, userinfoMap);
            } catch (Exception e) {
                throw new IOException("Attached Bearer Token is invalid", e);
            }
        }

        return result;
    }
}
