# Architecture Overview

## Module Structure

GeoStore is organised as a multi-module Maven project following a layered architecture: **model → persistence → services → REST API → web application**.

```
geostore/                              (parent POM — dependency management)
└── src/                               (module aggregator)
    ├── core/
    │   ├── model/                     JPA entities and enums
    │   ├── persistence/               DAO layer, datasource configuration
    │   ├── services-api/              Service interfaces and DTOs
    │   ├── services-impl/             Service implementations
    │   └── security/                  Password encoding utilities
    │
    ├── modules/rest/
    │   ├── api/                       JAX-RS REST interfaces
    │   ├── impl/                      REST implementations + security filters
    │   ├── extjs/                     ExtJS-compatible REST endpoints
    │   ├── auditing/                  Audit trail interceptors
    │   ├── client/                    Java REST client library
    │   └── test/                      Shared test utilities
    │
    └── web/app/                       WAR packaging + Spring context bootstrap
```

### Module Responsibilities

| Module | Artefact | Purpose |
|--------|----------|---------|
| **model** | `geostore-model` | JPA entity classes (`Resource`, `User`, `Category`, `UserGroup`, `StoredData`, `Attribute`, `SecurityRule`, `Tag`). No business logic — pure data model. |
| **persistence** | `geostore-persistence` | DAO interfaces and Hibernate-backed implementations. Defines the datasource, `EntityManagerFactory`, and transaction manager. Also provides LDAP-based DAO variants for read-only user/group resolution. |
| **services-api** | `geostore-services-api` | Service interfaces (`ResourceService`, `UserService`, `CategoryService`, etc.), search filters, and DTOs. Defines the contract consumed by the REST layer. |
| **services-impl** | `geostore-services-impl` | Transactional service implementations. Authorization checks, pagination, and business rules live here. |
| **security** | `geostore-security` | Password encoder utilities (PBE with Jasypt). |
| **rest-api** | `geostore-rest-api` | JAX-RS annotated interfaces declaring all REST endpoints. No implementation code — only annotations, paths, and parameter bindings. |
| **rest-impl** | `geostore-rest-impl` | REST endpoint implementations, OAuth2/OIDC security filters, bearer token validators, session management, and the diagnostics service. This is the largest module. |
| **rest-extjs** | `geostore-rest-extjs` | ExtJS/MapStore-specific REST endpoints that return paginated data in the format expected by ExtJS stores. |
| **rest-auditing** | `geostore-rest-auditing` | CXF interceptors for audit logging of REST operations. |
| **rest-client** | `geostore-rest-client` | Java client library for programmatic access to the GeoStore REST API. |
| **web/app** | `geostore-webapp` | WAR packaging module. Assembles all modules, provides the `web.xml` servlet configuration, Spring context bootstrap, security XML configuration, and property override files. |

---

## Data Model

GeoStore's persistence layer is built on JPA/Hibernate with the following core entities:

```
┌─────────────┐     1:N     ┌─────────────┐
│  Category   │────────────▶│  Resource    │
└─────────────┘             └──────┬───────┘
                                   │
                   ┌───────────────┼───────────────┐
                   │ 1:N           │ 1:1           │ 1:N
                   ▼               ▼               ▼
            ┌────────────┐  ┌────────────┐  ┌──────────────┐
            │ Attribute  │  │ StoredData │  │ SecurityRule │
            └────────────┘  └────────────┘  └──────┬───────┘
                                                   │
                                         ┌─────────┴─────────┐
                                         ▼                   ▼
                                   ┌──────────┐       ┌───────────┐
                                   │   User   │       │ UserGroup │
                                   └──────────┘       └───────────┘
```

| Entity | Table | Description |
|--------|-------|-------------|
| **Resource** | `gs_resource` | Central entity. Has a name, description, creation/update timestamps, and belongs to a Category. |
| **StoredData** | `gs_stored_data` | The actual data payload for a resource (JSON, XML, or arbitrary text). One-to-one with Resource. |
| **Attribute** | `gs_attribute` | Typed key-value metadata on a resource. Supports STRING, NUMBER, and DATE types. |
| **Category** | `gs_category` | Groups resources by type (e.g., MAP, DASHBOARD, CONTEXT). |
| **User** | `gs_user` | A GeoStore user with username, role (ADMIN/USER/GUEST), and optional attributes. |
| **UserGroup** | `gs_usergroup` | A named group of users. Resources can grant access to groups via security rules. |
| **SecurityRule** | `gs_security` | Per-resource ACL entry granting `canRead` / `canWrite` to a specific User or UserGroup. |
| **Tag** | `gs_tag` | Free-form labels that can be applied to resources for classification. |

---

## Request Processing Pipeline

An incoming REST request flows through the following layers:

```
HTTP Request
    │
    ▼
┌──────────────────────────────────────────────────┐
│  Servlet Container (Tomcat / Jetty)              │
│  └─ Spring DelegatingFilterProxy                 │
│      └─ Spring Security Filter Chain             │
│          ├─ BasicAuthenticationFilter             │
│          ├─ SessionTokenAuthenticationFilter      │
│          ├─ UserAttributeTokenAuthenticationFilter│
│          └─ CompositeOpenIdConnectFilter          │
│              ├─ OpenIdConnectFilter (provider 1)  │
│              ├─ OpenIdConnectFilter (provider 2)  │
│              └─ ...                               │
└──────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────┐
│  Apache CXF (JAX-RS)                             │
│  └─ REST Endpoint Implementation                 │
│      └─ Service Layer (transactional)            │
│          └─ DAO Layer (JPA / Hibernate)          │
│              └─ Database (H2 / PostgreSQL / Oracle)│
└──────────────────────────────────────────────────┘
```

### Security Filter Chain

The Spring Security filter chain evaluates authentication in order. The first filter that produces a valid `Authentication` object wins:

1. **BasicAuthenticationFilter** — Standard HTTP Basic auth against the local user store.
2. **SessionTokenAuthenticationFilter** — Validates session tokens issued by `/rest/session/login`.
3. **UserAttributeTokenAuthenticationFilter** — Validates Bearer tokens from the `Authorization` header using the token authentication cache.
4. **CompositeOpenIdConnectFilter** — Handles OIDC/OAuth2 flows:
    - **Authorization Code flow** — Browser-based login redirecting to the IdP.
    - **Bearer token validation** — Validates JWTs or opaque tokens attached to API requests.
    - Routes tokens to the correct provider based on audience matching when multiple OIDC providers are configured.

See [Security Overview](../security/index.md) for detailed configuration.

---

## Spring Context Wiring

GeoStore uses multiple Spring application context files loaded by `ContextLoaderListener` via the `classpath*:applicationContext.xml` pattern. Each module contributes its own context:

| Module | Context File | Key Beans |
|--------|-------------|-----------|
| **persistence** | `applicationContext.xml` + `applicationContext-geostoreDatasource.xml` | `geostoreDataSource`, `geostoreEntityManagerFactory`, `geostoreTransactionManager`, all DAO beans |
| **rest-impl** | `applicationContext.xml` | REST service beans, CXF server configuration, OAuth2/OIDC beans, token storage |
| **web/app** | `applicationContext.xml` + `geostore-spring-security.xml` | `geostoreInitializer`, security filter chain, authentication providers |

### Property Override

Bean properties are overridden at runtime using Spring's `PropertyOverrideConfigurer`:

```
geostore-ovr.properties
    ↓
oidcOAuth2Config.clientId=my-client  →  sets bean "oidcOAuth2Config", property "clientId"
```

See [Configuration](../getting-started/configuration.md) for the full override mechanism.

---

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 11+ |
| Build | Apache Maven | 3.6+ |
| Web Framework | Apache CXF (JAX-RS) | 3.5.x |
| IoC Container | Spring Framework | 5.3.x |
| Security | Spring Security + Spring Security OAuth2 | 5.7.x / 2.5.x |
| ORM | Hibernate (JPA) | 5.4.x |
| Serialisation | Jackson (JSON), JAXB (XML) | 2.16.x |
| JWT | java-jwt + Nimbus JOSE JWT | 3.18.x / 9.37.x |
| Claim Parsing | Jayway JsonPath | 2.9.x |
| Caching | EHCache (Hibernate L2), Guava (token cache) | — |
| Logging | SLF4J + Log4j 2 | 1.7.x / 2.19.x |
| Testing | JUnit 4, Mockito, WireMock | 4.13.x / 4.x / 2.x |

---

## Key Design Decisions

### Generic OIDC over Provider-Specific Adapters

GeoStore uses a single, generic OpenID Connect integration rather than provider-specific adapters (e.g., a dedicated Keycloak module). This means:

- Any OIDC-compliant identity provider works out of the box.
- Multiple providers can be configured simultaneously.
- New providers require zero code changes — only property configuration.

### Bearer Token Strategies

The OIDC filter supports three bearer token validation strategies (`jwt`, `introspection`, `auto`) to accommodate different IdP capabilities. See [Bearer Token Authentication](../security/bearer-tokens.md).

### Layered Security

Authentication is separated from authorization:

- **Authentication** — Handled by Spring Security filters (Basic, Session, Bearer, OIDC).
- **Authorization** — Handled by the service layer using `SecurityRule` entities that define per-resource ACLs.

### DAO Abstraction for LDAP

The DAO layer provides both JPA-backed and LDAP-backed implementations of `UserDAO` and `UserGroupDAO`. When LDAP is enabled, user and group lookups are resolved from the directory while resources, categories, and security rules remain in the database.
