/* ====================================================================
 *
 * Copyright (C) 2017 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest;


import java.text.ParseException;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;

import org.springframework.security.access.annotation.Secured;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.rest.model.SessionToken;

public interface RESTSessionService {
	
	/**
     * Gets the User object associated to the given sessionId (if it exists).
     * 
     * @param sessionId
     * @param refresh flag to automatically refresh the session (only if enabled)
     * @return
     */
	@GET
	@Path("/user/{sessionId}")
	@Produces({MediaType.APPLICATION_JSON})
	@Secured({ "ROLE_ADMIN", "ROLE_USER", "ROLE_ANONYMOUS" })
	User getUser(
			@PathParam("sessionId") String sessionId,
			@DefaultValue("true") @QueryParam("refresh") boolean refresh);
	
	 /**
     * Gets the username associated to the given sessionId (if it exists).
     * 
     * @param sessionId
     * @param refresh flag to automatically refresh the session (only if enabled)
     * @return
     */
	@GET
    @Path("/username/{sessionId}")
	@Produces({MediaType.TEXT_PLAIN})
	@Secured({ "ROLE_ADMIN", "ROLE_USER", "ROLE_ANONYMOUS" })
    public String getUserName(
    		@PathParam("sessionId") String sessionId,
    		@DefaultValue("true") @QueryParam("refresh") boolean refresh);
	
	/**
     * Creates a new session for the User in SecurityContext.
     * 
     * @return the session key
     * @throws ParseException 
     */
    
    @PUT
    @Path("/")
    @Produces({MediaType.TEXT_PLAIN})
    @Secured({ "ROLE_ADMIN", "ROLE_USER" })
    public String createSession(
    		@DefaultValue("") @QueryParam("expires") String expires, @Context SecurityContext sc) throws ParseException;
    
	/**
     * Creates a new session for the User in SecurityContext.
     * 
     * @return The session token with expiring time (in seconds and refresh token.
     * @throws ParseException 
     */
    
    @POST
    @Path("/login")
    @Produces({MediaType.APPLICATION_JSON})
    @Secured({ "ROLE_ADMIN", "ROLE_USER" })
    public SessionToken login(@Context SecurityContext sc) throws ParseException;
    
	/**
     * Refresh the session token
     * 
     * @param sessionId the current session token
     * @param refreshToken the token that allow you to refresh the session
     * 
     * @return the new session token with the new informations
     * @throws ParseException 
     */
    
    @POST
    @Path("/refresh/{sessionId}/{refreshToken}")
    @Produces({MediaType.APPLICATION_JSON})
    @Secured({ "ROLE_ADMIN", "ROLE_USER" })
    public SessionToken refresh(@Context SecurityContext sc, @PathParam("sessionId") String sessionId, @PathParam("refreshToken") String refreshToken)  throws ParseException;
    /**
     * Removes the given session.
     * 
     * @return
     */
    @DELETE
    @Path("/{sessionId}")
    @Secured({ "ROLE_ADMIN", "ROLE_USER" })
    public void removeSession(@PathParam("sessionId") String sessionId);
    
    /**
     * Removes all sessions.
     * 
     * @return
     */
    @DELETE
    @Path("/")
    @Secured({ "ROLE_ADMIN" })
    public void clear();
}
