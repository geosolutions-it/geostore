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

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.RESTUserService;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.UserList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.SecurityContext;

import org.apache.log4j.Logger;

/** 
 * Class RESTUserServiceImpl.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 *
 */
public class RESTUserServiceImpl implements RESTUserService{
	
    private final static Logger LOGGER = Logger.getLogger(RESTUserServiceImpl.class);

    private UserService userService;

    /**
	 * @param userService the userService to set
	 */
	public void setUserService(UserService userService) {
		this.userService = userService;
	}

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTUserInterface#insert(it.geosolutions.geostore.core.model.User)
     */
    @Override
    public long insert(SecurityContext sc, User user){
        if(user == null)
            throw new BadRequestWebEx("User is null");
        if(user.getId() != null)
            throw new BadRequestWebEx("Id should be null");

        long id = -1;
        try {        	
            //
            // Parsing UserAttributes list
            //
        	List<UserAttribute> usAttribute = user.getAttribute();
        	
        	if(usAttribute != null)        	
	        	if(usAttribute.size() > 0)
	        		user.setAttribute(usAttribute);
        	
			id = userService.insert(user);
		} catch (NotFoundServiceEx e) {
			throw new NotFoundWebEx(e.getMessage());
		} catch (BadRequestServiceEx e) {
			throw new BadRequestWebEx(e.getMessage());
		}
		
		return id;
    }
    
    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTUserInterface#update(long, it.geosolutions.geostore.core.model.User)
     */
    @Override
    public long update(SecurityContext sc, long id, User user){
    	try {
    		User old = userService.get(id);
            if(old == null)
                throw new NotFoundWebEx("User not found");
            
            old.setNewPassword(user.getNewPassword());
            old.setRole(user.getRole());
            
            UserGroup group = user.getGroup();
            if(group != null){
            	old.setGroup(group);
            }
            
			id = userService.update(old);
			
            //
            // Creating a new User Attribute list (updated).
            //
        	List<UserAttribute> attributeDto = user.getAttribute();
        	Iterator<UserAttribute> iteratorDto = attributeDto.iterator();
        	
        	List<UserAttribute> attributes = new ArrayList<UserAttribute>();
        	while(iteratorDto.hasNext()){
        		UserAttribute aDto = iteratorDto.next();
        		
        		UserAttribute a = new UserAttribute();
        		a.setValue(aDto.getValue());               
                a.setName(aDto.getName());
                attributes.add(a);
        	}
        	
            if(attributes.size() > 0)
            	userService.updateAttributes(id, attributes);
            
            return id;
            
		} catch (NotFoundServiceEx e) {
			throw new NotFoundWebEx(e.getMessage());
		} catch (BadRequestServiceEx e) {
			throw new BadRequestWebEx(e.getMessage());
		}		
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTUserInterface#delete(long)
     */
    @Override
    public void delete(SecurityContext sc, long id) throws NotFoundWebEx {
        boolean ret = userService.delete(id);
        if(!ret)
            throw new NotFoundWebEx("User not found");
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTUserInterface#get(long)
     */
    @Override
    public User get(SecurityContext sc, long id) throws NotFoundWebEx {
        if(id == -1) { 
        	if(LOGGER.isDebugEnabled())
        		LOGGER.debug("Retriving dummy data !");
        	
        	//
        	// return test instance
        	//
        	User user = new User();
        	user.setName("dummy name");
            return user;
        }

        User ret = userService.get(id);
        if(ret == null)
            throw new NotFoundWebEx("User not found");
        
        return ret;
    }
    
	/* (non-Javadoc)
	 * @see it.geosolutions.geostore.services.rest.RESTUserService#get(java.lang.String)
	 */
	@Override
	public User get(SecurityContext sc, String name) throws NotFoundWebEx {
        if(name == null) { 
        	if(LOGGER.isDebugEnabled())
        		LOGGER.debug("User Name is null !");
        	throw new BadRequestWebEx("User name is null");
        }

        User ret;
		try {
			ret = userService.get(name);
		} catch (NotFoundServiceEx e) {
			throw new NotFoundWebEx("User not found");
		}
        
        return ret;
	}

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTUserInterface#getAll(java.lang.Integer, java.lang.Integer)
     */
    @Override
    public UserList getAll(SecurityContext sc, Integer page, Integer entries) throws BadRequestWebEx {
        try {
            return new UserList(userService.getAll(page, entries));
        } catch (BadRequestServiceEx ex) {
            throw new BadRequestWebEx(ex.getMessage());
        }
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTUserInterface#getCount(java.lang.String)
     */
    @Override
    public long getCount(SecurityContext sc, String nameLike) {
    	nameLike = nameLike.replaceAll("[*]", "%");
        return userService.getCount(nameLike);
    }
    
}
