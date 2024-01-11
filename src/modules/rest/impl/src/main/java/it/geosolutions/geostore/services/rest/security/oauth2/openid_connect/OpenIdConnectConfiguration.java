package it.geosolutions.geostore.services.rest.security.oauth2.openid_connect;

import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Configuration;
import it.geosolutions.geostore.services.rest.security.oauth2.OAuth2Utils;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.Collections;

public class OpenIdConnectConfiguration extends OAuth2Configuration {
    String jwkURI;
    String responseMode;
    String postLogoutRedirectUri;
    boolean sendClientSecret = false;
    boolean allowBearerTokens = true;
    boolean usePKCE = false;

    public String getJwkURI() {
        return jwkURI;
    }

    public void setJwkURI(String jwkURI) {
        this.jwkURI = jwkURI;
    }

    public String getResponseMode() {
        return responseMode;
    }

    public void setResponseMode(String responseMode) {
        this.responseMode = responseMode;
    }

    public String getPostLogoutRedirectUri() {
        return postLogoutRedirectUri;
    }

    public void setPostLogoutRedirectUri(String postLogoutRedirectUri) {
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }

    public boolean isSendClientSecret() {
        return sendClientSecret;
    }

    public void setSendClientSecret(boolean sendClientSecret) {
        this.sendClientSecret = sendClientSecret;
    }

    public boolean isAllowBearerTokens() {
        return allowBearerTokens;
    }

    public void setAllowBearerTokens(boolean allowBearerTokens) {
        this.allowBearerTokens = allowBearerTokens;
    }

    public boolean isUsePKCE() {
        return usePKCE;
    }

    public void setUsePKCE(boolean usePKCE) {
        this.usePKCE = usePKCE;
    }

    /**
     * Build the logout endpoint.
     *
     * @param token the current access_token.
     * @return the logout endpoint.
     */
    @Override
    public Endpoint buildLogoutEndpoint(String token) {
        Endpoint result = null;
        String uri = getLogoutUri();
        String idToken = OAuth2Utils.getIdToken();
        if (uri != null) {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            if (idToken != null) params.put("id_token_hint", Collections.singletonList(idToken));
            if (StringUtils.hasText(getPostLogoutRedirectUri()))
                params.put("post_logout_redirect_uri", Collections.singletonList(getPostLogoutRedirectUri()));
            params.put("token", Collections.singletonList(token));
            result = new Endpoint(HttpMethod.GET, appendParameters(params, uri));
        }
        return result;
    }
}
