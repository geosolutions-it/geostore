/*
 *  Copyright (C) 2007 - 2012 GeoSolutions S.A.S.
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

import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.enums.DataType;
import it.geosolutions.geostore.services.dto.ShortAttribute;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.*;
import it.geosolutions.geostore.services.rest.model.CategoryList;
import it.geosolutions.geostore.services.rest.model.RESTCategory;
import it.geosolutions.geostore.services.rest.model.RESTResource;
import it.geosolutions.geostore.services.rest.model.RESTStoredData;
import it.geosolutions.geostore.services.rest.model.ShortResourceList;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import static org.junit.Assume.*;
import static org.junit.Assert.*;

/**
 *
 * @author ETj (etj at geo-solutions.it)
 */
abstract public class BaseGeoStoreClientTest {

    private final static Logger LOGGER = Logger.getLogger(BaseGeoStoreClientTest.class);

    protected GeoStoreClient client;

    public BaseGeoStoreClientTest() {
    }



    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void before() throws Exception {
        client = createClient();
        assumeTrue(pingGeoStore(client));

        // CLEAR
        removeAllResources(client);
        removeAllCategories(client);
    }

    protected GeoStoreClient createClient() {
        GeoStoreClient client = new GeoStoreClient();
        client.setGeostoreRestUrl("http://localhost:9191/geostore/rest");
        client.setUsername("admin");
        client.setPassword("admin");
        return client;
    }


    protected RESTResource createSampleResource(Long catId) {
        String timeid = Long.toString(System.currentTimeMillis());

        RESTStoredData storedData = new RESTStoredData();
        storedData.setData("test stored data #"+timeid);

        List<ShortAttribute> attrList = new ArrayList<ShortAttribute>();
        attrList.add(new ShortAttribute("stringAtt", "attVal"+timeid, DataType.STRING));
        attrList.add(ShortAttribute.createDateAttribute("dateAtt", new Date()));
        attrList.add(new ShortAttribute("longAtt", timeid, DataType.NUMBER));

        RESTResource resource = new RESTResource();
        resource.setCategory(new RESTCategory(catId));
        resource.setName("rest_test_resource_"+timeid);
        resource.setStore(storedData);
        resource.setAttribute(attrList);

        return resource;
    }

    protected void removeAllResources(GeoStoreClient client) {
        SearchFilter filter = new FieldFilter(BaseField.NAME, "*", SearchOperator.IS_NOT_NULL);
        {
            ShortResourceList resources = client.searchResources(filter);
            if(resources.getList() != null) {
                LOGGER.info("Found " + resources.getList().size() +" resources");
                for (ShortResource shortResource : resources.getList()) {
                    LOGGER.info("Found resource " + shortResource + " . Deleting...");
                    client.deleteResource(shortResource.getId());
                }
            } else {
                LOGGER.info("No resource found");
            }
        }
        {
            ShortResourceList resources = client.searchResources(filter);
            assertNull("Not all resources have been deleted", resources.getList());
//            assertEquals("Not all resources have been deleted", 0, resources.getList().size());
        }
    }

    protected void removeAllCategories(GeoStoreClient client) {
        {
            CategoryList categories = client.getCategories();
            if(categories.getList() != null) {
                LOGGER.info("Found " + categories.getList().size() +" categories");
                for (Category category : categories.getList()) {
                    LOGGER.info("Found category " + category + " . Deleting...");
                    client.deleteCategory(category.getId());
                }
            } else {
                LOGGER.info("No category found");
            }
        }
        {
            CategoryList categories = client.getCategories();
            assertNull("Not all categories have been deleted", categories.getList());
//            assertEquals("Not all categories have been deleted", 0, categories.getList().size());
        }
    }


    protected boolean pingGeoStore(GeoStoreClient client) {
        try {
            client.getCategories();
            return true;
        } catch (Exception ex) {
            LOGGER.debug("Error connecting to GeoStore", ex);
            //... and now for an awful example of heuristic.....
            Throwable t = ex;
            while(t!=null) {
                if(t instanceof ConnectException) {
                    LOGGER.warn("Testing GeoStore is offline");
                    return false;
                }
                t = t.getCause();
            }
            throw new RuntimeException("Unexpected exception: " + ex.getMessage(), ex);
        }
    }

}
