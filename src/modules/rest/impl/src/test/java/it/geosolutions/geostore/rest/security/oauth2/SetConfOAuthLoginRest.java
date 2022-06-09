package it.geosolutions.geostore.rest.security.oauth2;

import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2LoginRestImpl;

/**
 * Test class for the LoginRest endpoint. Allows the setting of an OAuth2Configuration object.
 */
public class SetConfOAuthLoginRest extends OAuth2LoginRestImpl {

    private OAuth2Configuration configuration;

    @Override
    protected OAuth2Configuration configuration(String provider) {
        return configuration;
    }

    public void setConfiguration(OAuth2Configuration configuration) {
        this.configuration = configuration;
    }
}
