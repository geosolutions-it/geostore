package it.geosolutions.geostore.services.rest;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.rest.model.RESTUserGroup;
import it.geosolutions.geostore.services.rest.model.ShortResourceList;
import it.geosolutions.geostore.services.rest.model.UserGroupList;
import it.geosolutions.geostore.services.rest.model.UserList;
import jakarta.ws.rs.core.MediaType;
import org.apache.cxf.jaxrs.client.WebClient;

/**
 * Advanced GeoStore client for user management.
 *
 * @author Lorenzo Natali
 * @author DamianoG
 */
@SuppressWarnings(
        "PMD.CloseResource") // CXF WebClient is single-use here; explicit close is not idiomatic
public class AdministratorGeoStoreClient extends GeoStoreClient {

    // ==========================================================================
    // === USERS MANAGEMENT
    // ==========================================================================

    public User getUser(long id) {
        return getBaseWebClient("users", "user", id).accept(MediaType.TEXT_XML).get(User.class);
    }

    public User getUser(String name) {
        return getBaseWebClient("users", "search", name).accept(MediaType.TEXT_XML).get(User.class);
    }

    public User getUser(long id, Boolean includeAttributes) {
        WebClient wb = getBaseWebClient("users", "user", id);
        if (includeAttributes != null) {
            wb = wb.query("includeattributes", includeAttributes.toString());
        }
        return wb.get(User.class);
    }

    public UserList getUsers() {
        return getBaseWebClient("users")
                .type(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(UserList.class);
    }

    public UserList getUsers(Integer page, Integer entries) {
        return getBaseWebClient("users")
                .query("page", page.toString())
                .query("entries", entries.toString())
                .type(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(UserList.class);
    }

    /** Returns the currently-authenticated user. TODO: move to base client to allow login. */
    public User getUserDetails() {
        return getBaseWebClient("users", "user", "details").get(User.class);
    }

    public Long insert(User user) {
        String sid =
                getBaseWebClient("users")
                        .type(MediaType.TEXT_XML)
                        .accept(MediaType.TEXT_PLAIN)
                        .post(user, String.class);
        return Long.parseLong(sid);
    }

    public void deleteUser(Long id) {
        getBaseWebClient("users", "user", id).delete();
    }

    public void update(Long id, User user) {
        getBaseWebClient("users", "user", id).type(MediaType.TEXT_XML).put(user);
    }

    // ==========================================================================
    // === USER GROUPS MANAGEMENT
    // ==========================================================================

    public long insertUserGroup(UserGroup usergroup) {
        String sid =
                getBaseWebClient("usergroups")
                        .type(MediaType.TEXT_XML)
                        .accept(MediaType.TEXT_PLAIN)
                        .post(usergroup, String.class);
        return Long.parseLong(sid);
    }

    public void deleteUserGroup(long usergroupId) {
        getBaseWebClient("usergroups", "group", usergroupId).delete();
    }

    public void assignUserGroup(long userId, long usergroupId) {
        getBaseWebClient("usergroups", "group", userId, usergroupId).post(null);
    }

    public void deassignUserGroup(long userId, long usergroupId) {
        getBaseWebClient("usergroups", "group", userId, usergroupId).delete();
    }

    public RESTUserGroup getUserGroup(long usergroupId) {
        return getBaseWebClient("usergroups", "group", usergroupId).get(RESTUserGroup.class);
    }

    public UserGroupList getUserGroups(Integer page, Integer entries, boolean all) {
        return getBaseWebClient("usergroups")
                .query("page", page.toString())
                .query("entries", entries.toString())
                .query("all", Boolean.toString(all))
                .type(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(UserGroupList.class);
    }

    public ShortResourceList updateSecurityRules(
            ShortResourceList resourcesToSet, Long groupId, boolean canRead, boolean canWrite) {
        return getBaseWebClient("usergroups", "update_security_rules", groupId, canRead, canWrite)
                .type(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .put(resourcesToSet, ShortResourceList.class);
    }

    public RESTUserGroup getUserGroup(String name) {
        return getBaseWebClient("usergroups", "group", "name", name).get(RESTUserGroup.class);
    }
}
