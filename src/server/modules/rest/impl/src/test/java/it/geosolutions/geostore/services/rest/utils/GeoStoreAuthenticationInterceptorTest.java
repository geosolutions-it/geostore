/*
 *  Copyright (C) 2007 - 2011 GeoSolutions S.A.S.
 *  http://www.geo-solutions.it
 * 
 *  GPLv3 + Classpath exception
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.geosolutions.geostore.services.rest.utils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.cxf.configuration.security.AuthorizationPolicy;
import org.apache.cxf.interceptor.security.AccessDeniedException;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * Test for GeoStoreAuthenticationInterceptor. Test different configurations for
 * the interceptor
 * 
 * @author adiaz (alejandro.diaz at geo-solutions.it)
 */
public class GeoStoreAuthenticationInterceptorTest {

	UserService userService;

	@Before
	public void init() {
		userService = new MockedUserService();
	}

	/**
	 * Access denied for a new user if the interceptor doesn't create new users
	 */
	@Test(expected = AccessDeniedException.class)
	public void testNotCreateUsers() {
		GeoStoreAuthenticationInterceptor interceptor = new GeoStoreAuthenticationInterceptor();
		interceptor.setAutoCreateUsers(false);
		interceptor.setUserService(userService);
		interceptor.handleMessage(getMockedMessage("test", "", null));
	}

	/**
	 * Create a user with a empty password
	 */
	@Test
	public void testCreateUsers() {
		GeoStoreAuthenticationInterceptor interceptor = new GeoStoreAuthenticationInterceptor();
		interceptor.setAutoCreateUsers(true);
		interceptor.setNewUsersPassword(NewPasswordStrategy.NONE);
		interceptor.setUserService(userService);
		interceptor.handleMessage(getMockedMessage("test", "", null));
		try {
			User user = userService.get("test");
			assertNotNull(user);
		} catch (NotFoundServiceEx e) {
			fail("Couldn't found user");
		}
	}

	/**
	 * Create a user with password as user name
	 */
	@Test
	public void testCreateUsersStrategyUserName() {
		GeoStoreAuthenticationInterceptor interceptor = new GeoStoreAuthenticationInterceptor();
		interceptor.setAutoCreateUsers(true);
		interceptor.setNewUsersPassword(NewPasswordStrategy.USERNAME);
		interceptor.setUserService(userService);
		interceptor.handleMessage(getMockedMessage("test2", "test2", null));
		try {
			User user = userService.get("test2");
			assertNotNull(user);
		} catch (NotFoundServiceEx e) {
			fail("Couldn't found user");
		}
	}

	/**
	 * Create a user with password from a header
	 */
	@Test
	public void testCreateUsersStrategyFromHeader() {
		GeoStoreAuthenticationInterceptor interceptor = new GeoStoreAuthenticationInterceptor();
		interceptor.setAutoCreateUsers(true);
		interceptor.setNewUsersPassword(NewPasswordStrategy.FROMHEADER);
		interceptor.setNewUsersPasswordHeader("newPassword");
		interceptor.setUserService(userService);
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("newPassword", "test3pass");
		interceptor.handleMessage(getMockedMessage("test3", "test3pass",
				headers));
		try {
			User user = userService.get("test3");
			assertNotNull(user);
		} catch (NotFoundServiceEx e) {
			fail("Couldn't found user");
		}
	}

	/**
	 * Mock a message to be handled by the interceptor
	 * 
	 * @param username
	 *            for the authorization policy
	 * @param password
	 *            for the authorization policy
	 * @param headers
	 *            for the request
	 * 
	 * @return Message to be handled
	 */
	private Message getMockedMessage(String username, String password,
			Map<String, String> headers) {
		MessageImpl messageImpl = (MessageImpl) new MessageImpl();
		AuthorizationPolicy policy = new AuthorizationPolicy();
		policy.setUserName(username);
		policy.setPassword(password);
		if (headers != null) {
			Map<String, List<String>> mockedHeaders = new HashMap<String, List<String>>();
			for (String key : headers.keySet()) {
				List<String> value = new LinkedList<String>();
				value.add(headers.get(key));
				mockedHeaders.put(key, value);
			}
			messageImpl.put(Message.PROTOCOL_HEADERS, mockedHeaders);
		}
		messageImpl.put(AuthorizationPolicy.class, policy);
		return messageImpl;
	}

}