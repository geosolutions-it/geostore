# OIDC / OAuth2 Configuration

GeoStore provides a generic OpenID Connect (OIDC) integration that works with any OIDC-compliant identity provider, including Keycloak, Azure AD / Entra ID, Google, Okta, Auth0, and others. Under the hood it uses Spring Security OAuth2 and supports two authentication flows:

- **Authorization Code Flow** for interactive browser-based login (with optional PKCE)
- **Bearer Token** for direct API authentication using a pre-obtained access token

All configuration is done through property overrides in `geostore-ovr.properties` using the bean name prefix `{provider}OAuth2Config.` (default: `oidcOAuth2Config.`). GeoStore supports [multiple simultaneous providers](#multiple-oidc-providers).

## How It Works

### Discovery

Setting a `discoveryUrl` is the recommended way to configure GeoStore's OIDC integration. When provided, GeoStore fetches the provider's `/.well-known/openid-configuration` document at startup and automatically populates the endpoint properties:

| Discovery field | GeoStore property |
|---|---|
| `authorization_endpoint` | `authorizationUri` |
| `token_endpoint` | `accessTokenUri` |
| `userinfo_endpoint` | `checkTokenEndpointUrl` |
| `jwks_uri` | `idTokenUri` |
| `end_session_endpoint` | `logoutUri` |
| `revocation_endpoint` | `revokeEndpoint` |
| `introspection_endpoint` | `introspectionEndpoint` |
| `scopes_supported` | `scopes` (only if not explicitly set) |

!!! tip
    You can override any auto-discovered endpoint by setting the corresponding property explicitly. Explicit values always take precedence over discovered ones. Scopes are only auto-filled from discovery if the `scopes` property has not been set.

### Authorization Code Flow

The standard redirect-based login flow works as follows:

1. An unauthenticated user is redirected to the IdP's authorization endpoint.
2. After successful login, the IdP redirects back to GeoStore's callback URL with an authorization code.
3. GeoStore exchanges the code for an access token, ID token, and (optionally) a refresh token.
4. The user's principal (username) is resolved from the token claims.

### Userinfo Endpoint

After obtaining an access token, GeoStore calls the OIDC userinfo endpoint (`checkTokenEndpointUrl`) per [OIDC Core Section 5.3.1](https://openid.net/specs/openid-connect-core-1_0.html#UserInfo). The request is a simple GET with a Bearer Authorization header:

```
GET /userinfo HTTP/1.1
Authorization: Bearer <access_token>
Accept: application/json
```

The userinfo response is used for principal resolution (see below) and as a fallback source for role and group claims. If `rolesClaim` or `groupsClaim` is configured but not found in the JWT, GeoStore checks the userinfo response before giving up. See [Roles & Groups - Claim Resolution Order](roles-and-groups.md#claim-resolution-order) for details.

### Principal Resolution

GeoStore uses a 3-level fallback strategy to resolve the authenticated username:

1. **Spring Security principal** -- the principal returned by Spring's OAuth2 authentication.
2. **Introspection / extension map** -- claims from the token introspection or check-token response.
3. **JWT claims** -- decoded from the ID token (preferred), then the access token.

At each level, the following claim keys are checked in order:

1. The configured `uniqueUsername` (if set)
2. The configured `principalKey` (default: `email`)
3. Common fallback keys: `upn`, `preferred_username`, `unique_name`, `user_name`, `username`, `email`, `sub`, `oid`

!!! note
    The first non-blank value found wins. Claim lookups are case-insensitive.

## Configuration Properties

All properties use the prefix `{provider}OAuth2Config.` in `geostore-ovr.properties` (default: `oidcOAuth2Config.`). When using [multiple providers](#multiple-oidc-providers), replace `oidc` with the provider name.

### Core Settings

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `false` | Enable the OIDC provider |
| `clientId` | String | -- | OAuth2 client ID (required) |
| `clientSecret` | String | -- | OAuth2 client secret (required) |
| `discoveryUrl` | String | -- | OIDC discovery URL (recommended -- auto-fills endpoints) |
| `redirectUri` | String | -- | OAuth2 redirect URI (callback URL registered with the IdP) |
| `internalRedirectUri` | String | -- | Internal redirect after callback (e.g. `../../mapstore/`) |
| `scopes` | String | (from discovery) | Comma-separated scopes, e.g. `openid,email,profile` |

### Endpoint Overrides

These are normally auto-filled by discovery. Set them explicitly only if you need to override the discovered values or if you are not using discovery.

| Property | Type | Default | Description |
|---|---|---|---|
| `authorizationUri` | String | -- | Authorization endpoint |
| `accessTokenUri` | String | -- | Token endpoint |
| `checkTokenEndpointUrl` | String | -- | UserInfo endpoint |
| `idTokenUri` | String | -- | JWKS URI for JWT signature verification |
| `logoutUri` | String | -- | End session endpoint |
| `revokeEndpoint` | String | -- | Token revocation endpoint |
| `introspectionEndpoint` | String | -- | Token introspection endpoint (RFC 7662) |

### User & Role Settings

| Property | Type | Default | Description |
|---|---|---|---|
| `autoCreateUser` | boolean | `false` | Auto-create users in GeoStore DB on first login |
| `authenticatedDefaultRole` | String | `USER` | Default role for authenticated users (`ADMIN`, `USER`, or `GUEST`) |
| `principalKey` | String | `email` | JWT claim used to resolve the username |
| `uniqueUsername` | String | -- | Alternative claim for username resolution (checked before `principalKey`) |
| `rolesClaim` | String | -- | Claim path for roles — supports dot-notation (e.g. `realm_access.roles`) and full [JsonPath](https://github.com/json-path/JsonPath) expressions (e.g. `$.resource_access.*.roles`). Resolved from both JWT and userinfo. |
| `groupsClaim` | String | -- | Claim path for groups — supports dot-notation (e.g. `groups`, `isMemberOf`) and full [JsonPath](https://github.com/json-path/JsonPath) expressions (e.g. `$.resource_access.*.groups`). Resolved from both JWT and userinfo. |
| `roleMappings` | String | -- | IdP-to-GeoStore role mappings (e.g. `admin:ADMIN,user:USER`) |
| `groupMappings` | String | -- | IdP-to-GeoStore group mappings (e.g. `devs:DEVELOPERS`) |
| `dropUnmapped` | boolean | `false` | Drop roles/groups that have no mapping entry |
| `groupNamesUppercase` | boolean | `false` | Convert group names to uppercase |

!!! note "Mapping format"
    Role and group mappings use the format `IdPValue:GeoStoreValue`, comma-separated. IdP keys are matched case-insensitively (uppercased internally). For example, `realm_admin:ADMIN,viewer:USER` maps the IdP role `realm_admin` to GeoStore's `ADMIN` role.

### Bearer Token Settings

| Property | Type | Default | Description |
|---|---|---|---|
| `allowBearerTokens` | boolean | `true` | Accept Bearer tokens for API authentication |
| `bearerTokenStrategy` | String | `jwt` | Bearer token validation strategy: `jwt`, `introspection`, or `auto` |
| `maxTokenAgeSecs` | int | `0` | Maximum bearer JWT age in seconds (0 = disabled) |

=== "jwt (default)"

    Decodes the bearer token as a JWT, verifies the signature against the provider's JWKS endpoint, and checks `exp` / `iat` claims. This is the fastest strategy and does not require a network call to the IdP for each request.

=== "introspection"

    Sends the token to the IdP's RFC 7662 introspection endpoint with client credentials (Basic auth). Requires `introspectionEndpoint` to be configured or discoverable. This works with opaque (non-JWT) tokens.

=== "auto"

    Tries JWT validation first. If decoding or signature verification fails, falls back to introspection.

### JWE (Encrypted Token) Settings

| Property | Type | Default | Description |
|---|---|---|---|
| `jweKeyStoreFile` | String | -- | Path to the Java keystore (JKS/PKCS12) containing the private key for JWE decryption |
| `jweKeyStorePassword` | String | -- | Keystore password |
| `jweKeyStoreType` | String | `PKCS12` | Keystore type (`PKCS12` or `JKS`) |
| `jweKeyAlias` | String | *(first alias)* | Alias of the private key in the keystore |
| `jweKeyPassword` | String | *(keystore password)* | Password for the specific key entry |

!!! note "JWE is opt-in"
    JWE decryption is only activated when `jweKeyStoreFile` is set. Plain JWS tokens are always accepted regardless of this setting. See [Bearer Tokens - JWE](bearer-tokens.md#jwe-encrypted-tokens) for details.

### Authentication Flow Settings

| Property | Type | Default | Description |
|---|---|---|---|
| `enableRedirectEntryPoint` | boolean | `false` | Always redirect to the authorization endpoint on authentication failure |
| `globalLogoutEnabled` | boolean | `false` | Enable global logout (revoke tokens on logout) |
| `postLogoutRedirectUri` | String | -- | Where to redirect after OIDC logout |

### Cache Settings

| Property | Type | Default | Description |
|---|---|---|---|
| `cacheSize` | int | `1000` | Token authentication cache maximum entries |
| `cacheExpirationMinutes` | int | `480` | Token cache entry expiration time (minutes) |

### Retry / Backoff Settings

| Property | Type | Default | Description |
|---|---|---|---|
| `maxRetries` | int | `3` | Maximum retries for token refresh |
| `initialBackoffDelay` | long | `1000` | Initial backoff delay for retry (milliseconds) |
| `backoffMultiplier` | double | `2.0` | Exponential backoff multiplier |

## PKCE (Proof Key for Code Exchange)

GeoStore supports Authorization Code Flow with PKCE, as defined in [RFC 7636](https://tools.ietf.org/html/rfc7636). PKCE is recommended for public clients or scenarios where the client secret cannot be kept confidential.

| Property | Type | Default | Description |
|---|---|---|---|
| `sendClientSecret` | boolean | `false` | Send `client_secret` to the token endpoint (for confidential clients) |
| `usePKCE` | boolean | `false` | Enable PKCE for the authorization code flow |
| `accessType` | String | -- | Access type for the authorization request (set to `offline` for refresh token support, e.g. Google) |

When `usePKCE=true`:

- A cryptographically random **code verifier** (32 bytes, base64url-encoded) is generated for each authorization request and stored in the HTTP session.
- The corresponding **code challenge** is computed using SHA-256 (`S256` method) and appended to the authorization URL.
- The code verifier is sent to the token endpoint during the code exchange step.
- The `sendClientSecret` setting is ignored when PKCE is enabled.

!!! warning
    When using PKCE, make sure your IdP is configured to accept the `S256` code challenge method. Most modern providers support this by default.

When `usePKCE=false` and `sendClientSecret=true`, GeoStore sends the `client_secret` as a form parameter in the token request. This is the standard behavior for confidential clients.

When both are `false`, GeoStore uses the default Spring OAuth2 request enhancer (no client secret in the token request body, relying on Basic auth headers).

## Logout (RP-Initiated Logout)

GeoStore implements OIDC RP-Initiated Logout ([OpenID Connect RP-Initiated Logout 1.0](https://openid.net/specs/openid-connect-rpinitiated-1_0.html)). When a user logs out, GeoStore redirects to the IdP's end session endpoint with the following parameters:

| Parameter | Source |
|---|---|
| `id_token_hint` | The ID token issued during login (falls back to the access token if unavailable) |
| `client_id` | The configured `clientId` |
| `post_logout_redirect_uri` | The configured `postLogoutRedirectUri` (if set) |

To also revoke tokens on logout, set `globalLogoutEnabled=true`. This sends a separate revocation request to the `revokeEndpoint`.

!!! tip
    Make sure the `postLogoutRedirectUri` is registered as a valid post-logout redirect URI in your IdP configuration.

## Discovery URL Auto-fill Behavior

Setting `discoveryUrl` is the recommended and simplest approach to configure OIDC. It should point to your IdP's issuer URL (or directly to the `.well-known/openid-configuration` endpoint). GeoStore will automatically append `/.well-known/openid-configuration` if not already present.

The discovery document is fetched once at filter initialization. The auto-fill logic works as follows:

- Each endpoint property (`authorizationUri`, `accessTokenUri`, etc.) is populated from the corresponding discovery field.
- **Explicit property values always override discovered values** -- if you set `accessTokenUri` in your properties file, the discovered `token_endpoint` is ignored.
- **Scopes are only auto-filled if the `scopes` property is empty or not set.** If you explicitly configure scopes, the discovered `scopes_supported` value is not used.

!!! note
    Discovery requires network access from GeoStore to the IdP at startup. If your deployment restricts outbound connections, set each endpoint property manually instead.

## Multiple OIDC Providers

GeoStore supports running multiple OIDC providers simultaneously (e.g. Keycloak + Google + Azure AD). Each provider gets its own configuration bean, filter, token cache, and JWKS key provider.

### How It Works

A `CompositeOpenIdConnectFilter` discovers all registered `OpenIdConnectConfiguration` beans at startup and creates per-provider filter instances. Request routing works as follows:

- **Login/callback URLs** use the pattern `/openid/{provider}/login` and `/openid/{provider}/callback`, routing to the matching provider automatically.
- **Bearer tokens** are tried against each enabled provider in order. The first provider that successfully validates the token wins.
- **Already authenticated** requests pass through without re-authentication.

### Configuration

Set the `oidc.providers` property to a comma-separated list of provider names. For each provider, use the prefix `{provider}OAuth2Config.` in `geostore-ovr.properties`.

```properties
# Declare providers (defaults to "oidc" if not set)
oidc.providers=oidc,google

# --- Provider 1: Keycloak (default "oidc" provider) ---
oidcOAuth2Config.enabled=true
oidcOAuth2Config.clientId=geostore-keycloak
oidcOAuth2Config.clientSecret=keycloak-secret
oidcOAuth2Config.discoveryUrl=https://keycloak.example.com/realms/master/.well-known/openid-configuration
oidcOAuth2Config.scopes=openid,email,profile
oidcOAuth2Config.redirectUri=https://geostore.example.com/geostore/rest/users/user/details
oidcOAuth2Config.autoCreateUser=true
oidcOAuth2Config.principalKey=email

# --- Provider 2: Google ---
googleOAuth2Config.enabled=true
googleOAuth2Config.clientId=123456789.apps.googleusercontent.com
googleOAuth2Config.clientSecret=google-secret
googleOAuth2Config.discoveryUrl=https://accounts.google.com/.well-known/openid-configuration
googleOAuth2Config.scopes=openid,email,profile
googleOAuth2Config.redirectUri=https://geostore.example.com/geostore/rest/users/user/details
googleOAuth2Config.autoCreateUser=true
googleOAuth2Config.principalKey=email
googleOAuth2Config.usePKCE=true
googleOAuth2Config.accessType=offline
```

!!! note "Backward compatibility"
    If `oidc.providers` is not set, GeoStore defaults to a single `oidc` provider, preserving the existing behavior. All existing `oidcOAuth2Config.*` properties continue to work unchanged.

### Provider List Endpoint

The REST API exposes a `GET /openid/providers` endpoint that returns all enabled providers as a JSON array:

```json
[
  {"name": "oidc", "loginUrl": "oidc/login"},
  {"name": "google", "loginUrl": "google/login"}
]
```

This is useful for building dynamic login UIs that present multiple identity provider options.

### Bearer Token Routing

When a bearer token arrives, the composite filter tries each enabled provider in order. Each provider validates the token against its own JWKS endpoint and audience (`clientId`). The first provider that accepts the token authenticates the request. If no provider accepts it, the request continues unauthenticated.

This provides **cross-provider isolation**: a token issued by Keycloak with audience `geostore-keycloak` will be rejected by the Google provider (which expects audience `123456789.apps.googleusercontent.com`), and vice versa.

### Complete Multi-Provider Example (Keycloak + Google + Azure AD)

Below is a full example configuring three providers simultaneously. See the individual [Keycloak](../guides/keycloak-setup.md), [Google](../guides/google-setup.md), and [Azure AD](../guides/azure-ad-setup.md) guides for detailed IdP setup steps.

```properties
# -----------------------------------------------
# Multi-Provider OIDC Configuration
# -----------------------------------------------

# Declare all providers
oidc.providers=oidc,google,azure

# -----------------------------------------------
# Provider 1: Keycloak (using default "oidc" name)
# -----------------------------------------------
oidcOAuth2Config.enabled=true
oidcOAuth2Config.clientId=geostore-keycloak
oidcOAuth2Config.clientSecret=keycloak-secret
oidcOAuth2Config.discoveryUrl=https://keycloak.example.com/realms/master/.well-known/openid-configuration
oidcOAuth2Config.scopes=openid,email,profile
oidcOAuth2Config.redirectUri=https://geostore.example.com/geostore/rest/users/user/details
oidcOAuth2Config.internalRedirectUri=../../mapstore/
oidcOAuth2Config.autoCreateUser=true
oidcOAuth2Config.principalKey=preferred_username
oidcOAuth2Config.rolesClaim=realm_access.roles
oidcOAuth2Config.roleMappings=admin:ADMIN,user:USER
oidcOAuth2Config.groupsClaim=groups

# -----------------------------------------------
# Provider 2: Google
# -----------------------------------------------
googleOAuth2Config.enabled=true
googleOAuth2Config.clientId=123456789.apps.googleusercontent.com
googleOAuth2Config.clientSecret=google-secret
googleOAuth2Config.discoveryUrl=https://accounts.google.com/.well-known/openid-configuration
googleOAuth2Config.scopes=openid,email,profile
googleOAuth2Config.redirectUri=https://geostore.example.com/geostore/rest/users/user/details
googleOAuth2Config.internalRedirectUri=../../mapstore/
googleOAuth2Config.autoCreateUser=true
googleOAuth2Config.principalKey=email
googleOAuth2Config.sendClientSecret=true
googleOAuth2Config.accessType=offline
googleOAuth2Config.usePKCE=true

# -----------------------------------------------
# Provider 3: Azure AD / Entra ID
# -----------------------------------------------
azureOAuth2Config.enabled=true
azureOAuth2Config.clientId=12345678-abcd-efgh-ijkl-1234567890ab
azureOAuth2Config.clientSecret=azure-secret
azureOAuth2Config.discoveryUrl=https://login.microsoftonline.com/YOUR_TENANT_ID/v2.0/.well-known/openid-configuration
azureOAuth2Config.scopes=openid,email,profile
azureOAuth2Config.redirectUri=https://geostore.example.com/geostore/rest/users/user/details
azureOAuth2Config.internalRedirectUri=../../mapstore/
azureOAuth2Config.autoCreateUser=true
azureOAuth2Config.principalKey=preferred_username
azureOAuth2Config.sendClientSecret=true
azureOAuth2Config.rolesClaim=roles
azureOAuth2Config.roleMappings=Admin:ADMIN,User:USER
```

With this configuration, users can log in via any of three identity providers:

| Provider | Login URL | Bearer token audience |
|----------|-----------|----------------------|
| Keycloak | `/openid/oidc/login` | `geostore-keycloak` |
| Google | `/openid/google/login` | `123456789.apps.googleusercontent.com` |
| Azure AD | `/openid/azure/login` | `12345678-abcd-efgh-ijkl-1234567890ab` |

Each provider has its own configuration, token cache, and JWKS keys. Users authenticated by different providers are fully isolated from each other.

## Minimal Working Example

Below is a minimal working configuration for a generic OIDC provider. See the [Guides](../guides/keycloak-setup.md) section for provider-specific examples.

```properties
# -----------------------------------------------
# OIDC Configuration
# -----------------------------------------------

# Enable the OIDC authentication filter
oidcOAuth2Config.enabled=true

# Client credentials (registered with your OIDC provider)
oidcOAuth2Config.clientId=geostore-client
oidcOAuth2Config.clientSecret=your-client-secret-here

# Discovery URL — points to the provider's .well-known endpoint
# GeoStore will auto-discover all endpoints from here
oidcOAuth2Config.discoveryUrl=https://idp.example.com/.well-known/openid-configuration

# Scopes to request (override discovery if needed)
oidcOAuth2Config.scopes=openid,email,profile

# OAuth2 redirect URI — must match the redirect URI registered with the provider
oidcOAuth2Config.redirectUri=https://geostore.example.com/geostore/rest/users/user/details

# After successful login, redirect the browser here
oidcOAuth2Config.internalRedirectUri=../../mapstore/

# Auto-create users in GeoStore DB on first OIDC login
oidcOAuth2Config.autoCreateUser=true

# Use the "email" claim as the GeoStore username
oidcOAuth2Config.principalKey=email

# Map IdP roles to GeoStore roles
oidcOAuth2Config.rolesClaim=roles
oidcOAuth2Config.roleMappings=admin:ADMIN,user:USER

# Map IdP groups to GeoStore groups
oidcOAuth2Config.groupsClaim=groups

# Enable OIDC logout
oidcOAuth2Config.globalLogoutEnabled=true
oidcOAuth2Config.postLogoutRedirectUri=https://geostore.example.com/mapstore/
```

!!! tip "Testing the configuration"
    After configuring OIDC, you can trigger the login flow by navigating to:
    ```
    https://geostore.example.com/geostore/rest/users/user/details?provider=oidc
    ```
    If discovery and credentials are correct, you will be redirected to your IdP's login page.
