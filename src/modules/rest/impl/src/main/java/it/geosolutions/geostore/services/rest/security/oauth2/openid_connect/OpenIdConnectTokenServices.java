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

import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreRemoteTokenServices;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** RemoteTokenServices that handles specifically the GoogleResponse. */
public class OpenIdConnectTokenServices extends GeoStoreRemoteTokenServices {

    public OpenIdConnectTokenServices(String principalKey) {
        super(new OpenIdConnectAccessTokenConverter(principalKey));
        LOGGER.info(
                "Instantiating OpenIdConnectAccessTokenConverter with principalKey: {}",
                principalKey);
    }

    @Override
    protected Map<String, Object> checkToken(String accessToken) {
        LOGGER.info("Checking token: {}", accessToken);
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("token", accessToken);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", getAuthorizationHeader(accessToken));
        LOGGER.info("Headers: {}", headers);
        String accessTokenUrl = checkTokenEndpointUrl + "?access_token=" + accessToken;
        LOGGER.info("Checking token with url: {}", accessTokenUrl);
        Map<String, Object> reults =
                sendRequestForMap(accessTokenUrl, formData, headers, HttpMethod.GET);
        LOGGER.info("Got sendRequestForMap results: {}", reults);
        return reults;
    }
}
