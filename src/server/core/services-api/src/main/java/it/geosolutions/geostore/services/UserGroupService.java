/*
 *  Copyright (C) 2007-2012 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services;

import java.util.List;

import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;

/**
 * @author DamianoG
 *
 */
public interface UserGroupService {

    /**
     * 
     * @param userGroup
     * @return
     * @throws BadRequestServiceEx
     */
    long insert(UserGroup userGroup) throws BadRequestServiceEx;
    
    /**
     * 
     * @param id
     * @throws NotFoundServiceEx
     */
    void delete(long id) throws NotFoundServiceEx;
    
    /**
     * 
     * @param userId
     * @param groupId
     * @throws NotFoundServiceEx
     */
    void assignUserGroup(long userId, long groupId) throws NotFoundServiceEx;
    
    /**
     * 
     * @param page
     * @param entries
     * @return
     * @throws BadRequestServiceEx
     */
    List<UserGroup> getAll(Integer page, Integer entries) throws BadRequestServiceEx;
}
