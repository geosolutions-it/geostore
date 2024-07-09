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
import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UsersResource;

/** Base DAO class for keycloak based repository. */
public abstract class BaseKeycloakDAO {

    protected KeycloakAdminClientConfiguration adminClientConfiguration;

    public BaseKeycloakDAO(KeycloakAdminClientConfiguration adminClientConfiguration) {
        this.adminClientConfiguration = adminClientConfiguration;
    }

    /**
     * Converts the ISearch to a {@link KeycloakQuery}.
     *
     * @param search the search with filters to convert.
     * @return a {@link KeycloakQuery} representation of the search.
     */
    protected KeycloakQuery toKeycloakQuery(ISearch search) {
        return new KeycloakSearchMapper().keycloackQuery(search);
    }

    /**
     * Get the Keycloak client instance.
     *
     * @return the {@link Keycloak} REST client.
     */
    protected Keycloak keycloak() {
        return adminClientConfiguration.getKeycloak();
    }

    /**
     * Get the UsersResource client instance.
     *
     * @param keycloak REST client instance.
     * @return the {@link UsersResource} REST client.
     */
    protected UsersResource getUsersResource(Keycloak keycloak) {
        return keycloak.realm(adminClientConfiguration.getRealm()).users();
    }

    /**
     * Get the RolesResource client instance.
     *
     * @param keycloak the {@link Keycloak} REST client instance.
     * @return the {@link RolesResource} REST client instance.
     */
    protected RolesResource getRolesResource(Keycloak keycloak) {
        return keycloak.realm(adminClientConfiguration.getRealm()).roles();
    }

    /**
     * Close the REST client.
     *
     * @param keycloak the {@link Keycloak} REST client instance.
     */
    protected void close(Keycloak keycloak) {
        if (keycloak.isClosed()) keycloak.close();
    }

    /**
     * Get an authorities mapper instance.
     *
     * @return the authorities' mapper.
     */
    protected GeoStoreKeycloakAuthoritiesMapper getAuthoritiesMapper() {
        KeyCloakConfiguration configuration = GeoStoreContext.bean(KeyCloakConfiguration.class);
        if (configuration != null)
            return new GeoStoreKeycloakAuthoritiesMapper(
                    configuration.getRoleMappings(),
                    configuration.getGroupMappings(),
                    configuration.isDropUnmapped());
        else return new GeoStoreKeycloakAuthoritiesMapper(null, null, false);
    }
}
