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

import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import org.apache.commons.lang.time.DateUtils;
import org.apache.cxf.jaxrs.impl.ResponseBuilderImpl;
import org.apache.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration.CONFIG_NAME_SUFFIX;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.REFRESH_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.getAccessToken;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.getRefreshAccessToken;

/**
 * This class provides authentication entry point to login using an OAuth2 provider.
 */
public class OAuth2LoginRestImpl implements OAuth2LoginRest, ApplicationContextAware {

    private ApplicationContext applicationContext;

    private final static Logger LOGGER = Logger.getLogger(OAuth2LoginRestImpl.class);


    @Override
    public void login(String provider) {
        HttpServletResponse resp = OAuth2Utils.getResponse();
        OAuth2Configuration configuration = configuration(provider);
        String login = configuration.buildLoginUri();
        try {
            resp.sendRedirect(login);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Response callback(String provider) throws NotFoundWebEx {
        Response.ResponseBuilder result = new ResponseBuilderImpl();
        String token = getAccessToken();
        String refreshToken = getRefreshAccessToken();
        OAuth2Configuration configuration = configuration(provider);
        if (token != null) {
            try {
                result = result.status(302)
                        .location(new URI(configuration.getInternalRedirectUri()));
                if (token != null) {
                    if(LOGGER.isDebugEnabled())
                        LOGGER.info("AccessToken found");
                    result = result.cookie(cookie(ACCESS_TOKEN_PARAM, token));
                }
                if (refreshToken != null){
                        if(LOGGER.isDebugEnabled())
                            LOGGER.info("RefreshToken found");
                    result = result.cookie(cookie(REFRESH_TOKEN_PARAM, refreshToken));
                }
            } catch (URISyntaxException e) {
                LOGGER.error(e);
                result = result
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Exception while parsing the internal redirect url: " + e.getMessage());
            }
        } else {
            result = Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("No access token found.");
        }
        return result.build();
    }

    private NewCookie cookie(String name, String value) {
        Cookie cookie = new Cookie(name, value, "/", null);
        return new AccessCookie(cookie, "", 60 * 60 * 24 * 1000, DateUtils.addDays(new Date(), 1), false, false, "lax");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    protected OAuth2Configuration configuration(String provider) {
        return (OAuth2Configuration) applicationContext.getBean(provider + CONFIG_NAME_SUFFIX);
    }
}
