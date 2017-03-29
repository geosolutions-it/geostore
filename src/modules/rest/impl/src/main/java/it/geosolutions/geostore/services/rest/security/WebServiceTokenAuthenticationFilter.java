/* ====================================================================
 *
 * Copyright (C) 2015 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.security;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.log4j.Logger;
import org.springframework.security.core.Authentication;

/**
 * Token based authentication filter that looks for the token calling an external webservice.
 * 
 * The url of the service needs to be configured. A placeholder in the url will be replaced by
 * the actual token.
 * 
 * The result of the web service call will be parsed using given regular expression to:
 *  - check if the token is valid
 *  - extract the user name from the result
 * 
 * @author Mauro Bartolomeoli
 *
 */
public class WebServiceTokenAuthenticationFilter extends TokenAuthenticationFilter {
    
    private final static Logger LOGGER = Logger.getLogger(WebServiceTokenAuthenticationFilter.class);
    
    private String url;
    
    // compiled user search regex
    Pattern searchUserRegex = null;

    // connection timeout to the mapper web service (in seconds)
    long connectTimeout = 5;

    // read timeout to the mapper web service (in seconds)
    int readTimeout = 10;

    // optional external httpClient for web service connection (used mainly for tests)
    private HttpClient httpClient = null;

    public WebServiceTokenAuthenticationFilter(String url) {
        super();
        this.url = url;
    }
    
    /**
     * Regular expression to extract the username from the
     * webservice response.
     * 
     * The first group in the expression will be used for extraction.
     * 
     * @param searchUser
     */
    public void setSearchUser(String searchUser) {
        searchUserRegex = Pattern.compile(searchUser);
    }



    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }



    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * Configures the HTTPClient implementation to be used to connect to the web service.
     * 
     * @param httpClient
     */
    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }
    
    private HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new HttpClient();
        }
        return httpClient;
    }
    
    @Override
    protected Authentication checkToken(String token) {
        String webServiceUrl = url.replace("{token}", token);
        HttpClient client = getHttpClient();
        client.getParams().setParameter(HttpClientParams.CONNECTION_MANAGER_TIMEOUT, connectTimeout * 1000l);
        client.getParams().setParameter(HttpClientParams.SO_TIMEOUT, readTimeout * 1000);
        HttpMethod method = null;
        try {
            LOGGER.debug("Issuing request to webservice: " + url);
            method = new GetMethod(webServiceUrl);
            int statusCode = client.executeMethod(method);
            
            if (statusCode == HttpStatus.SC_OK) {
                // get response content as a single string, without new lines
                // so that is simpler to apply an extraction regular expression
                String response = method.getResponseBodyAsString()
                        .replace("\r", "").replace("\n", "");
                if(response != null) {
                    if (searchUserRegex == null) {
                        return createAuthenticationForUser(response, null, "");
                    } else {
                        Matcher matcher = searchUserRegex.matcher(response);
                        if (matcher.find()) {
                            return createAuthenticationForUser(matcher.group(1), null, response);
                        } else {
                            LOGGER.warn("Error in getting username from webservice response cannot find userName in response");
                        }
                    }
                } else {
                    LOGGER.error("No response received from webservice: " + url);
                }
            }
        } catch (HttpException e) {
            LOGGER.error("Error contacting webservice: " + url, e);
        } catch (IOException e) {
            LOGGER.error("Error reading data from webservice: " + url, e);
        } finally {
            if(method != null) {
                method.releaseConnection();
            }
        }
        return null;
        
    }    
}
