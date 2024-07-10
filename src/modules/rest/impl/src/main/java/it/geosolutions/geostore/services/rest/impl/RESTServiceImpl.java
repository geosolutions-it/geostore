/* ====================================================================
 *
 * Copyright (C) 2012 - 2016 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.impl;

import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.GroupReservedNames;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.core.model.enums.UserReservedNames;
import it.geosolutions.geostore.services.SecurityService;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.exception.InternalErrorWebEx;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.ws.rs.core.SecurityContext;
import org.apache.commons.collections.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Class RESTServiceImpl.
 *
 * <p>This is the superclass for each RESTServices implementation
 *
 * @author ETj (etj at geo-solutions.it)
 * @author DamianoG
 */
public abstract class RESTServiceImpl {

    private static final Logger LOGGER = LogManager.getLogger(RESTServiceImpl.class);

    @Autowired UserService userService;

    /**
     * Given a Group Set returns a List that contains all the group names
     *
     * @param groups
     * @return
     */
    public static List<String> extratcGroupNames(Set<UserGroup> groups) {
        List<String> groupNames = new ArrayList<>(groups.size() + 1);
        for (UserGroup ug : groups) {
            groupNames.add(ug.getGroupName());
        }
        return groupNames;
    }

    protected abstract SecurityService getSecurityService();

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    /**
     * @return User - The authenticated user that is accessing this service, or null if guest
     *     access.
     */
    protected User extractAuthUser(SecurityContext sc) throws InternalErrorWebEx {
        if (sc == null) throw new InternalErrorWebEx("Missing auth info");
        else {
            Principal principal = sc.getUserPrincipal();
            if (principal == null) {
                // If I'm here, I'm sure that the service is running is allowed for the
                // unauthenticated users
                // due to a service-based authorization step that uses annotations on services
                // declaration (see module geostore-rest-api).
                // So I'm going to create a Principal to be used during resources-based
                // authorization.
                principal = createGuestPrincipal();
            }
            if (!(principal instanceof Authentication)) {
                logMismatchedPrincipal();
                throw new InternalErrorWebEx(
                        "Mismatching auth principal (" + principal.getClass() + ")");
            }

            Authentication usrToken = (Authentication) principal;

            // DamianoG 06/03/2014 Why create a new Instance when we can deal with the object taken
            // from the DB? Being the instance taken from DB Transient we avoid problems saving
            // security rules...
            //            User user = new User();
            //            user.setName(usrToken.getName());
            //            for (GrantedAuthority authority : usrToken.getAuthorities()) {
            //                if (authority != null) {
            //                    if (authority.getAuthority() != null
            //                            && authority.getAuthority().contains("ADMIN"))
            //                        user.setRole(Role.ADMIN);
            //
            //                    if (authority.getAuthority() != null
            //                            && authority.getAuthority().contains("USER") &&
            // user.getRole() == null)
            //                        user.setRole(Role.USER);
            //
            //                    if (user.getRole() == null)
            //                        user.setRole(Role.GUEST);
            //                }
            //            }
            if (usrToken.getPrincipal() instanceof User) {
                User user = (User) usrToken.getPrincipal();

                LOGGER.info(
                        "Accessing service with user {} and role {}",
                        user.getName(),
                        user.getRole());

                return user;
            }
            logMismatchedPrincipal();
            throw new InternalErrorWebEx("Mismatching auth principal (not a GeoStore User)");
        }
    }

    private static void logMismatchedPrincipal() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Mismatching auth principal");
        }
    }

    /**
     * This operation is responsible for check if a resource is accessible to a user to perform
     * WRITE operations (update/delete). This operation must check first if the user has the right
     * permissions then, if not, check if its group is allowed.
     *
     * @param resourceId
     * @return boolean
     */
    public boolean resourceAccessWrite(User authUser, long resourceId) {
        if (authUser.getRole().equals(Role.ADMIN)) {
            return true;
        }
        //        else if(belongTo(authUser, GroupReservedNames.ALLRESOURCES.toString())){
        //            return true;
        //        }
        else {
            List<SecurityRule> userSecurityRules =
                    getSecurityService().getUserSecurityRule(authUser.getName(), resourceId);

            if (userSecurityRules != null && !userSecurityRules.isEmpty()) {
                for (SecurityRule sr : userSecurityRules) {
                    // The getUserSecurityRules returns all rules instead of user rules.
                    // So the username check is necessary until a problem with DAO is solved
                    if (sr.isCanWrite()
                            && sr.getUser() != null
                            && sr.getUser().getName().equals(authUser.getName())) {
                        return true;
                    }
                }
            }

            List<String> groupNames = extratcGroupNames(authUser.getGroups());
            if (!groupNames.isEmpty()) {
                List<SecurityRule> groupSecurityRules =
                        getSecurityService().getGroupSecurityRule(groupNames, resourceId);

                if (groupSecurityRules != null && !groupSecurityRules.isEmpty()) {
                    // Check if at least one user group has write permission
                    for (SecurityRule sr : groupSecurityRules) {
                        if (sr.isCanWrite()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * This operation is responsible for check if a resource is accessible to an user to perform
     * READ operations. This operation must check first if the user has the right permissions then,
     * if not, check if its group is allowed.
     *
     * @param resourceId
     * @return boolean
     */
    public boolean resourceAccessRead(User authUser, long resourceId) {
        if (authUser.getRole().equals(Role.ADMIN)) {
            return true;
        }
        //        else if(belongTo(authUser, GroupReservedNames.ALLRESOURCES.toString())){
        //            return true;
        //        }
        else {
            List<SecurityRule> userSecurityRules =
                    getSecurityService().getUserSecurityRule(authUser.getName(), resourceId);

            if (userSecurityRules != null && !userSecurityRules.isEmpty()) {
                // The getUserSecurityRules returns all rules instead of user rules.
                // So the username check is necessary until a problem with DAO is solved
                for (SecurityRule sr : userSecurityRules) {
                    if (sr.isCanRead()
                            && sr.getUser() != null
                            && sr.getUser().getName().equals(authUser.getName())) {
                        return true;
                    }
                }
            }

            List<String> groupNames = extratcGroupNames(authUser.getGroups());
            if (!groupNames.isEmpty()) {
                List<SecurityRule> groupSecurityRules =
                        getSecurityService().getGroupSecurityRule(groupNames, resourceId);

                if (groupSecurityRules != null && !groupSecurityRules.isEmpty()) {
                    // Check if at least one user group has read permission
                    for (SecurityRule sr : groupSecurityRules) {
                        if (sr.isCanRead()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public ResourceAuth getResourceAuth(User authUser, long resourceId) {
        if (authUser.getRole().equals(Role.ADMIN)) {
            return new ResourceAuth(true, true);
        }

        List<SecurityRule> userSecurityRules =
                getSecurityService().getUserSecurityRule(authUser.getName(), resourceId);

        ResourceAuth ret = new ResourceAuth();

        if (CollectionUtils.isNotEmpty(userSecurityRules)) {
            // take the more permissive grants
            for (SecurityRule rule : userSecurityRules) {
                ret.canRead |= rule.isCanRead();
                ret.canWrite |= rule.isCanWrite();

                if (ret.canRead && ret.canWrite) { // short circuit
                    return ret;
                }
            }
        }

        List<String> groupNames = extratcGroupNames(authUser.getGroups());
        if (!groupNames.isEmpty()) {
            List<SecurityRule> groupSecurityRules =
                    getSecurityService().getGroupSecurityRule(groupNames, resourceId);

            if (CollectionUtils.isNotEmpty(groupSecurityRules)) {
                // take the more permissive grants
                for (SecurityRule rule : groupSecurityRules) {
                    ret.canRead |= rule.isCanRead();
                    ret.canWrite |= rule.isCanWrite();

                    if (ret.canRead && ret.canWrite) { // short circuit
                        return ret;
                    }
                }
            }
        }

        return ret;
    }

    /**
     * Creates a Guest principal with Username="guest" password="" and role ROLE_GUEST. The guest
     * principal should be used with unauthenticated users.
     *
     * @return the Principal instance
     */
    public Principal createGuestPrincipal() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_GUEST"));
        try {
            User u = userService.get(UserReservedNames.GUEST.userName());
            return new UsernamePasswordAuthenticationToken(u, "", authorities);
        } catch (NotFoundServiceEx e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("User GUEST is not configured, creating on-the-fly a default one");
            }
        }
        User guest = new User();
        guest.setName("guest");
        guest.setRole(Role.GUEST);
        HashSet<UserGroup> groups = new HashSet<UserGroup>();
        UserGroup everyoneGroup = new UserGroup();
        everyoneGroup.setEnabled(true);
        everyoneGroup.setId(-1L);
        everyoneGroup.setGroupName(GroupReservedNames.EVERYONE.groupName());
        groups.add(everyoneGroup);
        guest.setGroups(groups);
        return new UsernamePasswordAuthenticationToken(guest, "", authorities);
    }

    protected static class ResourceAuth {

        boolean canRead;
        boolean canWrite;

        public ResourceAuth() {
            this(false, false);
        }

        public ResourceAuth(boolean canRead, boolean canWrite) {
            this.canRead = canRead;
            this.canWrite = canWrite;
        }
    }
}
