# Keycloak Setup Guide

## Overview

Keycloak is one of the most commonly used OpenID Connect (OIDC) identity providers with GeoStore. It provides enterprise-grade identity management with support for realms, clients, roles, groups, and protocol mappers -- all of which integrate cleanly with GeoStore's generic OIDC configuration.

GeoStore uses its **generic OIDC integration** (based on Spring Security OAuth2) rather than a Keycloak-specific adapter. This means:

- No Keycloak-specific libraries are required on the GeoStore side.
- All configuration is done through `geostore-ovr.properties`.
- The same integration approach works with any OIDC-compliant provider.

This guide covers the complete setup: creating a realm and client in Keycloak, configuring protocol mappers for roles and groups, and wiring everything into GeoStore.

---

## Prerequisites

Before you begin, make sure you have:

- A **Keycloak server** running and accessible (Keycloak 18+ recommended).
- **Admin access** to the Keycloak administration console.
- A **GeoStore instance** deployed and accessible. HTTPS is strongly recommended for OAuth2 flows in production.
- Network connectivity between GeoStore and Keycloak (GeoStore must be able to reach Keycloak's endpoints for discovery, token exchange, and JWKS).

!!! warning
    Running OAuth2 over plain HTTP is acceptable for local development but **must not** be used in production. Tokens transmitted over unencrypted connections can be intercepted, leading to session hijacking.

---

## Keycloak Configuration

### Step 1: Create or Select a Realm

1. Log in to the Keycloak administration console (typically at `https://keycloak.example.com/admin`).
2. In the top-left realm selector, either choose an existing realm or click **Create Realm**.
3. If creating a new realm:
    - Enter a **Realm name** (e.g., `geostore-realm`).
    - Set **Enabled** to ON.
    - Click **Create**.

!!! note
    The realm name is part of the OIDC discovery URL. For a realm called `geostore-realm`, the discovery URL will be:
    ```
    https://keycloak.example.com/realms/geostore-realm/.well-known/openid-configuration
    ```

### Step 2: Create a Client

1. In the left sidebar, go to **Clients** and click **Create client**.
2. Fill in the initial settings:
    - **Client type**: `OpenID Connect`
    - **Client ID**: `geostore` (or your preferred name)
    - Click **Next**.
3. On the **Capability config** page:
    - **Client authentication**: ON (this makes it a confidential client with a client secret)
    - **Standard flow**: Enabled
    - **Direct access grants**: Enabled (optional -- useful for testing with `curl`)
    - Click **Next**.
4. On the **Login settings** page, fill in the redirect and origin URLs:

| Setting | Value |
|---------|-------|
| **Root URL** | `https://your-geostore-host/geostore` |
| **Valid redirect URIs** | `https://your-geostore-host/geostore/rest/users/user/details*` |
| **Valid post logout redirect URIs** | `https://your-geostore-host/mapstore/*` |
| **Web origins** | `https://your-geostore-host` |

5. Click **Save**.

After creating the client, navigate to the **Credentials** tab and copy the **Client secret**. You will need this for the GeoStore configuration.

!!! tip
    The **Valid redirect URIs** must match the `redirectUri` configured in GeoStore. The trailing `*` wildcard allows query parameters to be appended by the OAuth2 flow.

### Step 3: Configure Protocol Mappers for Roles

By default, Keycloak includes realm roles in the access token under the `realm_access.roles` claim. To also expose them in the **ID token** (which GeoStore prefers for claim extraction), you need to add or verify a protocol mapper.

1. Go to **Clients** > **geostore** > **Client scopes** tab.
2. Click on the **geostore-dedicated** scope.
3. Click **Add mapper** > **By configuration** > **User Realm Role**.
4. Configure the mapper:

| Field | Value |
|-------|-------|
| **Name** | `realm roles` |
| **Mapper type** | User Realm Role |
| **Token Claim Name** | `realm_access.roles` |
| **Claim JSON Type** | String |
| **Add to ID token** | ON |
| **Add to access token** | ON |
| **Add to userinfo** | ON |

5. Click **Save**.

!!! note
    If you prefer a flat `roles` claim at the top level instead of the nested `realm_access.roles` structure, set the **Token Claim Name** to `roles`. Then configure `oidcOAuth2Config.rolesClaim=roles` in GeoStore. The nested path `realm_access.roles` is the Keycloak default and is fully supported by GeoStore's dot-notation claim resolution.

### Step 4: Configure Protocol Mappers for Groups

Keycloak does not include groups in tokens by default. You must add a Group Membership mapper explicitly.

1. Go to **Clients** > **geostore** > **Client scopes** tab.
2. Click on the **geostore-dedicated** scope.
3. Click **Add mapper** > **By configuration** > **Group Membership**.
4. Configure the mapper:

| Field | Value |
|-------|-------|
| **Name** | `groups` |
| **Mapper type** | Group Membership |
| **Token Claim Name** | `groups` |
| **Full group path** | OFF |
| **Add to ID token** | ON |
| **Add to access token** | ON |
| **Add to userinfo** | ON |

5. Click **Save**.

!!! warning
    Set **Full group path** to **OFF** unless you specifically need hierarchical paths like `/parent/child`. When set to ON, Keycloak prefixes group names with `/` (e.g., `/developers` instead of `developers`), which requires adjusting your `groupMappings` keys accordingly.

### Step 5: Create Roles and Groups

#### Realm Roles

1. In the left sidebar, go to **Realm roles**.
2. Click **Create role**.
3. Create roles that correspond to your GeoStore role mappings. For example:

| Keycloak Role | Maps to GeoStore Role |
|---------------|----------------------|
| `admin` | `ADMIN` |
| `user` | `USER` |

#### Groups

1. In the left sidebar, go to **Groups**.
2. Click **Create group**.
3. Create groups as needed for your organization (e.g., `editors`, `viewers`, `developers`).

#### Assign to Users

1. Go to **Users** and select a user.
2. On the **Role mapping** tab, click **Assign role** and select the appropriate realm roles.
3. On the **Groups** tab, click **Join Group** and select the appropriate groups.

---

## GeoStore Configuration

Add the following to your `geostore-ovr.properties` file:

```properties title="geostore-ovr.properties"
# -----------------------------------------------
# OIDC Configuration -- Keycloak
# -----------------------------------------------

# Enable the OIDC authentication filter
oidcOAuth2Config.enabled=true

# Keycloak client credentials
oidcOAuth2Config.clientId=geostore
oidcOAuth2Config.clientSecret=YOUR_CLIENT_SECRET

# Discovery URL -- Keycloak realm issuer
# GeoStore auto-discovers all endpoints (authorization, token, JWKS, etc.)
oidcOAuth2Config.discoveryUrl=https://keycloak.example.com/realms/your-realm/.well-known/openid-configuration

# OAuth2 redirect URI -- must match a Valid Redirect URI in the Keycloak client
oidcOAuth2Config.redirectUri=https://your-geostore-host/geostore/rest/users/user/details

# After successful login, redirect the browser to MapStore (or your front-end)
oidcOAuth2Config.internalRedirectUri=../../mapstore/

# Scopes to request
oidcOAuth2Config.scopes=openid,email,profile

# Auto-create users in GeoStore DB on first OIDC login
oidcOAuth2Config.autoCreateUser=true

# Default role for authenticated users (when no role is resolved from claims)
oidcOAuth2Config.authenticatedDefaultRole=USER

# --- Principal Resolution ---
# Use Keycloak's preferred_username claim as the GeoStore username
oidcOAuth2Config.principalKey=preferred_username

# --- Role Mapping ---
# Extract roles from Keycloak's nested realm_access.roles claim
oidcOAuth2Config.rolesClaim=realm_access.roles
oidcOAuth2Config.roleMappings=admin:ADMIN,user:USER

# --- Group Mapping ---
# Extract groups from the "groups" claim (requires the Group Membership mapper)
oidcOAuth2Config.groupsClaim=groups

# --- Bearer Tokens ---
# JWT strategy is the default; tokens are validated locally using the JWKS endpoint
oidcOAuth2Config.bearerTokenStrategy=jwt
```

!!! tip
    You only need to set `discoveryUrl` -- GeoStore will auto-discover the authorization endpoint, token endpoint, JWKS URI, logout endpoint, and introspection endpoint. See the [OIDC Configuration](../security/oidc.md#discovery) page for the full list of auto-discovered fields.

---

## Nested Claim Paths

Keycloak uses nested JSON structures in its tokens. GeoStore's `rolesClaim` and `groupsClaim` properties support **dot-notation** to navigate these structures.

Common Keycloak token claim paths:

| Claim | Path | Example Value |
|-------|------|---------------|
| **Realm roles** | `realm_access.roles` | `["admin", "user", "offline_access"]` |
| **Client roles** | `resource_access.geostore.roles` | `["manage-resources"]` |
| **Groups** | `groups` | `["developers", "editors"]` |

A typical Keycloak access token looks like this:

```json
{
  "realm_access": {
    "roles": ["admin", "user", "offline_access", "uma_authorization"]
  },
  "resource_access": {
    "geostore": {
      "roles": ["manage-resources"]
    },
    "account": {
      "roles": ["manage-account", "view-profile"]
    }
  },
  "groups": ["developers", "editors"],
  "preferred_username": "johndoe",
  "email": "john.doe@example.com"
}
```

To use **realm roles**, set:

```properties
oidcOAuth2Config.rolesClaim=realm_access.roles
```

To use **client-specific roles** instead, set:

```properties
oidcOAuth2Config.rolesClaim=resource_access.geostore.roles
```

!!! note
    If you use client roles, replace `geostore` in the path with your actual Keycloak client ID.

---

## Migration from Keycloak Adapter

If you were previously using the now-removed Keycloak-specific adapter (`keycloak-spring-security-adapter` or similar), follow these steps to migrate to the generic OIDC integration:

1. **Remove Keycloak adapter dependencies.** Delete any `keycloak-*` JAR files or Maven dependencies from your deployment.

2. **Remove Keycloak-specific Spring beans.** Delete any `KeycloakWebSecurityConfigurerAdapter`, `KeycloakSpringBootConfigResolver`, or `keycloak.json` configuration files.

3. **Add the OIDC configuration.** Add the properties shown in the [GeoStore Configuration](#geostore-configuration) section above to your `geostore-ovr.properties`.

4. **Replace the old URL pattern.** The discovery URL replaces the old `auth-server-url` + `realm` combination:

    | Old (Keycloak Adapter) | New (OIDC) |
    |------------------------|------------|
    | `keycloak.auth-server-url=https://keycloak.example.com` | `oidcOAuth2Config.discoveryUrl=https://keycloak.example.com/realms/your-realm/.well-known/openid-configuration` |
    | `keycloak.realm=your-realm` | *(included in the discovery URL)* |
    | `keycloak.resource=geostore` | `oidcOAuth2Config.clientId=geostore` |
    | `keycloak.credentials.secret=SECRET` | `oidcOAuth2Config.clientSecret=SECRET` |

5. **Role and group claims work the same way.** The `realm_access.roles` and `groups` claim paths are unchanged -- only the configuration property names differ.

!!! tip
    The main difference is that the generic OIDC integration uses Spring Security OAuth2 instead of the Keycloak adapter. All configuration is done entirely through `geostore-ovr.properties` rather than `keycloak.json` or Spring Boot properties. The token claim structure from Keycloak remains identical.

---

## Testing the Integration

### Browser-Based Login

After configuring both Keycloak and GeoStore, test the interactive login flow by navigating to:

```
https://your-geostore-host/geostore/rest/users/user/details?provider=oidc
```

You should be redirected to the Keycloak login page. After successful authentication, you will be redirected back to GeoStore (or to the `internalRedirectUri` if configured).

### Bearer Token with curl

You can test bearer token authentication by obtaining a token from Keycloak's token endpoint and using it to call the GeoStore REST API.

```bash
# 1. Get a token from Keycloak using the Resource Owner Password Grant
#    (requires "Direct access grants" enabled on the client)
TOKEN=$(curl -s -X POST \
  "https://keycloak.example.com/realms/your-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=geostore" \
  -d "client_secret=YOUR_CLIENT_SECRET" \
  -d "username=testuser" \
  -d "password=testpass" \
  | jq -r '.access_token')

# 2. Verify the token was obtained
echo "$TOKEN" | head -c 50
echo "..."

# 3. Call GeoStore API with the bearer token
curl -H "Authorization: Bearer $TOKEN" \
  "https://your-geostore-host/geostore/rest/resources"
```

!!! tip
    To inspect the token claims (and verify roles/groups are present), paste the access token into [jwt.io](https://jwt.io) or decode it locally:
    ```bash
    echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null | jq .
    ```

### Verifying Role and Group Mappings

Call the user details endpoint with a bearer token to confirm that roles and groups are mapped correctly:

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://your-geostore-host/geostore/rest/users/user/details"
```

The response should include the user's resolved role and group memberships.

---

## Audience Configuration

Some Keycloak versions do not include the `aud` (audience) claim in access tokens by default. If GeoStore rejects bearer tokens with the message *"probably not meant for this application"*, you need to add an audience mapper.

1. Go to **Clients** > **geostore** > **Client scopes** tab.
2. Click on the **geostore-dedicated** scope.
3. Click **Add mapper** > **By configuration** > **Audience**.
4. Configure the mapper:

| Field | Value |
|-------|-------|
| **Name** | `geostore audience` |
| **Included Client Audience** | `geostore` |
| **Add to ID token** | ON |
| **Add to access token** | ON |

5. Click **Save**.

This ensures the `aud` claim in the access token contains your client ID, which the `AudienceAccessTokenValidator` checks during bearer token validation. See [Bearer Token Authentication](../security/bearer-tokens.md#audienceaccesstokenvalidator) for details.

---

## Troubleshooting

| Problem | Possible Cause | Solution |
|---------|---------------|----------|
| Login redirect fails with "Invalid redirect URI" | The `redirectUri` in GeoStore does not match any Valid Redirect URI in Keycloak | Verify the URI matches exactly. Check for trailing slashes and protocol (http vs https) |
| "Bearer tokens aren't allowed" | `allowBearerTokens` is `false` | Ensure `oidcOAuth2Config.allowBearerTokens=true` (this is the default) |
| Roles not mapped correctly | `rolesClaim` does not match the actual token structure | Decode the token (see [Testing](#bearer-token-with-curl)) and verify the claim path. Use `realm_access.roles` for realm roles |
| Groups not appearing | No Group Membership mapper configured, or mapper not added to the correct scope | Add the mapper to the **geostore-dedicated** client scope as described in [Step 4](#step-4-configure-protocol-mappers-for-groups) |
| "probably not meant for this application" | The `aud` claim does not contain the configured `clientId` | Add an Audience mapper in Keycloak (see [Audience Configuration](#audience-configuration)) |
| "No JWK key found for kid" | GeoStore cannot reach the Keycloak JWKS endpoint | Verify network connectivity between GeoStore and Keycloak. Check firewall rules and DNS resolution |
| User created but has wrong role | Role mapping mismatch or `authenticatedDefaultRole` is used | Verify `roleMappings` matches the exact role names in the token. Remember that mapping keys are uppercased internally |
| Groups include `/` prefix | **Full group path** is ON in the Group Membership mapper | Set **Full group path** to OFF, or adjust `groupMappings` keys to include the prefix (e.g., `/developers:DEVELOPERS`) |
| Discovery fails at startup | GeoStore cannot reach the discovery URL | Ensure the URL is correct and reachable. If outbound connections are restricted, set each endpoint property manually instead of using discovery |
| CORS errors in browser | Web Origins not configured in the Keycloak client | Add your GeoStore host to the **Web origins** field in the Keycloak client settings |

!!! tip
    Enable DEBUG logging for `it.geosolutions.geostore.services.rest.security.oauth2` to see detailed authentication flow information, including token claims, role resolution, and group reconciliation steps.

---

## Further Reading

- [OIDC / OAuth2 Configuration](../security/oidc.md) -- full reference for all OIDC configuration properties
- [Bearer Token Authentication](../security/bearer-tokens.md) -- JWT, introspection, and auto validation strategies
- [Roles & Groups Mapping](../security/roles-and-groups.md) -- detailed role resolution algorithm and mapping format
- [Keycloak Server Administration Guide](https://www.keycloak.org/docs/latest/server_admin/) -- official Keycloak documentation
