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
| Nested claim paths | JSON Path notation | Dot notation (JWT + userinfo) |
| Auto-create users | N/A (different user model) | Yes (`autoCreateUser`) |
| Configurable principal key | Yes | Yes (`principalKey`, `uniqueUsername`) |
| `sendClientSecret` toggle | Yes | Yes |
| Opaque token introspection | Yes (RFC 7662) | Yes (`bearerTokenStrategy`: jwt / introspection / auto) |

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

### Gap 1 (was Gap 2): Multiple simultaneous OIDC providers

**Priority:** High
**GeoServer:** Supports separate configurations for Google, GitHub, Azure, Keycloak, and generic OIDC — users can run multiple providers simultaneously (e.g., Google + Keycloak). Each gets its own filter chain entry, callback URL, and login button.

**GeoStore current state:** A single `oidcOAuth2Config` bean. Multiple `OAuth2Configuration` beans are technically possible (Spring will wire them), but there's no documented pattern, no separate callback routes per provider, and no tested multi-provider flow.

**What's needed:** Document and test the multi-provider pattern. Likely needs per-provider callback endpoints (`/openid/{provider}/callback`), and the login service needs to present multiple provider options.

---

### Gap 2 (was Gap 3): GitHub OAuth2 (non-OIDC provider)

**Priority:** Low
**GeoServer:** Dedicated GitHub provider that works with plain OAuth2 (no ID token, opaque access tokens). Uses GitHub's `/user` API endpoint for user info.

**GeoStore current state:** Requires OIDC-compliant providers. GitHub won't work because it doesn't issue ID tokens or support OIDC discovery.

**What's needed:** A separate OAuth2 flow that doesn't depend on OIDC (no ID token, no discovery). This is a significant effort for a single provider and low priority unless there's demand.

---

### Gap 3 (was Gap 4): Microsoft Graph role source

**Priority:** Low
**GeoServer:** Dedicated Azure Graph API integration to fetch `appRoleAssignments` from the Microsoft Graph endpoint. This is useful when Azure AD app roles are complex or exceed the token size limit.

**GeoStore current state:** Relies on token claims only. Azure AD users must ensure roles appear in the `roles` claim via manifest configuration (`groupMembershipClaims`).

**What's needed:** A pluggable role source mechanism that can call external APIs (like Graph) after initial authentication. Low priority because the token claim approach works for most Azure AD setups.

---

### Gap 4 (was Gap 5): Full JSON Path for role/group extraction

**Priority:** Low
**GeoServer:** Uses JSON Path expressions for navigating token structures (e.g., `resource_access.geostore-client.roles` with potential for wildcards and array indexing).

**GeoStore current state:** Simple dot notation (e.g., `realm_access.roles`). Supports multi-level nesting but no wildcards, array indexing, or filters.

**What's needed:** Replace or extend the dot-notation resolver with a JSON Path library (e.g., Jayway JsonPath). Low priority because dot notation covers the vast majority of real-world claim structures.

---

### Gap 5 (was Gap 6): JWE (encrypted token) support

**Priority:** Low
**GeoServer:** Can handle encrypted opaque tokens in JWE format.

**GeoStore current state:** No JWE support. Tokens must be plain JWTs (JWS) or opaque tokens validated via introspection.

**What's needed:** JWE decryption before JWT validation. Very niche — most OIDC providers use JWS, not JWE.

---

### Gap 6 (was Gap 7): Dedicated "log sensitive information" toggle

**Priority:** Low
**GeoServer:** Single checkbox flag to dump full token contents (access token, ID token, userinfo response) to logs for debugging.

**GeoStore current state:** Dynamic log level control via the diagnostics endpoint (`PUT /diagnostics/logging/{logger}/DEBUG`). More flexible but requires knowing which loggers to enable. Token values are logged at DEBUG level in `GeoStoreOAuthRestTemplate`.

**What's needed:** Optionally, a single `logSensitiveInfo=true` property that sets all security loggers to DEBUG. Low priority since the diagnostics endpoint already provides this capability.

---

## Resolved gaps

| Former gap | Resolution | Branch |
|---|---|---|
| Opaque token introspection (was Gap 1) | Fully implemented: `bearerTokenStrategy` supports jwt, introspection, and auto. RFC 7662 introspection via `introspectToken()`, auto strategy with JWT-first fallback. | `bearer_token_improvements` |

---

## Summary

| Gap | Priority | Effort | Impact |
|---|---|---|---|
| Multiple simultaneous providers | High | High | Enables organizations with multiple IdPs |
| GitHub OAuth2 | Low | High | Single non-OIDC provider |
| Microsoft Graph role source | Low | Medium | Niche Azure AD use case |
| Full JSON Path | Low | Small | Covers edge cases only |
| JWE support | Low | Medium | Very niche |
| Log sensitive info toggle | Low | Small | Convenience only |
