/*
 *  Copyright (C) 2007 - 2016 GeoSolutions S.A.S.
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
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.DataType;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.dto.ShortAttribute;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.*;
import it.geosolutions.geostore.services.rest.model.CategoryList;
import it.geosolutions.geostore.services.rest.model.RESTCategory;
import it.geosolutions.geostore.services.rest.model.RESTResource;
import it.geosolutions.geostore.services.rest.model.RESTStoredData;
import it.geosolutions.geostore.services.rest.model.RESTUser;
import it.geosolutions.geostore.services.rest.model.RESTUserGroup;
import it.geosolutions.geostore.services.rest.model.ShortResourceList;
import it.geosolutions.geostore.services.rest.model.UserGroupList;
import it.geosolutions.geostore.services.rest.model.UserList;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
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

    protected static final String GEOSTORE_REST_URL = "http://localhost:9191/geostore/rest";

    protected GeoStoreClient client;
    protected AdministratorGeoStoreClient adminClient;

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
        client = createClient("admin", "admin");
        adminClient = createAdminClient();

        assumeTrue(pingGeoStore(client));

        // CLEAR
        removeAllResources(client);
        removeAllCategories(client);
        removeAllGroups();
        removeAllUsers();
    }

    protected GeoStoreClient createClient(String u, String p) {
        GeoStoreClient client = new GeoStoreClient();
        client.setGeostoreRestUrl(GEOSTORE_REST_URL);
        client.setUsername(u);
        client.setPassword(p);
        return client;
    }

    protected AdministratorGeoStoreClient createAdminClient() {
        AdministratorGeoStoreClient client = new AdministratorGeoStoreClient();
        client.setGeostoreRestUrl(GEOSTORE_REST_URL);
        client.setUsername("admin");
        client.setPassword("admin");
        return client;
    }

    protected RESTResource createSampleResource(Long catId) {
        String timeid = Long.toString(System.currentTimeMillis());

        RESTStoredData storedData = new RESTStoredData();
        storedData.setData("test stored data #" + timeid);

        List<ShortAttribute> attrList = new ArrayList<ShortAttribute>();
        attrList.add(new ShortAttribute("stringAtt", "attVal" + timeid, DataType.STRING));
        attrList.add(ShortAttribute.createDateAttribute("dateAtt", new Date()));
        attrList.add(new ShortAttribute("longAtt", timeid, DataType.NUMBER));

        RESTResource resource = new RESTResource();
        resource.setCategory(new RESTCategory(catId));
        resource.setName("rest_test_resource_" + timeid);
        resource.setStore(storedData);
        resource.setAttribute(attrList);

        return resource;
    }

    protected void removeAllResources(GeoStoreClient client) {
        SearchFilter filter = new FieldFilter(BaseField.NAME, "*", SearchOperator.IS_NOT_NULL);
        {
            ShortResourceList resources = client.searchResources(filter);
            if (resources.getList() != null) {
                LOGGER.info("Found " + resources.getList().size() + " resources");
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
            // assertEquals("Not all resources have been deleted", 0, resources.getList().size());
        }
    }

    protected void removeAllCategories(GeoStoreClient client) {
        {
            CategoryList categories = client.getCategories();
            if (categories.getList() != null) {
                LOGGER.info("Found " + categories.getList().size() + " categories");
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
            // assertEquals("Not all categories have been deleted", 0, categories.getList().size());
        }
    }

    protected void removeAllGroups() 
    {
        UserGroupList userGroups = adminClient.getUserGroups(0, 1000, false);

        for (RESTUserGroup group : userGroups) {
            LOGGER.info("Found userGroup " + group + " . Deleting...");
            adminClient.deleteUserGroup(group.getId());
        }
    }

    protected void removeAllUsers()
    {
        UserList users = adminClient.getUsers();

        for (RESTUser user : users) {
            LOGGER.info("Found user " + user + " . Deleting...");
            if(user.getName().equals("admin")) {
                LOGGER.info("Skipping main admin");
                continue;
            }
            adminClient.deleteUser(user.getId());
        }
    }

    protected boolean pingGeoStore(GeoStoreClient client) {
        try {
            client.getCategories();
            return true;
        } catch (Exception ex) {
            LOGGER.debug("Error connecting to GeoStore", ex);
            // ... and now for an awful example of heuristic.....
            Throwable t = ex;
            while (t != null) {
                if (t instanceof ConnectException) {
                    LOGGER.warn("Testing GeoStore is offline");
                    return false;
                }
                t = t.getCause();
            }
            throw new RuntimeException("Unexpected exception: " + ex.getMessage(), ex);
        }
    }
    
    protected long createUser(String name, Role role, String pw, UserGroup ...group)
    {
        User user = new User();
        user.setName(name);
        user.setRole(role);
        user.setNewPassword(pw);
        if(group != null) {
            user.setGroups(new HashSet(Arrays.asList(group)));
        }

        return adminClient.insert(user);
    }

    protected UserGroup createUserGroup(String name)
    {
        UserGroup g1 = new UserGroup();
        g1.setGroupName(name);
        long id = adminClient.insertUserGroup(g1);
        g1.setId(id);
        return g1;
    }

}
