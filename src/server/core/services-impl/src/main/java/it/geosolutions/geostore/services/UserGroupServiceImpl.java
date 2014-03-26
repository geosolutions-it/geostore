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

import it.geosolutions.geostore.core.dao.ResourceDAO;
import it.geosolutions.geostore.core.dao.SecurityDAO;
import it.geosolutions.geostore.core.dao.UserDAO;
import it.geosolutions.geostore.core.dao.UserGroupDAO;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    
    private UserDAO userDAO;

    private ResourceDAO resourceDAO;
    
    private SecurityDAO securityDAO;
    
    /**
     * @param userGroupDAO the userGroupDAO to set
     */
    public void setUserGroupDAO(UserGroupDAO userGroupDAO) {
        this.userGroupDAO = userGroupDAO;
    }
    
    /**
     * 
     * @param userDAO the userDAO to set
     */
    public void setUserDAO(UserDAO userDAO) {
        this.userDAO = userDAO;
    }
    
    /**
     * 
     * @param resourceDAO the resourceDAO to set
     */
    public void setResourceDAO(ResourceDAO resourceDAO) {
        this.resourceDAO = resourceDAO;
    }
    
    /**
     * @param securityDAO the securityDAO to set
     */
    public void setSecurityDAO(SecurityDAO securityDAO) {
        this.securityDAO = securityDAO;
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
    public boolean delete(long id) throws NotFoundServiceEx {
        UserGroup group = userGroupDAO.find(id);
        if(group == null){
            LOGGER.error("Can't find usergroup with id '" + id + "'");
            throw new NotFoundServiceEx("Can't find usergroup with id '" + id + "'");
        }
        Set<User> users = group.getUsers();
        for(User u : users){
            u.getGroups().remove(group);
            userDAO.merge(u);
        }
        userGroupDAO.remove(group);
        return true;
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.UserGroupService#assignUserGroup(long, long)
     */
    @Override
    public void assignUserGroup(long userId, long groupId) throws NotFoundServiceEx{
        UserGroup groupToAssign = userGroupDAO.find(groupId);
        User targetUser = userDAO.find(userId);
        if(groupToAssign == null || targetUser == null){
            throw new NotFoundServiceEx("The userGroup or the user you provide doesn't exist");
        }
        if(targetUser.getGroups() == null){
            Set<UserGroup> groups = new HashSet<UserGroup>();
            groups.add(groupToAssign);
            targetUser.setGroups(groups);
            userDAO.merge(targetUser);
        }
        else{
            targetUser.getGroups().add(groupToAssign);
            userDAO.merge(targetUser);
        }
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

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.UserGroupService#updateSecurityRules(java.lang.Long, java.util.List, boolean, boolean)
     */
    @Override
    public List<ShortResource> updateSecurityRules(Long groupId, List<Long> resourcesIds, boolean canRead,
            boolean canWrite) throws NotFoundServiceEx {
        
        List<ShortResource> updated = new ArrayList<ShortResource>();
        UserGroup group = userGroupDAO.find(groupId);
        
        List<Resource> resourceToSet = resourceDAO.findResources(resourcesIds);
        
        for(Resource resource : resourceToSet){
            SecurityRule sr = getRuleForGroup(resource.getSecurity(), group);
            if(sr == null){
                // Create new rule
                SecurityRule newSR = new SecurityRule();
                newSR.setCanRead(canRead);
                newSR.setCanWrite(canWrite);
                newSR.setGroup(group);
                newSR.setResource(resource);
                securityDAO.persist(newSR);
                resource.getSecurity().add(newSR);
                
                ShortResource out = new ShortResource(resource);
                // In this case the short resource to return is not related to the permission available by the user
                // who call the service (like in other service) but can Delete/Edit are setted as the rule updated.
                out.setCanDelete(canRead);
                out.setCanEdit(canWrite);
                updated.add(out);
            }
            else{
                // Update the existing rule
                sr.setCanRead(canRead);
                sr.setCanWrite(canWrite);
                securityDAO.merge(sr);
                
                ShortResource out = new ShortResource(resource);
                // In this case the short resource to return is not related to the permission available by the user
                // who call the sevrice (like in other service) but can Delete/Edit are setted as the rule updated.
                out.setCanDelete(canRead);
                out.setCanEdit(canWrite);
                updated.add(out);
            }
        }
        
        return updated;
    }
    
    private SecurityRule getRuleForGroup(List<SecurityRule> securityList, UserGroup group){
        for(SecurityRule sr : securityList){
            if(sr.getGroup() != null && sr.getGroup().getGroupName() != null && sr.getGroup().getGroupName().equals(group.getGroupName())){
                return sr;
            }
        }
        return null;
    }
}
