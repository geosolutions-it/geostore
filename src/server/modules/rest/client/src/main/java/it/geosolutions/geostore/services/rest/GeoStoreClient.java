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
package it.geosolutions.geostore.services.rest;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.rest.model.CategoryList;
import it.geosolutions.geostore.services.rest.model.RESTCategory;
import it.geosolutions.geostore.services.rest.model.RESTResource;
import it.geosolutions.geostore.services.rest.model.ShortResourceList;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author ETj (etj at geo-solutions.it)
 */
public class GeoStoreClient {

    private String username = null;
    private String password = null;
    private String geostoreRestUrl = null;

    public GeoStoreClient() {
    }

    //==========================================================================
    //=== RESOURCES
    //==========================================================================

    public ShortResourceList searchResources(SearchFilter searchFilter) {        
//        WebResource wr = getBaseWebResource();
//
//        ShortResourceList resourceList = wr.path("resources").path("search")
        ShortResourceList resourceList = getBaseWebResource("resources","search")
                .header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .post(ShortResourceList.class, searchFilter);

        return resourceList;
    }

    public Long insert(RESTResource resource) {
//        WebResource wr = getBaseWebResource();
//
//        String sid = wr.path("resources")
        String sid = getBaseWebResource("resources")
                .header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_PLAIN)
                .post(String.class, resource);

        return Long.parseLong(sid);
    }

    public Resource getResource(Long id, boolean full) {
//        WebResource wr = getBaseWebResource();
//        Resource resource = wr.path("resources").path("resource").path(id.toString())
        WebResource resource = getBaseWebResource("resources","resource",id);
        if(full)
            resource = resource.queryParam("full", Boolean.valueOf(full).toString());

        return resource.header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(Resource.class);
    }

    public Resource getResource(Long id) {
        return getResource(id, false);
    }

    public void deleteResource(Long id) {
        getBaseWebResource("resources", "resource", id).delete();
    }

    public void updateResource(Long id, RESTResource resource) {
        getBaseWebResource("resources", "resource", id)
                .header("Content-Type", MediaType.TEXT_XML)                
                .put(resource);
    }


    //==========================================================================
    //=== DATA
    //==========================================================================

    public String getData(Long id) {
        return getData(id, MediaType.WILDCARD_TYPE);
    }

    public String getData(Long id, MediaType acceptMediaType ) {
        String data = getBaseWebResource("data", id)
                .accept(acceptMediaType)
                .get(String.class);
        return data;
    }

    public void setData(Long id, String data ) {
        getBaseWebResource("data", id).put(data);
    }

    //==========================================================================
    //=== CATEGORIES
    //==========================================================================

    public Long insert(RESTCategory category) {
        String sid = getBaseWebResource("categories")
                .header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_PLAIN)
                .post(String.class, category);

        return Long.parseLong(sid);
    }

    public Category getCategory(Long id) {
        Category category = getBaseWebResource("categories","category",id)
                .header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(Category.class);

        return category;
    }

//    @GET
//    @Path("/")
//    @RolesAllowed({"ADMIN", "USER", "GUEST"})
//    CategoryList getAll(
//    		@Context SecurityContext sc,
//            @QueryParam("page") Integer page,
//            @QueryParam("entries") Integer entries)throws BadRequestWebEx;

    public CategoryList getCategories(Integer page, Integer entries) {
        return getBaseWebResource("categories")
            .queryParam("page", page.toString())
            .queryParam("entries", entries.toString())
            .header("Content-Type", MediaType.TEXT_XML)
            .accept(MediaType.TEXT_XML)
            .get(CategoryList.class);
    }

    public CategoryList getCategories() {
        return getBaseWebResource("categories")
            .header("Content-Type", MediaType.TEXT_XML)
            .accept(MediaType.TEXT_XML)
            .get(CategoryList.class);
    }

    public void deleteCategory(Long id) {
        getBaseWebResource("categories", "category", id).delete();
    }


    //==========================================================================
    //==========================================================================

    protected WebResource getBaseWebResource() {
        if(geostoreRestUrl == null)
            throw new IllegalStateException("GeoStore URL not set");

        Client c = Client.create();
        if (username != null || password != null) {
            c.addFilter(new HTTPBasicAuthFilter(username != null ? username : "", password != null ? password : ""));
        }

        WebResource wr = c.resource(geostoreRestUrl);
        return wr;
    }

//    protected WebResource getBaseWebResource(Object ...path) {
//        if(geostoreRestUrl == null)
//            throw new IllegalStateException("GeoStore URL not set");
//
//        Client c = Client.create();
//        if (username != null || password != null) {
//            c.addFilter(new HTTPBasicAuthFilter(username != null ? username : "", password != null ? password : ""));
//        }
//
//        WebResource wr = c.resource(geostoreRestUrl);
//
//        for (Object o : path) {
//            wr = wr.path(o.toString());
//        }
//        return wr;
//    }

    protected WebResource getBaseWebResource(Object ...path) {
        if(geostoreRestUrl == null)
            throw new IllegalStateException("GeoStore URL not set");

        Client c = Client.create();
        if (username != null || password != null) {
            c.addFilter(new HTTPBasicAuthFilter(username != null ? username : "", password != null ? password : ""));
        }

        StringBuilder fullpath = new StringBuilder(geostoreRestUrl);
        for (Object o : path) {
            String p = o.toString();
            if(fullpath.charAt(fullpath.length()-1) != '/')
                fullpath.append('/');
            fullpath.append(p);
        }
        WebResource wr = c.resource(fullpath.toString());
        return wr;
    }

    //==========================================================================
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getGeostoreRestUrl() {
        return geostoreRestUrl;
    }

    public void setGeostoreRestUrl(String geostoreRestUrl) {
        this.geostoreRestUrl = geostoreRestUrl;
    }

}
