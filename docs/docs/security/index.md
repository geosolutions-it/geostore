# Security Overview

GeoStore provides a layered security model built on Spring Security. It supports multiple authentication backends that can be combined to suit different deployment scenarios.

## Authentication Methods

| Method | Description | Configuration |
|--------|-------------|---------------|
| **Local Users** | Database-backed username/password with HTTP Basic | Default, always available |
| **OIDC / OAuth2** | External identity providers (Keycloak, Azure AD, Google, Okta, etc.) | [OIDC Configuration](oidc.md), [Google Setup](../guides/google-setup.md) |
| **LDAP** | Directory-based authentication | [LDAP Configuration](ldap.md) |
| **Header-based SSO** | Trusted proxy authentication via HTTP headers | Spring XML config |

## Role Model

GeoStore uses a three-tier role hierarchy:

| Role | Level | Description |
|------|-------|-------------|
| `ADMIN` | Highest | Full access to all resources and administration |
| `USER` | Default | Standard access, can create and manage own resources |
| `GUEST` | Lowest | Read-only access to public resources |

Roles are assigned during user creation or mapped from identity provider claims. See [Roles & Groups](roles-and-groups.md) for details.

## Security Filter Chain

GeoStore registers multiple Spring Security filters in a specific order:

1. **UserAttributeTokenAuthenticationFilter** — Token-based authentication via request attributes
2. **SessionTokenAuthenticationFilter** — Session token validation
3. **OIDC OpenID Filter** — OIDC/OAuth2 login and bearer token validation (supports Google, Keycloak, Azure AD, etc.)
4. **HTTP Basic** — Standard username/password authentication

The first filter that successfully authenticates a request wins. Subsequent filters are skipped for that request.

## Configuration Approach

Security is configured through two mechanisms:

1. **Property overrides** (`geostore-ovr.properties`) — OAuth2/OIDC settings, session timeouts, user auto-creation
2. **Spring XML** (`geostore-spring-security.xml`) — Filter chain order, LDAP bean configuration, authentication manager setup

See the [Property Reference](properties-reference.md) for a complete list of all configurable security properties.
