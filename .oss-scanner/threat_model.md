# Threat model

The scanner reads this file before it starts. It covers GeoStore on the `master` branch. Our
disclosure policy is in [SECURITY.md](../SECURITY.md).

## What this project does and where untrusted input enters

GeoStore is a Java 17 web application (Spring, Spring Security, Apache CXF JAX-RS, JPA/Hibernate) that
stores resources (maps, dashboards, contexts, ...) with their metadata, attributes, stored data, categories,
tags and per-user/per-group permissions. It is the backend of [MapStore](https://github.com/geosolutions-it/MapStore2),
which embeds it and exposes its REST API at `/mapstore/rest/geostore/*`; it also runs standalone at
`/geostore/rest/*`. The API is usually reachable from the Internet.

Untrusted input reaches GeoStore through:

- **Anonymous HTTP requests** to any REST endpoint. Some endpoints accept `ROLE_ANONYMOUS`
  (public resources, session lookups, misc search).
- **Authenticated users** (`USER` role), who control every request body, path and query parameter: resource
  and attribute payloads, stored data, search filters (`SearchFilter` XML/JSON), sort fields, pagination,
  tag and favorite associations, permission (security rule) updates on resources they own.
- **Authentication material**: HTTP Basic credentials, session tokens (`/session`), access and refresh tokens,
  bearer tokens validated against an OAuth2/OIDC provider, LDAP answers, and pre-authentication headers
  when header-based authentication is configured.
- **Responses from identity providers**: OIDC discovery, JWKS, token and userinfo responses.

Trusted: the Spring configuration (`applicationContext*.xml`, `geostore-datasource-ovr.properties`, the
`*-ovr.properties` override files), the database, the environment (`INITIAL_ADMIN_PASSWORD`), and the
reverse proxy in front of GeoStore for the headers it is documented to set.

### Roles and trust

- **ADMIN** is trusted: it manages users, groups, categories, IP ranges, backups and diagnostics. An
  admin-only issue matters only if it reaches the host (code execution, file read/write outside the data
  store) or the stored password hashes and secrets.
- **USER** must stay within the permissions of the resources: read only what a security rule lets it read
  (its own rules, its groups' rules, the `everyone` group), write only what it can write, and never change
  users, groups, roles or other users' rules.
- **GUEST / anonymous** reaches only resources published to `everyone` and the endpoints that allow it.
- IP range rules (`RESTIPRangeService`) grant group permissions by client address: the client address must
  come from the connection or a trusted proxy header, not from a header the client can forge.

## Components that matter most / least

Highest priority:

1. Authorization in the service layer: `src/modules/rest/impl/.../services/rest/impl/` (resources, stored
   data `/data/{id}` and `/data/{id}/raw`, attributes, tags, favorites, categories, users, user groups,
   security rules), and the permission checks in `src/core/services-impl`.
2. Authentication: `services/rest/security/` (session tokens, `TokenAuthenticationCache`, the header and
   pre-auth filters, LDAP, `FormerDefaultPasswordCheck`) and `services/rest/security/oauth2/` (OAuth2/OIDC login,
   callback, state, bearer token validation, group and role sync).
3. Persistence and search: `src/core/persistence` (DAOs, search filters turned into queries) — injection
   through filter fields, sort fields or attribute names.
4. Password storage in `src/core/security/.../password`.

Lower priority: the auditing module (`src/modules/rest/auditing`), `src/modules/rest/extjs` (legacy UI
endpoints, still deployed and in scope), backup service (admin only).

Out of scope: `src/*/src/test/`, `qa/`, `doc/`, `LDAP/` sample data, `geoserver/` integration samples,
the `.github/` workflows.

Working rules: a test that asserts a behaviour marks it as deliberate. Read the code and probe the running
instance: some defects (URL parsing, redirects, encoded paths, header handling by the container) only show
up by sending requests, so use the instance to explore as well as to confirm. Once a root cause has a
reproducer and a patch, write it up rather than listing variants.

## How to exercise it

- Unit tests: `cd /src && mvn -o test -Ph2_disk,auditing` (tests run with H2).
- Running instance, offline:
  `cd /src/src/web/app && INITIAL_ADMIN_PASSWORD=<password> mvn -o jetty:run`, then the API is at
  `http://localhost:9191/geostore/rest/` (for example `curl -u admin:<password> .../rest/users/user/details`).
  Create a `USER` account and a second one through `/rest/users` as admin to test cross-user access.
  The legacy password `admin` is refused by design.

## How we rate severity

Rate against a default deployment. Name the role the attacker starts with and what they gain.

- **Critical**: unauthenticated code execution; unauthenticated full read or write of other users' private
  resources or stored data; authentication bypass to any account; anonymous or `USER` promotion to `ADMIN`.
- **High**: a `USER` reading or modifying another user's private resources, stored data, attributes or
  permissions; injection in queries (SQL/HQL/JPQL) reachable by a `USER`; OIDC/OAuth2 flaws that log a user
  into another account (missing state/nonce binding, token confusion, cache shared across users or
  providers); forging the client address to gain IP range permissions.
- **Medium**: disclosure of private metadata (names, descriptions, attributes, owner) without the data;
  user enumeration or disclosure of user attributes beyond what the API already exposes to users;
  session tokens that survive logout or password change; stored content that the API returns with a type
  a browser would execute.
- **Low**: information leaks without security impact, verbose errors, missing hardening headers.
- Admin-only issues: rated by what they give beyond admin powers (see above); otherwise not a finding.
- Behavior a documented configuration enables (header authentication trusting a header, public resources,
  a provider's group sync rules) is not a finding unless GeoStore applies it wrongly.

## Anything to leave alone

- Dependabot and CodeQL already scan this project: do not report dependency CVEs or plain CodeQL patterns
  unless you show they are reachable and exploitable. Focus on logic flaws: authorization, permission
  checks, credential and token handling.
- Generic DoS by request volume and missing rate limiting on login.
