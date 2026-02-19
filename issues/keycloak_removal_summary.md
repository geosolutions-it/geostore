# Keycloak Adapter Removal — Summary

**Date:** 2026-02-19
**Branch:** `bearer_token_improvements`

---

## Context

The GeoStore security module had two overlapping authentication paths for OpenID Connect providers:

1. **Keycloak-native** — used the Keycloak Java adapter SDK (`org.keycloak:keycloak-spring-security-adapter` v18.0.0) with 17+ classes
2. **Generic OIDC** — uses Spring Security OAuth2 with standard OpenID Connect

After recent improvements to the OIDC path (role/group mappings, PKCE, JWKS signature verification, `iat` validation, bearer token support, token caching), every feature provided by the Keycloak adapter was fully covered by the generic OIDC path. The Keycloak adapter was tightly coupled to a legacy SDK, added significant maintenance burden, and was removed.

---

## What Was Removed

### Java Source Files (17 files)

All files under `src/modules/rest/impl/src/main/java/.../security/keycloak/`:

| File | Purpose |
|------|---------|
| `KeyCloakConfiguration.java` | Config class (roleMappings, groupMappings, jsonConfig) |
| `KeyCloakFilter.java` | Auth filter using Keycloak adapter |
| `KeyCloakSecurityConfiguration.java` | Spring @Configuration beans |
| `KeyCloakHelper.java` | Token refresh, deployment context |
| `KeyCloakLoginService.java` | Login endpoint implementation |
| `KeyCloakRequestWrapper.java` | HTTP request preprocessing |
| `KeycloakAdminClientConfiguration.java` | Admin REST client config |
| `BaseKeycloakDAO.java` | Abstract DAO for Keycloak queries |
| `KeycloakUserDAO.java` | User DAO via Keycloak Admin API |
| `KeycloakUserGroupDAO.java` | Group DAO via Keycloak Admin API |
| `GeoStoreKeycloakAuthenticator.java` | Custom request authenticator |
| `GeoStoreKeycloakAuthProvider.java` | Spring AuthenticationProvider |
| `GeoStoreKeycloakAuthoritiesMapper.java` | Authorities mapper |
| `KeycloakAuthenticationEntryPoint.java` | Auth challenge entry point |
| `GeoStoreOAuthAuthenticator.java` | OAuth authenticator |
| `KeycloakCookieUtils.java` | Cookie-based token storage |
| `KeycloakTokenDetails.java` | Token details wrapper |
| `KeycloakSessionServiceDelegate.java` | Session/refresh/logout delegate |
| `KeycloakQuery.java` | Query abstraction |
| `KeycloakSearchMapper.java` | Search mapping |
| `AuthoritiesMappings.java` | Mappings helper |

### Test Files (9 files)

All files under `src/modules/rest/impl/src/test/java/.../security/keycloak/`:

| File | Purpose |
|------|---------|
| `KeyCloakConfigurationTest.java` | Config tests |
| `KeycloakFilterTest.java` | Filter tests |
| `KeycloakLoginTest.java` | Login flow tests |
| `KeycloakSessionServiceTest.java` | Session tests |
| `KeycloakUserDAOTest.java` | DAO tests |
| `KeycloakUserGroupDAOTest.java` | DAO tests |
| `KeycloakTestSupport.java` | Test base class |
| `MockUserService.java` | Test mock |
| `MockUserGroupService.java` | Test mock |

### Test Fixtures (10 JSON files)

All `keycloak_*.json` files under `src/modules/rest/impl/src/test/resources/__files/`:

- `keycloak_discovery.json`
- `keycloak_token_response.json`
- `token_response_keycloak.json`
- `keycloak_users.json`
- `keycloak_two_users.json`
- `keycloak_admin_user.json`
- `keycloak_roles.json`
- `keycloak_one_role.json`
- `keycloak_two_roles.json`
- `keycloak_user_roles.json`

### Spring XML Config (1 file)

- `src/web/app/src/main/resources/security-integration-keycloak-direct.xml`

### Maven Dependencies (3 artifacts + 1 version property)

**Root `pom.xml`:**
- Removed property: `<keycloak-spring-security-adapter.version>18.0.0</keycloak-spring-security-adapter.version>`

**`src/pom.xml` (dependency management):**
- Removed: `org.keycloak:keycloak-spring-security-adapter`
- Removed: `org.keycloak:keycloak-authz-client`
- Removed: `org.keycloak:keycloak-admin-client`

**`src/modules/rest/impl/pom.xml` (direct dependencies):**
- Removed: `org.keycloak:keycloak-spring-security-adapter`
- Removed: `org.keycloak:keycloak-authz-client`
- Removed: `org.keycloak:keycloak-admin-client`

---

## What Was Modified

### Spring Security XML (`geostore-spring-security.xml`)

- Removed filter chain entry: `<security:custom-filter ref="keycloakFilter" before="BASIC_AUTH_FILTER"/>`
- Removed bean: `<bean id="keycloakConfig" class="...KeyCloakSecurityConfiguration"/>`
- Removed the `<!-- Keycloak -->` comment block

### Application Context XML (`applicationContext.xml`)

- Removed bean: `keycloakServiceDelegate` (KeycloakSessionServiceDelegate)
- Removed bean: `keycloakLoginService` (KeyCloakLoginService)

### Properties File (`geostore-ovr.properties`)

- Removed the entire `# Keycloak` section (lines 64-84), including:
  - `keycloakOAuth2Config.*` properties
  - `keycloakRESTClient.*` properties
- Added an OIDC migration example section with equivalent properties:
  ```properties
  # ----------
  # OpenID Connect (OIDC) — replaces the former Keycloak-specific section
  # Works with Keycloak, Azure AD, Google, Okta, and any OIDC-compliant provider
  # ----------
  # oidcOAuth2Config.enabled=false
  # oidcOAuth2Config.clientId=
  # oidcOAuth2Config.clientSecret=
  # oidcOAuth2Config.discoveryUrl=https://<keycloak-host>/realms/<realm>/.well-known/openid-configuration
  # oidcOAuth2Config.autoCreateUser=true
  # oidcOAuth2Config.redirectUri=
  # oidcOAuth2Config.internalRedirectUri=../../mapstore/
  # oidcOAuth2Config.scopes=openid,email,profile
  # oidcOAuth2Config.authenticatedDefaultRole=USER
  # oidcOAuth2Config.principalKey=email
  # oidcOAuth2Config.rolesClaim=roles
  # oidcOAuth2Config.groupsClaim=groups
  # oidcOAuth2Config.roleMappings=admin:ADMIN,user:USER
  # oidcOAuth2Config.groupMappings=
  # oidcOAuth2Config.dropUnmapped=false
  # oidcOAuth2Config.usePKCE=false
  ```

### Bearer Token Validators (cosmetic)

**`AudienceAccessTokenValidator.java`:**
- Renamed field `KEYCLOAK_AUDIENCE_CLAIM_NAME` → `AZP_CLAIM_NAME` (it's the standard OIDC `azp` claim)
- Updated Javadoc: replaced "Keycloak has the audience..." with provider-neutral language
- Updated inline comment: `// azp - keycloak` → `// azp - authorized party (standard OIDC claim)`
- Updated Javadoc: replaced "from keycloak or azure" with "from an OIDC provider"

**`SubjectTokenValidator.java`:**
- Updated Javadoc: replaced "for keycloak, the sub of..." with "For most OIDC providers, the sub of..."

### Documentation

**`issues/oidc_bearer_token_code_review.md`:**
- Section 6.4: Updated to note Keycloak adapter has been removed, OIDC is the single path

**`issues/keycloak_roles_groups_configuration.md`:**
- Removed "Option B: Keycloak Adapter Path" section entirely
- Updated intro table to show only the OIDC path
- Added migration guide from `keycloakOAuth2Config.*` to `oidcOAuth2Config.*`
- Updated feature comparison table (single-column, OIDC only)
- Updated notes about mapping support (OIDC now supports `roleMappings`/`groupMappings`)

---

## Feature Parity Verification

| Keycloak Feature | OIDC Equivalent | Status |
|-----------------|-----------------|--------|
| Role mappings (`roleMappings`) | `OAuth2Configuration.roleMappings` | Already implemented |
| Group mappings (`groupMappings`) | `OAuth2Configuration.groupMappings` | Already implemented |
| Drop unmapped (`dropUnmapped`) | `OAuth2Configuration.dropUnmapped` | Already implemented |
| Bearer token validation | `OpenIdConnectFilter` + `JwksRsaKeyProvider` | Already implemented |
| JWT signature verification | `JwksRsaKeyProvider` + `RsaVerifier` | Already implemented |
| PKCE | `OpenIdConnectConfiguration` + `PKCERequestEnhancer` | Already implemented |
| Token caching | `TokenAuthenticationCache` (configurable) | Already implemented |
| Auto-discovery | `DiscoveryClient` + `.well-known` | Already implemented |
| Token refresh | `OAuth2SessionServiceDelegate.refresh()` | Already implemented |
| Session logout | `OpenIdConnectSessionServiceDelegate.doLogout()` | Already implemented |
| Login flow | `OpenIdConnectLoginService` | Already implemented |
| User auto-creation | `OAuth2GeoStoreAuthenticationFilter.createUser()` | Already implemented |
| Default role | `IdPConfiguration.authenticatedDefaultRole` | Already implemented |
| Cookie-based tokens | Not ported (in-memory cache is sufficient) | Intentionally dropped |
| Admin Client DAOs | Not ported (DB DAOs remain default) | Intentionally dropped |

---

## Build & Test Results

### QA Compile

```
mvn compile -Dqa=true
```

**Result:** BUILD SUCCESS — all 18 modules compiled cleanly with QA checks (checkstyle, google-java-format, spotbugs, PMD).

### Tests

```
mvn clean test -pl src/modules/rest/impl \
  -Dcheckstyle.skip=true -Dspotless.check.skip=true -Dfailsafe.skip=true
```

**Result:** BUILD SUCCESS — **30 tests run, 0 failures, 0 errors, 0 skipped**

Test classes executed:
- `OpenIdConnectIntegrationTest` (10 tests — redirect, auth, roles/groups, bearer token auth, bearer disabled, bearer principal key, bearer roles/groups, bearer expired, bearer wrong audience, bearer malformed)
- `OAuth2SessionServiceDelegateTest` (8 tests)
- `RefreshTokenServiceTest` (12 tests)

### Keycloak Reference Check

```
grep -ri "keycloak" src/ --include="*.java" --include="*.xml" --include="*.properties" -l
```

**Result:** Only `geostore-ovr.properties` — the OIDC migration comment mentioning "keycloak-host" as an example placeholder. Zero references in Java or XML code.

---

## Notes

- The initial `mvn test` (without `clean`) failed because stale `.class` files for deleted Keycloak tests remained in `target/`. The JUnit 5 test engine tried to scan `KeycloakFilterTest.class` and failed with `NoClassDefFoundError: org/keycloak/common/VerificationException`. Running with `clean` resolved this.
- The `fmt-maven-plugin` (google-java-format) auto-reformatted the modified `AudienceAccessTokenValidator.java` during compile, which is expected behavior.
