package it.geosolutions.geostore.services;

import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import java.util.List;
import java.util.function.BiFunction;

public class ResourcePermissionServiceImpl implements ResourcePermissionService {

    private final BiFunction<SecurityRule, User, Boolean> resourceUserOwnership =
            (rule, user) ->
                    (user.getId().equals(rule.getUser().getId())
                            || user.getName() != null && user.getName().equals(rule.getUsername()));

    private final BiFunction<SecurityRule, UserGroup, Boolean> resourceGroupOwnership =
            (rule, group) ->
                    (group.getId().equals(rule.getGroup().getId())
                            || group.getGroupName() != null
                                    && group.getGroupName().equals(rule.getGroupname()));

    private final BiFunction<SecurityRule, User, Boolean> resourceUserOwnershipWithReadPermission =
            (rule, user) ->
                    rule.getUser() != null
                            && resourceUserOwnership.apply(rule, user)
                            && rule.isCanRead();

    private final BiFunction<SecurityRule, UserGroup, Boolean>
            resourceGroupOwnershipWithReadPermission =
                    (rule, group) ->
                            rule.getGroup() != null
                                    && resourceGroupOwnership.apply(rule, group)
                                    && rule.isCanRead();

    private final BiFunction<SecurityRule, User, Boolean> resourceUserOwnershipWithWritePermission =
            (rule, user) ->
                    rule.getUser() != null
                            && resourceUserOwnership.apply(rule, user)
                            && rule.isCanWrite();

    private final BiFunction<SecurityRule, UserGroup, Boolean>
            resourceGroupOwnershipWithWritePermission =
                    (rule, group) ->
                            rule.getGroup() != null
                                    && resourceGroupOwnership.apply(rule, group)
                                    && rule.isCanWrite();

    @Override
    public boolean canResourceBeReadByUser(Resource resource, User user) {
        return user.getRole().equals(Role.ADMIN)
                || isUserOwnerWithReadPermission(user, resource)
                || haveUserGroupsOwnershipWithReadPermission(user, resource);
    }

    private boolean isUserOwnerWithReadPermission(User user, Resource resource) {
        checkResourceSecurityRules(resource);
        return checkSecurityRulesAgainstUser(
                resource.getSecurity(), user, resourceUserOwnershipWithReadPermission);
    }

    private boolean haveUserGroupsOwnershipWithReadPermission(User user, Resource resource) {
        return checkResourceSecurityRulesAgainstUserGroup(
                user, resource, resourceGroupOwnershipWithReadPermission);
    }

    @Override
    public boolean canResourceBeWrittenByUser(Resource resource, User user) {
        return !user.getRole().equals(Role.GUEST)
                && (user.getRole().equals(Role.ADMIN)
                        || isUserOwnerWithWritePermission(user, resource)
                        || haveUserGroupsOwnershipWithWritePermission(user, resource));
    }

    private boolean isUserOwnerWithWritePermission(User user, Resource resource) {
        checkResourceSecurityRules(resource);
        return checkSecurityRulesAgainstUser(
                resource.getSecurity(), user, resourceUserOwnershipWithWritePermission);
    }

    private void checkResourceSecurityRules(Resource resource) {
        if (resource.getSecurity() == null) {
            throw new IllegalArgumentException(
                    "set resource security rules prior checking for permissions");
        }
    }

    private boolean haveUserGroupsOwnershipWithWritePermission(User user, Resource resource) {
        return checkResourceSecurityRulesAgainstUserGroup(
                user, resource, resourceGroupOwnershipWithWritePermission);
    }

    private boolean checkResourceSecurityRulesAgainstUserGroup(
            User user, Resource resource, BiFunction<SecurityRule, UserGroup, Boolean> check) {
        return user.getGroups().stream()
                .anyMatch(
                        group ->
                                checkSecurityRulesAgainstUserGroup(
                                        resource.getSecurity(), group, check));
    }

    private boolean checkSecurityRulesAgainstUser(
            List<SecurityRule> rules, User user, BiFunction<SecurityRule, User, Boolean> check) {
        return rules.stream().anyMatch(rule -> check.apply(rule, user));
    }

    private boolean checkSecurityRulesAgainstUserGroup(
            List<SecurityRule> rules,
            UserGroup group,
            BiFunction<SecurityRule, UserGroup, Boolean> check) {
        return rules.stream().anyMatch(rule -> check.apply(rule, group));
    }
}
