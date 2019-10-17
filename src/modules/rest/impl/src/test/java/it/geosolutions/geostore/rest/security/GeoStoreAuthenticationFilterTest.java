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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserAttribute;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.core.security.MapExpressionUserMapper;
import it.geosolutions.geostore.services.exception.NotFoundServiceEx;
import it.geosolutions.geostore.services.rest.security.GeoStoreRequestHeadersAuthenticationFilter;
import it.geosolutions.geostore.services.rest.utils.MockedUserService;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.security.core.context.SecurityContextHolder;

public class GeoStoreAuthenticationFilterTest {
    
    private static final String USERNAME_HEADER = "username";
    private static final String SAMPLE_USER = "myuser";
    private MockedUserService userService;
    private GeoStoreRequestHeadersAuthenticationFilter filter;
    private HttpServletRequest req;
    private HttpServletResponse resp;
    
    @Before
    public void setUp() {
        userService = new MockedUserService();
        filter = new GeoStoreRequestHeadersAuthenticationFilter();
        filter.setUserNameHeader(USERNAME_HEADER);
        filter.setUserService(userService);
        filter.setAutoCreateUser(true);
        
        req = Mockito.mock(HttpServletRequest.class);
        resp = Mockito.mock(HttpServletResponse.class);
        
        Mockito.when(req.getHeader(USERNAME_HEADER)).thenReturn(SAMPLE_USER);
        Mockito.when(req.getHeader("header1")).thenReturn("value1");
        Mockito.when(req.getHeaderNames()).thenReturn(
                new Vector(Arrays.asList(USERNAME_HEADER, "header1")).elements());
    }
    
    @After
    public void tearDown() {
        SecurityContextHolder.getContext().setAuthentication(null);
    }
    
    @Test
    public void testAutoCreate() throws IOException, ServletException, NotFoundServiceEx {
        
        
        
        
        filter.doFilter(req, resp, new FilterChain() {
            
            @Override
            public void doFilter(ServletRequest arg0, ServletResponse arg1) throws IOException,
                    ServletException {
                
                
            }
            
        });
        
        User user = userService.get(SAMPLE_USER);
        checkUser(user);
        assertTrue(user.isEnabled());
    }

    
    @Test
    public void testAutoCreateDisabled() throws IOException, ServletException, NotFoundServiceEx {
        
        filter.setEnableAutoCreatedUsers(false);
        filter.doFilter(req, resp, new FilterChain() {
            
            @Override
            public void doFilter(ServletRequest arg0, ServletResponse arg1) throws IOException,
                    ServletException {
                
                
            }
            
        });
        
        User user = userService.get(SAMPLE_USER);
        checkUser(user);
        assertFalse(user.isEnabled());
    }
    
    @Test
    public void testAutoCreateAttributesMapping() throws IOException, ServletException, NotFoundServiceEx {
        
        Map<String, String> attributeMappings = new HashMap<String, String>();
        attributeMappings.put("attr1", "header1");
        filter.setUserMapper(new MapExpressionUserMapper(attributeMappings));
        
        
        filter.doFilter(req, resp, new FilterChain() {
            
            @Override
            public void doFilter(ServletRequest arg0, ServletResponse arg1) throws IOException,
                    ServletException {
                
                
            }
            
        });
        
        User user = userService.get(SAMPLE_USER);
        checkUser(user);
        List<UserAttribute> attributes = user.getAttribute();
        assertEquals(1, attributes.size());
        assertEquals("attr1", attributes.get(0).getName());
        assertEquals("value1", attributes.get(0).getValue());
    }
    

    private void checkUser(User user) {
        assertNotNull(user);
        assertEquals(Role.USER, user.getRole());
        assertTrue(user.getGroups().isEmpty());
    }
}
