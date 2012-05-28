/*
 *  Copyright (C) 2007 - 2011 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.impl;

import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.StoredData;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.StoredDataService;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.RESTStoredDataService;
import it.geosolutions.geostore.services.rest.exception.BadRequestWebEx;
import it.geosolutions.geostore.services.rest.exception.ForbiddenErrorWebEx;
import it.geosolutions.geostore.services.rest.exception.InternalErrorWebEx;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.utils.GeoStorePrincipal;

import java.io.StringReader;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;

import net.sf.json.JSON;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.xml.XMLSerializer;

import org.apache.log4j.Logger;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * Class RESTStoredDataServiceImpl.
 *
 * @author ETj (etj at geo-solutions.it)
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 *
 */
public class RESTStoredDataServiceImpl implements RESTStoredDataService {

    private final static Logger LOGGER = Logger.getLogger(RESTStoredDataServiceImpl.class);

    private StoredDataService storedDataService;

    /**
     * @param storedDataService
     */
    public void setStoredDataService(StoredDataService storedDataService) {
        this.storedDataService = storedDataService;
    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTStoredDataService#update(long, java.lang.String)
     */
    @Override
    public long update(SecurityContext sc, long id, String data) throws NotFoundWebEx {
        try {
            if(data == null)
                throw new BadRequestWebEx("Data is null");

            //
            // Authorization check.
            //
            boolean canUpdate = false;
			User authUser = extractAuthUser(sc);
			canUpdate = resourceAccess(authUser, id); // The ID is also the resource ID

    		if(canUpdate){
    			storedDataService.update(id, data);
    		}else{
    			throw new ForbiddenErrorWebEx("This user cannot update or create this store !");
    		}

            return id;
        } catch (NotFoundServiceEx ex) {
            LOGGER.warn("Data not found (" + id + "): " + ex.getMessage(), ex );
            throw new NotFoundWebEx("Data not found");
        }
    }

//    /* (non-Javadoc)
//     * @see it.geosolutions.geostore.services.rest.RESTStoredDataService#getAll()
//     */
//    @Override
//    public StoredDataList getAll(SecurityContext sc) {
//        return new StoredDataList(storedDataService.getAll());
//    }

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTStoredDataService#delete(long)
     */
    @Override
    public void delete(SecurityContext sc, long id) throws NotFoundWebEx {
        //
        // Authorization check.
        //
        boolean canDelete = false;
		User authUser = extractAuthUser(sc);
		canDelete = resourceAccess(authUser, id);

		if(canDelete){
	        boolean ret = storedDataService.delete(id);
	        if(!ret)
	            throw new NotFoundWebEx("Data not found");
		}else
			throw new ForbiddenErrorWebEx("This user cannot delete this store !");
    }


    private final static Collection<MediaType> GET_XML_MEDIA_TYPES = Arrays.asList(MediaType.TEXT_XML_TYPE, MediaType.APPLICATION_XML_TYPE);
    private final static Collection<MediaType> GET_JSON_MEDIA_TYPES = Arrays.asList(MediaType.APPLICATION_JSON_TYPE);
    private final static Collection<MediaType> GET_TEXT_MEDIA_TYPES = Arrays.asList(MediaType.TEXT_PLAIN_TYPE);

    /* (non-Javadoc)
     * @see it.geosolutions.geostore.services.rest.RESTStoredDataService#get(long)
     */
    @Override
    public String get(SecurityContext sc, HttpHeaders headers, long id) throws NotFoundWebEx {
        if(id == -1)
           return "dummy payload";

        StoredData storedData;
        try {
            storedData = storedDataService.get(id);
        } catch(NotFoundServiceEx e){
        	throw new NotFoundWebEx("Data not found");
        }

        String data = storedData == null? "" : storedData.getData();

        // prefer no transformation
        if( headers.getAcceptableMediaTypes().contains(MediaType.WILDCARD_TYPE)) {
            return data;
        } else if(! Collections.disjoint(GET_TEXT_MEDIA_TYPES, headers.getAcceptableMediaTypes())) {
            return data;
        } else if(! Collections.disjoint(GET_JSON_MEDIA_TYPES, headers.getAcceptableMediaTypes())) {
            return toJSON(data);
        } else if(! Collections.disjoint(GET_XML_MEDIA_TYPES, headers.getAcceptableMediaTypes())) {
            return toXML(data);
        } else
            throw new InternalErrorWebEx("Illegal state ("+headers.getAcceptableMediaTypes()+")");
    }

    private String toJSON(String data) {

        try {
            // ////////////////////////
            // XML to JSON
            // ////////////////////////
            XMLSerializer xmlSerializer = new XMLSerializer();
            JSON json = xmlSerializer.read(data);
            String ret = json.toString();
            if(LOGGER.isDebugEnabled())
                LOGGER.debug("Transformed XML -> JSON");
            return ret;
        } catch (JSONException exc) {
            if(LOGGER.isDebugEnabled())
                LOGGER.debug("Data is not in native XML format.");
        }

        try {
            // ///////////////////////
            // data To JSON conversion
            // ///////////////////////
            JSONSerializer.toJSON(data);            
            if(LOGGER.isDebugEnabled())
                LOGGER.debug("Data is in native JSON format.");
            return data;

        } catch (JSONException e) {
            if(LOGGER.isDebugEnabled())
                LOGGER.debug("Data is not in native JSON format.");
        }

        JSONObject jsonObj = new JSONObject();
        jsonObj.put("data", data);
        String ret = jsonObj.toString();
        if(LOGGER.isDebugEnabled())
            LOGGER.debug("Transformed plaintext -> JSON");

        return ret;
    }

    private String toXML(String data) {

        // Try XML source
        try {
            StringReader reader = new StringReader(data);
            SAXBuilder builder = new SAXBuilder();
            builder.build(reader);
            // no errors: return the original data
            if(LOGGER.isDebugEnabled())
                LOGGER.debug("Data is in native XML format.");
            return data;
        } catch (Exception e) {
            if(LOGGER.isDebugEnabled())
                LOGGER.debug("Data is not in native XML format.");
        }

        // Try JSON source
        try {
            // ///////////////////////
            // JSON To XML conversion
            // ///////////////////////
            JSON json = JSONSerializer.toJSON(data);
            XMLSerializer xmlSerializer = new XMLSerializer();
            String ret = xmlSerializer.write(json);
            if(LOGGER.isDebugEnabled())
                LOGGER.debug("Transformed JSON -> XML");
            return ret;

        } catch (JSONException exc) {
            if(LOGGER.isDebugEnabled())
                LOGGER.debug("Data is not in native JSON format.");
        }

        // Force XML format
        Element element = new Element("data");
        element.addContent(data);

        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        String ret = outputter.outputString(element);
        if(LOGGER.isDebugEnabled())
            LOGGER.debug("Transformed plaintext -> XML");

        return ret;
    }


    /**
     * @param id
     * @return long
     * @throws BadRequestWebEx
     */
    @SuppressWarnings("unused")
	private long parseId(String id) throws BadRequestWebEx {
        try {
            return Long.parseLong(id);
        } catch (Exception e) {
            LOGGER.info("Bad id requested '"+id+"'");
            throw new BadRequestWebEx("Bad id");
        }
    }

    /**
     * @return User - The authenticated user that is accessing this service, or null if guest access.
     */
    private User extractAuthUser(SecurityContext sc) throws InternalErrorWebEx {
        if(sc == null)
            throw new InternalErrorWebEx("Missing auth info");
        else {
            Principal principal = sc.getUserPrincipal();
            if(principal == null){
    			if(LOGGER.isInfoEnabled())
    				LOGGER.info("Missing auth principal");
    			throw new InternalErrorWebEx("Missing auth principal");
            }

            if( ! (principal instanceof GeoStorePrincipal )){
    			if(LOGGER.isInfoEnabled())
    				LOGGER.info("Missing auth principal");
    			throw new InternalErrorWebEx("Mismatching auth principal (" + principal.getClass() + ")");
            }

            GeoStorePrincipal gsp = (GeoStorePrincipal)principal;

            //
            // may be null if guest
            //
            User user = gsp.getUser();

            LOGGER.info("Accessing service with user " + (user == null ? "GUEST" : user.getName()));
            return user;
        }
    }

    /**
     * Check if the user can access the requested resource (is own resource or not ?)
	 * in order to update it.
	 *
     * @param resource
     * @return boolean
     */
    private boolean resourceAccess(User authUser, long resourceId) {
    	boolean canAccess = false;

    	if(authUser.getRole().equals(Role.ADMIN)){
    		canAccess = true;
    	}else{
        	List<SecurityRule> securityRules  = storedDataService.getUserSecurityRule(authUser.getName(), resourceId);

    		if(securityRules != null && ! securityRules.isEmpty() )
    			canAccess = true;
    	}

		return canAccess;
    }

}
