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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.googlecode.genericdao.search.Search;
import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.DataType;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.dto.ResourceSearchParameters;
import it.geosolutions.geostore.services.dto.ShortAttribute;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.AndFilter;
import it.geosolutions.geostore.services.dto.search.BaseField;
import it.geosolutions.geostore.services.dto.search.FieldFilter;
import it.geosolutions.geostore.services.dto.search.GroupFilter;
import it.geosolutions.geostore.services.dto.search.SearchOperator;
import it.geosolutions.geostore.services.model.ExtGroupList;
import it.geosolutions.geostore.services.model.ExtResource;
import it.geosolutions.geostore.services.model.ExtResourceList;
import it.geosolutions.geostore.services.model.ExtShortResource;
import it.geosolutions.geostore.services.rest.exception.ForbiddenErrorWebEx;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;
import it.geosolutions.geostore.services.rest.model.RESTSecurityRule;
import it.geosolutions.geostore.services.rest.model.SecurityRuleList;
import it.geosolutions.geostore.services.rest.model.Sort;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.core.SecurityContext;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.junit.Before;
import org.junit.Test;

/** @author ETj (etj at geo-solutions.it) */
public class RESTExtJsServiceImplTest extends ServiceTestBase {

    private final RESTExtJsServiceImpl restExtJsService;

    public RESTExtJsServiceImplTest() {
        restExtJsService = ctx.getBean("restExtJsService", RESTExtJsServiceImpl.class);
        assertNotNull(restExtJsService);
    }

    @Before
    public void setUp() throws Exception {
        assertNotNull(restExtJsService);
        removeAll();
    }

    @Test
    public void testGetAllResources_auth_base() throws Exception {
        final String CAT_NAME = "CAT000";

        assertEquals(
                0,
                resourceService
                        .getAll(
                                ResourceSearchParameters.builder()
                                        .authUser(buildFakeAdminUser())
                                        .build())
                        .size());

        long u0 = restCreateUser("u0", Role.USER, null, "p0");
        long u1 = restCreateUser("u1", Role.USER, null, "p1");

        Category cat = createCategory(CAT_NAME);

        restCreateResource("r_u0_0", "x", CAT_NAME, u0, true);

        restCreateResource("r_u1_0", "x", CAT_NAME, u1, true);
        restCreateResource("r_u1_1", "x", CAT_NAME, u1, true);

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
    public void testGetAllResources_auth_many() throws Exception {
        final String CAT_NAME = "CAT009";

        assertEquals(
                0,
                resourceService
                        .getAll(
                                ResourceSearchParameters.builder()
                                        .authUser(buildFakeAdminUser())
                                        .build())
                        .size());

        long u0 = restCreateUser("u0", Role.USER, null, "p0");
        long u1 = restCreateUser("u1", Role.USER, null, "p1");

        Category cat = createCategory(CAT_NAME);

        int RESNUM0 = 20;
        int RESNUM1 = RESNUM0 * 2;

        for (int i = 1000; i < 1000 + RESNUM0; i++) {
            restCreateResource("r_u0_" + i, "x", CAT_NAME, u0, true);

            restCreateResource("r_u1_" + i + "a", "x", CAT_NAME, u1, true);
            restCreateResource("r_u1_" + i + "b", "x", CAT_NAME, u1, true);
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
            String response =
                    restExtJsService.getResourcesByCategory(sc, CAT_NAME, "*", 0, 10, false, false);

            System.out.println("JSON for u0 " + response);

            JSONResult result = parse(response);
            assertEquals(RESNUM0, result.total);
            assertEquals(10, result.returnedCount);
        }

        {
            SecurityContext sc = new SimpleSecurityContext(u1);
            String response =
                    restExtJsService.getResourcesByCategory(sc, CAT_NAME, "*", 0, 10, false, false);

            System.out.println("JSON for u1 " + response);

            JSONResult result = parse(response);
            assertEquals(RESNUM1, result.total);
            assertEquals(10, result.returnedCount);
        }
    }

    @Test
    public void testGetAllResources_iLike() throws Exception {
        final String CAT0_NAME = "CAT000";
        final String CAT1_NAME = "CAT111";
        final String RES_NAME = "a MiXeD cAsE sTrInG";

        assertEquals(
                0,
                resourceService
                        .getAll(
                                ResourceSearchParameters.builder()
                                        .authUser(buildFakeAdminUser())
                                        .build())
                        .size());

        long u0 = restCreateUser("u0", Role.USER, null, "p0");

        createCategory(CAT0_NAME);
        createCategory(CAT1_NAME);

        restCreateResource(RES_NAME, "x", CAT0_NAME, u0, true);
        restCreateResource(RES_NAME.toLowerCase(), "x", CAT0_NAME, u0, true);
        restCreateResource(RES_NAME.toUpperCase(), "x", CAT0_NAME, u0, true);

        restCreateResource(RES_NAME + " in another category", "x", CAT1_NAME, u0, true);

        restCreateResource(
                "just an extra resource we shouldn't care about", "x", CAT0_NAME, u0, true);

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
            String response =
                    restExtJsService.getResourcesByCategory(
                            sc, CAT0_NAME, "*mIxEd*", null, 0, 1000, false, false);

            System.out.println("JSON " + response);

            JSONResult result = parse(response);
            assertEquals(3, result.total);
            assertEquals(3, result.returnedCount);
        }
    }

    @Test
    public void testGetAllResources_editorUpdate() throws Exception {
        final String CAT0_NAME = "CAT000";
        final String RES_NAME = "a MiXeD cAsE sTrInG";

        assertEquals(
                0,
                resourceService
                        .getAll(
                                ResourceSearchParameters.builder()
                                        .authUser(buildFakeAdminUser())
                                        .build())
                        .size());

        long a0 = restCreateUser("a0", Role.ADMIN, new HashSet<>(), "p0");
        long u0 = restCreateUser("u0", Role.USER, new HashSet<>(), "p0");

        createCategory(CAT0_NAME);
        long r0Id = restCreateResource(RES_NAME, "x", CAT0_NAME, u0, true);

        {
            SecurityContext sc = new SimpleSecurityContext(u0);
            String response = restExtJsService.getAllResources(sc, "*mIxEd*", 0, 1000);

            System.out.println("JSON " + response);

            JSONResult result = parse(response);
            assertEquals(1, result.total);
            assertEquals(1, result.returnedCount);

            ShortResource resource = restExtJsService.getExtResource(sc, r0Id, false, false);
            assertEquals(RES_NAME, resource.getName());
            assertEquals("u0", resource.getCreator());
            assertEquals("u0", resource.getEditor());
        }

        {
            SecurityContext sc = new SimpleSecurityContext(a0);
            Resource realResource = resourceService.get(r0Id);
            realResource.setName("new name");
            restResourceService.update(sc, r0Id, createRESTResource(realResource));

            ShortResource resource = restExtJsService.getExtResource(sc, r0Id, false, false);
            assertEquals(realResource.getName(), resource.getName());
            assertEquals("u0", resource.getCreator());
            assertEquals("a0", resource.getEditor());
        }
    }

    @Test
    public void testGetAllResources_unadvertised() throws Exception {
        final String CAT0_NAME = "CAT000";
        final String CAT1_NAME = "CAT111";
        final String RES_NAME = "a MiXeD cAsE sTrInG";

        assertEquals(
                0,
                resourceService
                        .getAll(
                                ResourceSearchParameters.builder()
                                        .authUser(buildFakeAdminUser())
                                        .build())
                        .size());

        long g0Id = createGroup("g0");
        UserGroup g0 = new UserGroup();
        g0.setId(g0Id);
        g0.setGroupName("g0");
        Set<UserGroup> groups = new HashSet<>();
        groups.add(g0);

        long a0 = restCreateUser("a0", Role.ADMIN, groups, "p0");
        long u0 = restCreateUser("u0", Role.USER, groups, "p0");
        long u1 = restCreateUser("u1", Role.USER, groups, "p1");

        createCategory(CAT0_NAME);
        createCategory(CAT1_NAME);

        SecurityRule sr0 = new SecurityRule();
        sr0.setUser(userDAO.find(u0));
        sr0.setCanRead(true);
        sr0.setCanWrite(true);

        SecurityRule sr1 = new SecurityRule();
        sr1.setGroup(g0);
        sr1.setCanRead(true);
        sr1.setCanWrite(false);

        SecurityRuleList rules = new SecurityRuleList(Arrays.asList(sr0, sr1));

        restCreateResource(RES_NAME, "x", CAT0_NAME, u0, rules, true);
        restCreateResource(RES_NAME.toLowerCase(), "x", CAT0_NAME, u0, rules, false);
        restCreateResource(RES_NAME.toUpperCase(), "x", CAT0_NAME, u0, rules, true);

        restCreateResource(RES_NAME + " in another category", "x", CAT1_NAME, u0, rules, false);

        restCreateResource(
                "just an extra resource we shouldn't care about", "x", CAT0_NAME, u0, rules, true);

        {
            // ADMIN
            SecurityContext sc = new SimpleSecurityContext(a0);
            String response = restExtJsService.getAllResources(sc, "*mIxEd*", 0, 1000);

            System.out.println("JSON " + response);

            JSONResult result = parse(response);
            assertEquals(4, result.total);
            assertEquals(4, result.returnedCount);

            // OWNER
            sc = new SimpleSecurityContext(u0);
            response = restExtJsService.getAllResources(sc, "*mIxEd*", 0, 1000);

            System.out.println("JSON " + response);

            result = parse(response);
            assertEquals(4, result.total);
            assertEquals(4, result.returnedCount);

            // READER
            sc = new SimpleSecurityContext(u1);
            response = restExtJsService.getAllResources(sc, "*mIxEd*", 0, 1000);

            System.out.println("JSON " + response);

            result = parse(response);
            assertEquals(2, result.total);
            assertEquals(2, result.returnedCount);
        }

        {
            // OWNER
            SecurityContext sc = new SimpleSecurityContext(u0);
            String response =
                    restExtJsService.getResourcesByCategory(
                            sc, CAT0_NAME, "*mIxEd*", null, 0, 1000, false, false);

            System.out.println("JSON " + response);

            JSONResult result = parse(response);
            assertEquals(3, result.total);
            assertEquals(3, result.returnedCount);

            // READER
            sc = new SimpleSecurityContext(u1);
            response =
                    restExtJsService.getResourcesByCategory(
                            sc, CAT0_NAME, "*mIxEd*", null, 0, 1000, false, false);

            System.out.println("JSON " + response);

            result = parse(response);
            assertEquals(2, result.total);
            assertEquals(2, result.returnedCount);
        }
    }

    @Test
    public void testExtResourcesList_sorted() throws Exception {
        final String CAT0_NAME = "CAT000";
        final String RES_ATTRIBUTE_A = "A";
        final String RES_ATTRIBUTE_B = "B";
        final String RES_ATTRIBUTE_C = "C";

        assertEquals(
                0,
                resourceService
                        .getShortResources(
                                ResourceSearchParameters.builder()
                                        .authUser(buildFakeAdminUser())
                                        .build())
                        .size());

        long u0 = restCreateUser("u0", Role.USER, null, "p0");
        SecurityContext sc = new SimpleSecurityContext(u0);

        createCategory(CAT0_NAME);

        restCreateResource(RES_ATTRIBUTE_A, RES_ATTRIBUTE_A, CAT0_NAME, u0, true);
        restCreateResource(RES_ATTRIBUTE_B, RES_ATTRIBUTE_B, CAT0_NAME, u0, true);
        restCreateResource(RES_ATTRIBUTE_C, RES_ATTRIBUTE_C, CAT0_NAME, u0, true);

        {
            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc,
                            0,
                            100,
                            new Sort("description", "asc"),
                            false,
                            false,
                            new AndFilter());

            List<ExtResource> resources = response.getList();
            assertEquals(3, resources.size());
            List<String> resourcesDescriptions =
                    resources.stream().map(Resource::getDescription).collect(Collectors.toList());
            assertEquals(
                    List.of(RES_ATTRIBUTE_A, RES_ATTRIBUTE_B, RES_ATTRIBUTE_C),
                    resourcesDescriptions);
        }

        {
            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc,
                            0,
                            100,
                            new Sort("creation", "desc"),
                            false,
                            false,
                            new AndFilter());

            List<ExtResource> resources = response.getList();
            assertEquals(3, resources.size());
            List<Date> resourcesCreationDates =
                    resources.stream().map(Resource::getCreation).collect(Collectors.toList());
            assertTrue(resourcesCreationDates.get(0).after(resourcesCreationDates.get(1)));
            assertTrue(resourcesCreationDates.get(1).after(resourcesCreationDates.get(2)));
        }

        {
            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 1000, new Sort(null, null), false, false, new AndFilter());

            List<ExtResource> resources = response.getList();
            assertEquals(3, resources.size());
            List<String> resourcesNames =
                    resources.stream().map(Resource::getName).collect(Collectors.toList());
            assertEquals(
                    List.of(RES_ATTRIBUTE_A, RES_ATTRIBUTE_B, RES_ATTRIBUTE_C), resourcesNames);
        }

        {
            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc,
                            0,
                            1000,
                            new Sort("unknown field", "desc"),
                            false,
                            false,
                            new AndFilter());

            assertNull(response);
        }
    }

    @Test
    public void testExtResourcesList_creatorFiltered() throws Exception {
        final String CAT0_NAME = "CAT000";
        final String CREATOR_A = "creatorA";
        final String CREATOR_B = "creatorB";

        long u0 = restCreateUser("u0", Role.USER, null, "p0");
        SecurityContext sc = new SimpleSecurityContext(u0);

        createCategory(CAT0_NAME);

        long resourceAId = restCreateResource("name_A", "description_A", CAT0_NAME, u0, true);
        long resourceBId = restCreateResource("name_B", "description_B", CAT0_NAME, u0, true);

        Resource resourceA = resourceService.get(resourceAId);
        resourceA.setCreator(CREATOR_A);
        resourceService.update(resourceA);

        Resource resourceB = resourceService.get(resourceBId);
        resourceB.setCreator(CREATOR_B);
        resourceService.update(resourceB);

        {
            FieldFilter editorFieldFilter =
                    new FieldFilter(BaseField.CREATOR, "creatorB", SearchOperator.EQUAL_TO);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, editorFieldFilter);

            List<ExtResource> resources = response.getList();
            assertEquals(1, resources.size());
            ExtResource resource = resources.get(0);
            assertEquals(CREATOR_B, resource.getCreator());
        }

        {
            FieldFilter editorFieldFilter =
                    new FieldFilter(BaseField.CREATOR, "CREATOR_", SearchOperator.ILIKE);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, editorFieldFilter);

            List<ExtResource> resources = response.getList();
            assertEquals(2, resources.size());
        }

        {
            FieldFilter editorFieldFilter =
                    new FieldFilter(BaseField.CREATOR, "unknown creator", SearchOperator.EQUAL_TO);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, editorFieldFilter);

            assertTrue(response.isEmpty());
        }
    }

    @Test
    public void testExtResourcesList_editorFiltered() throws Exception {
        final String CAT0_NAME = "CAT000";
        final String EDITOR_A = "editorA";
        final String EDITOR_B = "editorB";

        long u0 = restCreateUser("u0", Role.USER, null, "p0");
        SecurityContext sc = new SimpleSecurityContext(u0);

        createCategory(CAT0_NAME);

        long resourceAId = restCreateResource("name_A", "description_A", CAT0_NAME, u0, true);
        long resourceBId = restCreateResource("name_B", "description_B", CAT0_NAME, u0, true);

        Resource resourceA = resourceService.get(resourceAId);
        resourceA.setEditor(EDITOR_A);
        resourceService.update(resourceA);

        Resource resourceB = resourceService.get(resourceBId);
        resourceB.setEditor(EDITOR_B);
        resourceService.update(resourceB);

        {
            FieldFilter editorFieldFilter =
                    new FieldFilter(BaseField.EDITOR, "editorA", SearchOperator.EQUAL_TO);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, editorFieldFilter);

            List<ExtResource> resources = response.getList();
            assertEquals(1, resources.size());
            Resource resource = resources.get(0);
            assertEquals(EDITOR_A, resource.getEditor());
        }

        {
            FieldFilter editorFieldFilter =
                    new FieldFilter(BaseField.EDITOR, "EDITOR_", SearchOperator.ILIKE);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, editorFieldFilter);

            List<ExtResource> resources = response.getList();
            assertEquals(2, resources.size());
        }

        {
            FieldFilter editorFieldFilter =
                    new FieldFilter(BaseField.CREATOR, "unknown editor", SearchOperator.EQUAL_TO);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, editorFieldFilter);

            assertTrue(response.isEmpty());
        }
    }

    @Test
    public void testExtResourcesList_groupFiltered() throws Exception {
        final String CAT0_NAME = "CAT000";
        final String RESOURCE_A_NAME = "resourceA";
        final String RESOURCE_B_NAME = "resourceB";
        final String GROUP_A_NAME = "groupA";
        final String GROUP_B_NAME = "groupB";

        long user0Id = restCreateUser("u0", Role.USER, null, "p0");
        SecurityContext sc = new SimpleSecurityContext(user0Id);

        createCategory(CAT0_NAME);

        long resourceAId =
                restCreateResource(RESOURCE_A_NAME, "description_A", CAT0_NAME, user0Id, true);
        long resourceBId =
                restCreateResource(RESOURCE_B_NAME, "description_B", CAT0_NAME, user0Id, true);

        SecurityRule securityRuleGroupA = new SecurityRule();
        securityRuleGroupA.setGroup(userGroupService.get(createGroup(GROUP_A_NAME)));
        securityRuleGroupA.setCanWrite(true);

        List<SecurityRule> securityRulesResourceA = resourceService.getSecurityRules(resourceAId);
        securityRulesResourceA.add(securityRuleGroupA);
        restResourceService.updateSecurityRules(
                sc, resourceAId, new SecurityRuleList(securityRulesResourceA));

        SecurityRule securityRuleGroupB = new SecurityRule();
        securityRuleGroupB.setGroup(userGroupService.get(createGroup(GROUP_B_NAME)));
        securityRuleGroupB.setCanRead(true);

        List<SecurityRule> securityRulesResourceB = resourceService.getSecurityRules(resourceBId);
        securityRulesResourceB.add(securityRuleGroupB);
        restResourceService.updateSecurityRules(
                sc, resourceBId, new SecurityRuleList(securityRulesResourceB));

        {
            /* search for name equality of a single group */
            GroupFilter groupFilter =
                    new GroupFilter(Collections.singletonList("groupA"), SearchOperator.EQUAL_TO);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, groupFilter);

            List<ExtResource> resources = response.getList();
            assertEquals(1, resources.size());
            Resource resource = resources.get(0);
            assertEquals(RESOURCE_A_NAME, resource.getName());
        }

        {
            /* search for name similarity (ignoring case) of multiple groups */
            GroupFilter groupFilter =
                    new GroupFilter(Collections.singletonList("GROUP_"), SearchOperator.ILIKE);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, groupFilter);

            List<ExtResource> resources = response.getList();
            assertEquals(2, resources.size());
        }

        {
            /* search for name equality of multiple groups */
            GroupFilter groupFilter =
                    new GroupFilter(List.of("groupA", "groupB", "groupC"), SearchOperator.IN);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, groupFilter);

            List<ExtResource> resources = response.getList();
            assertEquals(2, resources.size());
        }

        {
            /* erroneous search for similarity of multiple groups */
            GroupFilter groupFilter = new GroupFilter(List.of("a", "b"), SearchOperator.LIKE);

            assertThrows(
                    IllegalStateException.class,
                    () ->
                            restExtJsService.getExtResourcesList(
                                    sc, 0, 100, new Sort("", ""), false, false, groupFilter));
        }

        {
            /* erroneous search for equality in empty group list */
            GroupFilter groupFilter =
                    new GroupFilter(Collections.emptyList(), SearchOperator.EQUAL_TO);

            assertThrows(
                    IllegalStateException.class,
                    () ->
                            restExtJsService.getExtResourcesList(
                                    sc, 0, 100, new Sort("", ""), false, false, groupFilter));
        }

        {
            /* unknown group */
            GroupFilter groupFilter =
                    new GroupFilter(
                            Collections.singletonList("unknown group"), SearchOperator.EQUAL_TO);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, groupFilter);

            assertTrue(response.getList().isEmpty());
        }
    }

    @Test
    public void testExtResourcesList_groupFilteredWithInvalidInFilter() throws Exception {
        final String CAT0_NAME = "CAT000";

        long user0Id = restCreateUser("u0", Role.USER, null, "p0");
        SecurityContext sc = new SimpleSecurityContext(user0Id);

        createCategory(CAT0_NAME);

        restCreateResource("resourceA", "description_A", CAT0_NAME, user0Id, true);

        {
            GroupFilter groupFilter = new GroupFilter(null, SearchOperator.IN);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, groupFilter);

            List<ExtResource> resources = response.getList();
            assertEquals(1, resources.size());
        }

        {
            GroupFilter groupFilter =
                    new GroupFilter(Collections.singletonList(""), SearchOperator.IN);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, groupFilter);

            assertTrue(response.getList().isEmpty());
        }

        {
            GroupFilter groupFilter =
                    new GroupFilter(Collections.singletonList(null), SearchOperator.IN);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, groupFilter);

            assertTrue(response.getList().isEmpty());
        }
    }

    @Test
    public void testExtResourcesList_timeAttributesFiltered() throws Exception {
        final String CAT0_NAME = "CAT000";

        long u0 = restCreateUser("u0", Role.USER, null, "p0");
        SecurityContext sc = new SimpleSecurityContext(u0);

        createCategory(CAT0_NAME);

        long resourceAId = restCreateResource("name_A", "", CAT0_NAME, u0, true);
        long resourceBId = restCreateResource("name_B", "", CAT0_NAME, u0, true);

        Resource resourceA = resourceService.get(resourceAId);

        Resource resourceB = resourceService.get(resourceBId);
        Thread.sleep(1000);
        resourceB.setDescription("posticipated");
        resourceService.update(resourceB);

        {
            FieldFilter ltDateFilter =
                    new FieldFilter(
                            BaseField.LASTUPDATE,
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
                                    .format(resourceB.getLastUpdate()),
                            SearchOperator.LESS_THAN);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, ltDateFilter);

            List<ExtResource> resources = response.getList();
            assertEquals(1, resources.size());
            Resource resource = resources.get(0);
            assertEquals(resourceAId, resource.getId().longValue());
        }

        {
            FieldFilter gteDateFilter =
                    new FieldFilter(
                            BaseField.CREATION,
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                    .format(resourceA.getCreation()),
                            SearchOperator.GREATER_THAN_OR_EQUAL_TO);
            FieldFilter lteDateFilter =
                    new FieldFilter(
                            BaseField.CREATION,
                            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
                                    .format(resourceB.getLastUpdate()),
                            SearchOperator.LESS_THAN_OR_EQUAL_TO);
            AndFilter betweenDatesFieldFilter = new AndFilter(gteDateFilter, lteDateFilter);

            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            sc, 0, 100, new Sort("", ""), false, false, betweenDatesFieldFilter);

            List<ExtResource> resources = response.getList();
            assertEquals(2, resources.size());
        }
    }

    @Test
    public void testExtResourcesList_userOwnedWithPermissionsInformation() throws Exception {
        final String CAT0_NAME = "CAT000";
        final String OWNED_RESOURCE_NAME = "ownedResource";
        final String READ_ONLY_RESOURCE_NAME = "readOnlyResource";

        long adminId = restCreateUser("admin", Role.ADMIN, null, "admin");
        SecurityContext adminSecurityContext = new SimpleSecurityContext(adminId);

        long userId = restCreateUser("u0", Role.USER, null, "p0");
        SecurityContext user0SecurityContext = new SimpleSecurityContext(userId);

        createCategory(CAT0_NAME);

        /* admin owned resource */
        restCreateResource("adminResource", "", CAT0_NAME, adminId, false);

        /* user owned resource */
        restCreateResource(OWNED_RESOURCE_NAME, "", CAT0_NAME, userId, false);

        /* user owned resource - read only */
        long readOnlyResourceId =
                restCreateResource(READ_ONLY_RESOURCE_NAME, "", CAT0_NAME, userId, false);
        SecurityRule readOnlyRule = new SecurityRule();
        readOnlyRule.setUser(userService.get(userId));
        readOnlyRule.setCanRead(true);
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                readOnlyResourceId,
                new SecurityRuleList(Collections.singletonList(readOnlyRule)));

        /* advertised resource */
        restCreateResource("advertisedResource", "", CAT0_NAME, adminId, true);

        /* resource without security rules */
        restCreateResource(
                "unruledResource",
                "",
                CAT0_NAME,
                adminId,
                new SecurityRuleList(Collections.emptyList()),
                false);

        {
            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            adminSecurityContext,
                            0,
                            1000,
                            new Sort("", ""),
                            false,
                            false,
                            new AndFilter());
            List<ExtResource> resources = response.getList();
            assertEquals(5, resources.size());
            assertTrue(
                    resources.stream()
                            .allMatch(r -> r.isCanEdit() && r.isCanDelete() && r.isCanCopy()));
        }

        {
            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            user0SecurityContext,
                            0,
                            1000,
                            new Sort("", ""),
                            false,
                            false,
                            new AndFilter());
            List<ExtResource> resources = response.getList();
            assertEquals(2, resources.size());

            ExtResource ownerResource =
                    resources.stream()
                            .filter(r -> r.getName().equals(OWNED_RESOURCE_NAME))
                            .findFirst()
                            .orElseThrow();
            assertTrue(ownerResource.isCanEdit());
            assertTrue(ownerResource.isCanDelete());
            assertTrue(ownerResource.isCanCopy());

            ExtResource readOnlyResource =
                    resources.stream()
                            .filter(r -> r.getName().equals(READ_ONLY_RESOURCE_NAME))
                            .findFirst()
                            .orElseThrow();
            assertFalse(readOnlyResource.isCanEdit());
            assertFalse(readOnlyResource.isCanDelete());
            assertTrue(readOnlyResource.isCanCopy());
        }
    }

    @Test
    public void testExtResourcesList_groupOwnedResourceWithPermissionsInformation()
            throws Exception {
        final String CAT0_NAME = "CAT000";
        final String GROUP_RESOURCE_NAME = "advertisedGroupResource";
        final String READ_ONLY_RESOURCE_NAME = "readOnlyResource";

        long groupId = createGroup("group");
        UserGroup group = userGroupService.get(groupId);

        long adminId = restCreateUser("admin", Role.ADMIN, null, "admin");
        SecurityContext adminSecurityContext = new SimpleSecurityContext(adminId);

        long userId = restCreateUser("u0", Role.USER, Collections.singleton(group), "p0");
        SecurityContext user0SecurityContext = new SimpleSecurityContext(userId);

        createCategory(CAT0_NAME);

        /* group owned resource - advertised */
        SecurityRule editorGroupRule = new SecurityRule();
        editorGroupRule.setGroup(group);
        editorGroupRule.setCanRead(true);
        editorGroupRule.setCanWrite(true);

        long advertisedGroupResourceId =
                restCreateResource(GROUP_RESOURCE_NAME, "", CAT0_NAME, adminId, true);
        List<SecurityRule> securityRulesAdvertisedGroupResource =
                resourceService.getSecurityRules(advertisedGroupResourceId);
        securityRulesAdvertisedGroupResource.add(editorGroupRule);
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                advertisedGroupResourceId,
                new SecurityRuleList(securityRulesAdvertisedGroupResource));

        /* group owned resource - read only, advertised */
        SecurityRule readOnlyGroupRule = new SecurityRule();
        readOnlyGroupRule.setGroup(group);
        readOnlyGroupRule.setCanRead(true);

        long readOnlyGroupResourceId =
                restCreateResource(READ_ONLY_RESOURCE_NAME, "", CAT0_NAME, adminId, true);
        List<SecurityRule> securityRulesReadOnlyGroupResource =
                resourceService.getSecurityRules(readOnlyGroupResourceId);
        securityRulesReadOnlyGroupResource.add(readOnlyGroupRule);
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                readOnlyGroupResourceId,
                new SecurityRuleList(securityRulesReadOnlyGroupResource));

        /* group owned resource - unadvertised */
        long unadvertisedGroupResourceId =
                restCreateResource("unadvertisedGroupResource", "", CAT0_NAME, adminId, false);
        List<SecurityRule> securityRulesUnadvertisedGroupResource =
                resourceService.getSecurityRules(unadvertisedGroupResourceId);
        securityRulesUnadvertisedGroupResource.add(readOnlyGroupRule);
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                unadvertisedGroupResourceId,
                new SecurityRuleList(securityRulesUnadvertisedGroupResource));

        {
            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            adminSecurityContext,
                            0,
                            1000,
                            new Sort("", ""),
                            false,
                            false,
                            new AndFilter());
            List<ExtResource> resources = response.getList();
            assertEquals(3, resources.size());
            assertTrue(
                    resources.stream()
                            .allMatch(r -> r.isCanEdit() && r.isCanDelete() && r.isCanCopy()));
        }

        {
            ExtResourceList response =
                    restExtJsService.getExtResourcesList(
                            user0SecurityContext,
                            0,
                            1000,
                            new Sort("", ""),
                            false,
                            false,
                            new AndFilter());
            List<ExtResource> resources = response.getList();
            assertEquals(2, resources.size());

            ExtResource groupResource =
                    resources.stream()
                            .filter(r -> r.getName().equals(GROUP_RESOURCE_NAME))
                            .findFirst()
                            .orElseThrow();
            assertTrue(groupResource.isCanEdit());
            assertTrue(groupResource.isCanDelete());
            assertTrue(groupResource.isCanCopy());

            ExtResource readOnlyResource =
                    resources.stream()
                            .filter(r -> r.getName().equals(READ_ONLY_RESOURCE_NAME))
                            .findFirst()
                            .orElseThrow();
            assertFalse(readOnlyResource.isCanEdit());
            assertFalse(readOnlyResource.isCanDelete());
            assertTrue(readOnlyResource.isCanCopy());
        }
    }

    @Test
    public void testGetExtResource_userOwnedWithAttributesInformation() throws Exception {
        final String CAT0_NAME = "CAT000";

        ShortAttribute attributeA = new ShortAttribute("attributeA", "ABC", DataType.STRING);
        ShortAttribute attributeB = new ShortAttribute("attributeB", "123.0", DataType.NUMBER);

        long adminId = restCreateUser("admin", Role.ADMIN, null, "admin");
        SecurityContext adminSecurityContext = new SimpleSecurityContext(adminId);

        long userId = restCreateUser("u0", Role.USER, null, "p0");
        SecurityContext user0SecurityContext = new SimpleSecurityContext(userId);

        createCategory(CAT0_NAME);

        /* user owned resource */
        long userOwnedResourceId =
                restCreateResource("ownedResource", "", CAT0_NAME, userId, false);
        restResourceService.updateAttribute(
                adminSecurityContext,
                userOwnedResourceId,
                attributeA.getName(),
                attributeA.getValue(),
                attributeA.getType());

        /* user owned resource - read only */
        long readOnlyResourceId =
                restCreateResource("readOnlyResource", "", CAT0_NAME, userId, false);
        SecurityRule readOnlyRule = new SecurityRule();
        readOnlyRule.setUser(userService.get(userId));
        readOnlyRule.setCanRead(true);
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                readOnlyResourceId,
                new SecurityRuleList(Collections.singletonList(readOnlyRule)));
        restResourceService.updateAttribute(
                adminSecurityContext,
                readOnlyResourceId,
                attributeB.getName(),
                attributeB.getValue(),
                attributeB.getType());

        /* user owned resource - no attributes */
        long noAttributesResourceId =
                restCreateResource("noAttributesResource", "", CAT0_NAME, userId, false);

        /* user owned resource - protected */
        long protectedResourceId =
                restCreateResource("protectedResource", "", CAT0_NAME, userId, false);
        SecurityRule protectedRule = new SecurityRule();
        protectedRule.setUser(userService.get(userId));
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                protectedResourceId,
                new SecurityRuleList(Collections.singletonList(protectedRule)));

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            adminSecurityContext, userOwnedResourceId, true, true);
            assertTrue(response.isCanEdit());
            assertTrue(response.isCanDelete());
            List<ShortAttribute> attributes = response.getAttributeList().getList();
            assertEquals(1, attributes.size());
            ShortAttribute attribute = attributes.get(0);
            assertEquals(attributeA.getName(), attribute.getName());
            assertEquals(attributeA.getValue(), attribute.getValue());
            assertEquals(attributeA.getType(), attribute.getType());
        }

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            adminSecurityContext, userOwnedResourceId, false, false);
            List<ShortAttribute> attributes = response.getAttributeList().getList();
            assertNull(attributes);
        }

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            user0SecurityContext, userOwnedResourceId, true, true);
            assertTrue(response.isCanEdit());
            assertTrue(response.isCanDelete());
            List<ShortAttribute> attributes = response.getAttributeList().getList();
            assertEquals(1, attributes.size());
            ShortAttribute attribute = attributes.get(0);
            assertEquals(attributeA.getName(), attribute.getName());
            assertEquals(attributeA.getValue(), attribute.getValue());
            assertEquals(attributeA.getType(), attribute.getType());
        }

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            user0SecurityContext, readOnlyResourceId, true, true);
            assertFalse(response.isCanEdit());
            assertFalse(response.isCanDelete());
            List<ShortAttribute> attributes = response.getAttributeList().getList();
            assertEquals(1, attributes.size());
            ShortAttribute attribute = attributes.get(0);
            assertEquals(attributeB.getName(), attribute.getName());
            assertEquals(attributeB.getValue(), attribute.getValue());
            assertEquals(attributeB.getType(), attribute.getType());
        }

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            user0SecurityContext, noAttributesResourceId, true, true);
            List<ShortAttribute> attributes = response.getAttributeList().getList();
            assertTrue(attributes.isEmpty());
        }

        {
            assertThrows(
                    ForbiddenErrorWebEx.class,
                    () ->
                            restExtJsService.getExtResource(
                                    user0SecurityContext, protectedResourceId, true, true));
        }

        {
            assertThrows(
                    NotFoundWebEx.class,
                    () ->
                            restExtJsService.getExtResource(
                                    user0SecurityContext, Long.MAX_VALUE, true, true));
        }
    }

    @Test
    public void testGetExtResource_groupOwnedWithAttributesInformation() throws Exception {
        final String CAT0_NAME = "CAT000";

        ShortAttribute attributeA = new ShortAttribute("attributeA", "ABC", DataType.STRING);
        ShortAttribute attributeB =
                new ShortAttribute("attributeB", "2024-08-31 16:22:45.654", DataType.DATE);

        long groupId = createGroup("group");
        UserGroup group = userGroupService.get(groupId);

        long adminId = restCreateUser("admin", Role.ADMIN, null, "admin");
        SecurityContext adminSecurityContext = new SimpleSecurityContext(adminId);

        long userId = restCreateUser("u0", Role.USER, Collections.singleton(group), "p0");
        SecurityContext user0SecurityContext = new SimpleSecurityContext(userId);

        createCategory(CAT0_NAME);

        /* group owned resource */
        SecurityRule ownerGroupRule = new SecurityRule();
        ownerGroupRule.setGroup(group);
        ownerGroupRule.setCanRead(true);
        ownerGroupRule.setCanWrite(true);

        long groupOwnedResourceId =
                restCreateResource("ownedResource", "", CAT0_NAME, adminId, false);
        List<SecurityRule> securityRulesGroupOwnedResource =
                resourceService.getSecurityRules(groupOwnedResourceId);
        securityRulesGroupOwnedResource.add(ownerGroupRule);
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                groupOwnedResourceId,
                new SecurityRuleList(securityRulesGroupOwnedResource));
        restResourceService.updateAttribute(
                adminSecurityContext,
                groupOwnedResourceId,
                attributeA.getName(),
                attributeA.getValue(),
                attributeA.getType());

        /* group owned resource - read only */
        SecurityRule readOnlyGroupRule = new SecurityRule();
        readOnlyGroupRule.setGroup(group);
        readOnlyGroupRule.setCanRead(true);

        long readOnlyGroupResourceId =
                restCreateResource("readOnlyResource", "", CAT0_NAME, adminId, false);
        List<SecurityRule> securityRulesReadOnlyGroupResource =
                resourceService.getSecurityRules(readOnlyGroupResourceId);
        securityRulesReadOnlyGroupResource.add(readOnlyGroupRule);
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                readOnlyGroupResourceId,
                new SecurityRuleList(securityRulesReadOnlyGroupResource));
        restResourceService.updateAttribute(
                adminSecurityContext,
                readOnlyGroupResourceId,
                attributeB.getName(),
                attributeB.getValue(),
                attributeB.getType());

        /* group owned resource - protected */
        SecurityRule protectedRule = new SecurityRule();
        protectedRule.setGroup(group);

        long protectedGroupResourceId =
                restCreateResource("protectedResource", "", CAT0_NAME, adminId, false);
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                protectedGroupResourceId,
                new SecurityRuleList(Collections.singletonList(protectedRule)));

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            adminSecurityContext, groupOwnedResourceId, true, true);
            List<ShortAttribute> attributes = response.getAttributeList().getList();
            assertEquals(1, attributes.size());
            ShortAttribute attribute = attributes.get(0);
            assertEquals(attributeA.getName(), attribute.getName());
            assertEquals(attributeA.getValue(), attribute.getValue());
            assertEquals(attributeA.getType(), attribute.getType());
        }

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            user0SecurityContext, groupOwnedResourceId, true, true);
            List<ShortAttribute> attributes = response.getAttributeList().getList();
            assertEquals(1, attributes.size());
            ShortAttribute attribute = attributes.get(0);
            assertEquals(attributeA.getName(), attribute.getName());
            assertEquals(attributeA.getValue(), attribute.getValue());
            assertEquals(attributeA.getType(), attribute.getType());
        }

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            user0SecurityContext, readOnlyGroupResourceId, true, true);
            assertFalse(response.isCanEdit());
            assertFalse(response.isCanDelete());
            List<ShortAttribute> attributes = response.getAttributeList().getList();
            assertEquals(1, attributes.size());
            ShortAttribute attribute = attributes.get(0);
            assertEquals(attributeB.getName(), attribute.getName());
            assertTrue(attribute.getValue().matches("2024-08-31T16:22:45.654\\+\\d+"));
            assertEquals(attributeB.getType(), attribute.getType());
        }

        {
            assertThrows(
                    ForbiddenErrorWebEx.class,
                    () ->
                            restExtJsService.getExtResource(
                                    user0SecurityContext, protectedGroupResourceId, true, true));
        }
    }

    @Test
    public void testGetExtResource_userOwnedWithPermissionsInformation() throws Exception {
        final String CAT0_NAME = "CAT000";

        long adminId = restCreateUser("admin", Role.ADMIN, null, "admin");
        SecurityContext adminSecurityContext = new SimpleSecurityContext(adminId);

        long userId = restCreateUser("u0", Role.USER, null, "p0");
        SecurityContext user0SecurityContext = new SimpleSecurityContext(userId);

        createCategory(CAT0_NAME);

        /* empty permissions resource */
        long noPermissionsResourceId =
                restCreateResource("noPermissionsResource", "", CAT0_NAME, adminId, false);
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                noPermissionsResourceId,
                new SecurityRuleList(Collections.emptyList()));

        /* user owned resource */
        long userOwnedResourceId =
                restCreateResource("ownedResource", "", CAT0_NAME, userId, false);

        /* user owned resource - read only */
        long readOnlyResourceId =
                restCreateResource("readOnlyResource", "", CAT0_NAME, userId, false);
        SecurityRule readOnlyRule = new SecurityRule();
        readOnlyRule.setUser(userService.get(userId));
        readOnlyRule.setCanRead(true);
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                readOnlyResourceId,
                new SecurityRuleList(Collections.singletonList(readOnlyRule)));

        /* user owned resource - protected */
        long protectedResourceId =
                restCreateResource("protectedResource", "", CAT0_NAME, userId, false);
        SecurityRule protectedRule = new SecurityRule();
        protectedRule.setUser(userService.get(userId));
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                protectedResourceId,
                new SecurityRuleList(Collections.singletonList(protectedRule)));

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            adminSecurityContext, noPermissionsResourceId, true, true);
            List<RESTSecurityRule> securityRules = response.getSecurityRuleList().getList();
            assertTrue(securityRules.isEmpty());
        }

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            adminSecurityContext, userOwnedResourceId, true, true);
            assertTrue(response.isCanEdit());
            assertTrue(response.isCanDelete());
            List<RESTSecurityRule> securityRules = response.getSecurityRuleList().getList();
            assertEquals(1, securityRules.size());
        }

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            adminSecurityContext, userOwnedResourceId, false, false);
            List<RESTSecurityRule> securityRules = response.getSecurityRuleList().getList();
            assertNull(securityRules);
        }

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            user0SecurityContext, userOwnedResourceId, true, true);
            assertTrue(response.isCanEdit());
            assertTrue(response.isCanDelete());
            List<RESTSecurityRule> securityRules = response.getSecurityRuleList().getList();
            assertEquals(1, securityRules.size());
            RESTSecurityRule securityRule = securityRules.get(0);
            assertEquals(userId, securityRule.getUser().getId().longValue());
            assertTrue(securityRule.isCanRead());
            assertTrue(securityRule.isCanWrite());
        }

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            user0SecurityContext, readOnlyResourceId, true, true);
            assertFalse(response.isCanEdit());
            assertFalse(response.isCanDelete());
            List<RESTSecurityRule> securityRules = response.getSecurityRuleList().getList();
            assertEquals(1, securityRules.size());
            RESTSecurityRule securityRule = securityRules.get(0);
            assertEquals(userId, securityRule.getUser().getId().longValue());
            assertTrue(securityRule.isCanRead());
            assertFalse(securityRule.isCanWrite());
        }

        {
            assertThrows(
                    ForbiddenErrorWebEx.class,
                    () ->
                            restExtJsService.getExtResource(
                                    user0SecurityContext, protectedResourceId, true, true));
        }

        {
            assertThrows(
                    NotFoundWebEx.class,
                    () ->
                            restExtJsService.getExtResource(
                                    user0SecurityContext, Long.MAX_VALUE, true, true));
        }
    }

    @Test
    public void testGetExtResource_groupOwnedWithPermissionsInformation() throws Exception {
        final String CAT0_NAME = "CAT000";

        long groupId = createGroup("group");
        UserGroup group = userGroupService.get(groupId);

        long adminId = restCreateUser("admin", Role.ADMIN, null, "admin");
        SecurityContext adminSecurityContext = new SimpleSecurityContext(adminId);

        long userId = restCreateUser("u0", Role.USER, Collections.singleton(group), "p0");
        SecurityContext user0SecurityContext = new SimpleSecurityContext(userId);

        createCategory(CAT0_NAME);

        /* group owned resource */
        SecurityRule ownerGroupRule = new SecurityRule();
        ownerGroupRule.setGroup(group);
        ownerGroupRule.setCanRead(true);
        ownerGroupRule.setCanWrite(true);

        long groupOwnedResourceId =
                restCreateResource("ownedResource", "", CAT0_NAME, adminId, false);
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                groupOwnedResourceId,
                new SecurityRuleList(Collections.singletonList(ownerGroupRule)));

        /* group owned resource - read only */
        SecurityRule readOnlyGroupRule = new SecurityRule();
        readOnlyGroupRule.setGroup(group);
        readOnlyGroupRule.setCanRead(true);

        long readOnlyGroupResourceId =
                restCreateResource("readOnlyResource", "", CAT0_NAME, adminId, false);
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                readOnlyGroupResourceId,
                new SecurityRuleList(Collections.singletonList(readOnlyGroupRule)));

        /* group owned resource - protected */
        SecurityRule protectedRule = new SecurityRule();
        protectedRule.setGroup(group);

        long protectedGroupResourceId =
                restCreateResource("protectedResource", "", CAT0_NAME, adminId, false);
        restResourceService.updateSecurityRules(
                adminSecurityContext,
                protectedGroupResourceId,
                new SecurityRuleList(Collections.singletonList(protectedRule)));

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            adminSecurityContext, groupOwnedResourceId, true, true);
            assertTrue(response.isCanEdit());
            assertTrue(response.isCanDelete());
            List<RESTSecurityRule> securityRules = response.getSecurityRuleList().getList();
            assertEquals(1, securityRules.size());
        }

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            user0SecurityContext, groupOwnedResourceId, true, true);
            assertTrue(response.isCanEdit());
            assertTrue(response.isCanDelete());
            List<RESTSecurityRule> securityRules = response.getSecurityRuleList().getList();
            assertEquals(1, securityRules.size());
            RESTSecurityRule securityRule = securityRules.get(0);
            assertEquals(groupId, securityRule.getGroup().getId().longValue());
            assertTrue(securityRule.isCanRead());
            assertTrue(securityRule.isCanWrite());
        }

        {
            ExtShortResource response =
                    restExtJsService.getExtResource(
                            user0SecurityContext, readOnlyGroupResourceId, true, true);
            assertFalse(response.isCanEdit());
            assertFalse(response.isCanDelete());
            List<RESTSecurityRule> securityRules = response.getSecurityRuleList().getList();
            assertEquals(1, securityRules.size());
            RESTSecurityRule securityRule = securityRules.get(0);
            assertEquals(groupId, securityRule.getGroup().getId().longValue());
            assertTrue(securityRule.isCanRead());
            assertFalse(securityRule.isCanWrite());
        }

        {
            assertThrows(
                    ForbiddenErrorWebEx.class,
                    () ->
                            restExtJsService.getExtResource(
                                    user0SecurityContext, protectedGroupResourceId, true, true));
        }
    }

    @Test
    public void testGetGroupsList() throws Exception {
        final String groupAName = "groupA";

        long groupAId = createGroup(groupAName);
        UserGroup groupA = userGroupService.get(groupAId);

        createGroup("groupB");

        long adminId = restCreateUser("admin", Role.ADMIN, null, "admin");
        SecurityContext adminSecurityContext = new SimpleSecurityContext(adminId);

        long userId = restCreateUser("u0", Role.USER, Collections.singleton(groupA), "p0");
        SecurityContext userSecurityContext = new SimpleSecurityContext(userId);

        {
            ExtGroupList response =
                    restExtJsService.getGroupsList(adminSecurityContext, null, 0, 1000, true);
            List<UserGroup> resources = response.getList();
            assertEquals(2, resources.size());
        }

        {
            ExtGroupList response =
                    restExtJsService.getGroupsList(userSecurityContext, null, 0, 1000, true);
            List<UserGroup> userGroups = response.getList();
            assertEquals(1, userGroups.size());
            UserGroup userGroup = userGroups.get(0);
            assertEquals(groupAName, userGroup.getGroupName());
        }
    }

    private JSONResult parse(String jsonString) {
        JSONResult ret = new JSONResult();

        JSON json = JSONSerializer.toJSON(jsonString);
        JSONObject jo = (JSONObject) json;
        ret.total = jo.getInt("totalCount");

        Set<String> names;

        JSONArray arrResults = jo.optJSONArray("results");
        if (arrResults != null) {
            names = getArray(arrResults);
        } else {
            JSONObject results = jo.optJSONObject("results");

            if (results != null) {
                names = Collections.singleton(getSingle(results));
            } else {
                LOGGER.warn("No results found");
                names = Collections.emptySet();
            }
        }

        ret.names = names;
        ret.returnedCount = names.size();

        return ret;
    }

    Set<String> getArray(JSONArray arr) {
        Set<String> ret = new HashSet<>();

        for (Object object : arr) {
            ret.add(getSingle((JSONObject) object));
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
