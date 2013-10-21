package it.geosolutions.geostore.services.rest;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.rest.model.UserList;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 * Advanced GeoStore client for user management
 * @author Lorenzo Natali
 *
 */
public class AdministratorGeoStoreClient extends GeoStoreClient {
	
	public User getUser(long id) {
		return getBaseWebResource("users", "user", id).get(User.class);

	}
	public User getUser(long id,Boolean includeAttributes) {
		WebResource wr = getBaseWebResource("users", "user", id);
		MultivaluedMap<String,String> queryParams = new MultivaluedMapImpl();

		if(includeAttributes!=null){
						  
			   queryParams.add("includeattributes", includeAttributes.toString());

		}
		return wr.queryParams(queryParams).get(User.class);

	}

	public UserList getUsers() {
		return getBaseWebResource("users").get(UserList.class);
	}

	public UserList getUsers(Integer page, Integer entries) {
		WebResource wr = getBaseWebResource("users");
		wr.queryParam("page", page.toString());
		wr.queryParam("entries", entries.toString());
		return wr.header("Content-Type", MediaType.TEXT_XML)
				.accept(MediaType.TEXT_XML).get(UserList.class);
	}

	//TODO move it to the base client to allow login
	public User getUserDetails() {
		return getBaseWebResource("users", "user", "details").get(
				User.class);
	}

	public Long insert(User user) {
		// WebResource wr = getBaseWebResource();
		//
		// String sid = wr.path("resources")
		String sid = getBaseWebResource("users")
				.header("Content-Type", MediaType.TEXT_XML)
				.accept(MediaType.TEXT_PLAIN).post(String.class, user);

		return Long.parseLong(sid);
	}

	public void deleteUser(Long id) {
		getBaseWebResource("users", "user", id).delete();
	}

	public void update(Long id, User user) {
		getBaseWebResource("users", "user", id).header("Content-Type",
				MediaType.TEXT_XML).put(user);
	}
}
