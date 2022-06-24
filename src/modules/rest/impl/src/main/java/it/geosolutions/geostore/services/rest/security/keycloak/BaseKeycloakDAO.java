package it.geosolutions.geostore.services.rest.security.keycloak;

import com.googlecode.genericdao.search.ISearch;
import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UsersResource;

import java.util.Map;

/**
 * Base DAO class for keycloak based repository.
 */
public abstract class BaseKeycloakDAO {

    protected KeycloakAdminClientConfiguration adminClientConfiguration;

    public BaseKeycloakDAO(KeycloakAdminClientConfiguration adminClientConfiguration){
        this.adminClientConfiguration=adminClientConfiguration;
    }

    /**
     * Converts the ISearch to a {@link KeycloakQuery}.
     * @param search the search with filters to convert.
     * @return a {@link KeycloakQuery} representation of the search.
     */
    protected KeycloakQuery toKeycloakQuery(ISearch search){
        return new KeycloakSearchMapper().keycloackQuery(search);
    }

    /**
     * Get the Keycloak client instance.
     * @return the {@link Keycloak} REST client.
     */
    protected Keycloak keycloak(){
        return adminClientConfiguration.getKeycloak();
    }

    /**
     * Get the UsersResource client instance.
     * @param keycloak REST client instance.
     * @return the {@link UsersResource} REST client.
     */
    protected UsersResource getUsersResource(Keycloak keycloak){
        return keycloak.realm(adminClientConfiguration.getRealm()).users();
    }

    /**
     * Get the RolesResource client instance.
     * @param keycloak the {@link Keycloak} REST client instance.
     * @return the {@link RolesResource} REST client instance.
     */
    protected RolesResource getRolesResource(Keycloak keycloak){
        return keycloak.realm(adminClientConfiguration.getRealm()).roles();
    }

    /**
     * Close the REST client.
     * @param keycloak the {@link Keycloak} REST client instance.
     */
    protected void close(Keycloak keycloak){
        if(keycloak.isClosed())
            keycloak.close();
    }

    /**
     * Get the RoleMappings if any has been configured.
     * @return the role mappings or null if none was configured.
     */
    protected Map<String,String> getRoleMappings(){
        KeyCloakConfiguration configuration=GeoStoreContext.bean(KeyCloakConfiguration.class);
        if (configuration==null) return null;
        return configuration.getRoleMappings();
    }
}
