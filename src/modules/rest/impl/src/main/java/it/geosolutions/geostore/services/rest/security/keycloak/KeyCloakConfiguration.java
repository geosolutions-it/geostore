package it.geosolutions.geostore.services.rest.security.keycloak;

import it.geosolutions.geostore.services.rest.security.IdPConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.springsecurity.KeycloakConfiguration;
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
        if (config!=null && StringUtils.isNotBlank(this.jsonConfig)){
            config=KeycloakDeploymentBuilder.loadAdapterConfig(
                    IOUtils.toInputStream(getJsonConfig()));
        }
    }

    /**
     * Read the adapter config from the json.
     * @return an {@link AdapterConfig} instance.
     */
    public AdapterConfig readAdapterConfig(){
        String jsonConfig=getJsonConfig();
        if (config==null && StringUtils.isNotBlank(jsonConfig)) {
            config = KeycloakDeploymentBuilder.loadAdapterConfig(
                    IOUtils.toInputStream(jsonConfig));
        }
        return config;
    }
}
