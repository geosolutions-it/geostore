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
package it.geosolutions.geostore.services;

import java.util.Date;
import java.util.List;

import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.CategoryFilter;
import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.dto.search.SearchOperator;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Class ResourceServiceImplTest.
 *
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 *
 */
public class ResourceServiceImplTest extends ServiceTestBase {

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    public ResourceServiceImplTest() {
    }

    @Test
    public void testInsertDeleteResource() throws Exception {

        long resourceId = createResource(
                "name1",
                "description1",
                "MAP");

        assertEquals(1, resourceService.getCount(null));
        assertTrue("Could not delete resource", resourceService.delete(resourceId));
        assertEquals(0, resourceService.getCount(null));
    }

    @Test
    public void testUpdateData() throws Exception {
        final String NAME1 = "name1";
        final String NAME2 = "name2";

        long resourceId = createResource(
                NAME1,
                "description1",
                "MAP");

        assertEquals(1, resourceService.getCount(null));

        {
            Resource loaded = resourceService.get(resourceId);
            assertNotNull(loaded);
            assertNull(loaded.getLastUpdate());
            assertEquals(NAME1, loaded.getName());

            loaded.setName(NAME2);
            resourceService.update(loaded);
        }

        {
            Resource loaded = resourceService.get(resourceId);
            assertNotNull(loaded);
            assertNotNull(loaded.getLastUpdate());
            assertEquals(NAME2, loaded.getName());
        }

        {
            assertEquals(1, resourceService.getCount(null));

            resourceService.delete(resourceId);
            assertEquals(0, resourceService.getCount(null));
        }
    }

    @Test
    public void testGetAllData() throws Exception {
        assertEquals(0, resourceService.getAll(null, null, null).size());

        for (int i = 0; i < 10; i++) {
            createResource(
                    "name" + i,
                    "description" + i,
                    "MAP1" + i);
        }

        for (int i = 0; i < 10; i++) {
            createResource(
                    "test name" + i,
                    "description" + i,
                    "MAP2" + i);
        }

        assertEquals(20, resourceService.getAll(null, null, null).size());
        assertEquals(10, resourceService.getCount("name%"));
        assertEquals(10, resourceService.getList("name%", null, null, null).size());
        assertEquals(20, resourceService.getCount("%name%"));
        assertEquals(20, resourceService.getList("%name%", null, null, null).size());
        assertEquals(2, resourceService.getCount("%name1%"));
        assertEquals(2, resourceService.getList("%name1%", null, null, null).size());
    }

    @Test
    public void testCategoryFilter() throws Exception {
        assertEquals(0, categoryService.getAll(null, null).size());

        long id0 = createCategory("category0");
        long id1 = createCategory("category1");
        assertEquals(2, categoryService.getAll(null, null).size());

        Category c0 = new Category();
        c0.setId(id0);

        Category c1i = new Category();
        c1i.setId(id1);

        Category c1n = new Category();
        c1n.setName("category1");


        assertEquals(0, resourceService.getAll(null, null, null).size());

        long r0 = createResource("res0", "des0", c0);
        long r1 = createResource("res1", "des1", c1i);
        long r2 = createResource("res2", "des2", c1n);
        assertEquals(3, resourceService.getAll(null, null, null).size());

        {
            SearchFilter filter = new CategoryFilter("category0", SearchOperator.EQUAL_TO);
            List<ShortResource> list = resourceService.getResources(filter, null);
            assertEquals(1, list.size());
            assertEquals(r0, list.get(0).getId());
        }

        {
            SearchFilter filter = new CategoryFilter("%1", SearchOperator.LIKE);
            List<ShortResource> list = resourceService.getResources(filter, null);
            assertEquals(2, list.size());
        }

        {
            SearchFilter filter = new CategoryFilter("cat%", SearchOperator.LIKE);
            List<ShortResource> list = resourceService.getResources(filter, null);
            assertEquals(3, list.size());
        }
    }
}
