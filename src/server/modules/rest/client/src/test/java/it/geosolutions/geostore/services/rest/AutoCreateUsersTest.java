/*
 *  Copyright (C) 2007 - 2012 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import it.geosolutions.geostore.core.model.User;

import java.net.ConnectException;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Auto create users integration test. You need to override this properties on your GeoStore instance (use your geostore-ovr.properties): <br />
 * <br />
 * <code>
 * 	geostoreAuthInterceptor.autoCreateUsers=true
 * 	geostoreAuthInterceptor.newUsersRole=USER
 * 	geostoreAuthInterceptor.newUsersPassword=NONE
 * 	geostoreAuthInterceptor.newUsersPasswordHeader=
 * </code>
 * 
 * @author adiaz (alejandro.diaz at geo-solutions.it)
 */
public class AutoCreateUsersTest {
    private final static Logger LOGGER = Logger.getLogger(AutoCreateUsersTest.class);

    AdministratorGeoStoreClient geoStoreClient;

    final String DEFAULTCATEGORYNAME = "TestCategory1";

    protected AdministratorGeoStoreClient createAdministratorClient() {
        geoStoreClient = new AdministratorGeoStoreClient();
        geoStoreClient.setGeostoreRestUrl("http://localhost:9191/geostore/rest");
        geoStoreClient.setUsername("admin");
        geoStoreClient.setPassword("admin");
        return geoStoreClient;
    }

    protected boolean pingGeoStore(GeoStoreClient client) {
        try {
            client.getCategories();
            return true;
        } catch (Exception ex) {
            LOGGER.debug("Error connecting to GeoStore", ex);
            // ... and now for an awful example of heuristic.....
            Throwable t = ex;
            while (t != null) {
                if (t instanceof ConnectException) {
                    LOGGER.warn("Testing GeoStore is offline");
                    return false;
                }
                t = t.getCause();
            }
            throw new RuntimeException("Unexpected exception: " + ex.getMessage(), ex);
        }
    }

    @Before
    public void before() throws Exception {
        geoStoreClient = createAdministratorClient();
        assumeTrue(pingGeoStore(geoStoreClient));
    }

    /**
     * Test auto create users with GeoStore client
     */
    @Test
    @Ignore("Ignore this test until the user autocreation won't be restored")
    public void testAutoCreateUsers() {

        geoStoreClient.setUsername("test");
        geoStoreClient.setPassword("");

        try {
            User user = geoStoreClient.getUserDetails();
            assertNotNull(user);
            assertTrue(user.getPassword() == null || user.getPassword().equals(""));
        } catch (Exception e) {
            fail("Unable to create user");
        }

    }

}
