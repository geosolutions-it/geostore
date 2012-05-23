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

import com.sun.jersey.api.client.UniformInterfaceException;
import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.cxf.helpers.IOUtils;
import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assume.*;
import static org.junit.Assert.*;

/**
 *
 * @author ETj (etj at geo-solutions.it)
 */
public class GeoStoreClientTest {
    private final static Logger LOGGER = Logger.getLogger(GeoStoreClientTest.class);

    public GeoStoreClientTest() {
    }



    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void before() throws Exception {
        GeoStoreClient client = createClient();
        assumeTrue(pingGeoStore(client));
    }

    protected GeoStoreClient createClient() {
        GeoStoreClient client = new GeoStoreClient();
        client.setGeostoreRestUrl("http://localhost:9191/geostore/rest");
        client.setUsername("admin");
        client.setPassword("admin");
        return client;
    }

    @Test
    public void testSearchResources() {
    }


    @Test
    public void testRemoveAllAttribs() {
        GeoStoreClient client = createClient();

        final String KEY_STRING = "stringAtt";

        final Date origDate = new Date();
        final String origString = "OrigStringValue";

        RESTStoredData storedData = new RESTStoredData();
        storedData.setData("we wish you a merry xmas and a happy new year");

        List<ShortAttribute> attrList = new ArrayList<ShortAttribute>();
        attrList.add(new ShortAttribute(KEY_STRING, origString, DataType.STRING));

        String timeid = Long.toString(System.currentTimeMillis());

        RESTResource origResource = new RESTResource();
        origResource.setCategory(new RESTCategory("TestCategory1"));
        origResource.setName("rest_test_resource_"+timeid);
        origResource.setStore(storedData);
        origResource.setAttribute(attrList);

        Long rid = client.insert(origResource);
        System.out.println("RESOURCE has ID " + rid);

        // test getResource
        {
            Resource loaded = client.getResource(rid);
            System.out.println("RESOURCE: " + loaded);

            // test reloaded attrs
            List<Attribute> loadedAttrs = loaded.getAttribute();
            assertEquals(1, loadedAttrs.size());
        }

        // remove attrib list
        // once updated, the attribs should be the same
        origResource.setAttribute(null);
        client.updateResource(rid, origResource);
        {
            Resource loaded = client.getResource(rid);
            System.out.println("RESOURCE: " + loaded);

            // test reloaded attrs
            List<Attribute> loadedAttrs = loaded.getAttribute();
            assertEquals(1, loadedAttrs.size());
        }

        // reattach a 0-length list
        // once updated, there should be no attribs in the resource
        origResource.setAttribute(new ArrayList<ShortAttribute>());
        assertTrue(origResource.getAttribute().isEmpty());
        
        client.updateResource(rid, origResource);

        // test getResource
        {
            Resource loaded = client.getResource(rid);
            System.out.println("RESOURCE: " + loaded);

            // test reloaded attrs
            List<Attribute> loadedAttrs = loaded.getAttribute();
            assertEquals(0, loadedAttrs.size());
        }

    }

    @Test
//    @Ignore
    public void _testInsertResource() {
        GeoStoreClient client = createClient();

        final String KEY_STRING = "stringAtt";
        final String KEY_DATE = "dateAtt";

        final Date origDate = new Date();
        final String origString = "OrigStringValue";

        RESTStoredData storedData = new RESTStoredData();
        storedData.setData("we wish you a merry xmas and a happy new year");

        List<ShortAttribute> attrList = new ArrayList<ShortAttribute>();
        attrList.add(new ShortAttribute(KEY_STRING, origString, DataType.STRING));
        attrList.add(ShortAttribute.createDateAttribute(KEY_DATE, origDate));

        String timeid = Long.toString(System.currentTimeMillis());

        RESTResource origResource = new RESTResource();
        origResource.setCategory(new RESTCategory("TestCategory1"));
        origResource.setName("rest_test_resource_"+timeid);
        origResource.setStore(storedData);
        origResource.setAttribute(attrList);

        Long rid = client.insert(origResource);
        System.out.println("RESOURCE has ID " + rid);

        // test getResource
        {
            Resource loaded = client.getResource(rid);
            System.out.println("RESOURCE: " + loaded);

            // test reloaded attrs
            List<Attribute> loadedAttrs = loaded.getAttribute();
            assertEquals(2, loadedAttrs.size());

            Attribute satt, datt;

            if(loadedAttrs.get(0).getType() == DataType.STRING) {
                satt = loadedAttrs.get(0);
                datt = loadedAttrs.get(1);
            } else {
                datt = loadedAttrs.get(0);
                satt = loadedAttrs.get(1);
            }

            assertEquals(DataType.STRING, satt.getType());
            assertEquals(KEY_STRING, satt.getName());
            assertEquals(origString, satt.getTextValue());

            assertEquals(DataType.DATE, datt.getType());
            assertEquals(KEY_DATE, datt.getName());
            assertEquals(origDate, datt.getDateValue());

        }
        // test Search
        SearchFilter searchFilter = new FieldFilter(BaseField.NAME, "%"+timeid, SearchOperator.LIKE);
        ShortResourceList rlist = client.searchResources(searchFilter);
        assertNotNull(rlist);
        assertEquals(1, rlist.getList().size());
        assertEquals(rid, (Long)rlist.getList().get(0).getId());
    }

    public void _testUpdateResource() {
        GeoStoreClient client = createClient();

        final String KEY_STRING = "stringAtt";
        final String KEY_DATE = "dateAtt";

        final Date origDate = new Date();
        final String origString = "OrigStringValue";

        Long rid;

        {
            RESTStoredData storedData = new RESTStoredData();
            storedData.setData("we wish you a merry xmas and a happy new year");

            List<ShortAttribute> attrList = new ArrayList<ShortAttribute>();
            attrList.add(new ShortAttribute("string1", "value1", DataType.STRING));
            attrList.add(new ShortAttribute("string2", "value2", DataType.STRING));
            attrList.add(new ShortAttribute("string3", "value3", DataType.STRING));

            String timeid = Long.toString(System.currentTimeMillis());

            RESTResource origResource = new RESTResource();
            origResource.setCategory(new RESTCategory("TestCategory1"));
            origResource.setName("rest_test_resource_"+timeid);
            origResource.setStore(storedData);
            origResource.setAttribute(attrList);

            rid = client.insert(origResource);
        }
        System.out.println("RESOURCE has ID " + rid);


        // test getResource
        String name1= "rest_test_resource_" + Long.toString(System.currentTimeMillis());
        {
            RESTResource updResource = new RESTResource();
            updResource.setName(name1);

            List<ShortAttribute> attrList = new ArrayList<ShortAttribute>();
            attrList.add(new ShortAttribute("string1", "value1", DataType.STRING)); // same
            attrList.add(new ShortAttribute("string2", "value2.2", DataType.STRING)); // updated
            //attrList.add(new ShortAttribute("string3", "value3", DataType.STRING)); //removed
            attrList.add(new ShortAttribute("string4", "value4", DataType.STRING)); // added

            updResource.setAttribute(attrList);
            client.updateResource(rid, updResource);
        }

        {
           Resource loaded = client.getResource(rid);
           System.out.println("RESOURCE: " + loaded);

            // test reloaded attrs
            List<Attribute> loadedAttrs = loaded.getAttribute();
            assertEquals(3, loadedAttrs.size());

            Map<String,String> attMap = new HashMap<String, String>();
            for (Attribute attribute : loadedAttrs) {
                attMap.put(attribute.getName(), attribute.getTextValue());
            }

            assertEquals("value1", attMap.get("string1"));
            assertEquals("value2.2", attMap.get("string2"));
            assertEquals("value4", attMap.get("string4"));
        }

        // try bad update
        {
            RESTResource res = new RESTResource();
            res.setCategory(new RESTCategory("TestCategory2"));
            try {
                client.updateResource(rid, res);
                fail("Undetected error");
            } catch (UniformInterfaceException e) {
                String response = "COULD NOT READ RESPONSE";
                try{
                    response = IOUtils.toString(e.getResponse().getEntityInputStream());
                } catch(Exception e2) {
                    LOGGER.warn("Error reading response: " + e2.getMessage());
                }
                LOGGER.info("Error condition successfully detected: " + response);
            } catch (Exception e) {
                LOGGER.info("Error condition successfully detected:" + e.getMessage(), e);

            }
        }

        client.deleteResource(rid);
    }

    @Test
    public void _testSearchByCategory() {
        GeoStoreClient client = createClient();

        removeAllResources(client);
        removeAllCategories(client);

        RESTCategory c1 = new RESTCategory();
        c1.setName("cat1");
        client.insert(c1);

        RESTResource res = new RESTResource();
        res.setCategory(c1);

        String timeid = Long.toString(System.currentTimeMillis());
        res.setName("rest_test_resource_"+timeid);

        Long id = client.insert(res);

        SearchFilter filter = new CategoryFilter("cat1", SearchOperator.EQUAL_TO);
        ShortResourceList resources = client.searchResources(filter);
        assertEquals(1, resources.getList().size());

    }

    @Test
    public void _testGetResource() {
        GeoStoreClient client = createClient();
        Resource resource = client.getResource(261l);
        System.out.println("Resource is " + resource);
        if(resource.getAttribute() != null) {
            System.out.println("Attributes " + resource.getAttribute());
        } else {
            System.out.println("No attrs");
        }
    }

    @Test
    public void _testClearAll() {
        GeoStoreClient client = createClient();

        removeAllResources(client);
        removeAllCategories(client);

        Long catId1 = client.insert(new RESTCategory("Test Category#1"));
        Long catId2 = client.insert(new RESTCategory("Test Category#2"));

        assertEquals(2, client.getCategories().getList().size());

        client.insert(createSampleResource(catId1));
        client.insert(createSampleResource(catId2));
        client.insert(createSampleResource(catId2));

        {
            SearchFilter filter = new FieldFilter(BaseField.NAME, "*", SearchOperator.IS_NOT_NULL);
            ShortResourceList resources = client.searchResources(filter);
            assertEquals(3, resources.getList().size());
        }

        removeAllResources(client);
        removeAllCategories(client);
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


    @Test
    public void testGetPassword() {
    }

    @Test
    public void testSetPassword() {
    }

    @Test
    public void testGetUsername() {
    }

    @Test
    public void testSetUsername() {
    }

    @Test
    public void testGetGeostoreRestUrl() {
    }

    @Test
    public void testSetGeostoreRestUrl() {
    }

    @Test
    public void testGetResourceFull() {

        GeoStoreClient client = createClient();

        final String DATA = "we wish you a merry xmas and a happy new year";

        RESTStoredData storedData = new RESTStoredData();
        storedData.setData(DATA);


        RESTResource origResource = new RESTResource();
        origResource.setCategory(new RESTCategory("TestCategory1"));
        origResource.setName("rest_test_resource_getFull");
        origResource.setStore(storedData);

        Long rid = client.insert(origResource);
        System.out.println("RESOURCE has ID " + rid);

        // make sure data has been saved
        {
            String data = client.getData(rid);
            assertEquals(DATA, data);
        }

        // test getResource
        {
            Resource loaded = client.getResource(rid);
            System.out.println("RESOURCE: " + loaded);
            assertNull(loaded.getData());
        }


        {
            Resource loaded = client.getResource(rid, true);
            System.out.println("RESOURCE: " + loaded);
            assertNotNull(loaded.getData());
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
