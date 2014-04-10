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

import it.geosolutions.geostore.core.dao.ResourceDAO;
import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.hibernate.Hibernate;
import org.springframework.transaction.annotation.Transactional;

import com.googlecode.genericdao.search.Filter;
import com.googlecode.genericdao.search.ISearch;
import com.googlecode.genericdao.search.Search;

/**
 * Class ResourceDAOImpl.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * @author ETj (etj at geo-solutions.it)
 */
@Transactional(value = "geostoreTransactionManager")
public class ResourceDAOImpl extends BaseDAO<Resource, Long> implements ResourceDAO {

    private static final Logger LOGGER = Logger.getLogger(ResourceDAOImpl.class);

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#persist(T[])
     */
    @Override
    public void persist(Resource... entities) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.info("Inserting new entities for Resource ... ");
        }

        int size = entities.length;

        Date creation = new Date();
        for (int i = 0; i < size; i++) {
            entities[i].setCreation(creation);
        }

        super.persist(entities);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#findAll()
     */
    @Override
    public List<Resource> findAll() {
        return super.findAll();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#find(java.io.Serializable)
     */
    @Override
    public Resource find(Long id) {
        return super.find(id);
    }

    /**
     * @param userName
     * @param resourceId
     * @return List<SecurityRule>
     */
    @Override
    public List<SecurityRule> findUserSecurityRule(String userName, long resourceId) {
        Search searchCriteria = new Search(Resource.class);
        searchCriteria.addField("security");

        Filter securityFilter = Filter.some(
                "security",
                Filter.and(Filter.equal("resource.id", resourceId),
                        Filter.equal("user.name", userName)));
        searchCriteria.addFilter(securityFilter);

        return super.search(searchCriteria);
    }

    /**
     * @param resourceId
     * @return List<SecurityRule>
     */
    @Override
    public List<SecurityRule> findSecurityRules(long resourceId) {
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
    public List<Attribute> findAttributes(long resourceId) {
        Search searchCriteria = new Search(Resource.class);
        searchCriteria.addField("attribute");

        Filter securityFilter = Filter.some("attribute", Filter.equal("resource.id", resourceId));

        searchCriteria.addFilter(securityFilter);

        return super.search(searchCriteria);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#search(com.trg.search.ISearch)
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<Resource> search(ISearch search) {
        return super.search(search);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#merge(java.lang.Object)
     */
    @Override
    public Resource merge(Resource entity) {
        entity.setLastUpdate(new Date());

        return super.merge(entity);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#remove(java.lang.Object)
     */
    @Override
    public boolean remove(Resource entity) {
        return super.remove(entity);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#remove(java.lang.Object)
     */
    @Override
    public void removeResources(ISearch search) {
        List<Resource> resources = super.search(search);
        super.remove(resources.toArray(new Resource[1]));
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#removeById(java.io.Serializable)
     */
    @Override
    public boolean removeById(Long id) {
        return super.removeById(id);
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.core.dao.ResourceDAO#findGroupSecurityRule(java.lang.String, long)
     */
    @Override
    public List<SecurityRule> findGroupSecurityRule(List<String> groupNames, long resourceId) {
        Search searchCriteria = new Search(Resource.class);
        searchCriteria.addField("security");
        
        Filter securityFilter = Filter.some("security", Filter.equal("resource.id", resourceId));
        //Advanced filters Filters doesn't work, I don't know why...
//        Filter securityFilter = Filter.some(
//                "security",
//                Filter.and(Filter.equal("resource.id", resourceId),
//                        Filter.in("group.groupName", groupNames),
//                        Filter.isNotEmpty("group")));
        
        searchCriteria.addFilter(securityFilter);
        List<SecurityRule> rules = super.search(searchCriteria);
        //WORKAROUND
        List<SecurityRule> filteredRules = new ArrayList<SecurityRule>();
        for(SecurityRule sr : rules){
            if(sr.getGroup() != null && groupNames.contains(sr.getGroup().getGroupName())){
                filteredRules.add(sr);
            }
        }
        return filteredRules;
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.core.dao.ResourceDAO#findResources(java.util.List)
     */
    @Override
    public List<Resource> findResources(List<Long> resourcesIds) {
        Search search = new Search(Resource.class);
        Filter filter = Filter.in("id", resourcesIds); 
        search.addFilter(filter);
        List<Resource> resourceToSet = super.search(search);
        //Initialize Lazy collection
        for(Resource resource : resourceToSet){
            if(!Hibernate.isInitialized(resource.getSecurity())){
                Hibernate.initialize(resource.getSecurity());
            }
        }
        return resourceToSet;
    }

    /**
     * Get criteria count by user
     * @param searchCriteria
     * @param user
     * @return resources' count that the user has access 
     */
	@Override
	public long count(Search searchCriteria, User user) {
        searchCriteria.addField("security");
        
        List<Long> groupsId = new ArrayList<Long>();
        for(UserGroup group: user.getGroups()){
        	groupsId.add(group.getId());
        }

        Filter securityFilter = Filter.some(
                "security",
                Filter.and(Filter.or(Filter.in("group.id", groupsId),
                		Filter.equal("user.name", user.getName())),
            		Filter.equal("canRead", true)
                        ));
        searchCriteria.addFilter(securityFilter);
		return count(searchCriteria);
	}

}
