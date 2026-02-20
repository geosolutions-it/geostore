# Migration Guide

This guide covers upgrading to GeoStore 2.5 from earlier versions. All new features are **opt-in** — existing configurations work unchanged without any modifications.

---

## Backward Compatibility

GeoStore 2.5 is **fully backward compatible** with existing `geostore-ovr.properties` files. No properties have been removed or renamed. New features are disabled by default:

| New Feature | Default | Activated by |
|---|---|---|
| Bearer token strategy | `jwt` (unchanged) | `bearerTokenStrategy=introspection` or `auto` |
| Multiple OIDC providers | Single `oidc` provider (unchanged) | `oidc.providers=oidc,google,...` |
| JsonPath claim extraction | Dot-notation still works (unchanged) | Paths starting with `$` use JsonPath |
| JWE encrypted tokens | Disabled (unchanged) | `jweKeyStoreFile=/path/to/keystore` |
| Microsoft Graph integration | Disabled (unchanged) | `msGraphEnabled=true` |
| Sensitive info logging | Disabled (unchanged) | `logSensitiveInfo=true` |
| Token age checking | Disabled (unchanged) | `maxTokenAgeSecs=3600` |

!!! tip "Zero-change upgrade"
    If your current deployment works, it will continue to work after upgrading to 2.5 without any property changes. Review the new features below and enable only what you need.

---

## New Features

### Bearer Token Strategy Selection

**Previous behavior:** Bearer tokens were always validated as JWTs using the JWKS endpoint.

**What's new:** You can now choose between three validation strategies:

- **`jwt`** (default) — local JWT validation, same as before
- **`introspection`** — sends the token to the IdP's RFC 7662 introspection endpoint, works with opaque (non-JWT) tokens
- **`auto`** — tries JWT first, falls back to introspection on failure

```properties
# Use introspection for opaque tokens
oidcOAuth2Config.bearerTokenStrategy=introspection

# Or auto-detect: try JWT first, fall back to introspection
oidcOAuth2Config.bearerTokenStrategy=auto
```

The introspection endpoint is auto-discovered from `discoveryUrl`. To set it explicitly:

```properties
oidcOAuth2Config.introspectionEndpoint=https://idp.example.com/oauth2/introspect
```

See [Bearer Tokens - Validation Strategies](../security/bearer-tokens.md#validation-strategies) for details.

### Multiple Simultaneous OIDC Providers

**Previous behavior:** A single OIDC provider (`oidcOAuth2Config`).

**What's new:** You can configure multiple OIDC providers simultaneously (e.g. Keycloak + Google + Azure AD). Each provider gets its own configuration, filter, token cache, and JWKS keys.

```properties
# Declare multiple providers
oidc.providers=oidc,google

# Provider 1: Keycloak (default "oidc" name)
oidcOAuth2Config.enabled=true
oidcOAuth2Config.clientId=geostore-keycloak
oidcOAuth2Config.discoveryUrl=https://keycloak.example.com/realms/master/.well-known/openid-configuration
# ... other properties ...

# Provider 2: Google
googleOAuth2Config.enabled=true
googleOAuth2Config.clientId=123456789.apps.googleusercontent.com
googleOAuth2Config.discoveryUrl=https://accounts.google.com/.well-known/openid-configuration
# ... other properties ...
```

Login URLs follow the pattern `/openid/{provider}/login` (e.g. `/openid/oidc/login`, `/openid/google/login`). Bearer tokens are automatically routed to the correct provider based on audience matching.

If `oidc.providers` is not set, GeoStore defaults to the single `oidc` provider — existing configurations are unaffected.

See [OIDC - Multiple OIDC Providers](../security/oidc.md#multiple-oidc-providers) for details.

### JsonPath Claim Extraction

**Previous behavior:** `rolesClaim` and `groupsClaim` only supported simple dot-notation (e.g. `realm_access.roles`).

**What's new:** Full [JsonPath](https://github.com/json-path/JsonPath) expressions are now supported for complex claim structures:

```properties
# Wildcard — collect roles from ALL resource_access entries
oidcOAuth2Config.rolesClaim=$.resource_access.*.roles

# Array index
oidcOAuth2Config.rolesClaim=$.roles[0]

# Filter expression
oidcOAuth2Config.rolesClaim=$.realm_access.roles[?(@=='ADMIN')]
```

Legacy dot-notation paths (e.g. `realm_access.roles`) continue to work — they are automatically converted to JsonPath internally. Paths starting with `$` are treated as explicit JsonPath expressions.

JsonPath works across all resolution sources: ID token JWT, access token JWT, and userinfo response.

See [Roles & Groups - JsonPath](../security/roles-and-groups.md#nested-claim-paths-and-jsonpath) for details.

### JWE (Encrypted Token) Support

**Previous behavior:** Only plain JWS (signed) tokens were supported.

**What's new:** GeoStore can now decrypt JWE (JSON Web Encryption) tokens. JWE tokens encrypt the payload for confidentiality — the claims are unreadable without the recipient's private key.

```properties
oidcOAuth2Config.jweKeyStoreFile=/etc/geostore/jwe-keystore.p12
oidcOAuth2Config.jweKeyStorePassword=changeit
oidcOAuth2Config.jweKeyStoreType=PKCS12
oidcOAuth2Config.jweKeyAlias=geostore-jwe
```

JWE is opt-in. When `jweKeyStoreFile` is not set, JWE support is completely disabled and plain JWS tokens work exactly as before.

Supported algorithms: RSA-OAEP, RSA-OAEP-256, ECDH-ES, ECDH-ES+A128KW, ECDH-ES+A256KW.

See [Bearer Tokens - JWE](../security/bearer-tokens.md#jwe-encrypted-tokens) for details.

### Microsoft Graph Integration (Azure AD)

**Previous behavior:** Groups were only read from JWT claims. Azure AD's groups overage (>200 groups) caused silent group data loss.

**What's new:** GeoStore can call Microsoft Graph API to resolve group memberships and app role assignments when Azure AD groups overage is detected.

```properties
# Enable MS Graph integration
oidcOAuth2Config.msGraphEnabled=true

# Optional: also resolve app roles
oidcOAuth2Config.msGraphRolesEnabled=true
```

When enabled, GeoStore automatically detects the overage condition (`_claim_names` or `hasgroups=true` in the JWT) and calls `GET /me/memberOf` to resolve group display names. Resolved groups flow through the standard mapping and reconciliation pipeline.

MS Graph is opt-in and requires Azure AD API permissions (`GroupMember.Read.All`). When disabled or when no overage is detected, behavior is unchanged.

See [Azure AD Setup - Microsoft Graph](azure-ad-setup.md#microsoft-graph-group-resolution) for details.

### Sensitive Information Logging

**Previous behavior:** No built-in toggle for security debug logging.

**What's new:** Set `logSensitiveInfo=true` to automatically enable DEBUG logging for all security loggers on the first filter invocation:

```properties
oidcOAuth2Config.logSensitiveInfo=true
```

When active, a prominent warning is logged and full token contents, credentials, and claim details appear in the logs. This is intended for development and debugging only.

For fine-grained runtime control in production, use the [diagnostics endpoint](../security/monitoring-and-auditing.md#dynamic-log-level-control) instead.

### Token Age Checking

**Previous behavior:** Only the `exp` (expiration) claim was checked.

**What's new:** The `maxTokenAgeSecs` property rejects bearer tokens that are older than a configured threshold based on the `iat` (issued-at) claim, even if `exp` hasn't passed yet:

```properties
# Reject tokens older than 1 hour
oidcOAuth2Config.maxTokenAgeSecs=3600
```

When set to `0` (the default), the age check is disabled and only `exp` is checked — same as before.

---

## Property Changes Summary

### New Properties

| Property | Type | Default | Description |
|---|---|---|---|
| `oidc.providers` | String | `oidc` | Comma-separated list of OIDC provider names |
| `bearerTokenStrategy` | String | `jwt` | Bearer token validation: `jwt`, `introspection`, or `auto` |
| `introspectionEndpoint` | String | (auto) | RFC 7662 introspection endpoint URL |
| `maxTokenAgeSecs` | int | `0` | Maximum bearer JWT age in seconds |
| `jweKeyStoreFile` | String | -- | JWE decryption keystore path |
| `jweKeyStorePassword` | String | -- | JWE keystore password |
| `jweKeyStoreType` | String | `PKCS12` | JWE keystore type |
| `jweKeyAlias` | String | *(first)* | JWE private key alias |
| `jweKeyPassword` | String | *(keystore)* | JWE key entry password |
| `msGraphEnabled` | boolean | `false` | Enable Microsoft Graph integration |
| `msGraphEndpoint` | String | `https://graph.microsoft.com/v1.0` | Graph API base URL |
| `msGraphGroupsEnabled` | boolean | `true` | Resolve groups via Graph |
| `msGraphRolesEnabled` | boolean | `false` | Resolve app roles via Graph |
| `msGraphAppId` | String | -- | App ID for role filtering |
| `logSensitiveInfo` | boolean | `false` | Enable DEBUG logging for security loggers |

### Unchanged Properties

All existing properties retain their previous names, types, and defaults. No breaking changes.

---

## Migration from Provider-Specific Configurations

If you are migrating from a deployment that used dedicated provider-specific configuration (e.g. a separate Keycloak adapter or Google-specific module), see the provider-specific migration sections:

- [Keycloak — Migration from Keycloak Adapter](keycloak-setup.md#migration-from-keycloak-adapter)
- [Google — Migration from Dedicated Google Provider](google-setup.md#migration-from-dedicated-google-provider)

---

## Troubleshooting After Upgrade

| Issue | Cause | Resolution |
|---|---|---|
| Bearer tokens stop working | Unlikely — `bearerTokenStrategy` defaults to `jwt` | Explicitly set `oidcOAuth2Config.bearerTokenStrategy=jwt` |
| New properties not recognized | Property file not reloaded | Restart the application after changing `geostore-ovr.properties` |
| `oidc.providers` doesn't work | Properties use wrong prefix | Each provider needs `{name}OAuth2Config.` prefix matching the name in `oidc.providers` |
| MS Graph returns 403 | Missing API permissions | Grant `GroupMember.Read.All` in Azure AD app registration |
| JWE tokens rejected | Wrong key in keystore | Verify `jweKeyAlias` matches a private key entry; use `keytool -list` to inspect |

See the [Monitoring & Auditing](../security/monitoring-and-auditing.md) page for runtime diagnostics tools.
