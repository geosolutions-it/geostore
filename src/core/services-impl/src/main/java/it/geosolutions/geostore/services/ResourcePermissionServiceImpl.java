package it.geosolutions.geostore.services;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import it.geosolutions.geostore.core.model.IPRange;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

public class ResourcePermissionServiceImpl implements ResourcePermissionService {

    private static final Logger LOGGER = LogManager.getLogger(ResourcePermissionServiceImpl.class);

    private final BiPredicate<SecurityRule, User> resourceUserOwnership =
            (rule, user) ->
                    (user.getId().equals(rule.getUser().getId())
                     || user.getName() != null && user.getName().equals(rule.getUsername()));

    private final BiPredicate<SecurityRule, UserGroup> resourceGroupOwnership =
            (rule, group) ->
                    (group.getId().equals(rule.getGroup().getId())
                     || group.getGroupName() != null
                        && group.getGroupName().equals(rule.getGroupname()));

    private final BiPredicate<SecurityRule, User> resourceUserAccessWithReadPermission =
            (rule, user) ->
                    rule.getUser() != null
                    && resourceUserOwnership.test(rule, user)
                    && rule.isCanRead();

    private final BiPredicate<SecurityRule, UserGroup> resourceGroupOwnershipWithReadPermission =
            (rule, group) ->
                    rule.getGroup() != null
                    && resourceGroupOwnership.test(rule, group)
                    && rule.isCanRead();

    private final BiPredicate<SecurityRule, User> resourceUserAccessWithWritePermission =
            (rule, user) ->
                    rule.getUser() != null
                    && resourceUserOwnership.test(rule, user)
                    && rule.isCanWrite();

    private final BiPredicate<SecurityRule, UserGroup> resourceGroupOwnershipWithWritePermission =
            (rule, group) ->
                    rule.getGroup() != null
                    && resourceGroupOwnership.test(rule, group)
                    && rule.isCanWrite();

    @Override
    public boolean canResourceBeReadByUser(Resource resource, User user) {
        if (user.getRole().equals(Role.ADMIN)) {
            return true;
        }
        checkResourceSecurityRules(resource);
        return canUserAccessWithReadPermission(user, resource)
               || haveUserGroupsOwnershipWithReadPermission(user, resource);
    }

    private boolean canUserAccessWithReadPermission(User user, Resource resource) {
        return checkSecurityRulesAgainstUser(
                resource.getSecurity(), user, resourceUserAccessWithReadPermission);
    }

    private boolean haveUserGroupsOwnershipWithReadPermission(User user, Resource resource) {
        return checkResourceSecurityRulesAgainstUserGroup(
                user, resource, resourceGroupOwnershipWithReadPermission);
    }

    @Override
    public boolean canResourceBeWrittenByUser(Resource resource, User user) {
        if (!user.getRole().equals(Role.GUEST) && user.getRole().equals(Role.ADMIN)) {
            return true;
        }
        checkResourceSecurityRules(resource);
        return canUserAccessWithWritePermission(user, resource)
               || haveUserGroupsOwnershipWithWritePermission(user, resource);
    }

    private void checkResourceSecurityRules(Resource resource) {
        if (resource.getSecurity() == null) {
            throw new IllegalArgumentException(
                    "set resource security rules prior checking for permissions");
        }
    }

    private boolean canUserAccessWithWritePermission(User user, Resource resource) {
        return checkSecurityRulesAgainstUser(
                resource.getSecurity(), user, resourceUserAccessWithWritePermission);
    }

    private boolean checkSecurityRulesAgainstUser(
            List<SecurityRule> rules, User user, BiPredicate<SecurityRule, User> check) {
        return rules.stream()
                .filter(rule -> check.test(rule, user))
                .anyMatch(rule -> isUserIPAllowed(user, rule.getIpRanges()));
    }

    private boolean haveUserGroupsOwnershipWithWritePermission(User user, Resource resource) {
        return checkResourceSecurityRulesAgainstUserGroup(
                user, resource, resourceGroupOwnershipWithWritePermission);
    }

    private boolean checkResourceSecurityRulesAgainstUserGroup(
            User user, Resource resource, BiPredicate<SecurityRule, UserGroup> check) {
        return user.getGroups().stream()
                .anyMatch(
                        group ->
                                checkSecurityRulesAgainstUserGroup(
                                        resource.getSecurity(), user, group, check));
    }

    private boolean checkSecurityRulesAgainstUserGroup(
            List<SecurityRule> rules,
            User user,
            UserGroup group,
            BiPredicate<SecurityRule, UserGroup> check) {
        return rules.stream()
                .filter(rule -> check.test(rule, group))
                .anyMatch(rule -> isUserIPAllowed(user, rule.getIpRanges()));
    }

    private boolean isUserIPAllowed(User user, Set<IPRange> ipRanges) {
        if (ipRanges == null || ipRanges.isEmpty()) {
            return true;
        }

        boolean userAllowed =
                ipRanges.stream()
                        .peek(System.out::println)
                        .anyMatch(ipRange -> isUserIpInRange(user, ipRange));

        if (!userAllowed) {
            LOGGER.debug("User not allowed to access resource due to IP address restriction");
        }

        return userAllowed;
    }

    private boolean isUserIpInRange(User user, IPRange ipRange) {
        try {
            IPAddress cidr = new IPAddressString(ipRange.getCidr()).getAddress().toPrefixBlock();
            IPAddress userIPAddress = new IPAddressString(user.getIpAddress()).getAddress();
            return cidr.contains(userIPAddress);
        } catch (Exception e) {
            throw new RuntimeException(
                    "An error occurred while checking IP-based security constraints", e);
        }
    }
}
