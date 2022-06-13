package it.geosolutions.geostore.services.rest.security.oauth2;

import it.geosolutions.geostore.services.rest.IdPLoginRest;

public class GoogleLoginService extends Oauth2LoginService {

    public GoogleLoginService(IdPLoginRest loginRest){
        loginRest.registerService("google",this);
    }
}
