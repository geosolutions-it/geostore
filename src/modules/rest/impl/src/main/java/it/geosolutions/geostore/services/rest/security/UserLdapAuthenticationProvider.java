/**
 * 
 */
package it.geosolutions.geostore.services.rest.security;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.GroupReservedNames;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.core.security.UserMapper;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.authentication.LdapAuthenticator;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapUserDetails;

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

    private UserMapper userMapper;
    
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

    
    
    public void setUserService(UserService userService) {
        this.userService = userService;
    }



    public void setUserGroupService(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }



    public void setUserMapper(UserMapper userMapper) {
        this.userMapper = userMapper;
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
        LdapUserDetails ldapUser = null;
        if (authentication.isAuthenticated()) {

            Collection<? extends GrantedAuthority> authorities = null;

            ldapUser = (LdapUserDetails) authentication.getPrincipal();

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
                LOGGER.info("US: " + us);// + " PW: " + PwEncoder.encode(pw) + " -- " + user.getPassword());
                
                if (!user.isEnabled()) {
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
                    user.setGroups(GroupReservedNames.checkReservedGroups(groups));

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
                    user.setNewPassword(null);
                    user.setEnabled(true);

                    Set<UserGroup> groups = new HashSet<UserGroup>();
                    Role role = extractUserRoleAndGroups(null, authorities, groups);
                    user.setRole(role);
                    user.setGroups(GroupReservedNames.checkReservedGroups(groups));
                    if(userMapper != null) {
                        userMapper.mapUser(ldapUser, user);
                    }
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
    protected Authentication prepareAuthentication(String pw, User user, Role role) {
        List<GrantedAuthority> grantedAuthorities = new ArrayList<GrantedAuthority>();
        grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + role));
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
    protected Role extractUserRoleAndGroups(Role userRole,
            Collection<? extends GrantedAuthority> authorities, Set<UserGroup> groups)
            throws BadRequestServiceEx {
        Role role = (userRole != null ? userRole : Role.USER);
        for (GrantedAuthority a : authorities) {
            if (a.getAuthority().startsWith("ROLE_")) {
                if (a.getAuthority().toUpperCase().endsWith("ADMIN")
                        && (role == Role.GUEST || role == Role.USER)) {
                    role = Role.ADMIN;
                } else if (a.getAuthority().toUpperCase().endsWith("USER") && role == Role.GUEST) {
                    role = Role.USER;
                }
            } else {
                groups.add(synchronizeGroup(a));
            }
        }
        return role;
    }

    public void synchronizeGroups() throws BadRequestServiceEx {
        if(getAuthoritiesPopulator() instanceof GroupsRolesService) {
            GroupsRolesService groupsService = (GroupsRolesService) getAuthoritiesPopulator();
            for(GrantedAuthority authority : groupsService.getAllGroups()) {
                synchronizeGroup(authority);
            }
        }
    }

    private UserGroup synchronizeGroup(GrantedAuthority a)
            throws BadRequestServiceEx {
        UserGroup group = new UserGroup();
        group.setGroupName(a.getAuthority());

        if (userGroupService != null) {
            UserGroup userGroup = userGroupService.get(group.getGroupName());

            if (userGroup == null) {
                LOGGER.log(Level.INFO, "Creating new group from LDAP: " + group.getGroupName());
                long groupId = userGroupService.insert(group);
                userGroup = userGroupService.get(groupId);
            }

            return userGroup;
        } else {
            return group;
        }
    }
}
