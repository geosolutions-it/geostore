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
import it.geosolutions.geostore.core.model.enums.GroupReservedNames;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.exception.ReservedUserGroupNameEx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.googlecode.genericdao.search.Filter;
import com.googlecode.genericdao.search.Search;

/**
 * @author DamianoG
 *
 */
public class UserGroupServiceImpl implements UserGroupService {

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
        
        if(!GroupReservedNames.isAllowedName(userGroup.getGroupName())){
            throw new ReservedUserGroupNameEx("The usergroup name you try to save: '" + userGroup.getGroupName() + "' is a reserved name!");
        }
        
        userGroup.setGroupName(userGroup.getGroupName());
        
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
    public boolean delete(long id) throws NotFoundServiceEx, BadRequestServiceEx {
        UserGroup group = userGroupDAO.find(id);
        if(group == null){
            LOGGER.error("Can't find usergroup with id '" + id + "'");
            throw new NotFoundServiceEx("Can't find usergroup with id '" + id + "'");
        }
        if(!GroupReservedNames.isAllowedName(group.getGroupName())){
            throw new BadRequestServiceEx("Delete a special usergroup ('" + group.getGroupName() + "' in this case) isn't possible");
        }

        for(User u : getUsersByGroup(id)){
            u.removeGroup(id);
            userDAO.merge(u);
        }

        userGroupDAO.remove(group);
        return true;
    }

    private Collection<User> getUsersByGroup(long groupId) {
        Search searchByGroup = new Search(User.class);
        searchByGroup.addFilterSome("groups", Filter.equal("id", groupId));
        return userDAO.search(searchByGroup);
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.UserGroupService#assignUserGroup(long, long)
     */
    @Override
    public void assignUserGroup(long userId, long groupId) throws NotFoundServiceEx{
        UserGroup groupToAssign = userGroupDAO.find(groupId);
        // Check if the group user want to assign is an allowed one
        if(!GroupReservedNames.isAllowedName(groupToAssign.getGroupName())){
            throw new NotFoundServiceEx("You can't re-assign the group EVERYONE or any other reserved groups...");
        }
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
     * @see it.geosolutions.geostore.services.UserGroupService#deassignUserGroup(long, long)
     */
    @Override
    public void deassignUserGroup(long userId, long groupId) throws NotFoundServiceEx{
        UserGroup groupToAssign = userGroupDAO.find(groupId);
        // Check if the group user want to remove is an allowed one
        if(!GroupReservedNames.isAllowedName(groupToAssign.getGroupName())){
            throw new NotFoundServiceEx("You can't remove the group EVERYONE or any other reserved groups from the users group list...");
        }
        User targetUser = userDAO.find(userId);
        if(groupToAssign == null || targetUser == null){
            throw new NotFoundServiceEx("The userGroup or the user you provide doesn't exist");
        }

        if(targetUser.removeGroup(groupId)) {
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

    @Override
    public List<UserGroup> getAllAllowed(User user, Integer page, Integer entries, String nameLike, boolean all) throws BadRequestServiceEx {
        if (user == null)
            throw new BadRequestServiceEx("User must be defined.");

        if (((page != null) && (entries == null)) || ((page == null) && (entries != null))) {
            throw new BadRequestServiceEx("Page and entries params should be declared together.");
        }

        Search searchCriteria = new Search(UserGroup.class);
        if (page != null) {
            searchCriteria.setMaxResults(entries);
            searchCriteria.setPage(page);
        }
        searchCriteria.addSortAsc("groupName");

        if (user.getRole() == Role.USER) {
            Set<UserGroup> userGrp = user.getGroups();
            List<Long> grpIds = new ArrayList<>(userGrp.size());
            for(UserGroup grp : userGrp){
                grpIds.add(grp.getId());
            }
            searchCriteria.addFilterIn("id", grpIds);
        }

        if (nameLike != null)
            searchCriteria.addFilterILike("groupName", nameLike);

        if(!all)
            searchCriteria.addFilterNotEqual("groupName", GroupReservedNames.EVERYONE.groupName());

        List<UserGroup> found = userGroupDAO.search(searchCriteria);

        return found;
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.UserGroupService#updateSecurityRules(java.lang.Long, java.util.List, boolean, boolean)
     */
    @Override
    public List<ShortResource> updateSecurityRules(Long groupId, List<Long> resourcesIds, boolean canRead,
            boolean canWrite) throws NotFoundServiceEx, BadRequestServiceEx {
        
        List<ShortResource> updated = new ArrayList<ShortResource>();
        UserGroup group = userGroupDAO.find(groupId);
        
        if(group == null){
            throw new NotFoundServiceEx("The usergroup id you provide doesn't exist!");
        }
        
        if(group.getGroupName().equals(GroupReservedNames.EVERYONE.groupName())){
            if(!canRead || canWrite){
                LOGGER.error("You are trying to assign to a resource the following permissions for the group EVERYONE: [canRead='" + canRead + "',canWrite'" + canWrite + "'] but...");
                LOGGER.error("...the group EVERYONE can be set only in this way: [canRead='true',canWrite='false'] .");
                throw new BadRequestServiceEx("GroupEveryone cannot be set with this grants [canRead='" + canRead + "',canWrite'" + canWrite + "']");
            }
        }
        
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
                // who call the service (like in other service) but can Delete/Edit are set as the rule updated.
                out.setCanDelete(canWrite);
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
                // who call the sevrice (like in other service) but can Delete/Edit are set as the rule updated.
                out.setCanDelete(canWrite);
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
    

    public boolean insertSpecialUsersGroups(){
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Persisting Reserved UsersGroup... ");
        }
        
        UserGroup ug = new UserGroup();
        ug.setGroupName(GroupReservedNames.EVERYONE.groupName());
        userGroupDAO.persist(ug);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Special UserGroup '" + ug.getGroupName() + "' persisted!");
        }
        return true;
    }
    
    public boolean removeSpecialUsersGroups(){
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Removing Reserved UsersGroup... ");
        }
        
        Search search = new Search();
        search.addFilterEqual("groupName", GroupReservedNames.EVERYONE.groupName());
        List<UserGroup> ugEveryone = userGroupDAO.search(search);
        if (ugEveryone.size() == 1) {
            UserGroup ug = ugEveryone.get(0);
            boolean res = userGroupDAO.removeById(ug.getId());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Special UserGroup '" + ug.getGroupName() + "' removed!");
            }
            return res;
        }
        return false;
    }

    @Override
    public UserGroup get(long id) throws BadRequestServiceEx {
        return userGroupDAO.find(id);
    }

    @Override
    public UserGroup get(String name) {
        Search searchCriteria = new Search(UserGroup.class);
        searchCriteria.addFilterEqual("groupName", name);

        List<UserGroup> existingGroups = userGroupDAO.search(searchCriteria);
        if (existingGroups.size() > 0) {
            return existingGroups.get(0);
        }
        return null;
    }

    @Override
    public long getCount(User user, String nameLike, boolean all) throws BadRequestServiceEx {
        if (user == null)
            throw new BadRequestServiceEx("User must be defined.");

        Search searchCriteria = new Search(UserGroup.class);

        searchCriteria.addSortAsc("groupName");

        if (user.getRole() ==  Role.USER){
            Set<UserGroup> userGrp = user.getGroups();
            Collection<Long> grpIds = new ArrayList<>();
            for(UserGroup grp :userGrp){
                grpIds.add(grp.getId());
            }
            searchCriteria.addFilterIn("id", grpIds);
        }

        if (nameLike != null) {
            searchCriteria.addFilterILike("groupName", nameLike);
        }

        if(!all)
            searchCriteria.addFilterNotEqual("groupName", GroupReservedNames.EVERYONE.groupName());

        return userGroupDAO.count(searchCriteria);


    }

    public long getCount(User user, String nameLike) throws BadRequestServiceEx {
        return getCount(user, nameLike, false);
    }
}
