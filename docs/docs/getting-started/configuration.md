# Configuration

GeoStore uses Spring-based configuration with a layered property override mechanism. Most runtime settings — database connections, security providers, session behaviour — are controlled through property files without modifying XML.

---

## Configuration Files

| File | Purpose |
|------|---------|
| `geostore-ovr.properties` | Main property overrides (OIDC, LDAP, sessions, auto-create users, logging) |
| `geostore-datasource-ovr.properties` | Database connection and Hibernate settings |
| `geostore-spring-security.xml` | Spring Security bean wiring (authentication providers, filter chains) |

These files are located in the WAR at `WEB-INF/classes/`. To customise them without rebuilding, use an external override directory (see below).

---

## Property Override Mechanism

Properties in `geostore-ovr.properties` override Spring bean properties using the `{beanName}.{property}` convention. For example:

```properties
oidcOAuth2Config.enabled=true
oidcOAuth2Config.clientId=my-client-id
```

This sets the `enabled` and `clientId` properties on the Spring bean named `oidcOAuth2Config`.

### Override Resolution Order

GeoStore loads properties in the following order (last wins):

1. `classpath:geostore.properties` — base defaults bundled in the persistence module.
2. `classpath*:geostore-datasource-ovr.properties` — datasource defaults bundled in the WAR.
3. `classpath*:${ovrdir}/geostore-datasource-ovr.properties` — optional external datasource overrides.
4. `classpath*:geostore-ovr.properties` — application overrides bundled in the WAR.

### Using an External Override File

To supply configuration without modifying the WAR, pass the `geostore-ovr` system property pointing to an external properties file:

```bash
# Tomcat: add to CATALINA_OPTS or setenv.sh
export CATALINA_OPTS="-Dgeostore-ovr=/etc/geostore/geostore-ovr.properties"
```

```bash
# Jetty plugin
mvn jetty:run -Dgeostore-ovr=/etc/geostore/geostore-ovr.properties
```

Properties in the external file take the highest precedence and override everything else.

---

## Database Configuration

The datasource is configured via `geostore-datasource-ovr.properties` (or the external override file). Key properties:

```properties
# JDBC connection
geostoreDataSource.driverClassName=org.postgresql.Driver
geostoreDataSource.url=jdbc:postgresql://localhost:5432/geostore
geostoreDataSource.username=geostore
geostoreDataSource.password=geostore

# Hibernate dialect
geostoreEntityManagerFactory.jpaPropertyMap[hibernate.dialect]=org.hibernate.dialect.PostgreSQLDialect

# Schema management: validate | update | create | create-drop
geostoreEntityManagerFactory.jpaPropertyMap[hibernate.hbm2ddl.auto]=update

# Default schema (optional)
geostoreEntityManagerFactory.jpaPropertyMap[hibernate.default_schema]=public

# Show SQL in logs (disable in production)
geostoreVendorAdapter.showSql=false
```

See [Installation — Database Setup](installation.md#database-setup) for database-specific examples.

---

## Security Configuration

### OIDC / OAuth2

```properties
oidcOAuth2Config.enabled=true
oidcOAuth2Config.clientId=geostore-client
oidcOAuth2Config.clientSecret=<secret>
oidcOAuth2Config.discoveryUrl=https://idp.example.com/.well-known/openid-configuration
oidcOAuth2Config.redirectUri=https://geostore.example.com/geostore/rest/openid/oidc/callback
oidcOAuth2Config.postLogoutRedirectUri=https://geostore.example.com/geostore/
oidcOAuth2Config.scopes=openid,email,profile
oidcOAuth2Config.principalKey=email
oidcOAuth2Config.rolesClaim=roles
oidcOAuth2Config.groupsClaim=groups
oidcOAuth2Config.autoCreateUser=true
```

See [OIDC / OAuth2](../security/oidc.md) and the [Property Reference](../security/properties-reference.md) for the full list.

### Bearer Token Validation

```properties
oidcOAuth2Config.allowBearerTokens=true
oidcOAuth2Config.bearerTokenStrategy=jwt
oidcOAuth2Config.maxTokenAgeSecs=0
```

See [Bearer Token Authentication](../security/bearer-tokens.md).

### LDAP

```properties
# Enable in geostore-spring-security.xml by uncommenting the LDAP provider bean
geostoreLdapProvider.ignoreUsernameCase=true
```

See [LDAP](../security/ldap.md).

### Session Management

```properties
# Session timeout in seconds (default: 86400 = 24 hours)
restSessionService.sessionTimeout=86400
```

See [Session Management](../security/session-management.md).

---

## User Initialization

On first startup, GeoStore loads seed users, categories, and groups from XML files:

| Property | Default | Purpose |
|----------|---------|---------|
| `geostoreInitializer.userListInitFile` | `classpath:sample_users.xml` | Initial users |
| `geostoreInitializer.categoryListInitFile` | `classpath:sample_categories.xml` | Initial categories |
| `geostoreInitializer.groupListInitFile` | `classpath:sample_groups.xml` | Initial user groups |

To skip initialization, set the file properties to empty strings.

---

## Logging

GeoStore uses Log4j 2. The log configuration file is at `WEB-INF/classes/log4j2.xml` inside the WAR.

For security-related debug logging at runtime, use the `logSensitiveInfo` property:

```properties
logSensitiveInfo=true
```

!!! warning
    `logSensitiveInfo` logs full token contents and claim details at DEBUG level. Enable it only for troubleshooting and disable it immediately afterwards.

You can also adjust log levels dynamically via the [Diagnostics endpoint](../security/monitoring-and-auditing.md) (admin only):

```bash
curl -u admin:admin -X PUT \
  http://localhost:8080/geostore/rest/diagnostics/logging/it.geosolutions.geostore/DEBUG
```

---

## Next Steps

- [Security Property Reference](../security/properties-reference.md) — Complete list of all configurable security properties.
- [Deployment Guide](../deployment/index.md) — Production hardening, TLS, and reverse proxy setup.
- [Monitoring & Auditing](../security/monitoring-and-auditing.md) — Runtime diagnostics and audit logging.
