package it.geosolutions.geostore.services.rest;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.rest.model.RESTUser;
import it.geosolutions.geostore.services.rest.model.UserList;

import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.client.WebResource;

/**
 * 
 * @author Lorenzo Natali
 *
 */
public class AdministratorGeoStoreClient extends GeoStoreClient {
	
	public RESTUser getUser(long id) {
		return getBaseWebResource("users", "user", id).get(RESTUser.class);

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

	public RESTUser getUserDetails() {
		return getBaseWebResource("users", "user", "details").get(
				RESTUser.class);
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
