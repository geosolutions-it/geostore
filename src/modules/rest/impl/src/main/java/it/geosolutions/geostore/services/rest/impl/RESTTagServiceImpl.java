/*
 * $ Header: it.geosolutions.georepo.services..rest.impl.RESTTagServiceImpl ,v. 0.1 9-set-2011 10.39.58 created by tobaro <tobia.dipisa at geo-solutions.it> $
 * $ Revision: 0.1 $
 * $ Date: 8-set-2011 10.39.58 $
 *
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
package it.geosolutions.geostore.services.rest.impl;

import it.geosolutions.geostore.core.model.Tag;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.SecurityService;
import it.geosolutions.geostore.services.TagService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.RESTTagService;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.ForbiddenErrorWebEx;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.TagList;
import javax.ws.rs.core.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RESTTagServiceImpl extends RESTServiceImpl implements RESTTagService {

    private static final Logger LOGGER = LogManager.getLogger(RESTTagServiceImpl.class);

    private TagService tagService;

    /** @param tagService the tagService to set */
    public void setTagService(TagService tagService) {
        this.tagService = tagService;
    }

    @Override
    public long insert(SecurityContext sc, Tag tag) {
        if (tag == null) throw new BadRequestWebEx("Tag is null");
        if (tag.getId() != null) throw new BadRequestWebEx("Id should be null");

        try {
            return tagService.insert(tag);
        } catch (BadRequestServiceEx e) {
            throw new BadRequestWebEx(e.getMessage());
        }
    }

    @Override
    public TagList getAll(SecurityContext sc, Integer page, Integer entries)
            throws BadRequestWebEx {
        try {
            return new TagList(tagService.getAll(page, entries));
        } catch (BadRequestServiceEx ex) {
            throw new BadRequestWebEx(ex.getMessage());
        }
    }

    @Override
    public Tag get(SecurityContext sc, long id) throws NotFoundWebEx {
        Tag ret = tagService.get(id);

        if (ret == null) {
            throw new NotFoundWebEx("Tag not found");
        }

        return ret;
    }

    @Override
    public long update(SecurityContext sc, long id, Tag tag) {
        try {
            //            Tag old = tagService.get(id);
            //
            //            if (old == null) {
            //                throw new NotFoundWebEx("Tag not found");
            //            }

            return tagService.update(id, tag);

        } catch (BadRequestServiceEx e) {
            throw new BadRequestWebEx(e.getMessage());
        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx(e.getMessage());
        }
    }

    @Override
    public void delete(SecurityContext sc, long id) throws NotFoundWebEx {
        //
        // Authorization check.
        //
        boolean canDelete = false;
        User authUser = extractAuthUser(sc);
        canDelete = resourceAccessWrite(authUser, id);

        if (canDelete) {
            boolean ret = tagService.delete(id);
            if (!ret) throw new NotFoundWebEx("Tag not found");
        } else throw new ForbiddenErrorWebEx("This user cannot delete this tag !");
    }

    @Override
    protected SecurityService getSecurityService() {
        return null;
    }
}
