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
package it.geosolutions.geostore.services;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.dto.search.BaseField;
import it.geosolutions.geostore.services.dto.search.CategoryFilter;
import it.geosolutions.geostore.services.dto.search.FieldFilter;
import it.geosolutions.geostore.services.dto.search.SearchFilter;
import it.geosolutions.geostore.services.dto.search.SearchOperator;
import it.geosolutions.geostore.services.exception.DuplicatedResourceNameServiceEx;

/**
 * Class ResourceServiceImplTest.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * 
 */
public class ResourceServiceImplTest extends ServiceTestBase {

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    public ResourceServiceImplTest() {
    }    

    @Test
    public void testInsertDeleteResource() throws Exception {

        long resourceId = createResource("name1", "description1", "MAP");

        assertEquals(1, resourceService.getCount(null));
        assertTrue("Could not delete resource", resourceService.delete(resourceId));
        assertEquals(0, resourceService.getCount(null));
    }

    @Test
    public void testUpdateData() throws Exception {
        final String NAME1 = "name1";
        final String NAME2 = "name2";

        long resourceId = createResource(NAME1, "description1", "MAP");

        assertEquals(1, resourceService.getCount(null));

        {
            Resource loaded = resourceService.get(resourceId);
            assertNotNull(loaded);
            assertNull(loaded.getLastUpdate());
            assertEquals(NAME1, loaded.getName());

            loaded.setName(NAME2);
            resourceService.update(loaded);
        }

        {
            Resource loaded = resourceService.get(resourceId);
            assertNotNull(loaded);
            assertNotNull(loaded.getLastUpdate());
            assertEquals(NAME2, loaded.getName());
        }

        {
            assertEquals(1, resourceService.getCount(null));

            resourceService.delete(resourceId);
            assertEquals(0, resourceService.getCount(null));
        }
    }

    @Test
    public void testGetAllData() throws Exception {
        assertEquals(0, resourceService.getAll(null, null, buildFakeAdminUser()).size());

        for (int i = 0; i < 10; i++) {
            createResource("name" + i, "description" + i, "MAP1" + i);
        }

        for (int i = 0; i < 10; i++) {
            createResource("test name" + i, "description" + i, "MAP2" + i);
        }

        assertEquals(20, resourceService.getAll(null, null, buildFakeAdminUser()).size());
        assertEquals(10, resourceService.getCount("name%"));
        assertEquals(10, resourceService.getList("name%", null, null, buildFakeAdminUser()).size());
        assertEquals(20, resourceService.getCount("%name%"));
        assertEquals(20, resourceService.getList("%name%", null, null, buildFakeAdminUser()).size());
        assertEquals(2, resourceService.getCount("%name1%"));
        assertEquals(2, resourceService.getList("%name1%", null, null, buildFakeAdminUser()).size());
    }
    /**
     * Tests if the results are sorted by name
     * @throws Exception
     */
    @Test
    public void testSorting() throws Exception {
    	assertEquals(0, resourceService.getAll(null, null, buildFakeAdminUser()).size());
    	// setup data. First set is ordered
        for (int i = 0; i < 20; i++) {
            createResource("FIRST SET - " + i, "description" + i, "MAP1" + i);
        }
        // note: inverse name order to check the final results are returned
        // "test name 10", "test name 9"..., "test name 0";
        // so second set is inserted in reverse order
        for (int i = 19; i >= 0; i--) {
            createResource("SECOND SET - " + i, "description" + i, "MAP2" + i);
        }
        // check getAll
        List<ShortResource> getAllResult = resourceService.getAll(null, null, buildFakeAdminUser());
        assertEquals(40, getAllResult.size());
        assertTrue(isSorted(getAllResult));
        
        //
        // check getResources, various filters
        // 
        
        // category like 
        SearchFilter MAPCategoryFilter = new CategoryFilter("MAP%", SearchOperator.LIKE);
        List<ShortResource> getResourcesMAPResult = resourceService.getResources(MAPCategoryFilter, buildFakeAdminUser());
        assertEquals(40, getResourcesMAPResult.size());
        assertTrue(isSorted(getResourcesMAPResult));
        SearchFilter MAP1CategoryFilter = new CategoryFilter("MAP1%", SearchOperator.LIKE);
        List<ShortResource> getResourcesMAP1Result = resourceService.getResources(MAP1CategoryFilter, buildFakeAdminUser());
        assertEquals(20, getResourcesMAP1Result.size());
        assertTrue(isSorted(getResourcesMAP1Result));
        SearchFilter MAP2CategoryFilter = new CategoryFilter("MAP2%", SearchOperator.LIKE);
        List<ShortResource> getResourcesMAP2Result = resourceService.getResources(MAP2CategoryFilter, buildFakeAdminUser());
        assertEquals(20, getResourcesMAP2Result.size());
        assertTrue(isSorted(getResourcesMAP2Result));
        
        // name like
        SearchFilter nameContain1Filter = new FieldFilter(BaseField.NAME, "%1%", SearchOperator.LIKE);
        List<ShortResource> nameContain1Result = resourceService.getResources(nameContain1Filter, buildFakeAdminUser());
        // 22 resources contain 1 in the name: "FIRST SET - 1" + "FIRST SET - 10" ... "FIRST SET - 19", same for second set
        assertEquals(22, nameContain1Result.size());
        assertTrue(isSorted(nameContain1Result));
        
        SearchFilter nameContain2Filter = new FieldFilter(BaseField.NAME, "%2%", SearchOperator.LIKE);
        List<ShortResource> nameContain2Result = resourceService.getResources(nameContain2Filter, buildFakeAdminUser());
        // 4 resources contain 1 in the name: "FIRST SET - 2" + "FIRST SET - 12" 
        assertEquals(4, nameContain2Result.size());
        assertTrue(isSorted(nameContain2Result));
        
    }
    
    /**
     * Check if the List passed is sorted by name 
     * @param resourcesList
     * @return
     */
    private static boolean isSorted(List<ShortResource> resourcesList) {
        if (resourcesList.size() == 1) {
            return true;
        }
     
        Iterator<ShortResource> iter = resourcesList.iterator();
        ShortResource current, previous = iter.next();
        while (iter.hasNext()) {
            current = iter.next();
            if (previous.getName().compareTo(current.getName()) > 0) {
                return false;
            }
            previous = current;
        }
        return true;
    }

    @Test
    public void testCategoryFilter() throws Exception {
        assertEquals(0, categoryService.getAll(null, null).size());

        long id0 = createCategory("category0");
        long id1 = createCategory("category1");
        assertEquals(2, categoryService.getAll(null, null).size());

        Category c0 = new Category();
        c0.setId(id0);

        Category c1i = new Category();
        c1i.setId(id1);

        Category c1n = new Category();
        c1n.setName("category1");

        assertEquals(0, resourceService.getAll(null, null, buildFakeAdminUser()).size());

        long r0 = createResource("res0", "des0", c0);
        long r1 = createResource("res1", "des1", c1i);
        long r2 = createResource("res2", "des2", c1n);
        assertEquals(3, resourceService.getAll(null, null, buildFakeAdminUser()).size());

        {
            SearchFilter filter = new CategoryFilter("category0", SearchOperator.EQUAL_TO);
            List<ShortResource> list = resourceService.getResources(filter, buildFakeAdminUser());
            assertEquals(1, list.size());
            assertEquals(r0, list.get(0).getId());
        }

        {
            SearchFilter filter = new CategoryFilter("%1", SearchOperator.LIKE);
            List<ShortResource> list = resourceService.getResources(filter, buildFakeAdminUser());
            assertEquals(2, list.size());
        }

        {
            SearchFilter filter = new CategoryFilter("cat%", SearchOperator.LIKE);
            List<ShortResource> list = resourceService.getResources(filter, buildFakeAdminUser());
            assertEquals(3, list.size());
        }
    }
    
    @Test
    public void testGetSecurityRules() throws Exception {
    	long userId = createUser("user1", Role.USER, "password");
    	User user = new User();
    	user.setId(userId);
    	
    	long groupId = createGroup("group1");
    	UserGroup group = new UserGroup();
    	group.setId(groupId);
    	
    	List<SecurityRule> rules = new ArrayList<>();
    	
    	SecurityRule rule = new SecurityRule();
    	rule.setUser(user);
    	rule.setCanRead(true);
    	rules.add(rule);
    	
    	rule = new SecurityRule();
    	rule.setCanRead(true);
    	rule.setCanWrite(true);
    	rule.setGroup(group);
    	rules.add(rule);
    	
    	long resourceId = createResource("name1", "description1", "MAP", rules);
    	
    	List<SecurityRule> writtenRules = resourceService.getSecurityRules(resourceId);
    	
    	assertEquals(2, writtenRules.size());
    	
    	SecurityRule userRule = writtenRules.get(0);
		assertNotNull(userRule.getUser());
    	assertNull(userRule.getGroup());
    	assertEquals((Long)userId, userRule.getUser().getId());
    	assertEquals((Long)resourceId, userRule.getResource().getId());
    	
    	SecurityRule groupRule = writtenRules.get(1);
		assertNotNull(groupRule.getGroup());
    	assertNull(groupRule.getUser());
    	assertEquals((Long)groupId, groupRule.getGroup().getId());
    	assertEquals((Long)resourceId, groupRule.getResource().getId());
    	
    }
    
    @Test
    public void testUpdateSecurityRules() throws Exception {
    	long resourceId = createResource("name1", "description1", "MAP");
    	
    	List<SecurityRule> writtenRules = resourceService.getSecurityRules(resourceId);
    	assertEquals(0, writtenRules.size());
    	
    	List<SecurityRule> rules = new ArrayList<SecurityRule>();
    	
		long userId = createUser("user1", Role.USER, "password");
		User user = new User();
		user.setId(userId);
		
		long groupId = createGroup("group1");
		UserGroup group = new UserGroup();
		group.setId(groupId);
		
		long otherGroupId = createGroup("group2");
		UserGroup othergroup = new UserGroup();
		othergroup.setId(otherGroupId);

    	SecurityRule rule = new SecurityRule();
    	rule.setUser(user);
    	rule.setCanRead(true);
    	rules.add(rule);
    	
    	rule = new SecurityRule();
    	rule.setCanRead(true);
    	rule.setCanWrite(true);
    	rule.setGroup(group);
    	rules.add(rule);
    	
    	resourceService.updateSecurityRules(resourceId, rules);
    	
    	writtenRules = resourceService.getSecurityRules(resourceId);
    	assertEquals(2, writtenRules.size());
    	
    	rules.clear();
    	
    	rule = new SecurityRule();
    	rule.setUser(user);
    	rule.setCanRead(true);
    	rules.add(rule);
    	
    	rule = new SecurityRule();
    	rule.setCanRead(true);
    	rule.setCanWrite(true);
    	rule.setGroup(group);
    	rules.add(rule);
    	rule = new SecurityRule();
    	rule.setCanRead(true);
    	rule.setCanWrite(true);
    	rule.setGroup(othergroup);
    	rules.add(rule);
    	
    	resourceService.updateSecurityRules(resourceId, rules);
    	
    	writtenRules = resourceService.getSecurityRules(resourceId);
    	assertEquals(3, writtenRules.size());
    }
    
    @Test
    public void testInsertTooBigResource() throws Exception {
        final String ORIG_RES_NAME = "testRes";
        final String DESCRIPTION = "description";
        final String CATEGORY_NAME = "MAP";
        String bigData = createDataSize(100000000);
        boolean error = false;
        assertEquals(0, resourceService.getCount(null));
        try {
            createResource(ORIG_RES_NAME, DESCRIPTION, CATEGORY_NAME, bigData);
        } catch(Exception e) {
            error = true;
        }
        assertEquals(0, resourceService.getCount(null));
        assertTrue(error);
    }
    
    private static String createDataSize(int msgSize) {
        StringBuilder sb = new StringBuilder(msgSize);
        for (int i = 0; i < msgSize; i++) {
            sb.append('a');
        }
        return sb.toString();
    }
    
    @Test
    public void testInsertUpdateDuplicatedResource() throws Exception {
    	final String ORIG_RES_NAME = "testRes";
    	final String DESCRIPTION = "description";
    	final String CATEGORY_NAME = "MAP";
    	final int NUM_COPIES = 3;
    	final long[] COPY_IDS = new long[NUM_COPIES];
    	
    	long origResourceId = createResource(ORIG_RES_NAME, DESCRIPTION, CATEGORY_NAME);
    	Category category = categoryService.get(CATEGORY_NAME);

        assertEquals(1, resourceService.getCount(null));
        assertNotNull(category);
        
        for (int i=0; i<NUM_COPIES; i++) {
        	// //////////////////////
            // test insert
            // //////////////////////
        	
        	long copyId = -1;
            try {
            	createResource(ORIG_RES_NAME, DESCRIPTION, category);
            	fail("DuplicatedResourceNameServiceEx was not thrown as expected");
            } catch (DuplicatedResourceNameServiceEx ex) {
            	// OK, exception was thrown: exception message be a valid resource name
            	String validCopyName = ex.getMessage();
            	
            	assertNotNull("Thrown DuplicatedResourceNameServiceEx exception's message was null", validCopyName);
                assertFalse("Thrown DuplicatedResourceNameServiceEx exception's message was empty", validCopyName.isEmpty());
                 
                copyId = createResource(validCopyName, DESCRIPTION, category);
            }
            
            assertTrue(copyId > 0);
            assertEquals(i+2, resourceService.getCount(null));
            
            // //////////////////////
            // test update
            // //////////////////////
            
            Resource copy = resourceService.get(copyId);
            assertNotNull(copy);
            copy.setName(ORIG_RES_NAME);
            try {
            	resourceService.update(copy);
            	fail("DuplicatedResourceNameServiceEx was not thrown as expected");
            } catch (DuplicatedResourceNameServiceEx ex) {
            	// OK, exception was thrown: exception message be a valid resource name
            	String validCopyName = ex.getMessage();
            	
            	assertNotNull("Thrown DuplicatedResourceNameServiceEx exception's message was null", validCopyName);
                assertFalse("Thrown DuplicatedResourceNameServiceEx exception's message was empty", validCopyName.isEmpty());
                 
                copy.setName(validCopyName);
                // should throw no exception
                try {
                	resourceService.update(copy);
                	
                	// update description
                	copy.setDescription(DESCRIPTION + " modified");
                	resourceService.update(copy);
                } catch (Exception e) {
                	fail("Exception was thrown during update: " + e.getMessage());
                }                
            }
            
            COPY_IDS[i] = copyId;
        }
        
        // cleanup
        assertTrue("Could not delete resource", resourceService.delete(origResourceId));
        for (int i=0; i<NUM_COPIES; i++) {
        	assertTrue("Could not delete resource", resourceService.delete(COPY_IDS[i]));        	
        }
        
        assertEquals(0, resourceService.getCount(null));    	
    }
    
}
