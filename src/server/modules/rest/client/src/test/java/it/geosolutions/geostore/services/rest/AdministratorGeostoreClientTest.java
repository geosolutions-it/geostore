package it.geosolutions.geostore.services.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.DataType;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.dto.ShortAttribute;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.BaseField;
import it.geosolutions.geostore.services.dto.search.FieldFilter;
import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.dto.search.SearchOperator;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.sun.jersey.api.client.UniformInterfaceException;

public class AdministratorGeostoreClientTest{

    AdministratorGeoStoreClient geoStoreClient;
    GeoStoreClient geoStoreUserClient;

    private final static Logger LOGGER = Logger.getLogger(AdministratorGeostoreClientTest.class);

    final String DEFAULTCATEGORYNAME = "TestCategory1";
    final String KEY_STRING = "stringAtt";
    final String origString = "OrigStringValue";
    
    protected AdministratorGeoStoreClient createAdministratorClient() {
        geoStoreClient = new AdministratorGeoStoreClient();
        geoStoreClient.setGeostoreRestUrl("http://localhost:9191/geostore/rest");
        geoStoreClient.setUsername("admin");
        geoStoreClient.setPassword("admin");
        return geoStoreClient;
    }
    
    protected GeoStoreClient createUserClient(String username, String password) {
        geoStoreUserClient = new AdministratorGeoStoreClient();
        geoStoreUserClient.setGeostoreRestUrl("http://localhost:9191/geostore/rest");
        geoStoreUserClient.setUsername(username);
        geoStoreUserClient.setPassword(password);
        return geoStoreUserClient;
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

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void before() throws Exception {
        geoStoreClient = createAdministratorClient();
        assumeTrue(pingGeoStore(geoStoreClient));

        // CLEAR
        removeAllUsers(geoStoreClient);
        removeAllUserGroup(geoStoreClient);
        removeAllResources(geoStoreClient);
         removeAllCategories(geoStoreClient);
    }

    // ==========================================================================
    // === USER MANAGEMENT TESTS
    // ==========================================================================
    
    @Test
    public void getIdTest() {

        // User user = geoStoreClient.getUser(1);
        try {
            User userd = geoStoreClient.getUserDetails();
            System.out.println(userd.getId());
        } catch (Exception e) {
            fail();
        }

    }

    @Test
    public void getUsersTest() {

        // User user = geoStoreClient.getUser(1);
        try {
            UserList users = geoStoreClient.getUsers(1, 1);
            System.out.println(users.getList().get(0).getName());
            users = geoStoreClient.getUsers(2, 1);
            System.out.println(users.getList().get(0).getName());
        } catch (Exception e) {
            fail();
        }

    }

    @Test
    public void createUserTest() {

        // User user = geoStoreClient.getUser(1);
        try {
            User user = new User();
            user.setName("testuser1");
            user.setRole(Role.USER);
            user.setNewPassword("testpw");
            UserAttribute email = new UserAttribute();
            email.setName("email");
            email.setValue("test@geo-solutions.it");

            List<UserAttribute> attrs = new ArrayList<UserAttribute>();
            attrs.add(email);
            user.setAttribute(attrs);
            Long id = geoStoreClient.insert(user);
            System.out.println(id);
            User us = geoStoreClient.getUser(id, true);
            user.getName().equals("testuser");
            attrs = us.getAttribute();
            assertNotNull("Missing attribute list", attrs);
            assertTrue("Attributes missing", attrs.size() > 0);

        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void deleteUserTest() {

        // geoStoreClient.deleteUser(new Long(8));
    }

    @Test
    public void updateUserTest() {

        
        User userOld = new User();
        userOld.setName("testuser1");
        userOld.setRole(Role.USER);
        userOld.setNewPassword("testpw");
        UserAttribute email = new UserAttribute();
        email.setName("email");
        email.setValue("test@geo-solutions.it");

        List<UserAttribute> attrs = new ArrayList<UserAttribute>();
        attrs.add(email);
        userOld.setAttribute(attrs);
        Long id = geoStoreClient.insert(userOld);
        
        // User user = geoStoreClient.getUser(1);
        try {
            
            User user = new User();
            user.setName("testuser1");
            user.setRole(Role.USER);
            user.setNewPassword("testpw");
            email = new UserAttribute();
            email.setName("email");
            email.setValue("test1@geo-solutions.it");

            attrs = new ArrayList<UserAttribute>();
            attrs.add(email);
            user.setAttribute(attrs);
            geoStoreClient.update(id, user);
            // System.out.println(id);
            // RESTUser us = geoStoreClient.getUser(id);
            user.getName().equals("testuser");

        } catch (Exception e) {
            fail();
        }
    }

    
    // ==========================================================================
    // === USER GROUPS MANAGEMENT
    // ==========================================================================
    
    @Test
    public void insertGetDeleteAssign_UserGroupTest() {
        
        User u1 = new User();
        u1.setName("u1");
        u1.setRole(Role.USER);
        User u2 = new User();
        u2.setName("u2");
        u2.setRole(Role.USER);
        Set<User> userSet = new HashSet<User>();
        userSet.add(u1);
        userSet.add(u2);
        geoStoreClient.insert(u1);
        geoStoreClient.insert(u2);
        
        UserList uli = geoStoreClient.getUsers(1,1000);
        long uid1 = -1;
        long uid2 = -1;
        for(RESTUser u : uli.getList()){
            if(!("admin".equals(u.getName()) || "user".equals(u.getName()))){
                try{
                    if("u1".equals(u.getName())){
                        uid1 = u.getId();
                    }
                    else{
                        uid2 = u.getId();
                    }
                }
                catch(Exception e){
                    // Swallow any exception...
                }
            }
        }
        
        
        
        UserGroup ug = new UserGroup();
        ug.setGroupName("usergroupTest1");
        ug.setUsers(userSet);
        geoStoreClient.insertUserGroup(ug);
        
        UserGroupList ugl = geoStoreClient.getUserGroups(0, 1000);
        List<RESTUserGroup> ugll = ugl.getUserGroupList();
        assertEquals(1, ugll.size());
        RESTUserGroup ug1 = ugll.get(0);
        assertEquals("usergroupTest1", ug1.getGroupName());
        List<RESTUser> userAssigned = ug1.getRestUsers().getList();
        assertEquals(null,userAssigned);
        
        geoStoreClient.assignUserGroup(uid1, ug1.getId());
        geoStoreClient.assignUserGroup(uid2, ug1.getId());
        ugl = geoStoreClient.getUserGroups(0, 1000);
        ugll = ugl.getUserGroupList();
        assertEquals(1, ugll.size());
        ug1 = ugll.get(0);
        assertEquals("usergroupTest1", ug1.getGroupName());
        userAssigned = ug1.getRestUsers().getList();
        assertEquals(2,userAssigned.size());
        
        geoStoreClient.deleteUserGroup(ug1.getId());
        ugl = geoStoreClient.getUserGroups(0, 1000);
        assertEquals(null,ugl.getUserGroupList());
        UserList ul = geoStoreClient.getUsers(0, 1000);
        List<RESTUser> ull = ul.getList();
        assertEquals(4, ull.size());
        geoStoreClient.deleteUser(uid1);
        geoStoreClient.deleteUser(uid2);
        ul = geoStoreClient.getUsers(0, 1000);
        assertEquals(2, ul.getList().size());
    }
    
    @Test
    public void specialGroupTest(){
        
        UserGroupList ugl = geoStoreClient.getUserGroups(1, 1000);
        assertEquals(1, ugl.getUserGroupList().size());
        assertEquals("allresources", ugl.getUserGroupList().get(0).getGroupName());
        createDefaultCategory();
        ShortResource sr = createAResource();
        
        User u1 = new User();
        u1.setName("u1");
        u1.setRole(Role.USER);
        u1.setNewPassword("u1");
        long uID = geoStoreClient.insert(u1);
        GeoStoreClient userGeoStoreClient = createUserClient("u1","u1");
        
        int u1StatusR = -1;
        int u1StatusW = -1;
        try{
            userGeoStoreClient.getResource(sr.getId());
        }
        catch(UniformInterfaceException e){
            u1StatusR = e.getResponse().getStatus();
        }
        assertEquals(403,u1StatusR);
        try{
            userGeoStoreClient.updateResource(sr.getId(), new RESTResource());
        }
        catch(UniformInterfaceException e){
            u1StatusW = e.getResponse().getStatus();
        }
        assertEquals(403,u1StatusW);
        
        // Assign to user "u1" the special group "allresources" (that should have id = 1)
        geoStoreClient.assignUserGroup(uID, 1);
        // Now "u1" should be able to READ and WRITE the created resource.
        userGeoStoreClient.getResource(sr.getId());
        userGeoStoreClient.updateResource(sr.getId(), new RESTResource());
           
    }
    
    @Test
    public void updateSecurityRulesTest() {
        
        // Create a group
        UserGroup ug = new UserGroup();
        ug.setGroupName("usergroupTest1");
        long gid = geoStoreClient.insertUserGroup(ug);
        
        UserGroup ug2 = new UserGroup();
        ug2.setGroupName("unusedGroup");
        long gid2 = geoStoreClient.insertUserGroup(ug2);
        
        UserGroup anotherGroup = new UserGroup();
        anotherGroup.setGroupName("anotherGroup");
        long anotherGid = geoStoreClient.insertUserGroup(anotherGroup);
        
        Set<UserGroup> ugroups = new HashSet<UserGroup>();
        ugroups.add(ug);
        ugroups.add(anotherGroup);
        
        // Create 2 user that belong to the previous created usergroup
        User u1 = new User();
        u1.setName("u1");
        u1.setNewPassword("u1");
        u1.setRole(Role.USER);
        
        User u2 = new User();
        u2.setName("u2");
        u2.setNewPassword("u2");
        u2.setRole(Role.USER);
        u2.setGroups(ugroups);
        
        geoStoreClient.insert(u1);
        geoStoreClient.insert(u2);
        
        GeoStoreClient u1Client = createUserClient("u1", "u1");
        GeoStoreClient u2Client = createUserClient("u2", "u2");
        
        // Create a resource with the user "user". So user will be the owner
        createDefaultCategory();
        ShortResource sr = createAResource();
        ShortResource sr2 = createAResource();
        
        int u1StatusW = -1;
        int u1StatusR = -1;
        int u2StatusR = -1;
        int u2StatusW = -1;
        
        // Since "user" is the owner the users: 
        // "u1" should have the default permissions on that resource (READ allowed, WRITE doesn't allowed) since it has no group assigned
        // and "u2" should have both READ and WRITE doesn't allowed because it isn't the owner of the reosource, it belong to at least one group and the resource doesn't have any groups rule security 
        u1Client.getResource(sr.getId(), true);
        try{
            u2Client.getResource(sr.getId(), true);
        }
        catch(UniformInterfaceException e){
            u2StatusR = e.getResponse().getStatus();
        }
        assertEquals(403,u2StatusR);
        try{
            u1Client.updateResource(sr.getId(), new RESTResource());
        }
        catch(UniformInterfaceException e){
            u1StatusW = e.getResponse().getStatus();
        }
        assertEquals(403,u1StatusW);
        try{
            u2Client.updateResource(sr.getId(), new RESTResource());
        }
        catch(UniformInterfaceException e){
            u2StatusW = e.getResponse().getStatus();
        }
        assertEquals(403,u2StatusW);
        
        // Update permissions for users belong to "usergroupTest1" ("u1") group
        // The effect of this service call will be: 
        // 1) The resource will be added with a group rule on usergroupTest1
        // 2) "u2" will have WRITE permission but not READ permissions
        // 3) Nothing will change for "u1"
        List<ShortResource> srl = new ArrayList<ShortResource>();
        srl.add(sr);
        ShortResourceList srll = new ShortResourceList(srl);
        geoStoreClient.updateSecurityRules(srll, gid, false, true);
        geoStoreClient.updateSecurityRules(srll, gid2, false, false);
        
        // READ shouldn't allowed, WRITE allowed
        u1StatusW = -1;
        u2StatusR = -1;
        u1Client.getResource(sr.getId(), true);
        try{
            u2Client.getResource(sr.getId(), true);
        }
        catch(UniformInterfaceException e){
            u2StatusR = e.getResponse().getStatus();
        }
        assertEquals(403,u2StatusR);
        try{
            u1Client.updateResource(sr.getId(), new RESTResource());
        }
        catch(UniformInterfaceException e){
            u1StatusW = e.getResponse().getStatus();
        }
        assertEquals(403,u1StatusW);
        u2Client.updateResource(sr.getId(), new RESTResource());
    }
    
    protected void removeAllUsers(GeoStoreClient client) {
        UserList users = geoStoreClient.getUsers(1, 1000);
        List<RESTUser> usersList = users.getList();
        usersList = (usersList == null)?new ArrayList<RESTUser>():usersList;
        for(RESTUser u : usersList){
            if(!("admin".equals(u.getName()) || "user".equals(u.getName()))){
                try{
                    geoStoreClient.deleteUser(u.getId());
                }
                catch(Exception e){
                    // Swallow any exception...
                }
            }
        }
    }
    
    protected void removeAllUserGroup(GeoStoreClient client) {
        UserGroupList groups = geoStoreClient.getUserGroups(1, 1000);
        List<RESTUserGroup> groupList = groups.getUserGroupList();
        groupList = (groupList == null)?new ArrayList<RESTUserGroup>():groupList; 
        for(RESTUserGroup ug : groupList){
            try{
                geoStoreClient.deleteUserGroup(ug.getId());
            }
            catch(Exception e){
                // Swallow any exception...
            }
        }
    }
    
    protected Long createDefaultCategory() {
        Long catid = geoStoreClient.insert(new RESTCategory(DEFAULTCATEGORYNAME));
        assertNotNull(catid);
        return catid;
    }
    
    protected ShortResource createAResource(){
        RESTStoredData storedData = new RESTStoredData();
        storedData.setData("we wish you a merry xmas and a happy new year");

        List<ShortAttribute> attrList = new ArrayList<ShortAttribute>();
        attrList.add(new ShortAttribute(KEY_STRING, origString, DataType.STRING));

        String timeid = Long.toString(System.currentTimeMillis());

       

        RESTResource origResource = new RESTResource();
        origResource.setCategory(new RESTCategory(DEFAULTCATEGORYNAME));
        origResource.setName("rest_test_resource_" + timeid);
        origResource.setStore(storedData);
        origResource.setAttribute(attrList);

        GeoStoreClient userGeoStoreClient = createUserClient("user","user");
        Long rid = userGeoStoreClient.insert(origResource);
        
        // Return a short resource represent the resource inserted
        origResource.setId(rid);
        ShortResource sr = new ShortResource();
        sr.setId(rid);
        sr.setName("rest_test_resource_" + timeid);
        return sr;
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
}
