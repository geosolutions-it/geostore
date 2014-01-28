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

import static org.junit.Assert.fail;
import it.geosolutions.geostore.core.model.User;

import org.apache.cxf.interceptor.security.AccessDeniedException;
import org.junit.Test;

/**
 * 
 * Test for GeoStoreAuthenticationInterceptor.
 * 
 * @author adiaz (alejandro.diaz at geo-solutions.it)
 */
public class BasicGeoStoreAuthenticationInterceptorTest extends BaseAuthenticationInterceptorTest{
	
	private final String USERNAME = "test";
	private final String PASSWORD = "test";

	/**
	 * Access denied for a new user
	 */
	@Test(expected = AccessDeniedException.class)
	public void testNotCreatesUser() {
		GeoStoreAuthenticationInterceptor interceptor = new GeoStoreAuthenticationInterceptor();
		interceptor.setUserService(userService);
		interceptor.handleMessage(getMockedMessage(USERNAME, PASSWORD, null));
	}

	/**
	 * When the user exists, shouldn't throw any exception
	 */
	@Test
	public void testCreatedUser() {
		GeoStoreAuthenticationInterceptor interceptor = new GeoStoreAuthenticationInterceptor();
		interceptor.setUserService(userService);
		User user = new User();
		user.setName(USERNAME);
		user.setNewPassword(PASSWORD);
		try {
			userService.insert(user);
		} catch (Exception e) {
			fail("Couldn't create user");
		}
		interceptor.handleMessage(getMockedMessage(USERNAME, PASSWORD, null));
	}

}