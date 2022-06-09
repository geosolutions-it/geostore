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
import java.util.List;

/**
 * A class holding utilities method for handling JWT tokens.
 */
public class JWTHelper {

    private DecodedJWT decodedJWT;
    public JWTHelper(String jwtToken){
        this.decodedJWT= JWT.decode(jwtToken);
    }

    /**
     * Get a claim by name from the idToken.
     *
     * @param claimName the name of the claim to retrieve.
     * @param binding   the Class to which convert the claim value.
     * @param <T>       the type of the claim value.
     * @return the claim value.
     */
    public <T> T getClaim(String claimName, Class<T> binding) {
        T result = null;
        if (decodedJWT != null && claimName!=null) {
            Claim claim = decodedJWT.getClaim(claimName);
            if (nonNullClaim(claim))
                result = claim.as(binding);

        }
        return result;
    }

    /**
     * Get a claim values as List by its name.
     *
     * @param claimName the name of the claim to retrieve.
     * @param binding   the Class to which convert the claim value.
     * @param <T>       the type of the claim value.
     * @return the claim value.
     */
    public <T> List<T> getClaimAsList(String claimName, Class<T> binding) {
        List<T> result = null;
        if (decodedJWT != null && claimName!=null) {
            Claim claim = decodedJWT.getClaim(claimName);
            if (nonNullClaim(claim)) {
                result = claim.asList(binding);
                if (result==null){
                    result=new ArrayList<>();
                    T singleValue=claim.as(binding);
                    if (singleValue!=null) result.add(singleValue);
                }
            }

        }
        return result;
    }

    private boolean nonNullClaim(Claim claim){
        return claim != null && !(claim instanceof NullClaim);
    }
}
