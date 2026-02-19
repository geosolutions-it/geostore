# GeoStore vs GeoServer OIDC — Feature Gap Analysis

**Date:** 2026-02-19
**GeoServer reference:** [OIDC docs (2.28.0)](https://docs.geoserver.org/main/en/user/community/oidc/index.html), [security community module](https://github.com/geoserver/geoserver/tree/main/src/community/security)

---

## Feature Parity

| Feature | GeoServer | GeoStore |
|---|---|---|
| Authorization Code flow | Yes | Yes |
| PKCE | Yes | Yes |
| Bearer JWT validation | Yes | Yes |
| JWKS signature verification | Yes | Yes |
| Token caching | Yes (until token expiry) | Yes (Guava cache, configurable TTL) |
| OIDC Discovery | Yes (auto-fill button) | Yes (`discoveryUrl` property) |
| Role extraction from JWT | ID token + access token | ID token + access token |
| Role extraction from UserInfo | Yes | Yes (fallback when JWT claim missing) |
| Role mapping (external to internal) | Role Converter Map | `roleMappings` property |
| Drop unmapped roles | "Only allow explicit roles" checkbox | `dropUnmapped` property |
| Audience validation | Yes | Yes (`AudienceAccessTokenValidator`) |
| Subject validation | Yes | Yes (`SubjectTokenValidator`) |
| Token expiry checking | Yes (`exp` claim) | Yes (`exp` + optional `iat` via `maxTokenAgeSecs`) |
| Logout / post-logout redirect | Yes | Yes (`postLogoutRedirectUri`) |
| Configurable scopes | Yes (space-separated) | Yes (comma-separated) |
| Nested claim paths | JSON Path notation | JsonPath + dot notation (JWT + userinfo) |
| Auto-create users | N/A (different user model) | Yes (`autoCreateUser`) |
| Configurable principal key | Yes | Yes (`principalKey`, `uniqueUsername`) |
| `sendClientSecret` toggle | Yes | Yes |
| Opaque token introspection | Yes (RFC 7662) | Yes (`bearerTokenStrategy`: jwt / introspection / auto) |
| Multiple simultaneous providers | Yes (Google, GitHub, Azure, Keycloak, generic) | Yes (`oidc.providers`, `CompositeOpenIdConnectFilter`) |
| JWE (encrypted tokens) | Yes | Yes (`JweTokenDecryptor`, RSA-OAEP / ECDH-ES, opt-in via `jweKeyStoreFile`) |
| Microsoft Graph group/role resolution | Yes | Yes (`msGraphEnabled`, overage detection + `/me/memberOf` + `/me/appRoleAssignments`) |

---

## GeoStore advantages (features GeoServer lacks)

| Feature | Details |
|---|---|
| **Group synchronization from claims** | Extracts groups from JWT/userinfo via `groupsClaim`, auto-creates them in DB (tagged `sourceService=oidc`), and reconciles memberships on each login. GeoServer only extracts roles, not groups. |
| **Group mappings** | `groupMappings` property with `dropUnmapped` + `groupNamesUppercase` support. |
| **Admin diagnostics endpoint** | `/rest/diagnostics/` — runtime log level control, token cache inspection, OIDC config dump with secrets redacted. |
| **JSON error bodies on 401** | Structured `{"error":"unauthorized","message":"..."}` responses instead of empty bodies. |
| **`iat` (issued-at) validation** | `maxTokenAgeSecs` config rejects tokens older than a threshold, even if `exp` hasn't passed. |

---

## GeoStore gaps (features GeoServer has that GeoStore lacks)

### Gap 1: GitHub OAuth2 (non-OIDC provider)

**Priority:** Low
**GeoServer:** Dedicated GitHub provider that works with plain OAuth2 (no ID token, opaque access tokens). Uses GitHub's `/user` API endpoint for user info.

**GeoStore current state:** Requires OIDC-compliant providers. GitHub won't work because it doesn't issue ID tokens or support OIDC discovery.

**What's needed:** A separate OAuth2 flow that doesn't depend on OIDC (no ID token, no discovery). This is a significant effort for a single provider and low priority unless there's demand.

---

### ~~Gap 2: Microsoft Graph role/group source~~ **RESOLVED**

**Status:** Implemented
**GeoStore:** `msGraphEnabled=true` activates automatic Azure AD groups overage detection and resolution via Microsoft Graph API (`/me/memberOf` for groups, `/me/appRoleAssignments` + `/servicePrincipals/{id}/appRoles` for roles). Overage is detected by checking for `_claim_names` or `hasgroups=true` in the JWT. Completely opt-in, zero changes to existing behavior when disabled. Graph results flow through the standard role/group mapping pipeline. Graceful degradation on Graph API failures.

---

### Gap 3: Dedicated "log sensitive information" toggle

**Priority:** Low
**GeoServer:** Single checkbox flag to dump full token contents (access token, ID token, userinfo response) to logs for debugging.

**GeoStore current state:** Dynamic log level control via the diagnostics endpoint (`PUT /diagnostics/logging/{logger}/DEBUG`). More flexible but requires knowing which loggers to enable. Token values are logged at DEBUG level in `GeoStoreOAuthRestTemplate`.

**What's needed:** Optionally, a single `logSensitiveInfo=true` property that sets all security loggers to DEBUG. Low priority since the diagnostics endpoint already provides this capability.

---

## Resolved gaps

| Former gap | Resolution | Branch |
|---|---|---|
| Opaque token introspection (was Gap 1) | Fully implemented: `bearerTokenStrategy` supports jwt, introspection, and auto. RFC 7662 introspection via `introspectToken()`, auto strategy with JWT-first fallback. | `bearer_token_improvements` |
| Multiple simultaneous OIDC providers (was Gap 2) | Fully implemented: `oidc.providers` property, `CompositeOpenIdConnectFilter` with per-provider routing for login/callback URLs and bearer tokens. Cross-provider isolation via separate JWKS/audience. | `bearer_token_improvements` |
| Full JSON Path (was Gap 4) | Fully implemented: `rolesClaim`/`groupsClaim` support full JsonPath expressions (e.g. `$.resource_access.*.roles`) via Jayway JsonPath, plus legacy dot-notation auto-conversion. | `bearer_token_improvements` |
| JWE encrypted token support (was Gap 6) | Fully implemented: `JweTokenDecryptor` detects 5-part JWE tokens and decrypts via Nimbus JOSE+JWT using the relying party's private key from a configurable Java keystore. Supports RSA-OAEP and ECDH-ES. Opt-in via `jweKeyStoreFile`. | `bearer_token_improvements` |

---

## Summary

| Gap | Priority | Effort | Impact |
|---|---|---|---|
| GitHub OAuth2 | Low | High | Single non-OIDC provider |
| Microsoft Graph role source | Low | Medium | Niche Azure AD use case |
| Log sensitive info toggle | Low | Small | Convenience only |
