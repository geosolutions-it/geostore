/*
 *  Copyright (C) 2007 - 2025 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;

import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.IPRange;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.DataType;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.ServiceTestBase;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.ForbiddenErrorWebEx;
import it.geosolutions.geostore.services.rest.model.RESTAttribute;
import it.geosolutions.geostore.services.rest.model.SecurityRuleList;
import it.geosolutions.geostore.services.rest.utils.MockSecurityContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.ws.rs.core.SecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Class ResourceServiceImplTest.
 *
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 */
public class RESTResourceServiceImplTest extends ServiceTestBase {

    RESTResourceServiceImpl restService;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        restService = new RESTResourceServiceImpl();
        restService.setResourceService(resourceService);
        restService.setResourcePermissionService(resourcePermissionService);
        mockHttpRequestIpAddressAttribute("localhost");
    }

    @After
    public void cleanup() {
        RequestContextHolder.resetRequestAttributes();
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
        RESTAttribute restAttribute = new RESTAttribute();
        String NAME = "NAME";
        String VALUE = "VALUE";
        restAttribute.setName(NAME);
        restAttribute.setValue(VALUE);

        // attempt to update the attribute from rest service
        restService.updateAttribute(sc, resourceId, restAttribute);

        Resource sr = restService.get(sc, resourceId, false);

        // verify the attribute has been changed
        Attribute attribute = sr.getAttribute().get(0);
        assertEquals(attribute.getName(), NAME);
        assertEquals(attribute.getValue(), VALUE);
        assertEquals(attribute.getType(), DataType.STRING);

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
        restAttribute = new RESTAttribute();
        NAME = "NAME";
        VALUE = "VALUE1";
        restAttribute.setName(NAME);
        restAttribute.setValue(VALUE);

        // attempt to update the attribute from rest service
        restService.updateAttribute(sc, resourceId, restAttribute);

        sr = restService.get(sc, resourceId, false);

        // verify the attribute has been changed
        attribute = sr.getAttribute().get(0);
        assertEquals(attribute.getName(), NAME);
        assertEquals(attribute.getValue(), VALUE);
        assertEquals(attribute.getType(), DataType.STRING);

        assertEquals(sr.getCreator(), "u0");
        assertEquals(sr.getEditor(), "u1");
    }

    @Test
    public void testUpdateResourceSecurityRules_withoutPermissions() throws Exception {

        long resourceId = createResource("name1", "description1", "MAP");

        long userId = createUser("user", Role.USER, "user");
        SecurityContext sc = new MockSecurityContext(userService.get(userId));

        SecurityRule securityRule = new SecurityRuleBuilder().build();

        assertThrows(
                ForbiddenErrorWebEx.class,
                () ->
                        restService.updateSecurityRules(
                                sc, resourceId, new SecurityRuleList(List.of(securityRule))));
    }

    @Test
    public void testUpdateResource_ipRangeUpdate() throws Exception {

        long adminId = createUser("user", Role.ADMIN, "user");
        long resourceId = createResource("name1", "description1", "MAP");

        SecurityContext sc = new MockSecurityContext(userService.get(adminId));

        IPRange ipRange = new IPRange();
        ipRange.setCidr("192.168.1.1/24");
        ipRange.setDescription("range");

        SecurityRuleList securityRulelist =
                new SecurityRuleList(
                        List.of(new SecurityRuleBuilder().ipRanges(Set.of(ipRange)).build()));

        restService.updateSecurityRules(sc, resourceId, securityRulelist);

        List<SecurityRule> securityRules = resourceService.getSecurityRules(resourceId);

        assertEquals(1, securityRules.size());
        Set<IPRange> ipRanges = securityRules.get(0).getIpRanges();
        assertEquals(1, ipRanges.size());
        IPRange savedIpRange = new ArrayList<>(ipRanges).get(0);
        assertEquals(ipRange.getCidr(), savedIpRange.getCidr());
        assertEquals(ipRange.getDescription(), savedIpRange.getDescription());
    }

    @Test
    public void testUpdateResource_ipRangeUpdateWithInvalidCidrFormat() throws Exception {

        long adminId = createUser("user", Role.ADMIN, "user");
        long resourceId = createResource("name1", "description1", "MAP");

        SecurityContext sc = new MockSecurityContext(userService.get(adminId));

        IPRange invalidIPRange = new IPRange();
        invalidIPRange.setCidr("1.1.1.1");

        SecurityRuleList securityRulelist =
                new SecurityRuleList(
                        List.of(
                                new SecurityRuleBuilder()
                                        .ipRanges(Set.of(invalidIPRange))
                                        .build()));

        BadRequestWebEx badRequestWebEx =
                assertThrows(
                        BadRequestWebEx.class,
                        () -> restService.updateSecurityRules(sc, resourceId, securityRulelist));
        assertTrue(badRequestWebEx.getMessage().contains("Invalid CIDR format"));
    }

    @Test
    public void testUpdateResource_ipRangeUpdateWithInvalidCidr() throws Exception {

        long adminId = createUser("user", Role.ADMIN, "user");
        long resourceId = createResource("name1", "description1", "MAP");

        SecurityContext sc = new MockSecurityContext(userService.get(adminId));

        IPRange invalidIPRange = new IPRange();
        invalidIPRange.setCidr("a.0.b.1.xx/s");

        SecurityRuleList securityRulelist =
                new SecurityRuleList(
                        List.of(
                                new SecurityRuleBuilder()
                                        .ipRanges(Set.of(invalidIPRange))
                                        .build()));

        BadRequestWebEx badRequestWebEx =
                assertThrows(
                        BadRequestWebEx.class,
                        () -> restService.updateSecurityRules(sc, resourceId, securityRulelist));
        assertTrue(badRequestWebEx.getMessage().contains("Malformed IP address"));
    }

    @Test
    public void testUpdateResource_ipRangeUpdateWithMissingPrefix() throws Exception {

        long adminId = createUser("user", Role.ADMIN, "user");
        long resourceId = createResource("name1", "description1", "MAP");

        SecurityContext sc = new MockSecurityContext(userService.get(adminId));

        IPRange invalidIPRange = new IPRange();
        invalidIPRange.setCidr("192.165.1.5/");

        SecurityRuleList securityRulelist =
                new SecurityRuleList(
                        List.of(
                                new SecurityRuleBuilder()
                                        .ipRanges(Set.of(invalidIPRange))
                                        .build()));

        BadRequestWebEx badRequestWebEx =
                assertThrows(
                        BadRequestWebEx.class,
                        () -> restService.updateSecurityRules(sc, resourceId, securityRulelist));
        assertTrue(badRequestWebEx.getMessage().contains("Invalid CIDR format"));
    }

    @Test
    public void testUpdateResource_ipRangeUpdateWithInvalidIP() throws Exception {

        long adminId = createUser("user", Role.ADMIN, "user");
        long resourceId = createResource("name1", "description1", "MAP");

        SecurityContext sc = new MockSecurityContext(userService.get(adminId));

        IPRange invalidIPRange = new IPRange();
        invalidIPRange.setCidr("666.555.444.333/222");

        SecurityRuleList securityRulelist =
                new SecurityRuleList(
                        List.of(
                                new SecurityRuleBuilder()
                                        .ipRanges(Set.of(invalidIPRange))
                                        .build()));

        BadRequestWebEx badRequestWebEx =
                assertThrows(
                        BadRequestWebEx.class,
                        () -> restService.updateSecurityRules(sc, resourceId, securityRulelist));
        assertTrue(
                badRequestWebEx
                        .getMessage()
                        .contains(invalidIPRange.getCidr() + " IP Address error"));
    }

    @Test
    public void testUpdateResource_ipRangeUpdateWithInvalidCidrPrefix() throws Exception {

        long adminId = createUser("user", Role.ADMIN, "user");
        long resourceId = createResource("name1", "description1", "MAP");

        SecurityContext sc = new MockSecurityContext(userService.get(adminId));

        IPRange invalidIPRange = new IPRange();
        invalidIPRange.setCidr("1.1.1.1/555");

        SecurityRuleList securityRulelist =
                new SecurityRuleList(
                        List.of(
                                new SecurityRuleBuilder()
                                        .ipRanges(Set.of(invalidIPRange))
                                        .build()));

        BadRequestWebEx badRequestWebEx =
                assertThrows(
                        BadRequestWebEx.class,
                        () -> restService.updateSecurityRules(sc, resourceId, securityRulelist));
        assertTrue(
                badRequestWebEx
                        .getMessage()
                        .contains(invalidIPRange.getCidr() + " IP Address error"));
    }

    @Test
    public void testUpdateResource_ipRangeUpdateSanitizingInput() throws Exception {

        long adminId = createUser("user", Role.ADMIN, "user");
        long resourceId = createResource("name1", "description1", "MAP");

        SecurityContext sc = new MockSecurityContext(userService.get(adminId));

        IPRange ipRange = new IPRange();
        ipRange.setCidr("008.08.8.80/0");
        ipRange.setDescription("sanitize");

        SecurityRuleList securityRulelist =
                new SecurityRuleList(
                        List.of(new SecurityRuleBuilder().ipRanges(Set.of(ipRange)).build()));

        restService.updateSecurityRules(sc, resourceId, securityRulelist);

        List<SecurityRule> securityRules = resourceService.getSecurityRules(resourceId);

        assertEquals(1, securityRules.size());
        Set<IPRange> ipRanges = securityRules.get(0).getIpRanges();
        assertEquals(1, ipRanges.size());
        IPRange savedIpRange = new ArrayList<>(ipRanges).get(0);
        assertEquals("8.8.8.80/0", savedIpRange.getCidr());
        assertEquals(ipRange.getDescription(), savedIpRange.getDescription());
    }
}
