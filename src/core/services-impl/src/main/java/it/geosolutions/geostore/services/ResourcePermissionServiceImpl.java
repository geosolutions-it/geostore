package it.geosolutions.geostore.services;

import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.enums.Role;
import java.util.List;
import java.util.function.BiFunction;

public class ResourcePermissionServiceImpl implements ResourcePermissionService {

    private final BiFunction<SecurityRule, Resource, Boolean> resourceOwnership =
            (rule, resource) -> resource.getId().equals(rule.getResource().getId());
    private final BiFunction<SecurityRule, Resource, Boolean> resourceOwnershipWithReadPermission =
            (rule, resource) -> resourceOwnership.apply(rule, resource) && rule.isCanRead();
    private final BiFunction<SecurityRule, Resource, Boolean> resourceOwnershipWithWritePermission =
            (rule, resource) -> resourceOwnership.apply(rule, resource) && rule.isCanWrite();
    private final BiFunction<SecurityRule, Resource, Boolean>
            resourceOwnershipWithReadAndWritePermission =
                    (rule, resource) ->
                            resourceOwnershipWithWritePermission.apply(rule, resource)
                                    && resourceOwnershipWithReadPermission.apply(rule, resource);

    @Override
    public boolean canUserReadResource(User user, Long resourceId) {
        Resource resource = new Resource();
        resource.setId(resourceId);

        return user.getRole().equals(Role.ADMIN)
                || isUserOwnerWithReadPermission(user, resource)
                || haveUserGroupsOwnershipWithReadPermission(user, resource);
    }

    private boolean isUserOwnerWithReadPermission(User user, Resource resource) {
        checkUserSecurityRules(user);
        return checkSecurityRulesAgainstResource(
                user.getSecurity(), resource, resourceOwnershipWithReadPermission);
    }

    private boolean haveUserGroupsOwnershipWithReadPermission(User user, Resource resource) {
        return checkUserGroupsSecurityRulesAgainstResource(
                user, resource, resourceOwnershipWithReadPermission);
    }

    @Override
    public boolean canUserWriteResource(User user, Resource resource) {
        return !user.getRole().equals(Role.GUEST)
                && (user.getRole().equals(Role.ADMIN)
                        || isUserOwnerWithWritePermission(user, resource)
                        || haveUserGroupsOwnershipWithWritePermission(user, resource));
    }

    private boolean isUserOwnerWithWritePermission(User user, Resource resource) {
        checkUserSecurityRules(user);
        return checkSecurityRulesAgainstResource(
                user.getSecurity(), resource, resourceOwnershipWithWritePermission);
    }

    private boolean haveUserGroupsOwnershipWithWritePermission(User user, Resource resource) {
        return checkUserGroupsSecurityRulesAgainstResource(
                user, resource, resourceOwnershipWithWritePermission);
    }

    @Override
    public boolean canUserReadAndWriteResource(User user, Resource resource) {
        return user.getRole().equals(Role.ADMIN)
                || isUserOwnerWithReadAndWritePermission(user, resource)
                || haveUserGroupOwnershipWithReadAndWritePermission(user, resource);
    }

    private boolean isUserOwnerWithReadAndWritePermission(User user, Resource resource) {
        checkUserSecurityRules(user);
        return checkSecurityRulesAgainstResource(
                user.getSecurity(), resource, resourceOwnershipWithReadAndWritePermission);
    }

    private void checkUserSecurityRules(User user) {
        if (user.getSecurity() == null) {
            throw new IllegalArgumentException(
                    "set user security rules prior checking for permissions");
        }
    }

    private boolean haveUserGroupOwnershipWithReadAndWritePermission(User user, Resource resource) {
        return checkUserGroupsSecurityRulesAgainstResource(
                user, resource, resourceOwnershipWithReadAndWritePermission);
    }

    private boolean checkUserGroupsSecurityRulesAgainstResource(
            User user, Resource resource, BiFunction<SecurityRule, Resource, Boolean> check) {
        return user.getGroups().stream()
                .anyMatch(
                        group ->
                                checkSecurityRulesAgainstResource(
                                        group.getSecurity(), resource, check));
    }

    private boolean checkSecurityRulesAgainstResource(
            List<SecurityRule> rules,
            Resource resource,
            BiFunction<SecurityRule, Resource, Boolean> check) {
        return rules.stream().anyMatch(rule -> check.apply(rule, resource));
    }
}
