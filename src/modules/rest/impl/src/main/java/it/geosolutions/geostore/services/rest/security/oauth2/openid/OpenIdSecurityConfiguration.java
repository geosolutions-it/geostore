package it.geosolutions.geostore.services.rest.security.oauth2.openid;

import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2GeoStoreSecurityConfiguration;

public class OpenIdSecurityConfiguration extends OAuth2GeoStoreSecurityConfiguration {
    @Override
    public OAuth2Configuration configuration() {
        return null;
    }
}
