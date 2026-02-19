# Bearer Token Authentication

## Overview

GeoStore accepts Bearer tokens for authenticating REST API requests without requiring an interactive browser login. This is the primary mechanism for machine-to-machine (M2M) communication and programmatic API access.

Tokens can be sent in two ways:

- **`Authorization` header** (recommended): `Authorization: Bearer <token>`
- **Query parameter**: `?access_token=<token>`

When the OIDC filter receives a request with an attached Bearer token, it validates it according to the configured strategy, extracts the user principal from the token claims, and creates a pre-authenticated security context. Bearer tokens are **enabled by default** (`allowBearerTokens=true`).

!!! note
    Bearer token validation is independent of the interactive authorization code flow. The filter detects the authentication type automatically: requests arriving via an OAuth2 callback go through the standard code exchange, while requests carrying a pre-obtained token go through the bearer validation path described here.

---

## Validation Strategies

Three strategies are available, controlled by the `bearerTokenStrategy` property on the OIDC configuration bean.

### JWT (default)

The `jwt` strategy validates the token locally by decoding and verifying the JWT signature. No call to the identity provider is made during validation (aside from the initial JWKS key fetch).

**Validation steps:**

1. Decode the JWT and extract the `kid` (key ID) from the header.
2. Fetch the RSA public key matching that `kid` from the JWKS endpoint (auto-discovered or configured via `idTokenUri` / `jwkURI`).
3. Verify the RSA signature.
4. Check the `exp` (expiration) claim -- reject if the token has expired.
5. Check the `iat` (issued-at) claim against `maxTokenAgeSecs` -- reject tokens older than the configured maximum age (skipped when `maxTokenAgeSecs` is `0`).
6. Run the `MultiTokenValidator` chain (see [Token Validators](#token-validators) below).
7. Extract the user principal from claims.

!!! tip
    The JWT strategy is the fastest option because it performs signature verification locally. It requires no network call to the IdP per request (keys are cached).

### Introspection

The `introspection` strategy delegates validation to the identity provider's RFC 7662 Token Introspection endpoint.

**Validation steps:**

1. POST the token to the configured `introspectionEndpoint`.
2. Authenticate the request using HTTP Basic auth with `clientId:clientSecret`.
3. Check the `active` field in the response -- reject if `false`.
4. Return the claims from the introspection response.

!!! note
    The introspection strategy is **required** for opaque (non-JWT) tokens, since they cannot be decoded or signature-verified locally. It is also useful when tokens may be revoked before expiry, as introspection always checks the current token status at the provider.

### Auto

The `auto` strategy tries JWT validation first and, if decoding or signature verification fails, falls back to introspection.

**Validation steps:**

1. Attempt JWT validation (decode, verify, check claims).
2. If any exception occurs during JWT processing, call the introspection endpoint instead.

!!! warning
    The `auto` strategy may add latency on opaque tokens because it always attempts JWT decoding first before falling back. If your provider exclusively issues opaque tokens, use `introspection` directly.

---

## Token Validators

After JWT decoding succeeds, the `MultiTokenValidator` runs a chain of validators against the claims:

### AudienceAccessTokenValidator

Ensures the token was issued for this application by checking the audience. The token is accepted if **any** of the following conditions is true:

| Claim checked | Condition |
|---------------|-----------|
| `aud` | Equals `clientId` (string comparison) |
| `aud` | Is a list that contains `clientId` |
| `azp` | Equals `clientId` (standard OIDC authorized party) |
| `appid` | Equals `clientId` (Azure AD specific) |

If none match, the token is rejected with the message *"JWT Bearer token - probably not meant for this application"*.

### SubjectTokenValidator

Verifies that the token subject matches the userInfo subject (when userInfo claims are available). This prevents a token issued for one user from being associated with a different user's profile.

- **Standard case**: checks that `sub` in the JWT equals `sub` from userInfo.
- **Azure AD case**: checks `xms_st.sub` in the JWT against `sub` from userInfo.
- **Bearer-only validation**: when no userInfo claims are available (the typical case for direct bearer token validation), this check is **skipped** -- there is nothing to compare against.

---

## Configuration

All bearer-related properties are set on the OIDC configuration bean (default bean name `oidcOAuth2Config`). When using [multiple providers](oidc.md#multiple-oidc-providers), each provider has its own bearer token configuration via `{provider}OAuth2Config.`. Properties can be configured via Spring XML, Java config, or property overrides.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `allowBearerTokens` | `boolean` | `true` | Enable or disable bearer token authentication |
| `bearerTokenStrategy` | `String` | `jwt` | Validation strategy: `jwt`, `introspection`, or `auto` |
| `maxTokenAgeSecs` | `int` | `0` | Maximum JWT age in seconds from the `iat` claim. `0` disables the check |
| `introspectionEndpoint` | `String` | *(auto-discovered)* | RFC 7662 introspection endpoint URL |
| `clientId` | `String` | -- | OAuth2 client ID, used for audience validation and introspection auth |
| `clientSecret` | `String` | -- | OAuth2 client secret, used for introspection Basic auth |
| `jwkURI` | `String` | *(auto-discovered)* | JWKS endpoint URL for fetching RSA public keys |
| `principalKey` | `String` | `email` | Primary claim key to extract the user principal |
| `uniqueUsername` | `String` | -- | Alternative claim key for the unique username (checked first) |

!!! tip
    When a `discoveryUrl` is configured (the OpenID Connect discovery endpoint, e.g. `https://idp.example.com/.well-known/openid-configuration`), the `introspectionEndpoint` and `jwkURI` values are auto-discovered and do not need to be set manually.

---

## JWKS Key Caching

The `JwksRsaKeyProvider` fetches and caches RSA public keys from the JWKS endpoint for JWT signature verification.

**How it works:**

- Keys are stored in a `ConcurrentHashMap` keyed by `kid` (key ID from the JWT header).
- On a **cache hit**, the key is returned immediately with no network call.
- On a **cache miss**, all keys are refreshed from the JWKS endpoint in a thread-safe `synchronized` block (double-checked locking pattern).
- Only RSA keys with `use=sig` (signature) are loaded. Other key types are ignored.
- Keys are parsed from the Base64url-encoded `n` (modulus) and `e` (exponent) JWK fields.

!!! note
    Key rotation at the identity provider is handled automatically. When a JWT arrives with a `kid` that is not in the cache, the provider re-fetches all keys from the JWKS endpoint. This means key rotation is transparent as long as the JWKS endpoint is reachable.

---

## Principal Resolution from Bearer Claims

After successful validation, the user principal (username) is extracted from the token claims. The resolution follows a two-phase lookup:

**Phase 1 -- Configured claim keys:**

1. `uniqueUsername` (if configured)
2. `principalKey` (default: `email`)

**Phase 2 -- Common fallback claims** (used if Phase 1 yields no result):

1. `upn`
2. `preferred_username`
3. `unique_name`
4. `user_name`
5. `username`
6. `email`
7. `sub`
8. `oid`

All claim lookups are **case-insensitive**. If the resolved claim value is an array or collection, the **first element** is used.

---

## Examples

### Sending Bearer Tokens

=== "Authorization Header"

    ```bash
    # Recommended: use the Authorization header
    curl -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..." \
      http://localhost:8080/geostore/rest/resources
    ```

=== "Query Parameter"

    ```bash
    # Alternative: pass as query parameter
    curl "http://localhost:8080/geostore/rest/resources?access_token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
    ```

!!! warning
    Passing tokens as query parameters is discouraged in production because the token may be logged in server access logs, proxy logs, and browser history. Use the `Authorization` header whenever possible.

### Strategy Configuration

=== "JWT (default)"

    ```xml
    <bean id="oidcOAuth2Config"
          class="...openid_connect.OpenIdConnectConfiguration">
        <property name="clientId" value="my-client-id" />
        <property name="clientSecret" value="my-client-secret" />
        <property name="discoveryUrl"
                  value="https://idp.example.com/.well-known/openid-configuration" />
        <!-- JWT is the default, but can be set explicitly -->
        <property name="bearerTokenStrategy" value="jwt" />
        <!-- Optional: reject tokens older than 1 hour -->
        <property name="maxTokenAgeSecs" value="3600" />
    </bean>
    ```

=== "Introspection"

    ```xml
    <bean id="oidcOAuth2Config"
          class="...openid_connect.OpenIdConnectConfiguration">
        <property name="clientId" value="my-client-id" />
        <property name="clientSecret" value="my-client-secret" />
        <property name="bearerTokenStrategy" value="introspection" />
        <property name="introspectionEndpoint"
                  value="https://idp.example.com/oauth2/introspect" />
    </bean>
    ```

=== "Auto (JWT + fallback)"

    ```xml
    <bean id="oidcOAuth2Config"
          class="...openid_connect.OpenIdConnectConfiguration">
        <property name="clientId" value="my-client-id" />
        <property name="clientSecret" value="my-client-secret" />
        <property name="discoveryUrl"
                  value="https://idp.example.com/.well-known/openid-configuration" />
        <property name="bearerTokenStrategy" value="auto" />
        <!-- introspectionEndpoint auto-discovered from discoveryUrl -->
    </bean>
    ```

=== "Disable Bearer Tokens"

    ```xml
    <bean id="oidcOAuth2Config"
          class="...openid_connect.OpenIdConnectConfiguration">
        <property name="allowBearerTokens" value="false" />
        <!-- ... other properties ... -->
    </bean>
    ```

---

## Multiple Providers

When multiple OIDC providers are configured (see [Multiple OIDC Providers](oidc.md#multiple-oidc-providers)), the `CompositeOpenIdConnectFilter` handles bearer token routing:

1. Each provider has its own JWKS key provider, token validator, and audience check.
2. When a bearer token arrives, the composite filter tries each enabled provider in order.
3. The first provider that successfully validates the token (correct signature, matching audience) authenticates the request.
4. If a provider rejects the token (wrong key, wrong audience, expired), the composite filter moves to the next provider.
5. If no provider accepts the token, the request continues unauthenticated.

This provides **cross-provider isolation** -- a token issued for one provider's `clientId` cannot be used to authenticate against another provider.

Each provider can independently configure its own `bearerTokenStrategy`, `maxTokenAgeSecs`, and `allowBearerTokens` settings.

---

## Troubleshooting

| Error message | Cause | Resolution |
|---------------|-------|------------|
| *"Bearer tokens aren't allowed"* | `allowBearerTokens` is set to `false` | Set `allowBearerTokens` to `true` on the OIDC configuration bean |
| *"No JWK key found for kid: ..."* | The JWKS endpoint is unreachable, or the `kid` in the JWT header does not match any key at the endpoint | Verify the JWKS endpoint URL is correct and reachable. Check that the IdP has not rotated all keys since the token was issued |
| *"Attached Bearer Token has expired"* | The `exp` claim in the JWT is in the past | Request a new token from the identity provider. Check that the system clock on the GeoStore server is synchronized (NTP) |
| *"Attached Bearer Token is too old"* | The `iat` claim age exceeds `maxTokenAgeSecs` | Request a fresh token, increase the `maxTokenAgeSecs` value, or set it to `0` to disable the age check |
| *"probably not meant for this application"* | None of the audience claims (`aud`, `azp`, `appid`) match the configured `clientId` | Verify that the `clientId` in GeoStore matches the audience/client configured at the identity provider |
| *"Token introspection returned active=false"* | The token has been revoked or has expired at the provider | Request a new token. If using refresh tokens, ensure the refresh flow is working |
| *"Bearer token introspection requested but no introspection endpoint is configured"* | Strategy is `introspection` or `auto` (fallback) but no endpoint is set | Configure `introspectionEndpoint` explicitly or set a `discoveryUrl` for auto-discovery |
| *"Attached Bearer Token is invalid (decoding failed)"* | The token is not a valid JWT (malformed, wrong encoding) | Verify the token format. If the provider issues opaque tokens, switch to the `introspection` or `auto` strategy |
