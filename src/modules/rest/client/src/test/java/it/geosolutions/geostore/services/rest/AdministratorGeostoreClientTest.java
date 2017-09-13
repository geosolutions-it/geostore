/*
 *  Copyright (C) 2016 GeoSolutions S.A.S.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
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
import it.geosolutions.geostore.services.rest.client.model.ExtGroupList;
import it.geosolutions.geostore.services.rest.model.CategoryList;
import it.geosolutions.geostore.services.rest.model.RESTCategory;
import it.geosolutions.geostore.services.rest.model.RESTResource;
import it.geosolutions.geostore.services.rest.model.RESTSecurityRule;
import it.geosolutions.geostore.services.rest.model.RESTStoredData;
import it.geosolutions.geostore.services.rest.model.RESTUser;
import it.geosolutions.geostore.services.rest.model.RESTUserGroup;
import it.geosolutions.geostore.services.rest.model.ResourceList;
import it.geosolutions.geostore.services.rest.model.SecurityRuleList;
import it.geosolutions.geostore.services.rest.model.ShortResourceList;
import it.geosolutions.geostore.services.rest.model.UserGroupList;
import it.geosolutions.geostore.services.rest.model.UserList;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import com.sun.jersey.api.client.UniformInterfaceException;
import java.util.Arrays;

public class AdministratorGeostoreClientTest {

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
            users = geoStoreClient.getUsers(0, 3);
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

        long uid1 = createUser("u1", Role.USER, "-");
        User u1 = geoStoreClient.getUser(uid1);
        assertNotNull(u1);

        long uid2 = createUser("u2", Role.USER, "-");
        User u2 = geoStoreClient.getUser(uid2);
        assertNotNull(u2);

        Set<User> userSet = new HashSet<>();
        userSet.add(u1);
        userSet.add(u2);
        
        UserGroup ug = new UserGroup();
        ug.setGroupName("usergroupTest1");
//!!        ug.setUsers(userSet);
        long ugid = geoStoreClient.insertUserGroup(ug);
        
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
        assertNotNull(usergroups);
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
//!!        ug2.setUsers(userSet);
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
        assertEquals(3, ul.getList().size()); // the 2 users added in this test, plus the admin

        geoStoreClient.deleteUser(uid1);
        geoStoreClient.deleteUser(uid2);
        ul = geoStoreClient.getUsers(0, 1000);
        assertEquals(1, ul.getList().size()); // only the admin 
    }
    
    @Test
    public void testStoredDataServices(){
        
        // Create a resource with Stored Data using the user "User"
        createDefaultCategory();

        createUser("user", Role.USER, "user");

        ShortResource sr = createAResource();

        GeoStoreClient userGeoStoreClient = createUserClient("user","user");
        String data = userGeoStoreClient.getData(sr.getId());
        assertEquals("we wish you a merry xmas and a happy new year", data);
        
        //try to get the related Stored Data with the user "u1", must not be possible due to authorization rules
        createUser("u1", Role.USER, "u1");
        userGeoStoreClient = createUserClient("u1","u1");
        try{
            userGeoStoreClient.getData(sr.getId());
            fail("Untrapped exception");
        }
        catch(UniformInterfaceException e){
            int u1StatusR = e.getResponse().getStatus();
            assertEquals(403,u1StatusR);
        }
    }
    
    @Test
    public void testEVERYONEassignmentResources(){
        // Create a resource with Stored Data using the user "User"
        createDefaultCategory();
        createUser("user", Role.USER, "user");
        ShortResource sr = createAResource();
        
        SecurityRuleList srlFinal = geoStoreUserClient.getSecurityRules(sr.getId());
        assertEquals(1, srlFinal.getList().size());
        
        SecurityRuleList srl = new SecurityRuleList();
        RESTSecurityRule everyoneRule = new RESTSecurityRule();
        Resource r = new Resource();
        r.setId(sr.getId());
        everyoneRule.setCanRead(true);
        everyoneRule.setCanWrite(true);
        
        RESTUserGroup everyoneGroup = geoStoreClient.getUserGroup(GroupReservedNames.EVERYONE.toString());
        RESTUserGroup ug = new RESTUserGroup();
        ug.setGroupName(GroupReservedNames.EVERYONE.toString());
        ug.setId(everyoneGroup.getId());
        everyoneRule.setGroup(ug);
        
        List<RESTSecurityRule> restSR = new ArrayList<RESTSecurityRule>();
        restSR.add(everyoneRule);
        restSR.add(srlFinal.getList().get(0));
        
        srl.setList(restSR);
        
        geoStoreUserClient.updateSecurityRules(sr.getId(), srl);
        
        srlFinal = geoStoreUserClient.getSecurityRules(sr.getId()); 
        assertEquals(2, srlFinal.getList().size());
        
    }
    
    @Test
    public void testEVERYONEassignmentUsers(){
        RESTUserGroup ug = geoStoreClient.getUserGroup(GroupReservedNames.EVERYONE.toString());
        createUser("user", Role.USER, "user");
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
        UserList ul = geoStoreClient.getUsers(0,100);
        assertEquals(1, ul.getList().size()); // admin only
        for(RESTUser u : ul.getList()){
            assertNull(u.getGroupsNames());
        }
    }
    
    @Test
    public void everyoneGroupTest(){
        
        UserGroupList ugl = geoStoreClient.getUserGroups(0, 1000, true);
        assertEquals(1, ugl.getUserGroupList().size());
        assertEquals("everyone", ugl.getUserGroupList().get(0).getGroupName());
        
        createUser("user", Role.USER, "user");
        createDefaultCategory();
        ShortResource sr = createAResource();

        long uID = createUser("u1", Role.USER, "u1");
        GeoStoreClient userGeoStoreClient = createUserClient("u1", "u1");
        
        
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
                
        // Create a user
        createUser("user", Role.USER, "user", ug);
        
        // Create a resource with the user "user". So user will be the owner
        createDefaultCategory();
        ShortResource sr = createAResource();
        ShortResource sr2 = createAResource();
        ShortResource sr3 = createAResource();
        ShortResource sr4 = createAResource();
        
        ShortResourceList srl = geoStoreUserClient.getAllShortResource(0, 1000);
        List<ShortResource> listG1 = new ArrayList<>();
        List<ShortResource> listG2 = new ArrayList<>();
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
        createUser("u1", Role.USER, "u1", ug);
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
        srl = u1Client.getAllShortResource(0, 1000);
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
        
        Set<UserGroup> ugroups = new HashSet<>();
        ugroups.add(ug);
        ugroups.add(anotherGroup);
        
        // Create 2 user
        createUser("u1", Role.USER, "u1");
        createUser("u2", Role.USER, "u2", ug, anotherGroup);
                
        GeoStoreClient u1Client = createUserClient("u1", "u1");
        GeoStoreClient u2Client = createUserClient("u2", "u2");
        
        // Create a resource with the user "user". So user will be the owner
        createUser("user", Role.USER, "user");
        createDefaultCategory();
        ShortResource sr = createAResource();
        ShortResource sr2 = createAResource();
        
        
        // Since "user" is the owner the users
        // "u1" and "u2" should not be allowed to READ or WRITE, because they are not the owner of the reosource, it belong to at least one group and the resource doesn't have any groups rule security
        try{
            u1Client.getResource(sr.getId(), true);
            fail("Untrapped exception");
        }
        catch(UniformInterfaceException e){
            assertEquals(403, e.getResponse().getStatus());
        }

        try{
            u2Client.getResource(sr.getId(), true);
            fail("Untrapped exception");
        }
        catch(UniformInterfaceException e){
            assertEquals(403, e.getResponse().getStatus());
        }

        try{
            u1Client.updateResource(sr.getId(), new RESTResource());
            fail("Untrapped exception");
        }
        catch(UniformInterfaceException e){
            assertEquals(403, e.getResponse().getStatus());
        }

        try{
            u2Client.updateResource(sr.getId(), new RESTResource());
            fail("Untrapped exception");
        }
        catch(UniformInterfaceException e){
            assertEquals(403, e.getResponse().getStatus());
        }

        
        // Update permissions for users belong to "usergroupTest1" ("u1") group
        // The effect of this service call will be: 
        // 1) The resource will be added with a group rule on usergroupTest1
        // 2) "u2" will have WRITE permission but not READ permissions
        // 3) Nothing will change for "u1"
        List<ShortResource> srl = new ArrayList<ShortResource>();
        srl.add(sr);
        ShortResourceList srll = new ShortResourceList(srl);
        
        ShortResourceList srlf = geoStoreClient.getAllShortResource(0, 1000);
        assertEquals(2, srlf.getList().size());
        geoStoreClient.updateSecurityRules(srll, gid, false, true);
        geoStoreClient.updateSecurityRules(srll, gid2, false, false);
        srlf = geoStoreClient.getAllShortResource(0, 1000);
        assertEquals(2, srlf.getList().size());
        
        // READ shouldn't allowed, WRITE allowed
        try{
            u1Client.getResource(sr.getId(), true);
        }
        catch(UniformInterfaceException e){
            assertEquals(403, e.getResponse().getStatus());
        }

        try{
            u2Client.getResource(sr.getId(), true);
        }
        catch(UniformInterfaceException e){
            assertEquals(403, e.getResponse().getStatus());
        }

        try{
            u1Client.updateResource(sr.getId(), new RESTResource());
        }
        catch(UniformInterfaceException e){
            assertEquals(403, e.getResponse().getStatus());
        }

        u2Client.updateResource(sr.getId(), new RESTResource());
    }
    
    @Test
    public void getAllGroupsWithoutEveryoneTest(){
        final int GROUP_NUM = 3;
        addSomeUserGroups(GROUP_NUM, "randomGroups");

        ExtGroupList res = geoStoreClient.searchUserGroup(0, 10, "*");
        assertEquals("Bad number of user group", GROUP_NUM, res.getCount());
    }

    @Test
    public void getAllGroupsWithEveryoneTest(){
        int grpCount = 3;
        addSomeUserGroups(grpCount, "randomGroups");

        ExtGroupList res = geoStoreClient.searchUserGroup(0, 10, "*", true);
        assertEquals(grpCount+1, res.getCount()); // +1: the everyone group
    }

    @Test
    public void searchGroupTest(){
        int grpNum = 4;
        int targetGrpNum = 2;
        String targetGrpPrefix = "target";
        addSomeUserGroups(grpNum, "smokeGrp");
        addSomeUserGroups(targetGrpNum, targetGrpPrefix);

        ExtGroupList searchResult = geoStoreClient.searchUserGroup(0, 10, "*" + targetGrpPrefix + "*");
        assertTrue(searchResult.getList().size() == targetGrpNum);
    }

    @Test
    public void noGroupsForNormalUserTest(){

        addSomeUserGroups(8, "randomGrp");

        createUser("user", Role.USER, "user");
        GeoStoreClient client = createUserClient("user", "user");

        ExtGroupList result = client.searchUserGroup(0, 10, "*");
        assertEquals(result.getCount(), 0);
    }

    @Test
    public void allGroupsOfAnUserTest(){
        int grpTestUserNum = 5, grpUUserNum = 3;
        String usrTestName = "test";
        String usrTestPasswd = "test";

        User testUser = new User();
        testUser.setName(usrTestName);
        testUser.setRole(Role.USER);
        testUser.setNewPassword(usrTestPasswd);

        Set<UserGroup> testUserGroups = createSomeGroups(grpTestUserNum, usrTestName);
        addSomeUserGroups(testUserGroups);
        testUser.setGroups(testUserGroups);
        geoStoreClient.insert(testUser);


        User u = new User();
        u.setName("u");
        u.setRole(Role.USER);

        Set<UserGroup> uGroups = createSomeGroups(grpUUserNum, "u");
        addSomeUserGroups(uGroups);
        u.setGroups(uGroups);
        geoStoreClient.insert(u);

        GeoStoreClient userClient = createUserClient(usrTestName, usrTestPasswd);

        ExtGroupList result = userClient.searchUserGroup(0, 10, "*");

        assertEquals(result.getCount(), grpTestUserNum);
    }

    @Test
    public void userGroupsPaginationTest(){
        int totalGrps = 10;
        int pageSize = 3;
        int expectedItems[] = {3, 3, 3, 1};
        ExtGroupList result;

        addSomeUserGroups(totalGrps, "paging");
        for(int page=0; page<expectedItems.length; page++){
            result = geoStoreClient.searchUserGroup(page*pageSize, pageSize, "*");
            assertTrue(expectedItems[page] == result.getList().size());
        }
    }

    /**
     * Generates some random user groups
     * @param amount the amount of user groups
     * @param namePrefix a string used as prefix in groups name and descriptions.
     * @return a Set of user groups.
     */
    protected Set<UserGroup> createSomeGroups(int amount, String namePrefix){
        Set<UserGroup> grps = new HashSet<UserGroup>();
        for(int i=0; i<amount; i++){
            UserGroup grp = new UserGroup();
            grp.setGroupName(namePrefix + i);
            grp.setDescription(namePrefix + i + "-Description");
            grps.add(grp);
        }
        return grps;
    }

    /**
     * Create n random UserGroups and insert them into db.
     * @param n the amount of random groups that will be created
     * @param namePrefix a string used as prefix in groups name and descriptions.
     */
    protected void addSomeUserGroups(int n, String namePrefix){
        for(UserGroup g : createSomeGroups(n, namePrefix)){
            geoStoreClient.insertUserGroup(g);
        }
    }

    /**
     * adds some UserGroup into db
     * @param groups set of UserGroups to insert into db.
     */
    protected void addSomeUserGroups(Set<UserGroup> groups){
        for(UserGroup g : groups){
            geoStoreClient.insertUserGroup(g);
        }
    }

    protected void removeAllUsers(GeoStoreClient client) {
        UserList users = geoStoreClient.getUsers(0, 1000);
        List<RESTUser> usersList = users.getList();
        usersList = (usersList == null)?new ArrayList<RESTUser>():usersList;
        for(RESTUser u : usersList){
            if(!("admin".equals(u.getName()))){
                try{
                    geoStoreClient.deleteUser(u.getId());
                }
                catch(Exception e){
                    LOGGER.error("Error removing " + u);
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
                LOGGER.error("Error removing " + ug);
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

        List<ShortAttribute> attrList = new ArrayList<>();
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
    
    protected long createUser(String name, Role role, String pw, UserGroup ...group) {

        User user = new User();
        user.setName(name);
        user.setRole(role);
        user.setNewPassword(pw);
        if(group != null) {
            user.setGroups(new HashSet(Arrays.asList(group)));
        }

        return geoStoreClient.insert(user);
    }
    
}
