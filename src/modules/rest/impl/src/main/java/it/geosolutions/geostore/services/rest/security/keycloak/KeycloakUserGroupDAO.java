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
package it.geosolutions.geostore.services.rest.security.keycloak;

import com.googlecode.genericdao.search.ISearch;
import com.googlecode.genericdao.search.Search;
import it.geosolutions.geostore.core.dao.UserGroupDAO;
import it.geosolutions.geostore.core.model.UserGroup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.RoleRepresentation;

import javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static it.geosolutions.geostore.core.model.enums.GroupReservedNames.EVERYONE;

/**
 * Keycloak implementation for a {@link UserGroupDAO}.
 * Supports basic search operations. Currently it doesn't support querying and updating by id.
 */
public class KeycloakUserGroupDAO extends BaseKeycloakDAO implements UserGroupDAO {


    private final static Logger LOGGER = LogManager.getLogger(KeycloakUserGroupDAO.class);

    private boolean addEveryOneGroup = false;


    public KeycloakUserGroupDAO(KeycloakAdminClientConfiguration clientConfiguration) {
        super(clientConfiguration);
    }

    static UserGroup everyoneGroup(int id) {
        UserGroup everyoneGroup = new UserGroup();
        everyoneGroup.setGroupName(EVERYONE.groupName());
        everyoneGroup.setEnabled(true);
        everyoneGroup.setId(Long.valueOf(id));
        return everyoneGroup;
    }

    @Override
    public List<UserGroup> findAll() {
        Keycloak keycloak = keycloak();
        try {
            List<RoleRepresentation> roleRepresentations = getRolesResource(keycloak).list();
            return toUserGroups(roleRepresentations, true);
        } catch (NotFoundException e) {
            LOGGER.warn("No users were found", e);
            return null;
        } finally {
            close(keycloak);
        }
    }

    @Override
    public UserGroup find(Long id) {
        return null;
    }

    @Override
    public void persist(UserGroup... userGroups) {
        Keycloak keycloak = keycloak();
        try {
            for (UserGroup group : userGroups) {
                RoleRepresentation roleRepresentation = toRoleRepresentation(group);
                getRolesResource(keycloak).create(roleRepresentation);
                group.setId(-1L);
            }
        } finally {
            close(keycloak);
        }

    }

    @Override
    public UserGroup[] save(UserGroup... userGroups) {
        Keycloak keycloak = keycloak();
        try {
            for (UserGroup group : userGroups) {
                RoleRepresentation roleRepresentation = toRoleRepresentation(group);
                getRolesResource(keycloak).create(roleRepresentation);
                group.setId(-1L);
            }
            return userGroups;
        } finally {
            close(keycloak);
        }
    }

    @Override
    public UserGroup merge(UserGroup userGroup) {
        Keycloak keycloak = keycloak();
        try {
            RoleRepresentation roleRepresentation = new RoleRepresentation();
            roleRepresentation.setName(userGroup.getGroupName());
            roleRepresentation.setDescription(userGroup.getDescription());
            getRolesResource(keycloak).get(userGroup.getGroupName()).update(roleRepresentation);
            userGroup.setId(-1L);
            return userGroup;
        } finally {
            close(keycloak);
        }
    }

    @Override
    public boolean remove(UserGroup userGroup) {
        Keycloak keycloak = keycloak();
        try {
            getRolesResource(keycloak).get(userGroup.getGroupName()).remove();
            return true;
        } catch (NotFoundException e) {
            LOGGER.warn("No user found with name " + userGroup.getGroupName(), e);
            return false;
        } finally {
            close(keycloak);
        }
    }

    @Override
    public boolean removeById(Long id) {
        return false;
    }

    @Override
    public List<UserGroup> search(ISearch search) {
        Keycloak keycloak = keycloak();
        try {
            KeycloakQuery query = toKeycloakQuery(search);
            if (LOGGER.isDebugEnabled()) LOGGER.debug("Executing the following Keycloak query " + query.toString());
            RolesResource rr = getRolesResource(keycloak);
            List<RoleRepresentation> roles;
            String groupName = query.getGroupName();
            if (groupName != null && !query.isExact())
                roles = rr.list(groupName, query.getStartIndex(), query.getMaxResults());
            else if (groupName != null && query.isExact()) {
                if (LOGGER.isDebugEnabled()) LOGGER.debug("Executing exact query");
                RoleRepresentation representation;
                if (EVERYONE.groupName().equals(groupName) && !query.getSkipEveryBodyGroup().booleanValue())
                    representation = everyoneRoleRep();
                else representation = rr.get(groupName).toRepresentation();
                roles = Collections.singletonList(representation);
            } else
                roles = rr.list(query.getStartIndex(), query.getMaxResults());
            return toUserGroups(roles, !query.getSkipEveryBodyGroup().booleanValue());
        } catch (NotFoundException e) {
            LOGGER.warn("No groups were found", e);
            return null;
        } finally {
            close(keycloak);
        }
    }

    @Override
    public int count(ISearch search) {
        Keycloak keycloak = keycloak();
        try {
            KeycloakQuery query = toKeycloakQuery(search);
            int count;
            RolesResource rr = getRolesResource(keycloak);
            if (query.getUserName() != null && query.isExact())
                count = 1;
            else if (query.getUserName() != null)
                count = rr.list(query.getUserName(), true).size();
            else
                count = rr.list().size();
            if (isAddEveryOneGroup()) count++;
            return count;
        } catch (NotFoundException e) {
            LOGGER.warn("No groups were found", e);
            return 0;
        } finally {
            close(keycloak);
        }
    }

    private List<UserGroup> toUserGroups(List<RoleRepresentation> roleRepresentations, boolean isEveryoneRequested) {
        List<UserGroup> groups = new ArrayList<>(roleRepresentations.size());
        int counter = 1;
        for (RoleRepresentation role : roleRepresentations) {
            GeoStoreKeycloakAuthoritiesMapper mapper = getAuthoritiesMapper();
            mapper.mapAuthorities(Collections.singletonList(role.getName()));
            if (mapper.getGroups() != null && !mapper.getGroups().isEmpty()) {
                UserGroup group = new UserGroup();
                group.setGroupName(role.getName());
                group.setDescription(role.getDescription());
                group.setEnabled(true);
                group.setId(Long.valueOf(counter));
                groups.add(group);
                counter++;
            }
        }
        addEveryOne(groups, isEveryoneRequested, counter);
        return groups;
    }

    private RoleRepresentation toRoleRepresentation(UserGroup group) {
        RoleRepresentation roleRepresentation = new RoleRepresentation();
        roleRepresentation.setName(group.getGroupName());
        roleRepresentation.setDescription(group.getDescription());
        return roleRepresentation;
    }

    /**
     * @return True if  everyone group should be added, false otherwise.
     */
    public boolean isAddEveryOneGroup() {
        return addEveryOneGroup;
    }

    /**
     * Sets the addEveryoneGroup flag.
     *
     * @param addEveryOneGroup
     */
    public void setAddEveryOneGroup(boolean addEveryOneGroup) {
        this.addEveryOneGroup = addEveryOneGroup;
    }

    /**
     * Add the everyOne group to the LDAP returned list.
     *
     * @param groups
     * @return
     */
    private List<UserGroup> addEveryOne(List<UserGroup> groups, boolean addEveryOneGroup, int id) {
        boolean found = groups.stream().anyMatch(g -> g.getGroupName().equals(EVERYONE.groupName()));
        if (!found && addEveryOneGroup && isAddEveryOneGroup()) {
            UserGroup everyoneGroup = everyoneGroup(id);
            groups.add(everyoneGroup);
        }
        return groups;
    }

    private RoleRepresentation everyoneRoleRep() {
        RoleRepresentation roleRepresentation = new RoleRepresentation();
        roleRepresentation.setName(EVERYONE.groupName());
        return roleRepresentation;
    }

    @Override
    public UserGroup findByName(String name) {
        Search searchCriteria = new Search(UserGroup.class);
        searchCriteria.addFilterEqual("groupName", name);
        UserGroup result = null;
        List<UserGroup> existingGroups = search(searchCriteria);
        if (existingGroups.size() > 0) {
            result = existingGroups.get(0);
        }
        return result;
    }
}
