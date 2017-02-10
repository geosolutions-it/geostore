/* ====================================================================
 *
 * Copyright (C) 2015 GeoSolutions S.A.S.
 * http://www.geo-solutions.it
 *
 * GPLv3 + Classpath exception
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 *
 * ====================================================================
 *
 * This software consists of voluntary contributions made by developers
 * of GeoSolutions.  For more information on GeoSolutions, please see
 * <http://www.geo-solutions.it/>.
 *
 */
package it.geosolutions.geostore.services.rest.security;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.UserSessionService;

/**
 * Token based authentication filter that looks for the token in a user session service.
 * 
 * The attribute name is configurable (defaults to UUID).
 * 
 * @author Lorenzo Natali
 *
 */
public class SessionTokenAuthenticationFilter extends TokenAuthenticationFilter {
    
    private final static Logger LOGGER = Logger.getLogger(SessionTokenAuthenticationFilter.class);
    
    @Autowired
    UserSessionService userSessionService;

	@Autowired
    UserService userService;

    @Override
    protected Authentication checkToken(String token) {
    	if (userSessionService == null) {
    		return null;
    	}
    	User ud = userSessionService.getUserData(token);
    	if(ud != null) {
			User user;
			user = userService.get((Long) ud.getId());
			if (user != null) {
				return createAuthenticationForUser(user);
			}
    	}
        return null;
    }


	public UserSessionService getUserSessionService() {
		return userSessionService;
	}

	public void setUserSessionService(UserSessionService userSessionService) {
		this.userSessionService = userSessionService;
	}

	public UserService getUserService() {
		return userService;
	}

	public void setUserService(UserService userService) {
		this.userService = userService;
	}
}
