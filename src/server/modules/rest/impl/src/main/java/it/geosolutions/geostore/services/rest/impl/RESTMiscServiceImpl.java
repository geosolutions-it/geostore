/* ====================================================================
 *
 * Copyright (C) 2012 GeoSolutions S.A.S.
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

import it.geosolutions.geostore.core.model.*;
import it.geosolutions.geostore.services.CategoryService;
import it.geosolutions.geostore.services.ResourceService;
import it.geosolutions.geostore.services.StoredDataService;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.*;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.InternalErrorServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.RESTMiscService;
import it.geosolutions.geostore.services.rest.exception.*;
import it.geosolutions.geostore.services.rest.model.RESTResource;
import java.util.List;

import javax.ws.rs.core.SecurityContext;

import org.apache.log4j.Logger;

/** 
 * Class RESTMiscServiceImpl.
 *
 * @author ETj (etj at geo-solutions.it)
 */
public class RESTMiscServiceImpl extends RESTServiceImpl implements RESTMiscService {
	
    private final static Logger LOGGER = Logger.getLogger(RESTMiscServiceImpl.class);

    private CategoryService categoryService;
    private ResourceService resourceService;
    private StoredDataService storedDataService;

    @Override
    public String getData(SecurityContext sc, String catName, String resName) 
            throws NotFoundWebEx, ConflictWebEx, BadRequestWebEx, InternalErrorWebEx {

        if(catName == null)
            throw new BadRequestWebEx("Category is null");
        if(resName == null)
            throw new BadRequestWebEx("Resource is null");

        SearchFilter filter = new AndFilter(
                new CategoryFilter(catName, SearchOperator.EQUAL_TO),
                new FieldFilter(BaseField.NAME, resName, SearchOperator.EQUAL_TO));

        List<Resource> resources = null;
        try {
            User user = extractAuthUser(sc);
            resources = resourceService.getResourcesFull(filter, user);
        } catch (BadRequestServiceEx ex) {
            throw new BadRequestWebEx(ex.getMessage());
        } catch (InternalErrorServiceEx ex) {
            throw new InternalErrorWebEx(ex.getMessage());
        }

        if (resources.isEmpty()) {
            throw new NotFoundWebEx("No resource found");
        } else  if (resources.size() > 1) {
            throw new ConflictWebEx("Too many resources match the request");
        }

        return resources.get(0).getData().getData();
    }

    @Override
    public Resource getResource(SecurityContext sc, String catName, String resName)
            throws NotFoundWebEx, ConflictWebEx, BadRequestWebEx, InternalErrorWebEx {

        if(catName == null)
            throw new BadRequestWebEx("Category is null");
        if(resName == null)
            throw new BadRequestWebEx("Resource is null");

        SearchFilter filter = new AndFilter(
                new CategoryFilter(catName, SearchOperator.EQUAL_TO),
                new FieldFilter(BaseField.NAME, resName, SearchOperator.EQUAL_TO));

        List<Resource> resources = null;
        try {
            User user = extractAuthUser(sc);
            resources = resourceService.getResourcesFull(filter, user);
        } catch (BadRequestServiceEx ex) {
            throw new BadRequestWebEx(ex.getMessage());
        } catch (InternalErrorServiceEx ex) {
            throw new InternalErrorWebEx(ex.getMessage());
        }

        if (resources.isEmpty()) {
            throw new NotFoundWebEx("No resource found");
        } else  if (resources.size() > 1) {
            throw new ConflictWebEx("Too many resources match the request");
        }

        return resources.get(0);
    }
    

    //=========================================================================

	public void setCategoryService(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    public void setStoredDataService(StoredDataService storedDataService) {
        this.storedDataService = storedDataService;
    }

}
