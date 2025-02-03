/* ====================================================================
 *
 * Copyright (C) 2007 - 2016 GeoSolutions S.A.S.
 * http://www.geo-solutions.it
 *
 * GPLv3 + Classpath exception
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 *
 * ====================================================================
 *
 * This software consists of voluntary contributions made by developers
 * of GeoSolutions.  For more information on GeoSolutions, please see
 * <http://www.geo-solutions.it/>.
 *
 */
package it.geosolutions.geostore.services.rest.impl;

import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.ResourcePermissionService;
import it.geosolutions.geostore.services.ResourceService;
import it.geosolutions.geostore.services.SecurityService;
import it.geosolutions.geostore.services.UserGroupService;
import it.geosolutions.geostore.services.dto.ResourceSearchParameters;
import it.geosolutions.geostore.services.dto.ShortAttribute;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.AndFilter;
import it.geosolutions.geostore.services.dto.search.BaseField;
import it.geosolutions.geostore.services.dto.search.CategoryFilter;
import it.geosolutions.geostore.services.dto.search.FieldFilter;
import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.dto.search.SearchOperator;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.InternalErrorServiceEx;
import it.geosolutions.geostore.services.model.ExtGroupList;
import it.geosolutions.geostore.services.model.ExtResource;
import it.geosolutions.geostore.services.model.ExtResourceList;
import it.geosolutions.geostore.services.model.ExtShortResource;
import it.geosolutions.geostore.services.model.ExtUserList;
import it.geosolutions.geostore.services.rest.RESTExtJsService;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.ForbiddenErrorWebEx;
import it.geosolutions.geostore.services.rest.exception.InternalErrorWebEx;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.SecurityRuleList;
import it.geosolutions.geostore.services.rest.model.ShortAttributeList;
import it.geosolutions.geostore.services.rest.model.Sort;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.SecurityContext;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class RESTExtJsServiceImpl.
 *
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * @author Emanuele Tajariol (etj at geo-solutions.it)
 */
public class RESTExtJsServiceImpl extends RESTServiceImpl implements RESTExtJsService {

    private static final Logger LOGGER = LogManager.getLogger(RESTExtJsServiceImpl.class);

    private ResourceService resourceService;

    private UserGroupService groupService;

    private ResourcePermissionService resourcePermissionService;

    /** @param resourceService */
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    public void setUserGroupService(UserGroupService userGroupService) {
        this.groupService = userGroupService;
    }

    public void setResourcePermissionService(ResourcePermissionService resourcePermissionService) {
        this.resourcePermissionService = resourcePermissionService;
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.impl.RESTServiceImpl#getSecurityService()
     */
    @Override
    protected SecurityService getSecurityService() {
        return resourceService;
    }

    /*
     * (non-Javadoc)
     *
     * @see it.geosolutions.geostore.services.rest.RESTExtJsService#getAllResources (javax.ws.rs.core.UriInfo, javax.ws.rs.core.SecurityContext,
     * java.lang.String, java.lang.Integer, java.lang.Integer)
     */
    @Override
    public String getAllResources(SecurityContext sc, String nameLike, Integer start, Integer limit)
            throws BadRequestWebEx {

        if (start == null || limit == null) {
            throw new BadRequestWebEx("Request parameters are missing !");
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Retrieving paginated resource list (start={} limit={})", start, limit);
        }

        User authUser = null;
        try {
            authUser = extractAuthUser(sc);
        } catch (InternalErrorWebEx ie) {
            // search without user information
            LOGGER.warn(
                    "Error in validating user (this action should probably be aborted)",
                    ie); // why is this exception caught?
        }

        int page = start == 0 ? start : start / limit;

        try {
            nameLike = nameLike.replaceAll("[*]", "%");
            // TODO: implement includeAttributes and includeData

            List<ShortResource> resources =
                    resourceService.getList(
                            ResourceSearchParameters.builder()
                                    .nameLike(nameLike)
                                    .page(page)
                                    .entries(limit)
                                    .authUser(authUser)
                                    .build());

            long count = 0;
            if (resources != null && !resources.isEmpty()) {
                count = resourceService.getCountByFilterAndUser(nameLike, authUser);
            }

            JSONObject result = makeJSONResult(true, count, resources, authUser);
            return result.toString();

        } catch (BadRequestServiceEx | InternalErrorServiceEx e) {
            LOGGER.warn(e.getMessage(), e);

            JSONObject obj = makeJSONResult(false, 0, null, authUser);
            return obj.toString();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see it.geosolutions.geostore.services.rest.RESTExtJsService#getResourcesByCategory(javax.ws.rs.core.SecurityContext, java.lang.String,
     * java.lang.Integer, java.lang.Integer)
     */
    @Override
    public String getResourcesByCategory(
            SecurityContext sc,
            String categoryName,
            Integer start,
            Integer limit,
            boolean includeAttributes,
            boolean includeData)
            throws BadRequestWebEx {
        return getResourcesByCategory(
                sc, categoryName, null, start, limit, includeAttributes, includeData);
    }

    /*
     * (non-Javadoc)
     *
     * @see it.geosolutions.geostore.services.rest.RESTExtJsService#getResourcesByCategory(javax.ws.rs.core.SecurityContext, java.lang.String,
     * java.lang.Integer, java.lang.Integer)
     */
    @Override
    public String getResourcesByCategory(
            SecurityContext sc,
            String categoryName,
            String resourceNameLike,
            Integer start,
            Integer limit,
            boolean includeAttributes,
            boolean includeData)
            throws BadRequestWebEx {
        return getResourcesByCategory(
                sc,
                categoryName,
                resourceNameLike,
                null,
                start,
                limit,
                includeAttributes,
                includeData);
    }

    @Override
    public String getResourcesByCategory(
            SecurityContext sc,
            String categoryName,
            String resourceNameLike,
            String extraAttributes,
            Integer start,
            Integer limit,
            boolean includeAttributes,
            boolean includeData)
            throws BadRequestWebEx, InternalErrorWebEx {
        if (((start != null) && (limit == null)) || ((start == null) && (limit != null))) {
            throw new BadRequestWebEx("start and limit params should be declared together");
        }

        if (categoryName == null) {
            throw new BadRequestWebEx("Category is null");
        }

        // read extra attributes
        List<String> extraAttributesList =
                extraAttributes != null
                        ? Arrays.asList(extraAttributes.split(","))
                        : Collections.emptyList();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "getResourcesByCategory("
                            + categoryName
                            + ", start="
                            + start
                            + ", limit="
                            + limit
                            + (resourceNameLike != null ? ", search=" + resourceNameLike : ""));
        }

        User authUser = null;
        try {
            authUser = extractAuthUser(sc);
        } catch (InternalErrorWebEx ie) {
            // search without user information
            LOGGER.warn(
                    "Error in validating user (this action should probably be aborted)",
                    ie); // why is this exception caught?
        }

        Integer page = null;
        if (start != null) {
            page = start / limit;
        }

        try {
            SearchFilter filter = new CategoryFilter(categoryName, SearchOperator.EQUAL_TO);
            if (resourceNameLike != null) {
                resourceNameLike = resourceNameLike.replaceAll("[*]", "%");
                filter =
                        new AndFilter(
                                filter,
                                new FieldFilter(
                                        BaseField.NAME, resourceNameLike, SearchOperator.ILIKE));
            }

            boolean shouldIncludeAttributes =
                    includeAttributes || (extraAttributes != null && !extraAttributes.isEmpty());
            List<Resource> resources =
                    resourceService.getResources(
                            ResourceSearchParameters.builder()
                                    .filter(filter)
                                    .page(page)
                                    .entries(limit)
                                    .includeAttributes(shouldIncludeAttributes)
                                    .includeData(includeData)
                                    .authUser(authUser)
                                    .build());

            long count = 0;
            if (!resources.isEmpty()) {
                count = resourceService.getCountByFilterAndUser(filter, authUser);
            }

            JSONObject result =
                    makeExtendedJSONResult(
                            true,
                            count,
                            resources,
                            authUser,
                            extraAttributesList,
                            includeAttributes,
                            includeData);
            return result.toString();
        } catch (InternalErrorServiceEx | BadRequestServiceEx e) {
            LOGGER.warn(e.getMessage(), e);

            JSONObject obj = makeJSONResult(false, 0, null, authUser);
            return obj.toString();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see it.geosolutions.geostore.services.rest.RESTExtJsService#getResourcesList(javax.ws.rs.core.SecurityContext, java.lang.Integer,
     * java.lang.Integer, java.lang.String, java.lang.String, boolean, boolean, it.geosolutions.geostore.services.dto.search.SearchFilter)
     */
    @Override
    public ExtResourceList getExtResourcesList(
            SecurityContext sc,
            Integer start,
            Integer limit,
            Sort sort,
            boolean includeAttributes,
            boolean includeData,
            SearchFilter filter)
            throws BadRequestWebEx {

        if (((start != null) && (limit == null)) || ((start == null) && (limit != null))) {
            throw new BadRequestWebEx("start and limit params should be declared together");
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "getResourcesList(start="
                            + start
                            + ", limit="
                            + limit
                            + ", includeAttributes="
                            + includeAttributes);
        }

        User authUser = null;
        try {
            authUser = extractAuthUser(sc);
        } catch (InternalErrorWebEx ie) {
            // search without user information
            LOGGER.warn(
                    "Error in validating user (this action should probably be aborted)",
                    ie); // why is this exception caught?
        }

        Integer page = null;
        if (start != null) {
            page = start / limit;
        }

        try {
            List<Resource> resources =
                    resourceService.getResources(
                            ResourceSearchParameters.builder()
                                    .filter(filter)
                                    .page(page)
                                    .entries(limit)
                                    .sortBy(sort.getSortBy())
                                    .sortOrder(sort.getSortOrder())
                                    .includeAttributes(includeAttributes)
                                    .includeData(includeData)
                                    .authUser(authUser)
                                    .build());

            long count = 0;
            if (!resources.isEmpty()) {
                count = resourceService.getCountByFilterAndUser(filter, authUser);
            }

            return new ExtResourceList(count, convertToExtResources(resources, authUser));

        } catch (InternalErrorServiceEx | BadRequestServiceEx e) {
            LOGGER.warn(e.getMessage(), e);

            return null;
        }
    }

    /**
     * Filters out unadvertised resources and adds permission information to the resources.
     *
     * @param foundResources
     * @param user
     * @return
     */
    private List<ExtResource> convertToExtResources(List<Resource> foundResources, User user) {

        userService.fetchSecurityRules(user);

        return foundResources.stream()
                .map(r -> convertToExtResource(r, user))
                .collect(Collectors.toList());
    }

    private ExtResource convertToExtResource(Resource resource, User user) {

        ExtResource.Builder extResourceBuilder =
                ExtResource.builder(resource)
                        /* setting copy permission as in ResourceEnvelop.isCanCopy */
                        .withCanCopy(user != null);

        if (resourcePermissionService.canUserWriteResource(user, resource)) {
            extResourceBuilder.withCanEdit(true).withCanDelete(true);
        }

        return extResourceBuilder.build();
    }

    /**
     * @param success
     * @param count
     * @param resources
     * @param extraAttributes
     * @return JSONObject
     */
    private JSONObject makeExtendedJSONResult(
            boolean success,
            long count,
            List<Resource> resources,
            User authUser,
            List<String> extraAttributes,
            boolean includeAttributes,
            boolean includeData) {
        return makeGeneralizedJSONResult(
                success,
                count,
                resources,
                authUser,
                extraAttributes,
                includeAttributes,
                includeData);
    }

    /**
     * @param success
     * @param count
     * @param resources
     * @return JSONObject
     */
    private JSONObject makeJSONResult(
            boolean success, long count, List<ShortResource> resources, User authUser) {
        return makeGeneralizedJSONResult(success, count, resources, authUser, null, false, false);
    }

    @Override
    public ExtUserList getUsersList(
            SecurityContext sc,
            String nameLike,
            Integer start,
            Integer limit,
            boolean includeAttributes)
            throws BadRequestWebEx {

        if (((start != null) && (limit == null)) || ((start == null) && (limit != null))) {
            throw new BadRequestWebEx("start and limit params should be declared together");
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("getUsersList(start={}, limit={}", start, limit);
        }

        Integer page = null;
        if (start != null) {
            page = start / limit;
        }

        try {
            nameLike = nameLike.replaceAll("[*]", "%");
            List<User> users = userService.getAll(page, limit, nameLike, includeAttributes);

            long count = 0;
            if (users != null && !users.isEmpty()) {
                count = userService.getCount(nameLike);
            }

            return new ExtUserList(count, users);

        } catch (BadRequestServiceEx e) {
            LOGGER.warn(e.getMessage(), e);

            return null;
        }
    }

    @Override
    public ExtGroupList getGroupsList(
            SecurityContext sc, String nameLike, Integer start, Integer limit, boolean all)
            throws BadRequestWebEx {

        if (((start != null) && (limit == null)) || ((start == null) && (limit != null))) {
            throw new BadRequestWebEx("start and limit params should be declared together");
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("getGroupsList(start={}, limit={}", start, limit);
        }

        Integer page = null;
        if (start != null) {
            page = start / limit;
        }

        User authUser = null;
        try {
            authUser = extractAuthUser(sc);
        } catch (InternalErrorWebEx ie) {
            // search without user information
            LOGGER.warn(
                    "Error in validating user (this action should probably be aborted)",
                    ie); // why is this exception caught?
            return null;
        }

        try {
            if (nameLike != null) {
                nameLike = nameLike.replaceAll("[*]", "%");
            }
            List<UserGroup> groups =
                    groupService.getAllAllowed(authUser, page, limit, nameLike, all);

            long count = 0;
            if (groups != null && !groups.isEmpty()) {
                count = groupService.getCount(authUser, nameLike, all);
            }

            return new ExtGroupList(count, groups);

        } catch (BadRequestServiceEx e) {
            LOGGER.warn(e.getMessage(), e);

            return null;
        }
    }

    /**
     * Generalize method. Use this.ResourceEnvelop class
     *
     * @param success
     * @param count
     * @param resources
     * @param authUser
     * @param extraAttributes
     * @return
     */
    private JSONObject makeGeneralizedJSONResult(
            boolean success,
            long count,
            List<?> resources,
            User authUser,
            List<String> extraAttributes,
            boolean includeAttributes,
            boolean includeData) {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("success", success);
        jsonObj.put("totalCount", count);

        if (resources != null) {
            Iterator<?> iterator = resources.iterator();

            JSON result;

            int size = resources.size();
            if (size == 0) {
                result = null;
            } else if (size > 1) {
                result = new JSONArray();
            } else {
                result = new JSONObject();
            }

            while (iterator.hasNext()) {
                Object obj = iterator.next();
                ResourceEnvelop sr = null;
                if (obj instanceof Resource) {
                    sr = new ResourceEnvelop((Resource) obj, authUser);
                } else if (obj instanceof ShortResource) {
                    sr = new ResourceEnvelop((ShortResource) obj, authUser);
                }

                if (sr != null) {
                    JSONObject jobj = new JSONObject();

                    jobj.element("canDelete", sr.isCanDelete());
                    jobj.element("canEdit", sr.isCanEdit());
                    jobj.element("canCopy", sr.isCanCopy());

                    Date date = sr.getCreation();
                    if (date != null) {
                        jobj.element("creation", date.toString());
                    }

                    date = sr.getLastUpdate();
                    if (date != null) {
                        jobj.element("lastUpdate", date.toString());
                    }

                    String description = sr.getDescription();
                    if (description != null) {
                        jobj.element("description", description);
                    }

                    jobj.element("id", sr.getId());
                    jobj.element("name", sr.getName());
                    String owner = sr.getOwner();
                    // Append extra attributes
                    if (sr.getAttribute() != null) {
                        for (Attribute at : sr.getAttribute()) {
                            if (includeAttributes
                                    || (extraAttributes != null
                                            && extraAttributes.contains(at.getName()))) {
                                jobj.element(at.getName(), at.getValue());
                            }
                            if ("owner".equals(at.getName())) {
                                owner = at.getValue();
                            }
                        }
                    }
                    if (includeData) {
                        assert obj instanceof Resource;
                        jobj.element("data", ((Resource) obj).getData().getData());
                    }
                    // get owner
                    if (owner != null) {
                        jobj.element("owner", owner);
                    }
                    if (sr.getCreator() != null) {
                        jobj.element("creator", sr.getCreator());
                    } else if (owner != null) {
                        jobj.element("creator", owner);
                    }
                    if (sr.getEditor() != null) {
                        jobj.element("editor", sr.getEditor());
                    } else if (owner != null) {
                        jobj.element("editor", owner);
                    }

                    if (result instanceof JSONArray) {
                        ((JSONArray) result).add(jobj);
                    } else {
                        result = jobj;
                    }
                }
            }

            jsonObj.put("results", result != null ? result.toString() : "");
        } else {
            jsonObj.put("results", "");
        }

        return jsonObj;
    }

    @Override
    public ExtShortResource getExtResource(
            SecurityContext sc, long id, boolean includeAttributes, boolean includePermissions) {

        Resource resource = resourceService.getResource(id, includeAttributes, includePermissions);

        if (resource == null) {
            throw new NotFoundWebEx("Resource not found");
        }

        User authUser = extractAuthUser(sc);
        userService.fetchSecurityRules(authUser);

        if (!resourcePermissionService.canUserReadResource(authUser, id)) {
            throw new ForbiddenErrorWebEx("Resource is protected");
        }

        ShortResource shortResource = new ShortResource(resource);
        if (resourcePermissionService.canUserWriteResource(authUser, resource)) {
            shortResource.setCanEdit(true);
            shortResource.setCanDelete(true);
        }

        return ExtShortResource.builder(shortResource)
                .withAttributes(createShortAttributeList(resource.getAttribute()))
                .withSecurityRules(new SecurityRuleList(resource.getSecurity()))
                .build();
    }

    private ShortAttributeList createShortAttributeList(List<Attribute> attributes) {
        if (attributes == null) {
            return new ShortAttributeList();
        }
        return new ShortAttributeList(
                attributes.stream().map(ShortAttribute::new).collect(Collectors.toList()));
    }

    /**
     * Encapsulates resource/short resource and credentials to perform operations with resources
     *
     * @author adiaz
     */
    private class ResourceEnvelop {

        ShortResource sr;
        Resource r;
        String owner;
        User authUser;
        boolean canEdit = false;
        boolean canDelete = false;

        /**
         * Create a resource envelop based on a short resource
         *
         * @param sr Short resource
         * @param authUser user logged
         */
        private ResourceEnvelop(ShortResource sr, User authUser) {
            super();
            this.sr = sr;
            this.authUser = authUser;
            readSecurity();
        }

        /**
         * Create a resource envelop based on a resource
         *
         * @param r resource
         * @param authUser user logged
         */
        private ResourceEnvelop(Resource r, User authUser) {
            super();
            this.r = r;
            this.authUser = authUser;
            readSecurity();
        }

        /** Read security for edit and delete */
        private void readSecurity() {
            if (sr != null) {
                canDelete = sr.isCanDelete();
                canEdit = sr.isCanEdit();
                return;
            }

            userService.fetchSecurityRules(authUser);

            if (authUser != null && resourcePermissionService.canUserWriteResource(authUser, r)) {
                canEdit = true;
                canDelete = true;
            }
        }

        /** @return true if the logged user is owner of the resource and false otherwise */
        boolean isCanDelete() {
            return canDelete;
        }

        /** @return true if the logged user is owner of the resource and false otherwise */
        boolean isCanEdit() {
            return canEdit;
        }

        /** @return data creation */
        Date getCreation() {
            return sr != null ? sr.getCreation() : r.getCreation();
        }

        /** @return last update */
        Date getLastUpdate() {
            return sr != null ? sr.getLastUpdate() : r.getLastUpdate();
        }

        /** @return resource description */
        String getDescription() {
            return sr != null ? sr.getDescription() : r.getDescription();
        }

        /** @return resource id */
        long getId() {
            return sr != null ? sr.getId() : r.getId();
        }

        /** @return resource name */
        String getName() {
            return sr != null ? sr.getName() : r.getName();
        }

        /** @return resource attributes if contains */
        List<Attribute> getAttribute() {
            return r != null ? r.getAttribute() : null;
        }

        /** @return true if there are a user logged */
        public Boolean isCanCopy() {
            return authUser != null;
        }

        /** @return resource owner */
        public String getOwner() {
            return owner;
        }

        public String getCreator() {
            String creator = sr != null ? sr.getCreator() : r.getCreator();
            return creator != null ? creator : getOwner();
        }

        public String getEditor() {
            String editor = sr != null ? sr.getEditor() : r.getEditor();
            return editor != null ? editor : getOwner();
        }
    }
}
