/*
 * Copyright (C) 2025 GeoSolutions S.A.S.
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
import it.geosolutions.geostore.services.TagService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.RESTTagService;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.TagList;
import java.util.List;
import javax.ws.rs.core.SecurityContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RESTTagServiceImpl implements RESTTagService {

    private static final Logger LOGGER = LogManager.getLogger(RESTTagServiceImpl.class);

    private TagService tagService;

    public void setTagService(TagService tagService) {
        this.tagService = tagService;
    }

    @Override
    public long insert(SecurityContext sc, Tag tag) {
        try {
            if (tag == null) throw new BadRequestWebEx("Tag is null");
            if (tag.getId() != null) throw new BadRequestWebEx("Id should be null");

            return tagService.insert(tag);
        } catch (BadRequestServiceEx e) {
            LOGGER.error(e.getMessage(), e);
            throw new BadRequestWebEx(e.getMessage());
        }
    }

    @Override
    public TagList getAll(SecurityContext sc, Integer page, Integer entries, String nameLike)
            throws BadRequestWebEx {
        try {
            List<Tag> tags = tagService.getAll(page, entries, nameLike);

            long count = 0;
            if (!tags.isEmpty()) {
                count = tagService.count(nameLike);
            }

            return new TagList(tags, count);
        } catch (BadRequestServiceEx e) {
            LOGGER.error(e.getMessage(), e);
            throw new BadRequestWebEx(e.getMessage());
        }
    }

    @Override
    public Tag get(SecurityContext sc, long id) throws NotFoundWebEx {
        Tag tag = tagService.get(id);

        if (tag == null) {
            throw new NotFoundWebEx("Tag not found");
        }

        return tag;
    }

    @Override
    public long update(SecurityContext sc, long id, Tag tag) {
        try {
            return tagService.update(id, tag);
        } catch (BadRequestServiceEx e) {
            LOGGER.error(e.getMessage(), e);
            throw new BadRequestWebEx(e.getMessage());
        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx(e.getMessage());
        }
    }

    @Override
    public void delete(SecurityContext sc, long id) throws NotFoundWebEx {
        try {
            tagService.delete(id);
        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx(e.getMessage());
        }
    }

    @Override
    public void addToResource(SecurityContext sc, long id, long resourceId) throws NotFoundWebEx {
        try {
            tagService.addToResource(id, resourceId);
        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx(e.getMessage());
        }
    }

    @Override
    public void removeFromResource(SecurityContext sc, long id, long resourceId)
            throws NotFoundWebEx {
        try {
            tagService.removeFromResource(id, resourceId);
        } catch (NotFoundServiceEx e) {
            throw new NotFoundWebEx(e.getMessage());
        }
    }
}
