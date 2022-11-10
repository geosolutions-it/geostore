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

import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.core.security.GrantedAuthoritiesMapper;
import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A granted authorities mapper aware of the difference that there is in GeoStore between Role and Groups.
 */
public class GeoStoreKeycloakAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private Set<UserGroup> groups;

    private Role role;

    private static final String ROLE_PREFIX = "ROLE_";

    private Map<String, String> roleMappings;

    private Map<String, String> groupMappings;

    private boolean dropUnmapped=false;


    private int idCounter;

    private final static Logger LOGGER = LogManager.getLogger(GeoStoreKeycloakAuthoritiesMapper.class);


    GeoStoreKeycloakAuthoritiesMapper(Map<String, String> roleMappings,Map<String,String> groupMappings, boolean dropUnmapped) {
        this.roleMappings = roleMappings;
        this.groupMappings = groupMappings;
        if (LOGGER.isDebugEnabled() && roleMappings != null)
            LOGGER.debug("Role mappings have been configured....");
        this.idCounter = 1;
        this.dropUnmapped=dropUnmapped;
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        HashSet<GrantedAuthority> mapped = new HashSet<>(
                authorities.size());
        for (GrantedAuthority authority : authorities) {
            GrantedAuthority newAuthority=mapAuthority(authority.getAuthority());
            if (newAuthority!=null) mapped.add(newAuthority);
        }
        KeyCloakConfiguration configuration = GeoStoreContext.bean(KeyCloakConfiguration.class);
        String defRoleStr = null;
        if (configuration != null) {
            Role def = configuration.getAuthenticatedDefaultRole();
            if (def == null) def = Role.USER;
            defRoleStr = "ROLE_" + def.name();
        }
        String finalDefRoleStr = defRoleStr;
        if (defRoleStr != null && !mapped.stream().anyMatch(ga -> ga.getAuthority().equalsIgnoreCase(finalDefRoleStr))) {
            GrantedAuthority ga = new SimpleGrantedAuthority(defRoleStr);
            mapped.add(ga);
        }
        if (getRole() == null) setDefaultRole();
        return mapped;
    }

    void mapAuthorities(List<String> authorities) {
        if (authorities != null) authorities.forEach(r -> mapStringAuthority(r));
        if (getRole() == null) setDefaultRole();
    }

    private void setDefaultRole() {
        KeyCloakConfiguration configuration = GeoStoreContext.bean(KeyCloakConfiguration.class);
        Role role = configuration != null ? configuration.getAuthenticatedDefaultRole() : null;
        setRole(role);
    }

    private GrantedAuthority mapAuthority(String name) {
        String authName = mapStringAuthority(name);
        if (authName==null) return null;
        return new SimpleGrantedAuthority(authName);
    }

    private String mapStringAuthority(String name) {
        name = name.toUpperCase();
        String authName;
        if (roleMappings == null) authName = defaultRoleMatch(name);
        else authName = userMappingsMatch(name);
        if (authName == null) {
            // if not a role then is a group.
            authName = name;
            if (groupMappings!= null)
                authName=groupMappings.get(authName);
            if (authName==null && !dropUnmapped)
                authName=name;
            if (authName != null)
                addGroup(authName);
        }
        return authName;
    }

    private String userMappingsMatch(String name) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.info("Using the configured role mappings..");
        }
        String result = roleMappings.get(name);
        if (result != null) {
            try {
                Role role = Role.valueOf(result);
                if (getRole() == null || role.ordinal() < getRole().ordinal()) setRole(role);
                result = ROLE_PREFIX + result;
            } catch (Exception e) {
                String message = "The value " + result + " set to match role " + name + " is not a valid Role. You should use one of ADMIN,USER,GUEST";
                LOGGER.error(message, e);
                throw new RuntimeException(message);
            }
        }
        return result;
    }

    private String defaultRoleMatch(String name) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.info("Matching the keycloak role to geostore roles based on name equality...");
        }
        String result = null;
        if (name.equalsIgnoreCase(Role.ADMIN.name())) {
            result = ROLE_PREFIX + Role.ADMIN.name();
            setRole(Role.ADMIN);
        } else if (name.equalsIgnoreCase(Role.USER.name())) {
            result = ROLE_PREFIX + Role.USER.name();
            setRole(Role.USER);
        } else if (name.equalsIgnoreCase(Role.GUEST.name())) {
            result = ROLE_PREFIX + Role.GUEST.name();
            setRole(Role.GUEST);
        }
        return result;
    }

    /**
     * @return the found UserGroups.
     */
    public Set<UserGroup> getGroups() {
        if (groups == null) return new HashSet<>();
        return groups;
    }

    /**
     * @return the found role.
     */
    Role getRole() {
        return role;
    }

    private void setRole(Role role) {
        if (this.role == null) this.role = role;
        else if (this.role.ordinal() > role.ordinal()) this.role = role;
    }

    private void addGroup(String groupName) {
        if (this.groups == null) this.groups = new HashSet<>();
        UserGroup userGroup = new UserGroup();
        userGroup.setGroupName(groupName);
        userGroup.setId(Long.valueOf(idCounter));
        idCounter++;
        userGroup.setEnabled(true);
        this.groups.add(userGroup);
    }

    int getIdCounter() {
        return idCounter;
    }
}
