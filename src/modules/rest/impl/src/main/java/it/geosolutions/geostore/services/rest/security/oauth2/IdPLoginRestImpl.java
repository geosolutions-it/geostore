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

import it.geosolutions.geostore.services.rest.IdPLoginRest;
import it.geosolutions.geostore.services.rest.IdPLoginService;
import it.geosolutions.geostore.services.rest.exception.NotFoundWebEx;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

/**
 * This class provides authentication entry point to login using an OAuth2 provider.
 */
public class IdPLoginRestImpl implements IdPLoginRest {

    private Map<String, IdPLoginService> services =new HashMap<>();


    @Override
    public void login(String provider) {
        HttpServletRequest request=OAuth2Utils.getRequest();
        HttpServletResponse resp = OAuth2Utils.getResponse();
        IdPLoginService service=services.get(provider);
        service.doLogin(request,resp,provider);
    }

    @Override
    public Response callback(String provider) throws NotFoundWebEx {
        IdPLoginService service= services.get(provider);
        return service.doInternalRedirect(OAuth2Utils.getRequest(),OAuth2Utils.getResponse(),provider);
    }

    @Override
    public void registerService(String providerName,IdPLoginService service){
        this.services.put(providerName,service);
    }
}
