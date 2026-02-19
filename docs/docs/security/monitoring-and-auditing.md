# Monitoring & Auditing

## Overview

GeoStore provides an admin-only diagnostics REST endpoint for runtime observability of the security subsystem. This endpoint allows administrators to:

- Inspect and dynamically change security logger levels
- View token cache contents and statistics
- Dump the current OIDC/OAuth2 configuration (with secrets redacted)

All diagnostics endpoints require `ROLE_ADMIN` authentication. Sensitive values (client secrets, full token strings) are always masked in the output.

## Diagnostics Endpoint

The diagnostics API is available at `/rest/diagnostics/` and provides five operations:

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/diagnostics/` | Full diagnostic report (logging + cache + config) |
| `GET` | `/diagnostics/logging` | Security logger levels |
| `PUT` | `/diagnostics/logging/{loggerName}/{level}` | Change a logger's level (volatile) |
| `GET` | `/diagnostics/cache` | Token cache stats and redacted entries |
| `GET` | `/diagnostics/configuration` | OIDC/OAuth2 configuration dump |

### Full Report

Returns a combined view of all diagnostic sections:

```bash
curl -u admin:admin http://localhost:8080/geostore/rest/diagnostics/
```

Response:

```json
{
  "logging": {
    "securityLoggers": [
      {"name": "it.geosolutions...OpenIdConnectFilter", "level": "INFO"},
      {"name": "it.geosolutions...OAuth2GeoStoreAuthenticationFilter", "level": "DEBUG"}
    ],
    "note": "Use PUT /diagnostics/logging/{loggerName}/{level} to change. Changes are volatile."
  },
  "cache": {
    "status": "active",
    "size": 2,
    "entries": [
      {
        "tokenPrefix": "eyJhbGci...",
        "principal": "admin@example.com",
        "role": "ADMIN",
        "tokenExpiry": "2025-02-19T18:30:00Z",
        "expired": false,
        "provider": "oidc",
        "authorities": ["ROLE_ADMIN"]
      }
    ]
  },
  "configuration": {
    "providers": [...]
  },
  "timestamp": "2025-02-19T14:30:00Z"
}
```

### Logging

```bash
curl -u admin:admin http://localhost:8080/geostore/rest/diagnostics/logging
```

### Cache

```bash
curl -u admin:admin http://localhost:8080/geostore/rest/diagnostics/cache
```

Cache entries include:

| Field | Description |
|-------|-------------|
| `tokenPrefix` | First 8 characters of the access token (masked) |
| `principal` | The authenticated user's name |
| `role` | The user's GeoStore role (ADMIN, USER, GUEST) |
| `tokenExpiry` | ISO 8601 expiration timestamp |
| `expired` | Whether the token has expired |
| `provider` | Which OAuth2 provider issued the token |
| `authorities` | Spring Security granted authorities |

### Configuration

```bash
curl -u admin:admin http://localhost:8080/geostore/rest/diagnostics/configuration
```

The configuration dump includes all registered OAuth2 providers with their endpoints, scopes, claim mappings, and flags. Client secrets are always shown as `********`. For OIDC providers, additional fields like `allowBearerTokens`, `bearerTokenStrategy`, `jwkURI`, and `usePKCE` are included.

## Dynamic Log Level Control

You can change the log level of any security logger at runtime without restarting the application. Changes are **volatile** — they revert when the application restarts.

### Changing a Logger Level

```bash
curl -u admin:admin -X PUT \
  http://localhost:8080/geostore/rest/diagnostics/logging/it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.OpenIdConnectFilter/DEBUG
```

Response:

```json
{
  "logger": "it.geosolutions.geostore.services.rest.security.oauth2.openid_connect.OpenIdConnectFilter",
  "previousLevel": "INFO",
  "newLevel": "DEBUG",
  "volatile": true
}
```

### Restrictions

Only loggers under the `it.geosolutions.geostore.services.rest.security` prefix can be modified. Attempting to change any other logger returns a `400 Bad Request`.

### Common Loggers

| Logger | Purpose |
|--------|---------|
| `...security.oauth2.openid_connect.OpenIdConnectFilter` | OIDC authentication filter |
| `...security.oauth2.OAuth2GeoStoreAuthenticationFilter` | Base OAuth2 filter |
| `...security.oauth2.openid_connect.bearer.AudienceAccessTokenValidator` | Bearer token audience validation |
| `...security.oauth2.openid_connect.bearer.SubjectTokenValidator` | Bearer token subject validation |
| `...security.oauth2.openid_connect.OpenIdConnectTokenServices` | OIDC token services |
| `...security.oauth2.GeoStoreRemoteTokenServices` | Remote token introspection |
| `...security.oauth2.DiscoveryClient` | OIDC discovery document fetching |
| `...security.TokenAuthenticationCache` | Token cache operations |

### Volatile vs Persistent

- **Volatile** (via diagnostics endpoint): Changes apply immediately but are lost on restart. Ideal for live debugging.
- **Persistent** (via `log4j2.xml`): Configure in your deployment's `log4j2.xml` for changes that survive restarts.

## Token Cache Inspection

The cache endpoint allows you to see all active sessions in the token authentication cache.

### Identifying Expired Sessions

Look for entries where `expired` is `true`. These entries have not yet been evicted by the Guava cache (eviction happens lazily on access or on a schedule):

```bash
curl -u admin:admin http://localhost:8080/geostore/rest/diagnostics/cache | \
  python3 -c "import sys,json; d=json.load(sys.stdin); [print(e['principal'],e['tokenExpiry']) for e in d['cache']['entries'] if e.get('expired')]"
```

### Cache Statistics

If Guava cache statistics recording is enabled, the response will include `hitCount`, `missCount`, and `evictionCount`. Otherwise, a note is included indicating that statistics are not enabled.

## Configuration Dump

Use the configuration endpoint to verify that OIDC discovery has populated endpoints correctly:

```bash
curl -u admin:admin http://localhost:8080/geostore/rest/diagnostics/configuration | \
  python3 -c "import sys,json; d=json.load(sys.stdin); print(json.dumps(d['configuration']['providers'][0]['endpoints'], indent=2))"
```

This is useful for diagnosing issues where discovery documents return unexpected endpoint URLs, or where configuration properties are not being applied correctly.

## Audit Logging

GeoStore's security module produces structured log messages at key authentication points. These messages are designed for security auditing and troubleshooting.

### Key Log Messages

| Level | Logger | Message Pattern | When |
|-------|--------|----------------|------|
| `WARN` | `OpenIdConnectFilter` | `Bearer token validation failed: ...` | Bearer token rejected |
| `WARN` | `OpenIdConnectFilter` | `OIDC authentication failed: ...` | Auth code flow failure |
| `INFO` | `OpenIdConnectFilter` | `Successfully authenticated user '...' via OIDC` | Successful login |
| `WARN` | `AudienceAccessTokenValidator` | `Token audience mismatch ...` | Wrong audience claim |
| `WARN` | `SubjectTokenValidator` | `Token subject missing or empty` | No sub claim |
| `ERROR` | `DiscoveryClient` | `Failed to fetch OIDC discovery document ...` | Discovery failure |
| `WARN` | `GeoStoreRemoteTokenServices` | `Token introspection failed ...` | Introspection error |
| `INFO` | `TokenAuthenticationCache` | Cache eviction/addition events | At DEBUG level |

### Recommended Log4j2 Configuration for Audit

To route security events to a separate audit file, add an appender to your `log4j2.xml`:

```xml
<RollingFile name="SecurityAudit"
             fileName="${sys:catalina.base}/logs/geostore-security-audit.log"
             filePattern="${sys:catalina.base}/logs/geostore-security-audit-%d{yyyy-MM-dd}.log.gz">
    <PatternLayout pattern="%d{ISO8601} [%t] %-5level %logger{36} - %msg%n"/>
    <Policies>
        <TimeBasedTriggeringPolicy interval="1" modulate="true"/>
    </Policies>
    <DefaultRolloverStrategy max="30"/>
</RollingFile>

<Logger name="it.geosolutions.geostore.services.rest.security" level="INFO" additivity="true">
    <AppenderRef ref="SecurityAudit"/>
</Logger>
```

## Troubleshooting Matrix

| Scenario | Diagnostic Endpoint | Log Messages to Search |
|----------|--------------------|-----------------------|
| Bearer token rejected | `GET /diagnostics/cache` (check if token is cached), `GET /diagnostics/configuration` (verify `allowBearerTokens`, `bearerTokenStrategy`) | `Bearer token validation failed`, `Token audience mismatch`, `Token subject missing` |
| OIDC discovery fails | `GET /diagnostics/configuration` (check `discoveryUrl` and populated endpoints) | `Failed to fetch OIDC discovery document` |
| Cache misses / re-authentication | `GET /diagnostics/cache` (check `size`, `expired` entries) | Set `TokenAuthenticationCache` to `DEBUG` via `PUT /diagnostics/logging/.../DEBUG` |
| User gets wrong role | `GET /diagnostics/cache` (check `role` and `authorities` for the entry), `GET /diagnostics/configuration` (verify `rolesClaim`, `roleMappings`) | Set `OpenIdConnectFilter` to `DEBUG` for claim extraction logs |
| Token introspection errors | `GET /diagnostics/configuration` (verify `introspectionEndpoint`) | `Token introspection failed`, set `GeoStoreRemoteTokenServices` to `DEBUG` |
| PKCE flow failures | `GET /diagnostics/configuration` (verify `usePKCE` is `true`) | `Failed to generate PKCE parameters` |
