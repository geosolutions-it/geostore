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

import com.googlecode.genericdao.search.Search;

import java.util.Iterator;
import java.util.List;

import it.geosolutions.geostore.core.dao.UserAttributeDAO;
import it.geosolutions.geostore.core.dao.UserDAO;
import it.geosolutions.geostore.core.dao.UserGroupDAO;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;

import org.apache.log4j.Logger;

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

    /* (non-Javadoc)
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

        User u = new User();
        u.setName(user.getName());
        u.setNewPassword(user.getNewPassword());
        u.setRole(user.getRole());

        //
        // Checking User Group
        //
        UserGroup group = user.getGroup();
        if (group != null) {
            String groupName = group.getGroupName();
            if (groupName != null) {
                //
                // Searching the corresponding UserGroup
                //
                Search searchCriteria = new Search(UserGroup.class);
                searchCriteria.addFilterEqual("groupName", groupName);

                List<UserGroup> groups = userGroupDAO.search(searchCriteria);

                if (groups.isEmpty()) {
                    throw new NotFoundServiceEx("User group not found; " + user.getId());
                }

                u.setGroup(groups.get(0));
            } else {
                throw new BadRequestServiceEx("User group name must be specified ! ");
            }
        }

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

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.UserService#update(it.geosolutions.geostore.core.model.User)
     */
    @Override
    public long update(User user) throws NotFoundServiceEx, BadRequestServiceEx {
        User orig = userDAO.find(user.getId());

        if (orig == null) {
            throw new NotFoundServiceEx("User not found " + user.getId());
        }

        //
        // Checking User Group
        //
        UserGroup group = user.getGroup();
        if (group != null) {
            String groupName = group.getGroupName();
            if (groupName != null) {
                //
                // Searching the corresponding UserGroup
                //
                Search searchCriteria = new Search(UserGroup.class);
                searchCriteria.addFilterEqual("groupName", groupName);

                List<UserGroup> groups = userGroupDAO.search(searchCriteria);

                if (groups.isEmpty()) {
                    throw new NotFoundServiceEx("User group not found; " + user.getId());
                }

                user.setGroup(groups.get(0));
            } else {
                throw new BadRequestServiceEx("User group name must be specified ! ");
            }
        }

        userDAO.merge(user);

        return orig.getId();
    }

    /* (non-Javadoc)
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
        Iterator<UserAttribute> iterator;

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

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.UserService#get(long)
     */
    @Override
    public User get(long id) {
        User user = userDAO.find(id);
        // CHECKME: shouldnt we throw a NotFound when user not found?
        return user;
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.UserService#get(java.lang.String)
     */
    @Override
    public User get(String name) throws NotFoundServiceEx {
        Search searchCriteria = new Search(User.class);
        searchCriteria.addFilterEqual("name", name);

        List<User> users = userDAO.search(searchCriteria);
        if( ! users.isEmpty()) {
            return users.get(0);
        } else {
            throw new NotFoundServiceEx("User not found with name: " + name);
        }
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.UserService#delete(long)
     */
    @Override
    public boolean delete(long id) {
        return userDAO.removeById(id);
    }

    /* (non-Javadoc)
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

    /* (non-Javadoc)
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
}
