package it.geosolutions.geostore.services.rest.security.keycloak;

import org.keycloak.adapters.spi.AuthChallenge;
import org.keycloak.adapters.springsecurity.facade.SimpleHttpFacade;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * AuthenticationEntryPoint that execute the Keycloak challenge and redirect to the Keycloak login page.
 */
class KeycloakAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private AuthChallenge challenge;

    KeycloakAuthenticationEntryPoint(AuthChallenge challenge){
        this.challenge=challenge;
    }


    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        if (challenge==null) throw new RuntimeException("Keycloak config is bearer only. No redirect to authorization page can be performed.");
        challenge.challenge(new SimpleHttpFacade(request, response));
        response.sendRedirect(response.getHeader("Location"));
    }
}