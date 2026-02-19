# Authentication Methods

GeoStore supports multiple authentication methods that can be used simultaneously. This page provides an overview of each method and when to use it.

## Local User Authentication

The simplest authentication method uses GeoStore's built-in user database with HTTP Basic authentication.

```bash
curl -u admin:admin http://localhost:8080/geostore/rest/resources
```

Local users are managed through the REST API (`/users` endpoint) or auto-created during OAuth2/OIDC login flows.

## OAuth2 / OpenID Connect

GeoStore supports any OIDC-compliant identity provider through a generic OIDC integration. This is the recommended approach for production deployments.

**Supported providers include:** Keycloak, Azure AD / Entra ID, Google, Okta, Auth0, and others.

Two authentication flows are supported:

- **Authorization Code Flow** — Interactive browser-based login with optional PKCE
- **Bearer Token** — Direct API authentication with a pre-obtained access token

See [OIDC / OAuth2 Configuration](oidc.md) for full details.

## LDAP

GeoStore can authenticate users against an LDAP directory, with support for:

- User search with configurable filters
- Group and role resolution from LDAP entries
- Hierarchical group support

See [LDAP Configuration](ldap.md) for details.

## Header-Based SSO

For deployments behind a trusted reverse proxy, GeoStore can accept pre-authenticated requests via HTTP headers:

| Header | Purpose |
|--------|---------|
| `x-geostore-user` | Username |
| `x-geostore-groups` | Comma-separated group list |
| `x-geostore-role` | Role assignment |

!!! warning
    Header-based SSO should only be used when the proxy is fully trusted and direct access to GeoStore is blocked. An untrusted client can forge these headers to impersonate any user.

## Bearer Token Authentication

Any authenticated endpoint can be accessed with a Bearer token in the `Authorization` header:

```bash
curl -H "Authorization: Bearer <access_token>" \
  http://localhost:8080/geostore/rest/resources
```

Alternatively, the token can be passed as an `access_token` query parameter:

```bash
curl http://localhost:8080/geostore/rest/resources?access_token=<access_token>
```

See [Bearer Tokens](bearer-tokens.md) for validation strategies and configuration.
