package it.geosolutions.geostore.rest.security.keycloak;

import it.geosolutions.geostore.services.rest.security.keycloak.KeyCloakConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertNull;

public class KeyCloakConfigurationTest {

    @Test
    public void testNPENotThrownOnNullJSONConfig() {
        KeyCloakConfiguration configuration = new KeyCloakConfiguration();
        assertNull(configuration.readAdapterConfig());
    }

}
