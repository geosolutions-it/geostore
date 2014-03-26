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

import com.googlecode.genericdao.search.Filter;
import com.googlecode.genericdao.search.ISearch;
import com.googlecode.genericdao.search.Search;

import java.util.List;

import it.geosolutions.geostore.core.dao.StoredDataDAO;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.StoredData;

import org.apache.log4j.Logger;
import org.springframework.transaction.annotation.Transactional;

/**
 * Class StoredDataDAOImpl.
 * 
 * @author Emanuele Tajariol (etj at geo-solutions.it)
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 */
@Transactional(value = "geostoreTransactionManager")
public class StoredDataDAOImpl extends BaseDAO<StoredData, Long> implements StoredDataDAO {

    private static final Logger LOGGER = Logger.getLogger(StoredDataDAOImpl.class);

    @Override
    public void persist(StoredData... entities) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.info("Inserting new entities for StoredData ... ");
        }

        super.persist(entities);
    }

    @Override
    public List<StoredData> findAll() {
        return super.findAll();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<StoredData> search(ISearch search) {
        return super.search(search);
    }

    @Override
    public StoredData merge(StoredData entity) {
        return super.merge(entity);
    }

    @Override
    public boolean remove(StoredData entity) {
        return super.remove(entity);
    }

    @Override
    public boolean removeById(Long id) {
        return super.removeById(id);
    }

    @Override
    public List<SecurityRule> findUserSecurityRule(String name, long resourceId) {
        Search searchCriteria = new Search(StoredData.class);
        searchCriteria.addField("resource.security");

        Filter securityFilter = Filter.some(
                "resource.security",
                Filter.and(Filter.equal("resource.security.resource.id", resourceId),
                        Filter.equal("resource.security.user.name", name)));
        searchCriteria.addFilter(securityFilter);

        return super.search(searchCriteria);
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.core.dao.StoredDataDAO#findGroupSecurityRule(java.lang.String, long)
     */
    @Override
    public List<SecurityRule> findGroupSecurityRule(List<String> userGroups, long resourceId) {
        Search searchCriteria = new Search(StoredData.class);
        searchCriteria.addField("resource.security");

        Filter securityFilter = Filter.some(
                "resource.security",
                Filter.and(Filter.equal("resource.security.resource.id", resourceId),
                        Filter.equal("resource.security.user.groups.groupName", userGroups)));
        searchCriteria.addFilter(securityFilter);

        return super.search(searchCriteria);
    }

}
