/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2002-2011, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package it.geosolutions.geostore.services.rest;

import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.rest.impl.RESTCategoryServiceImpl;

import javax.ws.rs.core.Response;

import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.ldap.userdetails.LdapUserDetailsImpl;

/**
 * @author Alessio
 * 
 */
public class SecurityTest extends BaseAuthenticationTest {

    private final static String ENDPOINT_ADDRESS = "http://localhost:9000/rest/categories";

    private final static String WADL_ADDRESS = ENDPOINT_ADDRESS + "?_wadl&_type=xml";

    private static Server server;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @BeforeClass
    public static void initialize() throws Exception {
        try {
            startServer();
            waitForWADL();
        } catch (Exception e) {
            throw e;
        } finally {
            destroy();
        }
    }

    @AfterClass
    public static void destroy() throws Exception {

        if (server != null) {
            server.stop();
            server.destroy();
        }
    }

    private static void startServer() throws Exception {
        JAXRSServerFactoryBean sf = (JAXRSServerFactoryBean) context
                .getBean("geoStoreRESTCategory");

        // sf.setServiceBeans( Arrays.< Object >asList( new RESTCategoryServiceImpl() ) );
        // sf.setResourceClasses(RESTCategoryServiceImpl.class);

        // add custom providers if any
        // List<Object> providers = new ArrayList<Object>();
        // providers.add(new JSONProvider());
        // sf.setProviders(providers);

        // sf.setResourceProvider(RESTCategoryServiceImpl.class, new SingletonResourceProvider(new RESTCategoryServiceImpl(), true));

        // sf.setAddress(ENDPOINT_ADDRESS);

        server = sf.create();
    }

    // Optional step - may be needed to ensure that by the time individual
    // tests start running the endpoint has been fully initialized
    private static void waitForWADL() throws Exception {
        WebClient client = WebClient.create(WADL_ADDRESS);
        // wait for 20 secs or so
        for (int i = 0; i < 2; i++) {
            Thread.currentThread();
            Thread.sleep(1000);
            Response response = client.get();
            if (response.getStatus() == 200) {
                break;
            }
        }
        // no WADL is available yet - throw an exception or give tests a chance to run anyway
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testSuite() {
        springAuthenticationTest();
        webClientAccessTest();
        proxyAccessTest();
    }

    protected void springAuthenticationTest() {
        doAutoLogin("admin", "admin", null);

        assertNotNull(SecurityContextHolder.getContext());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        authentication.getName();

        assertEquals("admin", authentication.getCredentials());

        Object principal = authentication.getPrincipal();
        assertNotNull(principal);
        
        if (principal instanceof User) {
        	User user = (User) principal;
        	
        	assertEquals("admin", user.getName());
        } else if (principal instanceof LdapUserDetailsImpl) {
        	LdapUserDetailsImpl userDetails = (LdapUserDetailsImpl) principal;

        	assertEquals("uid=admin,ou=people,dc=geosolutions,dc=it", userDetails.getDn());
        }
        
        assertEquals(authentication.getAuthorities().size(), 1);

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            assertEquals("ROLE_ADMIN", authority.getAuthority());
        }

    }

    // protected void testHTTPClientAccess() {
    // final String user = "admin";
    // final String password = "admin";
    // int expectedStatus = 200;
    //
    // GetMethod get = new GetMethod(ENDPOINT_ADDRESS);
    // get.setRequestHeader("Accept", "application/xml");
    // get.setRequestHeader("Authorization", "Basic " + base64Encode(user + ":" + password));
    // HttpClient httpClient = new HttpClient();
    // try {
    // int result = httpClient.executeMethod(get);
    // assertEquals(expectedStatus, result);
    // if (expectedStatus == 200) {
    // String content = getStringFromInputStream(get.getResponseBodyAsStream());
    // String resource = "/org/apache/cxf/systest/jaxrs/resources/expected_get_book123.txt";
    // InputStream expected = getClass().getResourceAsStream(resource);
    // assertEquals("Expected value is wrong", getStringFromInputStream(expected), content);
    // }
    // } catch (HttpException e) {
    // LOGGER.error(e.getMessage(), e);
    // } catch (IOException e) {
    // LOGGER.error(e.getMessage(), e);
    // } catch (Exception e) {
    // LOGGER.error(e.getMessage(), e);
    // } finally {
    // get.releaseConnection();
    // }
    //
    // }

    protected void webClientAccessTest() {
        WebClient client = WebClient.create(ENDPOINT_ADDRESS, "admin", "admin", null);
        client.accept("application/json");
        client.path("category/1");

        assertNotNull(client);

        // RESTCategory category = client.get(RESTCategory.class);
        // System.out.println(category.getName());
    }

    protected void proxyAccessTest() {
        doAutoLogin("admin", "admin", null);
        RESTCategoryServiceImpl client = JAXRSClientFactory.create(ENDPOINT_ADDRESS,
                RESTCategoryServiceImpl.class);

        assertNotNull(client);

        // SecurityContext sc = null;
        // client.get(sc, 1L);
    }
}
