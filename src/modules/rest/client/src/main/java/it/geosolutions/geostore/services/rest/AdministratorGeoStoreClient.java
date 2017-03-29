package it.geosolutions.geostore.services.rest;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.rest.model.RESTUserGroup;
import it.geosolutions.geostore.services.rest.model.ShortResourceList;
import it.geosolutions.geostore.services.rest.model.UserGroupList;
import it.geosolutions.geostore.services.rest.model.UserList;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 * Advanced GeoStore client for user management
 * 
 * @author Lorenzo Natali
 * @author DamianoG
 * 
 */
public class AdministratorGeoStoreClient extends GeoStoreClient {

    // ==========================================================================
    // === USERS MANAGEMENT
    // ==========================================================================
    
    public User getUser(long id) {
        return getBaseWebResource("users", "user", id).accept(MediaType.TEXT_XML).get(User.class);

    }
    
    public User getUser(String name) {
        return getBaseWebResource("users", "search", name).accept(MediaType.TEXT_XML).get(User.class);

    }

    public User getUser(long id, Boolean includeAttributes) {
        WebResource wr = getBaseWebResource("users", "user", id);
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();

        if (includeAttributes != null) {

            queryParams.add("includeattributes", includeAttributes.toString());

        }
        return wr.queryParams(queryParams).get(User.class);

    }

    public UserList getUsers() {
        return getBaseWebResource("users").header("Content-Type", MediaType.TEXT_XML).accept(MediaType.TEXT_XML).get(UserList.class);
    }

    public UserList getUsers(Integer page, Integer entries) {
        WebResource wr = getBaseWebResource("users");
        return wr.queryParam("page", page.toString())
                 .queryParam("entries", entries.toString())
                 .header("Content-Type", MediaType.TEXT_XML)
                 .accept(MediaType.TEXT_XML)
                 .get(UserList.class);
    }

    // TODO move it to the base client to allow login
    public User getUserDetails() {
        return getBaseWebResource("users", "user", "details").get(User.class);
    }

    public Long insert(User user) {
        // WebResource wr = getBaseWebResource();
        //
        // String sid = wr.path("resources")
        String sid = getBaseWebResource("users").header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_PLAIN).post(String.class, user);

        return Long.parseLong(sid);
    }

    public void deleteUser(Long id) {
        getBaseWebResource("users", "user", id).delete();
    }

    public void update(Long id, User user) {
        getBaseWebResource("users", "user", id).header("Content-Type", MediaType.TEXT_XML)
                .put(user);
    }
    
    // ==========================================================================
    // === USER GROUPS MANAGEMENT
    // ==========================================================================

    public long insertUserGroup(UserGroup usergroup) {
        String sid = getBaseWebResource("usergroups").header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_PLAIN).post(String.class, usergroup);

        return Long.parseLong(sid);
    }

    public void deleteUserGroup(long usergroupId) {
        getBaseWebResource("usergroups", "group", usergroupId).delete();
    }

    public void assignUserGroup(long userId, long usergroupId) {
        getBaseWebResource("usergroups", "group", userId, usergroupId).post();
    }

    public void deassignUserGroup(long userId, long usergroupId) {
    	 getBaseWebResource("usergroups", "group", userId, usergroupId).delete();
		
	}
    
    public RESTUserGroup getUserGroup(long usergroupId){
    	return getBaseWebResource("usergroups", "group", usergroupId).get(RESTUserGroup.class);
    }
    
    public UserGroupList getUserGroups(Integer page, Integer entries, boolean all) {
        WebResource wr = getBaseWebResource("usergroups");
        wr = wr.queryParam("page", page.toString());
        wr = wr.queryParam("entries", entries.toString());
        wr = wr.queryParam("all", ""+all);
        return wr.header("Content-Type", MediaType.TEXT_XML).accept(MediaType.TEXT_XML)
                .get(UserGroupList.class);
    }

    public ShortResourceList updateSecurityRules(ShortResourceList resourcesToSet, Long groupId,
            boolean canRead, boolean canWrite) {
        WebResource wr = getBaseWebResource("usergroups","update_security_rules", groupId, canRead, canWrite);
        ShortResourceList updatedResources = wr.header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML).put(ShortResourceList.class, resourcesToSet);
        return updatedResources;
    }

	public RESTUserGroup getUserGroup(String name) {
		return getBaseWebResource("usergroups", "group", "name",name).get(RESTUserGroup.class);
		
	}

	
}
