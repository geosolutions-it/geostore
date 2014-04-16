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
package it.geosolutions.geostore.services.rest.impl;

import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.ResourceService;
import it.geosolutions.geostore.services.SecurityService;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.AndFilter;
import it.geosolutions.geostore.services.dto.search.BaseField;
import it.geosolutions.geostore.services.dto.search.CategoryFilter;
import it.geosolutions.geostore.services.dto.search.FieldFilter;
import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.dto.search.SearchOperator;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.InternalErrorServiceEx;
import it.geosolutions.geostore.services.model.ExtResourceList;
import it.geosolutions.geostore.services.model.ExtUserList;
import it.geosolutions.geostore.services.rest.RESTExtJsService;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.InternalErrorWebEx;

import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.core.SecurityContext;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Class RESTExtJsServiceImpl.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * 
 */
public class RESTExtJsServiceImpl extends RESTServiceImpl implements RESTExtJsService {

    private final static Logger LOGGER = Logger.getLogger(RESTExtJsServiceImpl.class);

    private ResourceService resourceService;

    private UserService userService;

    /**
     * @param resourceService
     */
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * @param userService the userService to set
     */
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTExtJsService#getAllResources (javax.ws.rs.core.UriInfo, javax.ws.rs.core.SecurityContext,
     * java.lang.String, java.lang.Integer, java.lang.Integer)
     */
    @Override
    public String getAllResources(SecurityContext sc, String nameLike, Integer start, Integer limit)
            throws BadRequestWebEx {

        if (start == null || limit == null)
            throw new BadRequestWebEx("Request parameters are missing !");

        if (LOGGER.isInfoEnabled())
            LOGGER.info("Retrieving the paginated resource list ... ");
        
        User authUser = null;
        try{
        	authUser = extractAuthUser(sc);
        }catch (InternalErrorWebEx ie){
        	// serch without user information
        }

        int page = start == 0 ? start : start / limit;

        try {
            nameLike = nameLike.replaceAll("[*]", "%");
            List<ShortResource> resources = getShortResourcesAllowed(resourceService
                    .getList(nameLike, page, limit, authUser), authUser);

            long count = 0;
            if (resources != null && resources.size() > 0)
                count = resourceService.getCountByFilterAndUser(nameLike, authUser);

            JSONObject result = makeJSONResult(true, count, resources, authUser);
            return result.toString();

        } catch (BadRequestServiceEx e) {
            if (LOGGER.isEnabledFor(Level.ERROR))
                LOGGER.error(e.getMessage());

            JSONObject obj = makeJSONResult(false, 0, null, authUser);
            return obj.toString();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTExtJsService#getResourcesByCategory(javax.ws.rs.core.SecurityContext, java.lang.String,
     * java.lang.Integer, java.lang.Integer)
     */
    @Override
    public String getResourcesByCategory(SecurityContext sc, String categoryName, Integer start,
            Integer limit) throws BadRequestWebEx {
        return getResourcesByCategory(sc, categoryName, null, start, limit);
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTExtJsService#getResourcesByCategory(javax.ws.rs.core.SecurityContext, java.lang.String,
     * java.lang.Integer, java.lang.Integer)
     */
    @Override
    public String getResourcesByCategory(SecurityContext sc, String categoryName, String categorySearch, Integer start,
            Integer limit) throws BadRequestWebEx {
    	return getResourcesByCategory(sc, categoryName, categorySearch, null, start, limit);
    }

	@Override
	public String getResourcesByCategory(SecurityContext sc,
			String categoryName, String categorySearch, String extraAttributes,
			Integer start, Integer limit) throws BadRequestWebEx,
			InternalErrorWebEx {
		
		// read extra attribustes
		List<String> extraAttributesList = new LinkedList<String>();
		if(extraAttributes != null){
			if(extraAttributes.contains(",")){
				for(String extra: extraAttributes.split(",")){
					extraAttributesList.add(extra);
				}
			}else{
				extraAttributesList.add(extraAttributes);
			}
		}

        if (((start != null) && (limit == null)) || ((start == null) && (limit != null))) {
            throw new BadRequestWebEx("start and limit params should be declared together");
        }

        if (categoryName == null)
            throw new BadRequestWebEx("Category is null");

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("getResourcesByCategory(" + categoryName + ", start=" + start + ", limit="
                    + limit + (categorySearch != null ? ", search=" + categorySearch : ""));
        
        User authUser = null;
        try{
        	authUser = extractAuthUser(sc);
        }catch (InternalErrorWebEx ie){
        	// search without user information
        }

        Integer page = null;
        if (start != null) {
            page = start / limit;
        }

        try {
            SearchFilter filter = new CategoryFilter(categoryName, SearchOperator.EQUAL_TO);
            if(categorySearch != null){
            	categorySearch = categorySearch.replaceAll("[*]", "%");
            	filter = new AndFilter(filter, new FieldFilter(BaseField.NAME, categorySearch, SearchOperator.LIKE));
            }

            List<Resource> resources = getResourcesAllowed(resourceService.getResources(filter, page, limit, true, false,
                    authUser), authUser);

            long count = 0;
            if (resources != null && resources.size() > 0)
                count = resourceService.getCountByFilterAndUser(filter, authUser);
            
            JSONObject result = makeExtendedJSONResult(true, count, resources, authUser, extraAttributesList);
            return result.toString();

        } catch (InternalErrorServiceEx e) {
            if (LOGGER.isEnabledFor(Level.ERROR))
                LOGGER.error(e.getMessage());

            JSONObject obj = makeJSONResult(false, 0, null, authUser);
            return obj.toString();
        } catch (BadRequestServiceEx e) {
            if (LOGGER.isEnabledFor(Level.ERROR))
                LOGGER.error(e.getMessage());

            JSONObject obj = makeJSONResult(false, 0, null, authUser);
            return obj.toString();
        }
	}

    /*
     * (non-Javadoc)
     * 
     * @see it.geosolutions.geostore.services.rest.RESTExtJsService#getResourcesList(javax.ws.rs.core.SecurityContext, java.lang.Integer,
     * java.lang.Integer, boolean, boolean, it.geosolutions.geostore.services.dto.search.SearchFilter)
     */
    @Override
    public ExtResourceList getExtResourcesList(SecurityContext sc, Integer start, Integer limit,
            boolean includeAttributes, SearchFilter filter) throws BadRequestWebEx {

        if (((start != null) && (limit == null)) || ((start == null) && (limit != null))) {
            throw new BadRequestWebEx("start and limit params should be declared together");
        }

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("getResourcesList(start=" + start + ", limit=" + limit
                    + ", includeAttributes=" + includeAttributes);
        
        User authUser = null;
        try{
        	authUser = extractAuthUser(sc);
        }catch (InternalErrorWebEx ie){
        	// serch without user information
        }

        Integer page = null;
        if (start != null) {
            page = start / limit;
        }

        try {
            List<Resource> resources = getResourcesAllowed(resourceService.getResources(filter, page, limit,
                    includeAttributes, false, authUser), authUser);

            // Here the Read permission on each resource must be checked due to will be returned the full Resource not just a ShortResource
            // N.B. This is a bad method to check the permissions on each requested resource, it can perform 2 database access for each resource.
            // Possible optimization -> When retrieving the resources, add to "filter" also another part to load only the allowed resources.

            long count = 0;
            if (resources != null && resources.size() > 0)
                count = resourceService.getCountByFilterAndUser(filter, authUser);

            ExtResourceList list = new ExtResourceList(count, resources);
            return list;

        } catch (InternalErrorServiceEx e) {
            if (LOGGER.isEnabledFor(Level.ERROR))
                LOGGER.error(e.getMessage());

            return null;
        } catch (BadRequestServiceEx e) {
            if (LOGGER.isEnabledFor(Level.ERROR))
                LOGGER.error(e.getMessage());

            return null;
        }
    }

    /**
     * @param success
     * @param count
     * @param resources
     * @param extraAttributes 
     * @return JSONObject
     */
    private JSONObject makeExtendedJSONResult(boolean success, long count, List<Resource> resources,
            User authUser, List<String> extraAttributes) {
        return makeGeneralizedJSONResult(success, count, resources, authUser, extraAttributes);
    }

    /**
     * @param success
     * @param count
     * @param resources
     * @return JSONObject
     */
    private JSONObject makeJSONResult(boolean success, long count, List<ShortResource> resources,
            User authUser) {
        return makeGeneralizedJSONResult(success, count, resources, authUser, null);
    }
    
    @Override
    public ExtUserList getUsersList(SecurityContext sc, String nameLike, Integer start,
            Integer limit, boolean includeAttributes) throws BadRequestWebEx {

        if (((start != null) && (limit == null)) || ((start == null) && (limit != null))) {
            throw new BadRequestWebEx("start and limit params should be declared together");
        }

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("getUsersList(start=" + start + ", limit=" + limit);

        Integer page = null;
        if (start != null) {
            page = start / limit;
        }

        try {
            nameLike = nameLike.replaceAll("[*]", "%");
            List<User> users = userService.getAll(page, limit, nameLike, includeAttributes);

            long count = 0;
            if (users != null && users.size() > 0)
                count = userService.getCount(nameLike);

            ExtUserList list = new ExtUserList(count, users);
            return list;

        } catch (BadRequestServiceEx e) {
            if (LOGGER.isEnabledFor(Level.ERROR))
                LOGGER.error(e.getMessage());

            return null;
        }
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.impl.RESTServiceImpl#getSecurityService()
     */
    @Override
    protected SecurityService getSecurityService() {
        return resourceService;
    }
    
    /**
     * Generalize method. Use this.ResourceEnvelop class
     * @param success
     * @param count
     * @param resources
     * @param authUser
     * @param extraAttributes
     * @return
     */
    private JSONObject makeGeneralizedJSONResult(boolean success, long count, List<?> resources,
            User authUser, List<String> extraAttributes) {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("success", success);
        jsonObj.put("totalCount", count);

        if (resources != null) {
            Iterator<?> iterator = resources.iterator();

            JSON result;

            int size = resources.size();
            if (size == 0)
                result = null;
            else if (size > 1)
                result = new JSONArray();
            else
                result = new JSONObject();

            while (iterator.hasNext()) {
            	Object obj = iterator.next();
            	ResourceEnvelop sr = null;
            	if(obj instanceof Resource){
            		sr = new ResourceEnvelop((Resource)obj, authUser);
            	}else if(obj instanceof ShortResource){
            		sr = new ResourceEnvelop((ShortResource)obj, authUser);
            	}

                if (sr != null) {
                    JSONObject jobj = new JSONObject();

                	jobj.element("canDelete", sr.isCanDelete());
                    jobj.element("canEdit", sr.isCanEdit());
                    jobj.element("canCopy", sr.isCanCopy());

                    Date date = sr.getCreation();
                    if (date != null)
                        jobj.element("creation", date.toString());

                    date = sr.getLastUpdate();
                    if (date != null)
                        jobj.element("lastUpdate", date.toString());

                    String description = sr.getDescription();
                    if (description != null)
                        jobj.element("description", description);

                    jobj.element("id", sr.getId());
                    jobj.element("name", sr.getName());
                    
                    // Append extra attributes
                    if(extraAttributes != null && sr.getAttribute() != null){
                    	for(Attribute at: sr.getAttribute()){
                    		if(extraAttributes.contains(at.getName())){
                                jobj.element(at.getName(), at.getValue());
                    		}
                    	}
                    }

                    String owner = sr.getOwner();
                    if (owner != null)
                        jobj.element("owner", owner);

                    if (result instanceof JSONArray)
                        ((JSONArray) result).add(jobj);
                    else
                        result = jobj;
                }
            }

            jsonObj.put("results", result != null ? result.toString() : "");
        } else {
            jsonObj.put("results", "");
        }

        return jsonObj;
    }
    
    /**
     * Encapsulates resource/short resource and credentials to perform operations with resources
     * 
     * @author adiaz
     *
     */
    private class ResourceEnvelop{
    	ShortResource sr;
    	Resource r;
    	String owner;
    	User authUser;
    	boolean canEdit = false;
    	boolean canDelete = false;
    	/**
    	 * Create a resource envelop based on a short resource
    	 * @param sr Short resource
    	 * @param authUser user logged
    	 */
    	private ResourceEnvelop(ShortResource sr, User authUser){
    		super();
    		this.sr = sr;
    		this.authUser = authUser;
    		readSecurity();
    	}
    	/**
    	 * Create a resource envelop based on a resource
    	 * @param r resource
    	 * @param authUser user logged
    	 */
		private ResourceEnvelop(Resource r, User authUser){
    		super();
    		this.r = r;
    		this.authUser = authUser;
    		readSecurity();
    	}
		
		/**
		 * Read security for edit and delete
		 */
		private void readSecurity() {
			if (sr != null) {
				canDelete = sr.isCanDelete();
				canEdit = sr.isCanEdit();
			} else {
				// ///////////////////////////////////////////////////////////////////////
				// This fragment checks if the authenticated user can modify and
				// delete
				// the loaded resource (and associated attributes and stored
				// data).
				// This to inform the client in HTTP response result.
				// ///////////////////////////////////////////////////////////////////////
				if (authUser != null) {
					if (authUser.getRole().equals(Role.ADMIN)) {
						canEdit = true;
						canDelete = true;
					} else {
						for (SecurityRule rule : resourceService
								.getUserSecurityRule(authUser.getName(),
										r.getId())) {
							User owner = rule.getUser();
							UserGroup userGroup = rule.getGroup();
							if (owner != null) {
								if (owner.getId().equals(authUser.getId())) {
									if (rule.isCanWrite()) {
										canEdit = true;
										canDelete = true;
										break;
									}
								}
							} else if (userGroup != null) {
								if (authUser.getGroups() != null
										&& authUser.getGroups().contains(
												userGroup.getGroupName())) {
									if (rule.isCanWrite()) {
										canEdit = true;
										canDelete = true;
										break;
									}
								}
							}
						}
					}
				}
			}
		}
		
		/**
		 * @return true if the logged user is owner of the resource and false otherwise 
		 */
    	boolean isCanDelete(){
    		return canDelete;
    	}
		/**
		 * @return true if the logged user is owner of the resource and false otherwise 
		 */
    	boolean isCanEdit(){
    		return canEdit;
    	}
    	/**
    	 * @return data creation
    	 */
        Date getCreation(){
    		return sr != null ? sr.getCreation() : r.getCreation();
        }
        /**
         * @return last update
         */
        Date getLastUpdate(){
    		return sr != null ? sr.getLastUpdate() : r.getLastUpdate();
        }
        /**
         * @return resource description
         */
        String getDescription(){
    		return sr != null ? sr.getDescription() : r.getDescription();
        }
        /**
         * @return resource id
         */
        long getId(){
    		return sr != null ? sr.getId() : r.getId();
        }
        /**
         * @return resource name
         */
        String getName(){
    		return sr != null ? sr.getName() : r.getName();
        }
        /**
         * @return resource attributes if contains
         */
        List<Attribute> getAttribute(){
    		return r != null ? r.getAttribute(): null;
        }
		/**
		 * @return true if there are an user logged 
		 */
    	public Boolean isCanCopy() {
			return authUser != null;
		}
    	/**
    	 * @return resource owner
    	 */
    	public String getOwner() {
			return owner;
		}
    }
}
