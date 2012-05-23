/* ====================================================================
 *
 * Copyright (C) 2012 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.impl;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.rest.exception.*;
import it.geosolutions.geostore.services.rest.utils.GeoStorePrincipal;

import java.security.Principal;

import javax.ws.rs.core.SecurityContext;

import org.apache.log4j.Logger;

/** 
 * Class RESTServiceImpl.
 *
 * @author ETj (etj at geo-solutions.it)
 */
public class RESTServiceImpl {
	
    private final static Logger LOGGER = Logger.getLogger(RESTServiceImpl.class);


    /**
     * @return User - The authenticated user that is accessing this service, or null if guest access.
     */
    protected User extractAuthUser(SecurityContext sc) throws InternalErrorWebEx {
        if(sc == null)
            throw new InternalErrorWebEx("Missing auth info");
        else {
            Principal principal = sc.getUserPrincipal();
            if(principal == null){
    			if(LOGGER.isInfoEnabled())
    				LOGGER.info("Missing auth principal");
    			throw new InternalErrorWebEx("Missing auth principal");
            }
                
            if( ! (principal instanceof GeoStorePrincipal )){
    			if(LOGGER.isInfoEnabled())
    				LOGGER.info("Missing auth principal");
    			throw new InternalErrorWebEx("Mismatching auth principal (" + principal.getClass() + ")");
            }

            GeoStorePrincipal gsp = (GeoStorePrincipal)principal;
            
            //
            // may be null if guest
            //
            User user = gsp.getUser(); 

            LOGGER.info("Accessing service with user " + (user == null ? "GUEST" : user.getName()));
            return user;
        }
    }

}
