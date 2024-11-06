package it.geosolutions.geostore.rest.security.oauth2.openid_connect;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import it.geosolutions.geostore.services.rest.model.SessionToken;
import it.geosolutions.geostore.services.rest.security.TokenAuthenticationCache;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2SessionServiceDelegate;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils;
import it.geosolutions.geostore.services.rest.security.oauth2.TokenDetails;
import java.util.Date;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.common.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Test class for OAuth2SessionServiceDelegate. */
class RefreshTokenServiceTest {

    private TestOAuth2SessionServiceDelegate serviceDelegate;
    private OAuth2Configuration configuration;
    private OAuth2RestTemplate restTemplate;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private DefaultOAuth2AccessToken mockOAuth2AccessToken;

    @Mock private TokenAuthenticationCache authenticationCache;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Initialize mocks and dependencies
        configuration = mock(OAuth2Configuration.class);
        restTemplate = mock(OAuth2RestTemplate.class);
        authenticationCache = mock(TokenAuthenticationCache.class);

        // Create an instance of the test subclass
        serviceDelegate = spy(new TestOAuth2SessionServiceDelegate());
        serviceDelegate.setRestTemplate(restTemplate);
        serviceDelegate.setConfiguration(configuration);
        serviceDelegate.authenticationCache = authenticationCache;

        // Set up mock request and response
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        // Set the RequestAttributes in RequestContextHolder
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        // Mock configuration behavior
        when(configuration.isEnabled()).thenReturn(true);
        when(configuration.getClientId()).thenReturn("testClientId");
        when(configuration.getClientSecret()).thenReturn("testClientSecret");
        when(configuration.buildRefreshTokenURI()).thenReturn("https://example.com/oauth2/token");

        // Mock the existing OAuth2AccessToken with a refresh token
        mockOAuth2AccessToken = new DefaultOAuth2AccessToken("providedAccessToken");
        OAuth2RefreshToken mockRefreshToken = new DefaultOAuth2RefreshToken("existingRefreshToken");
        mockOAuth2AccessToken.setRefreshToken(mockRefreshToken);
        mockOAuth2AccessToken.setExpiration(new Date(System.currentTimeMillis() + 3600 * 1000));

        // Initialize currentAccessToken
        serviceDelegate.currentAccessToken = mockOAuth2AccessToken;

        // Mock the Authentication object
        Authentication mockAuthentication = mock(Authentication.class);

        // Mock TokenDetails
        TokenDetails mockTokenDetails = mock(TokenDetails.class);
        when(mockTokenDetails.getIdToken()).thenReturn("mockIdToken");

        // Mock getTokenDetails(authentication) to return mockTokenDetails
        doReturn(mockTokenDetails).when(serviceDelegate).getTokenDetails(mockAuthentication);

        // Ensure cache returns the mocked Authentication for oldToken
        when(authenticationCache.get("providedAccessToken")).thenReturn(mockAuthentication);

        // Optionally, set up SecurityContextHolder
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(mockAuthentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        // Clear the RequestContextHolder after each test
        RequestContextHolder.resetRequestAttributes();

        // Close static mocks if any
        // For Mockito 3.4.0 and above
        Mockito.framework().clearInlineMocks();
    }

    @Test
    void testRefreshWithValidTokens() {
        // Arrange
        String refreshToken = "providedRefreshToken";
        String accessToken = "providedAccessToken";

        // Mock the RestTemplate exchange method to simulate a successful token refresh
        DefaultOAuth2AccessToken newAccessToken = new DefaultOAuth2AccessToken("newAccessToken");
        OAuth2RefreshToken newRefreshToken = new DefaultOAuth2RefreshToken("newRefreshToken");
        newAccessToken.setRefreshToken(newRefreshToken);
        newAccessToken.setExpiration(
                new Date(System.currentTimeMillis() + 7200 * 1000)); // Expires in 2 hours

        ResponseEntity<OAuth2AccessToken> responseEntity =
                new ResponseEntity<>(newAccessToken, HttpStatus.OK);

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(OAuth2AccessToken.class)))
                .thenReturn(responseEntity);

        // Act
        SessionToken sessionToken = serviceDelegate.refresh(refreshToken, accessToken);

        // Assert
        assertNotNull(sessionToken, "SessionToken should not be null");
        assertEquals(
                "newAccessToken", sessionToken.getAccessToken(), "Access token should be updated");
        assertEquals(
                "newRefreshToken",
                sessionToken.getRefreshToken(),
                "Refresh token should be updated");
        assertTrue(
                sessionToken.getExpires() > System.currentTimeMillis(),
                "Token expiration should be in the future");
        assertEquals("bearer", sessionToken.getTokenType(), "Token type should be 'bearer'");
    }

    @Test
    void testRefreshWithInvalidRefreshToken() {
        // Arrange
        String refreshToken = "invalidRefreshToken";
        String accessToken = "providedAccessToken";

        // Mock the RestTemplate exchange method to simulate a client error (400 Bad Request)
        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(OAuth2AccessToken.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        // Act
        SessionToken sessionToken = serviceDelegate.refresh(refreshToken, accessToken);

        // Assert
        assertNotNull(sessionToken, "SessionToken should not be null even when refresh fails");
        assertEquals(
                "providedAccessToken",
                sessionToken.getAccessToken(),
                "Access token should remain unchanged");
        assertEquals(
                "existingRefreshToken",
                sessionToken.getRefreshToken(),
                "Refresh token should remain unchanged");
    }

    @Test
    void testRefreshWithServerError() {
        // Arrange
        String refreshToken = "providedRefreshToken";
        String accessToken = "providedAccessToken";

        // Mock the RestTemplate exchange method to simulate a server error (500 Internal Server
        // Error)
        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(OAuth2AccessToken.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // Act
        SessionToken sessionToken = serviceDelegate.refresh(refreshToken, accessToken);

        // Assert
        assertNotNull(sessionToken, "SessionToken should not be null even when refresh fails");
        assertEquals(
                "providedAccessToken",
                sessionToken.getAccessToken(),
                "Access token should remain unchanged after server error");
        assertEquals(
                "existingRefreshToken",
                sessionToken.getRefreshToken(),
                "Refresh token should remain unchanged after server error");
        // You can also verify that the method retried the expected number of times
        verify(restTemplate, times(3))
                .exchange(
                        anyString(),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(OAuth2AccessToken.class));
    }

    @Test
    void testRefreshWithNullResponse() {
        // Arrange
        String refreshToken = "providedRefreshToken";
        String accessToken = "providedAccessToken";

        // Mock the RestTemplate exchange method to return a response with null body
        ResponseEntity<OAuth2AccessToken> responseEntity =
                new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(OAuth2AccessToken.class)))
                .thenReturn(responseEntity);

        // Act
        SessionToken sessionToken = serviceDelegate.refresh(refreshToken, accessToken);

        // Assert
        assertNotNull(sessionToken, "SessionToken should not be null even when response is null");
        assertEquals(
                "providedAccessToken",
                sessionToken.getAccessToken(),
                "Access token should remain unchanged");
        assertEquals(
                "existingRefreshToken",
                sessionToken.getRefreshToken(),
                "Refresh token should remain unchanged");
    }

    @Test
    void testRefreshWhenConfigurationDisabled() {
        // Arrange
        String refreshToken = "providedRefreshToken";
        String accessToken = "providedAccessToken";

        // Mock configuration to be disabled
        when(configuration.isEnabled()).thenReturn(false);

        // Act
        SessionToken sessionToken = serviceDelegate.refresh(refreshToken, accessToken);

        // Assert
        assertNotNull(
                sessionToken, "SessionToken should not be null when configuration is disabled");
        assertEquals(
                "providedAccessToken",
                sessionToken.getAccessToken(),
                "Access token should remain unchanged");
        assertEquals(
                "existingRefreshToken",
                sessionToken.getRefreshToken(),
                "Refresh token should remain unchanged");
        // Verify that no exchange was attempted
        verify(restTemplate, never())
                .exchange(
                        anyString(),
                        any(HttpMethod.class),
                        any(HttpEntity.class),
                        eq(OAuth2AccessToken.class));
    }

    @Test
    void testRefreshWithMissingAccessToken() {
        // Arrange
        String refreshToken = "providedRefreshToken";
        String accessToken = null; // Access token is missing

        // Act & Assert
        Exception exception =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            serviceDelegate.refresh(refreshToken, accessToken);
                        });

        assertTrue(
                exception
                        .getMessage()
                        .contains("Either the accessToken or the refresh token are missing"),
                "Expected exception message");
    }

    @Test
    void testRefreshWhenCacheReturnsNullAuthentication() {
        // Arrange
        String refreshToken = "providedRefreshToken";
        String accessToken = "providedAccessToken";

        // Mock cache to return null
        when(authenticationCache.get("providedAccessToken")).thenReturn(null);

        // Act
        SessionToken sessionToken = serviceDelegate.refresh(refreshToken, accessToken);

        // Assert
        assertNotNull(
                sessionToken,
                "SessionToken should not be null even when authentication is not found in cache");
        assertEquals(
                "providedAccessToken",
                sessionToken.getAccessToken(),
                "Access token should remain unchanged");
        assertEquals(
                "existingRefreshToken",
                sessionToken.getRefreshToken(),
                "Refresh token should remain unchanged");
    }

    @Test
    void testRefreshWhenAuthenticationIsAnonymous() {
        // Arrange
        String refreshToken = "providedRefreshToken";
        String accessToken = "providedAccessToken";

        // Mock an AnonymousAuthenticationToken
        Authentication anonymousAuthentication =
                mock(
                        org.springframework.security.authentication.AnonymousAuthenticationToken
                                .class);
        when(authenticationCache.get("providedAccessToken")).thenReturn(anonymousAuthentication);

        // Act
        SessionToken sessionToken = serviceDelegate.refresh(refreshToken, accessToken);

        // Assert
        assertNotNull(
                sessionToken,
                "SessionToken should not be null even when authentication is anonymous");
        assertEquals(
                "providedAccessToken",
                sessionToken.getAccessToken(),
                "Access token should remain unchanged");
        assertEquals(
                "existingRefreshToken",
                sessionToken.getRefreshToken(),
                "Refresh token should remain unchanged");
    }

    @Test
    void testRefreshWithExpiredAccessToken() {
        // Arrange
        String refreshToken = "providedRefreshToken";
        String accessToken = "expiredAccessToken";

        // Set the current access token to be expired
        mockOAuth2AccessToken.setExpiration(
                new Date(System.currentTimeMillis() - 1000)); // Set expiration in the past
        serviceDelegate.currentAccessToken = mockOAuth2AccessToken;

        // Mock the RestTemplate exchange method to simulate a successful token refresh
        DefaultOAuth2AccessToken newAccessToken = new DefaultOAuth2AccessToken("newAccessToken");
        OAuth2RefreshToken newRefreshToken = new DefaultOAuth2RefreshToken("newRefreshToken");
        newAccessToken.setRefreshToken(newRefreshToken);
        newAccessToken.setExpiration(
                new Date(System.currentTimeMillis() + 7200 * 1000)); // Expires in 2 hours

        ResponseEntity<OAuth2AccessToken> responseEntity =
                new ResponseEntity<>(newAccessToken, HttpStatus.OK);

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(OAuth2AccessToken.class)))
                .thenReturn(responseEntity);

        // Act
        SessionToken sessionToken = serviceDelegate.refresh(refreshToken, accessToken);

        // Assert
        assertNotNull(sessionToken, "SessionToken should not be null");
        assertEquals(
                "newAccessToken", sessionToken.getAccessToken(), "Access token should be updated");
        assertEquals(
                "newRefreshToken",
                sessionToken.getRefreshToken(),
                "Refresh token should be updated");
        assertTrue(
                sessionToken.getExpires() > System.currentTimeMillis(),
                "Token expiration should be in the future");
    }

    /** Test subclass of OAuth2SessionServiceDelegate for testing purposes. */
    class TestOAuth2SessionServiceDelegate extends OAuth2SessionServiceDelegate {

        private OAuth2RestTemplate restTemplate;
        private OAuth2Configuration configuration;
        private OAuth2AccessToken currentAccessToken;
        protected TokenAuthenticationCache authenticationCache;

        public TestOAuth2SessionServiceDelegate() {
            super(null, null); // Mocked dependencies
        }

        public void setRestTemplate(OAuth2RestTemplate restTemplate) {
            this.restTemplate = restTemplate;
        }

        public void setConfiguration(OAuth2Configuration configuration) {
            this.configuration = configuration;
        }

        @Override
        protected OAuth2RestTemplate restTemplate() {
            return restTemplate;
        }

        @Override
        protected OAuth2Configuration configuration() {
            return configuration;
        }

        @Override
        protected HttpServletRequest getRequest() {
            return mockRequest;
        }

        @Override
        protected HttpServletResponse getResponse() {
            return mockResponse;
        }

        @Override
        protected TokenDetails getTokenDetails(Authentication authentication) {
            // This method is now mocked in the test setup using doReturn()
            return super.getTokenDetails(authentication);
        }

        @Override
        protected OAuth2AccessToken retrieveAccessToken(String accessToken) {
            return currentAccessToken;
        }

        @Override
        protected TokenAuthenticationCache cache() {
            return authenticationCache; // Return the mocked cache
        }

        @Override
        protected void updateAuthToken(
                String oldAccessToken,
                OAuth2AccessToken newAccessToken,
                OAuth2RefreshToken newRefreshToken,
                OAuth2Configuration configuration) {
            // Update the currentAccessToken to the newAccessToken
            this.currentAccessToken = newAccessToken;

            // Simulate updating the authentication in the cache
            Authentication newAuthentication = mock(Authentication.class);
            TokenDetails newTokenDetails = mock(TokenDetails.class);
            when(newTokenDetails.getAccessToken()).thenReturn(newAccessToken);
            when(OAuth2Utils.getTokenDetails(newAuthentication)).thenReturn(newTokenDetails);

            // Remove the old token from the cache
            authenticationCache.removeEntry(oldAccessToken);

            // Add the new token to the cache
            authenticationCache.putCacheEntry(newAccessToken.getValue(), newAuthentication);
        }
    }
}
