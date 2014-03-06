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

import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.UserGroupService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.rest.RESTUserGroupService;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.UserGroupList;

import javax.ws.rs.core.SecurityContext;

import org.apache.commons.lang.NotImplementedException;
import org.apache.log4j.Logger;

/**
 * @author DamianoG
 *
 */
public class RESTUserGroupServiceImpl implements RESTUserGroupService{

    private final static Logger LOGGER = Logger.getLogger(RESTUserGroupServiceImpl.class);

    private UserGroupService userGroupService;

    /**
     * 
     * @param userGroupService
     */
    public void setuserGroupService(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
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
        throw new NotImplementedException("This service is not implemented yet");
        
    }

    /* 
     * (non-Javadoc) @see it.geosolutions.geostore.services.rest.RESTUserGroupService#assignUserGroup(javax.ws.rs.core.SecurityContext, long, long)
     */
    @Override
    public void assignUserGroup(SecurityContext sc, long userId, long groupId) throws NotFoundWebEx {
        throw new NotImplementedException("This service is not implemented yet");
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTUserGroupService#getAll(javax.ws.rs.core.SecurityContext, java.lang.Integer, java.lang.Integer)
     */
    @Override
    public UserGroupList getAll(SecurityContext sc, Integer page, Integer entries)
            throws BadRequestWebEx {

        try {
            return new UserGroupList(userGroupService.getAll(page, entries));
        } catch (BadRequestServiceEx e) {
            LOGGER.error(e.getMessage(), e);
            throw new BadRequestWebEx(e.getMessage());
        }
        
    }

    

}
