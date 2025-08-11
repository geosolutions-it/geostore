package it.geosolutions.geostore.services.rest.security.keycloak;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.*;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.googlecode.genericdao.search.Search;
import it.geosolutions.geostore.core.model.User;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.http.MediaType;

public class KeycloakUserDAOTest {

    @Rule public WireMockRule wireMockServer = new WireMockRule(wireMockConfig().dynamicPort());
    private String authService;
    private KeycloakUserDAO userDAO;

    @Before
    public void before() {
        authService = "http://localhost:" + wireMockServer.port();
        KeycloakAdminClientConfiguration configuration = new KeycloakAdminClientConfiguration();
        configuration.setServerUrl(authService);
        configuration.setRealm("master");
        configuration.setUsername("username");
        configuration.setPassword("password");
        configuration.setClientId("clientId");
        this.userDAO = new KeycloakUserDAO(configuration);
        wireMockServer.stubFor(
                WireMock.post(urlEqualTo("/realms/master/protocol/openid-connect/token"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBodyFile("keycloak_token_response.json")));

        wireMockServer.stubFor(
                WireMock.get(urlEqualTo("/admin/realms/master/users"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBodyFile("keycloak_users.json")));
        wireMockServer.stubFor(
                WireMock.get(
                                urlEqualTo(
                                        "/admin/realms/master/users/0e72c14e-53d8-4619-a05a-a605dc2102b9/role-mappings/realm/composite"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBodyFile("keycloak_user_roles.json")));
        wireMockServer.stubFor(
                WireMock.get(
                                urlEqualTo(
                                        "/admin/realms/master/users?username=test&first=-1&max=-1&briefRepresentation=false"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBodyFile("keycloak_two_users.json")));
        wireMockServer.stubFor(
                WireMock.get(urlEqualTo("/admin/realms/master/users?username=admin&exact=true"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBodyFile("keycloak_admin_user.json")));
    }

    @Test
    public void testGetAll() {
        List<User> userList = userDAO.findAll();
        assertEquals(3, userList.size());
        for (User u : userList) {
            assertNotNull(u.getRole());
            assertEquals(5, u.getGroups().size());
        }
    }

    @Test
    public void testSearchNameLike() {
        Search searchCriteria = new Search(User.class);
        searchCriteria.addFilterILike("name", "test");
        List<User> userList = userDAO.search(searchCriteria);
        assertEquals(2, userList.size());
        List<String> validNames = Arrays.asList("test-user", "test-user-2");
        for (User u : userList) {
            assertNotNull(u.getRole());
            assertEquals(5, u.getGroups().size());
            assertTrue(validNames.contains(u.getName()));
        }
    }

    @Test
    public void testGetOneByName() {
        Search searchCriteria = new Search(User.class);
        searchCriteria.addFilterEqual("name", "admin");
        List<User> userList = userDAO.search(searchCriteria);
        assertEquals(1, userList.size());
        for (User u : userList) {
            assertNotNull(u.getRole());
            assertEquals(5, u.getGroups().size());
            assertEquals("admin", u.getName());
        }
    }
}
