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
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.rest.exception.InternalErrorWebEx;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.SecurityContext;

import org.apache.log4j.Logger;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.GrantedAuthorityImpl;

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
        if (sc == null)
            throw new InternalErrorWebEx("Missing auth info");
        else {
            Principal principal = sc.getUserPrincipal();
            if (principal == null) {
                // If I'm here I'm sure that the service is running is allowed for the unauthenticated users
                // due to service-based authorization step that uses annotations on services declaration (seee module geostore-rest-api). 
                // So I'm going to create a Principal to be used during resources-based authorization.
                principal = createGuestPrincipal();
            }
            if (!(principal instanceof UsernamePasswordAuthenticationToken)) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Mismatching auth principal");
                }
                throw new InternalErrorWebEx("Mismatching auth principal (" + principal.getClass()
                        + ")");
            }

            UsernamePasswordAuthenticationToken usrToken = (UsernamePasswordAuthenticationToken) principal;

            User user = new User();
            user.setName(usrToken.getName());
            for (GrantedAuthority authority : usrToken.getAuthorities()) {
                if (authority != null) {
                    if (authority.getAuthority() != null
                            && authority.getAuthority().contains("ADMIN"))
                        user.setRole(Role.ADMIN);

                    if (authority.getAuthority() != null
                            && authority.getAuthority().contains("USER") && user.getRole() == null)
                        user.setRole(Role.USER);

                    if (user.getRole() == null)
                        user.setRole(Role.GUEST);
                }
            }

            LOGGER.info("Accessing service with user " + user.getName() + " and role "
                    + user.getRole());

            return user;
        }
    }
    
    /**
     * Creates a Guest principal with Username="guest" password="" and role ROLE_GUEST.
     * The guest principal should be used with unauthenticated users.
     * 
     * @return the Principal instance
     */
    public static Principal createGuestPrincipal(){
        List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
        authorities.add(new GrantedAuthorityImpl("ROLE_GUEST"));
        if (LOGGER.isDebugEnabled()){
            LOGGER.debug("Missing auth principal, set it to the guest One...");
        }
        Principal principal = new UsernamePasswordAuthenticationToken("guest","", authorities);
        return principal;
    }
}
