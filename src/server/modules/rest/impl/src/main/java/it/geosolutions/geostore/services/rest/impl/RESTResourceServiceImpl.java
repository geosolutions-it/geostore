/* ====================================================================
 *
 * Copyright (C) 2007 - 2011 GeoSolutions S.A.S.
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
import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.ResourceService;
import it.geosolutions.geostore.services.SecurityService;
import it.geosolutions.geostore.services.dto.ShortAttribute;
import it.geosolutions.geostore.services.dto.search.BaseField;
import it.geosolutions.geostore.services.dto.search.FieldFilter;
import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.dto.search.SearchOperator;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.InternalErrorServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.RESTResourceService;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.ForbiddenErrorWebEx;
import it.geosolutions.geostore.services.rest.exception.InternalErrorWebEx;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.RESTCategory;
import it.geosolutions.geostore.services.rest.model.RESTResource;
import it.geosolutions.geostore.services.rest.model.ResourceList;
import it.geosolutions.geostore.services.rest.model.ShortAttributeList;
import it.geosolutions.geostore.services.rest.model.ShortResourceList;
import it.geosolutions.geostore.services.rest.utils.Convert;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.SecurityContext;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Class RESTResourceServiceImpl.
 * 
 * @author ETj (etj at geo-solutions.it)
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 */
public class RESTResourceServiceImpl extends RESTServiceImpl implements RESTResourceService {

    private final static Logger LOGGER = Logger.getLogger(RESTResourceServiceImpl.class);

    private ResourceService resourceService;

    /**
     * @param resourceService
     */
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
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
     * @see it.geosolutions.geostore.services.rest.RESTResourceService#insert(it.geosolutions.geostore.core.model.Resource)
     */
    @Override
    public long insert(SecurityContext sc, RESTResource resource) {
        if (resource == null)
            throw new BadRequestWebEx("Resource is null");
        if (resource.getId() != null)
            throw new BadRequestWebEx("Id should be null");
        if (resource.getCategory() == null)
            throw new BadRequestWebEx("Category should be not null");

        User authUser = extractAuthUser(sc);

        // This list holds the security rules for this resources
        // By default when a resource is inserted are create 2 rules: 
        // ONE is related to the User that insert the rule and THE OTHER ONE is related to its group
        List<SecurityRule> securities = new ArrayList<SecurityRule>();
        
        // User Security rule: the user that insert the resource (the "owner") is allowed to Read and Write the resources
        SecurityRule userSecurityRule = new SecurityRule();
        userSecurityRule.setCanRead(true);
        userSecurityRule.setCanWrite(true);
        userSecurityRule.setUser(authUser);
        securities.add(userSecurityRule);

        Resource r = Convert.convertResource(resource);
        r.setSecurity(securities);

        try {
            long id = resourceService.insert(r);
            return id;
        } catch (BadRequestServiceEx ex) {
            throw new BadRequestWebEx(ex.getMessage());
        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx(e.getMessage());
        }
    }

    /**
     * Updates a resource. Name, Description and Metadata will be replaced if not null.<br/>
     * Category can not be changed; category element may exist in the input resource provided it is the same as in the original resource.<br/>
     * Attribute list will be updated only if it exists in the input resource.<br/>
     * <P/>
     * TODO: attribute list behaviour should be checked: read comments in source.
     * 
     * @see it.geosolutions.geostore.services.rest.RESTResourceService#update(long, it.geosolutions.geostore.core.model.Resource)
     */
    @Override
    public long update(SecurityContext sc, long id, RESTResource resource) throws NotFoundWebEx,
            BadRequestWebEx {
        try {
            if (resource == null)
                throw new BadRequestWebEx("Resource is null");
            resource.setId(id);

            Resource old = resourceService.get(id);
            if (old == null)
                throw new NotFoundWebEx("Resource not found");

            if (resource.getCategory() != null) {
                RESTCategory newrc = resource.getCategory();
                Category oldrc = old.getCategory();
                if ((newrc.getId() != null && !newrc.getId().equals(oldrc.getId()))
                        || (newrc.getName() != null && !newrc.getName().equals(oldrc.getName()))) {
                    LOGGER.info("Trying to change category old(" + oldrc.getId() + ":"
                            + oldrc.getName() + ") new(" + newrc.getId() + ":" + newrc.getName()
                            + ")");
                    throw new BadRequestWebEx("Can't change resource category");
                }
            }

            //
            // Authorization check.
            //
            boolean canUpdate = false;
            User authUser = extractAuthUser(sc);
            canUpdate = resourceAccessWrite(authUser, old.getId());

            if (canUpdate) {
                if (resource.getDescription() != null)
                    old.setDescription(resource.getDescription());
                if (resource.getName() != null)
                    old.setName(resource.getName());
                if (resource.getMetadata() != null)
                    old.setMetadata(resource.getMetadata());

                resourceService.update(old);

                //
                // Check Attribute list
                //
                // 1) no Attributes element --> no change
                // 2a) empty attributes element --> remove all attributes
                // 2b) attributes element with children --> update attributes list
                if (resource.getAttribute() == null) {
                    if (LOGGER.isDebugEnabled())
                        LOGGER.debug("Attribute list is null: no change in the attrib list");
                } else {
                    if (LOGGER.isDebugEnabled())
                        LOGGER.debug("Attribute list is " + resource.getAttribute().size());
                    List<Attribute> attributes = Convert.convertAttributeList(resource
                            .getAttribute());
                    resourceService.updateAttributes(id, attributes);
                }

                return id;
            } else
                throw new ForbiddenErrorWebEx("Can't update resource");

        } catch (NotFoundServiceEx ex) {
            LOGGER.warn("Resource not found (" + id + "): " + ex.getMessage(), ex);
            throw new NotFoundWebEx("Resource not found");
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTResourceService#delete(long)
     */
    @Override
    public void delete(SecurityContext sc, long id) throws NotFoundWebEx {
        //
        // Authorization check.
        //
        boolean canDelete = false;
        User authUser = extractAuthUser(sc);
        canDelete = resourceAccessWrite(authUser, id);

        if (canDelete) {
            boolean ret = resourceService.delete(id);
            if (!ret)
                throw new NotFoundWebEx("Resource not found");
        } else
            throw new ForbiddenErrorWebEx("This user cannot delete this resource !");
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTResourceService#delete(long)
     */
    @Override
    public void deleteResources(SecurityContext sc, SearchFilter filter) throws BadRequestWebEx,
            InternalErrorWebEx {
        try {
            resourceService.deleteResources(filter);
        } catch (BadRequestServiceEx e) {
            if (LOGGER.isEnabledFor(Level.ERROR))
                LOGGER.error(e.getMessage());
            throw new BadRequestWebEx(e.getMessage());
        } catch (InternalErrorServiceEx e) {
            if (LOGGER.isEnabledFor(Level.ERROR))
                LOGGER.error(e.getMessage());
            throw new InternalErrorWebEx(e.getMessage());
        }
    }

    @Override
    public Resource get(SecurityContext sc, long id, boolean fullResource) throws NotFoundWebEx {

        //
        // Authorization check.
        //
        boolean canRead = false;
        User authUser = extractAuthUser(sc);
        canRead = resourceAccessRead(authUser, id);
        if(!canRead){
            throw new ForbiddenErrorWebEx("This user cannot read this resource !");
        }
        
        if (fullResource) {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Retrieving a full resource");
            List<Resource> resourcesFull;
            try {
                SearchFilter filter = new FieldFilter(BaseField.ID, Long.toString(id),
                        SearchOperator.EQUAL_TO);
                resourcesFull = resourceService.getResourcesFull(filter, authUser);
            } catch (Exception ex) {
                LOGGER.error(ex.getMessage(), ex);
                throw new InternalErrorWebEx("Internal error");
            }
            if (resourcesFull.isEmpty())
                throw new NotFoundWebEx("Resource not found");

            if (LOGGER.isDebugEnabled())
                LOGGER.debug("DATA is " + resourcesFull.get(0).getData());

            return resourcesFull.get(0);
        } else {

            Resource ret = resourceService.get(id);

            if (ret == null)
                throw new NotFoundWebEx("Resource not found");

            // CHECKME
            if (ret.getData() != null) {
                LOGGER.warn("Resource has data attached. It should not. Please check.");
                ret.setData(null); // make the data transmission lighter
            }

            return ret;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTResourceService#getList(java.lang.String, java.lang.Integer, java.lang.Integer)
     */
    @Override
    public ShortResourceList getList(SecurityContext sc, String nameLike, Integer page,
            Integer entries) throws BadRequestWebEx {
        User authUser = extractAuthUser(sc);
        nameLike = nameLike.replaceAll("[*]", "%");

        try {
            return new ShortResourceList(resourceService.getList(nameLike, page, entries, authUser));
        } catch (BadRequestServiceEx ex) {
            throw new BadRequestWebEx(ex.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTResourceService#getList(java.lang.Integer, java.lang.Integer)
     */
    @Override
    public ShortResourceList getAll(SecurityContext sc, Integer page, Integer entries)
            throws BadRequestWebEx {
        User authUser = extractAuthUser(sc);

        try {
            return new ShortResourceList(resourceService.getAll(page, entries, authUser));
        } catch (BadRequestServiceEx ex) {
            throw new BadRequestWebEx(ex.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTResourceService#getCount(java.lang.String)
     */
    @Override
    public long getCount(SecurityContext sc, String nameLike) {
        nameLike = nameLike.replaceAll("[*]", "%");
        return resourceService.getCount(nameLike);
    }

    /**
     * @param id
     * @return long
     * @throws BadRequestWebEx
     */
    @SuppressWarnings("unused")
    private long parseId(SecurityContext sc, String id) throws BadRequestWebEx {
        try {
            return Long.parseLong(id);
        } catch (Exception e) {
            LOGGER.info("Bad id requested '" + id + "'");
            throw new BadRequestWebEx("Bad id");
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTResourceService#getAttributes(long)
     */
    @Override
    public ShortAttributeList getAttributes(SecurityContext sc, long id) throws NotFoundWebEx {
        Resource resource = resourceService.get(id);
        if (resource == null)
            throw new NotFoundWebEx("Resource not found");

        return new ShortAttributeList(resourceService.getAttributes(id));
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTResourceService#getAttribute(long, java.lang.String)
     */
    @Override
    public String getAttribute(SecurityContext sc, long id, String name) throws NotFoundWebEx {
        Resource resource = resourceService.get(id);
        if (resource == null)
            throw new NotFoundWebEx("Resource not found");

        ShortAttribute shAttribute = resourceService.getAttribute(id, name);

        if (shAttribute == null)
            throw new NotFoundWebEx("Resource attribute not found");

        return shAttribute.getValue();
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTResourceService#insertAttribute(long, java.lang.String, java.lang.String)
     */
    @Override
    public long updateAttribute(SecurityContext sc, long id, String name, String value)
            throws NotFoundWebEx, InternalErrorWebEx {
        Resource resource = resourceService.get(id);
        if (resource == null)
            throw new NotFoundWebEx("Resource not found");

        //
        // Authorization check.
        //
        boolean canUpdate = false;
        try {
            User authUser = extractAuthUser(sc);
            canUpdate = resourceAccessWrite(authUser, resource.getId());

            if (canUpdate)
                return resourceService.updateAttribute(id, name, value);
            else
                throw new InternalErrorServiceEx("This user cannot access this resource !");
        } catch (InternalErrorServiceEx ex) {
            throw new InternalErrorWebEx(ex.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTResourceService#getResourceByFilter(it.geosolutions.geostore.services.dto.SearchFilter)
     */
    @Override
    public ShortResourceList getResources(SecurityContext sc, SearchFilter filter) {
        User authUser = extractAuthUser(sc);

        try {
            return new ShortResourceList(resourceService.getResources(filter, authUser));
        } catch (BadRequestServiceEx e) {
            if (LOGGER.isInfoEnabled())
                LOGGER.info(e.getMessage());
            throw new BadRequestWebEx(e.getMessage());
        } catch (InternalErrorServiceEx e) {
            if (LOGGER.isInfoEnabled())
                LOGGER.info(e.getMessage());
            throw new InternalErrorWebEx(e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTResourceService#getResourcesList(javax.ws.rs.core.SecurityContext,
     * it.geosolutions.geostore.services.dto.search.SearchFilter, java.lang.Integer, java.lang.Integer, boolean, boolean)
     */
    @Override
    public ResourceList getResourcesList(SecurityContext sc, Integer page, Integer entries,
            boolean includeAttributes, boolean includeData, SearchFilter filter) {
        User authUser = extractAuthUser(sc);

        try {
            return new ResourceList(resourceService.getResources(filter, page, entries,
                    includeAttributes, includeData, authUser));
        } catch (BadRequestServiceEx e) {
            if (LOGGER.isInfoEnabled())
                LOGGER.info(e.getMessage());
            throw new BadRequestWebEx(e.getMessage());
        } catch (InternalErrorServiceEx e) {
            if (LOGGER.isInfoEnabled())
                LOGGER.info(e.getMessage());
            throw new InternalErrorWebEx(e.getMessage());
        }
    }
}
