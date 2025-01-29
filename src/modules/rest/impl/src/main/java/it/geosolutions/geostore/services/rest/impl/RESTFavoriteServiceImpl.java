/*
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
package it.geosolutions.geostore.services.rest.impl;

import it.geosolutions.geostore.services.FavoriteService;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.RESTFavoriteService;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import javax.ws.rs.core.SecurityContext;

public class RESTFavoriteServiceImpl implements RESTFavoriteService {

    private FavoriteService favoriteService;

    public void setFavoriteService(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @Override
    public void addFavorite(SecurityContext sc, long id, long resourceId) throws NotFoundWebEx {
        try {
            favoriteService.addFavorite(id, resourceId);
        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx(e.getMessage());
        }
    }

    @Override
    public void removeFavorite(SecurityContext sc, long id, long resourceId) throws NotFoundWebEx {
        try {
            favoriteService.removeFavorite(id, resourceId);
        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx(e.getMessage());
        }
    }
}
