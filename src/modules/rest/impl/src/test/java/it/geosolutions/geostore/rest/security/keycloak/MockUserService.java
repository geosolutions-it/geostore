/* ====================================================================
 *
 * Copyright (C) 2022 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.rest.security.keycloak;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MockUserService for testing purpose with KeycloakFilter
 */
class MockUserService implements UserService {

    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final AtomicLong atomicLong = new AtomicLong();

    @Override
    public long insert(User user) throws BadRequestServiceEx, NotFoundServiceEx {
        Long id = atomicLong.incrementAndGet();
        user.setId(id);
        users.put(user.getName(), user);
        return id;
    }

    @Override
    public long update(User user) throws NotFoundServiceEx, BadRequestServiceEx {
        return 0;
    }

    @Override
    public boolean delete(long id) {
        return false;
    }

    @Override
    public User get(long id) {
        return users.values().stream().filter(u -> u.getId().equals(id)).findAny().get();
    }

    @Override
    public User get(String name) throws NotFoundServiceEx {
        return users.get(name);
    }

    @Override
    public List<User> getAll(Integer page, Integer entries) throws BadRequestServiceEx {
        return null;
    }

    @Override
    public List<User> getAll(Integer page, Integer entries, String nameLike, boolean includeAttributes) throws BadRequestServiceEx {
        return null;
    }

    @Override
    public long getCount(String nameLike) {
        return 0;
    }

    @Override
    public void updateAttributes(long id, List<UserAttribute> attributes) throws NotFoundServiceEx {

    }

    @Override
    public boolean insertSpecialUsers() {
        return false;
    }

    @Override
    public Collection<User> getByAttribute(UserAttribute attribute) {
        return null;
    }

    @Override
    public Collection<User> getByGroup(UserGroup group) {
        return null;
    }
}
