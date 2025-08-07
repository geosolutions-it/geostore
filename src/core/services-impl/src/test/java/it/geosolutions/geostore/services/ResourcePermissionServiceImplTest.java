/*  Copyright (C) 2007 - 2025 GeoSolutions S.A.S.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import it.geosolutions.geostore.core.model.IPRange;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class ResourcePermissionServiceImplTest {

    private ResourcePermissionServiceImpl service;

    @Before
    public void setUp() {
        service = new ResourcePermissionServiceImpl();
    }

    @Test
    public void testCanReadByUsernameMatch() {
        // Create a user with name "alice" and a dummy ID
        User user = new User();
        user.setId(100L);
        user.setName("alice");
        user.setRole(Role.USER);

        // Create a security rule: mismatch on user ID, but match on username
        SecurityRule rule = new SecurityRule();
        User ruleUser = new User();
        ruleUser.setId(999L);
        rule.setUser(ruleUser);
        rule.setUsername("alice");
        rule.setCanRead(true);

        Resource resource = new Resource();
        resource.setSecurity(Collections.singletonList(rule));

        // Assert that read is allowed via username matching
        assertTrue(
                "User should have read access via username match",
                service.canResourceBeReadByUser(resource, user));
    }

    @Test
    public void testCanReadByGroupnameMatch() {
        // Create a user and assign to a group named "editors"
        UserGroup group = new UserGroup();
        group.setId(10L);
        group.setGroupName("editors");

        User user = new User();
        user.setId(200L);
        user.setName("bob");
        user.setRole(Role.USER);
        user.setGroups(Collections.singleton(group));

        // Create a security rule: mismatch on group ID, but match on groupname
        SecurityRule rule = new SecurityRule();
        UserGroup ruleGroup = new UserGroup();
        ruleGroup.setId(888L);
        rule.setGroup(ruleGroup);
        rule.setGroupname("editors");
        rule.setCanRead(true);

        Resource resource = new Resource();
        resource.setSecurity(Collections.singletonList(rule));

        // Assert that read is allowed via groupname matching
        assertTrue(
                "User should have read access via groupname match",
                service.canResourceBeReadByUser(resource, user));
    }

    @Test
    public void testUserCanAccessByIPAddress() {
        // Create a user
        User user = new User();
        user.setId(100L);
        user.setRole(Role.USER);
        user.setIpAddress("1.2.3.4");

        // Create IP range for the security rule that contains user's IP address
        IPRange ipRange = new IPRange();
        ipRange.setCidr("1.2.3.0/24");

        // Create a security rule with read and write permissions
        SecurityRule rule = new SecurityRule();
        rule.setUser(user);
        rule.setCanRead(true);
        rule.setCanWrite(true);
        rule.setIpRanges(Set.of(ipRange));

        Resource resource = new Resource();
        resource.setSecurity(Collections.singletonList(rule));

        // Assert that access is allowed
        assertTrue(service.canResourceBeReadByUser(resource, user));
        assertTrue(service.canResourceBeWrittenByUser(resource, user));
    }

    @Test
    public void testUserCannotAccessByIPAddress() {
        // Create a user
        User user = new User();
        user.setId(100L);
        user.setRole(Role.USER);
        user.setIpAddress("127.0.0.1");
        user.setGroups(Set.of());

        // Create IP range for the security rule that does not contain user's IP address
        IPRange ipRange = new IPRange();
        ipRange.setCidr("1.2.3.4/32");

        // Create a security rule with read and write permissions
        SecurityRule rule = new SecurityRule();
        rule.setUser(user);
        rule.setCanRead(true);
        rule.setCanWrite(true);
        rule.setIpRanges(Set.of(ipRange));

        Resource resource = new Resource();
        resource.setSecurity(Collections.singletonList(rule));

        // Assert that access is not allowed
        assertFalse(service.canResourceBeReadByUser(resource, user));
        assertFalse(service.canResourceBeWrittenByUser(resource, user));
    }

    @Test
    public void testUserInGroupCanAccessByIPAddress() {
        // Create a group
        UserGroup group = new UserGroup();
        group.setId(888L);

        // Create a user in the group
        User user = new User();
        user.setId(100L);
        user.setRole(Role.USER);
        user.setIpAddress("1.2.3.4");
        user.setGroups(Set.of(group));

        // Create IP range for the security rule that contains user's IP address
        IPRange ipRange = new IPRange();
        ipRange.setCidr("1.2.3.0/24");

        // Create a group security rule with read and write permissions
        SecurityRule rule = new SecurityRule();
        rule.setGroup(group);
        rule.setCanRead(true);
        rule.setCanWrite(true);
        rule.setIpRanges(Set.of(ipRange));

        Resource resource = new Resource();
        resource.setSecurity(Collections.singletonList(rule));

        // Assert that access is allowed
        assertTrue(service.canResourceBeReadByUser(resource, user));
        assertTrue(service.canResourceBeWrittenByUser(resource, user));
    }

    @Test
    public void testUserInGroupCannotAccessByIPAddress() {
        // Create a group
        UserGroup group = new UserGroup();
        group.setId(888L);

        // Create a user in the group
        User user = new User();
        user.setId(100L);
        user.setRole(Role.USER);
        user.setIpAddress("1.2.3.4");
        user.setGroups(Set.of(group));

        // Create IP range for the security rule that does not contain user's IP address
        IPRange ipRange = new IPRange();
        ipRange.setCidr("121.52.23.220/15");

        // Create a group security rule with read and write permissions
        SecurityRule rule = new SecurityRule();
        rule.setGroup(group);
        rule.setCanRead(true);
        rule.setCanWrite(true);
        rule.setIpRanges(Set.of(ipRange));

        Resource resource = new Resource();
        resource.setSecurity(Collections.singletonList(rule));

        // Assert that access is not allowed
        assertFalse(service.canResourceBeReadByUser(resource, user));
        assertFalse(service.canResourceBeWrittenByUser(resource, user));
    }

    @Test
    public void testUserCanAccessIfIPRangeAppliesToAnotherRule() {
        // Create a user
        User user = new User();
        user.setId(100L);
        user.setRole(Role.USER);
        user.setIpAddress("127.0.0.1");
        user.setGroups(Set.of());

        // Create a group
        UserGroup group = new UserGroup();
        group.setId(888L);

        // Create IP range for the security rule that does not contain user's IP address
        IPRange ipRange = new IPRange();
        ipRange.setCidr("1.2.3.4/32");

        // Create a security rule with an IPRange for the group
        SecurityRule ruleWithIPRange = new SecurityRule();
        ruleWithIPRange.setGroup(group);
        ruleWithIPRange.setCanWrite(true);
        ruleWithIPRange.setIpRanges(Set.of(ipRange));

        // Create a security rule for the user without an IPRange
        SecurityRule ruleWithoutIPRange = new SecurityRule();
        ruleWithoutIPRange.setUser(user);
        ruleWithoutIPRange.setCanRead(true);
        ruleWithoutIPRange.setCanWrite(true);

        Resource resource = new Resource();
        resource.setSecurity(List.of(ruleWithIPRange, ruleWithoutIPRange));

        // Assert that access is allowed
        assertTrue(service.canResourceBeReadByUser(resource, user));
        assertTrue(service.canResourceBeWrittenByUser(resource, user));
    }

    @Test
    public void testPermissionsWithDefaultNetworkIPAddress() {
        User user = new User();
        user.setId(100L);
        user.setRole(Role.USER);
        user.setIpAddress("15.222.30.111");

        IPRange ipRange = new IPRange();
        ipRange.setCidr("0.0.0.0/0");

        SecurityRule rule = new SecurityRule();
        rule.setUser(user);
        rule.setCanRead(true);
        rule.setCanWrite(true);
        rule.setIpRanges(Set.of(ipRange));

        Resource resource = new Resource();
        resource.setSecurity(Collections.singletonList(rule));

        assertTrue(service.canResourceBeReadByUser(resource, user));
        assertTrue(service.canResourceBeWrittenByUser(resource, user));
    }

    @Test
    public void testPermissionsWithCidrEqualsIPAddress() {
        User user = new User();
        user.setId(100L);
        user.setRole(Role.USER);
        user.setIpAddress("192.168.1.0");

        IPRange ipRange = new IPRange();
        ipRange.setCidr("192.168.1.0/32");

        SecurityRule rule = new SecurityRule();
        rule.setUser(user);
        rule.setCanRead(true);
        rule.setCanWrite(true);
        rule.setIpRanges(Set.of(ipRange));

        Resource resource = new Resource();
        resource.setSecurity(Collections.singletonList(rule));

        assertTrue(service.canResourceBeReadByUser(resource, user));
        assertTrue(service.canResourceBeWrittenByUser(resource, user));
    }

    @Test
    public void testPermissionsWithMultipleCidrs() {
        User user = new User();
        user.setId(100L);
        user.setRole(Role.USER);
        user.setIpAddress("122.15.55.34");

        IPRange ipRangeExclusive = new IPRange();
        ipRangeExclusive.setCidr("192.168.1.0/12");
        IPRange ipRangeInclusive = new IPRange();
        ipRangeInclusive.setCidr("122.15.0.0/16");

        SecurityRule rule = new SecurityRule();
        rule.setUser(user);
        rule.setCanRead(true);
        rule.setCanWrite(true);
        rule.setIpRanges(Set.of(ipRangeExclusive, ipRangeInclusive));

        Resource resource = new Resource();
        resource.setSecurity(Collections.singletonList(rule));

        assertTrue(service.canResourceBeReadByUser(resource, user));
        assertTrue(service.canResourceBeWrittenByUser(resource, user));
    }
}
