# Security Properties Reference

All GeoStore security properties are configured in `geostore-ovr.properties` using the Spring property override convention:

```
{beanName}.{property}=value
```

Each property is prefixed with the name of the Spring bean it configures. For example, OIDC properties use the prefix `oidcOAuth2Config.`, so enabling the OIDC provider looks like:

```properties
oidcOAuth2Config.enabled=true
```

This page provides a comprehensive reference for every security-related property. For conceptual overviews and setup guides, see the linked pages in each section.

---

## OIDC / OpenID Connect Properties

**Prefix:** `oidcOAuth2Config.`

These properties configure the generic OpenID Connect integration that works with any OIDC-compliant identity provider (Keycloak, Azure AD / Entra ID, Google, Okta, Auth0, etc.). See [OIDC / OAuth2 Configuration](oidc.md) for a full conceptual overview.

### Core Settings

| Property | Type | Default | Required | Description |
|----------|------|---------|----------|-------------|
| `enabled` | boolean | `false` | Yes | Enable the OIDC authentication provider |
| `clientId` | String | -- | Yes | OAuth2 client ID registered with the identity provider |
| `clientSecret` | String | -- | Yes | OAuth2 client secret |
| `discoveryUrl` | String | -- | Recommended | OIDC discovery URL (auto-fills endpoint properties from `.well-known/openid-configuration`) |
| `redirectUri` | String | -- | Yes | OAuth2 callback URL (must match the redirect URI registered with the IdP) |
| `internalRedirectUri` | String | -- | No | Internal redirect after OAuth2 callback (e.g. `../../mapstore/`) |
| `scopes` | String | (from discovery) | No | Comma-separated OAuth2 scopes (e.g. `openid,email,profile`) |

!!! note "Discovery is recommended"
    Setting `discoveryUrl` is the simplest way to configure OIDC. GeoStore fetches the provider's discovery document at startup and auto-fills all endpoint properties. Explicit property values always take precedence over discovered values.

### Endpoint Overrides

These are normally auto-filled by discovery. Set them explicitly only when overriding discovered values or when not using discovery.

| Property | Type | Default | Required | Description |
|----------|------|---------|----------|-------------|
| `authorizationUri` | String | (auto) | No | Authorization endpoint URL |
| `accessTokenUri` | String | (auto) | No | Token endpoint URL |
| `checkTokenEndpointUrl` | String | (auto) | No | UserInfo endpoint URL |
| `idTokenUri` | String | (auto) | No | JWKS URI for JWT signature verification |
| `logoutUri` | String | (auto) | No | End session endpoint URL |
| `revokeEndpoint` | String | (auto) | No | Token revocation endpoint URL |
| `introspectionEndpoint` | String | (auto) | No | Token introspection endpoint URL (RFC 7662) |

### User & Role Settings

| Property | Type | Default | Required | Description |
|----------|------|---------|----------|-------------|
| `autoCreateUser` | boolean | `false` | No | Auto-create users in GeoStore DB on first login |
| `authenticatedDefaultRole` | String | `USER` | No | Default role for authenticated users: `ADMIN`, `USER`, or `GUEST` |
| `principalKey` | String | `email` | No | JWT claim used to resolve the username |
| `uniqueUsername` | String | -- | No | Alternative claim for username resolution (checked before `principalKey`) |
| `rolesClaim` | String | -- | No | JWT claim path for roles (e.g. `roles` or `realm_access.roles`) |
| `groupsClaim` | String | -- | No | JWT claim path for groups (e.g. `groups`) |
| `roleMappings` | String | -- | No | IdP-to-GeoStore role mappings (format: `idp_role:GEOSTORE_ROLE,idp_role2:GEOSTORE_ROLE2`) |
| `groupMappings` | String | -- | No | IdP-to-GeoStore group mappings (same format as `roleMappings`) |
| `dropUnmapped` | boolean | `false` | No | Drop roles/groups that have no mapping entry |
| `groupNamesUppercase` | boolean | `false` | No | Convert group names to uppercase |

!!! note "Mapping format"
    Role and group mappings use the format `IdPValue:GeoStoreValue`, comma-separated. IdP keys are matched case-insensitively (uppercased internally). For example, `realm_admin:ADMIN,viewer:USER` maps the IdP role `realm_admin` to GeoStore's `ADMIN` role.

### Authentication Flow Settings

| Property | Type | Default | Required | Description |
|----------|------|---------|----------|-------------|
| `enableRedirectEntryPoint` | boolean | `false` | No | Always redirect to the IdP authorization endpoint on authentication failure |
| `globalLogoutEnabled` | boolean | `false` | No | Enable global logout (revoke tokens when the user logs out) |
| `postLogoutRedirectUri` | String | -- | No | Where to redirect the browser after OIDC logout |
| `sendClientSecret` | boolean | `false` | No | Send `client_secret` as a form parameter to the token endpoint (for confidential clients) |
| `usePKCE` | boolean | `false` | No | Enable PKCE (Proof Key for Code Exchange) for the authorization code flow |
| `accessType` | String | -- | No | Access type for the authorization request (set to `offline` for refresh token support, e.g. Google) |

### Bearer Token Settings

| Property | Type | Default | Required | Description |
|----------|------|---------|----------|-------------|
| `allowBearerTokens` | boolean | `true` | No | Accept Bearer tokens in the `Authorization` header for API authentication |
| `bearerTokenStrategy` | String | `jwt` | No | Bearer token validation strategy: `jwt`, `introspection`, or `auto` |
| `maxTokenAgeSecs` | int | `0` | No | Maximum bearer JWT age in seconds (0 = disabled, no age check) |

!!! tip "Choosing a bearer token strategy"
    Use **jwt** (default) for best performance -- tokens are validated locally using the JWKS endpoint. Use **introspection** if your IdP issues opaque (non-JWT) tokens. Use **auto** to try JWT first and fall back to introspection on failure.

### Cache Settings

| Property | Type | Default | Required | Description |
|----------|------|---------|----------|-------------|
| `cacheSize` | int | `1000` | No | Token authentication cache maximum entries |
| `cacheExpirationMinutes` | int | `480` | No | Token authentication cache TTL in minutes (default: 8 hours) |

### Retry / Backoff Settings

| Property | Type | Default | Required | Description |
|----------|------|---------|----------|-------------|
| `maxRetries` | int | `3` | No | Maximum number of retries for token refresh operations |
| `initialBackoffDelay` | long | `1000` | No | Initial backoff delay for retry in milliseconds |
| `backoffMultiplier` | double | `2.0` | No | Exponential backoff multiplier for successive retries |

---

## LDAP Properties

LDAP authentication is configured through a combination of property overrides and Spring XML beans.

### Property Overrides

| Property | Type | Default | Required | Description |
|----------|------|---------|----------|-------------|
| `ldap.host` | String | -- | Yes | LDAP server hostname |
| `ldap.port` | int | -- | Yes | LDAP server port |
| `ldap.root` | String | -- | Yes | LDAP root DN (e.g. `dc=example,dc=com`) |
| `geostoreLdapProvider.ignoreUsernameCase` | boolean | `false` | No | Case-insensitive username matching |

!!! note
    LDAP group/role search filters, user search base, and other advanced settings are configured in the Spring XML beans in `geostore-spring-security.xml`. See the [LDAP Configuration](ldap.md) page for full details.

---

## Session & User Management Properties

These properties control session management, automatic user creation, and password handling.

### Session Management

| Property | Type | Default | Required | Description |
|----------|------|---------|----------|-------------|
| `restSessionService.sessionTimeout` | int | -- | No | Session timeout in seconds |

### Auto-Create Users Interceptor

The auto-create users interceptor automatically creates local user accounts when an authenticated user (e.g. from a trusted proxy) does not yet exist in the GeoStore database.

| Property | Type | Default | Required | Description |
|----------|------|---------|----------|-------------|
| `autoCreateUsersInterceptor.autoCreateUsers` | boolean | `false` | No | Enable the REST interceptor auto-creation of users |
| `autoCreateUsersInterceptor.newUsersRole.role` | String | `USER` | No | Role assigned to auto-created users (`ADMIN`, `USER`, or `GUEST`) |
| `autoCreateUsersInterceptor.newUsersPassword` | String | -- | No | Password strategy for auto-created users: `NONE`, `USERNAME`, or `FROMHEADER` |
| `autoCreateUsersInterceptor.newUsersPasswordHeader` | String | `newUserPassword` | No | HTTP header name to read the password from when using the `FROMHEADER` strategy |

### Password Management

| Property | Type | Default | Required | Description |
|----------|------|---------|----------|-------------|
| `geostoreInitializer.allowPasswordRecoding` | boolean | `false` | No | Re-encode stored passwords on startup (useful when changing the password encoder) |

---

!!! tip
    For a working example of a complete `geostore-ovr.properties` file, see the [Keycloak Setup Guide](../guides/keycloak-setup.md).
