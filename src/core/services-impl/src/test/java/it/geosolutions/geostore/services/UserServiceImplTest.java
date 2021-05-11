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

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.GroupReservedNames;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.core.security.password.PwEncoder;

/**
 * Class UserServiceImplTest.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * 
 */
public class UserServiceImplTest extends ServiceTestBase {

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    public UserServiceImplTest() {

    }

    @Test
    public void testInsertDeleteUser() throws Exception {

        //
        // Creating and deleting user data
        //
        long userId = createUser("test", Role.USER, "tesPW");

        assertEquals(1, userService.getCount(null));
        assertTrue("Could not delete user", userService.delete(userId));
        assertEquals(0, userService.getCount(null));
    }

    @Test
    public void testUpdateLoadData() throws Exception {
        final String NAME = "name1";

        long userId = createUser(NAME, Role.USER, "testPW");

        assertEquals(1, userService.getCount(null));

        //
        // Updating User
        //
        {
            User loaded = userService.get(userId);
            assertNotNull(loaded);
            assertEquals(NAME, loaded.getName());
            assertTrue( PwEncoder.isPasswordValid(loaded.getPassword(),"testPW"));
            assertEquals(Role.USER, loaded.getRole());

            loaded.setNewPassword("testPW2");
            userService.update(loaded);
        }

        //
        // Loading User
        //
        {
            User loaded = userService.get(userId);
            assertNotNull(loaded);
            assertTrue(PwEncoder.isPasswordValid(loaded.getPassword(),"testPW2"));
        }

        //
        // Deleting User
        //
        {
            assertEquals(1, userService.getCount(null));
            userService.delete(userId);
            assertEquals(0, userService.getCount(null));
        }

    }
    
    @Test
    public void testGetByAttribute() throws Exception {
        UserAttribute attribute = new UserAttribute();
        String token = UUID.randomUUID().toString();
        attribute.setName("UUID");
        attribute.setValue(token);
        createUser("test", Role.USER, "tesPW", Arrays.asList(attribute));
        
        assertEquals(1, userService.getByAttribute(attribute).size());
    }

    @Test
    public void testGetByGroupId() throws Exception {
        long groupId = createGroup("testgroup");
        createUser("test", Role.USER, "tesPW", groupId);
        UserGroup group = new UserGroup();
        group.setId(groupId);
        Collection<User> users = userService.getByGroup(group);
        assertEquals(1, users.size());
    }
    
    @Test
    public void testGetByGroupName() throws Exception {
        long groupId = createGroup("testgroup");
        createUser("test", Role.USER, "tesPW", groupId);
        UserGroup group = new UserGroup();
        group.setGroupName("testgroup");
        Collection<User> users = userService.getByGroup(group);
        assertEquals(1, users.size());
    }
    
    @Test
    public void testUpdateByUserId() throws Exception {
        final String NAME = "name1";

        long userId = createUser(NAME, Role.USER, "testPW");

        assertEquals(1, userService.getCount(null));
        
        User loaded = userService.get(userId);
        assertNotNull(loaded);
        assertEquals(NAME, loaded.getName());
        assertTrue( PwEncoder.isPasswordValid(loaded.getPassword(),"testPW"));
        assertEquals(Role.USER, loaded.getRole());

        loaded.setNewPassword("testPW2");
        userService.update(loaded);
        
        loaded = userService.get(userId);
        assertNotNull(loaded);
        assertTrue(PwEncoder.isPasswordValid(loaded.getPassword(),"testPW2"));
    }
    
    @Test
    public void testUpdateWithGroups() throws Exception {
        final String NAME = "name1";

        long userId = createUser(NAME, Role.USER, "testPW");
        assertEquals(1, userService.getCount(null));
        
        createUserGroup("testgroup", new long[] {userId});
        
        User loaded = userService.get(userId);
        assertNotNull(loaded);
        assertEquals(NAME, loaded.getName());
        assertTrue( PwEncoder.isPasswordValid(loaded.getPassword(),"testPW"));
        assertEquals(Role.USER, loaded.getRole());
        assertEquals(1, loaded.getGroups().size());

        loaded.setNewPassword("testPW2");
        userService.update(loaded);
        
        loaded = userService.get(userId);
        assertNotNull(loaded);
        assertTrue(PwEncoder.isPasswordValid(loaded.getPassword(),"testPW2"));
        assertEquals(1, loaded.getGroups().size());
    }
    
    @Test
    public void testUpdateWithGroupsAndEveryone() throws Exception {
        final String NAME = "name1";

        createSpecialUserGroups();
        
        long userId = createUser(NAME, Role.USER, "testPW");
        assertEquals(1, userService.getCount(null));
        
        createUserGroup("testgroup", new long[] {userId});
        
        
        User loaded = userService.get(userId);
        assertNotNull(loaded);
        assertEquals(NAME, loaded.getName());
        assertTrue( PwEncoder.isPasswordValid(loaded.getPassword(),"testPW"));
        assertEquals(Role.USER, loaded.getRole());
        assertEquals(2, loaded.getGroups().size());

        loaded.setNewPassword("testPW2");
        userService.update(loaded);
        
        loaded = userService.get(userId);
        assertNotNull(loaded);
        assertTrue(PwEncoder.isPasswordValid(loaded.getPassword(),"testPW2"));
        assertEquals(2, loaded.getGroups().size());
    }
    
    @Test
    public void testUpdateByUserName() throws Exception {
        final String NAME = "name1";

        long userId = createUser(NAME, Role.USER, "testPW");

        assertEquals(1, userService.getCount(null));
        
        User loaded = userService.get(userId);
        assertNotNull(loaded);
        assertEquals(NAME, loaded.getName());
        assertTrue( PwEncoder.isPasswordValid(loaded.getPassword(),"testPW"));
        assertEquals(Role.USER, loaded.getRole());

        loaded.setNewPassword("testPW2");
        loaded.setId(-1L);
        userService.update(loaded);
        
        loaded = userService.get(userId);
        assertNotNull(loaded);
        assertTrue(PwEncoder.isPasswordValid(loaded.getPassword(),"testPW2"));
    }

}
