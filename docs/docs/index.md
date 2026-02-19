# GeoStore Documentation

GeoStore is a Java-based, open-source framework for managing geospatial resources through a RESTful API. It provides storage, search, and retrieval of georeferenced resources with a flexible security model supporting multiple authentication backends.

## Key Features

- **RESTful API** — Full CRUD operations for resources, categories, and user management
- **Flexible Security** — Pluggable authentication with OAuth2/OIDC, LDAP, and local user stores
- **Resource Management** — Hierarchical categories, attributes, and spatial data storage
- **Multi-provider SSO** — Integrate with Keycloak, Azure AD, Google, and any OIDC-compliant provider
- **Bearer Token Support** — Authenticate API requests with JWT or opaque tokens
- **Role & Group Mapping** — Map identity provider roles and groups to GeoStore's permission model

## Quick Links

| Section | Description |
|---------|-------------|
| [Getting Started](getting-started/installation.md) | Installation, quickstart, and basic configuration |
| [Security](security/index.md) | Authentication, authorization, and identity provider integration |
| [Guides](guides/keycloak-setup.md) | Step-by-step provider setup guides (Keycloak, Azure AD, Google) |
| [REST API](api/index.md) | API endpoints and usage reference |
| [Architecture](architecture/index.md) | System design and module overview |
| [Deployment](deployment/index.md) | Production deployment options |

## Project Info

- **License:** GPLv3 + Classpath exception
- **Java:** 11+
- **Source:** [github.com/geosolutionsgroup/geostore](https://github.com/geosolutionsgroup/geostore)
- **Maintained by:** [GeoSolutions](https://www.geosolutionsgroup.com/)
