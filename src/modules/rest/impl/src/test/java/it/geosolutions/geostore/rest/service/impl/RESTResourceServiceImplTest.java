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

import it.geosolutions.geostore.core.model.*;
import it.geosolutions.geostore.core.model.enums.DataType;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.ServiceTestBase;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.impl.RESTResourceServiceImpl;
import it.geosolutions.geostore.services.rest.model.RESTAttribute;
import it.geosolutions.geostore.services.rest.utils.MockSecurityContext;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Class ResourceServiceImplTest.
 *
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 */
public class RESTResourceServiceImplTest extends ServiceTestBase {

    RESTResourceServiceImpl restService;
    long adminID;

    @Before
    public void setUp() throws BadRequestServiceEx, NotFoundServiceEx {
        restService = new RESTResourceServiceImpl();
        restService.setResourceService(resourceService);
    }

    @Test
    public void testUpdateResourceAttribute() throws Exception {
        // create a sample resource
        long resourceId = createResource("name1", "description1", "MAP");

        // insert fake admin user for security context
        long adminID = createUser("admin", Role.ADMIN, "admin");

        // create security context for the request
        SecurityContext sc = new MockSecurityContext(userService.get(adminID));

        // prepare request content
        RESTAttribute attribute = new RESTAttribute();
        String NAME = "NAME";
        String VALUE = "VALUE";
        attribute.setName(NAME);
        attribute.setValue(VALUE);

        // attempt to update the attribute from rest service
        restService.updateAttribute(sc, resourceId, attribute);

        // retrieve the modified resource
        Resource res = resourceService.get(resourceId);

        // verify the attribute has been changed
        Attribute a = res.getAttribute().get(0);
        assertEquals(a.getName(), NAME);
        assertEquals(a.getValue(), VALUE);
        assertEquals(a.getType(), DataType.STRING);
    }

    @Test
    public void testUpdateResource_editorUpdate() throws Exception {
        // insert fake user for security context
        long u0ID = createUser("u0", Role.USER, "p0");
        User user = new User();
        user.setId(u0ID);
        user.setName("u0");

        List<SecurityRule> rules = new ArrayList<>();

        SecurityRule rule = new SecurityRule();
        rule.setUser(user);
        rule.setCanRead(true);
        rule.setCanWrite(true);
        rules.add(rule);

        long groupId = createGroup("group1");
        UserGroup group = new UserGroup();
        group.setId(groupId);

        rule = new SecurityRule();
        rule.setCanRead(true);
        rule.setCanWrite(true);
        rule.setGroup(group);
        rules.add(rule);

        // create a sample resource
        long resourceId = createResource("name1", "description1", "MAP", rules);

        // create security context for the request
        SecurityContext sc = new MockSecurityContext(userService.get(u0ID));

        // prepare request content
        RESTAttribute attribute = new RESTAttribute();
        String NAME = "NAME";
        String VALUE = "VALUE";
        attribute.setName(NAME);
        attribute.setValue(VALUE);

        // attempt to update the attribute from rest service
        restService.updateAttribute(sc, resourceId, attribute);

        Resource sr = restService.get(sc, resourceId, false);

        assertEquals(sr.getCreator(), "u0");
        assertEquals(sr.getEditor(), "u0");

        // Update rule as "user1"
        // insert fake user for security context
        long u1ID = createUser("u1", Role.USER, "p1", groupId);
        user = new User();
        user.setId(u1ID);
        user.setName("u1");

        sc = new MockSecurityContext(userService.get(u1ID));

        // prepare request content
        attribute = new RESTAttribute();
        NAME = "NAME";
        VALUE = "VALUE1";
        attribute.setName(NAME);
        attribute.setValue(VALUE);

        // attempt to update the attribute from rest service
        restService.updateAttribute(sc, resourceId, attribute);

        sr = restService.get(sc, resourceId, false);

        assertEquals(sr.getCreator(), "u0");
        assertEquals(sr.getEditor(), "u1");
    }
}
