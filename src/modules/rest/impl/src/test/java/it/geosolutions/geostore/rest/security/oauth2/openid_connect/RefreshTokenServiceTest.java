package it.geosolutions.geostore.rest.security.oauth2.openid_connect;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import it.geosolutions.geostore.services.rest.model.SessionToken;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2SessionServiceDelegate;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils;
import it.geosolutions.geostore.services.rest.security.oauth2.TokenDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RefreshTokenServiceTest {

    @Mock private OAuth2RestTemplate restTemplate;

    @Mock private OAuth2Configuration configuration;

    private TestOAuth2SessionServiceDelegate service;

    private final String accessToken = "testAccessToken";
    private final String refreshToken = "testRefreshToken";
    private final String clientId = "testClientId";
    private final String clientSecret = "testClientSecret";
    private final String refreshTokenUri = "http://test.com/oauth2/refresh";

    @Mock private OAuth2RestTemplate oAuth2RestTemplate;

    @Mock private OAuth2ClientContext clientContext;

    @Mock private TokenAuthenticationCache authenticationCache;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create a spy of the service
        service =
                spy(
                        new TestOAuth2SessionServiceDelegate(
                                (OAuth2RestTemplate) restTemplate, configuration));

        // Mock the cache to return an Authentication with TokenDetails
        Authentication mockAuth = mock(Authentication.class);
        TokenDetails mockTokenDetails = mock(TokenDetails.class);
        OAuth2AccessToken mockOAuth2AccessToken = new DefaultOAuth2AccessToken(accessToken);

        when(authenticationCache.get(accessToken)).thenReturn(mockAuth);
        when(OAuth2Utils.getTokenDetails(mockAuth)).thenReturn(mockTokenDetails);
        when(mockTokenDetails.getAccessToken()).thenReturn(mockOAuth2AccessToken);

        // Mock restTemplate and clientContext
        when(oAuth2RestTemplate.getOAuth2ClientContext()).thenReturn(clientContext);
        when(clientContext.getAccessToken()).thenReturn(mockOAuth2AccessToken);

        // Mock configuration values
        when(configuration.getClientId()).thenReturn(clientId);
        when(configuration.getClientSecret()).thenReturn(clientSecret);
        when(configuration.buildRefreshTokenURI()).thenReturn(refreshTokenUri);

        // Mock HttpServletRequest and set in RequestContextHolder
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        // Mock updateAuthToken method
        doAnswer(
                        invocation -> {
                            String oldAccessToken = invocation.getArgument(0);
                            OAuth2AccessToken newAccessToken = invocation.getArgument(1);
                            OAuth2RefreshToken newRefreshToken = invocation.getArgument(2);
                            OAuth2Configuration config = invocation.getArgument(3);

                            // Remove old access token from cache
                            authenticationCache.removeEntry(oldAccessToken);

                            // Create new Authentication and TokenDetails
                            Authentication newAuth = mock(Authentication.class);
                            TokenDetails newTokenDetails = mock(TokenDetails.class);

                            when(newTokenDetails.getAccessToken()).thenReturn(newAccessToken);
                            when(OAuth2Utils.getTokenDetails(newAuth)).thenReturn(newTokenDetails);

                            // Put new access token and authentication in cache
                            when(authenticationCache.get(newAccessToken.getValue()))
                                    .thenReturn(newAuth);

                            return null;
                        })
                .when(service)
                .updateAuthToken(
                        anyString(),
                        any(OAuth2AccessToken.class),
                        any(OAuth2RefreshToken.class),
                        any(OAuth2Configuration.class));
    }

    @AfterEach
    void tearDown() {
        // Clear RequestContextHolder after each test to avoid interference with other tests
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testDoRefresh_SuccessfulRefresh() {
        // Mocking a successful response
        OAuth2AccessToken mockToken = new DefaultOAuth2AccessToken("newAccessToken");
        ResponseEntity<OAuth2AccessToken> responseEntity =
                new ResponseEntity<>(mockToken, HttpStatus.OK);

        when(restTemplate.exchange(
                        eq(refreshTokenUri),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(OAuth2AccessToken.class)))
                .thenReturn(responseEntity);

        SessionToken sessionToken = service.refresh(refreshToken, accessToken);

        assertNotNull(sessionToken, "SessionToken should not be null");
        assertEquals(
                "newAccessToken",
                sessionToken.getAccessToken(),
                "Access token should match the new token");
    }

    @Test
    void testDoRefresh_ClientError_NoRetry() {
        // Mocking a 4xx client error response
        ResponseEntity<OAuth2AccessToken> responseEntity =
                new ResponseEntity<>(HttpStatus.UNAUTHORIZED);

        when(restTemplate.exchange(
                        eq(refreshTokenUri),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(OAuth2AccessToken.class)))
                .thenReturn(responseEntity);

        SessionToken sessionToken = service.refresh(refreshToken, accessToken);

        assertNotNull(sessionToken, "SessionToken should not be null");
        assertEquals(
                "testAccessToken",
                sessionToken.getAccessToken(),
                "Access token should match the old token");
    }

    @Test
    void testDoRefresh_ServerError_WithRetry() {
        // Mocking a 5xx server error response
        ResponseEntity<OAuth2AccessToken> responseEntity =
                new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.exchange(
                        eq(refreshTokenUri),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(OAuth2AccessToken.class)))
                .thenReturn(responseEntity);

        SessionToken sessionToken = service.refresh(refreshToken, accessToken);

        assertNotNull(sessionToken, "SessionToken should not be null");
        assertEquals(
                "testAccessToken",
                sessionToken.getAccessToken(),
                "Access token should match the old token");
    }

    @Test
    void testDoRefresh_NullResponseFromServer() {
        // Mocking a null response body
        ResponseEntity<OAuth2AccessToken> responseEntity =
                new ResponseEntity<>(null, HttpStatus.OK);

        when(restTemplate.exchange(
                        eq(refreshTokenUri),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(OAuth2AccessToken.class)))
                .thenReturn(responseEntity);

        SessionToken sessionToken = service.refresh(refreshToken, accessToken);

        assertNotNull(sessionToken, "SessionToken should not be null");
        assertEquals(
                "testAccessToken",
                sessionToken.getAccessToken(),
                "Access token should match the old token");
    }
}

// Concrete test subclass for the abstract class
class TestOAuth2SessionServiceDelegate extends OAuth2SessionServiceDelegate {
    private final OAuth2RestTemplate oAuth2RestTemplate;

    public TestOAuth2SessionServiceDelegate(
            OAuth2RestTemplate oAuth2RestTemplate, OAuth2Configuration configuration) {
        super(oAuth2RestTemplate, configuration);
        this.oAuth2RestTemplate = oAuth2RestTemplate;
    }

    @Override
    protected OAuth2RestTemplate restTemplate() {
        return oAuth2RestTemplate;
    }

    // Override the updateAuthToken method
    protected void updateAuthToken(
            String oldAccessToken,
            OAuth2AccessToken newAccessToken,
            OAuth2RefreshToken newRefreshToken,
            OAuth2Configuration configuration) {
        // For testing purposes, you can provide a mock implementation or leave it empty
        // If needed, update the authentication cache or context here
    }
}
