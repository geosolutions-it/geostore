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
package it.geosolutions.geostore.services;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;

import java.util.Collection;
import java.util.List;

/**
 * Class UserInterface.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * 
 */
public interface UserService {

    /**
     * @param user
     * @return long
     * @throws BadRequestServiceEx
     * @throws NotFoundServiceEx
     */
    long insert(User user) throws BadRequestServiceEx, NotFoundServiceEx;

    /**
     * @param user
     * @return long
     * @throws NotFoundServiceEx
     * @throws BadRequestServiceEx
     */
    long update(User user) throws NotFoundServiceEx, BadRequestServiceEx;

    /**
     * @param id
     * @return boolean
     */
    boolean delete(long id);

    /**
     * @param id
     * @return User
     */
    User get(long id);

    /**
     * @param name
     * @return User
     * @throws NotFoundWebEx
     */
    public User get(String name) throws NotFoundServiceEx;

    /**
     * @param page
     * @param entries
     * @return List<User>
     * @throws BadRequestServiceEx
     */
    List<User> getAll(Integer page, Integer entries) throws BadRequestServiceEx;

    /**
     * @param page
     * @param entries
     * @param nameLike
     * @param includeAttributes
     * @return List<User>
     * @throws BadRequestServiceEx
     */
    List<User> getAll(Integer page, Integer entries, String nameLike, boolean includeAttributes)
            throws BadRequestServiceEx;

    /**
     * @param nameLike
     * @return long
     */
    long getCount(String nameLike);

    /**
     * @param id
     * @param attributes
     * @throws NotFoundServiceEx
     */
    void updateAttributes(long id, List<UserAttribute> attributes) throws NotFoundServiceEx;
    
    /**
     * Persist the special Users, those that implies special behavior (Like GUEST)
     * 
     * For obvious reasons this Method MUST NOT exposed through the rest interface.
     * 
     * @return true if the persist operation finish with success, false otherwise  
     */
    public boolean insertSpecialUsers();
    
    /**
     * Returns all user with the specified attribute (name / value).
     * 
     * @param attribute
     * @return
     */
    public Collection<User>  getByAttribute(UserAttribute attribute);
    public Collection<User>  getByGroup(UserGroup group);

}
