/*
 *  Copyright (C) 2015 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.rest.security;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import java.util.Collections;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.security.UserLdapAuthenticationProvider;
import it.geosolutions.geostore.services.rest.utils.MockedUserGroupService;
import it.geosolutions.geostore.services.rest.utils.MockedUserService;

public class UserLdapAuthenticationProviderTest {
    private static final String TEST_GROUP = "testgroup";
    
    private UserLdapAuthenticationProvider provider;
    private MockedUserService userService;
    private MockedUserGroupService userGroupService;
    
    @Before
    public void setUp() {
        provider = new UserLdapAuthenticationProvider(new MockLdapAuthenticator(), new MockLdapAuthoritiesPopulator(){

            @Override
            public Set<GrantedAuthority> getAllGroups() {
                return Collections.singleton((GrantedAuthority)new SimpleGrantedAuthority(TEST_GROUP));
            }
            
        });
        userService = new MockedUserService();
        userGroupService = new MockedUserGroupService();
        provider.setUserService(userService);
        provider.setUserGroupService(userGroupService);
    }
    
    @Test
    public void testNullPassword() throws NotFoundServiceEx {
        provider.authenticate(new UsernamePasswordAuthenticationToken("user", "password"));
        User user = userService.get("user");
        assertNotNull(user);
        assertNull(user.getPassword());
    }
    
    @Test
    public void testSynchronizeGroups() throws BadRequestServiceEx {
        assertNull(userGroupService.get(TEST_GROUP));
        
        provider.synchronizeGroups();
        
        assertNotNull(userGroupService.get(TEST_GROUP));
    }
}
