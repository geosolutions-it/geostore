package it.geosolutions.geostore.services.rest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

public interface IdPLoginService {

    void doLogin(HttpServletRequest request, HttpServletResponse response, String provider);

    Response doInternalRedirect(HttpServletRequest request, HttpServletResponse response, String provider);
}
