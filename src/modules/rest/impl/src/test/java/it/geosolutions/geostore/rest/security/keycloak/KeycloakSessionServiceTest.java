package it.geosolutions.geostore.rest.security.keycloak;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.services.rest.RESTSessionService;
import it.geosolutions.geostore.services.rest.impl.RESTSessionServiceImpl;
import it.geosolutions.geostore.services.rest.model.SessionToken;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.keycloak.KeyCloakConfiguration;
import it.geosolutions.geostore.services.rest.security.keycloak.KeyCloakHelper;
import it.geosolutions.geostore.services.rest.security.keycloak.KeycloakSessionServiceDelegate;
import it.geosolutions.geostore.services.rest.security.keycloak.KeycloakTokenDetails;
import it.geosolutions.geostore.services.rest.utils.GeoStoreContext;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import wiremock.org.eclipse.jetty.http.HttpStatus;

import java.text.ParseException;
import java.util.ArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static it.geosolutions.geostore.services.rest.SessionServiceDelegate.PROVIDER_KEY;
import static it.geosolutions.geostore.services.rest.security.keycloak.KeyCloakSecurityConfiguration.CACHE_BEAN_NAME;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.ACCESS_TOKEN_PARAM;
import static it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils.REFRESH_TOKEN_PARAM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KeycloakSessionServiceTest extends KeycloakTestSupport {

    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String ACCESS_TOKEN = "access_token";
    private static WireMockServer keycloakService;
    private KeyCloakHelper helper;

    @BeforeClass
    public static void mockServerSetup() {

        keycloakService =
                new WireMockServer(
                        wireMockConfig()
                                .port(12345)
                                // uncomment the following to get wiremock logging
                                .notifier(new ConsoleNotifier(true)));
        keycloakService.start();

        keycloakService.stubFor(
                WireMock.post(urlPathMatching("/auth/realms/master/protocol/openid-connect/token"))
                        .withRequestBody(containing("grant_type=refresh_token"))
                        .withRequestBody(containing("client_id=" + CLIENT_ID))
                        .withRequestBody(containing("client_secret=" + SECRET))
                        .withRequestBody(containing("refresh_token=" + REFRESH_TOKEN))

                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBodyFile("token_response_keycloak.json")));
        keycloakService.stubFor(
                WireMock.post(urlPathMatching("/auth/realms/master/protocol/openid-connect/logout"))
                        .withRequestBody(containing("client_id=" + CLIENT_ID))
                        .withRequestBody(containing("client_secret=" + SECRET))
                        .withRequestBody(containing("refresh_token=" + REFRESH_TOKEN))
                        .willReturn(aResponse().withStatus(204)));

        keycloakService.stubFor(WireMock.get(urlPathMatching("/auth/realms/master/.well-known/openid-configuration"))
                .willReturn(aResponse().withStatus(200).withBodyFile("keycloak_discovery.json")));


    }

    @AfterClass
    public static void afterClass() {
        keycloakService.stop();
    }

    @Before
    public void setUp() {
        setUpAdapter(AUTH_URL);
        adapterConfig.setAuthServerUrl("http://localhost:" + keycloakService.port() + "/auth");
        KeycloakDeployment deployment =
                KeycloakDeploymentBuilder.build(adapterConfig);
        AdapterDeploymentContext context = new AdapterDeploymentContext(deployment);
        KeyCloakHelper helper = new KeyCloakHelper(context);
        this.helper = helper;
    }

    @Test
    public void testRefreshToken() throws JsonProcessingException, ParseException {
        KeyCloakConfiguration configuration = createConfiguration();
        TokenAuthenticationCache cache = new TokenAuthenticationCache();
        PreAuthenticatedAuthenticationToken authenticationToken = new PreAuthenticatedAuthenticationToken("user", "", new ArrayList<>());
        KeycloakTokenDetails details = new KeycloakTokenDetails("access_token", REFRESH_TOKEN, 0L);
        authenticationToken.setDetails(details);
        cache.putCacheEntry(ACCESS_TOKEN, authenticationToken);
        try (MockedStatic<GeoStoreContext> utilities = Mockito.mockStatic(GeoStoreContext.class)) {
            utilities.when(() -> GeoStoreContext.bean(KeyCloakConfiguration.class)).thenReturn(configuration);
            utilities.when(() -> GeoStoreContext.bean(KeyCloakHelper.class)).thenReturn(helper);
            utilities.when(() -> GeoStoreContext.bean(CACHE_BEAN_NAME, TokenAuthenticationCache.class)).thenReturn(cache);
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            ServletRequestAttributes attributes = new ServletRequestAttributes(request, response);
            attributes.setAttribute(PROVIDER_KEY, "keycloak", 0);
            attributes.setAttribute(ACCESS_TOKEN_PARAM, ACCESS_TOKEN, 0);
            attributes.setAttribute(REFRESH_TOKEN_PARAM, REFRESH_TOKEN, 0);
            RequestContextHolder.setRequestAttributes(attributes);
            RESTSessionService sessionService = new RESTSessionServiceImpl();
            new KeycloakSessionServiceDelegate(sessionService, null);
            SessionToken token = new SessionToken();
            token.setTokenType("Bearer");
            token.setAccessToken(ACCESS_TOKEN);
            token.setRefreshToken(REFRESH_TOKEN);
            token.setExpires(0L);

            //start the test
            SessionToken result = sessionService.refresh(token);
            assertEquals("new_access_token", result.getAccessToken());
            assertEquals("new_refresh_token", result.getRefreshToken());
        }
    }

    @Test
    public void testGetUser() throws JsonProcessingException, ParseException {
        KeyCloakConfiguration configuration = createConfiguration();
        TokenAuthenticationCache cache = new TokenAuthenticationCache();
        PreAuthenticatedAuthenticationToken authenticationToken = new PreAuthenticatedAuthenticationToken("user", "", new ArrayList<>());
        KeycloakTokenDetails details = new KeycloakTokenDetails("access_token", REFRESH_TOKEN, 0L);
        authenticationToken.setDetails(details);
        cache.putCacheEntry(ACCESS_TOKEN, authenticationToken);
        try (MockedStatic<GeoStoreContext> utilities = Mockito.mockStatic(GeoStoreContext.class)) {
            utilities.when(() -> GeoStoreContext.bean(KeyCloakConfiguration.class)).thenReturn(configuration);
            utilities.when(() -> GeoStoreContext.bean(KeyCloakHelper.class)).thenReturn(helper);
            utilities.when(() -> GeoStoreContext.bean(CACHE_BEAN_NAME, TokenAuthenticationCache.class)).thenReturn(cache);
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            ServletRequestAttributes attributes = new ServletRequestAttributes(request, response);
            attributes.setAttribute(PROVIDER_KEY, "keycloak", 0);
            attributes.setAttribute(ACCESS_TOKEN_PARAM, ACCESS_TOKEN, 0);
            attributes.setAttribute(REFRESH_TOKEN_PARAM, REFRESH_TOKEN, 0);
            RequestContextHolder.setRequestAttributes(attributes);
            RESTSessionService sessionService = new RESTSessionServiceImpl();
            new KeycloakSessionServiceDelegate(sessionService, null);

            //start the test
            User user = sessionService.getUser(ACCESS_TOKEN, false);
            assertEquals("user", user.getName());
        }
    }

    @Test
    public void testLogout() throws JsonProcessingException, ParseException {
        KeyCloakConfiguration configuration = createConfiguration();
        PreAuthenticatedAuthenticationToken authenticationToken = new PreAuthenticatedAuthenticationToken("user", "", new ArrayList<>());
        KeycloakTokenDetails details = new KeycloakTokenDetails("access_token", REFRESH_TOKEN, 0L);
        authenticationToken.setDetails(details);
        TokenAuthenticationCache cache = new TokenAuthenticationCache();
        cache.putCacheEntry(ACCESS_TOKEN, authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        try (MockedStatic<GeoStoreContext> utilities = Mockito.mockStatic(GeoStoreContext.class)) {
            utilities.when(() -> GeoStoreContext.bean(KeyCloakConfiguration.class)).thenReturn(configuration);
            utilities.when(() -> GeoStoreContext.bean(KeyCloakHelper.class)).thenReturn(helper);
            utilities.when(() -> GeoStoreContext.bean(CACHE_BEAN_NAME, TokenAuthenticationCache.class)).thenReturn(cache);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter(ACCESS_TOKEN_PARAM, ACCESS_TOKEN);
            // test request.logout();
            request.setUserPrincipal(authenticationToken);
            MockHttpServletResponse response = new MockHttpServletResponse();
            ServletRequestAttributes attributes = new ServletRequestAttributes(request, response);
            attributes.setAttribute(PROVIDER_KEY, "keycloak", 0);
            RequestContextHolder.setRequestAttributes(attributes);
            RESTSessionService sessionService = new RESTSessionServiceImpl();
            new KeycloakSessionServiceDelegate(sessionService, null);

            // start the test
            sessionService.removeSession();
            assertEquals(response.getStatus(), HttpStatus.OK_200);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            assertNull(request.getUserPrincipal());
        }
    }


}
