# Session & Token Cache Management

GeoStore manages user sessions and token lifecycle through several cooperating mechanisms: servlet session timeouts, a Guava-based token authentication cache, automatic token refresh with exponential backoff, and optional auto-creation of user accounts on first login.

## Session Timeout

The HTTP session timeout is controlled by the `restSessionService.sessionTimeout` property, specified in seconds. This determines how long a user's session remains valid after their last interaction.

```properties
# Set session timeout to 1 hour
restSessionService.sessionTimeout=3600
```

!!! note
    If `sessionTimeout` is not explicitly set, the session lifetime depends on the servlet container's default (typically 30 minutes for Tomcat).

## Token Authentication Cache

GeoStore caches OAuth2 `Authentication` objects keyed by access token value using a Guava `Cache`. This avoids re-validating tokens against the identity provider on every request.

### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `oidcOAuth2Config.cacheSize` | int | 1000 | Maximum number of cached authentication entries |
| `oidcOAuth2Config.cacheExpirationMinutes` | int | 480 | Cache entry TTL in minutes (8 hours default) |

The same properties are available for the Google provider using the `googleOAuth2Config` prefix:

```properties
googleOAuth2Config.cacheSize=1000
googleOAuth2Config.cacheExpirationMinutes=480
```

### Cache Behavior

- **Keyed by access token value** -- each unique access token maps to one `Authentication` object.
- **Write-through** -- entries are added or updated on every successful authentication.
- **Expiration** -- entries expire after `cacheExpirationMinutes` from the time of write.
- **Eviction** -- LRU (least-recently-used) eviction is applied when `cacheSize` is reached.
- **On expiration** -- if the cached entry carries a refresh token that has not yet expired, GeoStore attempts to revoke the authorization via the configured revoke endpoint.
- **Refresh token preservation** -- when updating a cache entry, if the new authentication does not carry a refresh token but the previous entry does, the existing refresh token is carried forward to the new entry.

### Cache Hit Flow

When a Bearer token arrives in a request, the following lookup sequence is executed:

1. Look up the token value in the cache.
2. If found **and** the access token is not expired, return the cached `Authentication` (fast path).
3. If found **but** the access token is expired, re-authenticate against the provider and update the cache entry.
4. If **not found**, perform full authentication (JWT decode and verification via JWKS, or introspection) and cache the result.

!!! tip
    For high-throughput deployments, increase `cacheSize` to avoid excessive eviction. The cache is entirely in-memory, so size it according to the expected number of concurrent active tokens.

## Token Refresh with Retry and Backoff

When a token refresh is required (expired access token with a valid refresh token), GeoStore uses an exponential backoff strategy to handle transient failures from the identity provider's token endpoint.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `oidcOAuth2Config.maxRetries` | int | 3 | Maximum retry attempts for token refresh |
| `oidcOAuth2Config.initialBackoffDelay` | long | 1000 | Initial delay between retries in milliseconds |
| `oidcOAuth2Config.backoffMultiplier` | double | 2.0 | Multiplier applied to the delay after each retry |

With the default values, the retry delays are: **1 s**, **2 s**, **4 s**.

```properties
# Example: more aggressive retry for a local IdP
oidcOAuth2Config.maxRetries=5
oidcOAuth2Config.initialBackoffDelay=500
oidcOAuth2Config.backoffMultiplier=1.5
```

!!! warning
    Setting `maxRetries` to a very high value can cause request threads to block for extended periods. Keep the total retry window (sum of all delays) well below any upstream proxy or load-balancer timeout.

## Auto-Create Users

GeoStore can automatically create user accounts in its database when a user logs in for the first time. Two independent mechanisms are available depending on the authentication flow.

### OAuth2 / OIDC Auto-Creation

Enable auto-creation for the OIDC provider by setting `autoCreateUser` to `true`:

```properties
oidcOAuth2Config.autoCreateUser=true
```

When enabled:

- A new user record is created in the GeoStore database on the first successful OAuth2 login.
- The default role is determined by `authenticatedDefaultRole` (default: `USER`).
- The user is linked to the OAuth2 provider through a `CONFIGURATION_NAME` user attribute.

### REST Interceptor Auto-Creation

For non-OAuth2 flows (e.g., header-based SSO or proxy authentication), a JAX-RS interceptor can auto-create users as requests arrive:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `autoCreateUsersInterceptor.autoCreateUsers` | boolean | false | Enable auto-creation via the REST interceptor |
| `autoCreateUsersInterceptor.newUsersRole.role` | String | USER | Role assigned to auto-created users |
| `autoCreateUsersInterceptor.newUsersPassword` | String | -- | Password strategy: `NONE`, `USERNAME`, or `FROMHEADER` |
| `autoCreateUsersInterceptor.newUsersPasswordHeader` | String | newUserPassword | Header name when using the `FROMHEADER` strategy |

!!! note
    The REST interceptor requires uncommenting the `autoCreateUsersInterceptor` bean reference in `applicationContext.xml`.

## Password Encoding

GeoStore supports password recoding for database schema migrations. When upgrading from a pre-v1.2 schema that stored passwords in a different encoding, enable recoding so that passwords are transparently re-hashed on first use:

```properties
# Enable password recoding for DB migration from pre-v1.2
geostoreInitializer.allowPasswordRecoding=true
```

## Configuration Summary

The table below lists all session and token management properties in one place. The `*` prefix stands for the provider bean name (e.g., `oidcOAuth2Config`, `googleOAuth2Config`).

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `restSessionService.sessionTimeout` | int | -- | Session timeout in seconds |
| `*.cacheSize` | int | 1000 | Token auth cache max entries |
| `*.cacheExpirationMinutes` | int | 480 | Token auth cache TTL (minutes) |
| `*.maxRetries` | int | 3 | Token refresh max retries |
| `*.initialBackoffDelay` | long | 1000 | Initial retry backoff (ms) |
| `*.backoffMultiplier` | double | 2.0 | Backoff multiplier |
| `*.autoCreateUser` | boolean | false | Auto-create users on OAuth2 login |
| `autoCreateUsersInterceptor.autoCreateUsers` | boolean | false | Auto-create via REST interceptor |
| `geostoreInitializer.allowPasswordRecoding` | boolean | false | Enable password recoding |
