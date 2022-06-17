package it.geosolutions.geostore.services.rest.security.keycloak;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * A request wrapper to fix the url when using nginx.
 */
public class KeyCloakRequestWrapper extends HttpServletRequestWrapper {
    /**
     * Constructs a request object wrapping the given request.
     *
     * @param request
     * @throws IllegalArgumentException if the request is null
     */
    public KeyCloakRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public StringBuffer getRequestURL() {
        String url = super.getRequestURL().toString();
        String proto = super.getHeader("x-forwarded-proto");

        if (proto != null && url.startsWith("http://") && proto.equals("https")) {
            url = url.replaceAll("^http", "https");
        }
        return new StringBuffer(url);
    }
}
