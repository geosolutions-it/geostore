/*
 * $ Header: it.geosolutions.georepo.services..rest.impl.RESTCategoryServiceImpl ,v. 0.1 9-set-2011 10.39.58 created by tobaro <tobia.dipisa at geo-solutions.it> $
 * $ Revision: 0.1 $
 * $ Date: 8-set-2011 10.39.58 $
 *
 * ====================================================================
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

import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.CategoryService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.RESTCategoryService;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.ForbiddenErrorWebEx;
import it.geosolutions.geostore.services.rest.exception.InternalErrorWebEx;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.CategoryList;
import it.geosolutions.geostore.services.rest.utils.GeoStorePrincipal;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.SecurityContext;

import org.apache.log4j.Logger;

/** 
 * Class RESTCategoryServiceImpl.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 *
 */
public class RESTCategoryServiceImpl implements RESTCategoryService{
	
    private final static Logger LOGGER = Logger.getLogger(RESTCategoryServiceImpl.class);

    private CategoryService categoryService;
    
    /**
	 * @param categoryService the categoryService to set
	 */
	public void setCategoryService(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTCategoryService#insert(it.geosolutions.geostore.core.model.Category)
     */
    @Override
    public long insert(SecurityContext sc, Category category){
        if(category == null)
            throw new BadRequestWebEx("Category is null");
        if(category.getId() != null)
            throw new BadRequestWebEx("Id should be null");

        long id = -1;
        
        //
        // Preparing Security Rule
        //
        User authUser = extractAuthUser(sc);
        
        SecurityRule securityRule = new SecurityRule();
        securityRule.setCanRead(true);
        securityRule.setCanWrite(true);
        securityRule.setUser(authUser);
        
        List<SecurityRule> securities = new ArrayList<SecurityRule>();
        securities.add(securityRule);
        
        category.setSecurity(securities);
        
        try {
			id = categoryService.insert(category);
		} catch (NotFoundServiceEx e) {
			throw new NotFoundWebEx(e.getMessage());
		} catch (BadRequestServiceEx e) {
			throw new BadRequestWebEx(e.getMessage());
		}
		
		return id;
    }
    
    @Override
    public long update(SecurityContext sc, long id, Category category){
    	try {
    		Category old = categoryService.get(id);
            if(old == null)
                throw new NotFoundWebEx("Category not found");
            
            //
            // Authorization check.
            //
            boolean canUpdate = false;
			User authUser = extractAuthUser(sc);
			canUpdate = resourceAccess(authUser, old.getId());

    		if(canUpdate){
    			id = categoryService.update(category);
    		}else{
    			throw new ForbiddenErrorWebEx("This user cannot update this category !");
    		}
			
		} catch (BadRequestServiceEx e) {
			throw new BadRequestWebEx(e.getMessage());
		}
		
		return id;
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTCategoryService#delete(long)
     */
    @Override
    public void delete(SecurityContext sc, long id) throws NotFoundWebEx {
        //
        // Authorization check.
        //
        boolean canDelete = false;
		User authUser = extractAuthUser(sc);
		canDelete = resourceAccess(authUser, id);  			
		
		if(canDelete){
	        boolean ret = categoryService.delete(id);
	        if(!ret)
	            throw new NotFoundWebEx("Category not found");
		}else
			throw new ForbiddenErrorWebEx("This user cannot delete this category !");

    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTCategoryService#get(long)
     */
    @Override
    public Category get(SecurityContext sc, long id) throws NotFoundWebEx {
        if(id == -1) { 
        	if(LOGGER.isDebugEnabled())
        		LOGGER.debug("Retriving dummy data !");
        	
        	//
        	// return test instance
        	//
        	Category category = new Category();
        	category.setName("dummy name");
            return category;
        }

        Category ret = categoryService.get(id);
        if(ret == null)
            throw new NotFoundWebEx("Category not found");
        
        return ret;
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTCategoryService#getAll(java.lang.Integer, java.lang.Integer)
     */
    @Override
    public CategoryList getAll(SecurityContext sc, Integer page, Integer entries) throws BadRequestWebEx {
        try {
            return new CategoryList(categoryService.getAll(page, entries));
        } catch (BadRequestServiceEx ex) {
            throw new BadRequestWebEx(ex.getMessage());
        }
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTCategoryService#getCount(java.lang.String)
     */
    @Override
    public long getCount(SecurityContext sc, String nameLike) {
    	nameLike = nameLike.replaceAll("[*]", "%");
        return categoryService.getCount(nameLike);
    }

    /**
     * @return User - The authenticated user that is accessing this service, or null if guest access.
     */
    private User extractAuthUser(SecurityContext sc) throws InternalErrorWebEx {
        if(sc == null)
            throw new InternalErrorWebEx("Missing auth info");
        else {
            Principal principal = sc.getUserPrincipal();
            if(principal == null){
    			if(LOGGER.isInfoEnabled())
    				LOGGER.info("Missing auth principal");
    			throw new InternalErrorWebEx("Missing auth principal");
            }
                
            if( ! (principal instanceof GeoStorePrincipal )){
    			if(LOGGER.isInfoEnabled())
    				LOGGER.info("Missing auth principal");
    			throw new InternalErrorWebEx("Mismatching auth principal (" + principal.getClass() + ")");
            }

            GeoStorePrincipal gsp = (GeoStorePrincipal)principal;
            
            //
            // may be null if guest
            //
            User user = gsp.getUser(); 

            LOGGER.info("Accessing service with user " + (user == null ? "GUEST" : user.getName()));
            return user;
        }
    }

    /**
     * Check if the user can access the requested resource (is own resource or not ?) 
	 * in order to update it.
	 * 
     * @param resource
     * @return boolean
     */
    private boolean resourceAccess(User authUser, long resourceId) {
    	boolean canAccess = false;
    	
    	if(authUser.getRole().equals(Role.ADMIN)){
    		canAccess = true;
    	}else{
        	List<SecurityRule> securityRules  = categoryService.getUserSecurityRule(authUser.getName(), resourceId);
    		
    		if(securityRules != null && securityRules.size() > 0)
    			canAccess = true;
    	}
		
		return canAccess;
    }
    
}
