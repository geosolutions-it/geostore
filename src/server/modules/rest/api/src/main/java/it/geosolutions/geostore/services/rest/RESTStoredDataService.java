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
package it.geosolutions.geostore.services.rest;

import it.geosolutions.geostore.core.model.StoredData;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.StoredDataList;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;

import org.apache.cxf.jaxrs.ext.multipart.Multipart;

/**
 * Interface RESTStoredDataService.Operations on {@link StoredData StoredData}s.
 *
 * @author Emanuele Tajariol (etj at geo-solutions.it)
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 */
public interface RESTStoredDataService {

    /**
     * @param id
     * @param data
     * @return long
     * @throws NotFoundWebEx
     */
    @PUT
    @Path("/{id}")
    @Consumes({MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Produces({MediaType.TEXT_PLAIN, MediaType.TEXT_XML, MediaType.APPLICATION_JSON})
    @RolesAllowed({"ADMIN", "USER"})
    long update(
    		@Context SecurityContext sc,
    		@PathParam("id") long id,
            @Multipart("data") String data) throws NotFoundWebEx;

    /**
     * @return StoredDataList
     */
//    @GET
//    @Path("/")
//    @Produces({MediaType.TEXT_PLAIN, MediaType.TEXT_XML, MediaType.APPLICATION_JSON})
//    @RolesAllowed({"ADMIN", "USER", "GUEST"})
//    StoredDataList getAll(@Context SecurityContext sc);

    /**
     * @param id
     * @throws NotFoundWebEx
     */
    @DELETE
    @Path("/{id}")
    @RolesAllowed({"ADMIN", "USER"})
    void delete(
    		@Context SecurityContext sc,
    		@PathParam("id") long id) throws NotFoundWebEx;

    /**
     * @param id
     * @return String
     * @throws NotFoundWebEx
     */
    @GET
    @Path("/{id}")
    @Produces({MediaType.TEXT_PLAIN, MediaType.TEXT_XML, MediaType.APPLICATION_JSON})
    @RolesAllowed({"ADMIN", "USER", "GUEST"})
    String get(
    		@Context SecurityContext sc,
    		@Context HttpHeaders headers,
    		@PathParam("id") long id) throws NotFoundWebEx;

}
