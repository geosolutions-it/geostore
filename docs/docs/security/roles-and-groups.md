# Roles & Groups Mapping

GeoStore can map roles and groups from identity provider (IdP) token claims to its internal authorization model. This page describes the role hierarchy, how roles and groups are resolved from JWT tokens, and how to configure mappings.

---

## Role Model

GeoStore uses a three-tier role hierarchy:

| Role | Level | Description |
|------|-------|-------------|
| **ADMIN** | Highest | Full access to all resources and administration |
| **USER** | Default | Standard access, can create and manage own resources |
| **GUEST** | Lowest | Read-only access to public resources |

The `authenticatedDefaultRole` property controls the role assigned to authenticated users when no role can be resolved from token claims. It defaults to `USER`.

```properties
# Override the default role for authenticated users
oidcOAuth2Config.authenticatedDefaultRole=USER
```

---

## Claim Resolution Order

When `rolesClaim` or `groupsClaim` is configured, GeoStore resolves the claim value using a multi-level fallback chain:

1. **JWT (ID token)** -- the ID token returned during the authorization code flow is decoded and the claim is looked up (supports dot-notation for nested claims).
2. **JWT (access token)** -- if the ID token does not contain the claim, the access token JWT is tried next.
3. **Userinfo response** -- if neither JWT contains the claim, the response from the OIDC userinfo endpoint (`checkTokenEndpointUrl`) is checked as a final fallback.

This fallback chain ensures that roles and groups are resolved even when:

- The token is opaque (non-JWT) and claims are only available via the userinfo endpoint.
- The provider (e.g., Google, Azure AD) places certain claims in the userinfo response rather than in the JWT.
- The access token is a minimal JWT that delegates claim details to the userinfo endpoint.

## Role Resolution from Token Claims

When `rolesClaim` is configured, roles are extracted from the JWT token (or userinfo response, per the fallback chain above). GeoStore prefers the **ID token** for claim extraction; if the ID token is not available, it falls back to the **access token**, and finally to the **userinfo response**.

### Resolution Algorithm

```
1. Extract the value at `rolesClaim` path from JWT claims
2. If the value is a list, iterate through each value
3. For each role value:
   a. Look up in `roleMappings` (case-insensitive key lookup -- keys are uppercased)
   b. If a mapping exists, use the mapped value
   c. If no mapping and dropUnmapped=true, skip the value
   d. If no mapping and dropUnmapped=false, use the original value
4. Determine final role:
   - If ANY mapped value equals "ADMIN" (case-insensitive) -> role is ADMIN
   - If a mapped value equals "GUEST" (case-insensitive) -> role is GUEST
   - Otherwise -> role is authenticatedDefaultRole (default: USER)
5. If rolesClaim is missing/empty in the token -> preserve the current user role
```

!!! note "ADMIN takes highest priority"
    The ADMIN role short-circuits evaluation. If **any** role value maps to `ADMIN`, the user receives the ADMIN role regardless of other values in the list.

### Nested Claim Paths

The `rolesClaim` property supports **dot-notation** for nested claims. This is essential for providers like Keycloak that nest roles inside structured objects.

| Claim Path | Token Structure |
|------------|-----------------|
| `roles` | Top-level `roles` claim |
| `realm_access.roles` | Nested inside `realm_access` object (Keycloak realm roles) |
| `resource_access.my-client.roles` | Deeply nested (Keycloak client roles) |

For example, a Keycloak ID token typically contains:

```json
{
  "realm_access": {
    "roles": ["admin", "user", "offline_access"]
  }
}
```

To extract roles from this structure, set:

```properties
oidcOAuth2Config.rolesClaim=realm_access.roles
```

---

## Group Resolution from Token Claims

When `groupsClaim` is configured, groups are extracted from the JWT token and synchronized with GeoStore's internal group model.

### Resolution Algorithm

```
1. Extract the value at `groupsClaim` path from JWT claims
2. If groupMappings is configured and groups are present:
   a. For each group, look up in groupMappings (uppercased key)
   b. If mapped, use the mapped value
   c. If no mapping and dropUnmapped=true, skip
   d. If no mapping and dropUnmapped=false, use original
3. If groupNamesUppercase=true, convert all group names to uppercase
4. Reconcile groups (see below)
```

### Group Reconciliation (Per-Provider)

GeoStore tracks which groups come from which identity provider using a `sourceService` attribute on `UserGroup` entities. This enables multi-provider deployments where each provider manages its own set of groups independently.

On each login, the following reconciliation occurs:

1. Find all groups tagged with the current provider's name
2. **Remove** groups that are tagged for this provider but are **not** in the new token claims
3. For each group in the token claims:
    - If the group does not exist, **create** it and tag it with the provider name
    - If the group exists but is not tagged, **add** the provider tag
    - **Assign** the user to the group if not already assigned
4. Deduplicate by normalized name

!!! tip "Multi-Provider Safety"
    Groups from different providers never interfere with each other. A user can have groups from Keycloak and Azure AD simultaneously -- each provider only manages its own groups. Switching or removing one provider will not affect groups created by another.

---

## Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `rolesClaim` | String | -- | JWT claim path for roles (supports dot-notation) |
| `groupsClaim` | String | -- | JWT claim path for groups (supports dot-notation) |
| `roleMappings` | String | -- | Comma-separated `idpValue:geoStoreValue` pairs |
| `groupMappings` | String | -- | Comma-separated `idpValue:geoStoreValue` pairs |
| `dropUnmapped` | boolean | `false` | Drop roles/groups with no mapping entry |
| `groupNamesUppercase` | boolean | `false` | Convert all group names to uppercase |
| `authenticatedDefaultRole` | String | `USER` | Default role when no role resolved from claims |

All properties use the `oidcOAuth2Config.` prefix. For example: `oidcOAuth2Config.rolesClaim`.

### Mapping Format

Mappings use the format `idp_value:GEOSTORE_VALUE`, with multiple entries separated by commas:

```
idp_value:GEOSTORE_VALUE,idp_value2:GEOSTORE_VALUE2
```

!!! warning "Keys Are Uppercased Internally"
    Mapping keys are converted to uppercase before lookup. This means `admin:ADMIN` and `Admin:ADMIN` are equivalent -- both are stored as `ADMIN:ADMIN`. The **values** are used as-is and are not case-transformed.

Example configuration:

```properties
oidcOAuth2Config.roleMappings=admin:ADMIN,manager:ADMIN,viewer:USER,guest:GUEST
oidcOAuth2Config.groupMappings=dev-team:DEVELOPERS,qa-team:QA
```

With the role mappings above:

- An IdP role of `admin` or `Admin` or `ADMIN` maps to GeoStore `ADMIN`
- An IdP role of `manager` maps to GeoStore `ADMIN`
- An IdP role of `viewer` maps to GeoStore `USER`
- An IdP role of `guest` maps to GeoStore `GUEST`

---

## Examples

### Keycloak with Realm Roles

Keycloak includes realm roles in the `realm_access.roles` claim by default. This example maps Keycloak realm roles to GeoStore roles and extracts groups from a custom `groups` claim.

```properties title="geostore-ovr.properties"
# Enable OIDC provider
oidcOAuth2Config.enabled=true
oidcOAuth2Config.autoCreateUser=true

# Keycloak endpoints
oidcOAuth2Config.clientId=geostore-client
oidcOAuth2Config.clientSecret=my-client-secret
oidcOAuth2Config.discoveryUrl=https://keycloak.example.com/realms/myrealm/.well-known/openid-configuration

# Role mapping from Keycloak realm roles
oidcOAuth2Config.rolesClaim=realm_access.roles
oidcOAuth2Config.roleMappings=admin:ADMIN,realm-admin:ADMIN,default-roles-myrealm:USER
oidcOAuth2Config.dropUnmapped=true

# Group mapping from a custom "groups" claim
oidcOAuth2Config.groupsClaim=groups
oidcOAuth2Config.groupMappings=team-alpha:ALPHA,team-beta:BETA
oidcOAuth2Config.groupNamesUppercase=true
```

With this configuration, a Keycloak token containing:

```json
{
  "realm_access": {
    "roles": ["admin", "default-roles-myrealm", "offline_access"]
  },
  "groups": ["/team-alpha", "/team-beta", "/team-gamma"]
}
```

Would result in:

- **Role**: `ADMIN` (because `admin` maps to `ADMIN`, which has highest priority)
- **Groups**: `ALPHA`, `BETA` (because `team-gamma` is dropped by `dropUnmapped=true`, and names are uppercased)

!!! note "Keycloak Group Prefix"
    Keycloak may include a leading `/` in group names (e.g., `/team-alpha`). If your groups include this prefix, adjust your mapping keys accordingly: `/team-alpha:ALPHA`.

### Azure AD / Entra ID with App Roles

Azure AD exposes roles via the flat `roles` claim when App Roles are configured in the application registration, and groups via the `groups` claim (containing group Object IDs by default).

```properties title="geostore-ovr.properties"
# Enable OIDC provider
oidcOAuth2Config.enabled=true
oidcOAuth2Config.autoCreateUser=true

# Azure AD endpoints
oidcOAuth2Config.clientId=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
oidcOAuth2Config.clientSecret=my-azure-secret
oidcOAuth2Config.discoveryUrl=https://login.microsoftonline.com/{tenant-id}/v2.0/.well-known/openid-configuration

# Role mapping from Azure AD App Roles
oidcOAuth2Config.rolesClaim=roles
oidcOAuth2Config.roleMappings=GeoStore.Admin:ADMIN,GeoStore.User:USER,GeoStore.Reader:GUEST

# Group mapping from Azure AD group Object IDs
oidcOAuth2Config.groupsClaim=groups
oidcOAuth2Config.groupMappings=a1b2c3d4-e5f6-7890-abcd-ef1234567890:EDITORS,b2c3d4e5-f6a7-8901-bcde-f12345678901:VIEWERS
oidcOAuth2Config.dropUnmapped=true

# Default role if no App Role is assigned
oidcOAuth2Config.authenticatedDefaultRole=GUEST
```

With this configuration, an Azure AD token containing:

```json
{
  "roles": ["GeoStore.Admin"],
  "groups": [
    "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "ffffffff-ffff-ffff-ffff-ffffffffffff"
  ]
}
```

Would result in:

- **Role**: `ADMIN` (because `GeoStore.Admin` maps to `ADMIN`)
- **Groups**: `EDITORS` (because the second group ID has no mapping and `dropUnmapped=true`)

---

## Tips and Best Practices

!!! tip "Passthrough Mode"
    If `dropUnmapped=false` (the default), unmapped roles and groups pass through as-is. This is useful when your IdP role names already match GeoStore role names (e.g., the IdP sends `ADMIN` directly).

!!! tip "Strict Mode"
    If `dropUnmapped=true`, only explicitly mapped values are used. This is recommended for strict control, especially in multi-tenant environments where you want to prevent unexpected role assignments.

!!! note "ADMIN Priority"
    The ADMIN role always takes highest priority. If **any** role in the token maps to `ADMIN`, the user receives ADMIN access regardless of other roles present.

!!! note "Optional rolesClaim"
    The `rolesClaim` property is optional. If it is not set (or the claim is not present in the token), users receive the `authenticatedDefaultRole` (default: `USER`). If the claim path is configured but the value is missing from a specific token, the user's current role is preserved.

!!! warning "Group Reconciliation is Per-Provider"
    Group reconciliation only affects groups tagged with the current provider. Switching providers or reconfiguring a provider will not remove groups created by a different provider. To clean up orphaned groups, use the GeoStore REST API directly.
