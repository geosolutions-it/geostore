/*
 *  Copyright (C) 2016 GeoSolutions S.A.S.
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

import it.geosolutions.geostore.core.dao.ResourceDAO;
import it.geosolutions.geostore.core.dao.UserDAO;
import it.geosolutions.geostore.core.model.*;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.*;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.RESTResourceService;
import it.geosolutions.geostore.services.rest.RESTUserService;
import it.geosolutions.geostore.services.rest.model.RESTCategory;
import it.geosolutions.geostore.services.rest.model.RESTResource;
import it.geosolutions.geostore.services.rest.model.SecurityRuleList;
import it.geosolutions.geostore.services.rest.utils.Convert;
import org.apache.commons.collections.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Class ServiceTestBase.
 *
 * @author ETj (etj at geo-solutions.it)
 */
public class ServiceTestBase {


    protected static RESTResourceService restResourceService;
    protected static RESTUserService restUserService;

    protected static StoredDataService storedDataService;
    protected static ResourceService resourceService;
    protected static CategoryService categoryService;
    protected static UserService userService;
    protected static UserGroupService userGroupService;

    protected static ResourceDAO resourceDAO;
    protected static UserDAO userDAO;

    protected static ClassPathXmlApplicationContext ctx = null;
    protected final Logger LOGGER = LogManager.getLogger(getClass());
    @Rule
    public TestName testName = new TestName();


    /**
     *
     */
    public ServiceTestBase() {

        synchronized (ServiceTestBase.class) {
            if (ctx == null) {
                String[] paths = {"classpath*:applicationContext.xml"
                        // ,"applicationContext-test.xml"
                };
                ctx = new ClassPathXmlApplicationContext(paths);

                restResourceService = ctx.getBean("restResourceService", RESTResourceService.class);
                restUserService = ctx.getBean("restUserService", RESTUserService.class);

                storedDataService = ctx.getBean("storedDataService", StoredDataService.class);
                resourceService = (ResourceService) ctx.getBean("resourceService");
                categoryService = (CategoryService) ctx.getBean("categoryService");
                userService = (UserService) ctx.getBean("userService");
                userGroupService = (UserGroupService) ctx.getBean("userGroupService");

                resourceDAO = (ResourceDAO) ctx.getBean("resourceDAO");
                userDAO = (UserDAO) ctx.getBean("userDAO");
            }
        }
    }

    @Before
    protected void setUp() throws Exception {
        testCheckServices();

        LOGGER.info("################ Running " + getClass().getSimpleName() + "::" + testName.getMethodName());
        removeAll();
    }

    /**
     *
     */
    public void testCheckServices() {
        assertNotNull(restResourceService);
        assertNotNull(restUserService);

        assertNotNull(storedDataService);
        assertNotNull(resourceService);
        assertNotNull(categoryService);
        assertNotNull(userService);
        assertNotNull(userGroupService);

        assertNotNull(resourceDAO);
        assertNotNull(userDAO);
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

            boolean ret = userGroupService.delete(item.getId());
            assertTrue("Group not removed", ret);
        }

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
     * @param data
     * @param resource
     * @return long
     * @throws Exception
     */
    protected long createData(String data, Resource resource) throws Exception {
        return storedDataService.update(resource.getId(), data);
    }

    /**
     * @param name
     * @param description
     * @param catName
     * @param advertised
     * @return long
     * @return long
     * @throws Exception
     * @throws Exception
     */
    protected long createResource(String name, String description, String catName, boolean advertised) throws Exception {
        Category category = new Category();
        category.setName(catName);

        categoryService.insert(category);

        Resource resource = new Resource();
        resource.setName(name);
        resource.setDescription(description);
        resource.setCategory(category);
        resource.setCreator("USER1");
        resource.setEditor("USER2");
        resource.setAdvertised(advertised);

        return resourceService.insert(resource);
    }

    protected long restCreateResource(String name, String description, String catName, long userId, boolean advertised) throws Exception {
        RESTResource resource = new RESTResource();
        resource.setName(name);
        resource.setDescription(description);
        resource.setCategory(new RESTCategory(catName));
        resource.setAdvertised(advertised);

        SecurityContext sc = new SimpleSecurityContext(userId);

        return restResourceService.insert(sc, resource);
    }

    protected long restCreateResource(String name, String description, String catName, long userId, SecurityRuleList rules, boolean advertised) throws Exception {
        long resId = restCreateResource(name, description, catName, userId, advertised);

        SecurityContext sc = new SimpleSecurityContext(userId);

        restResourceService.updateSecurityRules(sc, resId, rules);
        return resId;
    }

    protected long createResource(String name, String description, Category category, boolean advertised) throws Exception {
        Resource resource = new Resource();
        resource.setName(name);
        resource.setDescription(description);
        resource.setCategory(category);
        resource.setCreator("USER1");
        resource.setEditor("USER2");
        resource.setAdvertised(advertised);

        return resourceService.insert(resource);
    }

    /**
     * @param name
     * @param description
     * @param catName
     * @param rules
     * @param advertised
     * @return long
     * @throws Exception
     */
    protected long createResource(String name, String description, String catName, List<SecurityRule> rules, boolean advertised) throws Exception {

        Category category = new Category();
        category.setName(catName);

        categoryService.insert(category);

        Resource resource = new Resource();
        resource.setName(name);
        resource.setDescription(description);
        resource.setCategory(category);
        resource.setSecurity(rules);
        resource.setCreator("USER1");
        resource.setEditor("USER2");
        resource.setAdvertised(advertised);

        return resourceService.insert(resource);
    }

    /**
     * @param name
     * @return long
     * @throws Exception
     */
    protected Category createCategory(String name) throws Exception {
        Category category = new Category();
        category.setName(name);

        long id = categoryService.insert(category);
        return categoryService.get(id);
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

        UserAttribute attr = new UserAttribute();
        attr.setName("attname");
        attr.setValue("attvalue");
        user.setAttribute(Collections.singletonList(attr));

        return userService.insert(user);
    }

    protected long restCreateUser(String name, Role role, Set<UserGroup> groups, String password) throws Exception {
        User user = new User();
        user.setName(name);
        user.setRole(role);
        if (groups != null && !groups.isEmpty())
            user.setGroups(groups);
        user.setNewPassword(password);

        UserAttribute attr = new UserAttribute();
        attr.setName("attname");
        attr.setValue("RESTattvalue");
        user.setAttribute(Collections.singletonList(attr));

        return restUserService.insert(null, user);
    }

    protected <T> T getTargetObject(Object proxy, Class<T> targetClass) throws Exception {
        if (AopUtils.isJdkDynamicProxy(proxy)) {
            return (T) ((Advised) proxy).getTargetSource().getTarget();
        } else {
            return (T) proxy; // expected to be cglib proxy then, which is simply a specialized class
        }
    }

    protected long createUser(String name, Role role, String password, List<UserAttribute> attributes) throws Exception {
        User user = new User();
        user.setName(name);
        user.setRole(role);
        user.setNewPassword(password);
        user.setAttribute(attributes);
        return userService.insert(user);
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

    protected RESTResource createRESTResource(Resource resource) {
        RESTResource ret = new RESTResource();
        ret.setCategory(new RESTCategory(resource.getCategory().getName()));
        ret.setName(resource.getName());
        ret.setDescription(resource.getDescription());
        ret.setMetadata(resource.getMetadata());
        ret.setCreator(resource.getCreator());
        ret.setEditor(resource.getEditor());
        if (resource.getData() != null)
            ret.setData(resource.getData().getData());
        if (CollectionUtils.isNotEmpty(resource.getAttribute()))
            ret.setAttribute(Convert.convertToShortAttributeList(resource.getAttribute()));
        return ret;
    }

    protected User buildFakeAdminUser() {
        User user = new User();
        user.setRole(Role.ADMIN);
        user.setName("ThisIsNotARealUser");
        return user;
    }

    class SimpleSecurityContext implements SecurityContext {

        private Principal userPrincipal;

        public SimpleSecurityContext() {
        }

        public SimpleSecurityContext(long userId) {
            userPrincipal = new UsernamePasswordAuthenticationToken(userDAO.find(userId), null);
        }

        @Override
        public Principal getUserPrincipal() {
            return userPrincipal;
        }

        public void setUserPrincipal(Principal userPrincipal) {
            this.userPrincipal = userPrincipal;
        }

        @Override
        public boolean isUserInRole(String role) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public boolean isSecure() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public String getAuthenticationScheme() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }
}
