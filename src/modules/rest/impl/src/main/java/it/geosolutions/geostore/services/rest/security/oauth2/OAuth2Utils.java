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

import it.geosolutions.geostore.core.security.password.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.provider.authentication.BearerTokenExtractor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;

/**
 * A class that groups some constants and utility methods used to handle OAuth2 related tasks.
 * Provides functionality like retrieving tokens from the request, or retrieving
 * the {@link TokenDetails} from an Authentication instance.
 */
public class OAuth2Utils {

    public static final String ID_TOKEN_PARAM = "id_token";

    public static final String ACCESS_TOKEN_PARAM = "access_token";

    public static final String REFRESH_TOKEN_PARAM = "refresh_token";

    public static final String TOKENS_KEY = "tokens_key";

    public static final String AUTH_PROVIDER = "authProvider";


    /**
     * Retrieve a token either from a request param or from the Bearer.
     *
     * @param paramName the name of the request param.
     * @param request   the request.
     * @return the token if found, null otherwise.
     */
    public static String tokenFromParamsOrBearer(String paramName, HttpServletRequest request) {
        String token = getParameterValue(paramName, request);
        if (token == null) {
            token = getBearerToken(request);
        }
        return token;
    }

    public static Date fiveMinutesFromNow() {
        Calendar currentTimeNow = Calendar.getInstance();
        System.out.println("Current time now : " + currentTimeNow.getTime());
        currentTimeNow.add(Calendar.MINUTE, 5);
        return currentTimeNow.getTime();
    }

    /**
     * Retrieve a value  from a request param.
     *
     * @param paramName the name of the request param.
     * @param request   the request.
     * @return the value if found, null otherwise.
     */
    public static String getParameterValue(String paramName, HttpServletRequest request) {
        for (Enumeration<String> iterator = request.getParameterNames();
             iterator.hasMoreElements(); ) {
            final String param = iterator.nextElement();
            if (paramName.equalsIgnoreCase(param)) {
                return request.getParameter(param);
            }
        }

        return null;
    }

    /**
     * Get the bearer token from the header.
     *
     * @param request the request.
     * @return the token if found null otherwise.
     */
    public static String getBearerToken(HttpServletRequest request) {
        Authentication auth = new BearerTokenExtractor().extract(request);
        if (auth != null) return SecurityUtils.getUsername(auth.getPrincipal());

        return null;
    }

    /**
     * Get a request attribute using first a request scope then the session scope.
     *
     * @param name the name of the attribute.
     * @return the token attribute value if found.
     */
    public static String getRequestAttribute(String name) {
        String token = (String) RequestContextHolder.getRequestAttributes().getAttribute(name, 0);
        if (token == null)
            token = (String) RequestContextHolder.getRequestAttributes().getAttribute(name, 1);
        return token;
    }

    /**
     * Get the id token from the request attributes.
     *
     * @return the id token value if found, null otherwise.
     */
    public static String getIdToken() {
        return getRequestAttribute(GeoStoreOAuthRestTemplate.ID_TOKEN_VALUE);
    }

    /**
     * Get the Access Token from the request attributes if present.
     *
     * @return the access token if found, null otherwise.
     */
    public static String getAccessToken() {
        String token = getRequestAttribute(ACCESS_TOKEN_PARAM);
        if (token == null) token = tokenFromParamsOrBearer(ACCESS_TOKEN_PARAM, getRequest());
        return token;
    }

    /**
     * Get the Refresh Toke from request attributes if present.
     *
     * @return the refresh token if found, null otherwise.
     */
    public static String getRefreshAccessToken() {
        String refreshToken = getRequestAttribute(REFRESH_TOKEN_PARAM);
        if (refreshToken == null)
            refreshToken = getParameterValue(REFRESH_TOKEN_PARAM, getRequest());
        return refreshToken;

    }

    /**
     * Return the {@link TokenDetails} stored in the Authentication instance.
     *
     * @param authentication the authentication eventually holding the TokenDetails.
     * @return the token details if found. Null otherwise.
     */
    public static TokenDetails getTokenDetails(Authentication authentication) {
        TokenDetails tokenDetails = null;
        if (authentication != null) {
            Object details = authentication.getDetails();
            if (details instanceof TokenDetails) {
                tokenDetails = ((TokenDetails) details);
            }
        }
        return tokenDetails;
    }

    /**
     * Get the HttpServletRequest from the RequestContext.
     *
     * @return the current HttpServletRequest.
     */
    public static HttpServletRequest getRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();
    }

    /**
     * Get the HttpServletResponse from the RequestContext.
     *
     * @return the current HttpServletResponse.
     */
    public static HttpServletResponse getResponse() {
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getResponse();
    }
}
