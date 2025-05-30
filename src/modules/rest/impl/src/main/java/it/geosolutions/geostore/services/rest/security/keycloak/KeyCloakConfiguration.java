/* ====================================================================
 *
 * Copyright (C) 2022 GeoSolutions S.A.S.
 * http://www.geo-solutions.it
 *
 * GPLv3 + Classpath exception
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 *
 * ====================================================================
 *
 * This software consists of voluntary contributions made by developers
 * of GeoSolutions.  For more information on GeoSolutions, please see
 * <http://www.geo-solutions.it/>.
 *
 */
package it.geosolutions.geostore.services.rest.security.keycloak;

import it.geosolutions.geostore.services.rest.security.IdPConfiguration;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.enums.TokenStore;
import org.keycloak.representations.adapters.config.AdapterConfig;

/** KeyCloak Configuration. */
public class KeyCloakConfiguration extends IdPConfiguration {

    private String jsonConfig;

    private AdapterConfig config;

    private Boolean forceConfiguredRedirectURI;

    private Map<String, String> roleMappings;

    private Map<String, String> groupMappings;

    private boolean dropUnmapped = false;

    /** @return the JSON config, obtained at client configuration time from Keycloak. */
    public String getJsonConfig() {
        return jsonConfig;
    }

    /**
     * Set the JsonConfig.
     *
     * @param jsonConfig the jsonConfig as a string.
     */
    public void setJsonConfig(String jsonConfig) {
        this.jsonConfig = jsonConfig;
        if (config != null && StringUtils.isNotBlank(this.jsonConfig)) {
            config =
                    KeycloakDeploymentBuilder.loadAdapterConfig(
                            IOUtils.toInputStream(getJsonConfig()));
        }
    }

    private Map<String, String> toMap(String mappings) {
        if (mappings != null) {
            String[] keyValues = mappings.split(",");
            Map<String, String> map = new AuthoritiesMappings(keyValues.length);
            for (String keyValue : keyValues) {
                String[] keyValueAr = keyValue.split(":");
                map.put(keyValueAr[0], keyValueAr[1]);
            }
            return map;
        }
        return null;
    }

    public Map<String, String> getRoleMappings() {
        return roleMappings;
    }

    public void setRoleMappings(String roleMappings) {
        this.roleMappings = toMap(roleMappings);
    }

    public Map<String, String> getGroupMappings() {
        return groupMappings;
    }

    public void setGroupMappings(String groupMappings) {
        this.groupMappings = toMap(groupMappings);
    }

    public boolean isDropUnmapped() {
        return dropUnmapped;
    }

    public void setDropUnmapped(boolean dropUnmapped) {
        this.dropUnmapped = dropUnmapped;
    }

    /**
     * Read the adapter config from the json.
     *
     * @return an {@link AdapterConfig} instance.
     */
    public AdapterConfig readAdapterConfig() {
        String jsonConfig = getJsonConfig();
        if (config == null && StringUtils.isNotBlank(jsonConfig)) {
            config = KeycloakDeploymentBuilder.loadAdapterConfig(IOUtils.toInputStream(jsonConfig));
            config.setTokenStore(TokenStore.COOKIE.name());
        }
        return config;
    }

    public Boolean getForceConfiguredRedirectURI() {
        if (forceConfiguredRedirectURI == null) return false;
        return forceConfiguredRedirectURI;
    }

    public void setForceConfiguredRedirectURI(Boolean forceConfiguredRedirectURI) {
        this.forceConfiguredRedirectURI = forceConfiguredRedirectURI;
    }
}
