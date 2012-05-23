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
package it.geosolutions.geostore.services;

import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.dto.ShortAttribute;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.InternalErrorServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;

import java.util.List;

/** 
 * Interafce ResourceService.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * @author ETj (etj at geo-solutions.it)
 */
public interface ResourceService {

    // ==========================================================================
    // Basic operations
	// ==========================================================================

    /**
     * @param resource
     * @return long
     * @throws BadRequestServiceEx 
     * @throws NotFoundServiceEx 
     */
    long insert(Resource resource) throws BadRequestServiceEx, NotFoundServiceEx;
    
    /**
     * @param resource
     * @return long
     * @throws NotFoundServiceEx
     */
    long update(Resource resource) throws NotFoundServiceEx;
    
    /**
     * @param id
     * @return long
     */
    boolean delete(long id);
    
    /**
     * @param id
     * @return long
     */
    Resource get(long id);

    /**
     * @param nameLike
     * @param page
     * @param entries
     * @param authUser
     * @return List<ShortResource>
     * @throws BadRequestServiceEx
     */
    List<ShortResource> getList(String nameLike, Integer page, Integer entries, User authUser)
            throws BadRequestServiceEx;
    
    /**
     * @param page
     * @param entries
     * @param authUser
     * @return List<ShortResource>
     * @throws BadRequestServiceEx
     */
    List<ShortResource> getAll(Integer page, Integer entries, User authUser)
            throws BadRequestServiceEx;

    /**
     * @param nameLike
     * @return long
     */
    long getCount(String nameLike);
    

	/**
	 * @param id
	 * @param attributes
	 */
	void updateAttributes(long id, List<Attribute> attributes) throws NotFoundServiceEx;
	
	/**
	 * @param id
	 * @return List<ShortAttribute>
	 * @throws NotFoundServiceEx
	 */
	List<ShortAttribute> getAttributes(long id);
	
	/**
	 * @param id
	 * @return ShortAttribute
	 * @throws NotFoundServiceEx
	 */
	ShortAttribute getAttribute(long id, String name);

	/**
	 * @param id
	 * @param name
	 * @param value
	 * @return long
	 * @throws InternalErrorServiceEx 
	 */
	long updateAttribute(long id, String name, String value) throws InternalErrorServiceEx;
	
	/**
	 * @param filter
	 * @param authUser
	 * @return List<ShortResource>
	 * @throws BadRequestServiceEx
	 * @throws InternalErrorServiceEx
	 */
	List<ShortResource> getResources(SearchFilter filter, User authUser) throws BadRequestServiceEx, InternalErrorServiceEx;


    /**
     * Return a list of resources joined with their data.
     * This call can be very heavy for the system. Please use this method only when you are sure
     * a few data will be returned, otherwise consider using
     * {@link #getResources(it.geosolutions.geostore.services.dto.search.SearchFilter, it.geosolutions.geostore.core.model.User) getResources)
     * if you need less data.
     */
    public List<Resource> getResourcesFull(SearchFilter filter, User authUser)
            throws BadRequestServiceEx,InternalErrorServiceEx;
    
	/**
	 * @param userName
	 * @param resourceId
	 * @return List<SecurityRule>
	 */
	List<SecurityRule> getUserSecurityRule(String userName, long resourceId);

}
