package it.geosolutions.geostore.rest.security.keycloak;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.geosolutions.geostore.services.rest.security.keycloak.KeyCloakConfiguration;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.adapters.config.AdapterConfig;

public abstract class KeycloakTestSupport {

    // identifiers for the auth context
    public static final String REALM = "master";
    public static final String CLIENT_ID = "nginx-authn";
    public static final String SECRET = "nginx-secret";

    // locations for useful resources
    public static final String APP_URL = "/app";
    public static final String AUTH_URL = "https://cas.core.maui.mda.ca:8040/auth";



    // some pre-generated data from keycloak that should work until the year 2037
    public static final String PUBLIC_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzkRIC4ow7QqXed+4WICpF5gU2AqXrKT2lPBZOyG6NETv7X"
                    + "g2FmlGA5KIPxcweexgJCcRY1oFEpulBhVo8zc7WVKX1gc8myXvqvdOMHTUMZ0C4l8Q8ls4fE8B4FiALv/48u"
                    + "T1YWXKKvsaBPSeh3QTINwtYsAxIrqTjW5wJVaH8L+EazeKep+JSKPvworT9Q8K4u0XURI9MZi983LEx4Wufc"
                    + "iTPqhD8v6h7Yr+Iy6H/vHHBulwIHZ4MnQBod1aiKuOhM8bsD+FPBVcKCanATVhz6pZoaZXv7j2ZnVSvh6iGi"
                    + "qP80DknLOyY3IqVST9w8KP1UG0upQ+Zsk8ohCg4Qlm6QIDAQAB";
    public static final String JWT_2018_2037 =
            "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJqS2RPZS0zNmhrLVI2R1puQk5tb2JfTFdtMUZJQU"
                    + "tWVXlKblEzTnNuU21RIn0.eyJqdGkiOiIzNTc5MDQ5MS0yNzI5LTRiNTAtOGIwOC1kYzNhYTM1NDE0ZjgiLC"
                    + "JleHAiOjIxMjE4MTY5OTYsIm5iZiI6MCwiaWF0IjoxNTE3MDE2OTk2LCJpc3MiOiJodHRwczovL2Nhcy5jb3"
                    + "JlLm1hdWkubWRhLmNhOjgwNDAvYXV0aC9yZWFsbXMvbWFzdGVyIiwiYXVkIjoibmdpbngtYXV0aG4iLCJzdW"
                    + "IiOiIxMDM3NzU0OC04OTZhLTQwODUtODY2OC0zNmM4OWQzYzU0OTMiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOi"
                    + "JuZ2lueC1hdXRobiIsImF1dGhfdGltZSI6MCwic2Vzc2lvbl9zdGF0ZSI6IjY5MWQwOTZiLTkzNjctNDdlZi"
                    + "04OGEyLTQ1ZjIwZGI4ZjMxNCIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOltdLCJyZWFsbV9hY2Nlc3"
                    + "MiOnsicm9sZXMiOlsiY3JlYXRlLXJlYWxtIiwiYWRtaW4iLCJ1bWFfYXV0aG9yaXphdGlvbiJdfSwicmVzb3"
                    + "VyY2VfYWNjZXNzIjp7Im1hc3Rlci1yZWFsbSI6eyJyb2xlcyI6WyJ2aWV3LWlkZW50aXR5LXByb3ZpZGVycy"
                    + "IsInZpZXctcmVhbG0iLCJtYW5hZ2UtaWRlbnRpdHktcHJvdmlkZXJzIiwiaW1wZXJzb25hdGlvbiIsImNyZW"
                    + "F0ZS1jbGllbnQiLCJtYW5hZ2UtdXNlcnMiLCJxdWVyeS1yZWFsbXMiLCJ2aWV3LWF1dGhvcml6YXRpb24iLC"
                    + "JxdWVyeS1jbGllbnRzIiwicXVlcnktdXNlcnMiLCJtYW5hZ2UtZXZlbnRzIiwibWFuYWdlLXJlYWxtIiwidm"
                    + "lldy1ldmVudHMiLCJ2aWV3LXVzZXJzIiwidmlldy1jbGllbnRzIiwibWFuYWdlLWF1dGhvcml6YXRpb24iLC"
                    + "JtYW5hZ2UtY2xpZW50cyIsInF1ZXJ5LWdyb3VwcyJdfSwiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYW"
                    + "Njb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwicHJlZmVycmVkX3VzZX"
                    + "JuYW1lIjoiYWRtaW4ifQ.deouu-Gqb1MNmfMYARKtkIaM4ztP2tDowG_X0yRxPPSefhQd0rUjLgUl_FS9yiM"
                    + "wJoZBCIYBEvgqBlQW1836SfDTiPXSUlhQRQElJwoXWCS1UaO8neVa-vt8uGo2vBBsOv8pGVM1dsunA3-BMF7"
                    + "P-MX9y0ZmMp4T5VOe4iK3K_uP1teTDyGg455WlL18CsVxKKSvOIrd2xF4M2qNny2fgU7Ca1s-7Jo555VB7fs"
                    + "Uu4nLYvoELb0f_4U4H3Yui_J4m2FplsGoqY7RgM_yTBZ9ZvS-W7ddEjpjyM_D1aFaSByzMYVA6yvnqWIsAVZ"
                    + "e4sZnjoVZM0sMCQtXtNQaUk7Rbg";

    protected AdapterConfig adapterConfig;

    protected void setUpAdapter(String serviceUrl){
        AdapterConfig aConfig = new AdapterConfig();
        aConfig.setRealm(REALM);
        aConfig.setResource(CLIENT_ID);
        aConfig.getCredentials().put("secret",SECRET);
        aConfig.setAuthServerUrl(serviceUrl);
        this.adapterConfig=aConfig;
    }

    protected KeyCloakConfiguration createConfiguration() throws JsonProcessingException {
        ObjectMapper om = new ObjectMapper();
        String stringConfig=om.writeValueAsString(adapterConfig);
        KeyCloakConfiguration configuration=new KeyCloakConfiguration();
        configuration.setJsonConfig(stringConfig);
        configuration.setEnabled(true);
        configuration.setAutoCreateUser(true);
        configuration.setBeanName("keycloakOAuth2Config");
        configuration.setInternalRedirectUri("../../../");
        return configuration;
    }

    protected KeycloakDeployment getKeycloakDeployment(KeyCloakConfiguration configuration){
        return
                KeycloakDeploymentBuilder.build(configuration.readAdapterConfig());
    }
}
