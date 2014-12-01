/**
 * 
 */
package it.geosolutions.geostore.services.rest.security;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.GroupReservedNames;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.core.security.password.PwEncoder;
import it.geosolutions.geostore.services.UserGroupService;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.GrantedAuthorityImpl;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.authentication.LdapAuthenticator;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapUserDetailsImpl;

/**
 * @author alessio.fabiani
 *
 */
public class UserLdapAuthenticationProvider extends LdapAuthenticationProvider {

private final static Logger LOGGER = Logger.getLogger(UserLdapAuthenticationProvider.class);
    
    @Autowired
    UserService userService;
    
    @Autowired
    UserGroupService userGroupService;
    
    /**
     * Message shown if the user credentials are wrong. TODO: Localize it
     */
    private static final String UNAUTHORIZED_MSG = "Bad credentials";
    
    /**
     * Message shown if the user it's not found. TODO: Localize it
     */
    public static final String USER_NOT_FOUND_MSG = "User not found. Please check your credentials";
    public static final String USER_NOT_ENABLED = "The user present but not enabled";
    
	public UserLdapAuthenticationProvider(LdapAuthenticator authenticator,
			LdapAuthoritiesPopulator authoritiesPopulator) {
		super(authenticator, authoritiesPopulator);
	}

	@Override
	public Authentication authenticate(Authentication authentication)
			throws AuthenticationException {
		try {
			authentication = super.authenticate(authentication);
		} catch (Exception e) {
			LOGGER.log(Level.ERROR, e.getMessage(), e);
			throw new BadCredentialsException(UNAUTHORIZED_MSG);
		}
		
		if (authentication.isAuthenticated()) {
			
			Collection<GrantedAuthority> authorities = null;
			
			LdapUserDetailsImpl ldapUser = (LdapUserDetailsImpl) authentication.getPrincipal();
			
			if (!(ldapUser.isAccountNonExpired() && ldapUser.isAccountNonLocked() 
					&& ldapUser.isCredentialsNonExpired() && ldapUser.isEnabled())) {
				throw new DisabledException(USER_NOT_FOUND_MSG);
			}
			
			authorities = ldapUser.getAuthorities();
			
			String pw = (String) authentication.getCredentials();
			String us = ldapUser.getUsername();

	        // We use the credentials for all the session in the GeoStore client
	        User user = null;
	        try {
	            user = userService.get(us);
	            LOGGER.info("US: " + us );//+ " PW: " + PwEncoder.encode(pw) + " -- " + user.getPassword());
	            if (user.getPassword() == null || !PwEncoder.isPasswordValid(user.getPassword(),pw)) {
	                if (user.getPassword() == null) throw new BadCredentialsException(UNAUTHORIZED_MSG);
	            	user.setNewPassword(pw);
	            }
	            if(!user.isEnabled()){
	            	throw new DisabledException(USER_NOT_FOUND_MSG);
	            }
	        } catch (Exception e) {
	            LOGGER.info(USER_NOT_FOUND_MSG);
	            user = null;
	        }

	        if (user != null) {
	            // check that ROLE and GROUPS match with the LDAP ones
				try {
		            Set<UserGroup> groups = new HashSet<UserGroup>();
	        		Role role = extractUserRoleAndGroups(user.getRole(), authorities, groups);
					user.setRole(role);
					user.setGroups(checkReservedGroups(groups));
					
					if (userService != null)
						userService.update(user);

					Authentication a = prepareAuthentication(pw, user, role);
					return a;
				} catch (BadRequestServiceEx e) {
					LOGGER.log(Level.ERROR, e.getMessage(), e);
					throw new UsernameNotFoundException(USER_NOT_FOUND_MSG);
				} catch (NotFoundServiceEx e) {
					LOGGER.log(Level.ERROR, e.getMessage(), e);
					throw new UsernameNotFoundException(USER_NOT_FOUND_MSG);
				}
	        } else {
	        	// check that ROLE and GROUPS match with the LDAP ones
	        	try {
	        		user = new User();
	        		
	        		user.setName(us);
	        		user.setNewPassword(pw);
	        		user.setEnabled(true);
	        		
	        		Set<UserGroup> groups = new HashSet<UserGroup>(); 	        		
	        		Role role = extractUserRoleAndGroups(null, authorities, groups);
					user.setRole(role);
					user.setGroups(checkReservedGroups(groups));

					if (userService != null)
						userService.insert(user);
					
		            Authentication a = prepareAuthentication(pw, user, role);
		            return a;
				} catch (BadRequestServiceEx e) {
					LOGGER.log(Level.ERROR, e.getMessage(), e);
					throw new UsernameNotFoundException(USER_NOT_FOUND_MSG);
				} catch (NotFoundServiceEx e) {
					LOGGER.log(Level.ERROR, e.getMessage(), e);
					throw new UsernameNotFoundException(USER_NOT_FOUND_MSG);
				}
	        }
		} else {
			throw new BadCredentialsException(UNAUTHORIZED_MSG);
		}
	}

	/**
	 * @param pw
	 * @param user
	 * @param role
	 * @return
	 */
	protected Authentication prepareAuthentication(String pw, User user,
			Role role) {
		List<GrantedAuthority> grantedAuthorities = new ArrayList<GrantedAuthority>();
		grantedAuthorities.add(new GrantedAuthorityImpl("ROLE_" + role));
		Authentication a = new UsernamePasswordAuthenticationToken(user, pw, grantedAuthorities);
		// a.setAuthenticated(true);
		return a;
	}

	/**
	 * @param role2 
	 * @param authorities
	 * @param groups
	 * @return
	 * @throws BadRequestServiceEx
	 */
	protected Role extractUserRoleAndGroups(
			Role userRole, Collection<GrantedAuthority> authorities, Set<UserGroup> groups)
			throws BadRequestServiceEx {
		Role role = (userRole != null ? userRole : Role.GUEST);
		for ( GrantedAuthority a : authorities ) {
			if (a.getAuthority().startsWith("ROLE_")) {
				if (a.getAuthority().toUpperCase().endsWith("ADMIN") && 
						(role == Role.GUEST || role == Role.USER)) {
					role = Role.ADMIN;
				} else if (a.getAuthority().toUpperCase().endsWith("USER") && role == Role.GUEST) {
					role = Role.USER;	
				}
			} else {
				UserGroup group = new UserGroup();
				group.setGroupName(a.getAuthority());

				if (userGroupService != null) {
					UserGroup userGroup = userGroupService.get(group
							.getGroupName());

					if (userGroup == null) {
						long groupId = userGroupService.insert(group);
						userGroup = userGroupService.get(groupId);
					}

					groups.add(userGroup);
				} else {
					groups.add(group);
				}
			}
		}
		return role;
	}

	 /**
     * Utility method to remove Reserved group (for example EVERYONE) from a group list
     * 
     * @param groups
     * @return
     */
    private Set<UserGroup> checkReservedGroups(Set<UserGroup> groups){
        List<UserGroup> reserved = new ArrayList<UserGroup>();
        for(UserGroup ug : groups){
            if(!GroupReservedNames.isAllowedName(ug.getGroupName())){
                reserved.add(ug);
            }
        }
        for(UserGroup ug : reserved){
			groups.remove(ug);
        }
        return groups;
    }
}
