/*
 *  Copyright (C) 2007 - 2012 GeoSolutions S.A.S.
 *  http://www.geo-solutions.it
 *
 *  GPLv3 + Classpath exception
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.geosolutions.geostore.core.dao.impl;

import com.googlecode.genericdao.search.Search;
import it.geosolutions.geostore.core.dao.UserGroupDAO;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;

import java.util.List;

import it.geosolutions.geostore.core.model.UserGroupAttribute;
import org.hibernate.Hibernate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.transaction.annotation.Transactional;

import com.googlecode.genericdao.search.ISearch;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * Class UserGroupDAOImpl.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * @author ETj (etj at geo-solutions.it)
 */
@Transactional(value = "geostoreTransactionManager")
public class UserGroupDAOImpl extends BaseDAO<UserGroup, Long> implements UserGroupDAO {

    private static final Logger LOGGER = LogManager.getLogger(UserGroupDAOImpl.class);

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#persist(T[])
     */
    @Override
    public void persist(UserGroup... entities) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.info("Inserting new entities for Attribute ... ");
        }

        super.persist(entities);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#findAll()
     */
    @Override
    public List<UserGroup> findAll() {
        return super.findAll();
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#find(java.io.Serializable)
     */
    @Override
    public UserGroup find(Long id) {
        UserGroup group= super.find(id);
        if (group != null) {
            initializeLazyMembers(group);
        }
        return group;
    }

    private void initializeLazyMembers(UserGroup group){
        if (Hibernate.isInitialized(group)) {
            List<UserGroupAttribute> attributes = group.getAttributes();
            Hibernate.initialize(attributes);
            List<SecurityRule> secRules = group.getSecurity();
            Hibernate.initialize(secRules);
        }
    }

    @Override
    public UserGroup findByName(String name){
        Search searchCriteria = new Search(UserGroup.class);
        searchCriteria.addFilterEqual("groupName", name);
        UserGroup result=null;
        List<UserGroup> existingGroups = search(searchCriteria);
        if (existingGroups.size() > 0) {
            result = existingGroups.get(0);
            initializeLazyMembers(result);
        }
        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#search(com.trg.search.ISearch)
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<UserGroup> search(ISearch search) {
        return super.search(search);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#merge(java.lang.Object)
     */
    @Override
    public UserGroup merge(UserGroup entity) {
        return super.merge(entity);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#remove(java.lang.Object)
     */
    @Override
    public boolean remove(UserGroup entity) {
        return super.remove(entity);
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

}
