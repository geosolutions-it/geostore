package it.geosolutions.geostore.rest.security.keycloak;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.googlecode.genericdao.search.Search;
import it.geosolutions.geostore.core.model.User;
import it.geosolutions.geostore.core.model.UserGroup;
import it.geosolutions.geostore.services.rest.security.keycloak.KeycloakAdminClientConfiguration;
import it.geosolutions.geostore.services.rest.security.keycloak.KeycloakUserGroupDAO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KeycloakUserGroupDAOTest {

    @Rule
    public WireMockRule wireMockServer = new WireMockRule(wireMockConfig()
            .dynamicPort());
    private String authService;
    private KeycloakUserGroupDAO userGroupDAO;

    @Before
    public void before() {
        authService = "http://localhost:" + wireMockServer.port();
        KeycloakAdminClientConfiguration configuration = new KeycloakAdminClientConfiguration();
        configuration.setServerUrl(authService);
        configuration.setRealm("master");
        configuration.setUsername("username");
        configuration.setPassword("password");
        configuration.setClientId("clientId");
        this.userGroupDAO = new KeycloakUserGroupDAO(configuration);
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/realms/master/protocol/openid-connect/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(
                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBodyFile("keycloak_token_response.json")
                ));

        wireMockServer.stubFor(
                WireMock.get(urlEqualTo("/admin/realms/master/roles"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBodyFile("keycloak_roles.json")));
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/admin/realms/master/users/0e72c14e-53d8-4619-a05a-a605dc2102b9/role-mappings/realm/composite"))
                .willReturn(aResponse().withStatus(200).withHeader(
                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBodyFile("keycloak_user_roles.json")));
        wireMockServer.stubFor(
                WireMock.get(urlEqualTo("/admin/realms/master/roles?search=create"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBodyFile("keycloak_two_roles.json")));
        wireMockServer.stubFor(
                WireMock.get(urlEqualTo("/admin/realms/master/roles/test-create-group"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader(
                                                "Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                        .withBodyFile("keycloak_one_role.json")));
    }

    @Test
    public void testGetAll() {
        List<UserGroup> groupList = userGroupDAO.findAll();
        assertEquals(5, groupList.size());
    }

    @Test
    public void testSearchNameLike() {
        Search searchCriteria = new Search(User.class);
        searchCriteria.addFilterILike("groupName", "create");
        List<UserGroup> groups = userGroupDAO.search(searchCriteria);
        assertEquals(2, groups.size());
        List<String> valid = Arrays.asList("test-create-group", "create-realm");
        for (UserGroup ug : groups)
            assertTrue(valid.contains(ug.getGroupName()));

    }


    @Test
    public void testGetOneByName() {
        Search searchCriteria = new Search(User.class);
        searchCriteria.addFilterEqual("groupName", "test-create-group");
        List<UserGroup> groups = userGroupDAO.search(searchCriteria);
        UserGroup group = groups.get(0);
        assertEquals("test-create-group", group.getGroupName());

    }

    @Test
    public void testGetOneByName2() {
        UserGroup group = userGroupDAO.findByName("test-create-group");
        assertEquals("test-create-group", group.getGroupName());

    }

}
