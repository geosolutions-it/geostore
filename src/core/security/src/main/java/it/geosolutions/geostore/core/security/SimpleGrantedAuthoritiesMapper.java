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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Map based implementation of GrantedAuthoritiesMapper.
 *  *  
 * @author Mauro Bartolomeoli
 *
 */
public class SimpleGrantedAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private Map<String, String> mappings = new HashMap<String, String>();
    
    public SimpleGrantedAuthoritiesMapper(Map<String, String> mappings) {
        super();
        this.mappings = mappings;
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(
            Collection<? extends GrantedAuthority> authorities) {
        if(mappings.isEmpty()) {
            return authorities;
        }
        List<GrantedAuthority> result = new ArrayList<GrantedAuthority>();
        for(GrantedAuthority authority : authorities) {
            if(mappings.containsKey(authority.getAuthority())) {
                result.add(new SimpleGrantedAuthority(mappings.get(authority.getAuthority())));
            } else {
                result.add(authority);
            }
        }
        return result;
    }

}
