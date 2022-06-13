package it.geosolutions.geostore.services.rest.security.keycloak;

import it.geosolutions.geostore.services.rest.security.IdPConfiguration;
import org.apache.commons.io.IOUtils;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.adapters.config.AdapterConfig;

/**
 * KeyCloak Configuration.
 */
public class KeyCloakConfiguration extends IdPConfiguration {

    private String jsonConfig;

    private AdapterConfig config;

    /**
     * @return the JSON config, obtained at client configuration time from Keycloak.
     */
    public String getJsonConfig() {
        return jsonConfig;
    }

    /**
     * Set the JsonConfig.
     * @param jsonConfig the jsonConfig as a string.
     */
    public void setJsonConfig(String jsonConfig) {
        this.jsonConfig = jsonConfig;
        if (config!=null){
            config=KeycloakDeploymentBuilder.loadAdapterConfig(
                    IOUtils.toInputStream(getJsonConfig()));
        }
    }

    /**
     * Read the adapter config from the json.
     * @return an {@link AdapterConfig} instance.
     */
    public AdapterConfig readAdapterConfig(){
        if (config==null) {
            config = KeycloakDeploymentBuilder.loadAdapterConfig(
                    IOUtils.toInputStream(getJsonConfig()));
        }
        return config;
    }
}
