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
package it.geosolutions.geostore.services;

import com.googlecode.genericdao.search.Search;
import it.geosolutions.geostore.core.dao.TagDAO;
import it.geosolutions.geostore.core.model.Tag;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TagServiceImpl implements TagService {

    private static final Logger LOGGER = LogManager.getLogger(TagServiceImpl.class);

    private TagDAO tagDAO;

    public void setTagDAO(TagDAO tagDAO) {
        this.tagDAO = tagDAO;
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
    public List<Tag> getAll(Integer page, Integer entries) throws BadRequestServiceEx {

        if (((page != null) && (entries == null)) || ((page == null) && (entries != null))) {
            throw new BadRequestServiceEx("Page and entries params should be declared together.");
        }

        Search searchCriteria = new Search(Tag.class);

        if (page != null) {
            searchCriteria.setMaxResults(entries);
            searchCriteria.setPage(page);
        }

        searchCriteria.addSortAsc("name");

        return tagDAO.search(searchCriteria);
    }

    @Override
    public Tag get(long id) {
        return tagDAO.find(id);
    }

    @Override
    public long update(long id, Tag tag) throws BadRequestServiceEx, NotFoundServiceEx {
        if (get(id) == null) {
            throw new NotFoundServiceEx("Tag not found");
        }
        tag.setId(id);
        tagDAO.merge(tag);
        return id;
    }

    @Override
    public boolean delete(long id) {
        return tagDAO.removeById(id);
    }
//
//    @Override
//    public long getCount(String nameLike) {
//        Search searchCriteria = new Search(Tag.class);
//
//        if (nameLike != null) {
//            searchCriteria.addFilterILike("name", nameLike);
//        }
//
//        return tagDAO.count(searchCriteria);
//    }
}
