/*
 *  Copyright (C) 2007 - 2012 GeoSolutions S.A.S.
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

import it.geosolutions.geostore.core.dao.UserAttributeDAO;
import it.geosolutions.geostore.core.dao.UserDAO;
import it.geosolutions.geostore.core.dao.UserGroupDAO;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.GroupReservedNames;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.core.model.enums.UserReservedNames;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.cxf.common.util.StringUtils;
import org.apache.log4j.Logger;

import com.googlecode.genericdao.search.Filter;
import com.googlecode.genericdao.search.Search;

/**
 * Class UserServiceImpl.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * @author ETj (etj at geo-solutions.it)
 */
public class UserServiceImpl implements UserService {

    private static final Logger LOGGER = Logger.getLogger(UserServiceImpl.class);

    private UserDAO userDAO;

    private UserAttributeDAO userAttributeDAO;

    private UserGroupDAO userGroupDAO;

    /**
     * @param userGroupDAO the userGroupDAO to set
     */
    public void setUserGroupDAO(UserGroupDAO userGroupDAO) {
        this.userGroupDAO = userGroupDAO;
    }

    /**
     * @param userAttributeDAO the userAttributeDAO to set
     */
    public void setUserAttributeDAO(UserAttributeDAO userAttributeDAO) {
        this.userAttributeDAO = userAttributeDAO;
    }

    /**
     * @param userDAO the userDAO to set
     */
    public void setUserDAO(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#insert(it.geosolutions.geostore.core.model.User)
     */
    @Override
    public long insert(User user) throws BadRequestServiceEx, NotFoundServiceEx {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Persisting User ... ");
        }

        if (user == null) {
            throw new BadRequestServiceEx("User type must be specified !");
        }
        if(!UserReservedNames.isAllowedName(user.getName())){
            throw new BadRequestServiceEx("User name '" + user.getName() + "' is not allowed...");
        }

        User u = new User();
        u.setName(user.getName());
        u.setNewPassword(user.getNewPassword());
        u.setEnabled(user.isEnabled());
        u.setRole(user.getRole());

        //
        // Checking User Group
        //
        Set<UserGroup> groups = user.getGroups();
        List<String> groupNames = new ArrayList<String>();
        List<UserGroup> existingGroups = new ArrayList<UserGroup>();
        if (groups != null && groups.size() > 0) {
            for(UserGroup group : groups){
                String groupName = group.getGroupName();
                groupNames.add(groupName);
                if (StringUtils.isEmpty(groupName)) {
                    throw new BadRequestServiceEx("The user group name must be specified! ");
                }
            }
            //
            // Searching the corresponding UserGroups
            //
            Search searchCriteria = new Search(UserGroup.class);
            searchCriteria.addFilterIn("groupName", groupNames);

            existingGroups = userGroupDAO.search(searchCriteria);

            if (existingGroups != null && groups.size() != existingGroups.size()) {
                throw new NotFoundServiceEx("At least one User group not found; review the groups associated to the user you want to insert" + user.getId());
            }            
        }
        // Special Usergroup EVERYONE management
        Search search = new Search();
        search.addFilterEqual("groupName", GroupReservedNames.EVERYONE.groupName());
        List<UserGroup> ugEveryone = userGroupDAO.search(search);
        if(ugEveryone == null || ugEveryone.size() != 1){
            // Only log the error at ERROR level and avoid to block the user creation... 
            LOGGER.error("No UserGroup EVERYONE found, or more than 1 results has been found... skip the EVERYONE group associations for user '" + user.getName() + "'");
        }
        else{
            existingGroups.add(ugEveryone.get(0));
        }
        u.setGroups(new HashSet<UserGroup>(existingGroups));

        userDAO.persist(u);

        //
        // Persisting User Attributes
        //
        List<UserAttribute> attributes = user.getAttribute();

        if (attributes != null) {
            for (UserAttribute a : attributes) {
                a.setUser(u);
                userAttributeDAO.persist(a);
            }
        }

        return u.getId();
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#update(it.geosolutions.geostore.core.model.User)
     */
    @Override
    public long update(User user) throws NotFoundServiceEx, BadRequestServiceEx {
        User orig = get(user.getId());

        if (orig == null) {
        	orig = get(user.getName());
        	user.setId(orig.getId());
        }
        
        if (orig == null) {
    		throw new NotFoundServiceEx("User not found " + user.getId());
    	}

        //
        // Checking User Group
        //
        Set<UserGroup> groups = GroupReservedNames.checkReservedGroups(user.getGroups());
        List<String> groupNames = new ArrayList<String>();
        Set<UserGroup> existingGroups = new HashSet<UserGroup>();
        if (groups != null && groups.size() > 0) {
            for(UserGroup group : groups){
                String groupName = group.getGroupName();
                groupNames.add(groupName);
                if (StringUtils.isEmpty(groupName)) {
                    throw new BadRequestServiceEx("The user group name must be specified! ");
                }
            }
            //
            // Searching the corresponding UserGroups
            //
            Search searchCriteria = new Search(UserGroup.class);
            searchCriteria.addFilterIn("groupName", groupNames);

            existingGroups = GroupReservedNames.checkReservedGroups(userGroupDAO.search(searchCriteria));

            if (existingGroups == null || (existingGroups != null && groups.size() != existingGroups.size())) {
                throw new NotFoundServiceEx("At least one User group not found; review the groups associated to the user you want to insert" + user.getId());
            }
           
        }
        // Special Usergroup EVERYONE management
        Search search = new Search();
        search.addFilterEqual("groupName", GroupReservedNames.EVERYONE.groupName());
        List<UserGroup> ugEveryone = userGroupDAO.search(search);
        if(ugEveryone == null || ugEveryone.size() != 1){
            // Only log the error at ERROR level and avoid to block the user creation... 
            LOGGER.error("No UserGroup EVERYONE found, or more than 1 results has been found... skip the EVERYONE group associations for user '" + user.getName() + "'");
        }
        else{
            existingGroups.add(ugEveryone.get(0));
        }
        user.getGroups().clear();
        user.getGroups().addAll(existingGroups);
        
        userDAO.merge(user);

        return orig.getId();
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#updateAttributes(long, java.util.List)
     */
    @Override
    public void updateAttributes(long id, List<UserAttribute> attributes) throws NotFoundServiceEx {
        User user = userDAO.find(id);
        if (user == null) {
            throw new NotFoundServiceEx("User not found " + id);
        }

        //
        // Removing old attributes
        //
        List<UserAttribute> oldList = user.getAttribute();
        // Iterator<UserAttribute> iterator;

        if (oldList != null) {
            for (UserAttribute a : oldList) {
                userAttributeDAO.removeById(a.getId());
            }
        }

        //
        // Saving old attributes
        //
        for (UserAttribute a : attributes) {
            a.setUser(user);
            userAttributeDAO.persist(a);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#get(long)
     */
    @Override
    public User get(long id) {
        User user = userDAO.find(id);
        // CHECKME: shouldnt we throw a NotFound when user not found?
        return user;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#get(java.lang.String)
     */
    @Override
    public User get(String name) throws NotFoundServiceEx {
        Search searchCriteria = new Search(User.class);
        searchCriteria.addFilterEqual("name", name);
        searchCriteria.addFetch("attribute");

        List<User> users = userDAO.search(searchCriteria);
        if (!users.isEmpty()) {
            return users.get(0);
        } else {
            throw new NotFoundServiceEx("User not found with name: " + name);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#delete(long)
     */
    @Override
    public boolean delete(long id) {
        return userDAO.removeById(id);
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#getAll(java.lang.Integer, java.lang.Integer)
     */
    @Override
    public List<User> getAll(Integer page, Integer entries) throws BadRequestServiceEx {

        if (((page != null) && (entries == null)) || ((page == null) && (entries != null))) {
            throw new BadRequestServiceEx("Page and entries params should be declared together.");
        }

        Search searchCriteria = new Search(User.class);

        if (page != null) {
            searchCriteria.setMaxResults(entries);
            searchCriteria.setPage(page);
        }

        searchCriteria.addSortAsc("name");

        List<User> found = userDAO.search(searchCriteria);

        return found;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#getAll(java.lang.Integer, java.lang.Integer)
     */
    @Override
    public List<User> getAll(Integer page, Integer entries, String nameLike,
            boolean includeAttributes) throws BadRequestServiceEx {

        if (((page != null) && (entries == null)) || ((page == null) && (entries != null))) {
            throw new BadRequestServiceEx("Page and entries params should be declared together.");
        }

        Search searchCriteria = new Search(User.class);

        if (page != null) {
            searchCriteria.setMaxResults(entries);
            searchCriteria.setPage(page);
        }

        searchCriteria.addSortAsc("name");

        if (nameLike != null) {
            searchCriteria.addFilterILike("name", nameLike);
        }

        List<User> found = userDAO.search(searchCriteria);
        found = this.configUserList(found, includeAttributes);

        return found;
    }

    /**
     * @param list
     * @param includeAttributes
     * @return List<User>
     */
    private List<User> configUserList(List<User> list, boolean includeAttributes) {
        List<User> uList = new ArrayList<User>(list.size());

        for (User user : list) {
            User u = new User();
            u.setGroups(user.getGroups());
            u.setId(user.getId());
            u.setName(user.getName());
            u.setEnabled(user.isEnabled());
            u.setPassword(user.getPassword());
            u.setRole(user.getRole());

            if (includeAttributes) {
                u.setAttribute(user.getAttribute());
            }

            uList.add(u);
        }

        return uList;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#getCount(java.lang.String)
     */
    @Override
    public long getCount(String nameLike) {
        Search searchCriteria = new Search(User.class);

        if (nameLike != null) {
            searchCriteria.addFilterILike("name", nameLike);
        }

        return userDAO.count(searchCriteria);
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.UserService#insertSpecialUsers()
     */
    @Override
    public boolean insertSpecialUsers() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Persisting Reserved Users... ");
        }
        
        User u = new User();
        u.setName(UserReservedNames.GUEST.userName());
        u.setRole(Role.GUEST);
        Search search = new Search();
        search.addFilterEqual("groupName", GroupReservedNames.EVERYONE.groupName());
        List<UserGroup> userGroup = userGroupDAO.search(search);
        if(userGroup.size() != 1){
            LOGGER.warn("More than EVERYONE group is found...");
        }
        u.setGroups(new HashSet<UserGroup>(userGroup));
        userDAO.persist(u);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Special User '" + u.getName() + "' persisted!");
        }
        return true;
    }

    @Override
    public Collection<User> getByAttribute(UserAttribute attribute) {
        
        Search searchCriteria = new Search(UserAttribute.class);
        searchCriteria.addFilterEqual("name", attribute.getName());
        searchCriteria.addFilterEqual("value", attribute.getValue());
        searchCriteria.addFetch("user");

        List<UserAttribute> attributes = userAttributeDAO.search(searchCriteria);
        
        Set<User> users = new HashSet<User>();
        for(UserAttribute userAttr : attributes) {
            if(userAttr.getUser() != null) {
                users.add(userAttr.getUser());
            }
        }
        return users;
    }

    @Override
    public Collection<User> getByGroup(UserGroup group) {

        Search searchByGroup = new Search(User.class);
        // preferred search is by group name, revert back to id based search for compatibility
        // if name is not present
        if (group.getGroupName() != null) {
            searchByGroup.addFilterSome("groups", Filter.equal("groupName", group.getGroupName()));
        } else {
            searchByGroup.addFilterSome("groups", Filter.equal("id", group.getId()));
        }
        return userDAO.search(searchByGroup);
    }
}
