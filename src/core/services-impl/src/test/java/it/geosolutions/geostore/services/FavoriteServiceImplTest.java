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
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserFavorite;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.exception.DuplicatedFavoriteServiceException;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.ws.rs.ForbiddenException;

public class FavoriteServiceImplTest extends ServiceTestBase {

    public void testAddFavoriteByUserId() throws Exception {

        long resourceId = createResource("resource", "description", "category");
        long userId = createUser("user", Role.USER, "password");

        SecurityRule rule = new SecurityRule();
        rule.setUser(userService.get(userId));
        rule.setCanRead(true);
        resourceService.updateSecurityRules(resourceId, List.of(rule));

        favoriteService.addFavoriteByUserId(userId, resourceId);

        User user = userService.get(userId);
        userService.fetchFavorites(user);

        Set<UserFavorite> userFavorites = user.getFavorites();
        assertEquals(1, userFavorites.size());
        UserFavorite resourceFavorite = userFavorites.stream().findFirst().orElseThrow();
        assertEquals(resourceId, resourceFavorite.getResource().getId().longValue());
    }

    public void testAddFavoriteByUserIdNotFoundUser() throws Exception {
        long resourceId = createResource("resource", "description", "category");
        assertThrows(
                NotFoundServiceEx.class, () -> favoriteService.addFavoriteByUserId(0L, resourceId));
    }

    public void testAddFavoriteByUserIdWhenNotPermitted() throws Exception {

        long resourceId = createResource("resource", "description", "category");
        long userId = createUser("user", Role.USER, "password");

        SecurityRule rule = new SecurityRule();
        rule.setUser(null);
        rule.setUsername("alice");
        rule.setCanRead(true);

        resourceService.updateSecurityRules(resourceId, List.of(rule));

        assertThrows(
                ForbiddenException.class,
                () -> favoriteService.addFavoriteByUserId(userId, resourceId));
    }

    public void testAddFavoriteByUserIdWhenDuplicateExists() throws Exception {

        long resourceId = createResource("resource", "description", "category");
        long userId = createUser("user", Role.USER, "password");

        SecurityRule rule = new SecurityRule();
        rule.setUser(userService.get(userId));
        rule.setCanRead(true);
        resourceService.updateSecurityRules(resourceId, List.of(rule));

        favoriteService.addFavoriteByUserId(userId, resourceId);

        assertThrows(
                DuplicatedFavoriteServiceException.class,
                () -> favoriteService.addFavoriteByUserId(userId, resourceId));
    }

    public void testAddFavoriteByUserIdNotFoundResource() throws Exception {
        long userId = createUser("user", Role.USER, "password");
        assertThrows(
                NotFoundServiceEx.class, () -> favoriteService.addFavoriteByUserId(userId, 0L));
    }

    public void testAddFavoriteByUsername() throws Exception {

        final String username = "user";
        long resourceId = createResource("resource", "description", "category");

        SecurityRule rule = new SecurityRule();
        rule.setUsername(username);
        rule.setCanRead(true);
        resourceService.updateSecurityRules(resourceId, List.of(rule));

        favoriteService.addFavoriteByUsername(username, resourceId);

        Resource resource = resourceService.get(resourceId);
        resourceService.fetchFavorites(resource);

        Set<UserFavorite> favorites = resource.getFavorites();
        assertEquals(1, favorites.size());
        UserFavorite resourceFavorite = favorites.stream().findFirst().orElseThrow();
        assertEquals(resourceId, resourceFavorite.getResource().getId().longValue());
    }

    public void testAddFavoriteByUsernameNotFoundResource() {
        assertThrows(
                NotFoundServiceEx.class, () -> favoriteService.addFavoriteByUsername("user", 0L));
    }

    public void testAddFavoriteByUsernameWhenDuplicateExists() throws Exception {

        final String username = "username";
        long resourceId = createResource("resource", "description", "category");

        SecurityRule rule = new SecurityRule();
        rule.setUsername(username);
        rule.setCanRead(true);
        resourceService.updateSecurityRules(resourceId, List.of(rule));

        favoriteService.addFavoriteByUsername(username, resourceId);

        assertThrows(
                DuplicatedFavoriteServiceException.class,
                () -> favoriteService.addFavoriteByUsername(username, resourceId));
    }

    public void testRemoveFavoriteByUserId() throws Exception {

        long resourceId = createResource("resource", "description", "category");
        long userId = createUser("user", Role.USER, "password");

        User user = userService.get(userId);
        Resource resource = resourceService.get(resourceId);

        UserFavorite favorite = UserFavorite.withUser(user, resource);

        user.setFavorites(Collections.singleton(favorite));
        userService.update(user);

        userService.fetchFavorites(user);
        assertFalse(user.getFavorites().isEmpty());

        favoriteService.removeFavoriteByUserId(userId, resourceId);
        userService.fetchFavorites(user);

        assertTrue(user.getFavorites().isEmpty());
    }

    public void testRemoveFavoriteByUserIdWhenNotFound() throws Exception {
        long resourceId = createResource("resource", "description", "category");
        assertThrows(
                NotFoundServiceEx.class,
                () -> favoriteService.removeFavoriteByUserId(0L, resourceId));
    }

    public void testRemoveFavoriteByUsername() throws Exception {

        final String username = "user";
        long resourceId = createResource("resource", "description", "category");

        Resource resource = resourceService.get(resourceId);

        UserFavorite favorite = UserFavorite.withUsername(username, resource);

        resource.setFavorites(Collections.singleton(favorite));
        resourceService.update(resource);

        resourceService.fetchFavorites(resource);
        assertFalse(resource.getFavorites().isEmpty());

        favoriteService.removeFavoriteByUsername(username, resourceId);
        resourceService.fetchFavorites(resource);

        assertTrue(resource.getFavorites().isEmpty());
    }

    public void testRemoveFavoriteByUsernameWhenNotFound() throws Exception {
        long resourceId = createResource("resource", "description", "category");
        assertThrows(
                NotFoundServiceEx.class,
                () -> favoriteService.removeFavoriteByUsername("notfounduser", resourceId));
    }
}
