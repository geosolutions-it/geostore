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
package it.geosolutions.geostore.rest.service.impl;

import it.geosolutions.geostore.core.model.Tag;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.ServiceTestBase;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.impl.RESTTagServiceImpl;
import it.geosolutions.geostore.services.rest.model.TagList;
import it.geosolutions.geostore.services.rest.utils.MockSecurityContext;
import java.util.List;
import javax.ws.rs.core.SecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RESTTagServiceImplTest extends ServiceTestBase {

    RESTTagServiceImpl restService;

    @Before
    public void setUp() throws BadRequestServiceEx, NotFoundServiceEx {
        restService = new RESTTagServiceImpl();
        restService.setTagService(tagService);
    }

    @After
    public void tearDown() throws Exception {
        removeAll();
    }

    @Test
    public void testGetAllWithPagination() throws Exception {

        final Tag tag_a = new Tag("tag-A", "#4561aa", "dusky");
        final Tag tag_b = new Tag("tag-B", "black", null);
        final Tag tag_c = new Tag("tag-C", "navy", "kind of blue");

        long userID = createUser("user", Role.USER, "user");
        SecurityContext sc = new MockSecurityContext(userService.get(userID));

        tagService.insert(tag_a);
        tagService.insert(tag_b);
        tagService.insert(tag_c);

        TagList firstPage = restService.getAll(sc, 0, 2, null);
        assertEquals(3, firstPage.getCount());
        assertEquals(List.of(tag_a, tag_b), firstPage.getList());

        TagList secondPage = restService.getAll(sc, 1, 2, null);
        assertEquals(3, firstPage.getCount());
        assertEquals(List.of(tag_c), secondPage.getList());
    }
}
