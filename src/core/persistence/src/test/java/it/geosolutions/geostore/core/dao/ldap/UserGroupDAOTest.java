/*
 *  Copyright (C) 2019 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.core.dao.ldap;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import org.junit.Test;
import org.springframework.ldap.core.DirContextAdapter;
import com.googlecode.genericdao.search.Filter;
import com.googlecode.genericdao.search.Search;
import it.geosolutions.geostore.core.dao.ldap.impl.UserGroupDAOImpl;
import it.geosolutions.geostore.core.ldap.IterableNamingEnumeration;
import it.geosolutions.geostore.core.ldap.MockContextSource;
import it.geosolutions.geostore.core.ldap.MockDirContextOperations;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;

public class UserGroupDAOTest extends BaseDAOTest {
    
    
    @Test
    public void testFindAll() {
        UserGroupDAOImpl userGroupDAO = new UserGroupDAOImpl(new MockContextSource(buildContextForGroups()));
        userGroupDAO.setSearchBase("ou=groups");
        List<UserGroup> groups = userGroupDAO.findAll();
        assertEquals(2, groups.size());
        UserGroup group = groups.get(0);
        assertEquals("group", group.getGroupName());
    }
    
    @Test
    public void testSearchByname() {
        UserGroupDAOImpl userGroupDAO = new UserGroupDAOImpl(new MockContextSource(buildContextForGroups()));
        userGroupDAO.setSearchBase("ou=groups");
        Search search = new Search(User.class);
        List<UserGroup> groups = userGroupDAO.search(search.addFilter(Filter.equal("groupName", "group")));
        assertEquals(1, groups.size());
        UserGroup group = groups.get(0);
        assertEquals("group", group.getGroupName());
    }
    
    @Test
    public void testAddEveryOne() {
        UserGroupDAOImpl userGroupDAO = new UserGroupDAOImpl(new MockContextSource(buildContextForGroups()));
        userGroupDAO.setSearchBase("ou=groups");
        userGroupDAO.setAddEveryOneGroup(true);
        List<UserGroup> groups = userGroupDAO.findAll();
        assertEquals(3, groups.size());
        UserGroup group = groups.get(2);
        assertEquals("everyone", group.getGroupName());
    }
}
