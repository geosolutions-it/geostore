# Keycloak Configuration for Roles and Groups in GeoStore

## Overview

This document describes how to configure Keycloak so that GeoStore can assign **ADMIN** and
**USER** roles and manage **user groups** from token claims.

GeoStore integrates with Keycloak (and any OIDC-compliant provider) via the generic OIDC path:

| Integration | Config prefix | Roles/Groups source | Mapping support |
|---|---|---|---|
| **OIDC provider** | `oidcOAuth2Config.*` | JWT token claims (`rolesClaim`, `groupsClaim`) | `roleMappings`, `groupMappings`, `dropUnmapped` |

> **Note:** The former Keycloak-specific adapter (`keycloakOAuth2Config.*`) has been removed.
> All Keycloak integration now uses the standard OIDC path above.

---

## GeoStore Role Model

GeoStore has three roles with a strict hierarchy (highest privilege first):

| Role | Ordinal | Description |
|---|---|---|
| `ADMIN` | 0 | Full access |
| `USER` | 1 | Standard authenticated user |
| `GUEST` | 2 | Minimal access |

### How roles are computed from token claims (OIDC path)

Code: `OAuth2GeoStoreAuthenticationFilter.computeRole()` (line 706)

```
1. If the roles claim is missing or empty -> use authenticatedDefaultRole (default: USER)
2. Start with authenticatedDefaultRole
3. Scan the list of roles from the claim:
   - If any value equals "ADMIN" (case-insensitive) -> immediately return ADMIN
   - If any value equals "GUEST" (case-insensitive) -> mark as GUEST (can be overridden by ADMIN)
4. Return the resolved role
```

**Key takeaway:** The roles claim must contain the literal strings `ADMIN`, `USER`, or `GUEST`
(case-insensitive). Any other value is ignored for role assignment.

---

## Option A: OIDC Provider Path (Recommended for OIDC-only setups)

### GeoStore Properties

```properties
# --- Core OIDC ---
oidcOAuth2Config.enabled=true
oidcOAuth2Config.clientId=geostore-client
oidcOAuth2Config.clientSecret=<CLIENT-SECRET>
oidcOAuth2Config.discoveryUrl=https://<KEYCLOAK-HOST>/realms/<REALM>/.well-known/openid-configuration
oidcOAuth2Config.sendClientSecret=true
oidcOAuth2Config.autoCreateUser=true
oidcOAuth2Config.redirectUri=https://<GEOSTORE-HOST>/geostore/rest/openid/oidc/callback
oidcOAuth2Config.internalRedirectUri=https://<GEOSTORE-HOST>/
oidcOAuth2Config.scopes=openid,email,profile

# --- Principal ---
oidcOAuth2Config.principalKey=email

# --- Roles (from token claim) ---
oidcOAuth2Config.rolesClaim=roles
oidcOAuth2Config.authenticatedDefaultRole=USER

# --- Groups (from token claim) ---
oidcOAuth2Config.groupsClaim=groups

# --- Optional: uppercase group names ---
# oidcOAuth2Config.groupNamesUppercase=false
```

### How it works

- **Roles:** GeoStore reads the `rolesClaim` (e.g. `roles`) from the ID token (preferred)
  or access token. The claim value must be a string or JSON array containing `ADMIN`, `USER`,
  or `GUEST`. The highest-privilege role found wins.

- **Groups:** GeoStore reads the `groupsClaim` (e.g. `groups`) from the token. The claim value
  must be a string or JSON array of group names. Groups are automatically created in GeoStore
  if they don't exist, tagged with `sourceService=oidc`. On each login, the user's remote
  groups are reconciled (added/removed) to match the token.

- **Mapping:** The OIDC path supports `roleMappings`, `groupMappings`, and `dropUnmapped`
  properties. If your Keycloak roles are named differently from `ADMIN`/`USER`/`GUEST`,
  configure the mappings (e.g., `oidcOAuth2Config.roleMappings=admin:ADMIN,editor:USER`).
  Alternatively, use a Keycloak protocol mapper to transform them
  (see Keycloak Configuration below).

### Keycloak Configuration

#### 1. Create the Client

1. Go to **Clients** > **Create client**
2. Set **Client ID** = `geostore-client`
3. Set **Client authentication** = ON (confidential)
4. Set **Valid redirect URIs** = `https://<GEOSTORE-HOST>/geostore/rest/openid/oidc/callback`
5. Copy the **Client secret** from the **Credentials** tab

#### 2. Create Realm Roles

Create two realm-level roles that GeoStore will recognize:

1. Go to **Realm roles** > **Create role**
   - Role name: `ADMIN`
2. **Create role** again
   - Role name: `USER`

These names must match exactly (case-insensitive) what GeoStore expects.

#### 3. Create Groups

1. Go to **Groups** > **Create group**
   - Name: `group-alpha`
2. Repeat for `group-beta` and `group-gamma`

#### 4. Assign Roles and Groups to Users

For each user:
1. Go to **Users** > select user > **Role mapping** > **Assign role**
   - Assign either `ADMIN` or `USER`
2. Go to **Users** > select user > **Groups** > **Join group**
   - Assign one or more of: `group-alpha`, `group-beta`, `group-gamma`

#### 5. Add Protocol Mappers (to include claims in the token)

##### Roles Mapper

Add a mapper to include realm roles in the token:

1. Go to **Clients** > `geostore-client` > **Client scopes** tab
2. Click the `geostore-client-dedicated` scope
3. **Add mapper** > **By configuration** > **User Realm Role**
4. Configure:
   - **Name:** `roles`
   - **Token Claim Name:** `roles`
   - **Claim JSON Type:** `String`
   - **Add to ID token:** ON
   - **Add to access token:** ON
   - **Add to userinfo:** ON
   - **Multivalued:** ON

##### Groups Mapper

Add a mapper to include group memberships in the token:

1. Same scope as above > **Add mapper** > **By configuration** > **Group Membership**
2. Configure:
   - **Name:** `groups`
   - **Token Claim Name:** `groups`
   - **Full group path:** OFF (use simple names, not `/parent/child` paths)
   - **Add to ID token:** ON
   - **Add to access token:** ON
   - **Add to userinfo:** ON

#### 6. Verify the Token

Use Keycloak's token endpoint or the **Evaluate** tab in Client Scopes to inspect the token.
The ID token should contain:

```json
{
  "email": "user@example.com",
  "roles": ["USER"],
  "groups": ["group-alpha", "group-beta"],
  ...
}
```

An admin user would have:

```json
{
  "email": "admin@example.com",
  "roles": ["ADMIN"],
  "groups": ["group-alpha", "group-gamma"],
  ...
}
```

---

## Migration from Keycloak Adapter

The former Keycloak adapter path (`keycloakOAuth2Config.*`) has been removed. To migrate,
use the OIDC path with equivalent role/group mapping properties:

```properties
# Former Keycloak adapter config          ->  OIDC equivalent
# keycloakOAuth2Config.enabled=true       ->  oidcOAuth2Config.enabled=true
# keycloakOAuth2Config.jsonConfig=...     ->  oidcOAuth2Config.discoveryUrl=https://<HOST>/realms/<REALM>/.well-known/openid-configuration
#                                             oidcOAuth2Config.clientId=<CLIENT-ID>
#                                             oidcOAuth2Config.clientSecret=<CLIENT-SECRET>
# keycloakOAuth2Config.roleMappings=...   ->  oidcOAuth2Config.roleMappings=admin:ADMIN,editor:USER
# keycloakOAuth2Config.groupMappings=...  ->  oidcOAuth2Config.groupMappings=KC-TEAM-A:team-alpha
# keycloakOAuth2Config.dropUnmapped=false ->  oidcOAuth2Config.dropUnmapped=false
```

---

## Features

| Feature | OIDC path (`oidcOAuth2Config`) |
|---|---|
| Role source | JWT claim (configurable name via `rolesClaim`) |
| Group source | JWT claim (configurable name via `groupsClaim`) |
| Role mapping | `roleMappings=kc_role:GEOSTORE_ROLE` |
| Group mapping | `groupMappings=KC_GROUP:geostore_group` |
| Drop unmapped | `dropUnmapped=true/false` |
| Group auto-create | Yes (tagged with `sourceService=oidc`) |
| Group reconciliation | Yes (add/remove on each login) |
| Bearer token support | Yes (`allowBearerTokens=true`) |
| PKCE support | Yes (`usePKCE=true`) |

---

## Quick-Start: Minimal OIDC Setup with 2 Roles and 3 Groups

### Keycloak Side

```
Realm roles:     ADMIN, USER
Groups:          analysts, editors, viewers
Protocol mapper: "roles"  -> claim "roles"  (User Realm Role, multivalued)
Protocol mapper: "groups" -> claim "groups" (Group Membership, simple names)
```

### GeoStore Side

```properties
oidcOAuth2Config.enabled=true
oidcOAuth2Config.clientId=geostore-client
oidcOAuth2Config.clientSecret=<secret>
oidcOAuth2Config.discoveryUrl=https://keycloak.example.com/realms/myrealm/.well-known/openid-configuration
oidcOAuth2Config.sendClientSecret=true
oidcOAuth2Config.autoCreateUser=true
oidcOAuth2Config.redirectUri=https://geostore.example.com/geostore/rest/openid/oidc/callback
oidcOAuth2Config.internalRedirectUri=https://geostore.example.com/
oidcOAuth2Config.scopes=openid,email,profile
oidcOAuth2Config.principalKey=email
oidcOAuth2Config.rolesClaim=roles
oidcOAuth2Config.groupsClaim=groups
oidcOAuth2Config.authenticatedDefaultRole=USER
```

### Expected Token

```json
{
  "sub": "a1b2c3d4",
  "email": "jane.doe@example.com",
  "roles": ["ADMIN"],
  "groups": ["analysts", "editors"]
}
```

GeoStore will:
1. Identify the user as `jane.doe@example.com` (via `principalKey=email`)
2. Assign role `ADMIN` (from `roles` claim)
3. Create/assign groups `analysts` and `editors`
4. Remove any previously assigned remote groups not in the current token
