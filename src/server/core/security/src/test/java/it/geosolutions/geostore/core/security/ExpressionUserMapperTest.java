/* ====================================================================
 *
 * Copyright (C) 2015 GeoSolutions S.A.S.
 * http://www.geo-solutions.it
 *
 * GPLv3 + Classpath exception
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 *
 * ====================================================================
 *
 * This software consists of voluntary contributions made by developers
 * of GeoSolutions.  For more information on GeoSolutions, please see
 * <http://www.geo-solutions.it/>.
 *
 */
package it.geosolutions.geostore.core.security;

import static org.junit.Assert.assertEquals;
import it.geosolutions.geostore.core.model.User;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class ExpressionUserMapperTest {
    ExpressionUserMapper mapper;
    private Map<String, String> attributeMappings;
    MockUserDetailsWithAttributes detailsWithAttributes;
    
    @Before
    public void setUp() {
        attributeMappings = new HashMap<String,String>();
        mapper = new ExpressionUserMapper(attributeMappings);
        detailsWithAttributes =  new MockUserDetailsWithAttributes();
    }
    
    @Test
    public void testMapping() {
        User user = new User();
        detailsWithAttributes.getAttributes().put("sample", "mock");
        attributeMappings.put("transformed", "sample");
        mapper.mapUser(detailsWithAttributes, user);
        
        assertEquals(1, user.getAttribute().size());
        assertEquals("transformed", user.getAttribute().get(0).getName());
        assertEquals("mock", user.getAttribute().get(0).getValue());
    }
}
