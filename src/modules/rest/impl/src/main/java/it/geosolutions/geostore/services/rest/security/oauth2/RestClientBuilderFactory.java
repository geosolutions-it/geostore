package it.geosolutions.geostore.services.rest.security.oauth2;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public class RestClientBuilderFactory {

    private final ClientHttpRequestFactory requestFactory;

    public RestClientBuilderFactory(ClientHttpRequestFactory requestFactory) {
        this.requestFactory = requestFactory;
    }

    public RestClient createBuilder() {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("http://localhost:9000")
                .build();
    }
}
