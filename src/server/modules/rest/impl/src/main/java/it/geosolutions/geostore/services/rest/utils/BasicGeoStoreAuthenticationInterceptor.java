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

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;

import org.apache.cxf.interceptor.security.AccessDeniedException;
import org.apache.cxf.message.Message;

/**
 * 
 * Class GeoStoreAuthenticationInterceptor. Starting point was
 * JAASLoginInterceptor.
 * 
 * @author ETj (etj at geo-solutions.it)
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * @author adiaz (alejandro.diaz at geo-solutions.it)
 */
public class BasicGeoStoreAuthenticationInterceptor extends
		AbstractGeoStoreAuthenticationInterceptor {	
	
	private UserService userService;
	
	/**
	 * @param userService
	 *            the userService to set
	 */
	public void setUserService(UserService userService) {
		this.userService = userService;
	}
	
	/**
	 * Obtain an user from his username
	 * 
	 * @param username
	 *            of the user
	 * @param message
	 *            intercepted
	 * 
	 * @return user identified with the username
	 */
	protected User getUser(String username, Message message) {
		User user = null;
		try {
			// Search on db
			user = userService.get(username);
		} catch (NotFoundServiceEx e) {
			if (LOGGER.isInfoEnabled()){
				LOGGER.info("Requested user not found: " + username);
			}
			// throw exception
			throw new AccessDeniedException("Not authorized");
		}
	
		return user;
	
	}

}