/*
 *  Copyright (C) 2007 - 2011 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.core.dao;

import it.geosolutions.geostore.core.model.Category;
import it.geosolutions.geostore.core.model.SecurityRule;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;

import org.apache.log4j.Logger;
import org.junit.Test;

/** 
 * Class UserGroupDAOTest.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 *
 */
public class UserGroupDAOTest extends BaseDAOTest {

	final private static Logger LOGGER = Logger.getLogger(UserGroupDAOTest.class);

	/**
	 * @throws Exception
	 */
	@Test
	public void testPersistUserGroup() throws Exception {
		
		if(LOGGER.isDebugEnabled()){
			LOGGER.debug("Persisting UserGroup");
		}
		
        long securityId;
        long userId;
        long groupId;
        
        //
        // PERSIST
        //
        {
        	Category category = new Category();
        	category.setName("MAP");
        		
        	categoryDAO.persist(category);
        	
            assertEquals(1, categoryDAO.count(null));
            assertEquals(1, categoryDAO.findAll().size());    
            
	        UserGroup group = new UserGroup();
	        group.setGroupName("GROUP1");
	        
	        userGroupDAO.persist(group);
	        groupId = group.getId();
	        
            assertEquals(1, userGroupDAO.count(null));
            assertEquals(1, userGroupDAO.findAll().size());    
	        
	        User user = new User();
	        user.setGroup(group);
	        user.setName("USER_NAME");
	        user.setNewPassword("user");
	        user.setRole(Role.ADMIN);
	        
	        userDAO.persist(user);
	        userId = user.getId();
	        
            assertEquals(1, userDAO.count(null));
            assertEquals(1, userDAO.findAll().size());   
	        
            SecurityRule security = new SecurityRule();
            security.setCanRead(true);
            security.setCanWrite(true);
            security.setCategory(category);
            security.setGroup(group);
            security.setUser(user);
            
	        securityDAO.persist(security);
	        securityId = security.getId();

            assertEquals(1, securityDAO.count(null));
            assertEquals(1, securityDAO.findAll().size());   
        }

        //
        // LOAD, REMOVE, CASCADING
        //
        {
        	UserGroup loaded = userGroupDAO.find(groupId);
        	assertNotNull("Can't retrieve UserGroup", loaded);   
        	
        	userGroupDAO.removeById(groupId);
        	assertNull("User not deleted", userGroupDAO.find(groupId));     
        	
        	//
        	// Cascading
        	//
            assertNull("User not deleted", userDAO.find(userId));            
            assertNull("SecurityRule not deleted", securityDAO.find(securityId));    
        }
        
	}

}
