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

import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.provider.token.DefaultUserAuthenticationConverter;

/** GeoStore specific AuthenticationConverter. */
public class GeoStoreAuthenticationConverter extends DefaultUserAuthenticationConverter {
    protected static Logger LOGGER = LogManager.getLogger(GeoStoreAuthenticationConverter.class);
    private Object usernameKey = USERNAME;

    /** Default Constructor. */
    public GeoStoreAuthenticationConverter() {
        super();
    }

    /** Default Constructor. */
    public GeoStoreAuthenticationConverter(final String username_key) {
        super();

        usernameKey = username_key;
    }

    @Override
    public Authentication extractAuthentication(Map<String, ?> map) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.info(
                    "Extracting authentication from a map with following keys: "
                            + map.keySet().stream().collect(Collectors.joining(",")));
        }
        if (map.containsKey(usernameKey)) {
            return new UsernamePasswordAuthenticationToken(map.get(usernameKey), "N/A", null);
        }
        return null;
    }
}
