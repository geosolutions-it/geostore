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
import it.geosolutions.geostore.core.model.enums.GroupReservedNames;
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
import it.geosolutions.geostore.services.rest.model.ResourceList;
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
            
            UserGroup ug1 = new UserGroup();
            ug1.setGroupName("testGroup1");
            ug1.setDescription("testGroup1-Description");
            UserGroup ug2 = new UserGroup();
            ug2.setGroupName("testGroup2");
            ug2.setDescription("testGroup2-Description");
            UserGroup ug3 = new UserGroup();
            ug3.setGroupName("testGroup3");
            ug3.setDescription("testGroup3-Description");
            geoStoreClient.insertUserGroup(ug1);
            geoStoreClient.insertUserGroup(ug2);
            geoStoreClient.insertUserGroup(ug3);
            Set<UserGroup> ugs = new HashSet<UserGroup>();
            ugs.add(ug1);
            ugs.add(ug2);
            ugs.add(ug3);
            User user = new User();
            user.setName("testuser111");
            user.setRole(Role.USER);
            user.setNewPassword("testpw");
            UserAttribute email = new UserAttribute();
            email.setName("email");
            email.setValue("test@geo-solutions.it");
            user.setGroups(ugs);
            geoStoreClient.insert(user);
            users = geoStoreClient.getUsers(3, 1);
            for(RESTUser u : users.getList()){
                if("testuser111".equals(u.getName())){
                    assertEquals(3,u.getGroupsNames().size());     
                }
            }
            
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
            
            UserGroup ug = new UserGroup();
            ug.setGroupName("testgroup1");
            ug.setDescription("testGroup1-Description");
            geoStoreClient.insertUserGroup(ug);
            Set<UserGroup> ugs = new HashSet<UserGroup>();
            ugs.add(ug);
            user.setGroups(ugs);
            
            List<UserAttribute> attrs = new ArrayList<UserAttribute>();
            attrs.add(email);
            user.setAttribute(attrs);
            Long id = geoStoreClient.insert(user);
            System.out.println(id);
            User us = geoStoreClient.getUser(id, true);
            //check assigned usergroup
            assertEquals(1,us.getGroups().size());
            UserGroup ugRetrieved = null;
            for(UserGroup ugIter : us.getGroups()){
                if("testgroup1".equals(ugIter.getGroupName())){
                    ugRetrieved = ugIter;
                }
            }
            assertEquals("testGroup1-Description",ugRetrieved.getDescription());
            assertNotNull(ugRetrieved.getId());
            
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
        long ugid= geoStoreClient.insertUserGroup(ug);
        
        //get created group 
        RESTUserGroup restUG1 = geoStoreClient.getUserGroup(ugid);
        ug = new UserGroup();
        ug.setGroupName(restUG1.getGroupName());
        assertNotNull(ug);
        assertEquals("usergroupTest1",ug.getGroupName());
        UserGroupList ugl = geoStoreClient.getUserGroups(0, 1000, true);
        List<RESTUserGroup> ugll = ugl.getUserGroupList();
        assertEquals(2, ugll.size());
        RESTUserGroup ug1 = ugll.get(1);
        assertEquals("usergroupTest1", ug1.getGroupName());

        List<RESTUser> userAssigned = ug1.getRestUsers().getList();
        assertEquals(null,userAssigned);
        
        geoStoreClient.assignUserGroup(uid1, ug1.getId());
        geoStoreClient.assignUserGroup(uid2, ug1.getId());
        ugl = geoStoreClient.getUserGroups(0, 1000, true);
        ugll = ugl.getUserGroupList();
        assertEquals(2, ugll.size());
        //
        // test deassign
        //
        User u = geoStoreClient.getUser(uid2);
        Set<UserGroup> usergroups = u.getGroups();
        assertEquals(1, usergroups.size());
        geoStoreClient.deassignUserGroup(uid2, ug1.getId());
        u = geoStoreClient.getUser(uid2);
        usergroups = u.getGroups();
        //the null is not a vaild response, the EVERYONE group at least is expected
        if(usergroups == null){
        	fail();
        }
        assertEquals(0, usergroups.size());
        //
        //reassign
        //
        geoStoreClient.assignUserGroup(uid2, ug1.getId());
        ugl = geoStoreClient.getUserGroups(0, 1000, true);
        ugll = ugl.getUserGroupList();
        assertEquals(2, ugll.size());
        ug1 = ugll.get(1);
        assertEquals("usergroupTest1", ug1.getGroupName());
        userAssigned = ug1.getRestUsers().getList();
        assertEquals(2,userAssigned.size());
        
        //
        // reassign from user
        //
        UserGroup ug2 = new UserGroup();
        ug2.setGroupName("usergroupTest2");
        ug2.setUsers(userSet);
        ugid = geoStoreClient.insertUserGroup(ug2);
        RESTUserGroup restUG = geoStoreClient.getUserGroup(ugid);
        assertNotNull(restUG);
        assertEquals("usergroupTest2", restUG.getGroupName());
        // usergrouptest1 user
        User us= geoStoreClient.getUser(userAssigned.get(0).getId());
        Set<UserGroup> usergroups2 = us.getGroups();
        assertEquals(1, usergroups2.size());
        ug2 = new UserGroup();
        ug2.setGroupName(restUG.getGroupName());
        //test add groups
        us.getGroups().add(ug2);
        geoStoreClient.update(us.getId(), us);
        us= geoStoreClient.getUser(userAssigned.get(0).getId());
        assertEquals(2, us.getGroups().size());
        
        //test remove groups 
        for( UserGroup gg: us.getGroups()){
        	if(gg.getGroupName().equals(ug2.getGroupName())){
        		us.getGroups().remove(gg);
        		break;
        	}
        }
        restUG = geoStoreClient.getUserGroup(ugid);
        geoStoreClient.update(us.getId(), us);
        us= geoStoreClient.getUser(userAssigned.get(0).getId());
        assertEquals(1, us.getGroups().size());
        
        //
        // delete 
        //
        geoStoreClient.deleteUserGroup(ug1.getId());
        geoStoreClient.deleteUserGroup(ugid);
        ugl = geoStoreClient.getUserGroups(0, 1000, true);
        assertEquals(1,ugl.getUserGroupList().size());
        UserList ul = geoStoreClient.getUsers(0, 1000);
        List<RESTUser> ull = ul.getList();
        assertEquals(4, ull.size());
        geoStoreClient.deleteUser(uid1);
        geoStoreClient.deleteUser(uid2);
        ul = geoStoreClient.getUsers(0, 1000);
        assertEquals(2, ul.getList().size());
    }
    
    @Test
    public void testStoredDataServices(){
        
        // Create a resource with Stored Data using the user "User"
        createDefaultCategory();
        ShortResource sr = createAResource();
        
        GeoStoreClient userGeoStoreClient = createUserClient("user","user");
        String data = userGeoStoreClient.getData(sr.getId());
        assertEquals("we wish you a merry xmas and a happy new year", data);
        
        //try to get the related Stored Data with the user "u1", must not be possible due to authorization rules
        User u1 = new User();
        u1.setName("u1");
        u1.setRole(Role.USER);
        u1.setNewPassword("u1");
        Set<User> userSet = new HashSet<User>();
        userSet.add(u1);
        geoStoreClient.insert(u1);
        userGeoStoreClient = createUserClient("u1","u1");
        int u1StatusR = -1;
        try{
            userGeoStoreClient.getData(sr.getId());
        }
        catch(UniformInterfaceException e){
            u1StatusR = e.getResponse().getStatus();
        }
        assertEquals(403,u1StatusR);
    }
    
    @Test
    public void testEVERYONEassignment(){
        RESTUserGroup ug = geoStoreClient.getUserGroup(GroupReservedNames.EVERYONE.toString());
        User u = geoStoreClient.getUser("user");
        
        int errorCode = -1;
        try{
            geoStoreClient.assignUserGroup(u.getId(), ug.getId());
        }
        catch(UniformInterfaceException e){
            errorCode = e.getResponse().getStatus();
        }
        assertEquals(404,errorCode);
        
        errorCode = -1;
        try{
            geoStoreClient.deassignUserGroup(u.getId(), ug.getId());
        }
        catch(UniformInterfaceException e){
            errorCode = e.getResponse().getStatus();
        }
        assertEquals(404,errorCode);
    }
    
    @Test
    public void testUserInitialization(){
        UserList ul = geoStoreClient.getUsers(1,100);
        assertEquals(2, ul.getList().size());
        for(RESTUser u : ul.getList()){
            assertNull(u.getGroupsNames());
        }
    }
    
    @Test
    public void everyoneGroupTest(){
        
        UserGroupList ugl = geoStoreClient.getUserGroups(0, 1000, true);
        assertEquals(1, ugl.getUserGroupList().size());
        assertEquals("everyone", ugl.getUserGroupList().get(0).getGroupName());
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
        
        // Trying to assign to user "u1" the special group "everyone" (that should have id = 1)
        // An error is expected
        RESTUserGroup ugEveryone = geoStoreClient.getUserGroup("everyone");
        assertNotNull(ugEveryone);
        assertEquals(0,geoStoreClient.getUser(uID).getGroups().size());
        int assignError = -1;
        try{
            geoStoreClient.assignUserGroup(uID, ugEveryone.getId());
        }
        catch(UniformInterfaceException e){
            assignError = e.getResponse().getStatus();
        }
        assertEquals(404, assignError);
        assertEquals(0,geoStoreClient.getUser(uID).getGroups().size());
        
        u1StatusR = -1;
        u1StatusW = -1;
        int updateStatus = -1;
        
        // Going to setup grants for group EVERYONE
        // Note that canRead=FALSE and canWrite=TRUE so I'm expect a BadRequestException
        ShortResourceList srl = new ShortResourceList();
        List<ShortResource> srlArray = new ArrayList<ShortResource>();
        srlArray.add(sr);
        srl.setList(srlArray);
        try{
            geoStoreClient.updateSecurityRules(srl, ugEveryone.getId(), false, true);
        }
        catch(UniformInterfaceException e){
            updateStatus = e.getResponse().getStatus();
        }
        assertEquals(400,updateStatus);
        // User updateSecurityRule in the right way
        geoStoreClient.updateSecurityRules(srl, ugEveryone.getId(), true, false);
        
        // Now "u1" should be able to READ but not to WRITE the created resource.
        userGeoStoreClient.getResource(sr.getId());
        try{
            userGeoStoreClient.updateResource(sr.getId(), new RESTResource());
        }
        catch(UniformInterfaceException e){
            u1StatusW = e.getResponse().getStatus();
        }
        assertEquals(403,u1StatusW);
           
    }
    
    @Test
    public void ListResourcesTest() {
        
        // Create a group
        UserGroup ug = new UserGroup();
        ug.setGroupName("g1");
        long gid = geoStoreClient.insertUserGroup(ug);
        
        UserGroup anotherGroup = new UserGroup();
        anotherGroup.setGroupName("g2");
        long anotherGid = geoStoreClient.insertUserGroup(anotherGroup);
        
        Set<UserGroup> ugroups = new HashSet<UserGroup>();
        ugroups.add(ug);
        
        // Create a user
        User u1 = new User();
        u1.setName("u1");
        u1.setNewPassword("u1");
        u1.setRole(Role.USER);
        u1.setGroups(ugroups);
        
        geoStoreClient.insert(u1);
        
        // Create a resource with the user "user". So user will be the owner
        createDefaultCategory();
        ShortResource sr = createAResource();
        ShortResource sr2 = createAResource();
        ShortResource sr3 = createAResource();
        ShortResource sr4 = createAResource();
        
        ShortResourceList srl = geoStoreUserClient.getAllShortResource(1, 1000);
        List<ShortResource> listG1 = new ArrayList<ShortResource>();
        List<ShortResource> listG2 = new ArrayList<ShortResource>();
        int i = 0;
        for(ShortResource r : srl.getList()){
            if(i<2){
                listG1.add(r);
            }else{
                listG2.add(r);
            }
            i++;
        }
        
        // Ok, now it's time to test something.
        GeoStoreClient u1Client = createUserClient("u1", "u1");
        
        // Since all resources inserted belong to user "user" and no groups security rules are added
        // the user "u1" won't see any resource neither as short resource
        ShortResourceList srlTmp = u1Client.getAllShortResource(1, 1000);
        assertNull(srlTmp.getList());
        
        // trying to get all the resource list the user will get an empty list
        SearchFilter filter = new FieldFilter(BaseField.NAME, "rest%", SearchOperator.LIKE);
        ResourceList rl = u1Client.searchResources(filter, -1, -1, false, false);
        assertNull(rl.getList());
        
        UserGroupList ugl = geoStoreClient.getUserGroups(0, 1000, true);
        long ug1_id = -1;
        long ug2_id = -1;
        for(RESTUserGroup tmp_ug : ugl.getUserGroupList()){
            if(tmp_ug.getGroupName().equalsIgnoreCase("g1")){
                ug1_id = tmp_ug.getId();
            }
            if(tmp_ug.getGroupName().equalsIgnoreCase("g2")){
                ug2_id = tmp_ug.getId();
            }
        }
        
        // Add group permissions to resources
        long errorCode = -1;
        try{
            geoStoreClient.updateSecurityRules(new ShortResourceList(listG1), 787687l, true, true);
        }
        catch(UniformInterfaceException e){
            errorCode = e.getResponse().getStatus();
        }
        assertEquals(404, errorCode);
        
        geoStoreClient.updateSecurityRules(new ShortResourceList(listG1), ug1_id, true, true);
        geoStoreClient.updateSecurityRules(new  ShortResourceList(listG2), ug2_id, true, true);
        
        // Now the situation should be changed: I should have access to 2 resources
        srl = u1Client.getAllShortResource(1, 1000);
        assertEquals(2, srl.getList().size());
        for(ShortResource r : srl.getList()){
            if(r.getId() == listG1.get(0).getId() || r.getId() == listG1.get(1).getId()){
                assertTrue(r.isCanDelete());
                assertTrue(r.isCanEdit());
            }
            else{
                assertTrue(!r.isCanDelete());
                assertTrue(!r.isCanEdit());
            }
        }
        
        rl = u1Client.searchResources(filter, -1, -1, false, false);
        assertEquals(2,rl.getList().size());
        
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
        
        // Create 2 user
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
        
        // Since "user" is the owner the users
        // "u1" and "u2" should have both READ and WRITE doesn't allowed because it isn't the owner of the reosource, it belong to at least one group and the resource doesn't have any groups rule security 
        try{
            u1Client.getResource(sr.getId(), true);
        }
        catch(UniformInterfaceException e){
            u1StatusR = e.getResponse().getStatus();
        }
        assertEquals(403,u1StatusR);
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
        
        ShortResourceList srlf = geoStoreClient.getAllShortResource(1, 1000);
        assertEquals(2, srlf.getList().size());
        geoStoreClient.updateSecurityRules(srll, gid, false, true);
        geoStoreClient.updateSecurityRules(srll, gid2, false, false);
        srlf = geoStoreClient.getAllShortResource(1, 1000);
        assertEquals(2, srlf.getList().size());
        
        // READ shouldn't allowed, WRITE allowed
        u1StatusW = -1;
        u1StatusR = -1;
        u2StatusR = -1;
        try{
            u1Client.getResource(sr.getId(), true);
        }
        catch(UniformInterfaceException e){
            u1StatusR = e.getResponse().getStatus();
        }
        assertEquals(403,u1StatusR);
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
        UserList users = geoStoreClient.getUsers(0, 1000);
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
        UserGroupList groups = geoStoreClient.getUserGroups(0, 1000, true);
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
