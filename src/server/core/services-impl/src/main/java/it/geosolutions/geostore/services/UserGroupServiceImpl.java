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
package it.geosolutions.geostore.services;

import it.geosolutions.geostore.core.dao.UserGroupDAO;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;

import java.util.List;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.googlecode.genericdao.search.Search;

/**
 * @author DamianoG
 *
 */
public class UserGroupServiceImpl implements UserGroupService{

    private static final Logger LOGGER = Logger.getLogger(UserGroupServiceImpl.class);

    private UserGroupDAO userGroupDAO;

    /**
     * @param userGroupDAO the userGroupDAO to set
     */
    public void setUserGroupDAO(UserGroupDAO userGroupDAO) {
        this.userGroupDAO = userGroupDAO;
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.UserGroupService#insert(it.geosolutions.geostore.core.model.UserGroup)
     */
    @Override
    public long insert(UserGroup userGroup) throws BadRequestServiceEx {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Persisting UserGroup... ");
        }

        if (userGroup == null || StringUtils.isEmpty(userGroup.getGroupName())) {
            throw new BadRequestServiceEx("The provided UserGroup instance is null or group Name is not specified!");
        }
        
        userGroupDAO.persist(userGroup);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("UserGroup '" + userGroup.getGroupName() + "' persisted!");
        }
        
        return userGroup.getId();
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.UserGroupService#delete(long)
     */
    @Override
    public void delete(long id) throws NotFoundServiceEx {
        throw new NotImplementedException("This service is not implemented yet");
        
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.UserGroupService#assignUserGroup(long, long)
     */
    @Override
    public void assignUserGroup(long userId, long groupId) throws NotFoundServiceEx {
        throw new NotImplementedException("This service is not implemented yet");
        
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.UserGroupService#getAll(java.lang.Integer, java.lang.Integer)
     */
    @Override
    public List<UserGroup> getAll(Integer page, Integer entries) throws BadRequestServiceEx {
        
        if (((page != null) && (entries == null)) || ((page == null) && (entries != null))) {
            throw new BadRequestServiceEx("Page and entries params should be declared together.");
        }
        
        Search searchCriteria = new Search(UserGroup.class);

        if (page != null) {
            searchCriteria.setMaxResults(entries);
            searchCriteria.setPage(page);
        }
        
        searchCriteria.addSortAsc("groupName");

        List<UserGroup> found = userGroupDAO.search(searchCriteria);

        return found;
    }

}
