package it.geosolutions.geostore.services.rest.security;

import org.apache.log4j.Logger;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

public class PreAuthenticatedAuthenticationProvider implements AuthenticationProvider {
    private final static Logger LOGGER = Logger.getLogger(PreAuthenticatedAuthenticationProvider.class);

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        PreAuthenticatedAuthenticationToken token =(PreAuthenticatedAuthenticationToken) authentication;
        LOGGER.debug("Pre Authentication for " + authentication.getName());
        return token;
    }

    @Override
    public boolean supports(Class<? extends Object> authentication) {
        return authentication.equals(PreAuthenticatedAuthenticationToken.class);
    }
}
