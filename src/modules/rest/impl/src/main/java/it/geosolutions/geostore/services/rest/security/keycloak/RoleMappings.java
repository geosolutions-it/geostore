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
