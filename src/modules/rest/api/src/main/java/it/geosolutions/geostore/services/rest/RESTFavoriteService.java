/* ====================================================================
 *
 * Copyright (C) 2025 GeoSolutions S.A.S.
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

import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import org.springframework.security.access.annotation.Secured;

/**
 * REST service mapped under the <code>/users</code> path. For example, to assign a favorite to the
 * user, use the endpoint: <code>POST /rest/users/user/{userId}/favorite/{resourceId}</code>.
 */
public interface RESTFavoriteService {

    /**
     * @param id user identifier
     * @param resourceId resource identifier
     * @throws NotFoundWebEx
     */
    @POST
    @Path("user/{id}/favorite/{resourceId}")
    @Secured({"ROLE_ADMIN", "ROLE_USER"})
    void addFavorite(
            @Context SecurityContext sc,
            @PathParam("id") long id,
            @PathParam("resourceId") long resourceId)
            throws NotFoundWebEx;

    /**
     * @param id user identifier
     * @param resourceId resource identifier
     * @throws NotFoundWebEx
     */
    @DELETE
    @Path("user/{id}/favorite/{resourceId}")
    @Secured({"ROLE_ADMIN", "ROLE_USER"})
    void removeFavorite(
            @Context SecurityContext sc,
            @PathParam("id") long id,
            @PathParam("resourceId") long resourceId)
            throws NotFoundWebEx;
}
