package it.geosolutions.geostore.services;

import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.enums.Role;
import java.util.List;
import java.util.function.BiFunction;

public class PermissionServiceImpl implements PermissionService {

    private final BiFunction<SecurityRule, Resource, Boolean> resourceOwnership =
            (rule, resource) -> resource.getId().equals(rule.getResource().getId());
    private final BiFunction<SecurityRule, Resource, Boolean> resourceOwnershipWithWritePermission =
            (rule, resource) -> resourceOwnership.apply(rule, resource) && rule.isCanWrite();

    @Override
    public boolean isResourceAvailableForUser(Resource resource, User user) {
        return resource.isAdvertised()
                || user.getRole().equals(Role.ADMIN)
                || isUserOwner(user, resource);
    }

    @Override
    public boolean isUserOwner(User user, Resource resource) {
        if (user.getSecurity() == null) {
            throw new IllegalArgumentException(
                    "set user security rules prior checking for ownership");
        }

        return user.getSecurity().stream()
                .anyMatch(rule -> resourceOwnership.apply(rule, resource));
    }

    @Override
    public boolean canUserAccessResource(User user, Resource resource) {
        return !user.getRole().equals(Role.GUEST)
                && (user.getRole().equals(Role.ADMIN)
                        || isUserOwnerWithWritePermission(user, resource)
                        || haveUserGroupsWritePermission(user, resource));
    }

    private boolean isUserOwnerWithWritePermission(User user, Resource resource) {
        if (user.getSecurity() == null) {
            throw new IllegalArgumentException(
                    "set user security rules prior checking for ownership");
        }
        return checkSecurityRulesAgainstResource(
                user.getSecurity(), resource, resourceOwnershipWithWritePermission);
    }

    private boolean haveUserGroupsWritePermission(User user, Resource resource) {
        return user.getGroups().stream()
                .anyMatch(
                        group ->
                                checkSecurityRulesAgainstResource(
                                        group.getSecurity(),
                                        resource,
                                        resourceOwnershipWithWritePermission));
    }

    private boolean checkSecurityRulesAgainstResource(
            List<SecurityRule> rules,
            Resource resource,
            BiFunction<SecurityRule, Resource, Boolean> check) {
        return rules.stream().anyMatch(rule -> check.apply(rule, resource));
    }
}
