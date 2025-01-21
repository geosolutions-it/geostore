package it.geosolutions.geostore.services;

import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.User;

public interface PermissionService {
    /**
     * This method allows us to know if we filter out "unadvertised" resources for
     * non-admin/non-owners, keeping only owned resources.
     *
     * <p>Be aware to fetch the user security rules prior to call this method.
     *
     * @param resource
     * @param user
     * @return <code>true</code> if the resource should be visible to the user, <code>false</code>
     *     otherwise
     * @throws IllegalArgumentException if the user security rules have not been initialized
     *     properly
     */
    boolean isResourceAvailableForUser(Resource resource, User user);

    /**
     * Check if the user has at least one {@link it.geosolutions.geostore.core.model.SecurityRule}
     * associated in which he is the user.
     *
     * <p>Be aware to fetch the user security rules prior to call this method.
     *
     * @param user
     * @param resource
     * @return @return <code>true</code> if the user is the owner of the resource, <code>false
     * </code> otherwise
     * @throws IllegalArgumentException if the user security rules have not been initialized
     *     properly
     */
    boolean isUserOwner(User user, Resource resource);

    /**
     * Verifies whether the user or any of their groups is the owner of the resource and has read
     * permissions on it.
     *
     * <p>Be aware to fetch the user security rules prior to call this method.
     *
     * @param user
     * @param resourceId
     * @return <code>true</code> if the user can read the resource, <code>false</code> otherwise
     * @throws IllegalArgumentException if the user security rules have not been initialized
     *     properly
     */
    boolean canUserReadResource(User user, Long resourceId);

    /**
     * Verifies whether the user or any of their groups is the owner of the resource and has write
     * permissions on it.
     *
     * <p>GUEST users can not access to the delete and edit (resource, data blob is editable)
     * services, so only admins and authenticated users with write permissions can.
     *
     * <p>Be aware to fetch the user security rules prior to call this method.
     *
     * @param user
     * @param resource
     * @return <code>true</code> if the user can write the resource, <code>false</code> otherwise
     * @throws IllegalArgumentException if the user security rules have not been initialized
     *     properly
     */
    boolean canUserWriteResource(User user, Resource resource);

    /**
     * Verifies whether the user or any of their groups is the owner of the resource and has both
     * read and write permissions on it.
     *
     * <p>Be aware to fetch the user security rules prior to call this method.
     *
     * @param user
     * @param resource
     * @return <code>true</code> if the user can read and write the resource, <code>false</code>
     *     otherwise
     * @throws IllegalArgumentException if the user security rules have not been initialized
     *     properly
     */
    boolean canUserReadAndWriteResource(User user, Resource resource);
}
