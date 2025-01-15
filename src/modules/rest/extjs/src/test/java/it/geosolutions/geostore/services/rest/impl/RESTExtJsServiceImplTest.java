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
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.AndFilter;
import it.geosolutions.geostore.services.dto.search.BaseField;
import it.geosolutions.geostore.services.dto.search.FieldFilter;
import it.geosolutions.geostore.services.dto.search.GroupFilter;
import it.geosolutions.geostore.services.dto.search.SearchOperator;
import it.geosolutions.geostore.services.model.ExtResourceList;
import it.geosolutions.geostore.services.rest.model.SecurityRuleList;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.SecurityContext;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * @author ETj (etj at geo-solutions.it)
 */
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

        assertEquals(0, resourceService.getAll(null, null, buildFakeAdminUser()).size());

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

        assertEquals(0, resourceService.getAll(null, null, buildFakeAdminUser()).size());

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

        assertEquals(0, resourceService.getAll(null, null, buildFakeAdminUser()).size());

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

        assertEquals(0, resourceService.getAll(null, null, buildFakeAdminUser()).size());

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

            ShortResource resource = restExtJsService.getResource(sc, r0Id);
            assertEquals(RES_NAME, resource.getName());
            assertEquals("u0", resource.getCreator());
            assertEquals("u0", resource.getEditor());
        }

        {
            SecurityContext sc = new SimpleSecurityContext(a0);
            Resource realResource = resourceService.get(r0Id);
            realResource.setName("new name");
            restResourceService.update(sc, r0Id, createRESTResource(realResource));

            ShortResource resource = restExtJsService.getResource(sc, r0Id);
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

        assertEquals(0, resourceService.getAll(null, null, buildFakeAdminUser()).size());

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

        assertEquals(0, resourceService.getResources(new AndFilter(), buildFakeAdminUser()).size());

        long u0 = restCreateUser("u0", Role.USER, null, "p0");
        SecurityContext sc = new SimpleSecurityContext(u0);

        createCategory(CAT0_NAME);

        restCreateResource(RES_ATTRIBUTE_A, RES_ATTRIBUTE_A, CAT0_NAME, u0, true);
        restCreateResource(RES_ATTRIBUTE_B, RES_ATTRIBUTE_B, CAT0_NAME, u0, true);
        restCreateResource(RES_ATTRIBUTE_C, RES_ATTRIBUTE_C, CAT0_NAME, u0, true);

        {
            ExtResourceList response = restExtJsService.getExtResourcesList(sc, 0, 1000, "description", "asc", false, false, new AndFilter());

            List<Resource> resources = response.getList();
            assertEquals(3, resources.size());
            List<String> resourcesDescriptions = resources.stream().map(Resource::getName).collect(Collectors.toList());
            assertEquals(List.of(RES_ATTRIBUTE_A, RES_ATTRIBUTE_B, RES_ATTRIBUTE_C), resourcesDescriptions);
        }

        {
            ExtResourceList response = restExtJsService.getExtResourcesList(sc, 0, 1000, "creation", "desc", false, false, new AndFilter());

            List<Resource> resources = response.getList();
            assertEquals(3, resources.size());
            List<Date> resourcesCreationDates = resources.stream().map(Resource::getCreation).collect(Collectors.toList());
            assertTrue(resourcesCreationDates.get(0).after(resourcesCreationDates.get(1)));
            assertTrue(resourcesCreationDates.get(1).after(resourcesCreationDates.get(2)));
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
            FieldFilter editorFieldFilter = new FieldFilter(BaseField.CREATOR, "creatorB", SearchOperator.EQUAL_TO);

            ExtResourceList response = restExtJsService.getExtResourcesList(sc, 0, 1000, "", "", false, false, editorFieldFilter);

            List<Resource> resources = response.getList();
            assertEquals(1, resources.size());
            Resource resource = resources.get(0);
            assertEquals(CREATOR_B, resource.getCreator());
        }

        {
            FieldFilter editorFieldFilter = new FieldFilter(BaseField.CREATOR, "CREATOR_", SearchOperator.ILIKE);

            ExtResourceList response = restExtJsService.getExtResourcesList(sc, 0, 1000, "", "", false, false, editorFieldFilter);

            List<Resource> resources = response.getList();
            assertEquals(2, resources.size());
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
            FieldFilter editorFieldFilter = new FieldFilter(BaseField.EDITOR, "editorA", SearchOperator.EQUAL_TO);

            ExtResourceList response = restExtJsService.getExtResourcesList(sc, 0, 1000, "", "", false, false, editorFieldFilter);

            List<Resource> resources = response.getList();
            assertEquals(1, resources.size());
            Resource resource = resources.get(0);
            assertEquals(EDITOR_A, resource.getEditor());
        }

        {
            FieldFilter editorFieldFilter = new FieldFilter(BaseField.EDITOR, "EDITOR_", SearchOperator.ILIKE);

            ExtResourceList response = restExtJsService.getExtResourcesList(sc, 0, 1000, "", "", false, false, editorFieldFilter);

            List<Resource> resources = response.getList();
            assertEquals(2, resources.size());
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

        long resourceAId = restCreateResource(RESOURCE_A_NAME, "description_A", CAT0_NAME, user0Id, true);
        long resourceBId = restCreateResource(RESOURCE_B_NAME, "description_B", CAT0_NAME, user0Id, true);

        SecurityRule securityRuleGroupA = new SecurityRule();
        securityRuleGroupA.setGroup(userGroupService.get(createGroup(GROUP_A_NAME)));
        securityRuleGroupA.setCanWrite(true);

        List<SecurityRule> securityRulesResourceA = resourceService.getSecurityRules(resourceAId);
        securityRulesResourceA.add(securityRuleGroupA);
        restResourceService.updateSecurityRules(sc, resourceAId, new SecurityRuleList(securityRulesResourceA));

        SecurityRule securityRuleGroupB = new SecurityRule();
        securityRuleGroupB.setGroup(userGroupService.get(createGroup(GROUP_B_NAME)));
        securityRuleGroupB.setCanRead(true);

        List<SecurityRule> securityRulesResourceB = resourceService.getSecurityRules(resourceBId);
        securityRulesResourceB.add(securityRuleGroupB);
        restResourceService.updateSecurityRules(sc, resourceBId, new SecurityRuleList(securityRulesResourceB));

        {
            /* search for name equality of a single group */
            GroupFilter groupFilter = new GroupFilter(Collections.singletonList("groupA"), SearchOperator.EQUAL_TO);

            ExtResourceList response = restExtJsService.getExtResourcesList(sc, 0, 1000, "", "", false, false, groupFilter);

            List<Resource> resources = response.getList();
            assertEquals(1, resources.size());
            Resource resource = resources.get(0);
            assertEquals(RESOURCE_A_NAME, resource.getName());
        }

        {
            /* search for name similarity (ignoring case) of multiple groups */
            GroupFilter groupFilter = new GroupFilter(Collections.singletonList("GROUP_"), SearchOperator.ILIKE);

            ExtResourceList response = restExtJsService.getExtResourcesList(sc, 0, 1000, "", "", false, false, groupFilter);

            List<Resource> resources = response.getList();
            assertEquals(2, resources.size());
        }

        {
            /* search for name equality of multiple groups */
            GroupFilter groupFilter = new GroupFilter(List.of("groupA", "groupB", "groupC"), SearchOperator.IN);

            ExtResourceList response = restExtJsService.getExtResourcesList(sc, 0, 1000, "", "", false, false, groupFilter);

            List<Resource> resources = response.getList();
            assertEquals(2, resources.size());
        }

        {
            /* erroneous search for similarity of multiple groups */
            GroupFilter groupFilter = new GroupFilter(List.of("a", "b"), SearchOperator.LIKE);

            assertThrows(IllegalStateException.class, () -> restExtJsService.getExtResourcesList(sc, 0, 1000, "", "", false, false, groupFilter));
        }

        {
            /* erroneous search for equality in empty group list */
            GroupFilter groupFilter = new GroupFilter(Collections.emptyList(), SearchOperator.EQUAL_TO);

            assertThrows(IllegalStateException.class, () -> restExtJsService.getExtResourcesList(sc, 0, 1000, "", "", false, false, groupFilter));
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
            FieldFilter ltDateFilter = new FieldFilter(BaseField.LASTUPDATE, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(resourceB.getLastUpdate()), SearchOperator.LESS_THAN);

            ExtResourceList response = restExtJsService.getExtResourcesList(sc, 0, 1000, "", "", false, false, ltDateFilter);

            List<Resource> resources = response.getList();
            assertEquals(1, resources.size());
            Resource resource = resources.get(0);
            assertEquals(resourceAId, resource.getId().longValue());
        }

        {
            FieldFilter gteDateFilter = new FieldFilter(BaseField.CREATION, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(resourceA.getCreation()), SearchOperator.GREATER_THAN_OR_EQUAL_TO);
            FieldFilter lteDateFilter = new FieldFilter(BaseField.CREATION, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(resourceB.getLastUpdate()), SearchOperator.LESS_THAN_OR_EQUAL_TO);
            AndFilter betweenDatesFieldFilter = new AndFilter(gteDateFilter, lteDateFilter);

            ExtResourceList response = restExtJsService.getExtResourcesList(sc, 0, 1000, "", "", false, false, betweenDatesFieldFilter);

            List<Resource> resources = response.getList();
            assertEquals(2, resources.size());
        }
    }

    @Test
    public void testExtResourcesList_withPermissionsInformation() throws Exception {
        final String CAT0_NAME = "CAT000";
        final String OWNER_RESOURCE_NAME = "ownerResource";
        final String READ_ONLY_RESOURCE_NAME = "readOnlyResource";
        final String ADVERTISED_GROUP_RESOURCE_NAME = "advertisedGroupResource";

        long groupId = createGroup("group");
        UserGroup group = userGroupService.get(groupId);

        long adminId = restCreateUser("admin", Role.ADMIN, null, "admin");
        SecurityContext adminSecurityContext = new SimpleSecurityContext(adminId);

        long userId = restCreateUser("u0", Role.USER, Collections.singleton(group), "p0");
        SecurityContext user0SecurityContext = new SimpleSecurityContext(userId);

        createCategory(CAT0_NAME);

        /* admin owned resource */
        restCreateResource("adminResource", "", CAT0_NAME, adminId, false);

        /* user owned resource */
        restCreateResource(OWNER_RESOURCE_NAME, "", CAT0_NAME, userId, false);

        /* user owned resource - read only */
        long readOnlyResourceId = restCreateResource(READ_ONLY_RESOURCE_NAME, "", CAT0_NAME, userId, false);
        SecurityRule readOnlyRule = new SecurityRule();
        readOnlyRule.setUser(userService.get(userId));
        readOnlyRule.setCanRead(true);
        restResourceService.updateSecurityRules(adminSecurityContext, readOnlyResourceId, new SecurityRuleList(Collections.singletonList(readOnlyRule)));

        /* advertised resource */
        restCreateResource("advertisedResource", "", CAT0_NAME, adminId, true);

        SecurityRule groupRule = new SecurityRule();
        groupRule.setGroup(group);
        groupRule.setCanRead(true);
        groupRule.setCanWrite(true);

        /* group owned resource - advertised */
        long advertisedGroupResourceId = restCreateResource(ADVERTISED_GROUP_RESOURCE_NAME, "", CAT0_NAME, adminId, true);
        List<SecurityRule> securityRulesAdvertisedGroupResource = resourceService.getSecurityRules(advertisedGroupResourceId);
        securityRulesAdvertisedGroupResource.add(groupRule);
        restResourceService.updateSecurityRules(adminSecurityContext, advertisedGroupResourceId, new SecurityRuleList(securityRulesAdvertisedGroupResource));

        /* group owned resource - unadvertised */
        long unadvertisedGroupResourceId = restCreateResource("unadvertisedGroupResource", "", CAT0_NAME, adminId, false);
        List<SecurityRule> securityRulesUnadvertisedGroupResource = resourceService.getSecurityRules(unadvertisedGroupResourceId);
        securityRulesUnadvertisedGroupResource.add(groupRule);
        restResourceService.updateSecurityRules(adminSecurityContext, unadvertisedGroupResourceId, new SecurityRuleList(securityRulesUnadvertisedGroupResource));

        {
            ExtResourceList response = restExtJsService.getExtResourcesList(adminSecurityContext, 0, 1000, "", "", false, false, new AndFilter());
            List<Resource> resources = response.getList();
            assertEquals(6, resources.size());
            assertTrue(resources.stream().allMatch(r -> r.isCanEdit() && r.isCanDelete() && r.isCanCopy()));
        }
        {
            ExtResourceList response = restExtJsService.getExtResourcesList(user0SecurityContext, 0, 1000, "", "", false, false, new AndFilter());
            List<Resource> resources = response.getList();
            assertEquals(3, resources.size());

            Resource ownerResource = resources.stream().filter(r -> r.getName().equals(OWNER_RESOURCE_NAME)).findFirst().orElseThrow();
            assertTrue(ownerResource.isCanEdit());
            assertTrue(ownerResource.isCanDelete());
            assertTrue(ownerResource.isCanCopy());

            Resource readOnlyResource = resources.stream().filter(r -> r.getName().equals(READ_ONLY_RESOURCE_NAME)).findFirst().orElseThrow();
            assertFalse(readOnlyResource.isCanEdit());
            assertFalse(readOnlyResource.isCanDelete());
            assertTrue(readOnlyResource.isCanCopy());

            Resource groupResource = resources.stream().filter(r -> r.getName().equals(ADVERTISED_GROUP_RESOURCE_NAME)).findFirst().orElseThrow();
            assertTrue(groupResource.isCanEdit());
            assertTrue(groupResource.isCanDelete());
            assertTrue(groupResource.isCanCopy());
        }
    }

    private JSONResult parse(String jsonString) {
        JSONResult ret = new JSONResult();

        JSON json = JSONSerializer.toJSON(jsonString);
        JSONObject jo = (JSONObject) json;
        ret.total = jo.getInt("totalCount");

        Set names;

        JSONArray arrResults = jo.optJSONArray("results");
        if (arrResults != null) {
            names = getArray(arrResults);
        } else {
            JSONObject results = jo.optJSONObject("results");

            if (results != null) {
                names = Collections.singleton(getSingle(results));
            } else {
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
        Set names;
    }
}
