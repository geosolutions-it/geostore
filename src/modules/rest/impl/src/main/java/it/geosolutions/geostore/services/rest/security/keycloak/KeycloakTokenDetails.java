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
package it.geosolutions.geostore.services.rest.security.keycloak;

import java.util.Calendar;
import java.util.Date;

/**
 * Data class meant to be set as details to an Authentication object. It holds information about the
 * access and the refresh tokens.
 */
public class KeycloakTokenDetails {

    private final Date expiration;
    private String accessToken;
    private String idToken;
    private String refreshToken;

    public KeycloakTokenDetails(String accessToken, String refreshToken, Long exp) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        Date epoch = new Date(0);
        this.expiration = expirationDate(epoch, exp.intValue());
    }

    public KeycloakTokenDetails(String accessToken, String refreshToken, long expIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        Date start = new Date();
        this.expiration = expirationDate(start, Long.valueOf(expIn).intValue());
    }

    private Date expirationDate(Date start, int toAdd) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(start);
        calendar.add(Calendar.SECOND, toAdd);
        return calendar.getTime();
    }

    /** @return the access_token. */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Set the access_token.
     *
     * @param accessToken the access_token.
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /** @return the refresh_token. */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Set the refresh_token.
     *
     * @param refreshToken the refresh_token.
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    /** @return the access_token expiration date. */
    public Date getExpiration() {
        return expiration;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}
