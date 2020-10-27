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
package it.geosolutions.geostore.rest.service.impl;

import java.util.List;

import javax.ws.rs.core.SecurityContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.ServiceTestBase;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.impl.RESTUserGroupServiceImpl;
import it.geosolutions.geostore.services.rest.model.RESTUserGroup;
import it.geosolutions.geostore.services.rest.model.UserGroupList;
import it.geosolutions.geostore.services.rest.utils.MockSecurityContext;

/**
 * Class ResourceServiceImplTest.
 *
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 *
 */
public class RESTUserGroupServiceImplTest extends ServiceTestBase {

	RESTUserGroupServiceImpl restService;
	long adminID;

	@Before
    public void setUp() throws BadRequestServiceEx, NotFoundServiceEx {
       restService = new RESTUserGroupServiceImpl();
       restService.setUserGroupService(userGroupService);
       restService.setUserService(userService);
    }
	
	@After
	public void tearDown() throws Exception {
		removeAll();
	}

    @Test
    public void testGetAllWithUsers() throws Exception {
        // create some sample users 
    	long adminID = createUser("admin", Role.ADMIN, "admin");
    	long userID = createUser("user", Role.USER, "user");

    	// create a some sample usergroup
    	createUserGroup("group", new long[] {adminID, userID});
        // create security context for the request
        SecurityContext sc = new MockSecurityContext(userService.get(adminID));

        // retrieve the list of usergroups (with users flag set to true)
        UserGroupList res = restService.getAll(sc, 0, 1000, true, true);
        List<RESTUserGroup> groups = res.getUserGroupList();
        assertEquals(1, groups.size());
        RESTUserGroup group = groups.get(0);
        assertEquals(2, group.getRestUsers().getList().size());
    }
    
    @Test
    public void testGetAllWithoutUsers() throws Exception {
        // create some sample users 
    	long adminID = createUser("admin", Role.ADMIN, "admin");
    	long userID = createUser("user", Role.USER, "user");

    	// create a some sample usergroup
    	createUserGroup("group", new long[] {adminID, userID});
        // create security context for the request
        SecurityContext sc = new MockSecurityContext(userService.get(adminID));

        // retrieve the list of usergroups (with users flag set to false)
        UserGroupList res = restService.getAll(sc, 0, 1000, true, false);
        List<RESTUserGroup> groups = res.getUserGroupList();
        assertEquals(1, groups.size());
        RESTUserGroup group = groups.get(0);
        assertEquals(0, group.getRestUsers().getList().size());
    }
}
