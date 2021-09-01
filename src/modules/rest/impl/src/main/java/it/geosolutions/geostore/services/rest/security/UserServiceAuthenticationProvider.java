package it.geosolutions.geostore.services.rest.security;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.security.password.PwEncoder;
import it.geosolutions.geostore.services.UserService;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Wrap geostore Rest Services to allow Authentication using Geostore Users
 * 
 * @author Lorenzo Natali
 * 
 */
public class UserServiceAuthenticationProvider implements AuthenticationProvider {

    private final static Logger LOGGER = Logger.getLogger(UserServiceAuthenticationProvider.class);
    
    @Autowired
    UserService userService;

    /**
     * Message shown if the user credentials are wrong. TODO: Localize it
     */
    private static final String UNAUTHORIZED_MSG = "Bad credentials";

    /**
     * Message shown if the user it's not found. TODO: Localize it
     */
    public static final String USER_NOT_FOUND_MSG = "User not found. Please check your credentials";
    public static final String USER_NOT_ENABLED = "The user present but not enabled";

    @Override
    public boolean supports(Class<? extends Object> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String pw = (String) authentication.getCredentials();
        String us = (String) authentication.getPrincipal();

        // We use the credentials for all the session in the GeoStore client
        User user = null;
        try {
            user = userService.get(us);
            LOGGER.info("US: " + us );//+ " PW: " + PwEncoder.encode(pw) + " -- " + user.getPassword());
            if (user.getPassword() == null || !PwEncoder.isPasswordValid(user.getPassword(),pw)) {
                throw new BadCredentialsException(UNAUTHORIZED_MSG);
            }
            if(!user.isEnabled()){
            	throw new DisabledException(USER_NOT_FOUND_MSG);
            }
        } catch (Exception e) {
            LOGGER.info(USER_NOT_FOUND_MSG);
            user = null;
        }

        if (user != null) {
            String role = user.getRole().toString();
            // return null;
            List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            Authentication a = new UsernamePasswordAuthenticationToken(user, pw, authorities);
            // a.setAuthenticated(true);
            return a;
        } else {
            throw new UsernameNotFoundException(USER_NOT_FOUND_MSG);
        }

    }

    // GETTERS AND SETTERS

}
