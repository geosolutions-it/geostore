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

package it.geosolutions.geostore.core.dao;

import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.StoredData;

import java.util.Date;

import org.junit.Test;

/**
 * Class ResourceDAOTest
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * 
 */
public class ResourceDAOTest extends BaseDAOTest {

    @Test
    public void testPersistResource() throws Exception {

        final String NAME1 = "FIRST_NAME";
        final String NAME2 = "SECOND_NAME";

        long dataId;
        long resourceId;
        long securityId;

        //
        // PERSIST
        //
        {
            Category category = new Category();
            category.setName("MAP");

            categoryDAO.persist(category);

            assertEquals(1, categoryDAO.count(null));
            assertEquals(1, categoryDAO.findAll().size());

            Resource resource = new Resource();
            resource.setName(NAME1);
            resource.setCreation(new Date());
            resource.setCategory(category);

            resourceDAO.persist(resource);
            resourceId = resource.getId();

            assertEquals(1, resourceDAO.count(null));
            assertEquals(1, resourceDAO.findAll().size());

            StoredData data = new StoredData();
            data.setData("Dummy data");
            data.setResource(resource);
            data.setId(resource.getId());

            storedDataDAO.persist(data);
            dataId = data.getId();

            assertEquals(1, storedDataDAO.count(null));
            assertEquals(1, storedDataDAO.findAll().size());

            assertEquals(dataId, resourceId);

            SecurityRule security = new SecurityRule();
            security.setCanRead(true);
            security.setCanWrite(true);
            security.setResource(resource);

            securityDAO.persist(security);
            securityId = security.getId();

            assertEquals(1, securityDAO.count(null));
            assertEquals(1, securityDAO.findAll().size());
        }

        //
        // UPDATE AND LOAD
        //
        {
            Resource loaded = resourceDAO.find(resourceId);
            assertNotNull("Can't retrieve resource", loaded);

            assertEquals(NAME1, loaded.getName());
            loaded.setName(NAME2);
            resourceDAO.merge(loaded);
        }

        {
            Resource loaded = resourceDAO.find(resourceId);
            assertNotNull("Can't retrieve resource", loaded);
            assertEquals(NAME2, loaded.getName());
        }

        //
        // REMOVE, CASCADING
        //
        {
            Resource loaded = resourceDAO.find(resourceId);
            StoredData data = loaded.getData();

            assertNotNull("Can't retrieve StoredData from Resource", data);

            resourceDAO.removeById(resourceId);
            assertNull("Resource not deleted", resourceDAO.find(resourceId));

            //
            // Cascading
            //
            assertNull("SecurityRule not deleted", securityDAO.find(securityId));
            assertNull("StoredData not deleted", storedDataDAO.find(dataId));
        }

    }

}
