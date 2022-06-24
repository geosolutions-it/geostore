package it.geosolutions.geostore.services.rest.security.keycloak;

import it.geosolutions.geostore.services.rest.security.IdPConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.enums.TokenStore;
import org.keycloak.representations.adapters.config.AdapterConfig;

import java.util.Map;

/**
 * KeyCloak Configuration.
 */
public class KeyCloakConfiguration extends IdPConfiguration {


    private String jsonConfig;

    private AdapterConfig config;

    private Boolean forceConfiguredRedirectURI;

    private Map<String,String> roleMappings;

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

    public void setRoleMappings(String roleMappings) {
        if (roleMappings!=null) {
            String[] keyValues = roleMappings.split(",");
            Map<String, String> mappings = new RoleMappings(keyValues.length);
            for (String keyValue : keyValues) {
                String[] keyValueAr = keyValue.split(":");
                mappings.put(keyValueAr[0], keyValueAr[1]);
            }
            this.roleMappings = mappings;
        }
    }

    public Map<String, String> getRoleMappings(){
        return roleMappings;
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
            config.setTokenStore(TokenStore.COOKIE.name());
        }
        return config;
    }

    public Boolean getForceConfiguredRedirectURI() {
        if (forceConfiguredRedirectURI==null) return false;
        return forceConfiguredRedirectURI;
    }

    public void setForceConfiguredRedirectURI(Boolean forceConfiguredRedirectURI) {
        this.forceConfiguredRedirectURI = forceConfiguredRedirectURI;
    }
}
