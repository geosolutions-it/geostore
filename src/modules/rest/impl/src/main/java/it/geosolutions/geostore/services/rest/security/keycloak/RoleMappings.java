/* ====================================================================
 *
 * Copyright (C) 2022 GeoSolutions S.A.S.
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
package it.geosolutions.geostore.services.rest.security.keycloak;

import java.util.HashMap;

/**
 * Case insensitive map for RoleMappings used by Keycloak classes.
 */
class RoleMappings extends HashMap<String,String> {

    RoleMappings(int initialCapacity) {
        super(initialCapacity);
    }

    RoleMappings() {}

    @Override
    public String get(Object key) {
        if (!(key instanceof String)) return null;
        return super.get(key.toString().toUpperCase());
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof String)) return false;
        return super.containsKey(key.toString().toUpperCase());
    }

    @Override
    public String put(String key, String value) {
        return super.put(key.toUpperCase(), value);
    }
}
