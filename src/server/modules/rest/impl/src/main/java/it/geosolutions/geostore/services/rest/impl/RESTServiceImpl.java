/* ====================================================================
 *
 * Copyright (C) 2012 GeoSolutions S.A.S.
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

import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.GroupReservedNames;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.core.model.enums.UserReservedNames;
import it.geosolutions.geostore.services.SecurityService;
import it.geosolutions.geostore.services.UserGroupService;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.exception.InternalErrorWebEx;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.SecurityContext;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.cas.authentication.CasAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.GrantedAuthorityImpl;

/**
 * Class RESTServiceImpl.
 * 
 * This is the super class for each RESTServices implementation 
 * 
 * @author ETj (etj at geo-solutions.it)
 * @author DamianoG
 */
public abstract class RESTServiceImpl{

    private final static Logger LOGGER = Logger.getLogger(RESTServiceImpl.class);

    @Autowired
    UserService userService;
    
    @Autowired
    UserGroupService userGroupService;
    
    protected abstract SecurityService getSecurityService();
    
    /**
     * @return User - The authenticated user that is accessing this service, or null if guest access.
     */
    protected User extractAuthUser(SecurityContext sc) throws InternalErrorWebEx {
        if (sc == null)
            throw new InternalErrorWebEx("Missing auth info");
        else {
            Principal principal = sc.getUserPrincipal();
            if (principal == null) {
                // If I'm here I'm sure that the service is running is allowed for the unauthenticated users
                // due to service-based authorization step that uses annotations on services declaration (seee module geostore-rest-api). 
                // So I'm going to create a Principal to be used during resources-based authorization.
                principal = createGuestPrincipal();
            }
            if (!(principal instanceof UsernamePasswordAuthenticationToken) && !(principal instanceof CasAuthenticationToken)) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Mismatching auth principal");
                }
                throw new InternalErrorWebEx("Mismatching auth principal (" + principal.getClass()
                        + ")");
            }

            User user = null;
            
            if (principal instanceof UsernamePasswordAuthenticationToken) {
                UsernamePasswordAuthenticationToken usrToken = (UsernamePasswordAuthenticationToken) principal;

                //DamianoG 06/03/2014 Why create a new Instance when we can deal with the object taken from the DB? Being the instance taken from DB Transient we avoid problems saving security rules...
                //            User user = new User();
                //            user.setName(usrToken.getName());
                //            for (GrantedAuthority authority : usrToken.getAuthorities()) {
                //                if (authority != null) {
                //                    if (authority.getAuthority() != null
                //                            && authority.getAuthority().contains("ADMIN"))
                //                        user.setRole(Role.ADMIN);
                //
                //                    if (authority.getAuthority() != null
                //                            && authority.getAuthority().contains("USER") && user.getRole() == null)
                //                        user.setRole(Role.USER);
                //
                //                    if (user.getRole() == null)
                //                        user.setRole(Role.GUEST);
                //                }
                //            }
                user = (User)usrToken.getPrincipal();
            } else if (principal instanceof CasAuthenticationToken) {
                CasAuthenticationToken usrToken = (CasAuthenticationToken) principal;
                user = new User();
                user.setEnabled(true);
                user.setName(usrToken.getName());
                
                Set<UserGroup> groups = new HashSet<UserGroup>();
                Role role = extractUserRoleAndGroups(null, usrToken.getAuthorities(), groups);
                user.setRole(role);
                user.setGroups(checkReservedGroups(groups));
            }
            
            LOGGER.info("Accessing service with user " + user.getName() + " and role "
                    + user.getRole());

            return user;
        }
    }
    
    /**
     * @param role2
     * @param authorities
     * @param groups
     * @return
     * @throws BadRequestServiceEx
     */
    private Role extractUserRoleAndGroups(Role userRole,
            Collection<GrantedAuthority> authorities, Set<UserGroup> groups) {
        Role role = (userRole != null ? userRole : Role.GUEST);
        for (GrantedAuthority a : authorities) {
            if (a.getAuthority().startsWith("ROLE_")) {
                if (a.getAuthority().toUpperCase().endsWith("ADMIN")
                        && (role == Role.GUEST || role == Role.USER)) {
                    role = Role.ADMIN;
                } else if (a.getAuthority().toUpperCase().endsWith("USER") && role == Role.GUEST) {
                    role = Role.USER;
                }
            } else {
                UserGroup group = new UserGroup();
                group.setGroupName(a.getAuthority());

                if (userGroupService != null) {
                    UserGroup userGroup = userGroupService.get(group.getGroupName());

                    if (userGroup == null) {
                        long groupId;
                        try {
                            groupId = userGroupService.insert(group);
                            userGroup = userGroupService.get(groupId);
                            groups.add(userGroup);
                        } catch (BadRequestServiceEx e) {
                            LOGGER.log(Level.ERROR, e.getMessage(), e);
                        }
                    }
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
    private Set<UserGroup> checkReservedGroups(Set<UserGroup> groups) {
        List<UserGroup> reserved = new ArrayList<UserGroup>();
        for (UserGroup ug : groups) {
            if (!GroupReservedNames.isAllowedName(ug.getGroupName())) {
                reserved.add(ug);
            }
        }
        for (UserGroup ug : reserved) {
            groups.remove(ug);
        }
        return groups;
    }
    
    /**
     * This operation is responsible for check if a resource is accessible to an user to perform WRITE operations (update/delete). 
     * this operation must checks first if the user has the right permissions then, if not, check if its group is allowed.
     * 
     * @param resource
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
            List<SecurityRule> userSecurityRules = getSecurityService().getUserSecurityRule(
                    authUser.getName(), resourceId);

            if (userSecurityRules != null && userSecurityRules.size() > 0){
                SecurityRule sr = userSecurityRules.get(0);
                if (sr.isCanWrite()){
                    return true;
                }
            }
            
            List<String> groupNames = extratcGroupNames(authUser.getGroups());
            if(groupNames != null && groupNames.size() > 0){
                List<SecurityRule> groupSecurityRules = getSecurityService().getGroupSecurityRule(
                        groupNames, resourceId);
    
                if (groupSecurityRules != null && groupSecurityRules.size() > 0){
                    // Check if at least one user group has write permission
                    for(SecurityRule sr : groupSecurityRules){
                        if (sr.isCanWrite()){
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * This operation is responsible for check if a resource is accessible to an user to perform READ operations. 
     * this operation must checks first if the user has the right permissions then, if not, check if its group is allowed. 
     * 
     * @param resource
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
            List<SecurityRule> userSecurityRules = getSecurityService().getUserSecurityRule(
                    authUser.getName(), resourceId);

            if (userSecurityRules != null && userSecurityRules.size() > 0){
                SecurityRule sr = userSecurityRules.get(0);
                if (sr.isCanRead()){
                    return true;
                }
            }
            
            List<String> groupNames = extratcGroupNames(authUser.getGroups());
            if(groupNames != null && groupNames.size() > 0){
                List<SecurityRule> groupSecurityRules = getSecurityService().getGroupSecurityRule(
                        groupNames, resourceId);
    
                if (groupSecurityRules != null && groupSecurityRules.size() > 0){
                    // Check if at least one user group has read permission
                    for(SecurityRule sr : groupSecurityRules){
                        if (sr.isCanRead()){
                            return true;
                        }
                    }
                }
            }
        }
        return false;    
    }
    
    /**
     * Creates a Guest principal with Username="guest" password="" and role ROLE_GUEST.
     * The guest principal should be used with unauthenticated users.
     * 
     * @return the Principal instance
     */
    public Principal createGuestPrincipal(){
        List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
        authorities.add(new GrantedAuthorityImpl("ROLE_GUEST"));
        try {
            User u = userService.get(UserReservedNames.GUEST.userName());
            return new UsernamePasswordAuthenticationToken(u,"", authorities);
        } catch (NotFoundServiceEx e) {
            if(LOGGER.isDebugEnabled()){
                LOGGER.debug("User GUEST is not configured, creating on-the-fly a default one");
            }
        }
        User guest = new User();
        guest.setName("guest");
        guest.setRole(Role.GUEST);
        guest.setGroups(new HashSet<UserGroup>());
        Principal principal = new UsernamePasswordAuthenticationToken(guest,"", authorities);
        return principal;
    }
    
    /**
     * Given a Group Set returns a List that contains all the group names
     * 
     * @param groups
     * @return
     */
    public static List<String> extratcGroupNames(Set<UserGroup> groups){
        List<String> groupNames = new ArrayList<String>();
        for(UserGroup ug : groups){
            groupNames.add(ug.getGroupName());
        }
        return groupNames;
    }
    
    /**
     * Check if the provided user belongs to a group called as the groupname param.
     * Please note that this method doesn't check if a group called as groupname really exist.
     * 
     * @param user
     * @param groupname
     * @return
     */
    public static boolean belongTo(User user, String groupname){
        Set<UserGroup> groups = user.getGroups();
        for(UserGroup ug : groups){
            if(ug.getGroupName().equalsIgnoreCase(groupname)){
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get resources filtered by authUser
     * 
     * @param resources
     * @param authUser
     * 
     * @return resources allowed for auth user
     */
    public List<Resource> getResourcesAllowed(List<Resource> resources, User authUser){

        List<Resource> allowedResources = new ArrayList<Resource>();
        //
        // Authorization check.
        //
        for (Resource r: resources) {
            if (resourceAccessRead(authUser, r.getId())) {
                allowedResources.add(r);
            }
        }
        return allowedResources;
    }

    /**
     * Get short resources filtered by authUser
     * 
     * @param resources
     * @param authUser
     * 
     * @return short resources allowed for auth user
     */
    public List<ShortResource> getShortResourcesAllowed(List<ShortResource> resources, User authUser){

        List<ShortResource> allowedResources = new ArrayList<ShortResource>();
        //
        // Authorization check.
        //
        for (ShortResource sr: resources) {
            if (resourceAccessRead(authUser, sr.getId())) {
                allowedResources.add(sr);
            }
        }
        return allowedResources;
    }
}
