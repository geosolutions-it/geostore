package it.geosolutions.geostore.services.rest.impl;

import it.geosolutions.geostore.services.UserSessionService;
import it.geosolutions.geostore.services.dto.UserSession;
import it.geosolutions.geostore.services.rest.RESTSessionService;
import it.geosolutions.geostore.services.rest.SessionServiceDelegate;
import it.geosolutions.geostore.services.rest.exception.ForbiddenErrorWebEx;
import it.geosolutions.geostore.services.rest.model.SessionToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static it.geosolutions.geostore.services.rest.impl.RESTSessionServiceImpl.BEARER_TYPE;

public class SessionServiceDelegateImpl implements SessionServiceDelegate {

    @Autowired
    private UserSessionService userSessionService;

    public static final String DEFAULT_NAME="DEFAULT";

    public SessionServiceDelegateImpl(RESTSessionService restSessionService){
        restSessionService.registerDelegate(DEFAULT_NAME, this);
    }

    public SessionServiceDelegateImpl(){
    }


    @Override
    public SessionToken refresh(String refreshToken, String accessToken) {
        UserSession sessionToken=userSessionService.refreshSession(accessToken, refreshToken);
        if(sessionToken == null) {
            throw new ForbiddenErrorWebEx("Refresh token was not provided or session is already expired.");
        }
        SessionToken token = new SessionToken();
        token.setAccessToken(accessToken);
        token.setRefreshToken(sessionToken.getRefreshToken());
        token.setExpires(sessionToken.getExpirationInterval());
        token.setTokenType(BEARER_TYPE);
        return token;
    }

    @Override
    public void doLogout(String accessToken) {
            userSessionService.removeSession(accessToken);
    }

    /**
     * Set the user session service.
     * @param userSessionService the user session service.
     */
    public void setUserSessionService(UserSessionService userSessionService) {
        this.userSessionService = userSessionService;
    }
}
