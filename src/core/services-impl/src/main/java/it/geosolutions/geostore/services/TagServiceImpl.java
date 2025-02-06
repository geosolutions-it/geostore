/*
 *  Copyright (C) 2025 GeoSolutions S.A.S.
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

import com.googlecode.genericdao.search.Search;
import it.geosolutions.geostore.core.dao.ResourceDAO;
import it.geosolutions.geostore.core.dao.TagDAO;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.Tag;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.transaction.annotation.Transactional;

public class TagServiceImpl implements TagService {

    private static final Logger LOGGER = LogManager.getLogger(TagServiceImpl.class);

    private TagDAO tagDAO;
    private ResourceDAO resourceDAO;

    public void setTagDAO(TagDAO tagDAO) {
        this.tagDAO = tagDAO;
    }

    public void setResourceDAO(ResourceDAO resourceDAO) {
        this.resourceDAO = resourceDAO;
    }

    @Override
    public long insert(Tag tag) throws BadRequestServiceEx {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Persisting Tag ... ");
        }

        if (tag == null) {
            throw new BadRequestServiceEx("Tag must be specified");
        }

        tagDAO.persist(tag);

        return tag.getId();
    }

    @Override
    public List<Tag> getAll(Integer page, Integer entries, String nameLike)
            throws BadRequestServiceEx {

        if (page != null && entries == null || page == null && entries != null) {
            throw new BadRequestServiceEx("Page and entries params should be declared together.");
        }

        Search searchCriteria = new Search(Tag.class);

        searchCriteria.addSortAsc("name");

        if (page != null) {
            searchCriteria.setMaxResults(entries);
            searchCriteria.setPage(page);
        }
        if (nameLike != null) {
            searchCriteria.addFilterILike("name", nameLike);
        }

        return tagDAO.search(searchCriteria);
    }

    @Override
    public Tag get(long id) {
        return tagDAO.find(id);
    }

    @Override
    public long update(long id, Tag tag) throws BadRequestServiceEx, NotFoundServiceEx {
        Tag original = get(id);
        if (original == null) {
            throw new NotFoundServiceEx("Tag not found");
        }

        tag.setId(id);
        tag.setResources(original.getResources());

        tagDAO.merge(tag);

        return id;
    }

    @Override
    public void delete(long id) throws NotFoundServiceEx {
        if (get(id) == null || !tagDAO.removeById(id)) {
            throw new NotFoundServiceEx("Tag not found");
        }
    }

    @Override
    public long count(String nameLike) {

        Search searchCriteria = new Search(Tag.class);

        if (nameLike != null) {
            searchCriteria.addFilterILike("name", nameLike);
        }

        return tagDAO.count(searchCriteria);
    }

    @Override
    @Transactional(value = "geostoreTransactionManager")
    public void addToResource(long id, long resourceId) throws NotFoundServiceEx {

        Tag tag = get(id);
        if (tag == null) {
            throw new NotFoundServiceEx("Tag not found");
        }

        Resource resource = resourceDAO.find(resourceId);
        if (resource == null) {
            throw new NotFoundServiceEx("Resource not found");
        }

        tag.getResources().add(resource);

        tagDAO.persist(tag);
    }

    @Override
    @Transactional(value = "geostoreTransactionManager")
    public void removeFromResource(long id, long resourceId) throws NotFoundServiceEx {

        Tag tag = get(id);
        if (tag == null) {
            throw new NotFoundServiceEx("Tag not found");
        }

        Resource resource = resourceDAO.find(resourceId);
        if (resource == null) {
            throw new NotFoundServiceEx("Resource not found");
        }

        tag.getResources().remove(resource);

        tagDAO.persist(tag);
    }
}
