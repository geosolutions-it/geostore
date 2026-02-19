# Google OAuth2 Setup

## Overview

GeoStore provides a **dedicated Google OAuth2 integration** that is separate from the generic OIDC provider. The dedicated provider uses Google's OAuth2 endpoints with `offline` access type, which ensures a refresh token is returned during the authorization code flow. Configuration uses the `googleOAuth2Config.` property prefix in `geostore-ovr.properties`.

Alternatively, you can use the **generic OIDC provider** (`oidcOAuth2Config.` prefix) with Google's standard OpenID Connect discovery URL. This approach gives you access to additional features such as bearer token validation and PKCE support.

!!! note
    Both providers can coexist in the same GeoStore deployment. Each maintains its own token cache and authentication filter chain.

## Prerequisites

Before configuring Google OAuth2 with GeoStore, ensure you have the following:

- Access to the [Google Cloud Console](https://console.cloud.google.com/)
- A project in Google Cloud Platform (GCP)
- GeoStore accessible via **HTTPS** (Google requires HTTPS for production redirect URIs)

!!! warning
    Google enforces HTTPS for all redirect URIs in production. During development, `http://localhost` is allowed, but any other non-HTTPS URI will be rejected.

## Google Cloud Console Setup

### Step 1: Create OAuth2 Credentials

1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Select an existing project or create a new one.
3. Navigate to **APIs & Services** > **Credentials**.
4. Click **Create Credentials** > **OAuth client ID**.
5. Select **Application type**: `Web application`.
6. Set the **Name** to `GeoStore` (or any descriptive name).
7. Under **Authorized redirect URIs**, add:
    ```
    https://your-geostore-host/geostore/rest/users/user/details
    ```
8. Click **Create**.
9. Note the **Client ID** and **Client Secret** -- you will need these for GeoStore configuration.

!!! tip
    You can add multiple redirect URIs if you have different environments (e.g. `https://localhost:8443/geostore/rest/users/user/details` for local development).

### Step 2: Configure OAuth Consent Screen

1. Go to **APIs & Services** > **OAuth consent screen**.
2. Choose the **User Type**:
    - **Internal** -- only available for Google Workspace organizations; limits login to users within your organization.
    - **External** -- available to any Google account; requires verification for production use.
3. Fill in the required fields: application name, user support email, and developer contact information.
4. Add the following **scopes**:
    - `openid`
    - `email`
    - `profile`
5. If you selected **External** and the app is in **Testing** status, add your test users under the **Test users** section.

!!! warning
    External apps in "Testing" status are limited to 100 test users. Users not on the list will see an "Access blocked" error. To remove this restriction, submit the app for verification.

## GeoStore Configuration

GeoStore provides two ways to integrate with Google. Choose the one that fits your requirements.

=== "Dedicated Google Provider"

    Uses the `googleOAuth2Config.` prefix with Google-specific defaults. The dedicated provider automatically sets the access type to `offline`, ensuring a refresh token is returned.

    ```properties
    # -----------------------------------------------
    # Google OAuth2 — Dedicated Provider
    # -----------------------------------------------

    # Enable Google OAuth2 authentication
    googleOAuth2Config.enabled=true

    # Google OAuth2 client credentials (from Cloud Console)
    googleOAuth2Config.clientId=YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com
    googleOAuth2Config.clientSecret=YOUR_CLIENT_SECRET

    # Discovery URL — auto-fills all Google endpoints
    googleOAuth2Config.discoveryUrl=https://accounts.google.com/.well-known/openid-configuration

    # OAuth2 redirect URI — must match the one registered in Cloud Console
    googleOAuth2Config.redirectUri=https://your-geostore-host/geostore/rest/users/user/details

    # After successful login, redirect the browser here
    googleOAuth2Config.internalRedirectUri=../../mapstore/

    # Auto-create users in GeoStore DB on first Google login
    googleOAuth2Config.autoCreateUser=true

    # Default role for authenticated Google users
    googleOAuth2Config.authenticatedDefaultRole=USER

    # Use the email claim as the GeoStore username
    googleOAuth2Config.principalKey=email

    # Scopes to request from Google
    googleOAuth2Config.scopes=openid,email,profile
    ```

=== "Generic OIDC Provider"

    Uses the `oidcOAuth2Config.` prefix. This approach is useful when you need OIDC features such as bearer token validation, PKCE, or introspection.

    ```properties
    # -----------------------------------------------
    # Google via Generic OIDC Provider
    # -----------------------------------------------

    # Enable OIDC authentication
    oidcOAuth2Config.enabled=true

    # Google OAuth2 client credentials (from Cloud Console)
    oidcOAuth2Config.clientId=YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com
    oidcOAuth2Config.clientSecret=YOUR_CLIENT_SECRET

    # Discovery URL — auto-fills all Google endpoints
    oidcOAuth2Config.discoveryUrl=https://accounts.google.com/.well-known/openid-configuration

    # OAuth2 redirect URI — must match the one registered in Cloud Console
    oidcOAuth2Config.redirectUri=https://your-geostore-host/geostore/rest/users/user/details

    # After successful login, redirect the browser here
    oidcOAuth2Config.internalRedirectUri=../../mapstore/

    # Auto-create users in GeoStore DB on first login
    oidcOAuth2Config.autoCreateUser=true

    # Default role for authenticated users
    oidcOAuth2Config.authenticatedDefaultRole=USER

    # Use the email claim as the GeoStore username
    oidcOAuth2Config.principalKey=email

    # Scopes to request
    oidcOAuth2Config.scopes=openid,email,profile

    # Send client secret in the token request body
    oidcOAuth2Config.sendClientSecret=true

    # Enable JWT-based bearer token validation for API clients
    oidcOAuth2Config.bearerTokenStrategy=jwt
    ```

## Google-Specific Properties

The following table lists all properties available under the `googleOAuth2Config.` prefix.

| Property | Type | Default | Description |
|---|---|---|---|
| `googleOAuth2Config.enabled` | boolean | `false` | Enable Google OAuth2 authentication |
| `googleOAuth2Config.clientId` | String | -- | Google OAuth2 client ID (from Cloud Console) |
| `googleOAuth2Config.clientSecret` | String | -- | Google OAuth2 client secret |
| `googleOAuth2Config.discoveryUrl` | String | -- | Set to `https://accounts.google.com/.well-known/openid-configuration` |
| `googleOAuth2Config.redirectUri` | String | -- | Authorized redirect URI (must match Cloud Console) |
| `googleOAuth2Config.internalRedirectUri` | String | -- | Internal redirect after successful callback |
| `googleOAuth2Config.autoCreateUser` | boolean | `false` | Auto-create users in GeoStore DB on first login |
| `googleOAuth2Config.authenticatedDefaultRole` | String | `USER` | Default role for authenticated users (`ADMIN`, `USER`, or `GUEST`) |
| `googleOAuth2Config.principalKey` | String | `email` | JWT claim used to resolve the username |
| `googleOAuth2Config.scopes` | String | (from discovery) | Comma-separated scopes to request |
| `googleOAuth2Config.rolesClaim` | String | -- | JWT claim path for roles |
| `googleOAuth2Config.groupsClaim` | String | -- | JWT claim path for groups |

!!! tip
    When using `discoveryUrl`, endpoint properties (`authorizationUri`, `accessTokenUri`, etc.) are auto-filled from Google's discovery document. You do not need to set them manually. See the [OIDC / OAuth2 Configuration](../security/oidc.md#discovery) page for details on the discovery auto-fill behavior.

## Google OAuth2 Scopes and Claims

Google supports the standard OpenID Connect scopes. The following table shows which claims are returned for each scope.

| Scope | Claims Provided |
|---|---|
| `openid` | `sub` (subject identifier) |
| `email` | `email`, `email_verified` |
| `profile` | `name`, `given_name`, `family_name`, `picture` |

!!! note
    Google's standard OAuth2 does not provide roles or groups claims. If you need role or group mapping from Google, you must manage role assignments in GeoStore directly, or use the [Google Workspace Admin SDK](https://developers.google.com/admin-sdk) to derive group memberships through a custom integration.

## Differences: Dedicated vs Generic Provider

The following table summarizes the key differences between the two integration approaches.

| Feature | Dedicated (`googleOAuth2Config`) | Generic (`oidcOAuth2Config`) |
|---|---|---|
| Access type | `offline` (automatic -- refresh token returned) | Not set (no refresh token by default) |
| Bearer token validation | Not supported | Full support (`jwt`, `introspection`, `auto`) |
| PKCE | Not supported | Supported (`usePKCE=true`) |
| Token cache | Separate cache instance | Separate cache instance |
| Can coexist with other providers | Yes | Yes |
| Configuration prefix | `googleOAuth2Config.` | `oidcOAuth2Config.` |

!!! tip
    If you only need browser-based login with Google, the dedicated provider is simpler to configure and automatically handles refresh tokens. If you also need bearer token support for API clients (e.g. machine-to-machine authentication), use the generic OIDC provider.

## Testing the Configuration

After configuring Google OAuth2, you can verify the setup by navigating to the login endpoint in your browser:

=== "Dedicated Google Provider"

    ```
    https://your-geostore-host/geostore/rest/users/user/details?provider=google
    ```

=== "Generic OIDC Provider"

    ```
    https://your-geostore-host/geostore/rest/users/user/details?provider=oidc
    ```

If the configuration is correct, you will be redirected to Google's login page. After authenticating, Google will redirect back to GeoStore and complete the login flow.

## Troubleshooting

| Problem | Solution |
|---|---|
| `redirect_uri_mismatch` error | Ensure the `redirectUri` property value **exactly** matches the Authorized redirect URI in Google Cloud Console (including trailing slashes and protocol). |
| No email in token | Add the `email` scope to the `scopes` property: `googleOAuth2Config.scopes=openid,email,profile`. |
| Refresh token not returned | The dedicated Google provider uses `offline` access by default. For the generic OIDC provider, the `offline` access type is not set automatically -- Google will not return a refresh token. |
| "Access blocked: app has not completed verification" | Either complete the OAuth consent screen verification process in Google Cloud Console, or add your test users under **OAuth consent screen** > **Test users**. |
| `invalid_client` error | Double-check that the `clientId` and `clientSecret` match the credentials from Google Cloud Console. Ensure no extra whitespace. |
| User created but no roles | Google does not provide role claims. Set `authenticatedDefaultRole` to assign a default role, or manage roles directly in GeoStore after user creation. |
| Token expired errors after restart | The token cache is in-memory and does not survive restarts. Users will need to re-authenticate after a GeoStore restart. |

!!! tip "Enable debug logging"
    To troubleshoot authentication issues, enable debug logging for the security module by adding the following to your logging configuration:
    ```properties
    log4j.logger.it.geosolutions.geostore.services.rest.security=DEBUG
    log4j.logger.org.springframework.security.oauth2=DEBUG
    ```
