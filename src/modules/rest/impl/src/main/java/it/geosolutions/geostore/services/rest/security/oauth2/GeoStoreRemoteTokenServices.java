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

import java.io.IOException;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.AccessTokenConverter;
import org.springframework.security.oauth2.provider.token.RemoteTokenServices;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * Extends the SpringSecurity class to provide an additional method to be able to deal with not
 * fully standardized/check_token endpoint responses.
 */
public class GeoStoreRemoteTokenServices extends RemoteTokenServices {

    protected static Logger LOGGER =
            LogManager.getLogger(GeoStoreRemoteTokenServices.class.getName());

    protected RestOperations restTemplate;

    protected String checkTokenEndpointUrl;

    protected String clientId;

    protected String clientSecret;

    protected AccessTokenConverter tokenConverter;

    protected GeoStoreRemoteTokenServices() {
        // constructor for subclasses that want to configure everything
    }

    protected GeoStoreRemoteTokenServices(AccessTokenConverter tokenConverter) {
        this.tokenConverter = tokenConverter;
        this.restTemplate = new RestTemplate();
        ((RestTemplate) restTemplate)
                .setErrorHandler(
                        new DefaultResponseErrorHandler() {
                            @Override
                            // Ignore 400
                            public void handleError(ClientHttpResponse response)
                                    throws IOException {
                                if (response.getRawStatusCode() != 400) {
                                    super.handleError(response);
                                }
                            }
                        });
    }

    @Override
    public void setRestTemplate(RestOperations restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void setCheckTokenEndpointUrl(String checkTokenEndpointUrl) {
        this.checkTokenEndpointUrl = checkTokenEndpointUrl;
    }

    @Override
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    @Override
    public void setAccessTokenConverter(AccessTokenConverter accessTokenConverter) {
        this.tokenConverter = accessTokenConverter;
    }

    @Override
    public OAuth2Authentication loadAuthentication(String accessToken)
            throws AuthenticationException, InvalidTokenException {
        Map<String, Object> checkTokenResponse = checkToken(accessToken);
        verifyTokenResponse(accessToken, checkTokenResponse);
        transformNonStandardValuesToStandardValues(checkTokenResponse);
        return tokenConverter.extractAuthentication(checkTokenResponse);
    }

    protected void verifyTokenResponse(String accessToken, Map<String, Object> checkTokenResponse) {
        if (checkTokenResponse.containsKey("error")) {
            logger.debug("check_token returned error: " + checkTokenResponse.get("error"));
            throw new InvalidTokenException(accessToken);
        }
    }

    /**
     * Subclass must override this method if values are not standardized.
     *
     * @param map the map of values to be transformed.
     */
    protected void transformNonStandardValuesToStandardValues(Map<String, Object> map) {
        // nothing to do if everything is standardized
    }

    protected Map<String, Object> checkToken(String accessToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("token", accessToken);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", getAuthorizationHeader(accessToken));
        String accessTokenUrl = checkTokenEndpointUrl + "?access_token=" + accessToken;
        return sendRequestForMap(accessTokenUrl, formData, headers, HttpMethod.POST);
    }

    protected String getAuthorizationHeader(String accessToken) {
        return "Bearer " + accessToken;
    }

    protected Map<String, Object> sendRequestForMap(
            String path,
            MultiValueMap<String, String> formData,
            HttpHeaders headers,
            HttpMethod method) {
        if (headers.getContentType() == null) {
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        }
        ParameterizedTypeReference<Map<String, Object>> map =
                new ParameterizedTypeReference<Map<String, Object>>() {};
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Executing request " + path + " form data are " + formData);
            LOGGER.debug("Headers are " + headers);
        }
        return restTemplate
                .exchange(path, method, new HttpEntity<>(formData, headers), map)
                .getBody();
    }
}
