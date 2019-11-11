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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import it.geosolutions.geostore.core.dao.ldap.impl.UserDAOImpl;
import it.geosolutions.geostore.core.dao.ldap.impl.UserGroupDAOImpl;
import it.geosolutions.geostore.core.ldap.IterableNamingEnumeration;
import it.geosolutions.geostore.core.ldap.MockContextSource;
import it.geosolutions.geostore.core.ldap.MockDirContextOperations;
import it.geosolutions.geostore.core.model.User;

public class UserDAOTest {
    
    DirContext buildContextForUsers() {
        return new DirContextAdapter() {
            @Override
            public NamingEnumeration<SearchResult> search(String name, String filter, SearchControls cons)
                    throws NamingException {
                if ("ou=users".equals(name)) {
                    if("cn=*".equals(filter)) {
                        SearchResult sr1 = new SearchResult("cn=*", null, new MockDirContextOperations() {

                            @Override
                            public String getNameInNamespace() {
                                return "cn=username,ou=users";
                            }

                            @Override
                            public String getStringAttribute(String name) {
                                if ("cn".equals(name)) {
                                    return "username";
                                }
                                return "";
                            }
                            
                        }, new BasicAttributes());
                        SearchResult sr2 = new SearchResult("cn=*", null, new MockDirContextOperations() {

                            @Override
                            public String getNameInNamespace() {
                                return "cn=username2,ou=users";
                            }

                            @Override
                            public String getStringAttribute(String name) {
                                if ("cn".equals(name)) {
                                    return "username2";
                                }
                                return "";
                            }
                            
                        }, new BasicAttributes());
                        return new IterableNamingEnumeration(Arrays.asList(sr1, sr2));
                    } else if ("(& (cn=*) (cn=username))".equals(filter)) {
                        SearchResult sr = new SearchResult("cn=*", null, new MockDirContextOperations() {

                            @Override
                            public String getNameInNamespace() {
                                return "cn=username,ou=users";
                            }

                            @Override
                            public String getStringAttribute(String name) {
                                if ("cn".equals(name)) {
                                    return "username";
                                }
                                return "";
                            }
                            
                        }, new BasicAttributes());
                        return new IterableNamingEnumeration(Collections.singletonList(sr));
                    } 
                }
                return new IterableNamingEnumeration(Collections.EMPTY_LIST);
            }
        };
    }
    
    DirContext buildContextForGroups(final String memberString) {
        return new DirContextAdapter() {
            @Override
            public NamingEnumeration<SearchResult> search(String name, String filter, SearchControls cons)
                    throws NamingException {
                if ("ou=groups".equals(name)) {
                    if ("(& (cn=*) (cn=group))".equals(filter)) {
                        SearchResult sr = new SearchResult("cn=*", null, new MockDirContextOperations() {

                            @Override
                            public String getNameInNamespace() {
                                return "cn=group,ou=groups";
                            }

                            @Override
                            public String getStringAttribute(String name) {
                                if ("cn".equals(name)) {
                                    return "group";
                                }
                                return "";
                            }

                            @Override
                            public String[] getStringAttributes(String name) {
                                if ("member".equals(name)) {
                                    return new String[] {memberString == null ? "username" : memberString};
                                }
                                return new String[] {};
                            }
                            
                            
                        }, new BasicAttributes());
                        return new IterableNamingEnumeration(Collections.singletonList(sr));
                    }
                }
                return new IterableNamingEnumeration(Collections.EMPTY_LIST);
            }
        };
    }
    
    @Test
    public void testFindAll() {
        UserDAOImpl userDAO = new UserDAOImpl(new MockContextSource(buildContextForUsers()));
        userDAO.setSearchBase("ou=users");
        List<User> users = userDAO.findAll();
        assertEquals(2, users.size());
        User user = users.get(0);
        assertEquals("username", user.getName());
    }
    
    @Test
    public void testSearchByname() {
        UserDAOImpl userDAO = new UserDAOImpl(new MockContextSource(buildContextForUsers()));
        userDAO.setSearchBase("ou=users");
        Search search = new Search(User.class);
        List<User> users = userDAO.search(search.addFilter(Filter.equal("name", "username")));
        assertEquals(1, users.size());
        User user = users.get(0);
        assertEquals("username", user.getName());
    }
    
    @Test
    public void testAttributesMapper() {
        UserDAOImpl userDAO = new UserDAOImpl(new MockContextSource(buildContextForUsers()));
        Map<String, String> mapper = new HashMap<String, String>();
        mapper.put("mail", "email");
        userDAO.setAttributesMapper(mapper);
        userDAO.setSearchBase("ou=users");
        List<User> users = userDAO.findAll();
        User user = users.get(0);
        assertEquals("username", user.getName());
        assertEquals(1, user.getAttribute().size());
        assertEquals("email", user.getAttribute().get(0).getName());
    }
    
    @Test
    public void testSearchByGroup() {
        UserGroupDAOImpl userGroupDAO = new UserGroupDAOImpl(new MockContextSource(buildContextForGroups(null)));
        userGroupDAO.setSearchBase("ou=groups");
        UserDAOImpl userDAO = new UserDAOImpl(new MockContextSource(buildContextForUsers()));
        userDAO.setUserGroupDAO(userGroupDAO);
        userDAO.setSearchBase("ou=users");
        userGroupDAO.setUserDAO(userDAO);
        Search search = new Search(User.class);
        List<User> users = userDAO.search(search.addFilter(Filter.some("groups", Filter.equal("groupName", "group"))));
        assertEquals(1, users.size());
        User user = users.get(0);
        assertEquals("username", user.getName());
    }
    
    @Test
    public void testMemberPattern() {
        UserGroupDAOImpl userGroupDAO = new UserGroupDAOImpl(new MockContextSource(buildContextForGroups("uid=username,ou=users")));
        userGroupDAO.setSearchBase("ou=groups");
        UserDAOImpl userDAO = new UserDAOImpl(new MockContextSource(buildContextForUsers()));
        userDAO.setUserGroupDAO(userGroupDAO);
        userDAO.setSearchBase("ou=users");
        userDAO.setMemberPattern("^uid=([^,]+).*$");
        userGroupDAO.setUserDAO(userDAO);
        Search search = new Search(User.class);
        List<User> users = userDAO.search(search.addFilter(Filter.some("groups", Filter.equal("groupName", "group"))));
        assertEquals(1, users.size());
        User user = users.get(0);
        assertEquals("username", user.getName());
    }
}
