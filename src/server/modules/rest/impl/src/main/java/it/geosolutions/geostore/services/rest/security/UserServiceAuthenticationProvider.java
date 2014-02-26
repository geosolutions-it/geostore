package it.geosolutions.geostore.services.rest.security;

import it.geosolutions.geostore.core.dao.util.PwEncoder;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.UserService;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.GrantedAuthorityImpl;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Wrap geostore Rest Services to allow Authentication using Geostore Users
 * 
 * @author Lorenzo Natali
 * 
 */
public class UserServiceAuthenticationProvider implements AuthenticationProvider {

    /**
     * GeoStoreClient in the applicationContext
     */
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
            System.out.println("US: " + us + " PW: " + PwEncoder.encode(pw) + " -- "
                    + user.getPassword());
            if (!user.getPassword().equals(PwEncoder.encode(pw))) {
                throw new BadCredentialsException(UNAUTHORIZED_MSG);
            }
        } catch (Exception e) {
            // user not found generic response.
            user = null;
        }

        if (user != null) {
            String role = user.getRole().toString();
            // return null;
            List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
            authorities.add(new GrantedAuthorityImpl("ROLE_" + role));
            Authentication a = new UsernamePasswordAuthenticationToken(us, pw, authorities);
            // a.setAuthenticated(true);
            return a;
        } else {
            throw new UsernameNotFoundException(USER_NOT_FOUND_MSG);
        }

    }

    // GETTERS AND SETTERS

}
