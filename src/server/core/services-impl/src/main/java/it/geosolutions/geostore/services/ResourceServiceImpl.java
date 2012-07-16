/* ====================================================================
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

import com.googlecode.genericdao.search.Search;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import it.geosolutions.geostore.core.dao.AttributeDAO;
import it.geosolutions.geostore.core.dao.CategoryDAO;
import it.geosolutions.geostore.core.dao.ResourceDAO;
import it.geosolutions.geostore.core.dao.SecurityDAO;
import it.geosolutions.geostore.core.dao.StoredDataDAO;
import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.StoredData;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.dto.ShortAttribute;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.InternalErrorServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.util.SearchConverter;

import org.apache.log4j.Logger;

/**
 * Class ResourceServiceImpl.
 *
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * @author ETj (etj at geo-solutions.it)
 */
public class ResourceServiceImpl implements ResourceService {

    private static final Logger LOGGER = Logger.getLogger(ResourceServiceImpl.class);
    private ResourceDAO resourceDAO;
    private AttributeDAO attributeDAO;
    private StoredDataDAO storedDataDAO;
    private CategoryDAO categoryDAO;
    private SecurityDAO securityDAO;

    /**
     * @param securityDAO the securityDAO to set
     */
    public void setSecurityDAO(SecurityDAO securityDAO) {
        this.securityDAO = securityDAO;
    }

    /**
     * @param storedDataDAO the storedDataDAO to set
     */
    public void setStoredDataDAO(StoredDataDAO storedDataDAO) {
        this.storedDataDAO = storedDataDAO;
    }

    /**
     * @param resourceDAO
     */
    public void setResourceDAO(ResourceDAO resourceDAO) {
        this.resourceDAO = resourceDAO;
    }

    /**
     * @param attributeDAO
     */
    public void setAttributeDAO(AttributeDAO attributeDAO) {
        this.attributeDAO = attributeDAO;
    }

    /**
     * @param categoryDAO the categoryDAO to set
     */
    public void setCategoryDAO(CategoryDAO categoryDAO) {
        this.categoryDAO = categoryDAO;
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.ResourceService#insert(it.geosolutions.geostore.core.model.Resource)
     */
    @Override
    public long insert(Resource resource) throws BadRequestServiceEx, NotFoundServiceEx {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Persisting Resource ... ");
        }

        Category category = resource.getCategory();
        if (category == null) {
            throw new BadRequestServiceEx("Category type must be specified");
        }

        //
        // Searching the corresponding Category
        //
        Category loadedCategory = null;

        if (category.getId() != null) {
            loadedCategory = categoryDAO.find(category.getId());
            if (loadedCategory == null) {
                throw new NotFoundServiceEx("Resource Category not found [id:" + category.getId() + "]");
            }

        } else if (category.getName() != null) {
            Search searchCriteria = new Search(Category.class);
            searchCriteria.addFilterEqual("name", category.getName());

            List<Category> categories = categoryDAO.search(searchCriteria);
            if (categories.isEmpty()) {
                throw new NotFoundServiceEx("Resource Category not found [name:" + category.getName() + "]");
            }
            loadedCategory = categories.get(0);
        }


        Resource r = new Resource();
        r.setCreation(new Date());
        r.setDescription(resource.getDescription());
        r.setMetadata(resource.getMetadata());
        r.setName(resource.getName());
        r.setCategory(loadedCategory);

        resourceDAO.persist(r);

        //
        // Persisting Attributes
        //
        List<Attribute> attributes = resource.getAttribute();
        if (attributes != null) {
            for (Attribute a : attributes) {
                a.setResource(r);
                attributeDAO.persist(a);
            }
        }

        //
        // Persisting StoredData
        //
        StoredData data = resource.getData();
        if (data != null) {
            data.setId(r.getId());
            data.setResource(r);
            storedDataDAO.persist(data);
        }

        //
        // Persisting SecurityRule
        //
        List<SecurityRule> rules = resource.getSecurity();

        if (rules != null) {
            for (SecurityRule rule : rules) {
                rule.setResource(r);
                securityDAO.persist(rule);
            }
        }

        return r.getId();
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.ResourceService#update(it.geosolutions.geostore.core.model.Resource)
     */
    @Override
    public long update(Resource resource) throws NotFoundServiceEx {
        Resource orig = resourceDAO.find(resource.getId());
        if (orig == null) {
            throw new NotFoundServiceEx("Resource not found " + resource.getId());
        }

        // reset some server-handled data.
        resource.setCreation(orig.getCreation());
        resource.setLastUpdate(new Date());

        resourceDAO.merge(resource);

        return orig.getId();
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.ResourceService#updateAttributes(long, java.util.List)
     */
    @Override
    public void updateAttributes(long id, List<Attribute> attributes) throws NotFoundServiceEx {
        Resource resource = resourceDAO.find(id);
        if (resource == null) {
            throw new NotFoundServiceEx("Resource not found " + id);
        }

        //
        // Removing old attributes
        //
        List<Attribute> oldList = resource.getAttribute();

        if (oldList != null) {
            for (Attribute a : oldList) {
                attributeDAO.removeById(a.getId());
            }
        }

        //
        // Saving old attributes
        //
        for (Attribute a : attributes) {
            a.setResource(resource);
            attributeDAO.persist(a);
        }
    }

    /* @param id
     * @return the Resource or null if none was found with given id
     *
     * @see it.geosolutions.geostore.services.ResourceService#get(long)
     */
    @Override
    public Resource get(long id) {
        Resource resource = resourceDAO.find(id);
        
        return resource;
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.ResourceService#delete(long)
     */
    @Override
    public boolean delete(long id) {
        //
        // data on ancillary tables should be deleted by cascading
        //
        return resourceDAO.removeById(id);
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.ResourceService#getList(java.lang.String, java.lang.Integer, java.lang.Integer)
     */
    @Override
    public List<ShortResource> getList(String nameLike, Integer page, Integer entries, User authUser)
            throws BadRequestServiceEx {

        if (((page != null) && (entries == null)) || ((page == null) && (entries != null))) {
            throw new BadRequestServiceEx("Page and entries params should be declared together");
        }

        Search searchCriteria = new Search(Resource.class);

        if (page != null) {
            searchCriteria.setMaxResults(entries);
            searchCriteria.setPage(page);
        }

        searchCriteria.addSortAsc("name");

        if (nameLike != null) {
            searchCriteria.addFilterILike("name", nameLike);
        }

        // //////////////////////////////////////////////////////////
        // addFetch to charge the corresponding security rules
        // for each resource in the list
        // //////////////////////////////////////////////////////////
        searchCriteria.addFetch("security");

        List<Resource> found = resourceDAO.search(searchCriteria);

        return convertToShortList(found, authUser);
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.ResourceService#getList(java.lang.Integer, java.lang.Integer)
     */
    @Override
    public List<ShortResource> getAll(Integer page, Integer entries, User authUser) throws BadRequestServiceEx {

        if (((page != null) && (entries == null)) || ((page == null) && (entries != null))) {
            throw new BadRequestServiceEx("Page and entries params should be declared together");
        }

        Search searchCriteria = new Search(Resource.class);

        if (page != null) {
            searchCriteria.setMaxResults(entries);
            searchCriteria.setPage(page);
        }

        searchCriteria.addSortAsc("name");

        // //////////////////////////////////////////////////////////
        // addFetch to charge the corresponding security rules
        // for each resource in the list
        // //////////////////////////////////////////////////////////
        searchCriteria.addFetch("security");

        List<Resource> found = resourceDAO.search(searchCriteria);

        return convertToShortList(found, authUser);
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.ResourceService#getCount(java.lang.String)
     */
    @Override
    public long getCount(String nameLike) {
        Search searchCriteria = new Search(Resource.class);

        if (nameLike != null) {
            searchCriteria.addFilterILike("name", nameLike);
        }

        return resourceDAO.count(searchCriteria);
    }

    /**
     * @param list
     * @return List<ShortResource>
     */
    private List<ShortResource> convertToShortList(List<Resource> list, User authUser) {
        List<ShortResource> swList = new ArrayList<ShortResource>(list.size());
        for (Resource resource : list) {
            ShortResource shortResource = new ShortResource(resource);

            // ///////////////////////////////////////////////////////////////////////
            // This fragment checks if the authenticated user can modify and delete
            // the loaded resource (and associated attributes and stored data).
            // This to inform the client in HTTP response result.
            // ///////////////////////////////////////////////////////////////////////
            if (authUser != null) {
                List<SecurityRule> securities = resource.getSecurity();
                Iterator<SecurityRule> iterator = securities.iterator();

                while (iterator.hasNext()) {
                    SecurityRule rule = iterator.next();
                    User owner = rule.getUser();
                    
                    if (owner.getId().equals(authUser.getId()) || authUser.getRole().equals(Role.ADMIN)) {
                        if (rule.isCanWrite()) {
                            shortResource.setCanEdit(true);
                            shortResource.setCanDelete(true);
                            
                            break;
                        }
                    }
                }
            }

            swList.add(shortResource);
        }

        return swList;
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.ResourceService#getListAttributes(long)
     */
    @Override
    public List<ShortAttribute> getAttributes(long id) {
        Search searchCriteria = new Search(Attribute.class);
        searchCriteria.addFilterEqual("resource.id", id);

        List<Attribute> attributes = this.attributeDAO.search(searchCriteria);
        List<ShortAttribute> dtoList = convertToShortAttributeList(attributes);

        return dtoList;
    }

    /**
     * @param list
     * @return List<ShortAttribute>
     */
    private List<ShortAttribute> convertToShortAttributeList(List<Attribute> list) {
        List<ShortAttribute> swList = new ArrayList<ShortAttribute>(list.size());
        for (Attribute attribute : list) {
            swList.add(new ShortAttribute(attribute));
        }

        return swList;
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.ResourceService#getAttribute(long, java.lang.String)
     */
    @Override
    public ShortAttribute getAttribute(long id, String name) {
        Search searchCriteria = new Search(Attribute.class);
        searchCriteria.addFilterEqual("resource.id", id);
        searchCriteria.addFilterEqual("name", name);

        List<Attribute> attributes = this.attributeDAO.search(searchCriteria);
        List<ShortAttribute> dtoList = convertToShortAttributeList(attributes);

        if(dtoList.size() > 0)
        	return dtoList.get(0);
        else
        	return null;       
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.ResourceService#updateAttribute(long, java.lang.String, java.lang.String)
     */
    @Override
    public long updateAttribute(long id, String name, String value) throws InternalErrorServiceEx {
        Search searchCriteria = new Search(Attribute.class);
        searchCriteria.addFilterEqual("resource.id", id);
        searchCriteria.addFilterEqual("name", name);

        List<Attribute> attributes = this.attributeDAO.search(searchCriteria);

        Attribute attribute = attributes.get(0);

        switch (attribute.getType()) {
            case DATE:

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                try {
                    attribute.setDateValue(sdf.parse(value));
                } catch (ParseException e) {
                    throw new InternalErrorServiceEx("Error parsing attribute date value");
                }
                break;
            case NUMBER:
                attribute.setNumberValue(Double.valueOf(value));
                break;
            case STRING:
                attribute.setTextValue(value);
                break;
            default:
                throw new IllegalStateException("Unknown type " + attribute.getType());
        }

        attribute = this.attributeDAO.merge(attribute);

        return attribute.getId();
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.ResourceService#getResourcesByFilter(it.geosolutions.geostore.services.dto.SearchFilter)
     */
    @Override
    public List<ShortResource> getResources(SearchFilter filter, User authUser) throws BadRequestServiceEx,
            InternalErrorServiceEx {
        Search searchCriteria = SearchConverter.convert(filter);

        // //////////////////////////////////////////////////////////
        // addFetch to charge the corresponding security rules
        // for each resource in the list
        // //////////////////////////////////////////////////////////
        searchCriteria.addFetch("security"); // TODO: Test this, should be runs

        List<Resource> resources = this.resourceDAO.search(searchCriteria);

        return convertToShortList(resources, authUser);
    }

    /**
     * Return a list of resources joined with their data.
     * This call can be very heavy for the system. Please use this method only when you are sure
     * a few data will be returned, otherwise consider using
     * {@link #getResources(it.geosolutions.geostore.services.dto.search.SearchFilter, it.geosolutions.geostore.core.model.User) getResources)
     * if you need less data.
     *
     * @param filter
     * @param authUser
     * @return
     * @throws BadRequestServiceEx
     * @throws InternalErrorServiceEx
     */
    @Override
    public List<Resource> getResourcesFull(SearchFilter filter, User authUser) 
            throws BadRequestServiceEx,InternalErrorServiceEx {

        Search searchCriteria = SearchConverter.convert(filter);
        searchCriteria.addFetch("security");
        searchCriteria.addFetch("data");

        List<Resource> resources = this.resourceDAO.search(searchCriteria);

        return resources;
    }

    @Override
    public List<SecurityRule> getUserSecurityRule(String userName, long resourceId) {
        return resourceDAO.findUserSecurityRule(userName, resourceId);
    }
}
