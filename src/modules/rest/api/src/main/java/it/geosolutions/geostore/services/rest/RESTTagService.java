/* ====================================================================
 *
 * Copyright (C) 2007 - 2012 GeoSolutions S.A.S.
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

import it.geosolutions.geostore.core.model.Tag;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.TagList;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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

@Path("tag")
public interface RESTTagService {

    /**
     * @param tag
     * @return long
     * @throws BadRequestServiceEx
     */
    @POST
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.TEXT_PLAIN})
    @Secured({"ROLE_ADMIN"})
    long insert(@Context SecurityContext sc, @Multipart("tag") Tag tag) throws BadRequestServiceEx;

    /**
     * @param page
     * @param entries
     * @return Tag
     * @throws BadRequestWebEx
     */
    @GET
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_JSON})
    @Secured({"ROLE_ADMIN"})
    TagList getAll(
            @Context SecurityContext sc,
            @QueryParam("page") Integer page,
            @QueryParam("entries") Integer entries)
            throws BadRequestWebEx;

    /**
     * @param id
     * @return Tag
     * @throws NotFoundWebEx
     */
    @GET
    @Path("{id}")
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_JSON})
    @Secured({"ROLE_ADMIN"})
    Tag get(@Context SecurityContext sc, @PathParam("id") long id) throws NotFoundWebEx;

    /**
     * @param id
     * @param tag
     * @return long
     * @throws NotFoundWebEx
     */
    @PUT
    @Path("{id}")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.TEXT_PLAIN})
    @Secured({"ROLE_ADMIN"})
    long update(@Context SecurityContext sc, @PathParam("id") long id, @Multipart("tag") Tag tag)
            throws NotFoundWebEx;

    /**
     * @param id
     * @throws NotFoundWebEx
     */
    @DELETE
    @Path("{id}")
    @Secured({"ROLE_ADMIN"})
    void delete(@Context SecurityContext sc, @PathParam("id") long id) throws NotFoundWebEx;
}
