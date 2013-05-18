package it.geosolutions.geostore.services.rest;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;

import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.rest.model.RESTUser;
import it.geosolutions.geostore.services.rest.model.UserList;

import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class AdministratorGeostoreClientTest {

	AdministratorGeoStoreClient geoStoreClient;
    private final static Logger LOGGER = Logger.getLogger(AdministratorGeostoreClientTest.class);

	protected AdministratorGeoStoreClient createAdministratorClient() {
		geoStoreClient = new AdministratorGeoStoreClient();
		geoStoreClient
				.setGeostoreRestUrl("http://localhost:9191/geostore/rest");
		geoStoreClient.setUsername("admin");
		geoStoreClient.setPassword("admin");
		return geoStoreClient;
	}

	protected boolean pingGeoStore(GeoStoreClient client) {
		try {
			client.getCategories();
			return true;
		} catch (Exception ex) {
			LOGGER.debug("Error connecting to GeoStore", ex);
			// ... and now for an awful example of heuristic.....
			Throwable t = ex;
			while (t != null) {
				if (t instanceof ConnectException) {
					LOGGER.warn("Testing GeoStore is offline");
					return false;
				}
				t = t.getCause();
			}
			throw new RuntimeException("Unexpected exception: "
					+ ex.getMessage(), ex);
		}
	}

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}

	@Before
	public void before() throws Exception {
		geoStoreClient = createAdministratorClient();
		assumeTrue(pingGeoStore(geoStoreClient));

		// CLEAR
		//removeAllResources(client);
		//removeAllCategories(client);
	}

	@Test
	public void getIdTest() {

		// User user = geoStoreClient.getUser(1);
		try {
			RESTUser userd = geoStoreClient.getUserDetails();
			System.out.println(userd.getId());
		} catch (Exception e) {
			fail();
		}

	}

	@Test
	public void getUsersTest() {

		// User user = geoStoreClient.getUser(1);
		try {
			UserList users = geoStoreClient.getUsers(1, 1);
			System.out.println(users.getList().get(0).getName());
			users = geoStoreClient.getUsers(2, 1);
			System.out.println(users.getList().get(0).getName());
		} catch (Exception e) {
			fail();
		}

	}

	@Test
	public void createUserTest() {

		// User user = geoStoreClient.getUser(1);
		try {
			User user = new User();
			user.setName("testuser1");
			user.setRole(Role.USER);
			user.setNewPassword("testpw");
			UserAttribute email = new UserAttribute();
			email.setName("email");
			email.setValue("test@geo-solutions.it");

			List<UserAttribute> attrs = new ArrayList<UserAttribute>();
			attrs.add(email);
			user.setAttribute(attrs);
			Long id = geoStoreClient.insert(user);
			System.out.println(id);
			RESTUser us = geoStoreClient.getUser(id);
			user.getName().equals("testuser");

		} catch (Exception e) {
			fail();
		}
	}

	@Test
	public void deleteUserTest() {

		// geoStoreClient.deleteUser(new Long(8));
	}

	@Test
	public void updateUserTest() {

		// User user = geoStoreClient.getUser(1);
		try {
			User user = new User();
			user.setName("testuser1");
			user.setRole(Role.USER);
			user.setNewPassword("testpw");
			UserAttribute email = new UserAttribute();
			email.setName("email");
			email.setValue("test1@geo-solutions.it");

			List<UserAttribute> attrs = new ArrayList<UserAttribute>();
			attrs.add(email);
			user.setAttribute(attrs);
			geoStoreClient.update(new Long(3), user);
			// System.out.println(id);
			// RESTUser us = geoStoreClient.getUser(id);
			user.getName().equals("testuser");

		} catch (Exception e) {
			fail();
		}
	}

}
