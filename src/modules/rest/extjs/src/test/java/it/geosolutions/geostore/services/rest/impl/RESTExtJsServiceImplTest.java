/*
 * Copyright (C) 2016 GeoSolutions
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package it.geosolutions.geostore.services.rest.impl;

import com.googlecode.genericdao.search.Search;
import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.enums.Role;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.SecurityContext;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author ETj (etj at geo-solutions.it)
 */
public class RESTExtJsServiceImplTest extends ServiceTestBase
{

    private RESTExtJsServiceImpl restExtJsService;

    public RESTExtJsServiceImplTest()
    {
        restExtJsService = ctx.getBean("restExtJsService", RESTExtJsServiceImpl.class);
        assertNotNull(restExtJsService);
    }

    @Before
    public void setUp() throws Exception
    {
        assertNotNull(restExtJsService);
        removeAll();
    }

    @Test
    public void testGetAllResources_auth_base() throws Exception
    {
        final String CAT_NAME = "CAT000";

        assertEquals(0, resourceService.getAll(null, null, buildFakeAdminUser()).size());

        long u0 = restCreateUser("u0", Role.USER, "p0");
        long u1 = restCreateUser("u1", Role.USER, "p1");

        Category cat = createCategory(CAT_NAME);

        restCreateResource("r_u0_0", "x", CAT_NAME, u0);

        restCreateResource("r_u1_0", "x", CAT_NAME, u1);
        restCreateResource("r_u1_1", "x", CAT_NAME, u1);

        {
            SecurityContext sc = new SimpleSecurityContext(u0);
            String response = restExtJsService.getAllResources(sc, "*", 0, 1000);

            System.out.println("JSON " + response);

            JSONResult result = parse(response);
            assertEquals(1, result.total);
            assertEquals(1, result.returnedCount);

            assertTrue(result.names.contains("r_u0_0"));
        }

        {
            SecurityContext sc = new SimpleSecurityContext(u1);
            String response = restExtJsService.getAllResources(sc, "*", 0, 1000);

            System.out.println("JSON " + response);

            JSONResult result = parse(response);
            assertEquals(2, result.total);
            assertEquals(2, result.returnedCount);

            assertTrue(result.names.contains("r_u1_0"));
            assertTrue(result.names.contains("r_u1_1"));

            assertFalse(result.names.contains("r_u0_0"));
        }
    }

    @Test
    public void testGetAllResources_auth_many() throws Exception
    {
        final String CAT_NAME = "CAT009";

        assertEquals(0, resourceService.getAll(null, null, buildFakeAdminUser()).size());

        long u0 = restCreateUser("u0", Role.USER, "p0");
        long u1 = restCreateUser("u1", Role.USER, "p1");

        Category cat = createCategory(CAT_NAME);

        int RESNUM0 = 20;
        int RESNUM1 = RESNUM0 * 2;

        for (int i = 1000; i < 1000 + RESNUM0; i++) {

            restCreateResource("r_u0_"+i, "x", CAT_NAME, u0);

            restCreateResource("r_u1_"+i+"a", "x", CAT_NAME, u1);
            restCreateResource("r_u1_"+i+"b", "x", CAT_NAME, u1);
        }

        int cnt = resourceDAO.count(new Search(Resource.class));
        assertEquals(RESNUM0 + RESNUM1, cnt);

        {
            SecurityContext sc = new SimpleSecurityContext(u0);
            String response = restExtJsService.getAllResources(sc, "*", 0, 10);

            System.out.println("JSON for u0 " + response);

            JSONResult result = parse(response);
            assertEquals(RESNUM0, result.total);
            assertEquals(10, result.returnedCount);
        }

        {
            SecurityContext sc = new SimpleSecurityContext(u1);
            String response = restExtJsService.getAllResources(sc, "*", 0, 10);

            System.out.println("JSON for u1 " + response);

            JSONResult result = parse(response);
            assertEquals(RESNUM1, result.total);
            assertEquals(10, result.returnedCount);
        }
        {
            SecurityContext sc = new SimpleSecurityContext(u0);
            String response = restExtJsService.getResourcesByCategory(sc, CAT_NAME, "*", 0, 10, false, false);

            System.out.println("JSON for u0 " + response);

            JSONResult result = parse(response);
            assertEquals(RESNUM0, result.total);
            assertEquals(10, result.returnedCount);
        }

        {
            SecurityContext sc = new SimpleSecurityContext(u1);
            String response = restExtJsService.getResourcesByCategory(sc, CAT_NAME, "*", 0, 10, false, false);

            System.out.println("JSON for u1 " + response);

            JSONResult result = parse(response);
            assertEquals(RESNUM1, result.total);
            assertEquals(10, result.returnedCount);
        }
    }


    @Test
    public void testGetAllResources_ilike() throws Exception
    {
        final String CAT0_NAME = "CAT000";
        final String CAT1_NAME = "CAT111";
        final String RES_NAME = "a MiXeD cAsE sTrInG";

        assertEquals(0, resourceService.getAll(null, null, buildFakeAdminUser()).size());

        long u0 = restCreateUser("u0", Role.USER, "p0");

        createCategory(CAT0_NAME);
        createCategory(CAT1_NAME);

        restCreateResource(RES_NAME, "x", CAT0_NAME, u0);
        restCreateResource(RES_NAME.toLowerCase(), "x", CAT0_NAME, u0);
        restCreateResource(RES_NAME.toUpperCase(), "x", CAT0_NAME, u0);

        restCreateResource(RES_NAME + " in another category", "x", CAT1_NAME, u0);

        restCreateResource("just an extra resource we shouldnt care about", "x", CAT0_NAME, u0);


        {
            SecurityContext sc = new SimpleSecurityContext(u0);
            String response = restExtJsService.getAllResources(sc, "*mIxEd*", 0, 1000);

            System.out.println("JSON " + response);

            JSONResult result = parse(response);
            assertEquals(4, result.total);
            assertEquals(4, result.returnedCount);
        }

        {
            SecurityContext sc = new SimpleSecurityContext(u0);
            String response = restExtJsService.getResourcesByCategory(sc, CAT0_NAME, "*mIxEd*", null, 0, 1000, false, false);;

            System.out.println("JSON " + response);

            JSONResult result = parse(response);
            assertEquals(3, result.total);
            assertEquals(3, result.returnedCount);
        }
    }


    private JSONResult parse(String jsonString)
    {
        JSONResult ret = new JSONResult();

        JSON json = JSONSerializer.toJSON(jsonString);
        JSONObject jo = (JSONObject)json;
        ret.total = jo.getInt("totalCount");

        Set<String> names;

        JSONArray arrResults = jo.optJSONArray("results");
        if(arrResults != null) {
            names = getArray(arrResults);
        } else {
            JSONObject results = jo.optJSONObject("results");

            if(results != null) {
                names = Collections.singleton(getSingle(results));
            }
            else {
                LOGGER.warn("No results found");
                names = Collections.EMPTY_SET;
            }
        }

        ret.names = names;
        ret.returnedCount = names.size();

        return ret;
    }

    Set<String> getArray(JSONArray arr) {
        Set<String> ret = new HashSet<>();

        for (Object object : arr) {
            ret.add(getSingle((JSONObject)object));
        }

        return ret;
    }

    String getSingle(JSONObject jo) {
        return jo.getString("name");
    }

    static class JSONResult {
        int total;
        int returnedCount;
        Set<String> names;
    }

    
}
