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
package it.geosolutions.geostore.core.dao;

import java.util.List;
import javax.persistence.NonUniqueResultException;
import com.googlecode.genericdao.search.ISearch;
import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.Resource;

/**
 * Interface ResourceDAO. Public interface to define operations on Resource
 *
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 *
 */
public interface ResourceDAO extends RestrictedGenericDAO<Resource>
{

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
     * Gets a resource by name.
     *
     * @return the resource with the specified name, or null if none was found
     * @throws NonUniqueResultException if more than one result
     */
    public Resource findByName(String resourceName);

    /**
     * Returns a list of resource names matching the specified pattern
     *
     * @param pattern the pattern used to build a LIKE filter
     * @return a list of resource names
     */
    public List<String> findResourceNamesMatchingPattern(String pattern);
}
