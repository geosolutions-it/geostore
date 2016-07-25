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
package it.geosolutions.geostore.services;

import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.enums.DataType;
import it.geosolutions.geostore.services.dto.ShortAttribute;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.DuplicatedResourceNameServiceEx;
import it.geosolutions.geostore.services.exception.InternalErrorServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;

import java.util.List;

/**
 * Interafce ResourceService.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * @author ETj (etj at geo-solutions.it)
 */
public interface ResourceService extends SecurityService{

    // ==========================================================================
    // Basic operations
    // ==========================================================================

    /**
     * @param resource
     * @return long
     * @throws BadRequestServiceEx
     * @throws NotFoundServiceEx
     * @throws DuplicatedResourceNameServiceEx 
     */
    long insert(Resource resource) throws BadRequestServiceEx, NotFoundServiceEx, DuplicatedResourceNameServiceEx;

    /**
     * @param resource
     * @return long
     * @throws NotFoundServiceEx
     * @throws DuplicatedResourceNameServiceEx 
     */
    long update(Resource resource) throws NotFoundServiceEx, DuplicatedResourceNameServiceEx;

    /**
     * @param id
     * @return long
     */
    boolean delete(long id);

    /**
     * @param filter
     * @throws InternalErrorServiceEx
     * @throws BadRequestServiceEx
     */
    void deleteResources(SearchFilter filter) throws BadRequestServiceEx, InternalErrorServiceEx;

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
     *
     * @deprecated count should be done on a per-user basis
     */
    @Deprecated
    long getCount(String nameLike);

    /**
     * @param filter
     * @return long
     * @throws InternalErrorServiceEx
     * @throws BadRequestServiceEx
     *
     * @deprecated count should be done on a per-user basis
     */
    @Deprecated
    long getCountByFilter(SearchFilter filter) throws InternalErrorServiceEx, BadRequestServiceEx;

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
    List<ShortResource> getResources(SearchFilter filter, User authUser)
            throws BadRequestServiceEx, InternalErrorServiceEx;

    /**
     * @param filter
     * @param page
     * @param entries
     * @param authUser
     * @return List<ShortResource>
     * @throws BadRequestServiceEx
     * @throws InternalErrorServiceEx
     */
    List<ShortResource> getResources(SearchFilter filter, Integer page, Integer entries,
            User authUser) throws BadRequestServiceEx, InternalErrorServiceEx;

    /**
     * @param filter
     * @param page
     * @param entries
     * @param includeAttributes
     * @param includeData
     * @return List<Resource>
     * @throws BadRequestServiceEx
     * @throws InternalErrorServiceEx
     */
    List<Resource> getResources(SearchFilter filter, Integer page, Integer entries,
            boolean includeAttributes, boolean includeData, User authUser)
            throws BadRequestServiceEx, InternalErrorServiceEx;

    /**
     * Return a list of resources joined with their data. This call can be very heavy for the system. Please use this method only when you are sure a
     * few data will be returned, otherwise consider using
     * {@link #getResources(it.geosolutions.geostore.services.dto.search.SearchFilter, it.geosolutions.geostore.core.model.User) getResources) if you
     * need less data.
     */
    public List<Resource> getResourcesFull(SearchFilter filter, User authUser)
            throws BadRequestServiceEx, InternalErrorServiceEx;


    /**
     * Returns the list of security rules for the resource.
     * 
     * @param resources
     * @return
     */
    public List<SecurityRule> getSecurityRules(long id)
    		throws BadRequestServiceEx, InternalErrorServiceEx;
    
    /**
     * Replaces the list of security rules for the given resource.
     * 
     * @param id
     * @param rules
     * @throws BadRequestServiceEx
     * @throws InternalErrorServiceEx
     * @throws NotFoundServiceEx 
     */
    public void updateSecurityRules(long id, List<SecurityRule> rules)
    		throws BadRequestServiceEx, InternalErrorServiceEx, NotFoundServiceEx;
    
    
    /**
     * Get filter count by filter and user
     * @param filter
     * @param user
     * @return resources' count that the user has access
     * @throws InternalErrorServiceEx 
     * @throws BadRequestServiceEx
     *
     * @deprecated rename into count()
     */
    @Deprecated
    long getCountByFilterAndUser(SearchFilter filter, User user)
            throws BadRequestServiceEx, InternalErrorServiceEx;

    /**
     * Get filter count by namerLike and user
     * @param nameLike
     * @param user
     * @return resources' count that the user has access
     * @throws BadRequestServiceEx 
     * @deprecated rename into count()
     */
    @Deprecated
    long getCountByFilterAndUser(String nameLike, User user)
            throws BadRequestServiceEx;

    long insertAttribute(long id, String name, String value, DataType type)
        throws InternalErrorServiceEx;


}
