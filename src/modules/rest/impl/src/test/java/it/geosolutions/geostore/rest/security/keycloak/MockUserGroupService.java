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
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.UserGroupAttribute;
import it.geosolutions.geostore.services.UserGroupService;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** MockUserGroupService for testing purpose with KeycloakFilter */
class MockUserGroupService implements UserGroupService {

    private final Map<String, UserGroup> groups = new ConcurrentHashMap<>();
    private final AtomicLong atomicLong = new AtomicLong();

    @Override
    public long insert(UserGroup userGroup) throws BadRequestServiceEx {
        Long id = atomicLong.incrementAndGet();
        userGroup.setId(id);
        groups.put(userGroup.getGroupName(), userGroup);
        return id;
    }

    @Override
    public boolean delete(long id) throws NotFoundServiceEx, BadRequestServiceEx {
        return false;
    }

    @Override
    public void assignUserGroup(long userId, long groupId) throws NotFoundServiceEx {}

    @Override
    public void deassignUserGroup(long userId, long groupId) throws NotFoundServiceEx {}

    @Override
    public List<UserGroup> getAll(Integer page, Integer entries) throws BadRequestServiceEx {
        return null;
    }

    @Override
    public List<UserGroup> getAll(Integer page, Integer entries, String nameLike, boolean all)
            throws BadRequestServiceEx {
        return null;
    }

    @Override
    public List<UserGroup> getAllAllowed(
            User user, Integer page, Integer entries, String nameLike, boolean all)
            throws BadRequestServiceEx {
        return null;
    }

    @Override
    public UserGroup get(long id) throws BadRequestServiceEx {
        return groups.values().stream().filter(g -> g.getId().equals(id)).findAny().get();
    }

    @Override
    public List<ShortResource> updateSecurityRules(
            Long groupId, List<Long> resourcesToSet, boolean canRead, boolean canWrite)
            throws NotFoundServiceEx, BadRequestServiceEx {
        return null;
    }

    @Override
    public boolean insertSpecialUsersGroups() {
        return false;
    }

    @Override
    public boolean removeSpecialUsersGroups() {
        return false;
    }

    @Override
    public UserGroup get(String name) {
        return groups.get(name);
    }

    @Override
    public long getCount(User authUser, String nameLike) throws BadRequestServiceEx {
        return 0;
    }

    @Override
    public long getCount(User authUser, String nameLike, boolean all) throws BadRequestServiceEx {
        return 0;
    }

    @Override
    public void updateAttributes(long id, List<UserGroupAttribute> attributes)
            throws NotFoundServiceEx {}

    @Override
    public long update(UserGroup group) throws NotFoundServiceEx, BadRequestServiceEx {
        return 0;
    }

    @Override
    public Collection<UserGroup> findByAttribute(
            String name, List<String> values, boolean ignoreCase) {
        return null;
    }
}
