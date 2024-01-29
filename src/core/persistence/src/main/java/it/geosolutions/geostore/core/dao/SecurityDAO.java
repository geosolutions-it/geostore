/*
 *  Copyright (C) 2007 - 2011 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.core.dao;

import java.util.List;
import com.googlecode.genericdao.search.Search;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;

/**
 * Interface SecurityDAO.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * 
 */
public interface SecurityDAO extends RestrictedGenericDAO<SecurityRule> {
    /**
     * Add security filtering in order to filter out resources the user has not read access to
     */
    void addReadSecurityConstraints(Search searchCriteria, User user);

    /**
     * Add security filtering in order to filter out resources hidden the user
     */
    void addAdvertisedSecurityConstraints(Search searchCriteria, User user);

    /**
     * @param userName
     * @param resourceId
     * @return List<SecurityRule>
     */
    public List<SecurityRule> findUserSecurityRule(String userName, long resourceId);

    /**
     *
     * @param groupNames
     * @param resourceId
     * @return
     */
    public List<SecurityRule> findGroupSecurityRule(List<String> groupNames, long resourceId);

    /**
     * @param resourceId
     * @return List<SecurityRule>
     */
    public List<SecurityRule> findSecurityRules(long resourceId);
}
