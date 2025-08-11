package it.geosolutions.geostore.services.rest.security.keycloak;

import static org.junit.Assert.assertNull;

import org.junit.Test;

public class KeyCloakConfigurationTest {

    @Test
    public void testNPENotThrownOnNullJSONConfig() {
        KeyCloakConfiguration configuration = new KeyCloakConfiguration();
        assertNull(configuration.readAdapterConfig());
    }
}
