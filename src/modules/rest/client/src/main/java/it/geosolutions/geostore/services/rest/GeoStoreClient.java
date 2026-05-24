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
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.rest.client.model.ExtGroupList;
import it.geosolutions.geostore.services.rest.model.CategoryList;
import it.geosolutions.geostore.services.rest.model.RESTCategory;
import it.geosolutions.geostore.services.rest.model.RESTResource;
import it.geosolutions.geostore.services.rest.model.ResourceList;
import it.geosolutions.geostore.services.rest.model.SecurityRuleList;
import it.geosolutions.geostore.services.rest.model.ShortResourceList;
import it.geosolutions.geostore.services.rest.model.enums.RawFormat;
import jakarta.ws.rs.core.MediaType;
import org.apache.cxf.jaxrs.client.WebClient;

/**
 * GeoStore REST client.
 *
 * <p>The Jersey 1.x classic API used by the pre-spring7 implementation is no longer compatible with
 * Jakarta EE 10 / Spring 7. The internal HTTP plumbing was rewritten to use Apache CXF's JAX-RS 2
 * {@link WebClient} — public method signatures are byte-identical so existing callers compile and
 * run without changes.
 *
 * @author ETj (etj at geo-solutions.it)
 */
@SuppressWarnings(
        "PMD.CloseResource") // CXF WebClient is single-use here; explicit close is not idiomatic
public class GeoStoreClient {

    private String username = null;
    private String password = null;
    private String geostoreRestUrl = null;

    public GeoStoreClient() {}

    // ==========================================================================
    // === RESOURCES
    // ==========================================================================

    /**
     * @deprecated the REST service call is deprecated and should be replaced by {@link
     *     #searchResources(it.geosolutions.geostore.services.dto.search.SearchFilter,
     *     java.lang.Integer, java.lang.Integer, java.lang.Boolean, java.lang.Boolean) }
     */
    @Deprecated
    public ShortResourceList searchResources(SearchFilter searchFilter) {
        return getBaseWebClient("resources", "search")
                .type(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .post(searchFilter, ShortResourceList.class);
    }

    public ResourceList searchResources(
            SearchFilter searchFilter,
            Integer page,
            Integer entries,
            Boolean includeAttributes,
            Boolean includeData) {
        WebClient wb = getBaseWebClient("resources", "search", "list");
        wb = addQParam(wb, "page", page);
        wb = addQParam(wb, "entries", entries);
        wb = addQParam(wb, "includeAttributes", includeAttributes);
        wb = addQParam(wb, "includeData", includeData);
        return wb.type(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .post(searchFilter, ResourceList.class);
    }

    protected WebClient addQParam(WebClient wb, String key, Object value) {
        if (value != null) return wb.query(key, value.toString());
        return wb;
    }

    public Long insert(RESTResource resource) {
        String sid =
                getBaseWebClient("resources")
                        .type(MediaType.TEXT_XML)
                        .accept(MediaType.TEXT_PLAIN)
                        .post(resource, String.class);
        return Long.parseLong(sid);
    }

    public Resource getResource(Long id, boolean full) {
        WebClient wb = getBaseWebClient("resources", "resource", id);
        if (full) wb = wb.query("full", Boolean.toString(full));
        return wb.type(MediaType.TEXT_XML).accept(MediaType.TEXT_XML).get(Resource.class);
    }

    public Resource getResource(Long id) {
        return getResource(id, false);
    }

    public void deleteResource(Long id) {
        getBaseWebClient("resources", "resource", id).delete();
    }

    public void updateResource(Long id, RESTResource resource) {
        getBaseWebClient("resources", "resource", id).type(MediaType.TEXT_XML).put(resource);
    }

    public ShortResourceList getAllShortResource(Integer page, Integer entries) {
        return getBaseWebClient("resources")
                .query("page", page.toString())
                .query("entries", entries.toString())
                .type(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(ShortResourceList.class);
    }

    public SecurityRuleList getSecurityRules(Long resourceId) {
        return getBaseWebClient("resources", "resource", resourceId, "permissions")
                .type(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(SecurityRuleList.class);
    }

    public void updateSecurityRules(Long resourceId, SecurityRuleList rules) {
        getBaseWebClient("resources", "resource", resourceId, "permissions")
                .type(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_PLAIN)
                .post(rules);
    }

    // ==========================================================================
    // === DATA
    // ==========================================================================

    public String getData(Long id) {
        return getData(id, MediaType.WILDCARD_TYPE);
    }

    public String getData(Long id, MediaType acceptMediaType) {
        return getBaseWebClient("data", id).accept(acceptMediaType).get(String.class);
    }

    public byte[] getRawData(Long id, RawFormat decodeFrom) {
        return getRawData(byte[].class, id, decodeFrom);
    }

    public <T> T getRawData(Class<T> clazz, Long id, RawFormat decodeFrom) {
        WebClient wb = getBaseWebClient("data", id, "raw");
        if (decodeFrom != null) {
            wb = wb.query("decode", decodeFrom.name());
        }
        return wb.get(clazz);
    }

    public void setData(Long id, String data) {
        getBaseWebClient("data", id).put(data);
    }

    public void updateData(Long id, String data) {
        getBaseWebClient("data", id).type(MediaType.TEXT_PLAIN).put(data);
    }

    // ==========================================================================
    // === CATEGORIES
    // ==========================================================================

    public Long insert(RESTCategory category) {
        String sid =
                getBaseWebClient("categories")
                        .type(MediaType.TEXT_XML)
                        .accept(MediaType.TEXT_PLAIN)
                        .post(category, String.class);
        return Long.parseLong(sid);
    }

    public Category getCategory(Long id) {
        return getBaseWebClient("categories", "category", id)
                .type(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(Category.class);
    }

    public Long getCategoryCount(String nameLike) {
        String count =
                getBaseWebClient("categories", "count", nameLike)
                        .accept(MediaType.TEXT_PLAIN)
                        .get(String.class);
        return Long.parseLong(count);
    }

    public CategoryList getCategories(Integer page, Integer entries) {
        return getBaseWebClient("categories")
                .query("page", page.toString())
                .query("entries", entries.toString())
                .type(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(CategoryList.class);
    }

    public CategoryList getCategories() {
        return getBaseWebClient("categories")
                .type(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(CategoryList.class);
    }

    public void deleteCategory(Long id) {
        getBaseWebClient("categories", "category", id).delete();
    }

    // ==========================================================================
    // === ExtJS
    // ==========================================================================

    public ExtGroupList searchUserGroup(
            Integer start, Integer limit, String nameLike, boolean all) {
        return getBaseWebClient("extjs", "search", "groups", nameLike)
                .query("start", start.toString())
                .query("limit", limit.toString())
                .query("all", Boolean.toString(all))
                .type(MediaType.TEXT_XML)
                .accept(MediaType.TEXT_XML)
                .get(ExtGroupList.class);
    }

    public ExtGroupList searchUserGroup(Integer start, Integer limit, String nameLike) {
        return searchUserGroup(start, limit, nameLike, false);
    }

    public ShortResource getShortResource(long id) {
        return getBaseWebClient("extjs", "resource", id).get(ShortResource.class);
    }

    // ==========================================================================
    // === Internal helpers
    // ==========================================================================

    protected WebClient getBaseWebClient() {
        if (geostoreRestUrl == null) throw new IllegalStateException("GeoStore URL not set");
        if (username != null || password != null) {
            return WebClient.create(
                    geostoreRestUrl,
                    username != null ? username : "",
                    password != null ? password : "",
                    null);
        }
        return WebClient.create(geostoreRestUrl);
    }

    /**
     * Returns a {@link WebClient} positioned at {@code baseUrl} + path segments.
     *
     * <p>API-compatible with the pre-spring7 Jersey 1.x {@code getBaseWebResource(Object...)}; the
     * variadic path is appended exactly the same way.
     */
    protected WebClient getBaseWebClient(Object... path) {
        WebClient wb = getBaseWebClient();
        for (Object segment : path) {
            wb = wb.path(segment.toString());
        }
        return wb;
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
