package it.geosolutions.geostore.services.rest.security.keycloak;

import java.util.Calendar;
import java.util.Date;

/**
 * Data class meant to be set as details to an Authentication object. It holds information about the access
 * and the refresh tokens.
 */
public class KeycloakTokenDetails {

    private String accessToken;
    private String refreshToken;
    private Date expiration;

    public KeycloakTokenDetails(String accessToken, String refreshToken,Long exp){
        this.accessToken=accessToken;
        this.refreshToken=refreshToken;
        Date epoch=new Date(0);
        this.expiration=expirationDate(epoch,exp.intValue());
    }

    public KeycloakTokenDetails(String accessToken, String refreshToken,long expIn){
        this.accessToken=accessToken;
        this.refreshToken=refreshToken;
        Date start=new Date();
        this.expiration=expirationDate(start,Long.valueOf(expIn).intValue());
    }

    private Date expirationDate(Date start, int toAdd){
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(start);
        calendar.add(Calendar.SECOND, toAdd);
        return calendar.getTime();
    }

    /**
     *
     * @return the access_token.
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Set the access_token.
     * @param accessToken the access_token.
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * @return the refresh_token.
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Set the refresh_token.
     * @param refreshToken the refresh_token.
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    /**
     * @return the access_token expiration date.
     */
    public Date getExpiration() {
        return expiration;
    }
}
