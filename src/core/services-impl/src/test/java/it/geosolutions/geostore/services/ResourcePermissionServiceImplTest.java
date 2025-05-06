package it.geosolutions.geostore.services;

import static org.junit.Assert.*;

import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import java.util.Collections;
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
}
