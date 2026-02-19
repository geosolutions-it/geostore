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
import it.geosolutions.geostore.services.rest.model.SessionToken;
import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

/** This class provides authentication entry point to login using an OAuth2 provider. */
public class IdPLoginRestImpl implements IdPLoginRest {

    private final Map<String, IdPLoginService> services = new HashMap<>();

    @Override
    public void login(String provider) {
        HttpServletRequest request = OAuth2Utils.getRequest();
        HttpServletResponse resp = OAuth2Utils.getResponse();
        IdPLoginService service = services.get(provider);
        service.doLogin(request, resp, provider);
    }

    @Override
    public Response callback(String provider) throws NotFoundWebEx {
        IdPLoginService service = services.get(provider);
        return service.doInternalRedirect(
                OAuth2Utils.getRequest(), OAuth2Utils.getResponse(), provider);
    }

    @Override
    public SessionToken getTokensByTokenIdentifier(String provider, String tokenIdentifier)
            throws NotFoundWebEx {
        return services.get(provider).getTokenByIdentifier(provider, tokenIdentifier);
    }

    @Override
    public Response listProviders() {
        Map<String, OAuth2Configuration> configs = GeoStoreContext.beans(OAuth2Configuration.class);
        List<Map<String, String>> providers = new ArrayList<>();
        if (configs != null) {
            for (OAuth2Configuration config : configs.values()) {
                if (config.isEnabled()) {
                    Map<String, String> entry = new HashMap<>();
                    entry.put("name", config.getProvider());
                    entry.put("loginUrl", config.getProvider() + "/login");
                    providers.add(entry);
                }
            }
        }
        return Response.ok(providers).build();
    }

    @Override
    public void registerService(String providerName, IdPLoginService service) {
        this.services.put(providerName, service);
    }
}
