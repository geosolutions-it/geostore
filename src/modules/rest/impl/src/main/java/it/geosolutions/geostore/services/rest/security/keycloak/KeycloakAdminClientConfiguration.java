package it.geosolutions.geostore.services.rest.security.keycloak;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

/**
 * Keycloak Admin REST client configuration class.
 * Used to configure the {@link KeycloakUserDAO} and {@link KeycloakUserGroupDAO}.
 */
public class KeycloakAdminClientConfiguration {

    private Keycloak keycloak;

    private String serverUrl;

    private String realm;

    private String username;

    private String password;

    private String clientId;


    /**
     * @return the keycloak server url.
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * @param  serverUrl the keycloak server url.
     */
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    /**
     * @return the realm from which retrieve users and groups.
     */
    public String getRealm() {
        return realm;
    }

    /**
     *
     * @param realm the realm from which retrieve users and groups.
     */
    public void setRealm(String realm) {
        this.realm = realm;
    }

    /**
     * @return the username of a keycloak admin.
     */
    public String getUsername() {
        return username;
    }

    /**
     *
     * @param username the username of a keycloak admin.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return the pwd of a keycloak admin.
     */
    public String getPassword() {
        return password;
    }

    /**
     *
     * @param password the pwd of a keycloak admin.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return The client id of the client web app configured.
     */
    public String getClientId() {
        return clientId;
    }

    /**
     *
     * @param clientId The client id of the client web app configured.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * @return the {@link Keycloak} REST client.
     */
    public Keycloak getKeycloak(){
        if (keycloak==null) this.keycloak=buildKeycloak();
        return keycloak;
    }

    private Keycloak buildKeycloak(){
        KeycloakBuilder keycloakBuilder=KeycloakBuilder.builder();
        return keycloakBuilder.serverUrl(getServerUrl())
                .realm(getRealm())
                .clientId(getClientId())
                .username(getUsername())
                .password(getPassword())
                .resteasyClient(ResteasyClientBuilder.newClient())
                .build();
    }
}
