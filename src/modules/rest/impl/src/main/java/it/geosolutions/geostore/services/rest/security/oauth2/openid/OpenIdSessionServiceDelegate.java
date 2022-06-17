package it.geosolutions.geostore.services.rest.security.oauth2.openid;

import it.geosolutions.geostore.services.rest.RESTSessionService;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2SessionServiceDelegate;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;


public class OpenIdSessionServiceDelegate extends OAuth2SessionServiceDelegate {


    /**
     * @param restSessionService the session service to which register this delegate.
     * @param delegateName       this delegate name eg. google or github etc...
     */
    public OpenIdSessionServiceDelegate(RESTSessionService restSessionService, String delegateName) {
        super(restSessionService, delegateName);
    }

    @Override
    protected OAuth2Configuration configuration() {
        return null;
    }

    @Override
    protected OAuth2RestTemplate restTemplate() {
        return null;
    }
}
