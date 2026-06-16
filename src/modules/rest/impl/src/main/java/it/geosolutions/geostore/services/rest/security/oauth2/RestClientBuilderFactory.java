package it.geosolutions.geostore.services.rest.security.oauth2;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public class RestClientBuilderFactory {

    private final ClientHttpRequestFactory requestFactory;

    public RestClientBuilderFactory(ClientHttpRequestFactory requestFactory) {
        this.requestFactory = requestFactory;
    }

    public RestClient createBuilder() {
        // No baseUrl: OAuth2 token/introspection/userinfo calls always use absolute provider URLs.
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
