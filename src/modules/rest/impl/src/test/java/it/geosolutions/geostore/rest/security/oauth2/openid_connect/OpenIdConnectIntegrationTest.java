package it.geosolutions.geostore.rest.security.oauth2.openid_connect;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.core.model.enums.Role;
import it.geosolutions.geostore.services.rest.security.oauth2.GeoStoreOAuthRestTemplate;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.OpenIdConnectConfiguration;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.OpenIdConnectFilter;
import it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.OpenIdConnectSecurityConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.DefaultOAuth2ClientContext;
import org.springframework.security.oauth2.client.token.DefaultAccessTokenRequest;
import org.springframework.security.oauth2.client.token.grant.code.AuthorizationCodeAccessTokenProvider;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.ServletException;
import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;

public class OpenIdConnectIntegrationTest {

    private static final String CLIENT_ID = "kbyuFDidLLm280LIwVFiazOqjO3ty8KH";
    private static final String CLIENT_SECRET =
            "60Op4HFM0I8ajz0WdiStAbziZ-VFQttXuxixHHs2R7r7-CW8GR79l-mmLqMhc-Sa";
    private static final String CODE = "R-2CqM7H1agwc7Cx";
    private static WireMockServer openIdConnectService;
    private String authService;
    private OpenIdConnectFilter filter;
    private OpenIdConnectConfiguration configuration;

    @BeforeClass
    public static void beforeClass() {
        openIdConnectService =
                new WireMockServer(
                        wireMockConfig()
                                .dynamicPort()
                                // uncomment the following to get wiremock logging
                                .notifier(new ConsoleNotifier(true)));
        openIdConnectService.start();

        openIdConnectService.stubFor(
                WireMock.get(urlEqualTo("/certs"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBodyFile("jkws.json")));

        openIdConnectService.stubFor(
                WireMock.post(urlPathEqualTo("/token"))
                        .withRequestBody(containing("grant_type=authorization_code"))
                        .withRequestBody(containing("client_id=" + CLIENT_ID))
                        .withRequestBody(containing("code=" + CODE))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBodyFile("token_response.json")));
        openIdConnectService.stubFor(any(urlPathEqualTo("/userinfo")).willReturn(
                aResponse()
                        .withStatus(200)
                        .withHeader(
                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBodyFile("userinfo.json"))); // disallow query parameters
    }

    @Before
    public void before() {
        // prepare mock server base path
        authService = "http://localhost:" + openIdConnectService.port();
        OpenIdConnectConfiguration configuration = new OpenIdConnectConfiguration();
        configuration.setClientId(CLIENT_ID);
        configuration.setClientSecret(CLIENT_SECRET);
        configuration.setRevokeEndpoint(authService + "/revoke");
        configuration.setAccessTokenUri(authService + "/token");
        configuration.setAuthorizationUri(authService + "/authorize");
        configuration.setCheckTokenEndpointUrl(authService + "/userinfo");
        configuration.setEnabled(true);
        configuration.setAutoCreateUser(true);
        configuration.setIdTokenUri(authService + "/certs");
        configuration.setBeanName("oidcOAuth2Config");
        configuration.setEnableRedirectEntryPoint(true);
        configuration.setRedirectUri("../../../geostore/rest/users/user/details");
        configuration.setScopes("openId,email");
        configuration.setSendClientSecret(true);
        this.configuration = configuration;
        OpenIdConnectSecurityConfiguration securityConfiguration = new OpenIdConnectSecurityConfiguration() {

            @Override
            protected GeoStoreOAuthRestTemplate restTemplate() {
                return new GeoStoreOAuthRestTemplate(resourceDetails(), new DefaultOAuth2ClientContext(new DefaultAccessTokenRequest()), configuration());
            }

            @Override
            public OAuth2Configuration configuration() {
                return configuration;
            }
        };
        GeoStoreOAuthRestTemplate restTemplate = securityConfiguration.oauth2RestTemplate();
        AuthorizationCodeAccessTokenProvider provider = securityConfiguration.authorizationAccessTokenProvider();
        this.filter = new OpenIdConnectFilter(securityConfiguration.oidcTokenServices(), restTemplate, provider, configuration, securityConfiguration.oidcCache());
    }

    @After
    public void afterTest() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testRedirect() throws IOException, ServletException {
        MockHttpServletRequest request = createRequest("oidc/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(302, response.getStatus());
        assertEquals(response.getRedirectedUrl(), configuration.buildLoginUri());
    }

    @Test
    public void testAuthentication() throws IOException, ServletException {
        MockHttpServletRequest request = createRequest("oidc/login");
        request.setParameter("authorization_code", CODE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.restTemplate.getOAuth2ClientContext().getAccessTokenRequest().setAuthorizationCode(CODE);
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();
        assertEquals("ritter@erdukunde.de", user.getName());
        assertEquals(Role.USER, user.getRole());
    }

    @Test
    public void testGroupsAndRolesFromToken() throws IOException, ServletException {
        configuration.setGroupsClaim("hd");
        MockHttpServletRequest request = createRequest("oidc/login");
        request.setParameter("authorization_code", CODE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.restTemplate.getOAuth2ClientContext().getAccessTokenRequest().setAuthorizationCode(CODE);
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();
        assertEquals("ritter@erdukunde.de", user.getName());
        assertEquals(Role.USER, user.getRole());
        UserGroup group = user.getGroups().stream().findAny().get();
        assertEquals("geosolutionsgroup.com", group.getGroupName());
    }

    private MockHttpServletRequest createRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setContextPath("/geostore");
        request.setRequestURI("/geostore/" + path);
        // request.setRequestURL(ResponseUtils.appendPath("http://localhost:8080/geoserver", path )
        // );
        request.setRemoteAddr("127.0.0.1");
        request.setServletPath("/geostore");
        request.setPathInfo(path);
        request.addHeader("Host", "localhost:8080");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
        return request;
    }
}
