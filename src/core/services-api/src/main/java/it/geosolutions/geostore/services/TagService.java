/*
 * ====================================================================
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
package it.geosolutions.geostore.services;

import it.geosolutions.geostore.core.model.Tag;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import java.util.List;

public interface TagService {

    /**
     * @param tag
     * @return long
     * @throws BadRequestServiceEx
     */
    long insert(Tag tag) throws BadRequestServiceEx;

    /**
     * @param page
     * @param entries
     * @return List<Tag>
     * @throws BadRequestServiceEx
     */
    List<Tag> getAll(Integer page, Integer entries) throws BadRequestServiceEx;

    /**
     * @param id
     * @return Tag
     */
    Tag get(long id);

    /**
     * @param id
     * @param tag
     * @return long
     * @throws NotFoundServiceEx
     * @throws BadRequestServiceEx
     */
    long update(long id, Tag tag) throws BadRequestServiceEx, NotFoundServiceEx;

    /**
     * @param id
     * @return
     */
    boolean delete(long id);
    //    /**
    //     * @return the tag with the exact name requested, or null if none was found
    //     * @throws BadRequestServiceEx is a null name was given
    //     */

    //    Tag get(String name) throws BadRequestServiceEx;

//    /**
//     * @param nameLike
//     * @return long
//     */
//    long getCount(String nameLike);
}
