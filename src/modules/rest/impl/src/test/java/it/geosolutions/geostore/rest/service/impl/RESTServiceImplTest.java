/*
 *  Copyright (C) 2018 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.rest.service.impl;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.GroupReservedNames;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.SecurityService;
import it.geosolutions.geostore.services.UserService;
import it.geosolutions.geostore.services.exception.BadRequestServiceEx;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.impl.RESTServiceImpl;

/**
 * This test checks that the resourceAccessRead and resourceAccessWrite
 * methods properly check and return expected canEdit and canWrite for a mock SecurityService. 
 * 
 * @author Lorenzo Natali, GeoSolutions S.a.s.
 *
 */
public class RESTServiceImplTest {
	TESTRESTServiceImpl restService;
	TestSecurityService securityService;
	TestUserService userService;
	User user;
	UserGroup group;
	UserGroup everyone;
	UserGroup extGroup;
	private class TestUserService implements UserService {

        @Override
        public long insert(User user) throws BadRequestServiceEx, NotFoundServiceEx {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public long update(User user) throws NotFoundServiceEx, BadRequestServiceEx {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public boolean delete(long id) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public User get(long id) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public User get(String name) throws NotFoundServiceEx {
            throw new NotFoundServiceEx(name);
        }

        @Override
        public List<User> getAll(Integer page, Integer entries) throws BadRequestServiceEx {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public List<User> getAll(Integer page, Integer entries, String nameLike,
                boolean includeAttributes) throws BadRequestServiceEx {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public long getCount(String nameLike) {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public void updateAttributes(long id, List<UserAttribute> attributes)
                throws NotFoundServiceEx {
            // TODO Auto-generated method stub
            
        }

        @Override
        public boolean insertSpecialUsers() {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public Collection<User> getByAttribute(UserAttribute attribute) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Collection<User> getByGroup(UserGroup group) {
            // TODO Auto-generated method stub
            return null;
        }
                
    }
	private class TestSecurityService implements SecurityService {
		private List<SecurityRule> userSecurityRules = null;
		private List<SecurityRule> groupSecurityRules = null;
		public void setUserSecurityRules(List<SecurityRule> userSecurityRules) {
			this.userSecurityRules = userSecurityRules;
		}

		public void setGroupSecurityRules(List<SecurityRule> groupSecurityRules) {
			this.groupSecurityRules = groupSecurityRules;
		}

		@Override
		public List<SecurityRule> getUserSecurityRule(String userName, long entityId) {
			return userSecurityRules;
		}

		@Override
		public List<SecurityRule> getGroupSecurityRule(List<String> groupNames, long entityId) {
			return groupSecurityRules;
		}
		
	}
    private class TESTRESTServiceImpl extends RESTServiceImpl {
    	private SecurityService securityService = null;
    	public void setSecurityService(SecurityService s) {
    		securityService = s;
    	}
		@Override
		protected SecurityService getSecurityService() {
			return securityService;
		}
    }
    
    private SecurityRule createSecurityRule(long id,User user,UserGroup group,boolean canRead,boolean canWrite) {
    	SecurityRule sr = new SecurityRule();
    	sr.setId(id);
    	sr.setUser(user);
    	sr.setGroup(group);
    	sr.setCanRead(canRead);
    	sr.setCanWrite(canWrite);
    	return sr;
    }
    
    @Before
    public void setUp () {
    	// set up services
    	 restService = new TESTRESTServiceImpl();
    	securityService = new TestSecurityService();
    	userService = new TestUserService();
        restService.setSecurityService(securityService);
        restService.setUserService(userService);
        
        // set up users and groups 
        user = new User();
        user.setName("TEST_USER");
        user.setId(new Long(100));
        user.setRole(Role.USER);
        everyone = new UserGroup();
        everyone.setId(new Long(200));
        everyone.setGroupName("everyone");
        group = new UserGroup();
        group.setGroupName("TEST_GROUP");
        group.setId(new Long(201));
        HashSet<UserGroup> groups = new HashSet<UserGroup>();
        groups.add(everyone);
        groups.add(group);
        user.setGroups(groups);
        user.setGroups(groups);
        
    }
    
    @Test
    public void testRulesReadWrite() {
        
        // set up rules for group write access
        List<SecurityRule> groupSecurityRules = new ArrayList<SecurityRule>();
        groupSecurityRules.add(createSecurityRule(1, null, group, true, true));
        
        List<SecurityRule> userSecurityRules = new ArrayList<SecurityRule>();
        userSecurityRules.add(createSecurityRule(1, user, null, true, false));
        securityService.setGroupSecurityRules(groupSecurityRules);
        securityService.setUserSecurityRules(userSecurityRules);
        
        assertTrue(restService.resourceAccessRead(user, 1));
        assertTrue(restService.resourceAccessWrite(user, 1));
        
        groupSecurityRules = new ArrayList<SecurityRule>();
        groupSecurityRules.add(createSecurityRule(1, null, group, true, false));
        
        userSecurityRules = new ArrayList<SecurityRule>();
        userSecurityRules.add(createSecurityRule(1, user, null, true, true));
        securityService.setGroupSecurityRules(groupSecurityRules);
        securityService.setUserSecurityRules(userSecurityRules);
        
        assertTrue(restService.resourceAccessRead(user, 1));
        assertTrue(restService.resourceAccessWrite(user, 1));
        
    }
    @Test
    public void testRulesReadOnly() {
        // set up rules 
        List<SecurityRule> groupSecurityRules = new ArrayList<SecurityRule>();
        groupSecurityRules.add(createSecurityRule(1, null, group, true, false));
        
        List<SecurityRule> userSecurityRules = new ArrayList<SecurityRule>();
        userSecurityRules.add(createSecurityRule(1, user, null, true, false));
        securityService.setGroupSecurityRules(groupSecurityRules);
        securityService.setUserSecurityRules(userSecurityRules);
        
        assertTrue(restService.resourceAccessRead(user, 1));
        assertFalse(restService.resourceAccessWrite(user, 1));
    }
    
    @Test
    public void testRulesAccessDenied() {
        // set up rules 
        List<SecurityRule> groupSecurityRules = new ArrayList<SecurityRule>();
        groupSecurityRules.add(createSecurityRule(1, null, group, false, false));
        
        List<SecurityRule> userSecurityRules = new ArrayList<SecurityRule>();
        userSecurityRules.add(createSecurityRule(1, user, null, false, false));
        securityService.setGroupSecurityRules(groupSecurityRules);
        securityService.setUserSecurityRules(userSecurityRules);
        
        assertFalse(restService.resourceAccessRead(user, 1));
        assertFalse(restService.resourceAccessWrite(user, 1));
    }
    
    @Test
    public void testIgnoreNotValidUserRules () {
    	// set up rules 
        List<SecurityRule> groupSecurityRules = new ArrayList<SecurityRule>();
        groupSecurityRules.add(createSecurityRule(1, null, group, false, false));
        
        List<SecurityRule> userSecurityRules = new ArrayList<SecurityRule>();
        userSecurityRules.add(createSecurityRule(1, null, group, true, false)); // this should be skipped
        userSecurityRules.add(createSecurityRule(1, user, null, true, true));
        securityService.setGroupSecurityRules(groupSecurityRules);
        securityService.setUserSecurityRules(userSecurityRules);
        
        assertTrue(restService.resourceAccessRead(user, 1));
        assertTrue(restService.resourceAccessWrite(user, 1));
    }
    
    @Test
    public void testGuestHasEveryoneGroup() {
        Principal principal = restService.createGuestPrincipal();
        assertTrue(principal instanceof UsernamePasswordAuthenticationToken);
        UsernamePasswordAuthenticationToken userPrincipal = (UsernamePasswordAuthenticationToken)principal;
        assertTrue(userPrincipal.getPrincipal() instanceof User);
        User user = (User)userPrincipal.getPrincipal();
        assertEquals(1, user.getGroups().size());
        assertEquals(GroupReservedNames.EVERYONE.groupName(), user.getGroups().iterator().next().getGroupName());
    }


	
    
    
}
;