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
package it.geosolutions.geostore.services.rest.security.oauth2;

import com.auth0.jwt.JWT;
import com.auth0.jwt.impl.NullClaim;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** A class holding utilities method for handling JWT tokens. */
public class JWTHelper {

    private final DecodedJWT decodedJWT;

    public JWTHelper(String jwtToken) {
        this.decodedJWT = JWT.decode(jwtToken);
    }

    /**
     * Get a claim by name from the idToken. Supports dot-notation for nested claims (e.g.
     * "realm_access.roles" will traverse into the "realm_access" object and return "roles").
     *
     * @param claimName the name of the claim to retrieve, may use dot-notation for nested objects.
     * @param binding the Class to which convert the claim value.
     * @param <T> the type of the claim value.
     * @return the claim value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getClaim(String claimName, Class<T> binding) {
        if (decodedJWT == null || claimName == null) return null;

        // If the claim name contains dots, traverse nested objects
        if (claimName.contains(".")) {
            Object nested = resolveNestedClaim(claimName);
            if (nested == null) return null;
            if (binding.isInstance(nested)) return (T) nested;
            // For String binding, toString the value
            if (binding == String.class) return (T) String.valueOf(nested);
            return null;
        }

        Claim claim = decodedJWT.getClaim(claimName);
        if (nonNullClaim(claim)) return claim.as(binding);
        return null;
    }

    /**
     * Get a claim values as List by its name. Supports dot-notation for nested claims (e.g.
     * "realm_access.roles" will traverse into the "realm_access" object and return "roles").
     *
     * @param claimName the name of the claim to retrieve, may use dot-notation for nested objects.
     * @param binding the Class to which convert the claim value.
     * @param <T> the type of the claim value.
     * @return the claim value.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getClaimAsList(String claimName, Class<T> binding) {
        if (decodedJWT == null || claimName == null) return null;

        // If the claim name contains dots, traverse nested objects
        if (claimName.contains(".")) {
            Object nested = resolveNestedClaim(claimName);
            return extractListFromValue(nested, binding);
        }

        Claim claim = decodedJWT.getClaim(claimName);
        if (nonNullClaim(claim)) {
            List<T> result = claim.asList(binding);
            if (result == null) {
                result = new ArrayList<>();
                T singleValue = claim.as(binding);
                if (singleValue != null) result.add(singleValue);
            }
            return result;
        }
        return null;
    }

    /**
     * Resolves a dot-notation claim path (e.g. "realm_access.roles") by traversing nested objects.
     */
    @SuppressWarnings("unchecked")
    private Object resolveNestedClaim(String path) {
        String[] parts = path.split("\\.");
        if (parts.length < 2) return null;

        // Get the top-level claim as a Map
        Claim topClaim = decodedJWT.getClaim(parts[0]);
        if (!nonNullClaim(topClaim)) return null;

        Object current;
        try {
            current = topClaim.as(Map.class);
        } catch (Exception e) {
            return null;
        }

        // Traverse the remaining path segments
        for (int i = 1; i < parts.length; i++) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(parts[i]);
            if (current == null) return null;
        }
        return current;
    }

    /** Extracts a List from a value that may be a List, a Collection, or a single value. */
    @SuppressWarnings("unchecked")
    private <T> List<T> extractListFromValue(Object value, Class<T> binding) {
        if (value == null) return null;
        if (value instanceof List) {
            List<?> raw = (List<?>) value;
            List<T> result = new ArrayList<>(raw.size());
            for (Object item : raw) {
                if (binding.isInstance(item)) {
                    result.add((T) item);
                } else if (binding == String.class && item != null) {
                    result.add((T) String.valueOf(item));
                }
            }
            return result;
        }
        if (value instanceof Collection) {
            return extractListFromValue(new ArrayList<>((Collection<?>) value), binding);
        }
        // Single value -> wrap in list
        List<T> result = new ArrayList<>(1);
        if (binding.isInstance(value)) {
            result.add((T) value);
        } else if (binding == String.class && value != null) {
            result.add((T) String.valueOf(value));
        }
        return result.isEmpty() ? null : result;
    }

    private boolean nonNullClaim(Claim claim) {
        return claim != null && !(claim instanceof NullClaim);
    }
}
