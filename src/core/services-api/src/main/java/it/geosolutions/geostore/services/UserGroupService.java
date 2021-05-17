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

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;

import java.util.List;

/**
 * @author DamianoG
 *
 */
public interface UserGroupService {

    /**
     * 
     * @param userGroup
     * @return
     * @throws BadRequestServiceEx
     */
    long insert(UserGroup userGroup) throws BadRequestServiceEx;
    
    /**
     * 
     * @param id
     * @throws NotFoundServiceEx
     * @throws BadRequestServiceEx 
     */
    boolean delete(long id) throws NotFoundServiceEx, BadRequestServiceEx;
    
    /**
     * 
     * @param userId
     * @param groupId
     * @throws NotFoundServiceEx
     */
    void assignUserGroup(long userId, long groupId) throws NotFoundServiceEx;
    
    /**
     * 
     * @param userId
     * @param groupId
     * @throws NotFoundServiceEx
     */
    void deassignUserGroup(long userId, long groupId) throws NotFoundServiceEx;
    
    /**
     * 
     * @param page
     * @param entries
     * @return
     * @throws BadRequestServiceEx
     */
    List<UserGroup> getAll(Integer page, Integer entries) throws BadRequestServiceEx;

    /**
     * Returns a list of groups that match searching criteria with pagination.
     *
     * @param user the user that performs the research
     * @param page the requested page number
     * @param entries max entries for page.
     * @param nameLike a sub-string to search in group name
     * @param all if <code>true</code> adds to result the 'everyone' group if it matches the searching criteria
     * @return a list of groups that match searching criteria with pagination.
     *
     * @throws BadRequestServiceEx
     */
    List<UserGroup> getAllAllowed(User user, Integer page, Integer entries, String nameLike, boolean all) throws BadRequestServiceEx;

    UserGroup get(long id) throws BadRequestServiceEx;
    /**
     * 
     * @param groupId
     * @param resourcesToSet
     * @param canRead
     * @param canWrite
     * @return
     * @throws BadRequestServiceEx 
     * @throws BadRequestWebEx
     * @throws NotFoundWebEx
     */
    List<ShortResource> updateSecurityRules(Long groupId, List<Long> resourcesToSet, boolean canRead, boolean canWrite) throws NotFoundServiceEx, BadRequestServiceEx;
    
    /**
     * Persist the special UserGroups, those that implies special behavior
     * 
     * For obvious reasons this Method MUST NOT exposed through the rest interface.
     * 
     * @return true if the persist operation finish with success, false otherwise  
     */
    public boolean insertSpecialUsersGroups();
    
    /**
     * Remove the special UserGroups, those that implies special behavior
     * 
     * For obvious reasons this Method MUST NOT exposed through the rest interface.
     * 
     * @return true if the removal operation finish with success, false otherwise  
     */
    public boolean removeSpecialUsersGroups();
    /**
     * Get The UserGroup from the name
     * @param name
     */
    public UserGroup get(String name);

    /**
     * Returns the amount of groups that match searching criteria.
     * The 'everyone' group is never included.
     *
     * @param authUser the user that performs the research
     * @param nameLike a sub-string to search in group name
     * @return the amount of groups that match searching criteria
     *
     * @throws BadRequestServiceEx
     */
    long getCount(User authUser, String nameLike) throws BadRequestServiceEx;

    /**
     * Returns the amount of groups that match searching criteria.
     *
     * @param authUser the user that performs the research
     * @param nameLike a sub-string to search in group name
     * @param all if <code>true</code> adds to result the 'everyone' group if it matches the searching criteria
     * @return the amount of groups that match searching criteria
     *
     * @throws BadRequestServiceEx
     */
    long getCount(User authUser, String nameLike, boolean all) throws BadRequestServiceEx;
}
