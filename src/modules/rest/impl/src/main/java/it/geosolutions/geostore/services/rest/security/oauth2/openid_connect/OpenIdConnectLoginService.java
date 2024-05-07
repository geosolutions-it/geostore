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

import static it.geosolutions.geostore.services.rest.SessionServiceDelegate.PROVIDER_KEY;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.*;

import it.geosolutions.geostore.services.rest.IdPLoginRest;
import it.geosolutions.geostore.services.rest.security.oauth2.Oauth2LoginService;
import it.geosolutions.geostore.services.rest.security.oauth2.TokenDetails;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Extension point to customize the login and redirect after login performed from the {@link
 * IdPLoginRest};
 */
public class OpenIdConnectLoginService extends Oauth2LoginService {

    private static final Logger LOGGER =
            LogManager.getLogger(OpenIdConnectLoginService.class.getName());

    public OpenIdConnectLoginService(IdPLoginRest loginRest) {
        loginRest.registerService("oidc", this);
    }

    /**
     * @param request the request.
     * @param response the response.
     * @param provider the provider name.
     * @return
     */
    @Override
    public Response doInternalRedirect(
            HttpServletRequest request, HttpServletResponse response, String provider) {
        String token = getAccessToken();
        String refreshToken = getRefreshAccessToken();
        if (token == null && SecurityContextHolder.getContext() != null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null
                    && auth.getDetails() != null
                    && auth.getDetails() instanceof TokenDetails) {
                TokenDetails tokenDetails = ((TokenDetails) auth.getDetails());
                OAuth2AccessToken accessTokenDetails = tokenDetails.getAccessToken();
                if (accessTokenDetails != null) {
                    token = accessTokenDetails.getValue();
                    RequestContextHolder.getRequestAttributes()
                            .setAttribute(ACCESS_TOKEN_PARAM, accessTokenDetails, 0);
                    RequestContextHolder.getRequestAttributes()
                            .setAttribute(OAuth2AuthenticationDetails.ACCESS_TOKEN_VALUE, token, 0);
                    if (accessTokenDetails.getRefreshToken().getValue() != null) {
                        refreshToken = accessTokenDetails.getRefreshToken().getValue();
                        RequestContextHolder.getRequestAttributes()
                                .setAttribute(
                                        REFRESH_TOKEN_PARAM,
                                        accessTokenDetails.getRefreshToken().getValue(),
                                        0);
                    }
                }
                if (tokenDetails.getIdToken() != null) {
                    RequestContextHolder.getRequestAttributes()
                            .setAttribute(ID_TOKEN_PARAM, tokenDetails.getIdToken(), 0);
                    RequestContextHolder.getRequestAttributes()
                            .setAttribute(
                                    OAuth2AuthenticationDetails.ACCESS_TOKEN_VALUE,
                                    tokenDetails.getIdToken(),
                                    0);
                }
            }
        }
        RequestContextHolder.getRequestAttributes().setAttribute(PROVIDER_KEY, provider, 0);
        return buildCallbackResponse(token, refreshToken, provider);
    }
}
