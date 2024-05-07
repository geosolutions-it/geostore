/*
 *  Copyright (C) 2007-2012 GeoSolutions S.A.S.
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

import it.geosolutions.geostore.core.model.Attribute;
import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.UserGroupAttribute;
import it.geosolutions.geostore.core.model.enums.DataType;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.dto.ShortResource;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.DuplicatedResourceNameServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** @author DamianoG */
public class UserGroupServiceImplTest extends ServiceTestBase {

    @Test
    public void testGroupCRUDOperations() throws BadRequestServiceEx, NotFoundServiceEx {
        UserGroup ug1 = new UserGroup();
        ug1.setGroupName("ug1");
        userGroupService.insert(ug1);
        UserGroup ug2 = new UserGroup();
        ug2.setGroupName("ug2");
        userGroupService.insert(ug2);
        UserGroup ug3 = new UserGroup();
        ug3.setGroupName("ug3");
        userGroupService.insert(ug3);
        UserGroup ug4 = new UserGroup();
        ug4.setGroupName("ug4");
        userGroupService.insert(ug4);

        List<UserGroup> groups = userGroupService.getAll(null, null);
        assertEquals("Saved 4 groups but retrieved less or more groups...", 4, groups.size());

        userGroupService.delete(ug4.getId());
        groups = userGroupService.getAll(null, null);
        assertEquals("Removed 1 group of 4 but retrieved less or more groups...", 3, groups.size());
    }

    @Test
    public void testAssignGroupToUser() throws BadRequestServiceEx, NotFoundServiceEx {
        UserGroup ug1 = new UserGroup();
        ug1.setGroupName("ug1");
        long gid = userGroupService.insert(ug1);

        User u = new User();
        u.setName("u1");
        u.setPassword("password");
        u.setRole(Role.USER);
        long uid = userService.insert(u);

        userGroupService.assignUserGroup(uid, gid);

        User uu = userService.get(uid);
        Set<UserGroup> groups = uu.getGroups();
        assertEquals("GroupSize must be 1!", 1, groups.size());
    }

    /**
     * Test the case of updating permissions on rules based on resource/group when the group isn't
     * assigned yet to the resource Test the case of updating permissions on rules based on
     * resource/group when the group is already assigned to the resource
     *
     * @throws BadRequestServiceEx
     * @throws NotFoundServiceEx
     * @throws DuplicatedResourceNameServiceEx
     */
    @Test
    public void testChangeGroupPermissionsOnResources()
            throws BadRequestServiceEx, NotFoundServiceEx, DuplicatedResourceNameServiceEx {

        UserGroup ug1 = new UserGroup();
        ug1.setGroupName("ug1");
        long gid = userGroupService.insert(ug1);

        User u = new User();
        u.setName("u1");
        u.setPassword("password");
        u.setRole(Role.USER);
        Set<UserGroup> group = new HashSet<UserGroup>();
        group.add(ug1);
        u.setGroups(group);
        long uid = userService.insert(u);

        Resource r = new Resource();
        List<Attribute> attributeList = new ArrayList<Attribute>();
        Attribute a1 = new Attribute();
        a1.setTextValue("a1");
        a1.setType(DataType.STRING);
        a1.setName("a1");
        attributeList.add(a1);
        r.setAttribute(attributeList);
        Category cat = new Category();
        cat.setName("cat1");
        r.setCategory(cat);
        r.setCreation(new Date());
        r.setName("r1");
        categoryService.insert(cat);
        long id = resourceService.insert(r);
        r = resourceService.get(id);

        List<Long> idList = new ArrayList<Long>();
        idList.add(id);
        List<Resource> resourcelist = resourceDAO.findResources(idList);
        List<SecurityRule> listSecurity = resourcelist.get(0).getSecurity();
        assertEquals(0, listSecurity.size()); // shouldn't be any rule...

        List<Long> listR = new ArrayList<Long>();
        listR.add(r.getId());

        List<ShortResource> listsr =
                userGroupService.updateSecurityRules(ug1.getId(), listR, true, true);
        assertEquals(1, listsr.size());
        assertTrue("Expected TRUE", listsr.get(0).isCanDelete());
        assertTrue("Expected TRUE", listsr.get(0).isCanEdit());

        idList = new ArrayList<Long>();
        idList.add(id);
        resourcelist = resourceDAO.findResources(idList);
        listSecurity = resourcelist.get(0).getSecurity();
        assertEquals(1, listSecurity.size()); // now the rules should be 1: one for the group added

        listsr = userGroupService.updateSecurityRules(ug1.getId(), listR, false, false);
        assertEquals(1, listsr.size());
        assertTrue("Expected FALSE", !listsr.get(0).isCanDelete());
        assertTrue("Expected FALSE", !listsr.get(0).isCanEdit());
    }

    /**
     * Test the insertion of a UserGroup with UserGroupAttributes.
     *
     * @throws BadRequestServiceEx
     */
    @Test
    public void testInsertGroupWithAttributes() throws BadRequestServiceEx {
        UserGroup group = new UserGroup();
        group.setGroupName("GroupWithAttrs");
        UserGroupAttribute attribute = new UserGroupAttribute();
        attribute.setName("attr1");
        attribute.setValue("value,value2,value3");

        UserGroupAttribute attribute2 = new UserGroupAttribute();
        attribute2.setName("attr2");
        attribute2.setValue("value4,value5,value6");

        group.setAttributes(Arrays.asList(attribute, attribute2));

        long id = userGroupService.insert(group);

        UserGroup ug = userGroupService.get(id);
        List<UserGroupAttribute> attributes = ug.getAttributes();
        assertEquals(2, attributes.size());
        assertEquals("attr1", attributes.get(0).getName());
        assertEquals("attr2", attributes.get(1).getName());
    }

    /**
     * Test the updating of UserGroupAttributes.
     *
     * @throws BadRequestServiceEx
     * @throws NotFoundServiceEx
     */
    @Test
    public void testUpdateGroup() throws BadRequestServiceEx, NotFoundServiceEx {
        UserGroup group = new UserGroup();
        group.setGroupName("GroupWithAttrs2");
        UserGroupAttribute attribute = new UserGroupAttribute();
        attribute.setName("attr1");
        attribute.setValue("value,value2,value3");

        UserGroupAttribute attribute2 = new UserGroupAttribute();
        attribute2.setName("attr2");
        attribute2.setValue("value4,value5,value6");

        group.setAttributes(Arrays.asList(attribute, attribute2));

        long id = userGroupService.insert(group);

        UserGroup toUpdate = userGroupService.get(id);
        toUpdate.setDescription("Updated Description");

        long idUpdated = userGroupService.update(toUpdate);

        UserGroup updated = userGroupService.get(idUpdated);

        assertEquals(id, idUpdated);
        assertEquals("Updated Description", updated.getDescription());
    }

    @Test
    public void testUpdateUserGroupAttributes() throws BadRequestServiceEx, NotFoundServiceEx {
        UserGroup group = new UserGroup();
        group.setGroupName("GroupWithAttrs2");
        UserGroupAttribute attribute = new UserGroupAttribute();
        attribute.setName("attr1");
        attribute.setValue("value,value2,value3");

        UserGroupAttribute attribute2 = new UserGroupAttribute();
        attribute2.setName("attr2");
        attribute2.setValue("value4,value5,value6");

        group.setAttributes(Arrays.asList(attribute, attribute2));

        long id = userGroupService.insert(group);

        UserGroupAttribute attributeToUpdate1 = new UserGroupAttribute();
        attributeToUpdate1.setName(attribute.getName());
        attributeToUpdate1.setValue(attribute.getValue());
        UserGroupAttribute attributeToUpdate2 = new UserGroupAttribute();
        attributeToUpdate2.setName("updated");
        attributeToUpdate2.setValue("updatedValue");
        List<UserGroupAttribute> attributes = Arrays.asList(attributeToUpdate1, attributeToUpdate2);

        userGroupService.updateAttributes(id, attributes);
        UserGroup groupUpdated = userGroupService.get(id);
        List<UserGroupAttribute> updatedList = groupUpdated.getAttributes();
        assertTrue(updatedList.stream().anyMatch(g -> g.getName().equals(attribute.getName())));
        assertTrue(
                updatedList.stream()
                        .anyMatch(g -> g.getName().equals(attributeToUpdate2.getName())));
        assertFalse(updatedList.stream().anyMatch(g -> g.getName().equals(attribute2.getName())));
    }

    @Test
    public void testgetByAttributes() throws BadRequestServiceEx {
        UserGroup group = new UserGroup();
        group.setGroupName("GroupWithAttrs");
        UserGroupAttribute attribute = new UserGroupAttribute();
        attribute.setName("organization");
        attribute.setValue("value");

        UserGroupAttribute attribute2 = new UserGroupAttribute();
        attribute2.setName("attr2");
        attribute2.setValue("value4,value5,value6");

        group.setAttributes(Arrays.asList(attribute, attribute2));

        long id = userGroupService.insert(group);

        UserGroup group2 = new UserGroup();
        group2.setGroupName("GroupWithAttrs2");
        UserGroupAttribute attribute21 = new UserGroupAttribute();
        attribute21.setName("Organization");
        attribute21.setValue("value");

        UserGroupAttribute attribute22 = new UserGroupAttribute();
        attribute22.setName("attr2");
        attribute22.setValue("value4,value5,value6");

        group2.setAttributes(Arrays.asList(attribute21, attribute22));

        userGroupService.insert(group2);
        UserGroupAttribute groupAttribute = new UserGroupAttribute();
        groupAttribute.setName("organization");
        groupAttribute.setValue("value");
        Collection<UserGroup> groups =
                userGroupService.findByAttribute("organization", Arrays.asList("value"), true);
        assertEquals(2, groups.size());

        groups = userGroupService.findByAttribute("organization", Arrays.asList("value"), false);
        assertEquals(1, groups.size());
    }
}
