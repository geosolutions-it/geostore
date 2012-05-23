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

package it.geosolutions.geostore.core.dao.impl;

import com.googlecode.genericdao.search.Filter;
import com.googlecode.genericdao.search.ISearch;
import com.googlecode.genericdao.search.Search;

import java.util.Date;
import java.util.List;

import it.geosolutions.geostore.core.dao.ResourceDAO;
import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;

import org.apache.log4j.Logger;
import org.springframework.transaction.annotation.Transactional;


/**
 * Class ResourceDAOImpl.
 *
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * @author ETj (etj at geo-solutions.it)
 */
@Transactional(value = "geostoreTransactionManager")
public class ResourceDAOImpl extends BaseDAO<Resource, Long> implements ResourceDAO
{

    private static final Logger LOGGER = Logger.getLogger(ResourceDAOImpl.class);

    /* (non-Javadoc)
    * @see com.trg.dao.jpa.GenericDAOImpl#persist(T[])
    */
    @Override
    public void persist(Resource... entities)
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.info("Inserting new entities for Resource ... ");
        }

        int size = entities.length;

        Date creation = new Date();
        for (int i = 0; i < size; i++)
        {
            entities[i].setCreation(creation);
        }

        super.persist(entities);
    }

    /* (non-Javadoc)
     * @see com.trg.dao.jpa.GenericDAOImpl#findAll()
     */
    @Override
    public List<Resource> findAll()
    {
        return super.findAll();
    }

    /* (non-Javadoc)
     * @see com.trg.dao.jpa.GenericDAOImpl#find(java.io.Serializable)
     */
    @Override
    public Resource find(Long id)
    {
        return super.find(id);
    }

    /**
     * @param userName
     * @param resourceId
     * @return List<SecurityRule>
     */
    @Override
    public List<SecurityRule> findUserSecurityRule(String userName, long resourceId)
    {
        Search searchCriteria = new Search(Resource.class);
        searchCriteria.addField("security");

        Filter securityFilter = Filter.some("security",
                Filter.and(
                    Filter.equal("resource.id", resourceId),
                    Filter.equal("user.name", userName)));
        searchCriteria.addFilter(securityFilter);

        return super.search(searchCriteria);
    }

    /**
     * @param resourceId
     * @return List<SecurityRule>
     */
    @Override
    public List<SecurityRule> findSecurityRules(long resourceId)
    {
        Search searchCriteria = new Search(Resource.class);
        searchCriteria.addField("security");

        Filter securityFilter = Filter.some("security", Filter.equal("resource.id", resourceId));

        searchCriteria.addFilter(securityFilter);

        return super.search(searchCriteria);
    }

    /**
     * @param resourceId
     * @return List<Attribute>
     */
    @Override
    public List<Attribute> findAttributes(long resourceId)
    {
        Search searchCriteria = new Search(Resource.class);
        searchCriteria.addField("attribute");

        Filter securityFilter = Filter.some("attribute", Filter.equal("resource.id", resourceId));

        searchCriteria.addFilter(securityFilter);

        return super.search(searchCriteria);
    }

    /* (non-Javadoc)
     * @see com.trg.dao.jpa.GenericDAOImpl#search(com.trg.search.ISearch)
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Resource> search(ISearch search)
    {
        return super.search(search);
    }

    /* (non-Javadoc)
     * @see com.trg.dao.jpa.GenericDAOImpl#merge(java.lang.Object)
     */
    @Override
    public Resource merge(Resource entity)
    {
        entity.setLastUpdate(new Date());

        return super.merge(entity);
    }

    /* (non-Javadoc)
     * @see com.trg.dao.jpa.GenericDAOImpl#remove(java.lang.Object)
     */
    @Override
    public boolean remove(Resource entity)
    {
        return super.remove(entity);
    }

    /* (non-Javadoc)
     * @see com.trg.dao.jpa.GenericDAOImpl#removeById(java.io.Serializable)
     */
    @Override
    public boolean removeById(Long id)
    {
        return super.removeById(id);
    }

}
