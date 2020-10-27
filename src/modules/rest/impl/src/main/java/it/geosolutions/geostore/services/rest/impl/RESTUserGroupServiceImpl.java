/*
 *  Copyright (C) 2007-2012 GeoSolutions S.A.S.
 *  http://www.geo-solutions.it
 *
 *  GPLv3 + Classpath exception
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.geosolutions.geostore.services.rest.impl;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.GroupReservedNames;
import it.geosolutions.geostore.services.UserGroupService;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.RESTUserGroupService;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.RESTUserGroup;
import it.geosolutions.geostore.services.rest.model.ShortResourceList;
import it.geosolutions.geostore.services.rest.model.UserGroupList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.ws.rs.core.SecurityContext;

import org.apache.log4j.Logger;

/**
 * @author DamianoG
 *
 */
public class RESTUserGroupServiceImpl implements RESTUserGroupService {

    private final static Logger LOGGER = Logger.getLogger(RESTUserGroupServiceImpl.class);

    private UserGroupService userGroupService;
    private UserService userService;

    /**
     * 
     * @param userGroupService
     */
    public void setUserGroupService(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    /* 
     * (non-Javadoc) @see it.geosolutions.geostore.services.rest.RESTUserGroupService#insert(javax.ws.rs.core.SecurityContext, it.geosolutions.geostore.core.model.UserGroup)
     */
    @Override
    public long insert(SecurityContext sc, UserGroup userGroup){
        if (userGroup == null) {
            throw new BadRequestWebEx("User is null");
        }
        if (userGroup.getId() != null) {
            throw new BadRequestWebEx("Id should be null");
        }
        
        long id = -1;
        try {
            id = userGroupService.insert(userGroup);
        } catch (BadRequestServiceEx e) {
            throw new BadRequestWebEx(e.getMessage());
        }
        return id;
        
    }

    /* 
     * (non-Javadoc) @see it.geosolutions.geostore.services.rest.RESTUserGroupService#delete(javax.ws.rs.core.SecurityContext, long)
     */
    @Override
    public void delete(SecurityContext sc, long id) throws NotFoundWebEx {
        if (id < 0) {
            throw new BadRequestWebEx("The user group id you provide is < 0... not good...");
        }
        try {
            userGroupService.delete(id);
        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx(e.getMessage());
        } catch (BadRequestServiceEx e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /* 
     * (non-Javadoc) @see it.geosolutions.geostore.services.rest.RESTUserGroupService#get(javax.ws.rs.core.SecurityContext, long)
     */
    @Override
    public RESTUserGroup get(SecurityContext sc, long id)
            throws NotFoundWebEx {
        try {
            UserGroup g = userGroupService.get(id);
            Collection<User> users = userService.getByGroup(g);

            return new RESTUserGroup(g.getId(), g.getGroupName(), new HashSet<>(users), g.getDescription());
        } catch (BadRequestServiceEx e) {
            throw new BadRequestWebEx("UserGroup Not found");
        }
    }
    /* 
     * (non-Javadoc) @see it.geosolutions.geostore.services.rest.RESTUserGroupService#assignUserGroup(javax.ws.rs.core.SecurityContext, long, long)
     */
    @Override
    public void assignUserGroup(SecurityContext sc, long userId, long groupId) throws NotFoundWebEx {
        if (userId < 0 || groupId < 0) {
            throw new BadRequestWebEx("The user group or user id you provide is < 0... not good...");
        }
        try {
            userGroupService.assignUserGroup(userId, groupId);
        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx(e.getMessage());
        }
    }
    
    @Override
    public void deassignUserGroup(SecurityContext sc, long userId, long groupId) throws NotFoundWebEx {
        if (userId < 0 || groupId < 0) {
            throw new BadRequestWebEx("The user group or user id you provide is < 0... not good...");
        }
        try {
            userGroupService.deassignUserGroup(userId, groupId);
        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx(e.getMessage());
        }
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTUserGroupService#getAll(javax.ws.rs.core.SecurityContext, java.lang.Integer, java.lang.Integer)
     */
    @Override
    public UserGroupList getAll(SecurityContext sc, Integer page, Integer entries, boolean all, boolean includeUsers)
            throws BadRequestWebEx {
        try {
            List<UserGroup> returnList = userGroupService.getAll(page, entries);
            List<RESTUserGroup> ugl = new ArrayList<>(returnList.size());
            for(UserGroup ug : returnList){
                if(all || GroupReservedNames.isAllowedName(ug.getGroupName())) {
                    Collection<User> users = includeUsers ? userService.getByGroup(ug) : new HashSet<User>();
                    RESTUserGroup rug = new RESTUserGroup(ug.getId(), ug.getGroupName(), new HashSet<>(users), ug.getDescription());
                    ugl.add(rug);
                }
            }
            return new UserGroupList(ugl);
        } catch (BadRequestServiceEx e) {
            LOGGER.error(e.getMessage(), e);
            throw new BadRequestWebEx(e.getMessage());
        }
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTUserGroupService#updateSecurityRules(it.geosolutions.geostore.core.model.UserGroup, java.util.List, boolean, boolean)
     */
    @Override
    public ShortResourceList updateSecurityRules(SecurityContext sc, ShortResourceList resourcesToSet, Long groupId,
            Boolean canRead, Boolean canWrite) throws BadRequestWebEx, NotFoundWebEx {
        List<ShortResource> srll = new ArrayList<ShortResource>();
        if(groupId == null || groupId < 0){
            throw new BadRequestWebEx("The groupId is null or less than 0...");
        }
        if(resourcesToSet == null || resourcesToSet.isEmpty()){
            throw new BadRequestWebEx("The resources set provided is null or empty...");
        }
        List<ShortResource> sl = resourcesToSet.getList();
        List<Long> slOnlyIds = new ArrayList<Long>();
        for(ShortResource sr : sl){
            if(sr.getId() < 0){
                throw new BadRequestWebEx("One or more ids in resource set is less than 0... check the resources list.");
            }
            slOnlyIds.add(sr.getId());
        }
        try {
            srll = userGroupService.updateSecurityRules(groupId, slOnlyIds, canRead, canWrite);
        } catch (NotFoundServiceEx e) {
            LOGGER.error(e.getMessage(), e);
            throw new NotFoundWebEx(e.getMessage());
        } catch (BadRequestServiceEx e) {
            LOGGER.error(e.getMessage(), e);
            throw new BadRequestWebEx(e.getMessage());
        }
        ShortResourceList srl = new ShortResourceList(srll);
        return srl;
    }

    @Override
    public RESTUserGroup get(SecurityContext sc, String name)
            throws NotFoundWebEx {
        UserGroup ug = userGroupService.get(name);
        if (ug != null) {
            Collection<User> users = userService.getByGroup(ug);
            return new RESTUserGroup(ug.getId(), ug.getGroupName(), new HashSet(users), ug.getDescription());
        }
        return null;
    }
}
