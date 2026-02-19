# Azure AD / Entra ID Setup Guide

## Overview

Microsoft Entra ID (formerly Azure Active Directory) can be used as an OpenID Connect (OIDC) identity provider for GeoStore. This guide walks through registering an application in the Azure portal and configuring GeoStore to authenticate users against your Azure AD tenant.

GeoStore's OIDC integration handles several Azure-specific behaviors out of the box:

- **Audience validation** -- Azure AD access tokens use a fixed `aud` claim (e.g., the Microsoft Graph API identifier) instead of the application's client ID. GeoStore's `AudienceAccessTokenValidator` also checks the `appid` and `azp` claims, so Azure-issued tokens are accepted without extra configuration.
- **Subject mapping** -- Azure AD may return different `sub` values in the access token and the userinfo response. GeoStore's `SubjectTokenValidator` handles the Azure-specific `xms_st.sub` claim for cross-token subject comparison.
- **Group object IDs** -- Azure AD emits group memberships as GUIDs by default. GeoStore supports mapping these to human-readable group names via `groupMappings`.

!!! note
    This guide assumes you are using the Azure AD **v2.0** endpoints. The v2.0 endpoints return standard OIDC-compliant tokens and are the recommended choice for new integrations.

---

## Prerequisites

- An Azure AD (Microsoft Entra ID) tenant with sufficient privileges to register applications (Application Administrator or Global Administrator role).
- A GeoStore instance accessible via **HTTPS**. Azure AD requires HTTPS for redirect URIs in production (localhost is exempt for development).

---

## Azure AD Configuration

### Step 1: Register an Application

1. Sign in to the [Azure Portal](https://portal.azure.com).
2. Navigate to **Microsoft Entra ID** (or search for "Entra ID" in the portal search bar).
3. Go to **App registrations** -> **New registration**.
4. Fill in the registration form:

    | Field | Value |
    |-------|-------|
    | **Name** | `GeoStore` (or any descriptive name) |
    | **Supported account types** | Choose based on your needs (see below) |
    | **Redirect URI** | Platform: `Web`, URI: `https://your-geostore-host/geostore/rest/users/user/details` |

5. Click **Register**.

!!! tip "Choosing the account type"
    - **Single tenant** -- Only users from your Azure AD directory can sign in. Best for internal/enterprise deployments.
    - **Multi-tenant** -- Users from any Azure AD directory can sign in. Use this if GeoStore serves multiple organizations.
    - **Multi-tenant + personal accounts** -- Also allows Microsoft personal accounts (outlook.com, live.com).

### Step 2: Configure Authentication

1. In the app registration, go to the **Authentication** tab.
2. Verify the redirect URI is listed under **Web** platform redirects:
    ```
    https://your-geostore-host/geostore/rest/users/user/details
    ```
3. Under **Implicit grant and hybrid flows**, enable **ID tokens** (optional -- only needed for hybrid flow).
4. Under **Front-channel logout URL**, enter:
    ```
    https://your-geostore-host/geostore/
    ```
5. Click **Save**.

### Step 3: Create a Client Secret

1. Go to **Certificates & secrets** -> **Client secrets** -> **New client secret**.
2. Enter a description (e.g., `GeoStore production`) and choose an expiry period.
3. Click **Add**.

!!! warning
    Copy the secret **Value** immediately after creation. It is displayed only once. If you lose it, you must create a new secret.

### Step 4: Configure Token Claims

1. Go to **Token configuration** -> **Add optional claim**.
2. Select **ID** token type and add the following claims:

    - `email`
    - `preferred_username`
    - `groups`

3. Select **Access** token type and add the same claims:

    - `email`
    - `preferred_username`
    - `groups`

4. Click **Add**. If prompted to add Microsoft Graph permissions, accept.

For the groups claim, additional configuration may be needed:

1. Go to **Token configuration** -> **Add groups claim**.
2. Select **Security groups** (or **All groups**, depending on your needs).
3. For each token type, optionally check **Emit groups as role claims** if you want groups delivered in the `roles` claim instead.

!!! note
    If you select "Emit groups as role claims", the groups will appear under the `roles` claim in the token. Otherwise, they appear under the `groups` claim. Adjust your GeoStore `groupsClaim` or `rolesClaim` property accordingly.

### Step 5: Define App Roles (Optional)

App Roles provide a cleaner alternative to group-based access control, especially when group membership exceeds Azure AD's limits.

1. Go to **App roles** -> **Create app role**.
2. Create roles for your application:

    | Field | Example Value |
    |-------|---------------|
    | **Display name** | `Admin` |
    | **Allowed member types** | Users/Groups |
    | **Value** | `Admin` |
    | **Description** | `GeoStore administrator` |

3. Repeat for additional roles (e.g., `User`, `Viewer`).
4. To assign users or groups to these roles:
    - Go to **Enterprise Applications** -> find your `GeoStore` app.
    - Go to **Users and groups** -> **Add user/group**.
    - Select the user/group and assign the appropriate role.

!!! tip
    App Roles are emitted in the `roles` claim of the token. They are simpler to manage than groups because they are defined per-application, and there is no 200-group limit (see [Group Object IDs vs Names](#group-object-ids-vs-names) below).

### Step 6: API Permissions

1. Go to **API permissions**.
2. Ensure the following **Microsoft Graph** delegated permissions are granted:

    | Permission | Type | Description |
    |------------|------|-------------|
    | `openid` | Delegated | Sign users in |
    | `email` | Delegated | View users' email address |
    | `profile` | Delegated | View users' basic profile |

3. If your organization requires it, click **Grant admin consent for [your tenant]**.

### Step 7: Note the Endpoints

1. Go to the **Overview** page of your app registration.
2. Record the following values:

    | Value | Where to find it |
    |-------|------------------|
    | **Application (client) ID** | Overview page, top section |
    | **Directory (tenant) ID** | Overview page, top section |
    | **OpenID Connect metadata document** | Overview -> Endpoints button |

3. The discovery URL follows this pattern:
    ```
    https://login.microsoftonline.com/{tenant-id}/v2.0/.well-known/openid-configuration
    ```

    Replace `{tenant-id}` with your **Directory (tenant) ID**.

---

## GeoStore Configuration

Add the following properties to your `geostore-ovr.properties` file, replacing the placeholder values with your Azure AD app registration details.

```properties
# -----------------------------------------------
# OIDC Configuration — Azure AD / Entra ID
# -----------------------------------------------

# Enable the OIDC authentication filter
oidcOAuth2Config.enabled=true

# Azure AD app registration credentials
oidcOAuth2Config.clientId=YOUR_APPLICATION_CLIENT_ID
oidcOAuth2Config.clientSecret=YOUR_CLIENT_SECRET

# Discovery URL — uses the v2.0 endpoint for standard OIDC compliance
oidcOAuth2Config.discoveryUrl=https://login.microsoftonline.com/YOUR_TENANT_ID/v2.0/.well-known/openid-configuration

# OAuth2 redirect URI — must match the redirect URI registered in Azure AD
oidcOAuth2Config.redirectUri=https://your-geostore-host/geostore/rest/users/user/details

# After successful login, redirect the browser here
oidcOAuth2Config.internalRedirectUri=../../mapstore/

# Scopes to request
oidcOAuth2Config.scopes=openid,email,profile

# Auto-create users in GeoStore DB on first OIDC login
oidcOAuth2Config.autoCreateUser=true

# Default role for authenticated users
oidcOAuth2Config.authenticatedDefaultRole=USER

# Send client secret in the token request body (required for Azure AD confidential clients)
oidcOAuth2Config.sendClientSecret=true

# -----------------------------------------------
# Principal Resolution
# -----------------------------------------------

# Azure AD typically provides preferred_username (UPN) or email
oidcOAuth2Config.principalKey=preferred_username
# Alternative — use email instead:
# oidcOAuth2Config.principalKey=email

# -----------------------------------------------
# Roles (if using App Roles)
# -----------------------------------------------
oidcOAuth2Config.rolesClaim=roles
oidcOAuth2Config.roleMappings=Admin:ADMIN,User:USER

# -----------------------------------------------
# Groups (if using the groups claim)
# -----------------------------------------------
oidcOAuth2Config.groupsClaim=groups

# -----------------------------------------------
# Bearer Token Configuration
# -----------------------------------------------
oidcOAuth2Config.bearerTokenStrategy=jwt
```

!!! tip "Testing the configuration"
    After configuring OIDC, trigger the login flow by navigating to:
    ```
    https://your-geostore-host/geostore/rest/users/user/details?provider=oidc
    ```
    If discovery and credentials are correct, you will be redirected to the Microsoft login page.

---

## Azure AD Specifics

### Audience Claim

Azure AD v2.0 access tokens use a fixed `aud` claim that corresponds to the target API resource, not the client application itself. For example, if your application requests Microsoft Graph scopes, the `aud` claim will be `00000003-0000-0000-c000-000000000000` (the Microsoft Graph resource identifier).

GeoStore's `AudienceAccessTokenValidator` handles this by checking multiple claims in the following order:

| Claim | Description |
|-------|-------------|
| `aud` | Standard OIDC audience -- checked first (string or list) |
| `azp` | Standard OIDC authorized party |
| `appid` | Azure AD specific -- the application ID of the client that requested the token |

If any of these claims matches the configured `clientId`, the token is accepted. This means Azure AD tokens work without requiring custom audience overrides.

### Subject Mapping

Azure AD may issue different `sub` (subject) values in the access token and the userinfo endpoint response. This is a known behavior when the access token is issued for a different audience than the client itself.

GeoStore's `SubjectTokenValidator` handles this automatically by checking the `xms_st.sub` claim in the access token. This Azure-specific claim contains the subject value that corresponds to the userinfo `sub`, enabling cross-token subject comparison to succeed.

!!! note
    The `xms_st` (cross-Microsoft subject token) claims are Azure AD extension claims. GeoStore checks for them transparently -- no configuration is needed.

### Group Object IDs vs Names

By default, Azure AD emits group memberships as **object IDs** (GUIDs) rather than display names. For example:

```json
{
  "groups": [
    "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "b2c3d4e5-f6a7-8901-bcde-f12345678901"
  ]
}
```

There are several approaches to handle this:

**Option 1: Map GUIDs to GeoStore group names**

Use `groupMappings` to translate each GUID to a meaningful group name:

```properties
oidcOAuth2Config.groupMappings=a1b2c3d4-e5f6-7890-abcd-ef1234567890:EDITORS,b2c3d4e5-f6a7-8901-bcde-f12345678901:VIEWERS
```

**Option 2: Use App Roles instead of groups**

App Roles are emitted with human-readable values (the `Value` field you defined in Step 5) and do not have the 200-group limit. Configure GeoStore to read from the `roles` claim:

```properties
oidcOAuth2Config.rolesClaim=roles
oidcOAuth2Config.roleMappings=Admin:ADMIN,User:USER
```

**Option 3: Configure Azure AD to emit group names**

In the Azure portal, under **Token configuration** -> **Groups claim**, some configurations allow emitting group display names. However, this is limited and not available for all group types.

!!! warning
    Azure AD limits the `groups` claim to **200 groups** per token. If a user belongs to more than 200 groups, Azure AD returns a `_claim_sources` link (a URL to fetch the full group list) instead of the groups array. GeoStore does not currently resolve this overage link. For users with large group memberships, use **App Roles** instead.

---

## Differences from Standard OIDC

The following table summarizes where Azure AD behavior deviates from standard OIDC conventions and how GeoStore handles each difference.

| Feature | Standard OIDC | Azure AD | GeoStore Handling |
|---------|--------------|----------|-------------------|
| Audience (`aud`) | Set to the client ID | Fixed value (e.g., MS Graph resource ID) | Checks `appid` and `azp` as fallbacks |
| Application identity | `azp` (authorized party) | `appid` (Azure-specific claim) | `AudienceAccessTokenValidator` checks both |
| Subject mapping | `sub` matches across tokens | `sub` may differ; uses `xms_st.sub` | `SubjectTokenValidator` checks `xms_st.sub` |
| Groups | Direct group names | Object IDs (GUIDs) by default | Use `groupMappings` or App Roles |
| Discovery URL | Provider-specific | `login.microsoftonline.com/{tenant}/v2.0` | Standard discovery, no special handling needed |
| Token format | JWT | JWT (v2.0 tokens recommended) | Standard JWT validation via JWKS |

---

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|----------|
| *"probably not meant for this application"* | Azure's `aud` claim does not match the client ID | This is handled automatically via `appid`/`azp` checks. Verify that the `clientId` in GeoStore matches the **Application (client) ID** in Azure AD |
| Groups appear as GUIDs | Azure AD emits group object IDs by default | Configure `groupMappings` to map GUIDs to names, or switch to App Roles |
| No `email` claim in the token | The `email` optional claim is not configured | Go to **Token configuration** -> **Add optional claim** -> select `email` for both ID and Access token types |
| Token validation fails | Using v1.0 endpoints instead of v2.0 | Ensure the `discoveryUrl` uses the `/v2.0/` path: `https://login.microsoftonline.com/{tenant}/v2.0/.well-known/openid-configuration` |
| *"subjects dont match"* | Azure's `sub` differs between access token and userinfo | GeoStore handles this automatically via `xms_st.sub`. If the error persists, verify you are using the v2.0 endpoint |
| Login redirects fail | Redirect URI mismatch | Ensure the `redirectUri` in GeoStore **exactly** matches the redirect URI registered in Azure AD (including trailing slashes and protocol) |
| *"AADSTS700016: Application not found"* | Wrong tenant or client ID | Verify the `clientId` and tenant ID in the `discoveryUrl` are correct |
| Admin consent required | Permissions require admin approval | Go to **API permissions** in Azure AD and click **Grant admin consent** |
| *"Attached Bearer Token has expired"* | Azure AD access tokens have a default lifetime of 60-90 minutes | Request a new token. Consider using refresh tokens for long-lived sessions |

!!! tip "Enabling debug logging"
    To diagnose authentication issues, enable debug logging for the GeoStore security module:
    ```properties
    log4j.logger.it.geosolutions.geostore.services.rest.security=DEBUG
    ```
    This will log token claims, audience validation results, and principal resolution details.

---

## Multi-Provider Setup

When running Azure AD alongside other identity providers (e.g. Keycloak, Google), declare Azure AD as one of the providers in `oidc.providers` and use a distinct prefix for its properties.

```properties
# Declare Keycloak + Azure AD
oidc.providers=oidc,azure

# --- Keycloak (default "oidc" provider) ---
oidcOAuth2Config.enabled=true
oidcOAuth2Config.clientId=geostore
oidcOAuth2Config.clientSecret=KEYCLOAK_SECRET
oidcOAuth2Config.discoveryUrl=https://keycloak.example.com/realms/your-realm/.well-known/openid-configuration
oidcOAuth2Config.scopes=openid,email,profile
oidcOAuth2Config.redirectUri=https://your-geostore-host/geostore/rest/users/user/details
oidcOAuth2Config.autoCreateUser=true
oidcOAuth2Config.principalKey=preferred_username
oidcOAuth2Config.rolesClaim=realm_access.roles
oidcOAuth2Config.roleMappings=admin:ADMIN,user:USER

# --- Azure AD (additional provider) ---
azureOAuth2Config.enabled=true
azureOAuth2Config.clientId=YOUR_APPLICATION_CLIENT_ID
azureOAuth2Config.clientSecret=YOUR_CLIENT_SECRET
azureOAuth2Config.discoveryUrl=https://login.microsoftonline.com/YOUR_TENANT_ID/v2.0/.well-known/openid-configuration
azureOAuth2Config.scopes=openid,email,profile
azureOAuth2Config.redirectUri=https://your-geostore-host/geostore/rest/users/user/details
azureOAuth2Config.internalRedirectUri=../../mapstore/
azureOAuth2Config.autoCreateUser=true
azureOAuth2Config.principalKey=preferred_username
azureOAuth2Config.sendClientSecret=true
azureOAuth2Config.rolesClaim=roles
azureOAuth2Config.roleMappings=Admin:ADMIN,User:USER
```

With this configuration:

- Keycloak login: `https://your-geostore-host/geostore/rest/users/user/details?provider=oidc`
- Azure AD login: `https://your-geostore-host/geostore/rest/users/user/details?provider=azure`
- Bearer tokens are automatically routed to the correct provider based on audience/signature

!!! tip
    When registering Azure AD as a multi-provider, make sure the **redirect URI** in the Azure portal includes a wildcard or matches the GeoStore callback URL pattern. Each provider's callback flows through `/openid/{provider}/callback`.

See [Multiple OIDC Providers](../security/oidc.md#multiple-oidc-providers) for the full multi-provider architecture overview.

---

## Complete Example

Below is a complete `geostore-ovr.properties` configuration for Azure AD with App Roles, bearer token support, and logout enabled.

```properties
# -----------------------------------------------
# OIDC Configuration — Azure AD / Entra ID
# Complete example
# -----------------------------------------------

# Core OIDC settings
oidcOAuth2Config.enabled=true
oidcOAuth2Config.clientId=12345678-abcd-efgh-ijkl-1234567890ab
oidcOAuth2Config.clientSecret=your-client-secret-value
oidcOAuth2Config.discoveryUrl=https://login.microsoftonline.com/abcdef01-2345-6789-abcd-ef0123456789/v2.0/.well-known/openid-configuration

# Redirect URIs
oidcOAuth2Config.redirectUri=https://geostore.example.com/geostore/rest/users/user/details
oidcOAuth2Config.internalRedirectUri=../../mapstore/

# Scopes and authentication
oidcOAuth2Config.scopes=openid,email,profile
oidcOAuth2Config.sendClientSecret=true
oidcOAuth2Config.autoCreateUser=true
oidcOAuth2Config.authenticatedDefaultRole=USER

# Principal resolution
oidcOAuth2Config.principalKey=preferred_username

# App Roles mapping
oidcOAuth2Config.rolesClaim=roles
oidcOAuth2Config.roleMappings=Admin:ADMIN,User:USER

# Bearer tokens (JWT validation)
oidcOAuth2Config.bearerTokenStrategy=jwt
oidcOAuth2Config.allowBearerTokens=true

# Logout
oidcOAuth2Config.globalLogoutEnabled=true
oidcOAuth2Config.postLogoutRedirectUri=https://geostore.example.com/mapstore/
```
