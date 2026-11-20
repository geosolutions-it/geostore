package it.geosolutions.geostore.services.rest.security.oauth2;

import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import it.geosolutions.geostore.services.rest.IdPLoginRest;
import it.geosolutions.geostore.services.rest.security.IdPConfiguration;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.OpenIdConnectConfiguration;

import jakarta.ws.rs.core.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class OAuth2LoginTest {

    private final IdPLoginRest idPLoginRest = new IdPLoginRestImpl();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private ServletRequestAttributes attributes;

    @Before
    public void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        attributes = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    @Test
    public void testLogin() {
        OAuth2Configuration configuration = new OAuth2Configuration();
        configuration.setScopes("openid");
        configuration.setClientId("mockClientId");
        configuration.setAuthorizationUri("http://localhost:8080/authorization");
        SetConfOAuthLoginService setConfiguration = new SetConfOAuthLoginService(idPLoginRest);
        setConfiguration.setConfiguration(configuration);
        idPLoginRest.login("mock");
        assertEquals(302, response.getStatus());
        assertEquals(
                "http://localhost:8080/authorization?response_type=code&client_id=mockClientId&scope=openid&redirect_uri=null",
                response.getRedirectedUrl());
    }

    @Test
    public void testLoginWithPKCE() {
        OpenIdConnectConfiguration configuration = new OpenIdConnectConfiguration();
        configuration.setScopes("openid");
        configuration.setClientId("mockClientId");
        configuration.setAuthorizationUri("http://localhost:8080/authorization");
        configuration.setUsePKCE(true);
        SetConfOAuthLoginService setConfiguration = new SetConfOAuthLoginService(idPLoginRest);
        setConfiguration.setConfiguration(configuration);
        idPLoginRest.login("mock");
        assertEquals(302, response.getStatus());
        String redirectUrl = response.getRedirectedUrl();
        assertNotNull(redirectUrl);
        assertTrue(redirectUrl.contains("code_challenge_method=S256"));
        assertTrue(redirectUrl.contains("code_challenge="));
    }

    @Test
    public void testCallback() {
        attributes.setAttribute(REFRESH_TOKEN_PARAM, "mockRefreshToken", 0);
        attributes.setAttribute(ACCESS_TOKEN_PARAM, "mockAccessToken", 0);
        OAuth2Configuration configuration = new OAuth2Configuration();
        configuration.setInternalRedirectUri("http://localhost:8080/geostore/redirect");
        SetConfOAuthLoginService setConfiguration = new SetConfOAuthLoginService(idPLoginRest);
        setConfiguration.setConfiguration(configuration);
        setConfiguration.setTokenStorage(new InMemoryTokenStorage());
        Response result = idPLoginRest.callback("mock");
        assertEquals(302, result.getStatus());
        List<Object> cookies = result.getMetadata().get("Set-Cookie");
        List<Object> tokenCookies =
                cookies.stream()
                        .filter(
                                c ->
                                        ((String) c).contains(AUTH_PROVIDER)
                                                || ((String) c).contains(TOKENS_KEY))
                        .collect(Collectors.toList());
        assertEquals(2, tokenCookies.size());
        assertEquals("http://localhost:8080/geostore/redirect", result.getHeaderString("Location"));
    }

    @Test
    public void testCallbackCookiesFollowRequestScheme() {
        assertCallbackCookies(new OAuth2Configuration(), false);

        request.setSecure(true);
        assertCallbackCookies(new OAuth2Configuration(), true);
    }

    @Test
    public void testCallbackCookiesSecureWhenForced() {
        OAuth2Configuration configuration = new OAuth2Configuration();
        configuration.setForceSecureCookies(true);

        assertCallbackCookies(configuration, true);
    }

    @Test
    public void testCallbackRefreshCookieDefaults() {
        List<String> refreshCookies = callbackRefreshCookies(new OAuth2Configuration());

        assertEquals("Only the cookie itself: " + refreshCookies, 1, refreshCookies.size());
        String refreshCookie = refreshCookies.get(0);
        assertEquals(refreshCookie, "604800", attribute(refreshCookie, "Max-Age"));
        assertEquals(refreshCookie, "/", attribute(refreshCookie, "Path"));
    }

    @Test
    public void testCallbackRefreshCookieConfiguredLifetimeAndPath() {
        OAuth2Configuration configuration = new OAuth2Configuration();
        configuration.setRefreshTokenCookieMaxAge(3600);
        configuration.setRefreshTokenCookiePath("/geostore/rest");

        String refreshCookie = callbackRefreshCookie(configuration);

        assertEquals(refreshCookie, "3600", attribute(refreshCookie, "Max-Age"));
        assertEquals(refreshCookie, "/geostore/rest", attribute(refreshCookie, "Path"));
    }

    @Test
    public void testCallbackDeletesTheRootCookieWhenThePathIsNarrower() {
        OAuth2Configuration configuration = new OAuth2Configuration();
        configuration.setRefreshTokenCookiePath("/geostore/rest");
        configuration.setForceSecureCookies(true);

        List<String> refreshCookies = callbackRefreshCookies(configuration);

        assertEquals(refreshCookies.toString(), 2, refreshCookies.size());
        String refreshCookie = refreshCookies.get(0);
        assertEquals(refreshCookie, "/geostore/rest", attribute(refreshCookie, "Path"));
        assertTrue(refreshCookie, refreshCookie.startsWith(REFRESH_TOKEN_PARAM + "=mock"));
        // The cookie an earlier version left on "/" would otherwise stay until it expires.
        String deletion = refreshCookies.get(1);
        assertEquals(deletion, "/", attribute(deletion, "Path"));
        assertEquals(deletion, "0", attribute(deletion, "Max-Age"));
        assertTrue(deletion, !deletion.contains("mockRefreshToken"));
        assertTrue(deletion, hasSecureAttribute(deletion));
        assertTrue(deletion, hasAttribute(deletion, "HttpOnly"));
    }

    @Test
    public void testCallbackRefreshCookieAsSessionCookie() {
        OAuth2Configuration configuration = new OAuth2Configuration();
        configuration.setRefreshTokenCookieMaxAge(-1);

        String refreshCookie = callbackRefreshCookie(configuration);

        assertNull(refreshCookie, attribute(refreshCookie, "Max-Age"));
        assertNull(refreshCookie, attribute(refreshCookie, "Expires"));
    }

    /** Runs the callback and returns the Set-Cookie header of the refresh token. */
    private String callbackRefreshCookie(OAuth2Configuration configuration) {
        List<String> refreshCookies = callbackRefreshCookies(configuration);
        assertTrue("No refresh token cookie", !refreshCookies.isEmpty());
        return refreshCookies.get(0);
    }

    /** Runs the callback and returns its Set-Cookie headers for the refresh token, in order. */
    private List<String> callbackRefreshCookies(OAuth2Configuration configuration) {
        attributes.setAttribute(REFRESH_TOKEN_PARAM, "mockRefreshToken", 0);
        attributes.setAttribute(ACCESS_TOKEN_PARAM, "mockAccessToken", 0);
        configuration.setInternalRedirectUri("http://localhost:8080/geostore/redirect");
        SetConfOAuthLoginService service = new SetConfOAuthLoginService(idPLoginRest);
        service.setConfiguration(configuration);
        service.setTokenStorage(new InMemoryTokenStorage());

        Response result = idPLoginRest.callback("mock");

        return result.getMetadata().get("Set-Cookie").stream()
                .map(Object::toString)
                .filter(c -> c.startsWith(REFRESH_TOKEN_PARAM + "="))
                .collect(Collectors.toList());
    }

    /** The value of a Set-Cookie attribute, or null when the attribute is absent. */
    private static String attribute(String setCookie, String name) {
        return Arrays.stream(setCookie.split(";"))
                .map(String::trim)
                .filter(part -> part.regionMatches(true, 0, name + "=", 0, name.length() + 1))
                .map(part -> part.substring(name.length() + 1))
                .findFirst()
                .orElse(null);
    }

    /** Runs the callback and checks the Secure attribute of the three cookies it sets. */
    private void assertCallbackCookies(OAuth2Configuration configuration, boolean secure) {
        attributes.setAttribute(REFRESH_TOKEN_PARAM, "mockRefreshToken", 0);
        attributes.setAttribute(ACCESS_TOKEN_PARAM, "mockAccessToken", 0);
        configuration.setInternalRedirectUri("http://localhost:8080/geostore/redirect");
        SetConfOAuthLoginService service = new SetConfOAuthLoginService(idPLoginRest);
        service.setConfiguration(configuration);
        service.setTokenStorage(new InMemoryTokenStorage());

        Response result = idPLoginRest.callback("mock");

        List<Object> cookies = result.getMetadata().get("Set-Cookie");
        assertEquals(3, cookies.size());
        for (Object cookie : cookies) {
            assertEquals(cookie.toString(), secure, hasSecureAttribute(cookie.toString()));
        }
    }

    private static boolean hasSecureAttribute(String setCookie) {
        return hasAttribute(setCookie, "Secure");
    }

    private static boolean hasAttribute(String setCookie, String name) {
        return Arrays.stream(setCookie.split(";"))
                .map(String::trim)
                .anyMatch(name::equalsIgnoreCase);
    }

    @After
    public void afterTest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private class SetConfOAuthLoginService extends Oauth2LoginService {

        private OAuth2Configuration configuration;

        private TokenStorage tokenStorage;

        public SetConfOAuthLoginService(IdPLoginRest loginRest) {
            loginRest.registerService("mock", this);
        }

        public void setConfiguration(OAuth2Configuration configuration) {
            this.configuration = configuration;
        }

        @Override
        protected OAuth2Configuration oauth2Configuration(String provider) {
            return configuration;
        }

        @Override
        protected IdPConfiguration configuration(String provider) {
            return configuration;
        }

        @Override
        protected TokenStorage tokenStorage() {
            return tokenStorage;
        }

        public void setTokenStorage(TokenStorage tokenStorage) {
            this.tokenStorage = tokenStorage;
        }
    }
}
