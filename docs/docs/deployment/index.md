# Deployment Guide

## Deployment Options

GeoStore is packaged as a standard Java WAR file and runs in any Servlet 3.1+ container:

- **Apache Tomcat 9+** (recommended)
- **Jetty 9+**

---

## Database Support

GeoStore uses JPA/Hibernate and supports three databases:

| Database | Driver | Hibernate Dialect | Use Case |
|----------|--------|-------------------|----------|
| **H2** | `org.h2.Driver` | `H2Dialect` | Development and testing (embedded, in-memory) |
| **PostgreSQL** | `org.postgresql.Driver` | `PostgreSQLDialect` | Production (recommended) |
| **Oracle** | `oracle.jdbc.OracleDriver` | `Oracle10gDialect` | Production (enterprise) |

See [Installation — Database Setup](../getting-started/installation.md#database-setup) for connection configuration.

---

## Tomcat Deployment

### Basic Deployment

1. Build the WAR (see [Installation](../getting-started/installation.md)):

    ```bash
    mvn clean install -Ppostgres
    ```

2. Copy the WAR into Tomcat:

    ```bash
    cp src/web/app/target/geostore-webapp.war $CATALINA_HOME/webapps/geostore.war
    ```

3. Start Tomcat:

    ```bash
    $CATALINA_HOME/bin/startup.sh
    ```

### External Configuration

To keep configuration outside the WAR, pass an external properties file via a JVM system property:

```bash
# In $CATALINA_HOME/bin/setenv.sh
export CATALINA_OPTS="$CATALINA_OPTS -Dgeostore-ovr=/etc/geostore/geostore-ovr.properties"
```

This allows you to update configuration without rebuilding or redeploying the WAR. See [Configuration](../getting-started/configuration.md) for the full property reference.

### JVM Settings

Recommended JVM options for production:

```bash
# In $CATALINA_HOME/bin/setenv.sh
export CATALINA_OPTS="$CATALINA_OPTS \
  -Xms512m \
  -Xmx2g \
  -Dgeostore-ovr=/etc/geostore/geostore-ovr.properties"
```

---

## Reverse Proxy Setup

In production, GeoStore should sit behind a reverse proxy that handles TLS termination. This is **required** for OAuth2/OIDC flows, which mandate HTTPS.

### Nginx

```nginx
server {
    listen 443 ssl;
    server_name geostore.example.com;

    ssl_certificate     /etc/ssl/certs/geostore.crt;
    ssl_certificate_key /etc/ssl/private/geostore.key;

    location /geostore/ {
        proxy_pass http://127.0.0.1:8080/geostore/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Apache HTTP Server

```apache
<VirtualHost *:443>
    ServerName geostore.example.com

    SSLEngine on
    SSLCertificateFile    /etc/ssl/certs/geostore.crt
    SSLCertificateKeyFile /etc/ssl/private/geostore.key

    ProxyPreserveHost On
    ProxyPass        /geostore/ http://127.0.0.1:8080/geostore/
    ProxyPassReverse /geostore/ http://127.0.0.1:8080/geostore/

    RequestHeader set X-Forwarded-Proto "https"
</VirtualHost>
```

### Tomcat Proxy Configuration

When running behind a reverse proxy, configure Tomcat to trust the forwarded headers. In `$CATALINA_HOME/conf/server.xml`, add the `RemoteIpValve`:

```xml
<Valve className="org.apache.catalina.valves.RemoteIpValve"
       remoteIpHeader="X-Forwarded-For"
       protocolHeader="X-Forwarded-Proto" />
```

This ensures GeoStore generates correct redirect URIs for OAuth2 callbacks.

---

## TLS / HTTPS

OAuth2 and OIDC flows require HTTPS in production. Options:

1. **TLS at the reverse proxy** (recommended) — Terminate TLS at Nginx/Apache and proxy to Tomcat over HTTP on localhost.
2. **TLS at Tomcat** — Configure an HTTPS connector in `server.xml` with a Java keystore.

!!! warning
    Running OAuth2 over plain HTTP is acceptable for local development but **must not** be used in production. Access tokens transmitted over unencrypted connections can be intercepted.

---

## CORS Configuration

If GeoStore is accessed from a different origin (e.g., a MapStore frontend on a separate domain), configure CORS in Tomcat's `web.xml`:

```xml
<filter>
    <filter-name>CorsFilter</filter-name>
    <filter-class>org.apache.catalina.filters.CorsFilter</filter-class>
    <init-param>
        <param-name>cors.allowed.origins</param-name>
        <param-value>https://mapstore.example.com</param-value>
    </init-param>
    <init-param>
        <param-name>cors.allowed.methods</param-name>
        <param-value>GET,POST,PUT,DELETE,OPTIONS</param-value>
    </init-param>
    <init-param>
        <param-name>cors.allowed.headers</param-name>
        <param-value>Content-Type,Authorization,X-Requested-With</param-value>
    </init-param>
</filter>
<filter-mapping>
    <filter-name>CorsFilter</filter-name>
    <url-pattern>/rest/*</url-pattern>
</filter-mapping>
```

---

## Production Checklist

### Database

- [ ] Use PostgreSQL (or Oracle) instead of the embedded H2
- [ ] Set `hibernate.hbm2ddl.auto=validate` (not `update`) after initial schema creation
- [ ] Configure connection pooling (Commons DBCP is used by default; consider tuning `maxActive`, `maxIdle`, `maxWait`)
- [ ] Set up regular database backups

### Security

- [ ] Change default `admin`/`user` passwords immediately
- [ ] Enable HTTPS (required for OAuth2)
- [ ] Configure an identity provider (see [Security Overview](../security/index.md))
- [ ] Review `autoCreateUser` — disable if you do not want OIDC-authenticated users created automatically
- [ ] Ensure `logSensitiveInfo` is set to `false` in production
- [ ] Restrict the `/rest/diagnostics` endpoint to trusted networks (it is admin-only but exposes configuration details)

### Session & Token Management

- [ ] Set an appropriate `sessionTimeout` (default: 86400 seconds = 24 hours)
- [ ] Configure bearer token `maxTokenAgeSecs` if you want to limit token lifetime beyond the IdP's `exp` claim
- [ ] Review token cache settings (`cacheSize`, `cacheExpirationMinutes`)

### Monitoring

- [ ] Configure Log4j 2 output to files with rotation (default logs to stdout)
- [ ] Use the [Diagnostics endpoint](../security/monitoring-and-auditing.md) for runtime observability
- [ ] Set up health checks against `/rest/users/user/details` (returns 401 when the app is running)

### Network

- [ ] GeoStore must be able to reach the IdP's discovery, token, JWKS, and userinfo endpoints
- [ ] If using Microsoft Graph for Azure AD groups overage, GeoStore must be able to reach `https://graph.microsoft.com`
- [ ] Configure firewall rules to restrict direct access to the Tomcat port (route all traffic through the reverse proxy)

---

## Backup and Recovery

GeoStore provides built-in backup/restore via the REST API:

```bash
# Create a full backup (admin only)
curl -u admin:admin http://localhost:8080/geostore/rest/backup/full -o backup.xml

# Restore from a backup
curl -u admin:admin -X PUT \
  -H "Content-Type: application/xml" \
  -d @backup.xml \
  http://localhost:8080/geostore/rest/backup/full/{token}
```

For production environments, complement API-level backups with database-level backups (e.g., `pg_dump` for PostgreSQL).

---

## Scaling Considerations

GeoStore is a stateful application (server-side sessions, in-memory token cache). When running multiple instances behind a load balancer:

- **Sticky sessions** — Configure the load balancer for session affinity so that requests from the same user always hit the same instance.
- **Token cache** — Each instance maintains its own bearer token cache. A token validated on instance A will need to be re-validated on instance B. This is transparent to the client but adds latency on the first request to a new instance.
- **Shared database** — All instances must point to the same database.

!!! note
    For most GeoStore deployments a single instance is sufficient. Horizontal scaling is only necessary for high-availability requirements.
