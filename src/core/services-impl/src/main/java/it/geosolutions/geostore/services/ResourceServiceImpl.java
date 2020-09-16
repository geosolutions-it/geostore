/* ====================================================================
 *
 * Copyright (C) 2007 - 2016 GeoSolutions S.A.S.
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

import it.geosolutions.geostore.core.dao.AttributeDAO;
import it.geosolutions.geostore.core.dao.CategoryDAO;
import it.geosolutions.geostore.core.dao.ResourceDAO;
import it.geosolutions.geostore.core.dao.SecurityDAO;
import it.geosolutions.geostore.core.dao.StoredDataDAO;
import it.geosolutions.geostore.core.dao.UserGroupDAO;
import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.StoredData;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.DataType;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.dto.ShortAttribute;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.DuplicatedResourceNameServiceEx;
import it.geosolutions.geostore.services.exception.InternalErrorServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.util.SearchConverter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;

import com.googlecode.genericdao.search.Search;
import com.googlecode.genericdao.search.Sort;

import java.util.LinkedList;

/**
 * Class ResourceServiceImpl.
 *
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * @author ETj (etj at geo-solutions.it)
 */
public class ResourceServiceImpl implements ResourceService
{

    private static final Logger LOGGER = Logger.getLogger(ResourceServiceImpl.class);

    private UserGroupDAO userGroupDAO;

    private ResourceDAO resourceDAO;

    private AttributeDAO attributeDAO;

    private StoredDataDAO storedDataDAO;

    private CategoryDAO categoryDAO;

    private SecurityDAO securityDAO;

    /**
     * @param securityDAO the securityDAO to set
     */
    public void setSecurityDAO(SecurityDAO securityDAO)
    {
        this.securityDAO = securityDAO;
    }

    /**
     * @param storedDataDAO the storedDataDAO to set
     */
    public void setStoredDataDAO(StoredDataDAO storedDataDAO)
    {
        this.storedDataDAO = storedDataDAO;
    }

    /**
     * @param resourceDAO
     */
    public void setResourceDAO(ResourceDAO resourceDAO)
    {
        this.resourceDAO = resourceDAO;
    }

    /**
     * @param attributeDAO
     */
    public void setAttributeDAO(AttributeDAO attributeDAO)
    {
        this.attributeDAO = attributeDAO;
    }

    /**
     * @param categoryDAO the categoryDAO to set
     */
    public void setCategoryDAO(CategoryDAO categoryDAO)
    {
        this.categoryDAO = categoryDAO;
    }

    /**
     * @param userGroupDAO the userGroupDAO to set
     */
    public void setUserGroupDAO(UserGroupDAO userGroupDAO)
    {
        this.userGroupDAO = userGroupDAO;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.ResourceService#insert(it.geosolutions.geostore.core.model.Resource)
     */
    @Override
    public long insert(Resource resource) throws BadRequestServiceEx, NotFoundServiceEx, DuplicatedResourceNameServiceEx
    {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Persisting Resource ... ");
        }

        validateResourceName(resource, false);

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
                throw new NotFoundServiceEx("Resource Category not found [id:" + category.getId()
                        + "]");
            }

        } else if (category.getName() != null) {
            Search searchCriteria = new Search(Category.class);
            searchCriteria.addFilterEqual("name", category.getName());

            List<Category> categories = categoryDAO.search(searchCriteria);
            if (categories.isEmpty()) {
                throw new NotFoundServiceEx("Resource Category not found [name:"
                        + category.getName() + "]");
            }
            loadedCategory = categories.get(0);
        }

        Resource r = new Resource();
        r.setCreation(new Date());
        r.setDescription(resource.getDescription());
        r.setMetadata(resource.getMetadata());
        r.setName(resource.getName());
        r.setCategory(loadedCategory);

        try {
            resourceDAO.persist(r);
        } catch (DataIntegrityViolationException exc) {
            throw new BadRequestServiceEx(exc.getLocalizedMessage());
        }
        try {
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
        } catch(Exception e) {
            // remove resource if we cannot persist other objects bound to the resource
            // (attributes, data, etc.)
            delete(r.getId());
            throw e;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.ResourceService#update(it.geosolutions.geostore.core.model.Resource)
     */
    @Override
    public long update(Resource resource) throws NotFoundServiceEx, DuplicatedResourceNameServiceEx
    {
        Resource orig = resourceDAO.find(resource.getId());
        if (orig == null) {
            throw new NotFoundServiceEx("Resource not found " + resource.getId());
        }

        validateResourceName(resource, true);

        // reset some server-handled data.
        resource.setCreation(orig.getCreation());
        resource.setLastUpdate(new Date());

        resourceDAO.merge(resource);

        return orig.getId();
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.ResourceService#updateAttributes(long, java.util.List)
     */
    @Override
    public void updateAttributes(long id, List<Attribute> attributes) throws NotFoundServiceEx
    {
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

    /*
     * Makes sure that no resource with the same name exists in the database;
     * if a conflict is found, the method throws a DuplicatedResourceNameServiceEx
     * exception, putting an alternative, non-conflicting resource name in the exception's message
     */
    private void validateResourceName(Resource resource, boolean isUpdate) throws DuplicatedResourceNameServiceEx
    {
        Resource existentResource = resourceDAO.findByName(resource.getName());
        if (existentResource != null && !(isUpdate && existentResource.getId().equals(resource.getId()))) {
            String validResourceName = suggestValidResourceName(resource.getName());

            throw new DuplicatedResourceNameServiceEx(validResourceName);
        }
    }

    /*
     * Utility method containing the logic to determine a valid (i.e. not duplicated) resource name.
     * To do so, the method queries the database in search of resouce names matching the pattern:
     *  	[baseResourceName] - [counter]
     *  The maximum employed counter is then established and a unique resource name following the
     *  aforementioned pattern is constructed.
     */
    private String suggestValidResourceName(String baseResourceName)
    {
        final String COUNTER_SEPARATOR = " - ";
        final String BASE_PATTERN = baseResourceName + COUNTER_SEPARATOR;

        final String RESOURCE_NAME_LIKE_PATTERN = BASE_PATTERN + "%";
        final Pattern RESOURCE_NAME_REGEX_PATTERN = Pattern.compile(BASE_PATTERN + "(\\d+)");
        int maxCounter = 0, initialCounter = 2;

        List<String> resourceNames = resourceDAO.findResourceNamesMatchingPattern(RESOURCE_NAME_LIKE_PATTERN);
        for (String resourceName : resourceNames) {
            Matcher matcher = RESOURCE_NAME_REGEX_PATTERN.matcher(resourceName);
            if (matcher.matches()) {
                String suffix = matcher.group(1);
                int suffixAsInteger = 0;
                try {
                    suffixAsInteger = Integer.valueOf(suffix);
                    if (suffixAsInteger > maxCounter) {
                        maxCounter = suffixAsInteger;
                    }
                } catch (NumberFormatException ex) {
                    // ignore: suffix is NOT an integer
                }
            }
        }

        Integer validCounter = Math.max(maxCounter + 1, initialCounter);
        String validName = BASE_PATTERN + validCounter;

        return validName;
    }

    /*
     * @param id
     * 
     * @return the Resource or null if none was found with given id
     * 
     * @see it.geosolutions.geostore.services.ResourceService#get(long)
     */
    @Override
    public Resource get(long id)
    {
        Resource resource = resourceDAO.find(id);

        return resource;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.ResourceService#delete(long)
     */
    @Override
    public boolean delete(long id)
    {
        //
        // data on ancillary tables should be deleted by cascading
        //
        return resourceDAO.removeById(id);
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.ResourceService#delete(long)
     */
    @Override
    public void deleteResources(SearchFilter filter) throws BadRequestServiceEx,
            InternalErrorServiceEx
    {
        Search searchCriteria = SearchConverter.convert(filter);
        this.resourceDAO.removeResources(searchCriteria);
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.ResourceService#getList(java.lang.String, java.lang.Integer, java.lang.Integer)
     */
    @Override
    public List<ShortResource> getList(String nameLike, Integer page, Integer entries, User authUser)
            throws BadRequestServiceEx
    {

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

        // load security rules for each resource in the list
        searchCriteria.addFetch("security");
        searchCriteria.setDistinct(true);

        securityDAO.addReadSecurityConstraints(searchCriteria, authUser);

        if(LOGGER.isTraceEnabled()) {
            LOGGER.trace("Get Resource List: " + searchCriteria);
        }
        List<Resource> found = search(searchCriteria);

        return convertToShortList(found, authUser);
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.ResourceService#getList(java.lang.Integer, java.lang.Integer)
     */
    @Override
    public List<ShortResource> getAll(Integer page, Integer entries, User authUser)
            throws BadRequestServiceEx
    {

        if (((page != null) && (entries == null)) || ((page == null) && (entries != null))) {
            throw new BadRequestServiceEx("Page and entries params should be declared together");
        }

        Search searchCriteria = new Search(Resource.class);

        if (page != null) {
            searchCriteria.setMaxResults(entries);
            searchCriteria.setPage(page);
        }

        searchCriteria.addSortAsc("name");

        // load security rules for each resource in the list
        searchCriteria.addFetch("security");
        searchCriteria.setDistinct(true);

        securityDAO.addReadSecurityConstraints(searchCriteria, authUser);
        
        List<Resource> found = search(searchCriteria);

        return convertToShortList(found, authUser);
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.ResourceService#getCount(java.lang.String)
     */
    @Override
    public long getCount(String nameLike)
    {
        Search searchCriteria = new Search(Resource.class);

        if (nameLike != null) {
            searchCriteria.addFilterILike("name", nameLike);
        }

        return resourceDAO.count(searchCriteria);
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.ResourceService#getCount(java.lang.String)
     */
    @Override
    public long getCountByFilter(SearchFilter filter) throws InternalErrorServiceEx,
            BadRequestServiceEx
    {
        Search searchCriteria = SearchConverter.convert(filter);
        return resourceDAO.count(searchCriteria);
    }

    /**
     * @param list
     * @return List<ShortResource>
     */
    private List<ShortResource> convertToShortList(List<Resource> list, User authUser)
    {
        List<ShortResource> swList = new LinkedList<>();
        for (Resource resource : list) {
            ShortResource shortResource = new ShortResource(resource);

            // ///////////////////////////////////////////////////////////////////////
            // This fragment checks if the authenticated user can modify and delete
            // the loaded resource (and associated attributes and stored data).
            // This to inform the client in HTTP response result.
            // ///////////////////////////////////////////////////////////////////////
            if (authUser != null) {
                if (authUser.getRole().equals(Role.ADMIN)) {
                    shortResource.setCanEdit(true);
                    shortResource.setCanDelete(true);
                } else {
                    for (SecurityRule rule : resource.getSecurity()) {
                        User owner = rule.getUser();
                        UserGroup userGroup = rule.getGroup();
                        if (owner != null) {
                            if (owner.getId().equals(authUser.getId())) {
                                if (rule.isCanWrite()) {
                                    shortResource.setCanEdit(true);
                                    shortResource.setCanDelete(true);

                                    break;
                                }
                            }
                        } else if (userGroup != null) {
                            List<String> groups = extratcGroupNames(authUser.getGroups());
                            if (groups.contains(userGroup.getGroupName())) {
                                if (rule.isCanWrite()) {
                                    shortResource.setCanEdit(true);
                                    shortResource.setCanDelete(true);

                                    break;
                                }
                            }
                        }
                    }
                }
            }

            swList.add(shortResource);
        }

        return swList;
    }

    public static List<String> extratcGroupNames(Set<UserGroup> groups)
    {
        List<String> groupNames = new ArrayList<String>();
        if (groups == null) {
            return groupNames;
        }
        for (UserGroup ug : groups) {
            groupNames.add(ug.getGroupName());
        }
        return groupNames;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.ResourceService#getListAttributes(long)
     */
    @Override
    public List<ShortAttribute> getAttributes(long id)
    {
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
    private List<ShortAttribute> convertToShortAttributeList(List<Attribute> list)
    {
        List<ShortAttribute> swList = new ArrayList<ShortAttribute>(list.size());
        for (Attribute attribute : list) {
            swList.add(new ShortAttribute(attribute));
        }

        return swList;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.ResourceService#getAttribute(long, java.lang.String)
     */
    @Override
    public ShortAttribute getAttribute(long id, String name)
    {
        Search searchCriteria = new Search(Attribute.class);
        searchCriteria.addFilterEqual("resource.id", id);
        searchCriteria.addFilterEqual("name", name);
        List<Attribute> attributes = this.attributeDAO.search(searchCriteria);
        List<ShortAttribute> dtoList = convertToShortAttributeList(attributes);

        if (dtoList.size() > 0) {
            return dtoList.get(0);
        } else {
            return null;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.ResourceService#updateAttribute(long, java.lang.String, java.lang.String)
     */
    @Override
    public long updateAttribute(long id, String name, String value) throws InternalErrorServiceEx
    {
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

    @Override
    public long insertAttribute(long id, String name, String value, DataType type) throws InternalErrorServiceEx
    {
        Search searchCriteria = new Search(Attribute.class);
        searchCriteria.addFilterEqual("resource.id", id);
        searchCriteria.addFilterEqual("name", name);

        List<Attribute> attributes = this.attributeDAO.search(searchCriteria);
        //if the attribute exist, update id (note: it must have the same type)
        if (attributes.size() > 0) {
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
        } else {
            //create the new attribute
            Attribute attribute = new Attribute();
            attribute.setType(type);
            attribute.setName(name);
            attribute.setResource(resourceDAO.find(id));

            switch (type) {
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

            return this.attributeDAO.merge(attribute).getId();

        }

    }


    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.ResourceService#getResourcesByFilter(it.geosolutions.geostore.services.dto.SearchFilter)
     */
    @Override
    public List<ShortResource> getResources(SearchFilter filter, User authUser)
            throws BadRequestServiceEx, InternalErrorServiceEx
    {
        return getResources(filter, null, null, authUser);
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.ResourceService#getResources(it.geosolutions.geostore.services.dto.search.SearchFilter,
     * java.lang.Integer, java.lang.Integer, boolean, boolean, it.geosolutions.geostore.core.model.User)
     */
    public List<Resource> getResources(SearchFilter filter, Integer page, Integer entries,
            boolean includeAttributes, boolean includeData, User authUser)
            throws BadRequestServiceEx, InternalErrorServiceEx
    {

        if (((page != null) && (entries == null)) || ((page == null) && (entries != null))) {
            throw new BadRequestServiceEx("Page and entries params should be declared together");
        }

        Search searchCriteria = SearchConverter.convert(filter);

        if (page != null) {
            searchCriteria.setMaxResults(entries);
            searchCriteria.setPage(page);
        }

        searchCriteria.addFetch("security");
        searchCriteria.setDistinct(true);

        securityDAO.addReadSecurityConstraints(searchCriteria, authUser);
        List<Resource> resources = this.search(searchCriteria);
        resources = this.configResourceList(resources, includeAttributes, includeData, authUser);

        return resources;
    }

    /**
     * @param list
     * @param includeAttributes
     * @param includeData
     * @param authUser
     * @return List<Resource>
     */
    private List<Resource> configResourceList(List<Resource> list, boolean includeAttributes,
            boolean includeData, User authUser)
    {
        List<Resource> rList = new LinkedList<>();

        for (Resource resource : list) {
            Resource res = new Resource();

            res.setCategory(resource.getCategory());
            res.setCreation(resource.getCreation());
            res.setDescription(resource.getDescription());
            res.setId(resource.getId());
            res.setLastUpdate(resource.getLastUpdate());
            res.setName(resource.getName());

            if (includeData) {
                res.setData(resource.getData());
            }

            if (includeAttributes) {
                res.setAttribute(resource.getAttribute());
            }

            rList.add(res);
        }

        return rList;
    }

    @Override
    public List<ShortResource> getResources(SearchFilter filter, Integer page, Integer entries,
            User authUser) throws BadRequestServiceEx, InternalErrorServiceEx
    {

        if (((page != null) && (entries == null)) || ((page == null) && (entries != null))) {
            throw new BadRequestServiceEx("Page and entries params should be declared together");
        }

        Search searchCriteria = SearchConverter.convert(filter);

        if (page != null) {
            searchCriteria.setMaxResults(entries);
            searchCriteria.setPage(page);
        }

        // //////////////////////////////////////////////////////////
        // addFetch to charge the corresponding security rules
        // for each resource in the list
        // //////////////////////////////////////////////////////////
        searchCriteria.addFetch("security");
        searchCriteria.setDistinct(true);

        securityDAO.addReadSecurityConstraints(searchCriteria, authUser);
        List<Resource> resources = this.search(searchCriteria);

        return convertToShortList(resources, authUser);
    }

    /**
     * Return a list of resources joined with their data. This call can be very heavy for the system. Please use this
     * method only when you are sure a few data will be returned, otherwise consider using
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
            throws BadRequestServiceEx, InternalErrorServiceEx
    {

        Search searchCriteria = SearchConverter.convert(filter);
        searchCriteria.addFetch("security");
        searchCriteria.setDistinct(true);
        searchCriteria.addFetch("data");

        securityDAO.addReadSecurityConstraints(searchCriteria, authUser);
        List<Resource> resources = this.search(searchCriteria);

        return resources;
    }

    /**
     * Internal method to apply default search options and search
     * @param searchCriteria search criteria	
     * @return results of the search
     */
    private List<Resource> search(Search searchCriteria) {
    	// apply defaults for sorting
    	if(searchCriteria != null) {
    		searchCriteria.addSort(new Sort("name"));
    	}
    	
    	// search
    	return resourceDAO.search(searchCriteria);
	}

	/*
     * (non-Javadoc)
     * @see it.geosolutions.geostore.services.SecurityService#getUserSecurityRule(java.lang.String, long)
     */
    @Override
    public List<SecurityRule> getUserSecurityRule(String userName, long resourceId)
    {
        return securityDAO.findUserSecurityRule(userName, resourceId);
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.SecurityService#getGroupSecurityRule(java.lang.String, long)
     */
    @Override
    public List<SecurityRule> getGroupSecurityRule(List<String> groupNames, long resourceId)
    {
        return securityDAO.findGroupSecurityRule(groupNames, resourceId);
    }

    @Override
    public List<SecurityRule> getSecurityRules(long id)
            throws BadRequestServiceEx, InternalErrorServiceEx
    {
        return securityDAO.findSecurityRules(id);
    }

    @Override
    public void updateSecurityRules(long id, List<SecurityRule> rules)
            throws BadRequestServiceEx, InternalErrorServiceEx, NotFoundServiceEx
    {
        Resource resource = resourceDAO.find(id);

        if (resource != null) {
            Search searchCriteria = new Search();
            searchCriteria.addFilterEqual("resource.id", id);

            List<SecurityRule> resourceRules = this.securityDAO.search(searchCriteria);

            // remove previous rules
            for (SecurityRule rule : resourceRules) {
                securityDAO.remove(rule);
            }
            // insert new rules
            for (SecurityRule rule : rules) {
                rule.setResource(resource);
                if (rule.getGroup() != null) {
                    UserGroup ug = userGroupDAO.find(rule.getGroup().getId());
                    if (ug != null) {
                        rule.setGroup(ug);
                    }
                }
                securityDAO.persist(rule);
            }
        } else {
            throw new NotFoundServiceEx("Resource not found " + id);
        }
    }

    /**
     * Get filter count by filter and user
     *
     * @param filter
     * @param user
     * @return resources' count that the user has access
     * @throws InternalErrorServiceEx
     * @throws BadRequestServiceEx
     */
    public long getCountByFilterAndUser(SearchFilter filter, User user) throws BadRequestServiceEx, InternalErrorServiceEx
    {
        Search searchCriteria = SearchConverter.convert(filter);
        securityDAO.addReadSecurityConstraints(searchCriteria, user);
        return resourceDAO.count(searchCriteria);
    }

    /**
     * Get filter count by namerLike and user
     *
     * @param nameLike
     * @param user
     * @return resources' count that the user has access
     * @throws InternalErrorServiceEx
     * @throws BadRequestServiceEx
     */
    public long getCountByFilterAndUser(String nameLike, User user) throws BadRequestServiceEx
    {

        Search searchCriteria = new Search(Resource.class);

        if (nameLike != null) {
            searchCriteria.addFilterILike("name", nameLike);
        }

        securityDAO.addReadSecurityConstraints(searchCriteria, user);

        return resourceDAO.count(searchCriteria);
    }
}
