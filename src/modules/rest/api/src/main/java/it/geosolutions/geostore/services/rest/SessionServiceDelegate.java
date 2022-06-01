package it.geosolutions.geostore.services.rest;

import it.geosolutions.geostore.services.rest.model.SessionToken;

/**
 * Base interface for a SessionServiceDelegate. Instances of this type are meant to customize refresh token
 * and logout operations.
 */
public interface SessionServiceDelegate {

    public String PROVIDER_KEY="PROVIDER";

    /**
     * Refresh a token,
     * @param refreshToken the refresh token.
     * @param accessToken the current access token.
     * @return a Session Token instance holding the new token and the refresh token.
     */
    SessionToken refresh(String refreshToken, String accessToken);

    /**
     * Do the logout.
     * @param accessToken the current session token.
     */
    void doLogout(String accessToken);
}
