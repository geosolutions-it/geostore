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

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;

import org.apache.log4j.Logger;
import org.junit.Test;

/** 
 * Class RoleDAOTest.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 *
 */
public class UserAttributeDAOTest extends BaseDAOTest {

	final private static Logger LOGGER = Logger.getLogger(UserAttributeDAOTest.class);

	/**
	 * @throws Exception
	 */
	@Test
	public void testPersistRole() throws Exception {
		
        final String VALUE1 = "value1";
        final String VALUE2 = "value2";
        
		if(LOGGER.isDebugEnabled()){
			LOGGER.debug("Persisting Role");
		}
		
        long attributeId;
        
        //
        // PERSIST
        //
        { 
	        UserGroup group = new UserGroup();
	        group.setGroupName("GROUP1");
	        
	        userGroupDAO.persist(group);
	        
            assertEquals(1, userGroupDAO.count(null));
            assertEquals(1, userGroupDAO.findAll().size());    
	        
	        User user = new User();
	        user.setGroup(group);
	        user.setName("USER_NAME");
	        user.setNewPassword("user");
	        user.setRole(Role.ADMIN);
	        
	        userDAO.persist(user);
	        
            assertEquals(1, userDAO.count(null));
            assertEquals(1, userDAO.findAll().size());   
            
	        UserAttribute attribute = new UserAttribute();
	        attribute.setName("attr1");
	        attribute.setValue(VALUE1);
	        attribute.setUser(user);
	       
	        userAttributeDAO.persist(attribute);
	        attributeId = attribute.getId();
	        
            assertEquals(1, userAttributeDAO.count(null));
            assertEquals(1, userAttributeDAO.findAll().size());   
        }

        //
        // UPDATE AND LOAD
        //
        {
        	UserAttribute loaded = userAttributeDAO.find(attributeId);
            assertNotNull("Can't retrieve UserAttribute", loaded);
            
            assertEquals(VALUE1, loaded.getValue());
            loaded.setValue(VALUE2);
            userAttributeDAO.merge(loaded);
        }

        {
        	UserAttribute loaded = userAttributeDAO.find(attributeId);
            assertNotNull("Can't retrieve UserAttribute", loaded);
            assertEquals(VALUE2, loaded.getValue());
        }
        
        //
        // LOAD, REMOVE
        //
        {
        	UserAttribute loaded = userAttributeDAO.find(attributeId);
        	assertNotNull("Can't retrieve Role", loaded);   
        	
        	userAttributeDAO.removeById(attributeId);
        	assertNull("Role not deleted", userAttributeDAO.find(attributeId));     
        }

	}

}
