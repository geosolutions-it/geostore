package it.geosolutions.geostore.rest.security;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.junit.Test;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.security.core.GrantedAuthority;
import it.geosolutions.geostore.core.ldap.IterableNamingEnumeration;
import it.geosolutions.geostore.core.ldap.MockContextSource;
import it.geosolutions.geostore.services.rest.security.GeoStoreLdapAuthoritiesPopulator;

public class GeoStoreLdapAuthoritiesPopulatorTest {
    
    DirContext ctx = new DirContextAdapter() {
        @Override
        public NamingEnumeration<SearchResult> search(String name, String filter, SearchControls cons)
                throws NamingException {
            if ("ou=groups".equals(name)) {
                if("(member=uid=bill,ou=people)".equals(filter)) {
                    List<SearchResult> groups = new ArrayList<SearchResult>();
                    SearchResult sr = new SearchResult("cn=group1", null, new MockDirContextOperations() {

                        @Override
                        public String getNameInNamespace() {
                            return "cn=group1,ou=groups";
                        }

                        @Override
                        public String getStringAttribute(String name) {
                            if ("cn".equals(name)) {
                                return "group1";
                            }
                            return "";
                        }
                        
                    }, new BasicAttributes());
                    return new IterableNamingEnumeration(Collections.singletonList(sr));
                } else if ("(member=cn=group1,ou=groups)".equals(filter)) {
                    List<SearchResult> groups = new ArrayList<SearchResult>();
                    SearchResult sr = new SearchResult("cn=parentgroup1", null, new MockDirContextOperations() {

                        @Override
                        public String getNameInNamespace() {
                            return "cn=parentgroup1,ou=groups";
                        }

                        @Override
                        public String getStringAttribute(String name) {
                            if ("cn".equals(name)) {
                                return "parentgroup1";
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
    
    @Test
    public void testNestedGroupsEnabled() {
        GeoStoreLdapAuthoritiesPopulator authoritiesPopulator = 
                new GeoStoreLdapAuthoritiesPopulator(new MockContextSource(ctx), "ou=groups", "ou=roles");
        authoritiesPopulator.setEnableHierarchicalGroups(true);
        Set<GrantedAuthority> authorities = authoritiesPopulator.getGroupMembershipRoles("uid=bill,ou=people", "bill");
        assertEquals(2, authorities.size());
    }
    
    @Test
    public void testNestedGroupsDisabled() {
        
        GeoStoreLdapAuthoritiesPopulator authoritiesPopulator = 
                new GeoStoreLdapAuthoritiesPopulator(new MockContextSource(ctx), "ou=groups", "ou=roles");
        authoritiesPopulator.setEnableHierarchicalGroups(false);
        Set<GrantedAuthority> authorities = authoritiesPopulator.getGroupMembershipRoles("uid=bill,ou=people", "bill");
        assertEquals(1, authorities.size());
    }
}
