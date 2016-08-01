/*
 *  Copyright (C) 2007 - 2016 GeoSolutions S.A.S.
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

import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.rest.client.model.ExtGroupList;
import it.geosolutions.geostore.services.rest.model.CategoryList;
import it.geosolutions.geostore.services.rest.model.RESTCategory;
import it.geosolutions.geostore.services.rest.model.RESTResource;
import it.geosolutions.geostore.services.rest.model.ResourceList;
import it.geosolutions.geostore.services.rest.model.SecurityRuleList;
import it.geosolutions.geostore.services.rest.model.ShortResourceList;

import javax.ws.rs.core.MediaType;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.rest.model.enums.RawFormat;

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

    // ==========================================================================
    // === RESOURCES
    // ==========================================================================

    /**
     * @deprecated the REST service call is deprecated and should be replaced by
     *             {@link #searchResources(it.geosolutions.geostore.services.dto.search.SearchFilter, java.lang.Integer, java.lang.Integer, java.lang.Boolean, java.lang.Boolean) }
     */
    @Deprecated
    public ShortResourceList searchResources(SearchFilter searchFilter)
    {
        ShortResourceList resourceList = getBaseWebResource("resources", "search")
                .header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .post(ShortResourceList.class, searchFilter);

        return resourceList;
    }

    public ResourceList searchResources(SearchFilter searchFilter,
            Integer page, Integer entries,
            Boolean includeAttributes, Boolean includeData)
    {
        WebResource wb = getBaseWebResource("resources", "search", "list");

        wb = addQParam(wb, "page", page);
        wb = addQParam(wb, "entries", entries);

        wb = addQParam(wb, "includeAttributes", includeAttributes);
        wb = addQParam(wb, "includeData", includeData);

        return wb.header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .post(ResourceList.class, searchFilter);
    }

    protected WebResource addQParam(WebResource wb, String key, Object value)
    {
        if (value != null)
            return wb.queryParam(key, value.toString());
        else
            return wb;
    }

    public Long insert(RESTResource resource)
    {
        String sid = getBaseWebResource("resources")
                .header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_PLAIN)
                .post(String.class, resource);

        return Long.parseLong(sid);
    }

    public Resource getResource(Long id, boolean full)
    {
        WebResource resource = getBaseWebResource("resources", "resource", id);
        if (full)
            resource = resource.queryParam("full", Boolean.toString(full));

        return resource.header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(Resource.class);
    }

    public Resource getResource(Long id)
    {
        return getResource(id, false);
    }

    public void deleteResource(Long id)
    {
        getBaseWebResource("resources", "resource", id).delete();
    }

    public void updateResource(Long id, RESTResource resource)
    {
        getBaseWebResource("resources", "resource", id)
                .header("Content-Type", MediaType.TEXT_XML)
                .put(resource);
    }

    public ShortResourceList getAllShortResource(Integer page, Integer entries)
    {
        WebResource wr = getBaseWebResource("resources");
        return wr.queryParam("page", page.toString())
                 .queryParam("entries", entries.toString())
                 .header("Content-Type", MediaType.TEXT_XML)
                 .accept(MediaType.TEXT_XML)
                 .get(ShortResourceList.class);
    }
    
    public SecurityRuleList getSecurityRules(Long resourceId)
    {
    	WebResource wr = getBaseWebResource("resources", "resource", resourceId, "permissions");
    	return wr.header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(SecurityRuleList.class);
    }
    
    public void updateSecurityRules(Long resourceId, SecurityRuleList rules)
    {
    	 getBaseWebResource("resources", "resource", resourceId, "permissions")
    	 	.header("Content-Type", MediaType.TEXT_XML)
                 .accept(MediaType.TEXT_PLAIN)
    	 	.post(rules);
    }

    // ==========================================================================
    // === DATA
    // ==========================================================================

    public String getData(Long id)
    {
        return getData(id, MediaType.WILDCARD_TYPE);
    }

    public String getData(Long id, MediaType acceptMediaType)
    {
        return getBaseWebResource("data", id)
                .accept(acceptMediaType)
                .get(String.class);
    }

    public byte[] getRawData(Long id, RawFormat decodeFrom)
    {
        return getRawData(byte[].class, id, decodeFrom);
    }

    public <T> T getRawData(Class<T> clazz, Long id, RawFormat decodeFrom)
    {
        WebResource wr = getBaseWebResource("data", id, "raw");
        if(decodeFrom != null) {
            wr = wr.queryParam("decode", decodeFrom.name());
        }

        return wr.get(clazz);
    }

    public void setData(Long id, String data)
    {
        getBaseWebResource("data", id).put(data);
    }

    public void updateData(Long id, String data)
    {
        getBaseWebResource("data", id)
                .header("Content-Type", MediaType.TEXT_PLAIN)
                .put(data);
    }

    // ==========================================================================
    // === CATEGORIES
    // ==========================================================================

    public Long insert(RESTCategory category)
    {
        String sid = getBaseWebResource("categories")
                .header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_PLAIN)
                .post(String.class, category);

        return Long.parseLong(sid);
    }

    public Category getCategory(Long id)
    {
        Category category = getBaseWebResource("categories", "category", id)
                .header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(Category.class);

        return category;
    }

    public Long getCategoryCount(String nameLike)
    {
        String count = getBaseWebResource("categories", "count", nameLike)
                .accept(MediaType.TEXT_PLAIN)
                .get(String.class);

        return Long.parseLong(count);
    }

    public CategoryList getCategories(Integer page, Integer entries)
    {
        return getBaseWebResource("categories")
                .queryParam("page", page.toString())
                .queryParam("entries", entries.toString())
                .header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(CategoryList.class);
    }

    public CategoryList getCategories()
    {
        return getBaseWebResource("categories")
                .header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(CategoryList.class);
    }

    public void deleteCategory(Long id)
    {
        getBaseWebResource("categories", "category", id).delete();
    }

    // ==========================================================================
    // ExtJS
    // ==========================================================================
    // These methods should not belong here, since they are needed for JS only.
    // Anyway we can use these methods to test the logic in integrations tests.
    // ==========================================================================

    public ExtGroupList searchUserGroup(Integer start,Integer limit, String nameLike, boolean all)
    {
        WebResource wr = getBaseWebResource("extjs", "search", "groups", nameLike);
    
        wr = wr.queryParam("start", start.toString())
               .queryParam("limit", limit.toString())
               .queryParam("all", Boolean.toString(all));

        return wr.header("Content-Type", MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(ExtGroupList.class);

    }

    public ExtGroupList searchUserGroup(Integer start,Integer limit, String nameLike)
    {
        return searchUserGroup(start, limit, nameLike, false);
    }

    public ShortResource getShortResource(long id)
    {
        WebResource wr = getBaseWebResource("extjs", "resource", id);
        return wr.get(ShortResource.class);
    }

    // ==========================================================================
    // ==========================================================================

    protected WebResource getBaseWebResource()
    {
        if (geostoreRestUrl == null)
            throw new IllegalStateException("GeoStore URL not set");

        Client c = Client.create();
        if (username != null || password != null) {
            c.addFilter(new HTTPBasicAuthFilter(username != null ? username : "",
                    password != null ? password : ""));
        }

        WebResource wr = c.resource(geostoreRestUrl);
        return wr;
    }

    protected WebResource getBaseWebResource(Object... path)
    {
        if (geostoreRestUrl == null)
            throw new IllegalStateException("GeoStore URL not set");

        Client c = Client.create();
        if (username != null || password != null) {
            c.addFilter(new HTTPBasicAuthFilter(username != null ? username : "",
                    password != null ? password : ""));
        }

        StringBuilder fullpath = new StringBuilder(geostoreRestUrl);
        for (Object o : path) {
            String p = o.toString();
            if (fullpath.charAt(fullpath.length() - 1) != '/')
                fullpath.append('/');
            fullpath.append(p);
        }
        WebResource wr = c.resource(fullpath.toString());
        return wr;
    }

    // ==========================================================================

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
