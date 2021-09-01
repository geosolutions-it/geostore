/*
 *  Copyright (C) 2007-2012 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest;

import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.RESTUserGroup;
import it.geosolutions.geostore.services.rest.model.ShortResourceList;
import it.geosolutions.geostore.services.rest.model.UserGroupList;

import javax.ws.rs.Consumes;
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

import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.springframework.security.access.annotation.Secured;

/**
 * @author DamianoG
 * 
 */
public interface RESTUserGroupService {

	
	
    @POST
    @Path("/")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.TEXT_XML,MediaType.APPLICATION_JSON })
    @Produces({ MediaType.TEXT_PLAIN })
    @Secured({ "ROLE_ADMIN" })
    long insert(@Context SecurityContext sc, @Multipart("userGroup") UserGroup userGroup)
            throws BadRequestWebEx;

    @DELETE
    @Path("/group/{id}")
    @Secured({ "ROLE_ADMIN" })
    void delete(@Context SecurityContext sc, @PathParam("id") long id) throws NotFoundWebEx;

    @GET
	@Path("/group/{id}")
    @Secured({ "ROLE_ADMIN" })
    @Produces({ MediaType.TEXT_XML, MediaType.APPLICATION_JSON })
    RESTUserGroup get(@Context SecurityContext sc, @PathParam("id") long id) throws NotFoundWebEx;
    
    @GET
	@Path("/group/name/{name}")
    @Secured({ "ROLE_ADMIN" })
    @Produces({ MediaType.TEXT_XML, MediaType.APPLICATION_JSON })
    RESTUserGroup get(@Context SecurityContext sc, @PathParam("id") String name) throws NotFoundWebEx;
    
    @POST
    @Path("/group/{userid}/{groupid}")
    @Secured({ "ROLE_ADMIN" })
    void assignUserGroup(@Context SecurityContext sc, @PathParam("userid") long userId, @PathParam("groupid") long groupId)
            throws NotFoundWebEx;
    
    @DELETE
    @Path("/group/{userid}/{groupid}")
    @Secured({ "ROLE_ADMIN" })
    void deassignUserGroup(@Context SecurityContext sc, @PathParam("userid") long userId, @PathParam("groupid") long groupId)
            throws NotFoundWebEx;
    
    @GET
    @Path("/")
    @Produces({ MediaType.TEXT_XML, MediaType.APPLICATION_JSON })
    @Secured({ "ROLE_ADMIN" })
    UserGroupList getAll(@Context SecurityContext sc, @QueryParam("page") Integer page,
            @QueryParam("entries") Integer entries, @QueryParam("all") @DefaultValue("false") boolean all, @QueryParam("users") @DefaultValue("true") boolean includeUsers) throws BadRequestWebEx;
    
    @PUT
    @Path("/update_security_rules/{groupId}/{canRead}/{canWrite}")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.TEXT_XML, MediaType.APPLICATION_JSON })    
    @Secured({ "ROLE_ADMIN" })
    ShortResourceList updateSecurityRules(@Context SecurityContext sc, @Multipart("resourcelist")ShortResourceList resourcesToSet, @PathParam("groupId") Long groupId, @PathParam("canRead") Boolean canRead, @PathParam("canWrite") Boolean canWrite) throws BadRequestWebEx, NotFoundWebEx;
}
