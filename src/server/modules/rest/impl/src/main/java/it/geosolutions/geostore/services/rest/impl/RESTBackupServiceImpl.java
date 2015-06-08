/* ====================================================================
 *
 * Copyright (C) 2007 - 2012 GeoSolutions S.A.S.
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

import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.services.CategoryService;
import it.geosolutions.geostore.services.ResourceService;
import it.geosolutions.geostore.services.SecurityService;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.CategoryFilter;
import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.dto.search.SearchOperator;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.DuplicatedResourceNameServiceEx;
import it.geosolutions.geostore.services.exception.InternalErrorServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.RESTBackupService;
import it.geosolutions.geostore.services.rest.exception.InternalErrorWebEx;
import it.geosolutions.geostore.services.rest.model.RESTCategory;
import it.geosolutions.geostore.services.rest.model.RESTQuickBackup;
import it.geosolutions.geostore.services.rest.model.RESTQuickBackup.RESTBackupCategory;
import it.geosolutions.geostore.services.rest.model.RESTQuickBackup.RESTBackupResource;
import it.geosolutions.geostore.services.rest.model.RESTResource;
import it.geosolutions.geostore.services.rest.utils.Convert;

import java.util.List;

import javax.ws.rs.core.SecurityContext;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.NotImplementedException;
import org.apache.log4j.Logger;

/** 
 *
 */
public class RESTBackupServiceImpl extends RESTServiceImpl implements RESTBackupService {

    private final static Logger LOGGER = Logger.getLogger(RESTBackupServiceImpl.class);

    private CategoryService categoryService;

    private ResourceService resourceService;

    private final static long MAX_RESOURCES_FOR_QUICK_BACKUP = 100l;

    @Override
    public String backup(SecurityContext sc) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String restore(SecurityContext sc, String token) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public RESTQuickBackup quickBackup(SecurityContext sc) throws BadRequestServiceEx {

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("quickBackup()");

        if (resourceService.getCount((String) null) > MAX_RESOURCES_FOR_QUICK_BACKUP)
            throw new BadRequestServiceEx("Too many resources for a quick backup");

        RESTQuickBackup backup = new RESTQuickBackup();

        try {
            List<Category> categories = categoryService.getAll(null, null);
            for (Category category : categories) {
                RESTBackupCategory bc = createCategory(category);
                backup.addCategory(bc);
            }
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new InternalErrorWebEx("Internal error while performing backup");
        }

        return backup;
    }

    @Override
    public String quickRestore(SecurityContext sc, RESTQuickBackup backup) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("quickRestore()");

        try {

            LOGGER.error("Deleting all categories");
            for (Category category : categoryService.getAll(null, null)) {
                categoryService.delete(category.getId());
            }

            LOGGER.error("Deleting all resources");
            for (ShortResource shortResource : resourceService.getAll(null, null, null)) {
                resourceService.delete(shortResource.getId());
            }

            for (RESTBackupCategory rbc : backup.getCategories()) {
                quickRestoreCategory(rbc);
            }

            return "ok";

        } catch (Exception ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new InternalErrorWebEx(ex.getMessage());
        }
    }

    private void quickRestoreCategory(RESTBackupCategory rbc) throws BadRequestServiceEx,
            NotFoundServiceEx, DuplicatedResourceNameServiceEx {
        LOGGER.info("Restoring category: " + rbc.getName());
        Category cat = rbc2cat(rbc);
        long catId = categoryService.insert(cat);
        // TODO: cat auth

        for (RESTBackupResource rbr : rbc.getResources()) {
            if (LOGGER.isInfoEnabled()) {
                int attnum = (rbr != null && rbr.getResource() != null && rbr.getResource()
                        .getAttribute() != null) ? rbr.getResource().getAttribute().size() : -1;
                LOGGER.info("Restoring resource: " + cat.getName() + ":"
                        + rbr.getResource().getName() + " (" + attnum + " attrs)");
            }
            Resource res = rbr2res(rbr, catId);
            resourceService.insert(res);
            // TODO: res auth
        }
    }

    private static Category rbc2cat(RESTBackupCategory rbc) {
        Category ret = new Category();
        ret.setName(rbc.getName());
        return ret;
    }

    private Resource rbr2res(RESTBackupResource rbr, long catId) {
        Resource ret = Convert.convertResource(rbr.getResource());
        ret.getCategory().setId(catId); // remap category
        return ret;
    }

    private RESTBackupCategory createCategory(Category category) throws BadRequestServiceEx,
            InternalErrorServiceEx {
        LOGGER.info("Packing category " + category.getName());

        RESTBackupCategory ret = new RESTBackupCategory();
        ret.setName(category.getName());
        SearchFilter filter = new CategoryFilter(category.getName(), SearchOperator.EQUAL_TO);
        List<Resource> resList = resourceService.getResourcesFull(filter, null);
        for (Resource resource : resList) {
            RESTBackupResource rbe = createResource(resource);
            ret.addResource(rbe);
        }
        return ret;
    }

    private RESTBackupResource createResource(Resource resource) {
        LOGGER.info("Packing resource " + resource.getName());

        RESTResource rr = createRESTResource(resource);
        RESTBackupResource rbr = new RESTBackupResource();
        rbr.setResource(rr);
        return rbr;
    }

    private RESTResource createRESTResource(Resource resource) {
        RESTResource ret = new RESTResource();
        ret.setCategory(new RESTCategory(resource.getCategory().getName()));
        ret.setName(resource.getName());
        ret.setDescription(resource.getDescription());
        ret.setMetadata(resource.getMetadata());
        if (resource.getData() != null)
            ret.setData(resource.getData().getData());
        if (CollectionUtils.isNotEmpty(resource.getAttribute()))
            ret.setAttribute(Convert.convertToShortAttributeList(resource.getAttribute()));
        return ret;
    }

    // =========================================================================

    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    public void setCategoryService(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.impl.RESTServiceImpl#getSecurityService()
     */
    @Override
    protected SecurityService getSecurityService() {
        throw new NotImplementedException("This method is not implemented yet...");
    }

}
