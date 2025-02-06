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

import static org.junit.Assert.assertThrows;

import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import java.util.Collections;
import java.util.Set;

public class FavoriteServiceImplTest extends ServiceTestBase {

    public FavoriteServiceImplTest() {}

    public void testAddFavorite() throws Exception {

        long resourceId = createResource("resource", "description", "category");
        long userId = createUser("user", Role.USER, "password");

        favoriteService.addFavorite(userId, resourceId);

        User user = userService.get(userId);
        userService.fetchFavorites(user);

        Set<Resource> resourceFavorites = user.getFavorites();
        assertEquals(1, resourceFavorites.size());
        Resource resourceFavorite = resourceFavorites.stream().findFirst().orElseThrow();
        assertEquals(resourceId, resourceFavorite.getId().longValue());
    }

    public void testAddFavoriteNotFoundUser() throws Exception {
        long resourceId = createResource("resource", "description", "category");
        assertThrows(NotFoundServiceEx.class, () -> favoriteService.addFavorite(0L, resourceId));
    }

    public void testAddFavoriteNotFoundResource() throws Exception {
        long userId = createUser("user", Role.USER, "password");
        assertThrows(NotFoundServiceEx.class, () -> favoriteService.addFavorite(userId, 0L));
    }

    public void testRemoveFavorite() throws Exception {

        long resourceId = createResource("resource", "description", "category");
        long userId = createUser("user", Role.USER, "password");

        User user = userService.get(userId);
        Resource resource = resourceService.get(resourceId);
        user.setFavorites(Collections.singleton(resource));
        userService.update(user);

        userService.fetchFavorites(user);
        assertFalse(user.getFavorites().isEmpty());

        favoriteService.removeFavorite(userId, resourceId);
        userService.fetchFavorites(user);

        assertTrue(user.getFavorites().isEmpty());
    }

    public void testRemoveFromResourceNotFoundUser() throws Exception {
        long resourceId = createResource("resource", "description", "category");
        assertThrows(NotFoundServiceEx.class, () -> favoriteService.removeFavorite(0L, resourceId));
    }

    public void testRemoveFromResourceNotFoundResource() throws Exception {
        long userId = createUser("user", Role.USER, "password");
        assertThrows(NotFoundServiceEx.class, () -> favoriteService.removeFavorite(userId, 0L));
    }
}
