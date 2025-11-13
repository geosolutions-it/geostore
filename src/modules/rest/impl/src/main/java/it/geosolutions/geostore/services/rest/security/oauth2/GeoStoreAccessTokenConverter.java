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

import java.io.Serializable;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.token.DefaultAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.DefaultUserAuthenticationConverter;
import org.springframework.security.oauth2.provider.token.UserAuthenticationConverter;

/**
 * Extends the spring security DefaultAccessTokenConverter to customize the creation of the
 * Authentication object.
 */
public class GeoStoreAccessTokenConverter extends DefaultAccessTokenConverter {

    // For oidc, this will be the userinfo.
    // For oauth2, the result of the "token check" endpoint.
    // This is attached to the request's extensions once it's been retrieved.
    public static String ACCESS_TOKEN_CHECK_KEY = "oauth2.AccessTokenCheckResponse";
    protected static Logger LOGGER = LogManager.getLogger(GeoStoreAccessTokenConverter.class);
    protected UserAuthenticationConverter userTokenConverter;

    public GeoStoreAccessTokenConverter() {
        this("email");
    }

    public GeoStoreAccessTokenConverter(String usernameKey) {
        final DefaultUserAuthenticationConverter defaultUserAuthConverter =
                new GeoStoreAuthenticationConverter(usernameKey);
        setUserTokenConverter(defaultUserAuthConverter);
    }

    /**
     * Converter for the part of the data in the token representing a user.
     *
     * @param userTokenConverter the userTokenConverter to set
     */
    @Override
    public final void setUserTokenConverter(UserAuthenticationConverter userTokenConverter) {
        this.userTokenConverter = userTokenConverter;
        super.setUserTokenConverter(userTokenConverter);
    }

    @Override
    public OAuth2Authentication extractAuthentication(Map<String, ?> map) {
        LOGGER.info("OAuth2Authentication extractAuthentication from {}", map);
        Map<String, String> parameters = new HashMap<>();
        Set<String> scope = parseScopes(map);
        Authentication user = userTokenConverter.extractAuthentication(map);
        LOGGER.info("User: {}", user);
        String clientId = (String) map.get(CLIENT_ID);
        parameters.put(CLIENT_ID, clientId);

        Map<String, Serializable> extensionParameters = new HashMap<>();
        try {
            extensionParameters.put(ACCESS_TOKEN_CHECK_KEY, (Serializable) map);
        } catch (Exception e) {
            //
            LOGGER.info("Exception while trying to record the access token check info", e);
        }

        Set<String> resourceIds = new LinkedHashSet<>(getAud(map));
        LOGGER.info("ResourceIds: {}", resourceIds);
        OAuth2Request request =
                new OAuth2Request(
                        parameters,
                        clientId,
                        null,
                        true,
                        scope,
                        resourceIds,
                        null,
                        null,
                        extensionParameters);
        return new OAuth2Authentication(request, user);
    }

    @SuppressWarnings("unchecked")
    private Collection<String> getAud(Map<String, ?> map) {
        if (!map.containsKey(AUD)) {
            return Collections.emptySet();
        }

        Object aud = map.get(AUD);
        if (aud instanceof Collection) return (Collection) aud;
        else return Collections.singletonList(String.valueOf(aud));
    }

    private Set<String> parseScopes(Map<String, ?> map) {
        // Parsing of scopes coming back from GeoNode is slightly different from
        // the default implementation. Instead of it being a collection, it is a
        // String where multiple scopes are separated by a space.
        Object scopeAsObject = map.containsKey(SCOPE) ? map.get(SCOPE) : "";
        Set<String> scope = new LinkedHashSet<>();
        if (String.class.isAssignableFrom(scopeAsObject.getClass())) {
            String scopeAsString = (String) scopeAsObject;
            Collections.addAll(scope, scopeAsString.split(" "));
        } else if (Collection.class.isAssignableFrom(scopeAsObject.getClass())) {
            Collection<String> scopes = (Collection<String>) scopeAsObject;
            scope.addAll(scopes);
        }
        return scope;
    }
}
