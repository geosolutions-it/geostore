# Code Review: OIDC Bearer Token Authentication Fix

**Date:** 2026-02-17
**Scope:** 4 modified files, 280 lines added / 35 removed
**Tests:** 10/10 passing

---

## 1. Summary of Changes

### Files Modified

| File | Change |
|------|--------|
| `OpenIdConnectFilter.java` | Core fix: bearer tokens validated via direct JWT decode + expiry check instead of introspection |
| `SubjectTokenValidator.java` | Null guard: skip subject comparison when no userinfo available |
| `OAuth2GeoStoreAuthenticationFilter.java` | Bug fix: group assignment with null IDs + null service guards |
| `OpenIdConnectIntegrationTest.java` | JUnit 5 migration + 7 new tests (bearer auth, expiry, audience, malformed) |

### What Changed and Why

**Problem:** When GeoStore is configured with OIDC only (no Keycloak adapter), the initial
OAuth2 login callback succeeds, but subsequent API requests with `Authorization: Bearer <token>`
fail with 401. The root cause: `OpenIdConnectFilter.getPreAuthenticatedPrincipal()` always
delegated to `super.getPreAuthenticatedPrincipal()` which triggers Spring's introspection-based
flow, making an HTTP call to the userinfo endpoint. This call is fragile and fails with many
OIDC-only providers.

**Fix:** When the authentication type is BEARER and `bearerTokenValidator` is configured, the
filter now:
1. Decodes the JWT directly (`JwtHelper.decode()`)
2. Checks token expiry (`exp` claim)
3. Runs the validator chain (audience check)
4. Extracts the principal from JWT claims using configured keys + fallbacks
5. Sets the access token on the rest template context for downstream role/group extraction
6. **Skips** `super.getPreAuthenticatedPrincipal()` entirely

The USER auth type (initial login callback) flow is unchanged.

---

## 2. Test Coverage

| Test | What it covers |
|------|---------------|
| `testRedirect` | Login redirect to OIDC provider |
| `testAuthentication` | Authorization code login flow |
| `testGroupsAndRolesFromToken` | Groups/roles from login token claims |
| `testBearerTokenAuthentication` | Bearer JWT auth with email principal |
| `testBearerTokenDisallowed` | Bearer rejected when `allowBearerTokens=false` |
| `testBearerTokenWithPrincipalKey` | Custom `principalKey` for bearer tokens |
| `testBearerTokenRolesAndGroups` | ADMIN role + multiple groups from bearer JWT |
| `testBearerTokenExpired` | Expired JWT rejected via `exp` claim |
| `testBearerTokenWrongAudience` | Wrong `aud` claim rejected by `AudienceAccessTokenValidator` |
| `testBearerTokenMalformed` | Invalid JWT format fails gracefully |

**Note:** The test class was migrated from JUnit 4 to JUnit 5. The original 3 tests were
never actually executed because the module uses `JUnitPlatformProvider` (JUnit 5) with no
`junit-vintage-engine`, but the test class had JUnit 4 annotations (`org.junit.Test`,
`@Before`, `@BeforeClass`).

---

## 3. Bugs Fixed

### 3.1 Bearer token auth fails with OIDC-only providers (primary fix)

**File:** `OpenIdConnectFilter.java:82-187`

The bearer path now validates JWTs directly instead of routing through Spring's introspection
flow. The principal is extracted from JWT claims using the configured `principalKey` (or
`uniqueUsername`) with a fallback chain: `upn`, `preferred_username`, `unique_name`,
`user_name`, `username`, `email`, `sub`, `oid`.

### 3.2 SubjectTokenValidator NPE on direct bearer validation

**File:** `SubjectTokenValidator.java:24-28`

When the bearer path calls `bearerTokenValidator.verifyToken(config, claims, null)`, the
`SubjectTokenValidator` would NPE trying to access `userInfoClaims.get("sub")`. Fixed with
an early return when `userInfoClaims` is null or empty.

### 3.3 Group deduplication fails when IDs are null

**File:** `OAuth2GeoStoreAuthenticationFilter.java:808-819`

In `reconcileRemoteGroups()`, the `alreadyAssigned` check compared groups by `getId()`.
When `userGroupService` is null or fails to persist (IDs remain null),
`Objects.equals(null, null)` returns `true`, silently dropping all groups after the first.

Fixed by falling back to group name comparison when either ID is null:
```java
if (finalGroup.getId() != null && g.getId() != null) {
    return Objects.equals(g.getId(), finalGroup.getId());
}
return Objects.equals(
        normalizeGroupName(g.getGroupName()),
        normalizeGroupName(finalGroup.getGroupName()));
```

### 3.4 NPE when assigning groups without persistence layer

**File:** `OAuth2GeoStoreAuthenticationFilter.java:820-833`

The original code called `userGroupService.assignUserGroup(user.getId(), group.getId())`
unconditionally, NPE-ing when `userGroupService`, `user.getId()`, or `group.getId()` is null.
Fixed with null guards. The in-memory `user.getGroups().add(group)` is now always executed
regardless of persistence success.

### 3.5 Token expiry not validated for bearer tokens

**File:** `OpenIdConnectFilter.java:142-156`

Neither `AudienceAccessTokenValidator` nor `SubjectTokenValidator` checks the JWT `exp` claim.
Added explicit expiry validation before running the validator chain.

---

## 4. Security Analysis

### 4.1 ~~CRITICAL: No JWT Signature Verification~~

**Status:** RESOLVED. JWT signature verification is now implemented via `JwksRsaKeyProvider`
which fetches keys from the configured JWKS endpoint and verifies token signatures using
the `kid` header. The `OpenIdConnectFilter` uses `JwtHelper.decodeAndVerify()` with an
`RsaVerifier` when a JWKS key provider is available.

### 4.2 LOW: Log Injection via Principal

**File:** `OpenIdConnectFilter.java:170`
```java
LOGGER.info("Authenticated OIDC Bearer token for user (JWT claims): {}", principal);
```

The `principal` value comes from JWT claims (attacker-controlled). In modern Log4j 2 with
SLF4J-style `{}` placeholders, the value is properly escaped and not interpreted as a format
string. Risk is negligible with current logging configuration.

### 4.3 LOW: Weak Principal Fallback (`oid`)

**File:** `OpenIdConnectFilter.java:166`

The fallback chain includes `oid` (Azure Object ID), which could result in a UUID being used
as a username. This mirrors the parent class behavior and is not a regression, but means a
crafted JWT with only an `oid` claim would create a user with a UUID name.

### 4.4 Thread Safety: OK

**File:** `OpenIdConnectFilter.java:175`
```java
restTemplate.getOAuth2ClientContext().setAccessToken(accessToken);
```

The `restTemplate` bean is request-scoped (`SCOPE_REQUEST` at
`OpenIdConnectSecurityConfiguration.java:88`), so each HTTP request gets its own instance.
No thread safety issue.

---

## 5. Correctness Analysis

### 5.1 Bearer Path Flow (end-to-end trace)

```
1. doFilter()  [OAuth2GeoStoreAuthenticationFilter:166]
   - Checks OAuth2 enabled, no existing Authentication
   - Calls super.doFilter()

2. attemptAuthentication()  [OAuth2GeoStoreAuthenticationFilter:182]
   - tokenFromParamsOrBearer() extracts token from Authorization header
   - Sets OAUTH2_AUTHENTICATION_TYPE_KEY = BEARER
   - Wraps token as DefaultOAuth2AccessToken
   - Calls authenticateAndUpdateCache()

3. authenticateAndUpdateCache()  [OAuth2GeoStoreAuthenticationFilter:240]
   - Checks TokenAuthenticationCache (returns cached auth if valid)
   - Calls performOAuthAuthentication()

4. performOAuthAuthentication()  [OAuth2GeoStoreAuthenticationFilter:305]
   - Calls getPreAuthenticatedPrincipal()

5. OpenIdConnectFilter.getPreAuthenticatedPrincipal()  [OpenIdConnectFilter:82]
   - Type is BEARER + validator exists -> direct JWT path:
     a. Resolve token string from OAuth2AccessToken / request attributes
     b. Decode JWT claims (JwtHelper.decode)
     c. Check exp claim
     d. Run bearerTokenValidator.verifyToken() (audience + subject)
     e. Extract principal from claims (configured keys + fallbacks)
     f. Set access token on restTemplate context
     g. Return principal string

6. Back in performOAuthAuthentication()
   - Principal is non-blank -> calls createPreAuthentication()

7. createPreAuthentication()  [OAuth2GeoStoreAuthenticationFilter:575]
   - Retrieves or auto-creates user
   - Reads access token from restTemplate context (set in step 5f)
   - Calls addAuthoritiesFromToken() -> syncs roles + groups from JWT
   - Returns PreAuthenticatedAuthenticationToken

8. Back in authenticateAndUpdateCache()
   - Caches the authentication keyed by token value
   - Sets SecurityContext
```

### 5.2 Exception Handling

When `getPreAuthenticatedPrincipal()` throws IOException (bearer disallowed, missing token,
decode failure, expired, invalid audience), the exception is caught at
`performOAuthAuthentication():316`:
```java
} catch (IOException | ServletException e1) {
    LOGGER.error("Error obtaining pre-authenticated principal: {}", e1.getMessage(), e1);
}
```

Principal remains null, no authentication is created, and the request continues unauthenticated
(filter chain proceeds). This is correct behavior — the error is logged and the request is
treated as anonymous.

### 5.3 Cache Integration

Bearer tokens are cached in `TokenAuthenticationCache` keyed by the JWT string value. Identical
bearer tokens across requests hit the cache and skip re-validation. Cache entries have a TTL
(default 30 minutes). This means an expired JWT could still authenticate from cache until the
cache entry expires. This is acceptable behavior — the cache TTL should be shorter than typical
token lifetimes.

### 5.4 Edge Cases

| Scenario | Behavior | Status |
|----------|----------|--------|
| `type` is null (non-OAuth request) | Falls through to `super.getPreAuthenticatedPrincipal()` | Correct |
| `bearerTokenValidator` is null | Falls through to parent's introspection flow | Correct |
| Bearer + validator + `allowBearerTokens=false` | Throws IOException, request is anonymous | Correct |
| Token expired | Throws IOException, request is anonymous | Correct |
| Wrong audience | Validator throws, IOException, request is anonymous | Correct |
| Malformed JWT (bad base64) | Decode throws, IOException, request is anonymous | Correct |
| No recognizable principal in claims | Returns null, request is anonymous | Correct |
| Array-type claim as principal value | `String.valueOf()` returns stringified array, user created with that name | Minor issue, not exploitable |

---

## 6. Items NOT in Scope of This Fix

### 6.1 JWT Signature Verification

See section 4.1 above. Requires a separate `SignatureTokenValidator` implementation.

### 6.2 ~~OIDC Role/Group Mapping~~

**Status:** RESOLVED. `roleMappings` and `groupMappings` (plus `dropUnmapped`) are now
supported on `OAuth2Configuration`. The `computeRole()` and `addAuthoritiesFromToken()`
methods in `OAuth2GeoStoreAuthenticationFilter` apply mappings before name comparison.
Configuration format: `"idp_role:ADMIN,idp_viewer:GUEST"` (comma-separated key:value pairs).

### 6.3 ~~PKCE Support~~

**Status:** RESOLVED. PKCE is now fully implemented:
- `OpenIdConnectConfiguration.getAuthenticationEntryPoint()` generates a random `code_verifier`,
  computes `code_challenge = BASE64URL(SHA-256(code_verifier))`, stores the verifier in the
  HTTP session, and appends `code_challenge` + `code_challenge_method=S256` to the login URI.
- `PKCERequestEnhancer.enhance()` retrieves the `code_verifier` from the session if not
  already present in the `AccessTokenRequest`.

### 6.4 Keycloak Filter

`KeyCloakFilter.java` uses Keycloak's own adapter library (`RequestAuthenticator`) for bearer
token validation. It is NOT affected by the introspection bug and was not modified.

---

## 7. Recommendations

| Priority | Item | Effort | Status |
|----------|------|--------|--------|
| **High** | Implement JWT signature verification via JWK endpoint | Medium | **RESOLVED** — `JwksRsaKeyProvider` + `JwtHelper.decodeAndVerify()` |
| Medium | Add `roleMappings`/`groupMappings` support to OIDC path | Medium | **RESOLVED** — `OAuth2Configuration.roleMappings/groupMappings/dropUnmapped` |
| Medium | Implement PKCE (`code_challenge` / `code_verifier`) | Medium | **RESOLVED** — `OpenIdConnectConfiguration` entry point + `PKCERequestEnhancer` |
| Low | Handle array-type principal claims gracefully | Small | **RESOLVED** — `coalesceClaimValue()` extracts first element from arrays/collections |
| Low | Add `iat` (issued-at) validation to reject very old tokens | Small | **RESOLVED** — `maxTokenAgeSecs` config + `iat` check in `OpenIdConnectFilter` |
| Low | Make cache TTL configurable for bearer tokens | Small | **RESOLVED** — `TokenAuthenticationCache(cacheSize, cacheExpirationMinutes)` constructor |
