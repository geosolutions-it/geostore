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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 *
 * ====================================================================
 *
 * This software consists of voluntary contributions made by developers
 * of GeoSolutions. For more information on GeoSolutions, please see
 * <http://www.geo-solutions.it/>.
 *
 */
package it.geosolutions.geostore.services.rest.security.oauth2.google;

import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.DiscoveryClient;
import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreOAuthRestTemplate;
import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreRemoteTokenServices;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2GeoStoreAuthenticationFilter;
import org.apache.commons.lang3.StringUtils;

/** Google OAuth2 filter implementation. */
public class OpenIdFilter extends OAuth2GeoStoreAuthenticationFilter {

    public OpenIdFilter(
            GeoStoreRemoteTokenServices tokenServices,
            GeoStoreOAuthRestTemplate oAuth2RestOperations,
            OAuth2Configuration configuration,
            TokenAuthenticationCache cache) {
        super(tokenServices, oAuth2RestOperations, configuration, cache);
        if (StringUtils.isNotBlank(configuration.getDiscoveryUrl())) {
            new DiscoveryClient(configuration.getDiscoveryUrl()).autofill(configuration);
        }
    }
}
