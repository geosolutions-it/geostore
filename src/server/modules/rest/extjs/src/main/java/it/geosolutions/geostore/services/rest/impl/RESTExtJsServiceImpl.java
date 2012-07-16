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

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.ResourceService;
import it.geosolutions.geostore.services.dto.ShortAttribute;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.rest.RESTExtJsService;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.InternalErrorWebEx;
import it.geosolutions.geostore.services.rest.utils.GeoStorePrincipal;

import java.security.Principal;
import java.util.Date;
import java.util.Iterator;
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
public class RESTExtJsServiceImpl implements RESTExtJsService {

	private final static Logger LOGGER = Logger
			.getLogger(RESTExtJsServiceImpl.class);

	private ResourceService resourceService;

	/**
	 * @param resourceService
	 */
	public void setResourceService(ResourceService resourceService) {
		this.resourceService = resourceService;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * it.geosolutions.geostore.services.rest.RESTExtJsService#getAllResources
	 * (javax.ws.rs.core.UriInfo, javax.ws.rs.core.SecurityContext,
	 * java.lang.String, java.lang.Integer, java.lang.Integer)
	 */
	@Override
	public String getAllResources(SecurityContext sc, String nameLike,
			Integer start, Integer limit) throws BadRequestWebEx {

		if (start == null || limit == null)
			throw new BadRequestWebEx("Request parameters are missing !");

		if (LOGGER.isInfoEnabled())
			LOGGER.info("Retrieving the paginated resource list ... ");

		User authUser = extractAuthUser(sc);

		int page = start == 0 ? start : start / limit;

		try {
			nameLike = nameLike.replaceAll("[*]", "%");
			List<ShortResource> resources = resourceService.getList(nameLike,
					page, limit, authUser);

			long count = 0;
			if (resources != null && resources.size() > 0)
				count = resourceService.getCount(nameLike);

			JSONObject result = makeJSONResult(true, count, resources, authUser);
			return result.toString();

		} catch (BadRequestServiceEx e) {
			if (LOGGER.isEnabledFor(Level.ERROR))
				LOGGER.error(e.getMessage());

			JSONObject obj = makeJSONResult(false, 0, null, authUser);
			return obj.toString();
		}
	}

	/**
	 * @param success
	 * @param count
	 * @param resources
	 * @return JSONObject
	 */
	private JSONObject makeJSONResult(boolean success, long count,
			List<ShortResource> resources, User authUser) {
		JSONObject jsonObj = new JSONObject();
		jsonObj.put("success", success);
		jsonObj.put("totalCount", count);

		if (resources != null) {
			Iterator<ShortResource> iterator = resources.iterator();

			JSON result;

			int size = resources.size();
			if (size == 0)
				result = null;
			else if (size > 1)
				result = new JSONArray();
			else
				result = new JSONObject();

			while (iterator.hasNext()) {
				ShortResource sr = iterator.next();

				if (sr != null) {
					JSONObject jobj = new JSONObject();
					jobj.element("canDelete", sr.isCanDelete());
					jobj.element("canEdit", sr.isCanEdit());
					
					if(authUser != null)
						jobj.element("canCopy", true);
					else
						jobj.element("canCopy", false);
					
					Date date = sr.getCreation();
					if(date != null)
						jobj.element("creation", date.toString());
				
					date = sr.getLastUpdate();
					if(date != null)
						jobj.element("lastUpdate", date.toString());
					
					String description = sr.getDescription();
					if(description != null)
						jobj.element("description", description);
					
					jobj.element("id", sr.getId());
					jobj.element("name", sr.getName());
					
					ShortAttribute owner = resourceService.getAttribute(sr.getId(), "owner");
					if(owner != null)
						jobj.element("owner", owner.getValue());

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
	 * @return User - The authenticated user that is accessing this service, or
	 *         null if guest access.
	 */
	private User extractAuthUser(SecurityContext sc) throws InternalErrorWebEx {
		if (sc == null)
			throw new InternalErrorWebEx("Missing auth info");
		else {
			Principal principal = sc.getUserPrincipal();
			if (principal == null) {
				if (LOGGER.isInfoEnabled())
					LOGGER.info("Missing auth principal");
				throw new InternalErrorWebEx("Missing auth principal");
			}

			if (!(principal instanceof GeoStorePrincipal)) {
				if (LOGGER.isInfoEnabled())
					LOGGER.info("Missing auth principal");
				throw new InternalErrorWebEx("Mismatching auth principal ("
						+ principal.getClass() + ")");
			}

			GeoStorePrincipal gsp = (GeoStorePrincipal) principal;

			//
			// may be null if guest
			//
			User user = gsp.getUser();

			LOGGER.info("Accessing service with user "
					+ (user == null ? "GUEST" : user.getName()));
			return user;
		}
	}

}
