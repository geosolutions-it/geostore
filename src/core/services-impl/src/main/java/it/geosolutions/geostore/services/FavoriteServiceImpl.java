/*
 *  Copyright (C) 2025 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services;

import it.geosolutions.geostore.core.dao.ResourceDAO;
import it.geosolutions.geostore.core.dao.UserDAO;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import org.springframework.transaction.annotation.Transactional;

public class FavoriteServiceImpl implements FavoriteService {

    private UserDAO userDAO;
    private ResourceDAO resourceDAO;

    public void setUserDAO(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public void setResourceDAO(ResourceDAO resourceDAO) {
        this.resourceDAO = resourceDAO;
    }

    @Override
    @Transactional(value = "geostoreTransactionManager")
    public void addFavorite(long userId, long resourceId) throws NotFoundServiceEx {

        User user = userDAO.find(userId);
        if (user == null) {
            throw new NotFoundServiceEx("User not found");
        }

        Resource resource = resourceDAO.find(resourceId);
        if (resource == null) {
            throw new NotFoundServiceEx("Resource not found");
        }

        user.getFavorites().add(resource);

        userDAO.persist(user);
    }

    @Override
    @Transactional(value = "geostoreTransactionManager")
    public void removeFavorite(long userId, long resourceId) throws NotFoundServiceEx {

        User user = userDAO.find(userId);
        if (user == null) {
            throw new NotFoundServiceEx("User not found");
        }

        Resource resource = resourceDAO.find(resourceId);
        if (resource == null) {
            throw new NotFoundServiceEx("Resource not found");
        }

        user.getFavorites().remove(resource);

        userDAO.persist(user);
    }
}
