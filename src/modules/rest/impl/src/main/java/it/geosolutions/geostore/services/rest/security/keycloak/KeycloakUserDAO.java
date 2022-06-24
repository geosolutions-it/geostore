package it.geosolutions.geostore.services.rest.security.keycloak;

import com.googlecode.genericdao.search.ISearch;
import it.geosolutions.geostore.core.dao.UserDAO;
import it.geosolutions.geostore.core.dao.UserGroupDAO;
import it.geosolutions.geostore.core.model.User;
import org.apache.log4j.Logger;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keycloak implementation for a {@link UserDAO}.
 * Supports basic search operations. Currently it doesn't support querying and updating by id.
 */
public class KeycloakUserDAO extends BaseKeycloakDAO implements UserDAO {

    private final static Logger LOGGER = Logger.getLogger(KeycloakUserDAO.class);

    public KeycloakUserDAO (KeycloakAdminClientConfiguration adminClientConfiguration){
        super(adminClientConfiguration);
    }
    @Override
    public List<User> findAll() {
        Keycloak keycloak=keycloak();
        try {
            UsersResource ur=getUsersResource(keycloak);
            List<UserRepresentation> userRepresentations = ur.list();
            return toUsers(userRepresentations,ur);
        }  catch (NotFoundException e){
            LOGGER.warn("No users were found.",e);
            return null;
        } finally {
            close(keycloak);
        }
    }

    @Override
    public User find(Long id) {
        return null;
    }

    @Override
    public void persist(User... users) {
        Keycloak keycloak=keycloak();
        try {
            List<UserRepresentation> representations = toUserRepresentation(users);
            UsersResource usersResource = getUsersResource(keycloak);
            representations.forEach(r -> usersResource.create(r));
        } finally {
            close(keycloak);
        }
    }

    @Override
    public User[] save(User... users) {
        Keycloak keycloak=keycloak();
        try {
            List<UserRepresentation> representations = toUserRepresentation(users);
            UsersResource usersResource = getUsersResource(keycloak);
            representations.forEach(r -> usersResource.create(r));
        } finally {
            close(keycloak);
        }
        return users;
    }

    @Override
    public User merge(User user) {
        return null;
    }

    @Override
    public boolean remove(User user) {
        Keycloak keycloak=keycloak();
        try {
            UsersResource ur = getUsersResource(keycloak);
            List<UserRepresentation> userRep = ur.search(user.getName(), true);
            if (userRep.size() == 0) return false;
            Response response = ur.delete(userRep.get(0).getId());
            if (response.getStatus() == HttpStatus.NO_CONTENT.value()) return true;
            LOGGER.debug("Delete failed with response status " + response.getStatus());
            return false;
        }  catch (NotFoundException e){
            LOGGER.warn("No user found with name " + user.getName(),e);
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
    public List<User> search(ISearch search) {
        Keycloak keycloak=keycloak();
        try {
            KeycloakQuery query = toKeycloakQuery(search);
            if (LOGGER.isDebugEnabled()) LOGGER.debug("Executing the query " + query.toString());

            UsersResource ur = getUsersResource(keycloak);
            Collection<UserRepresentation> userRepresentations;
            if (query.isExact())
                userRepresentations = ur.search(query.getUserName(), query.getExact());
            else if (query.getGroupName() != null)
                userRepresentations = getRolesResource(keycloak).get(query.getGroupName()).getRoleUserMembers(query.getStartIndex(), query.getMaxResults());
            else
                userRepresentations = ur.search(
                        query.getUserName(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        search.getPage(), search.getMaxResults(), query.getEnabled(), false);
            return toUsers(userRepresentations,ur);
        }  catch (NotFoundException e){
            LOGGER.warn("No users were found",e);
            return null;
        } finally {
            close(keycloak);
        }
    }

    @Override
    public int count(ISearch search) {
        Keycloak keycloak=keycloak();
        try {
            KeycloakQuery query = toKeycloakQuery(search);
            UsersResource ur = getUsersResource(keycloak);
            if (LOGGER.isDebugEnabled()) LOGGER.debug("Executing the query " + query.toString());

            Integer count;
            if (query.getExact() != null && query.getExact().booleanValue())
                count = ur.count(null, null, null, null, query.getUserName());
            else count = ur.search(
                    query.getUserName(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    search.getPage(), search.getMaxResults(), query.getEnabled(), true).size();
            return count.intValue();
        }  catch (NotFoundException e){
            LOGGER.warn("No users were found",e);
            return 0;
        } finally {
            close(keycloak);
        }
    }

    private List<UserRepresentation> toUserRepresentation(User... users){
        if (LOGGER.isDebugEnabled()){
            LOGGER.debug("Converting User to UserRepresentation");
        }
        List<UserRepresentation> userList= new ArrayList<>();
        for (User user:users){
            UserRepresentation representation=new UserRepresentation();
            if (user.getNewPassword()!=null) {
                CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
                credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
                credentialRepresentation.setValue(user.getNewPassword());
                representation.setCredentials(Arrays.asList(credentialRepresentation));
            }
            user.setId(-1L);
            representation.setUsername(user.getName());
            userList.add(representation);
        }
        return userList;
    }

    private List<User> toUsers(Collection<UserRepresentation> userRepresentations, UsersResource ur){
        if (LOGGER.isDebugEnabled()){
            LOGGER.debug("Converting UserRepresentation to User");
        }
        List<User> users=new ArrayList<>();
        Map<String,String> roleMappings=getRoleMappings();
        for (UserRepresentation representation:userRepresentations){
            User user=new User();
            user.setId(1L);
            user.setName(representation.getUsername());
            user.setEnabled(representation.isEnabled());
            user.setTrusted(true);
            GeoStoreKeycloakAuthoritiesMapper mapper = new GeoStoreKeycloakAuthoritiesMapper(roleMappings);
            List<String> roles=ur.get(representation.getId()).roles().realmLevel().listEffective().stream().map(m->m.getName()).collect(Collectors.toList());
            mapper.mapAuthorities(roles);
            user.setRole(mapper.getRole());
            user.setGroups(mapper.getGroups());
            users.add(user);
        }
        return users;
    }
}
