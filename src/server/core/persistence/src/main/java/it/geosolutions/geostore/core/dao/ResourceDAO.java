/*
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
package it.geosolutions.geostore.core.dao;

import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;

import java.util.List;

import com.googlecode.genericdao.search.ISearch;
import com.googlecode.genericdao.search.Search;

/**
 * Interface ResourceDAO. Public interface to define operations on Resource
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * 
 */
public interface ResourceDAO extends RestrictedGenericDAO<Resource> {

    /**
     * @param userName
     * @param resourceId
     * @return List<SecurityRule>
     */
    public List<SecurityRule> findUserSecurityRule(String userName, long resourceId);
    
    /**
     * 
     * @param userName
     * @param resourceId
     * @return
     */
    public List<SecurityRule> findGroupSecurityRule(List<String> groupNames, long resourceId);

    /**
     * @param resourceId
     * @return List<SecurityRule>
     */
    public List<SecurityRule> findSecurityRules(long resourceId);

    /**
     * @param resourceId
     * @return List<Attribute>
     */
    public List<Attribute> findAttributes(long resourceId);

    /**
     * @param search
     */
    public void removeResources(ISearch search);
    
    /**
     * @param resourcesIDs A list of resources Ids to search
     */
    public List<Resource> findResources(List<Long> resourcesIds);

    /**
     * Get criteria count by user
     * @param searchCriteria
     * @param user
     * @return resources' count that the user has access 
     */
	public long count(Search searchCriteria, User user);
}
