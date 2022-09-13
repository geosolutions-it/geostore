/* ====================================================================
 *
 * Copyright (C) 2022 GeoSolutions S.A.S.
 * http://www.geo-solutions.it
 *
 * GPLv3 + Classpath exception
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 *
 * ====================================================================
 *
 * This software consists of voluntary contributions made by developers
 * of GeoSolutions.  For more information on GeoSolutions, please see
 * <http://www.geo-solutions.it/>.
 *
 */

package it.geosolutions.geostore.services.rest.security.keycloak;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.GroupReservedNames;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.core.security.password.SecurityUtils;
import it.geosolutions.geostore.services.UserGroupService;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.adapters.OidcKeycloakAccount;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.keycloak.adapters.springsecurity.account.KeycloakRole;
import org.keycloak.adapters.springsecurity.account.SimpleKeycloakAccount;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.keycloak.representations.AccessToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.REFRESH_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.getRequest;

/**
 * GeoStore custom Authentication  provider. It is used to map a Keycloak Authentication to a GeoStore Authentication where
 * the principal is of type {@link User}.
 */
public class GeoStoreKeycloakAuthProvider implements AuthenticationProvider {

    private final static Logger LOGGER = Logger.getLogger(GeoStoreKeycloakAuthProvider.class);


    @Autowired
    private UserService userService;

    @Autowired
    private UserGroupService groupService;

    private KeyCloakConfiguration configuration;

    public GeoStoreKeycloakAuthProvider(KeyCloakConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        KeycloakAuthenticationToken token = (KeycloakAuthenticationToken) authentication;
        OidcKeycloakAccount account = token.getAccount();
        KeycloakSecurityContext context = account.getKeycloakSecurityContext();
        AccessToken accessToken = context.getToken();
        String accessTokenStr = context.getTokenString();
        String refreshToken = null;
        Long expiration = null;
        HttpServletRequest request = getRequest();
        // set tokens as request attributes so that can made available in a cookie for the frontend on the callback url.
        if (accessToken != null) {
            expiration = accessToken.getExp();
            if (request != null) request.setAttribute(ACCESS_TOKEN_PARAM, accessToken);
        }
        if (context instanceof RefreshableKeycloakSecurityContext) {
            refreshToken = ((RefreshableKeycloakSecurityContext) context).getRefreshToken();
            if (request != null) request.setAttribute(REFRESH_TOKEN_PARAM, refreshToken);
        }


        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        GeoStoreKeycloakAuthoritiesMapper grantedAuthoritiesMapper = new GeoStoreKeycloakAuthoritiesMapper(configuration.getRoleMappings(), configuration.getGroupMappings(), configuration.isDropUnmapped());
        for (String role : token.getAccount().getRoles()) {
            grantedAuthorities.add(new KeycloakRole(role));
        }

        // maps authorities to GeoStore Role and UserGroup
        Collection<? extends GrantedAuthority> mapped = mapAuthorities(grantedAuthoritiesMapper, grantedAuthorities);

        KeycloakTokenDetails details = new KeycloakTokenDetails(accessTokenStr, refreshToken, expiration);
        details.setIdToken(context.getIdTokenString());
        String username = getUsername(authentication);
        Set<UserGroup> keycloakGroups = grantedAuthoritiesMapper != null ? grantedAuthoritiesMapper.getGroups() : new HashSet<>();

        // if the auto creation of user is set to true from keycloak properties we add the groups as well.
        if (configuration.isAutoCreateUser())
            keycloakGroups = importGroups(keycloakGroups, grantedAuthorities);

        User user = retrieveUser(username, "", grantedAuthoritiesMapper,keycloakGroups);
        addEveryOne(user.getGroups());
        if (user.getRole() == null) {
            // no role get the one configured to be default for authenticated users.
            Role defRole = configuration.getAuthenticatedDefaultRole();
            user.setRole(defRole);
        }
        if (user.getGroups() == null) user.setGroups(new HashSet<>());
        PreAuthenticatedAuthenticationToken result = new PreAuthenticatedAuthenticationToken(user, "", mapped);
        result.setDetails(details);
        return result;
    }

    private Collection<? extends GrantedAuthority> mapAuthorities(GeoStoreKeycloakAuthoritiesMapper grantedAuthoritiesMapper,
                                                                  Collection<? extends GrantedAuthority> authorities) {
        return grantedAuthoritiesMapper != null
                ? grantedAuthoritiesMapper.mapAuthorities(authorities)
                : authorities;
    }

    @Override
    public boolean supports(Class<?> aClass) {
        return KeycloakAuthenticationToken.class.isAssignableFrom(aClass);
    }

    /**
     * Retrieve the user from db or create a new instance. If {@link KeyCloakConfiguration#isAutoCreateUser()} returns
     * true, will insert the user in the db.
     *
     * @param userName
     * @param credentials
     * @return
     */
    protected User retrieveUser(String userName, String credentials, GeoStoreKeycloakAuthoritiesMapper mapper,Set<UserGroup>groups) {
        User user = null;
        if (userService != null) {
            try {
                user = userService.get(userName);
            } catch (NotFoundServiceEx e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.warn("Keycloak user not found in DB.", e);
                }
            }
        }
        if (user == null) {
            user = new User();
            user.setName(userName);
            user.setNewPassword(credentials);
            user.setEnabled(true);
            Role role = mappedRole(mapper);
            user.setRole(role);
            if (groups==null) groups=new HashSet<>();
            user.setGroups(groups);
            // user not found in db, if configured to insert will insert it.
            if (userService != null && configuration.isAutoCreateUser()) {
                try {
                    long id = userService.insert(user);
                    user = userService.get(id);
                } catch (NotFoundServiceEx | BadRequestServiceEx e) {
                    LOGGER.error("Exception while inserting the user.", e);
                }
            } else {
                user.setTrusted(true);
            }
        } else {
            Role role = mappedRole(mapper);
            // might need to update the role / groups if on keycloak side roles changed.
            if (isUpdateUser(user,groups,role)) {
               updateRoleAndGroups(role,groups,user);
            }
        }
        return user;
    }

    // update user groups adding the one not already present and added on keycloak side
    private User updateRoleAndGroups(Role role,Set<UserGroup> groups, User user){
            user.setRole(role);
            try {
                for (UserGroup g:user.getGroups()){
                    if (!groups.stream().anyMatch(group->group.getGroupName().equals(g.getGroupName()))) {
                        UserGroup newGroup = new UserGroup();
                        newGroup.setGroupName(g.getGroupName());
                        newGroup.setId(g.getId());
                        groups.add(g);
                    }
                }
                user.setGroups(groups);
                userService.update(new User(user));
                user = userService.get(user.getName());
            } catch (NotFoundServiceEx | BadRequestServiceEx ex) {
                LOGGER.error("Error while updating user role...", ex);
            }
        return user;
    }

    // we only update if new roles were added on keycloak or the role changed
    private boolean isUpdateUser(User user, Set<UserGroup> groups, Role mappedRole){
        Set<UserGroup> incoming=new HashSet<>(groups);
        incoming.removeAll(user.getGroups());
        if (!incoming.stream().allMatch(g->g.getGroupName().equals(GroupReservedNames.EVERYONE.groupName())))
            return true;

        if (configuration.isAutoCreateUser() && (user.getRole() == null || !user.getRole().equals(mappedRole)))
            return true;

        return false;

    }

    private Role mappedRole(GeoStoreKeycloakAuthoritiesMapper mapper) {
        Role role = null;
        if (mapper != null && mapper.getRole() != null)
            role = mapper.getRole();
        if (role == null) role = configuration.getAuthenticatedDefaultRole();
        if (role == null) role = Role.USER;
        return role;
    }

    private String getUsername(Authentication authentication) {
        String username = null;
        if (authentication != null && authentication.getDetails() instanceof SimpleKeycloakAccount) {
            SimpleKeycloakAccount account = (SimpleKeycloakAccount) authentication.getDetails();
            AccessToken token = account.getKeycloakSecurityContext().getToken();
            if (token != null) username = token.getPreferredUsername();
        }
        if (username == null) username = SecurityUtils.getUsername(authentication);
        return username;
    }

    private Set<UserGroup> importGroups(Set<UserGroup> mappedGroups, Collection<GrantedAuthority> authorities) {
        Set<UserGroup> returnSet = new HashSet<>(mappedGroups.size());
        try {
            if (mappedGroups == null || mappedGroups.isEmpty()) {
                for (GrantedAuthority auth : authorities) {
                    UserGroup res = importGroup(auth);
                    returnSet.add(res);
                }
            } else {
                for (UserGroup g : mappedGroups) {
                    UserGroup res = importGroup(g.getGroupName());
                    returnSet.add(res);
                }
            }
        } catch (BadRequestServiceEx e) {
            LOGGER.error("Error while synchronizing groups.... Error is: ", e);
        }
        return returnSet;
    }

    private UserGroup importGroup(GrantedAuthority a)
            throws BadRequestServiceEx {
        return importGroup(a.getAuthority());
    }

    private UserGroup importGroup(String groupName) throws BadRequestServiceEx {
        UserGroup group;
        if (groupService != null) {
            group = groupService.get(groupName);

            if (group == null) {
                LOGGER.log(Level.INFO, "Creating new group from Keycloak: " + groupName);
                group = new UserGroup();
                group.setGroupName(groupName);
                long id = groupService.insert(group);
                group = groupService.get(id);
            }
        } else {
            group = new UserGroup();
            group.setGroupName(groupName);
        }
        return group;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public void setGroupService(UserGroupService groupService) {
        this.groupService = groupService;
    }

    private void addEveryOne(Set<UserGroup> groups){
        String everyone=GroupReservedNames.EVERYONE.groupName();
        if (!groups.stream().anyMatch(g->g.getGroupName().equals(everyone))) {
            UserGroup everyoneGroup = new UserGroup();
            everyoneGroup.setEnabled(true);
            everyoneGroup.setId(-1L);
            everyoneGroup.setGroupName(GroupReservedNames.EVERYONE.groupName());
            groups.add(everyoneGroup);
        }
    }
}
