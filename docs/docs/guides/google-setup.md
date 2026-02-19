# Google OAuth2 Setup

## Overview

GeoStore integrates with Google as an OpenID Connect (OIDC) provider through the generic OIDC integration. When used as the only provider, configuration uses the `oidcOAuth2Config.` property prefix in `geostore-ovr.properties`. When used alongside other providers, you can use any provider name (e.g. `googleOAuth2Config.`). This gives you access to all OIDC features including bearer token validation, PKCE support, and JWKS signature verification.

!!! tip
    To request a refresh token from Google, set `oidcOAuth2Config.accessType=offline`. This appends `access_type=offline` to the authorization URL, which tells Google to return a refresh token during the authorization code flow.

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

Add the following properties to `geostore-ovr.properties`:

```properties
# -----------------------------------------------
# Google via OIDC Provider
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

# Request offline access for refresh token support
oidcOAuth2Config.accessType=offline

# Enable JWT-based bearer token validation for API clients
oidcOAuth2Config.bearerTokenStrategy=jwt
```

## Google OAuth2 Scopes and Claims

Google supports the standard OpenID Connect scopes. The following table shows which claims are returned for each scope.

| Scope | Claims Provided |
|---|---|
| `openid` | `sub` (subject identifier) |
| `email` | `email`, `email_verified` |
| `profile` | `name`, `given_name`, `family_name`, `picture` |

!!! note
    Google's standard OAuth2 does not provide roles or groups claims. If you need role or group mapping from Google, you must manage role assignments in GeoStore directly, or use the [Google Workspace Admin SDK](https://developers.google.com/admin-sdk) to derive group memberships through a custom integration.

## Testing the Configuration

After configuring Google OAuth2, you can verify the setup by navigating to the login endpoint in your browser:

```
https://your-geostore-host/geostore/rest/users/user/details?provider=oidc
```

If the configuration is correct, you will be redirected to Google's login page. After authenticating, Google will redirect back to GeoStore and complete the login flow.

## Multi-Provider Setup

Google can be used alongside other identity providers (e.g. Keycloak, Azure AD) by declaring multiple providers in the `oidc.providers` property. See [Multiple OIDC Providers](../security/oidc.md#multiple-oidc-providers) for the full explanation.

### Example: Google + Keycloak

```properties
# Declare two providers
oidc.providers=keycloak,google

# -----------------------------------------------
# Provider 1: Keycloak (corporate identity)
# -----------------------------------------------
keycloakOAuth2Config.enabled=true
keycloakOAuth2Config.clientId=geostore-app
keycloakOAuth2Config.clientSecret=KEYCLOAK_CLIENT_SECRET
keycloakOAuth2Config.discoveryUrl=https://keycloak.example.com/realms/master/.well-known/openid-configuration
keycloakOAuth2Config.redirectUri=https://your-geostore-host/geostore/rest/users/user/details
keycloakOAuth2Config.internalRedirectUri=../../mapstore/
keycloakOAuth2Config.autoCreateUser=true
keycloakOAuth2Config.principalKey=email
keycloakOAuth2Config.scopes=openid,email,profile
keycloakOAuth2Config.rolesClaim=realm_access.roles
keycloakOAuth2Config.roleMappings=realm_admin:ADMIN,realm_user:USER

# -----------------------------------------------
# Provider 2: Google (external users)
# -----------------------------------------------
googleOAuth2Config.enabled=true
googleOAuth2Config.clientId=YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com
googleOAuth2Config.clientSecret=YOUR_GOOGLE_CLIENT_SECRET
googleOAuth2Config.discoveryUrl=https://accounts.google.com/.well-known/openid-configuration
googleOAuth2Config.redirectUri=https://your-geostore-host/geostore/rest/users/user/details
googleOAuth2Config.internalRedirectUri=../../mapstore/
googleOAuth2Config.autoCreateUser=true
googleOAuth2Config.principalKey=email
googleOAuth2Config.scopes=openid,email,profile
googleOAuth2Config.sendClientSecret=true
googleOAuth2Config.accessType=offline
googleOAuth2Config.authenticatedDefaultRole=USER
```

With this configuration:

| Provider | Login URL | Bearer audience (`clientId`) |
|----------|-----------|------------------------------|
| Keycloak | `?provider=keycloak` | `geostore-app` |
| Google | `?provider=google` | `YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com` |

Bearer tokens are routed automatically -- a JWT issued by Google (with Google's `clientId` in the `aud` claim) is validated against the Google provider's JWKS keys, while a Keycloak-issued token is validated against Keycloak's keys. See [Bearer Token Multiple Providers](../security/bearer-tokens.md#multiple-providers) for details.

## Migration from Dedicated Google Provider

If you were previously using the dedicated Google provider (`googleOAuth2Config.` prefix) with a single-provider setup, you have two options:

**Option A: Single provider (simplest)**

1. Replace the `googleOAuth2Config.` prefix with `oidcOAuth2Config.` for all properties.
2. Add `oidcOAuth2Config.accessType=offline` to preserve refresh token behavior.
3. Add `oidcOAuth2Config.sendClientSecret=true` (the dedicated provider sent the client secret by default).
4. Update the login URL from `?provider=google` to `?provider=oidc`.

**Option B: Multi-provider (recommended if adding other IdPs)**

1. Declare Google as a named provider: `oidc.providers=google` (or `oidc.providers=google,keycloak` etc.).
2. Keep the `googleOAuth2Config.` prefix -- it is now a valid provider-specific prefix.
3. Add `googleOAuth2Config.accessType=offline` and `googleOAuth2Config.sendClientSecret=true`.
4. The login URL `?provider=google` continues to work as before.

## Troubleshooting

| Problem | Solution |
|---|---|
| `redirect_uri_mismatch` error | Ensure the `redirectUri` property value **exactly** matches the Authorized redirect URI in Google Cloud Console (including trailing slashes and protocol). |
| No email in token | Add the `email` scope to the `scopes` property: `{provider}OAuth2Config.scopes=openid,email,profile`. |
| Refresh token not returned | Set `{provider}OAuth2Config.accessType=offline` to request a refresh token from Google. |
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
