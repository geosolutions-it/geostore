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
import it.geosolutions.geostore.core.model.Resource;
import it.geosolutions.geostore.core.model.SecurityRule;

import java.util.Date;

import org.apache.log4j.Logger;
import org.junit.Test;

/** 
 * Class SecurityDAOTest.
 * 
 * @author Tobia di Pisa (tobia.dipisa at geo-solutions.it)
 *
 */
public class SecurityDAOTest extends BaseDAOTest {

	final private static Logger LOGGER = Logger.getLogger(SecurityDAOTest.class);

	/**
	 * @throws Exception
	 */
	@Test
	public void testPersistSecurity() throws Exception {
		
		final String NAME = "NAME";
		
		if(LOGGER.isDebugEnabled()){
			LOGGER.debug("Persisting Security");
		}
		
        long categoryId;
        long resourceId;
        long securityId;
        
        //
        // PRE-PERSIST
        //
        {
        	Category category = new Category();
        	category.setName("MAP");
        		
        	categoryDAO.persist(category);
        	categoryId = category.getId();
        	
            assertEquals(1, categoryDAO.count(null));
            assertEquals(1, categoryDAO.findAll().size());    
            
            Resource resource = new Resource();
            resource.setName(NAME);
            resource.setCreation(new Date());
            resource.setCategory(category);
            
            resourceDAO.persist(resource);
            resourceId = resource.getId();

            assertEquals(1, resourceDAO.count(null));
            assertEquals(1, resourceDAO.findAll().size());   
            
            SecurityRule security = new SecurityRule();
            security.setCanRead(true);
            security.setCanWrite(true);
            security.setCategory(category);
            security.setResource(resource);
            
	        try{
	        	securityDAO.persist(security);
	        	fail("Exception not trapped");
        	} catch(Exception exc) {
        	   if(LOGGER.isDebugEnabled()){
        		   LOGGER.debug("OK: exception trapped", exc);
        	   }
        	}
        }
        
        //
        // PERSIST
        //
        {         
            SecurityRule security = new SecurityRule();
            security.setCanRead(true);
            security.setCanWrite(true);
            security.setResource(resourceDAO.find(resourceId));
            
            securityDAO.persist(security);
            securityId = security.getId();
            
            assertEquals(1, securityDAO.count(null));
            assertEquals(1, securityDAO.findAll().size());    
        }
        
        //
        // UPDATE
        //
        {
        	SecurityRule loaded = securityDAO.find(securityId);
            assertNotNull("Can't retrieve Security", loaded);  
            
            assertTrue(loaded.isCanWrite());
            
            loaded.setCanWrite(false);            
            securityDAO.merge(loaded);
            
            loaded = securityDAO.find(securityId);
        	assertNotNull("Can't retrieve Security", loaded);   
            assertFalse(loaded.isCanWrite());
        }

        //
        // LOAD, REMOVE
        //
        {
        	securityDAO.removeById(securityId);
            assertNull("Security not deleted", securityDAO.find(categoryId));            
        }
        
	}

}
