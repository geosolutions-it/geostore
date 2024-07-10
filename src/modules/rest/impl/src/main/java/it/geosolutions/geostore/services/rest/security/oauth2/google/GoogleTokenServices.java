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
package it.geosolutions.geostore.services.rest.security.oauth2.google;

import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreRemoteTokenServices;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** RemoteTokenServices that handles specifically the GoogleResponse. */
public class GoogleTokenServices extends GeoStoreRemoteTokenServices {

    public GoogleTokenServices(String principalKey) {
        super(new GoogleAccessTokenConverter(principalKey));
    }

    @Override
    protected Map<String, Object> checkToken(String accessToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("token", accessToken);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", getAuthorizationHeader(accessToken));
        String accessTokenUrl = checkTokenEndpointUrl + "?access_token=" + accessToken;
        return sendRequestForMap(accessTokenUrl, formData, headers, HttpMethod.POST);
    }

    @Override
    protected void transformNonStandardValuesToStandardValues(Map<String, Object> map) {
        LOGGER.debug("Original map = " + map);
        map.put("client_id", map.get("issued_to")); // Google sends 'client_id' as 'issued_to'
        map.put("user_name", map.get("user_id")); // Google sends 'user_name' as 'user_id'
        LOGGER.debug("Transformed = " + map);
    }

    @Override
    protected String getAuthorizationHeader(String accessToken) {
        String creds = String.format("%s:%s", clientId, clientSecret);
        return "Basic "
                + new String(Base64.getEncoder().encode(creds.getBytes(StandardCharsets.UTF_8)));
    }
}
