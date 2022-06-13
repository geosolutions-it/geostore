package it.geosolutions.geostore.services.rest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

/**
 * Extension point to customize the login and redirect after login performed from the {@link IdPLoginRest};
 */
public interface IdPLoginService {

    /**
     * Perform the login to an external IdP.
     * @param request the request.
     * @param response the response.
     * @param provider the provider name.
     */
    void doLogin(HttpServletRequest request, HttpServletResponse response, String provider);

    /**
     * Perform a redirect to an application url. Usefull if the external IdP redirect to an app url when login is successfull.
     * @param request the request.
     * @param response the response.
     * @param provider the provider name.
     * @return a {@link Response instance}.
     */
    Response doInternalRedirect(HttpServletRequest request, HttpServletResponse response, String provider);
}
