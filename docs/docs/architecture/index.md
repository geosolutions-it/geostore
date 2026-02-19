# Architecture Overview

!!! note "Placeholder"
    This page is a placeholder. Content will be added in a future update.

## Module Structure

GeoStore is organized as a multi-module Maven project:

```
geostore/
├── src/core/
│   ├── model/          # JPA entities (User, Resource, Category, UserGroup)
│   ├── persistence/    # DAO layer (JPA + LDAP implementations)
│   ├── services-api/   # Service interfaces
│   ├── services-impl/  # Service implementations
│   └── security/       # Password encoding and security utilities
├── src/modules/rest/
│   ├── api/            # JAX-RS REST interfaces
│   ├── impl/           # REST implementation + security filters
│   ├── extjs/          # ExtJS-compatible REST endpoints
│   ├── auditing/       # Audit trail for REST operations
│   └── client/         # Java REST client library
└── src/web/app/        # WAR packaging and Spring configuration
```

## Security Architecture

The security module supports pluggable authentication providers:

- **Local user store** — Database-backed username/password authentication
- **OAuth2 / OIDC** — External identity provider integration (Keycloak, Azure AD, Google)
- **LDAP** — Directory-based authentication and group resolution
- **Header-based SSO** — Trusted proxy authentication via HTTP headers

See the [Security section](../security/index.md) for detailed documentation.
