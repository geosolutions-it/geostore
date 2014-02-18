/*
 *  Copyright (C) 2007 - 2011 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.utils;

import it.geosolutions.geostore.core.dao.util.PwEncoder;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mocked user service for GeoStoreAuthenticationInterceptorTest
 * 
 * @author adiaz (alejandro.diaz at geo-solutions.it)
 * 
 */
public class MockedUserService implements UserService {

    private static Random RANDOM = new Random();

    private Map<Long, User> USERS = new ConcurrentHashMap<Long, User>();

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#insert(it.geosolutions. geostore.core.model.User)
     */
    @Override
    public long insert(User user) throws BadRequestServiceEx, NotFoundServiceEx {
        Long id = RANDOM.nextLong();
        user.setId(id);
        String password = user.getPassword() != null ? user.getPassword()
                : user.getNewPassword() != null ? user.getNewPassword() : "";
        user.setPassword(PwEncoder.encode(password));
        USERS.put(id, user);
        return id;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#update(it.geosolutions. geostore.core.model.User)
     */
    @Override
    public long update(User user) throws NotFoundServiceEx, BadRequestServiceEx {
        Long id = null;
        if (user.getId() != null && USERS.containsKey(user.getId())) {
            id = user.getId();
            USERS.put(id, user);
        }
        return id;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#delete(long)
     */
    @Override
    public boolean delete(long id) {
        return USERS.containsKey(new Long(id)) && USERS.remove(new Long(id)) != null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#get(long)
     */
    @Override
    public User get(long id) {
        return USERS.get(new Long(id));
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#get(java.lang.String)
     */
    @Override
    public User get(String name) throws NotFoundServiceEx {
        for (User user : USERS.values()) {
            if (user.getName().equals(name)) {
                return user;
            }
        }
        throw new NotFoundServiceEx("User not found");
    }

    /**
     * Don't use page for the mocked service
     */
    public List<User> getAll(Integer page, Integer entries) throws BadRequestServiceEx {
        List<User> users = new LinkedList<User>();
        for (User user : USERS.values()) {
            users.add(user);
        }
        return users;
    }

    /**
     * Don't filter in the mocked service
     */
    public List<User> getAll(Integer page, Integer entries, String nameLike,
            boolean includeAttributes) throws BadRequestServiceEx {
        return getAll(page, entries);
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#getCount(java.lang.String)
     */
    @Override
    public long getCount(String nameLike) {
        // Don't needed
        return 0;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.UserService#updateAttributes(long, java.util.List)
     */
    @Override
    public void updateAttributes(long id, List<UserAttribute> attributes) throws NotFoundServiceEx {
        // Don't needed
    }

}
