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
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import it.geosolutions.geostore.core.dao.ResourceDAO;
import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.StoredData;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.GroupReservedNames;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import junit.framework.TestCase;

/**
 * Class ServiceTestBase.
 * 
 * @author ETj (etj at geo-solutions.it)
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 * 
 */
public class ServiceTestBase extends TestCase {

    protected static StoredDataService storedDataService;

    protected static ResourceService resourceService;

    protected static CategoryService categoryService;

    protected static UserService userService;
    
    protected static UserGroupService userGroupService;
    
    protected static ResourceDAO resourceDAO;

    protected static ClassPathXmlApplicationContext ctx = null;

    protected final Logger LOGGER = Logger.getLogger(getClass());

    /**
     *
     */
    public ServiceTestBase() {
        synchronized (ServiceTestBase.class) {
            if (ctx == null) {
                String[] paths = { "classpath*:applicationContext.xml"
                // ,"applicationContext-test.xml"
                };
                ctx = new ClassPathXmlApplicationContext(paths);

                storedDataService = (StoredDataService) ctx.getBean("storedDataService");
                resourceService = (ResourceService) ctx.getBean("resourceService");
                categoryService = (CategoryService) ctx.getBean("categoryService");
                userService = (UserService) ctx.getBean("userService");
                userGroupService = (UserGroupService) ctx.getBean("userGroupService");
                resourceDAO = (ResourceDAO) ctx.getBean("resourceDAO");
            }
        }
    }

    /*
     * (non-Javadoc) @see junit.framework.TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        LOGGER.info("################ Running " + getClass().getSimpleName() + "::" + getName());
        super.setUp();
        removeAll();
    }

    /**
     *
     */
    public void testCheckServices() {
        assertNotNull(storedDataService);
        assertNotNull(resourceService);
        assertNotNull(categoryService);
        assertNotNull(userService);
        assertNotNull(userGroupService);
    }

    /**
     * @throws NotFoundServiceEx
     * @throws BadRequestServiceEx
     */
    protected void removeAll() throws NotFoundServiceEx, BadRequestServiceEx {
        LOGGER.info("***** removeAll()");
        removeAllResource();
        removeAllStoredData();
        removeAllCategory();
        removeAllUser();
        removeAllUserGroup();
    }

    /**
     * @throws BadRequestServiceEx
     * @throws NotFoundServiceEx 
     */
    private void removeAllUserGroup() throws BadRequestServiceEx, NotFoundServiceEx {
        List<UserGroup> list = userGroupService.getAll(null, null);
        for (UserGroup item : list) {
            LOGGER.info("Removing User: " + item.getGroupName());
            if (GroupReservedNames.isAllowedName(item.getGroupName())) {
                boolean ret = userGroupService.delete(item.getId());
                assertTrue("Group not removed", ret);
            }
        }
        boolean res = userGroupService.removeSpecialUsersGroups();
        assertEquals("Group have not been properly deleted", 0, userService.getCount(null));
    }

    
    /**
     * @throws BadRequestServiceEx
     */
    private void removeAllUser() throws BadRequestServiceEx {
        List<User> list = userService.getAll(null, null);
        for (User item : list) {
            LOGGER.info("Removing User: " + item.getName());

            boolean ret = userService.delete(item.getId());
            assertTrue("User not removed", ret);
        }

        assertEquals("User have not been properly deleted", 0, userService.getCount(null));
    }

    /**
     * @throws BadRequestServiceEx
     */
    private void removeAllCategory() throws BadRequestServiceEx {
        List<Category> list = categoryService.getAll(null, null);
        for (Category item : list) {
            LOGGER.info("Removing " + item);

            boolean ret = categoryService.delete(item.getId());
            assertTrue("Category not removed", ret);
        }

        assertEquals("Category have not been properly deleted", 0, categoryService.getCount(null));
    }

    /**
     * @throws NotFoundServiceEx
     */
    protected void removeAllStoredData() throws NotFoundServiceEx {
        List<StoredData> list = storedDataService.getAll();
        for (StoredData item : list) {
            LOGGER.info("Removing " + item);

            boolean ret = storedDataService.delete(item.getId());
            assertTrue("Data not removed", ret);
        }
    }

    /**
     * @throws BadRequestServiceEx
     * 
     */
    private void removeAllResource() throws BadRequestServiceEx {
        List<ShortResource> list = resourceService.getAll(null, null, buildFakeAdminUser());
        for (ShortResource item : list) {
            LOGGER.info("Removing " + item);

            boolean ret = resourceService.delete(item.getId());
            assertTrue("Resource not removed", ret);
        }

        assertEquals("Resource have not been properly deleted", 0, resourceService.getCount(null));
    }

    /**
     * @param name
     * @param data
     * @return long
     * @throws Exception
     */
    protected long createData(String data, Resource resource) throws Exception {
        return storedDataService.update(resource.getId(), data);
    }

    /**
     * @param name
     * @param creation
     * @param description
     * @param storedData
     * @return long
     * @throws Exception
     */
    protected long createResource(String name, String description, String catName) throws Exception {

        Category category = new Category();
        category.setName(catName);

        categoryService.insert(category);

        Resource resource = new Resource();
        resource.setName(name);
        resource.setDescription(description);
        resource.setCategory(category);

        return resourceService.insert(resource);
    }
    
    /**
     * @param name
     * @param creation
     * @param description
     * @param data
     *
     * @return long
     * @throws Exception
     */
    protected long createResource(String name, String description, String catName, String data) throws Exception {

        Category category = new Category();
        category.setName(catName);

        categoryService.insert(category);

        Resource resource = new Resource();
        resource.setName(name);
        resource.setDescription(description);
        resource.setCategory(category);
        StoredData storedData = new StoredData();
        storedData.setData(data);
        resource.setData(storedData);

        return resourceService.insert(resource);
    }
    
    /**
     * @param name
     * @param creation
     * @param description
     * @param storedData
     * @return long
     * @throws Exception
     */
    protected long createResource(String name, String description, String catName, List<SecurityRule> rules) throws Exception {

        Category category = new Category();
        category.setName(catName);

        categoryService.insert(category);

        Resource resource = new Resource();
        resource.setName(name);
        resource.setDescription(description);
        resource.setCategory(category);
        resource.setSecurity(rules);

        return resourceService.insert(resource);
    }

    protected long createResource(String name, String description, Category category)
            throws Exception {

        Resource resource = new Resource();
        resource.setName(name);
        resource.setDescription(description);
        resource.setCategory(category);

        return resourceService.insert(resource);
    }

    /**
     * @param name
     * @return long
     * @throws Exception
     */
    protected long createCategory(String name) throws Exception {
        Category category = new Category();
        category.setName(name);

        return categoryService.insert(category);
    }

    /**
     * @param name
     * @param role
     * @param password
     * @return long
     * @throws Exception
     */
    protected long createUser(String name, Role role, String password) throws Exception {
        User user = new User();
        user.setName(name);
        user.setRole(role);
        user.setNewPassword(password);

        return userService.insert(user);
    }
    
    protected long createUserGroup(String name, long[] usersId) throws Exception {
    	UserGroup group = new UserGroup();
    	group.setGroupName(name);
    	group.setDescription("");
    	long groupId = userGroupService.insert(group);
    	for (long userId : usersId) {
    		userGroupService.assignUserGroup(userId, groupId);
    	}
    	return groupId;
    }
    
    protected void createSpecialUserGroups() {
        userGroupService.insertSpecialUsersGroups();
    }
    
    protected long createUser(String name, Role role, String password, List<UserAttribute> attributes) throws Exception {
        User user = new User();
        user.setName(name);
        user.setRole(role);
        user.setNewPassword(password);
        user.setAttribute(attributes);
        return userService.insert(user);
    }
    
    protected long createUser(String name, Role role, String password, long groupId) throws Exception {
        User user = new User();
        user.setName(name);
        user.setRole(role);
        user.setNewPassword(password);
        long id = userService.insert(user);
        userGroupService.assignUserGroup(id, groupId);
        return id;
    }
    
    /**
     * @param name
     * @param role
     * @param password
     * @return long
     * @throws Exception
     */
    protected long createGroup(String name) throws Exception {
        UserGroup group = new UserGroup();
        group.setGroupName(name);

        return userGroupService.insert(group);
    }

    protected User buildFakeAdminUser() {
        User user = new User();
        user.setRole(Role.ADMIN);
        user.setName("ThisIsNotARealUser");
        return user;
    }
}
