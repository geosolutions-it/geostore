package it.geosolutions.geostore.services.security;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.rest.AdministratorGeoStoreClient;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.sun.jersey.api.client.ClientHandlerException;
/**
 * Wrap geostore Rest Services to allow Authentication using Geostore Users
 * @author Lorenzo Natali
 *
 */
public class GeoStoreAuthenticationProvider implements AuthenticationProvider {

	/**
	 * The rest service 
	 */
	String geoStoreRestURL;
	
	/**
	 * a list of allowed Roles
	 */
	List<String> allowedRoles;
	
	
        
	
	/**
	 * Message shown if the user logged haven't got an allowed role.
	 * TODO: Localize it
	 */
        public static final String UNAUTHORIZED_MSG = "This user have not enougth permissions to access to the Admin GUI";
        
        /**
         * Message shown if the user it's not found.
         * TODO: Localize it
         */
        public static final String USER_NOT_FOUND_MSG = "User not found. Please check your credentials";
        
        /**
         * Message shown if GeoStore it's unavailable.
         * TODO: Localize it
         */
        public static final String GEOSTORE_UNAVAILABLE = "GeoStore it's not availabile. Please contact with the administrator";

	@Override
	public boolean supports(Class<? extends Object> authentication) {
		return authentication.equals(UsernamePasswordAuthenticationToken.class);
	}

	@Override
	public Authentication authenticate(Authentication authentication) {
		String pw = (String) authentication.getCredentials();
		String us = (String) authentication.getPrincipal();
		// We use the credentials for all the session in the GeoStore client

		AdministratorGeoStoreClient geoStoreClient =new AdministratorGeoStoreClient();
		geoStoreClient.setUsername(us);
		geoStoreClient.setPassword(pw);
		geoStoreClient.setGeostoreRestUrl(geoStoreRestURL);

		User user = null;
		try {
			user = geoStoreClient.getUserDetails();
		} catch (ClientHandlerException che) {
		    throw new UsernameNotFoundException(GEOSTORE_UNAVAILABLE);
                } catch (Exception e){
                    // user not found generic response.
                    user = null;
                }
		
		if (user != null) {
			String role = user.getRole().toString();
			if (!roleAllowed(role)){
			    throw new BadCredentialsException(UNAUTHORIZED_MSG);
			}
//				return null;
			List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
			authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
			Authentication a = new UsernamePasswordAuthenticationToken(us, pw,
					authorities);
			// a.setAuthenticated(true);
			return a;
		} else {
                    throw new UsernameNotFoundException(USER_NOT_FOUND_MSG);
		}

	}

	private boolean roleAllowed(String role) {
		for (String allowed : allowedRoles) {
			if (allowed != null) {
				if (allowed.equals(role))
					return true;
			}
		}
		return false;
	}

	// GETTERS AND SETTERS
	public List<String> getAllowedRoles() {
		return allowedRoles;
	}

	public void setAllowedRoles(List<String> roleFilter) {
		this.allowedRoles = roleFilter;
	}

	public String getGeoStoreRestURL() {
		return geoStoreRestURL;
	}

	public void setGeoStoreRestURL(String geoStoreRestURL) {
		this.geoStoreRestURL = geoStoreRestURL;
	}

}