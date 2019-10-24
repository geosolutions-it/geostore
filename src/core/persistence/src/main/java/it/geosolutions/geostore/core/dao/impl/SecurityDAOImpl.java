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

import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import org.springframework.transaction.annotation.Transactional;
import com.googlecode.genericdao.search.Filter;
import com.googlecode.genericdao.search.ISearch;
import com.googlecode.genericdao.search.Search;
import it.geosolutions.geostore.core.dao.SecurityDAO;
import it.geosolutions.geostore.core.dao.UserGroupDAO;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;

/**
 * Class SecurityDAOImpl.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * @author ETj (etj at geo-solutions.it)
 */
@Transactional(value = "geostoreTransactionManager")
public class SecurityDAOImpl extends BaseDAO<SecurityRule, Long> implements SecurityDAO {

    private static final Logger LOGGER = Logger.getLogger(SecurityDAOImpl.class);

    private UserGroupDAO userGroupDAO;
    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#persist(T[])
     */
    @Override
    public void persist(SecurityRule... entities) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.info("Inserting new entities for Security ... ");
        }
        for (SecurityRule rule : entities) {
            validateGroup(rule);
        }
        super.persist(entities);
    }
    
    protected void validateGroup(SecurityRule rule) throws InternalError {
        if (rule.getGroup() != null) {
            UserGroup ug = userGroupDAO.find(rule.getGroup().getId());
            if (ug == null) {
                throw new InternalError("The usergroup having the provided Id doesn't exist");
            }
            rule.setGroup(ug);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#findAll()
     */
    @Override
    public List<SecurityRule> findAll() {
        return super.findAll();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#search(com.trg.search.ISearch)
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<SecurityRule> search(ISearch search) {
        return super.search(search);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#merge(java.lang.Object)
     */
    @Override
    public SecurityRule merge(SecurityRule entity) {
        return super.merge(entity);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.trg.dao.jpa.GenericDAOImpl#remove(java.lang.Object)
     */
    @Override
    public boolean remove(SecurityRule entity) {
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
    
    /**
     * Add security filtering in order to filter out resources the user has not read access to
     */
    public void addReadSecurityConstraints(Search searchCriteria, User user)
    {
        // no further constraints for admin user
        if(user.getRole() == Role.ADMIN) {
            return;
        }

        Filter userFiltering = Filter.equal("user.name", user.getName());

        if(! user.getGroups().isEmpty()) {
            List<Long> groupsId = new ArrayList<>();
            for (UserGroup group : user.getGroups()) {
                groupsId.add(group.getId());
            }
            
            userFiltering = Filter.or( userFiltering, Filter.in("group.id", groupsId));
        }

        Filter securityFilter = Filter.some(
                "security",
                Filter.and(
                        Filter.equal("canRead", true),
                        userFiltering
                        )
                );

        searchCriteria.addFilter(securityFilter);
    }

    /**
     * @param userName
     * @param resourceId
     * @return List<SecurityRule>
     */
    @Override
    public List<SecurityRule> findUserSecurityRule(String userName, long resourceId) {
        Search searchCriteria = new Search(SecurityRule.class);

        Filter securityFilter = 
                Filter.and(Filter.equal("resource.id", resourceId),
                        Filter.equal("user.name", userName));
        searchCriteria.addFilter(securityFilter);
        // now rules are not properly filtered. 
        // so no user rules have to be removed externally (see RESTServiceImpl > ResourceServiceImpl)
        // TODO: apply same worakaround of findGroupSecurityRule or fix searchCriteria issue (when this unit is well tested).
        return super.search(searchCriteria);
    }

    /**
     * @param resourceId
     * @return List<SecurityRule>
     */
    @Override
    public List<SecurityRule> findSecurityRules(long resourceId) {
        Search searchCriteria = new Search(SecurityRule.class);

        Filter securityFilter = Filter.equal("resource.id", resourceId);

        searchCriteria.addFilter(securityFilter);

        return super.search(searchCriteria);
    }
    
    /* (non-Javadoc)
     * @see it.geosolutions.geostore.core.dao.ResourceDAO#findGroupSecurityRule(java.lang.String, long)
     */
    @Override
    public List<SecurityRule> findGroupSecurityRule(List<String> groupNames, long resourceId) {
        List<SecurityRule> rules = findSecurityRules(resourceId);
        //WORKAROUND
        List<SecurityRule> filteredRules = new ArrayList<SecurityRule>();
        for(SecurityRule sr : rules){
            if(sr.getGroup() != null && groupNames.contains(sr.getGroup().getGroupName())){
                filteredRules.add(sr);
            }
        }
        return filteredRules;
    }

    public UserGroupDAO getUserGroupDAO() {
        return userGroupDAO;
    }

    public void setUserGroupDAO(UserGroupDAO userGroupDAO) {
        this.userGroupDAO = userGroupDAO;
    }
    
    
}
