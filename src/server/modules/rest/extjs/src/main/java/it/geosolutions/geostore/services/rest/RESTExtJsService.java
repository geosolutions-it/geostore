/*
 * $ Header: it.geosolutions.georepo.services.rest.RESTExtJsService,v. 0.1 9-set-2011 10.39.58 created by tobaro <tobia.dipisa at geo-solutions.it> $
 * $ Revision: 0.1 $
 * $ Date: 8-set-2011 10.39.58 $
 *
 * ====================================================================
 *
 * Copyright (C) 2007 - 2011 GeoSolutions S.A.S.
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

import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.model.ExtResourceList;
import it.geosolutions.geostore.services.model.ExtUserList;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.InternalErrorWebEx;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
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
 * Interface RESTExtJsService.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * 
 */
public interface RESTExtJsService {

    /**
     * @param sc
     * @param page
     * @param entries
     * @return String
     * @throws BadRequestWebEx
     */
    @GET
    @Path("/search/{nameLike}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Secured({ "ROLE_ADMIN", "ROLE_USER", "ROLE_GUEST" })
    String getAllResources(@Context SecurityContext sc, @PathParam("nameLike") String nameLike,
            @QueryParam("start") Integer start, @QueryParam("limit") Integer limit)
            throws BadRequestWebEx;

    @GET
    @Path("/search/category/{categoryName}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Secured({ "ROLE_ADMIN", "ROLE_USER", "ROLE_GUEST" })
    String getResourcesByCategory(@Context SecurityContext sc,
            @PathParam("categoryName") String categoryName, @QueryParam("start") Integer start,
            @QueryParam("limit") Integer limit) throws BadRequestWebEx, InternalErrorWebEx;

    /**
     * @param sc
     * @param filter
     * @param page
     * @param entries
     * @param includeAttributes
     * @return ResourceList
     * @throws BadRequestWebEx
     * @throws InternalErrorWebEx
     */
    @POST
    @GET
    @Path("/search/list")
    @Produces({ MediaType.TEXT_PLAIN, MediaType.TEXT_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    @Secured({ "ROLE_ADMIN", "ROLE_USER", "ROLE_GUEST" })
    ExtResourceList getExtResourcesList(@Context SecurityContext sc,
            @QueryParam("start") Integer start, @QueryParam("limit") Integer limit,
            @QueryParam("includeAttributes") @DefaultValue("false") boolean includeAttributes,
            @Multipart("filter") SearchFilter filter) throws BadRequestWebEx, InternalErrorWebEx;

    /**
     * @param sc
     * @param page
     * @param entries
     * @return String
     * @throws BadRequestWebEx
     */
    @GET
    @Path("/search/users/{nameLike}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Secured({ "ROLE_ADMIN"})
    ExtUserList getUsersList(@Context SecurityContext sc, @PathParam("nameLike") String nameLike,
            @QueryParam("start") Integer start, @QueryParam("limit") Integer limit,
            @QueryParam("includeAttributes") @DefaultValue("false") boolean includeAttributes)
            throws BadRequestWebEx;

}
